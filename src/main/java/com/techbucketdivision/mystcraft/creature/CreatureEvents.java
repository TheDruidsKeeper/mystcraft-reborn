package com.techbucketdivision.mystcraft.creature;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.api.symbol.CreatureDifficulty;
import com.techbucketdivision.mystcraft.api.symbol.CreatureGroup;
import com.techbucketdivision.mystcraft.api.symbol.logic.CreatureController;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime of the creature controllers (plan §10): natural spawns in an Age are capped per group (a census every
 * {@link #CENSUS_TICKS} ticks), hostiles get the Age's difficulty when they spawn, and groups whose cap is raised
 * above vanilla get an extra spawn pass from the Age ticker, since vanilla stops at its own cap.
 */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class CreatureEvents {
    private CreatureEvents() {}

    public static final int CENSUS_TICKS = 40;
    private static final int EXTRA_SPAWN_TICKS = 20;
    private static final int EXTRA_SPAWN_RADIUS = 6;

    private static final class Census {
        final Map<CreatureGroup, Integer> counts = new EnumMap<>(CreatureGroup.class);
        int spawnableChunks = CreatureRules.SPAWN_AREA_CHUNKS;
        long tick;
    }

    private static final Map<ResourceKey<Level>, Census> CENSUS = new HashMap<>();

    /** Mobs of each group currently in the level (cached; refreshed every {@link #CENSUS_TICKS} ticks). */
    public static Map<CreatureGroup, Integer> census(ServerLevel level) {
        return census(level, false).counts;
    }

    private static Census census(ServerLevel level, boolean force) {
        Census census = CENSUS.computeIfAbsent(level.dimension(), k -> new Census());
        long now = level.getGameTime();
        if (!force && census.tick != 0 && now - census.tick < CENSUS_TICKS) return census;
        census.tick = now;
        census.counts.clear();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob) || mob.isPersistenceRequired()) continue;
            CreatureGroup group = CreatureGroup.of(mob.getType());
            if (group != null) census.counts.merge(group, 1, Integer::sum);
        }
        NaturalSpawner.SpawnState state = level.getChunkSource().getLastSpawnState();
        if (state != null && state.getSpawnableChunkCount() > 0) census.spawnableChunks = state.getSpawnableChunkCount();
        return census;
    }

    public static void forget(ResourceKey<Level> level) {
        CENSUS.remove(level);
    }

    public static void forgetAll() {
        CENSUS.clear();
    }

    // --- caps ------------------------------------------------------------------------------------------------------

    /** Natural spawns in an Age are refused once the group's scaled cap is reached. */
    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !AgeData.isAgeLevel(level.dimension())) return;
        if (event.getSpawnType() != EntitySpawnReason.NATURAL && event.getSpawnType() != EntitySpawnReason.CHUNK_GENERATION) return;
        Mob mob = event.getEntity();
        CreatureGroup group = CreatureGroup.of(mob.getType());
        if (group == null) return;
        CreatureController controller = CreatureRules.controller(level, group);
        if (controller == null || controller.capFactor() >= 1f) return; // vanilla's own cap applies
        Census census = census(level, false);
        int allowed = CreatureRules.allowed(mob.getType().getCategory(), census.spawnableChunks, controller);
        if (census.counts.getOrDefault(group, 0) >= allowed) {
            Mystcraft.LOGGER.debug("[creatures] {}: {} refused, {} at cap {}", level.dimension().identifier(), mob.getType().toShortString(), group, allowed);
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    // --- difficulty --------------------------------------------------------------------------------------------------

    /** Hostiles spawning in an Age take the Age's difficulty (one step harder while Frenzy is dealt). */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !AgeData.isAgeLevel(level.dimension())) return;
        Mob mob = event.getEntity();
        if (CreatureGroup.of(mob.getType()) != CreatureGroup.HOSTILE) return;
        CreatureDifficulty difficulty = CreatureRules.difficulty(CreatureRules.controller(level, CreatureGroup.HOSTILE), CreatureRules.frenzy(level));
        if (difficulty == CreatureDifficulty.NORMAL) return;
        CreatureRules.applyDifficulty(mob, difficulty);
        Mystcraft.LOGGER.debug("[creatures] {}: {} spawned {}", level.dimension().identifier(), mob.getType().toShortString(), difficulty);
    }

    // --- extra spawn pass for raised caps -----------------------------------------------------------------------------

    /**
     * Called by the Age ticker: for every group whose cap factor is above 1 and whose census is below its scaled cap,
     * runs one vanilla spawn attempt in a random loaded chunk near each player (vanilla would have stopped at its own
     * cap). The spawn filter only lets through types of such groups, so a shared category (monsters = hostile +
     * tagged neutrals) only grows the groups that asked for it.
     */
    public static void extraSpawns(ServerLevel level, AgeController controller) {
        if (level.getGameTime() % EXTRA_SPAWN_TICKS != 0 || level.players().isEmpty()) return;
        boolean any = false;
        for (CreatureGroup group : CreatureGroup.values()) {
            CreatureController c = controller.creatures(group);
            if (c != null && c.capFactor() > 1f && c.rate() > 0f) any = true;
        }
        if (!any) return;
        Census census = census(level, false);
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            ChunkPos center = player.chunkPosition();
            LevelChunk chunk = level.getChunkSource().getChunkNow(center.x() + random.nextInt(EXTRA_SPAWN_RADIUS * 2 + 1) - EXTRA_SPAWN_RADIUS,
                    center.z() + random.nextInt(EXTRA_SPAWN_RADIUS * 2 + 1) - EXTRA_SPAWN_RADIUS);
            if (chunk == null) continue;
            for (MobCategory category : MobCategory.values()) {
                if (category == MobCategory.MISC) continue;
                boolean wanted = false;
                for (CreatureGroup group : CreatureGroup.values()) {
                    if (group.covers(category) && belowRaisedCap(census, category, controller.creatures(group), group)) wanted = true;
                }
                if (!wanted) continue;
                NaturalSpawner.spawnCategoryForChunk(category, level, chunk,
                        (type, pos, levelChunk) -> {
                            CreatureGroup group = CreatureGroup.of(type);
                            return group != null && belowRaisedCap(census, category, controller.creatures(group), group);
                        },
                        (mob, levelChunk) -> {
                            CreatureGroup group = CreatureGroup.of(mob.getType());
                            if (group != null) census.counts.merge(group, 1, Integer::sum);
                            Mystcraft.LOGGER.debug("[creatures] {}: extra {} ({}) at {}", level.dimension().identifier(), mob.getType().toShortString(), group, mob.blockPosition().toShortString());
                        });
            }
        }
    }

    private static boolean belowRaisedCap(Census census, MobCategory category, @Nullable CreatureController c, CreatureGroup group) {
        if (c == null || c.capFactor() <= 1f || c.rate() <= 0f) return false;
        return census.counts.getOrDefault(group, 0) < CreatureRules.allowed(category, census.spawnableChunks, c);
    }
}
