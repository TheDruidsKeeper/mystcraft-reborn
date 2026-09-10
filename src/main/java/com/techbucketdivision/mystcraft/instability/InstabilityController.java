package com.techbucketdivision.mystcraft.instability;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.api.instability.InstabilityDirector;
import com.techbucketdivision.mystcraft.api.instability.InstabilityProvider;
import com.techbucketdivision.mystcraft.api.symbol.logic.EnvironmentalEffect;
import com.techbucketdivision.mystcraft.config.BalanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-Age instability runtime (REQUIREMENTS §6.1, §6.4): computes the score, quantises it, walks the decks and keeps
 * the resulting effects. One instance per loaded Age level; obtain with {@link #get(ServerLevel)}.
 */
public final class InstabilityController implements InstabilityDirector {
    public static final int MIN_CHUNKS = 400;
    public static final int RECOMPUTE_INTERVAL = 100;
    /** Radius (chunks) loaded around spawn to reach {@link #MIN_CHUNKS} profiled chunks. */
    private static final int PROFILE_RADIUS = 10;
    private static final TicketType PROFILING_TICKET = new TicketType(1200L, TicketType.FLAG_LOADING);

    private static final Map<ResourceKey<Level>, InstabilityController> CONTROLLERS = new ConcurrentHashMap<>();

    /** Controller for an Age level, or {@code null} for non-Age levels. */
    public static @Nullable InstabilityController get(ServerLevel level) {
        if (!AgeData.isAgeLevel(level.dimension())) return null;
        InstabilityController existing = CONTROLLERS.get(level.dimension());
        if (existing != null) return existing;
        AgeController age = AgeControllers.server(level);
        if (age == null) return null;
        return CONTROLLERS.computeIfAbsent(level.dimension(), k -> new InstabilityController(level, age));
    }

    public static void invalidate(ResourceKey<Level> key) {
        CONTROLLERS.remove(key);
    }

    public static void clearAll() {
        CONTROLLERS.clear();
    }

    private final ServerLevel level;
    private final AgeController age;
    private final InstabilityDeckData deckData;
    private final Map<String, List<String>> decks = new LinkedHashMap<>();
    private final Map<String, Integer> providerLevels = new LinkedHashMap<>();
    private final List<EnvironmentalEffect> effects = new ArrayList<>();

    private boolean enabled;
    private int lastScore;
    private int debugInstability;
    private @Nullable Integer blockInstability;
    private int nextProfiled;
    private long lastProfileRequest = Long.MIN_VALUE;

    private InstabilityController(ServerLevel level, AgeController age) {
        this.level = level;
        this.age = age;
        this.deckData = InstabilityDeckData.get(level);
        this.enabled = isInstabilityEnabled();
        buildDecks();
        this.lastScore = quantise(score());
        reconstruct();
    }

    public AgeController ageController() {
        return age;
    }

    /** Global switch AND per-Age flag. */
    public boolean isInstabilityEnabled() {
        return BalanceConfig.INSTABILITY_ENABLED.get() && age.ageData().instabilityEnabled();
    }

    /** State the controller was last (re)built with. */
    public boolean isEnabled() {
        return enabled;
    }

    // --- decks -------------------------------------------------------------------------------------------------

    private void buildDecks() {
        Random rand = new Random(age.ageData().seed());
        for (String deckName : InstabilityManager.decks()) {
            List<String> remaining = new ArrayList<>(InstabilityManager.deckCards(deckName));
            List<String> order = new ArrayList<>();
            boolean dirty = false;
            for (String card : deckData.deck(deckName)) {
                if (remaining.remove(card)) order.add(card);
                else dirty = true;
            }
            if (!remaining.isEmpty()) {
                Collections.shuffle(remaining, rand);
                order.addAll(remaining);
                dirty = true;
            }
            decks.put(deckName, order);
            if (dirty) deckData.updateDeck(deckName, order);
        }
    }

    /** Card order of a deck for this Age (debugging / commands). */
    public List<String> deck(String name) {
        return Collections.unmodifiableList(decks.getOrDefault(name, List.of()));
    }

    public Map<String, Integer> providerLevels() {
        return Collections.unmodifiableMap(providerLevels);
    }

    // --- score -------------------------------------------------------------------------------------------------

    /**
     * Full (un-quantised) score: {@code (debug + symbol + block + base + bonus) * difficulty}. 0 while fewer than
     * {@link #MIN_CHUNKS} chunks have been profiled (REQUIREMENTS §6.1).
     */
    public int score() {
        ChunkProfiler profiler = ChunkProfiler.get(level);
        int profiled = profiler.count();
        if (profiled < MIN_CHUNKS || profiled > nextProfiled || blockInstability == null) {
            nextProfiled = profiled + RECOMPUTE_INTERVAL;
            updateProfiledInstability(profiler);
        }
        if (blockInstability == null) return 0;
        int score = debugInstability + age.symbolInstability() + blockInstability + age.ageData().baseInstability()
                + InstabilityBonusManager.get(level).total();
        return Math.round(score * BalanceConfig.difficultyMultiplier());
    }

    private void updateProfiledInstability(ChunkProfiler profiler) {
        if (profiler.count() < MIN_CHUNKS) {
            requestProfilingChunks();
            return;
        }
        blockInstability = profiler.calculateInstability();
    }

    /** Loads a spiral of chunks around spawn so the profiler reaches its minimum sample (original: force-generate). */
    private void requestProfilingChunks() {
        long now = level.getGameTime();
        if (now - lastProfileRequest < 1200L) return;
        lastProfileRequest = now;
        BlockPos spawn = age.ageData().spawn();
        if (spawn == null) spawn = level.getRespawnData().pos();
        level.getChunkSource().addTicketWithRadius(PROFILING_TICKET, ChunkPos.containing(spawn), PROFILE_RADIUS);
    }

    public @Nullable Integer blockInstability() {
        return blockInstability;
    }

    public int profiledChunks() {
        return ChunkProfiler.get(level).count();
    }

    public int debugInstability() {
        return debugInstability;
    }

    public void setDebugInstability(int value) {
        debugInstability = value;
    }

    /** Forces the block instability to be recomputed on the next score query (after /myst-reprofile). */
    public void invalidateProfile() {
        blockInstability = null;
        nextProfiled = 0;
    }

    private static int quantise(int score) {
        int step = InstabilityManager.smallestCost();
        return step <= 0 ? score : score - Math.floorMod(score, step);
    }

    // --- (re)construction --------------------------------------------------------------------------------------

    private void validate() {
        boolean nowEnabled = isInstabilityEnabled();
        if (enabled != nowEnabled) {
            enabled = nowEnabled;
            reconstruct();
            return;
        }
        int newScore = quantise(score());
        if (newScore != lastScore) {
            lastScore = newScore;
            reconstruct();
        }
    }

    /** Rebuilds provider levels and effects from the decks for the current quantised score. */
    public synchronized void reconstruct() {
        providerLevels.clear();
        effects.clear();
        if (!enabled) return;
        for (Map.Entry<String, List<String>> deck : decks.entrySet()) {
            int budget = lastScore - InstabilityManager.deckCost(deck.getKey());
            if (budget < 0) continue;
            for (String card : deck.getValue()) {
                budget -= InstabilityManager.cardCost(card);
                if (budget < 0) break;
                providerLevels.merge(card, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : providerLevels.entrySet()) {
            InstabilityProvider provider = InstabilityManager.getProvider(e.getKey());
            if (provider == null) continue;
            try {
                provider.addEffects(this, e.getValue());
            } catch (Exception ex) {
                Mystcraft.LOGGER.error("Instability provider {} failed to add effects", e.getKey(), ex);
            }
        }
    }

    // --- ticking -----------------------------------------------------------------------------------------------

    /** Runs every active instability effect for one chunk (called from {@code AgeTicker}). */
    public void tick(LevelChunk chunk) {
        validate();
        if (!enabled || effects.isEmpty()) return;
        for (EnvironmentalEffect effect : effects) {
            try {
                effect.tick(level, chunk);
            } catch (Exception e) {
                Mystcraft.LOGGER.error("Instability effect {} threw while ticking chunk {}", effect.getClass().getSimpleName(), chunk.getPos(), e);
            }
        }
    }

    // --- InstabilityDirector -----------------------------------------------------------------------------------

    @Override
    public int getInstabilityScore() {
        return lastScore;
    }

    @Override
    public void registerEffect(EnvironmentalEffect effect) {
        effects.add(effect);
    }
}
