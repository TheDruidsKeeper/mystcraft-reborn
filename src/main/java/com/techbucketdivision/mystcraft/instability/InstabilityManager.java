package com.tbd.mystcraft.instability;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.api.instability.InstabilityDirector;
import com.tbd.mystcraft.api.instability.InstabilityProvider;
import com.tbd.mystcraft.api.symbol.logic.EnvironmentalEffect;
import com.tbd.mystcraft.block.DecayType;
import com.tbd.mystcraft.config.MystcraftConfig;
import com.tbd.mystcraft.instability.effects.CrumbleEffect;
import com.tbd.mystcraft.instability.effects.EffectProviders;
import com.tbd.mystcraft.instability.effects.PotionEffectProvider;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Global registry of instability providers ("cards"), their activation costs, and the decks they are dealt into
 * (original spec §6.4). Providers disabled in {@code instability.disabled} are silently skipped.
 */
public final class InstabilityManager {
    private InstabilityManager() {}

    public static final String DECK_BASIC = "basic";
    public static final String DECK_HARSH = "harsh";
    public static final String DECK_DESTRUCTIVE = "destructive";
    public static final String DECK_EATING = "eating";
    public static final String DECK_DEATH = "death";

    private static final Map<String, InstabilityProvider> PROVIDERS = new LinkedHashMap<>();
    private static final Map<String, Integer> CARD_COSTS = new LinkedHashMap<>();
    private static final Map<String, List<String>> DECK_CARDS = new LinkedHashMap<>();
    private static final Map<String, Integer> DECK_COSTS = new LinkedHashMap<>();
    private static final Set<String> WARNED = new java.util.HashSet<>();
    private static int smallestCost = 500;
    private static boolean defaultsRegistered;

    /** Registers a provider. Returns {@code false} when rejected (disabled by config or invalid id). */
    public static synchronized boolean register(String id, InstabilityProvider provider, int activationCost) {
        if (id == null || id.isEmpty()) {
            Mystcraft.LOGGER.error("Attempted to register an instability provider with an empty id");
            return false;
        }
        if (PROVIDERS.containsKey(id)) {
            Mystcraft.LOGGER.warn("Instability provider {} registered twice; replacing", id);
        }
        if (!MystcraftConfig.isInstabilityEnabled(id)) {
            Mystcraft.LOGGER.info("Instability provider {} disabled by config", id);
            return false;
        }
        // Dry-run the provider against a recording director so broken providers are rejected early.
        try {
            provider.addEffects(new InstabilityDirector() {
                @Override public int getInstabilityScore() { return 0; }
                @Override public void registerEffect(EnvironmentalEffect effect) {}
            }, 1);
        } catch (Exception e) {
            Mystcraft.LOGGER.error("Instability provider {} threw during profiling and has been disabled", id, e);
            return false;
        }
        PROVIDERS.put(id, provider);
        CARD_COSTS.put(id, activationCost);
        if (activationCost > 0 && activationCost < smallestCost) smallestCost = activationCost;
        return true;
    }

    public static synchronized void setDeckCost(String deck, int cost) {
        DECK_COSTS.put(deck, cost);
        DECK_CARDS.computeIfAbsent(deck, d -> new ArrayList<>());
    }

    /** Adds {@code count} copies of a card to a deck. Unknown (disabled) providers are skipped. */
    public static synchronized void addCards(String deck, String card, int count) {
        if (!DECK_COSTS.containsKey(deck)) {
            throw new IllegalArgumentException("Unknown instability deck " + deck);
        }
        if (!PROVIDERS.containsKey(card)) {
            if (MystcraftConfig.isInstabilityEnabled(card) && WARNED.add(card)) {
                Mystcraft.LOGGER.error("Attempted to add card {} to deck {} but no such provider is registered", card, deck);
            }
            return;
        }
        List<String> cards = DECK_CARDS.computeIfAbsent(deck, d -> new ArrayList<>());
        for (int i = 0; i < count; i++) cards.add(card);
    }

    public static @Nullable InstabilityProvider getProvider(String id) {
        InstabilityProvider p = PROVIDERS.get(id);
        if (p == null && WARNED.add(id)) {
            Mystcraft.LOGGER.error("No instability provider for id {}", id);
        }
        return p;
    }

    public static Collection<String> allProviders() {
        return Collections.unmodifiableCollection(PROVIDERS.keySet());
    }

    public static int cardCost(String card) {
        return CARD_COSTS.getOrDefault(card, 0);
    }

    public static int deckCost(String deck) {
        return DECK_COSTS.getOrDefault(deck, 0);
    }

    public static Collection<String> decks() {
        return Collections.unmodifiableCollection(DECK_COSTS.keySet());
    }

    /** Cards of a deck in registration order (with duplicates). */
    public static List<String> deckCards(String deck) {
        return Collections.unmodifiableList(DECK_CARDS.getOrDefault(deck, List.of()));
    }

    /** Smallest positive activation cost; scores are quantised down to a multiple of it. */
    public static int smallestCost() {
        return smallestCost;
    }

    // --- defaults ------------------------------------------------------------------------------------------------

    /** Registers the built-in decks, cards and watched blocks (called from common setup). */
    public static synchronized void registerDefaults() {
        if (defaultsRegistered) return;
        defaultsRegistered = true;

        InstabilityBlocks.registerDefaults();

        setDeckCost(DECK_BASIC, 0);
        setDeckCost(DECK_HARSH, 2500);
        setDeckCost(DECK_DESTRUCTIVE, 10000);
        setDeckCost(DECK_EATING, 15000);
        setDeckCost(DECK_DEATH, 20000);

        card("blindness", new PotionEffectProvider(false, MobEffects.BLINDNESS, 60, false), 1000).add(DECK_EATING, 1);
        card("blindness,g", new PotionEffectProvider(true, MobEffects.BLINDNESS, 60, false), 1500).add(DECK_DEATH, 1);
        card("enemyregen,g", new PotionEffectProvider(true, MobEffects.REGENERATION, 200, true), 1000).add(DECK_BASIC, 5).add(DECK_HARSH, 2);
        card("enemyresist,g", new PotionEffectProvider(true, MobEffects.RESISTANCE, 200, true), 1000).add(DECK_BASIC, 2).add(DECK_HARSH, 1);
        card("fatigue", new PotionEffectProvider(false, MobEffects.MINING_FATIGUE, 80, false), 500).add(DECK_BASIC, 5);
        card("fatigue,g", new PotionEffectProvider(true, MobEffects.MINING_FATIGUE, 80, false), 1000).add(DECK_HARSH, 5);
        card("hunger", new PotionEffectProvider(false, MobEffects.HUNGER, 80, false), 500).add(DECK_BASIC, 8).add(DECK_HARSH, 2);
        card("hunger,g", new PotionEffectProvider(true, MobEffects.HUNGER, 80, false), 1000).add(DECK_HARSH, 5);
        card("nausea", new PotionEffectProvider(false, MobEffects.NAUSEA, 60, false), 1000).add(DECK_EATING, 1);
        card("nausea,g", new PotionEffectProvider(true, MobEffects.NAUSEA, 60, false), 1500).add(DECK_DEATH, 1);
        card("poison", new PotionEffectProvider(false, MobEffects.POISON, 80, false), 500).add(DECK_BASIC, 9).add(DECK_HARSH, 3);
        card("poison,g", new PotionEffectProvider(true, MobEffects.POISON, 80, false), 1000).add(DECK_HARSH, 5);
        card("slow", new PotionEffectProvider(false, MobEffects.SLOWNESS, 80, false), 500).add(DECK_BASIC, 6).add(DECK_HARSH, 1);
        card("slow,g", new PotionEffectProvider(true, MobEffects.SLOWNESS, 80, false), 1000).add(DECK_HARSH, 5);
        card("weakness", new PotionEffectProvider(false, MobEffects.WEAKNESS, 80, false), 500).add(DECK_BASIC, 8).add(DECK_HARSH, 2);
        card("weakness,g", new PotionEffectProvider(true, MobEffects.WEAKNESS, 80, false), 1000).add(DECK_HARSH, 5);
        card("wither", new PotionEffectProvider(false, MobEffects.WITHER, 30, false), 1000).add(DECK_HARSH, 1).add(DECK_DESTRUCTIVE, 1).add(DECK_EATING, 2);
        card("wither,g", new PotionEffectProvider(true, MobEffects.WITHER, 30, false), 2000).add(DECK_DESTRUCTIVE, 1).add(DECK_EATING, 1).add(DECK_DEATH, 1);

        card("burning", EffectProviders.scorched(), 500).add(DECK_HARSH, 1);
        CrumbleEffect.initMappings();
        card("crumble", EffectProviders.crumble(), 2000).add(DECK_DESTRUCTIVE, 6);
        card("decayblue", EffectProviders.decay(DecayType.BLUE, 25, null), 2000).add(DECK_EATING, 2).add(DECK_DEATH, 1);
        card("decaypurple", EffectProviders.decay(DecayType.PURPLE, 25, 54), 2000).add(DECK_EATING, 2).add(DECK_DEATH, 1);
        card("decayred", EffectProviders.decay(DecayType.RED, 25, null), 2000).add(DECK_EATING, 2).add(DECK_DEATH, 1);
        card("decaywhite", EffectProviders.whiteDecay(), 5000).add(DECK_EATING, 1).add(DECK_DEATH, 3);
        card("explosions", EffectProviders.explosions(), 1000).add(DECK_DESTRUCTIVE, 8);
        card("lightning", EffectProviders.lightning(), 1000).add(DECK_HARSH, 4).add(DECK_DESTRUCTIVE, 4);
        card("meteors", EffectProviders.meteors(), 1000).add(DECK_DESTRUCTIVE, 4);
        card(com.tbd.mystcraft.creature.CreatureRules.FRENZY_CARD, EffectProviders.frenzy(), 1000).add(DECK_HARSH, 3);

        // Registered but not dealt (as in the original): burning,g / crumblebedrock / decayblack / erosion.
        card("burning,g", EffectProviders.scorchedGlobal(), 1000);
        card("decayblack", EffectProviders.blackDecay(), 5000);
    }

    private static CardBuilder card(String id, InstabilityProvider provider, int cost) {
        return new CardBuilder(id, register(id, provider, cost));
    }

    private record CardBuilder(String id, boolean registered) {
        CardBuilder add(String deck, int count) {
            if (registered) addCards(deck, id, count);
            return this;
        }
    }
}
