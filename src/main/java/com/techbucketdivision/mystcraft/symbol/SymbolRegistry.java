package com.techbucketdivision.mystcraft.symbol;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.Modifier;
import com.techbucketdivision.mystcraft.api.symbol.ModifierSlot;
import com.techbucketdivision.mystcraft.api.symbol.SymbolCategory;
import com.techbucketdivision.mystcraft.api.symbol.logic.BiomeController;
import com.techbucketdivision.mystcraft.api.symbol.logic.Celestial;
import com.techbucketdivision.mystcraft.api.symbol.logic.ChunkFinalizer;
import com.techbucketdivision.mystcraft.api.symbol.logic.DynamicColorProvider;
import com.techbucketdivision.mystcraft.api.symbol.logic.EnvironmentalEffect;
import com.techbucketdivision.mystcraft.api.symbol.logic.LightingController;
import com.techbucketdivision.mystcraft.api.symbol.logic.Populator;
import com.techbucketdivision.mystcraft.api.symbol.logic.StaticColorProvider;
import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainAlteration;
import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainGenerator;
import com.techbucketdivision.mystcraft.api.symbol.logic.WeatherController;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mod-owned registry of {@link AgeSymbol}s. Symbols are profiled on registration to know which logic interfaces they
 * provide (used for fallbacks when an Age lacks a terrain generator, biome controller, lighting or weather).
 * <p>
 * Thread-safe for reads after {@link #freeze()}.
 */
public final class SymbolRegistry {
    private SymbolRegistry() {}

    private static final Map<Identifier, AgeSymbol> SYMBOLS = new LinkedHashMap<>();
    private static final Map<Identifier, SymbolProfiler.Profile> PROFILES = new LinkedHashMap<>();
    private static final Set<Identifier> BLACKLIST = new LinkedHashSet<>();
    private static final List<Identifier> ERRORED = new ArrayList<>();
    private static volatile boolean frozen;

    /** Registers a symbol. Returns {@code false} if rejected (disabled by config, blacklisted, remapped, or errored). */
    public static synchronized boolean register(AgeSymbol symbol) {
        if (frozen) throw new IllegalStateException("Symbol registry is frozen; register during mod construction or common setup");
        Identifier id = symbol.id();
        if (SYMBOLS.containsKey(id)) {
            Mystcraft.LOGGER.warn("Duplicate symbol registration ignored: {}", id);
            return false;
        }
        if (BLACKLIST.contains(id) || SymbolRemapper.hasRemapping(id)) {
            return false;
        }
        if (symbol.generatesConfigOption() && !MystcraftConfig.isSymbolEnabled(id)) {
            return false;
        }
        SymbolProfiler.Profile profile;
        try {
            profile = SymbolProfiler.profile(symbol);
        } catch (Exception e) {
            Mystcraft.LOGGER.error("Symbol {} threw during profiling and has been disabled", id, e);
            ERRORED.add(id);
            return false;
        }
        bindSchema(symbol, profile);
        SYMBOLS.put(id, symbol);
        PROFILES.put(id, profile);
        return true;
    }

    // --- world-building schema ---------------------------------------------------------------------------------

    /**
     * Derives the symbol's schema from its profile: the category (explicit, else inferred from the logic it
     * registers), the modifier slots it accepts (the modifiers it pops) and, for modifier categories, the slot it
     * fills (the modifier it pushes).
     */
    private static void bindSchema(AgeSymbol symbol, SymbolProfiler.Profile profile) {
        SymbolCategory category = symbol.hasCategory() ? symbol.category() : inferCategory(profile);
        Set<ModifierSlot> accepts = EnumSet.noneOf(ModifierSlot.class);
        for (String id : profile.consumedModifiers()) {
            ModifierSlot slot = ModifierSlot.byModifierId(id);
            if (slot != null) accepts.add(slot);
        }
        // a sunset colour is built from the pending gradient, and a gradient from pending colours and lengths, so a
        // symbol taking one takes the ones it is made of too
        if (accepts.contains(ModifierSlot.SUNSET)) accepts.add(ModifierSlot.GRADIENT);
        if (accepts.contains(ModifierSlot.GRADIENT)) {
            accepts.add(ModifierSlot.COLOR);
            accepts.add(ModifierSlot.LENGTH);
        }
        ModifierSlot fills = null;
        if (category.isModifier()) {
            for (ModifierSlot candidate : FILL_PRIORITY) {
                if (profile.producedModifiers().contains(candidate.modifierId())) {
                    fills = candidate;
                    break;
                }
            }
            accepts.clear(); // modifiers are attached, they do not take modifiers of their own
        }
        symbol.bindSchema(category, accepts, fills, profile.consumedBlockCategories());
    }

    /** Which produced modifier names a modifier symbol's slot when it produces several (the gradient also pops colours). */
    private static final List<ModifierSlot> FILL_PRIORITY = List.of(ModifierSlot.GRADIENT, ModifierSlot.SUNSET, ModifierSlot.COLOR,
            ModifierSlot.DIRECTION, ModifierSlot.PHASE, ModifierSlot.LENGTH, ModifierSlot.BLOCK);

    /** Category for symbols registered without one (add-ons): by the logic they provide, else by what they push. */
    private static SymbolCategory inferCategory(SymbolProfiler.Profile profile) {
        if (profile.provides(TerrainGenerator.class)) return SymbolCategory.TERRAIN;
        if (profile.provides(BiomeController.class)) return SymbolCategory.BIOME_LAYOUT;
        if (profile.provides(LightingController.class)) return SymbolCategory.LIGHTING;
        if (profile.provides(WeatherController.class)) return SymbolCategory.WEATHER;
        if (profile.provides(Celestial.class)) return SymbolCategory.CELESTIALS;
        if (profile.provides(EnvironmentalEffect.class)) return SymbolCategory.EFFECTS;
        if (profile.provides(Populator.class) || profile.provides(TerrainAlteration.class) || profile.provides(ChunkFinalizer.class)) {
            return SymbolCategory.FEATURES;
        }
        if (profile.provides(DynamicColorProvider.class) || profile.provides(StaticColorProvider.class)) return SymbolCategory.SKY_COLORS;
        if (profile.producedModifiers().contains(Modifier.BIOMELIST)) return SymbolCategory.BIOMES;
        if (profile.producedModifiers().contains(Modifier.BLOCKLIST)) return SymbolCategory.MATERIALS;
        if (!profile.producedModifiers().isEmpty()) return SymbolCategory.MODIFIERS;
        return SymbolCategory.FEATURES;
    }

    /** Registered symbols of one category, in registration order. */
    public static List<AgeSymbol> inCategory(SymbolCategory category) {
        List<AgeSymbol> out = new ArrayList<>();
        for (AgeSymbol s : SYMBOLS.values()) if (s.category() == category) out.add(s);
        return out;
    }

    /**
     * Registers a symbol after the registry froze (biome/fluid symbols that depend on datapack registries). Replaces an
     * existing symbol with the same id and rebuilds the card-rank table.
     */
    public static synchronized boolean registerLate(AgeSymbol symbol) {
        boolean wasFrozen = frozen;
        frozen = false;
        try {
            SYMBOLS.remove(symbol.id());
            PROFILES.remove(symbol.id());
            return register(symbol);
        } finally {
            frozen = wasFrozen;
            if (wasFrozen) CardRanks.rebuild(SYMBOLS.values());
        }
    }

    public static synchronized void blacklist(Identifier id) {
        BLACKLIST.add(id);
        SYMBOLS.remove(id);
        PROFILES.remove(id);
    }

    public static @Nullable AgeSymbol get(Identifier id) {
        return SYMBOLS.get(id);
    }

    public static @Nullable AgeSymbol get(String id) {
        Identifier rl = Identifier.tryParse(id);
        return rl == null ? null : SYMBOLS.get(rl);
    }

    public static boolean contains(Identifier id) {
        return SYMBOLS.containsKey(id);
    }

    public static Collection<AgeSymbol> all() {
        return Collections.unmodifiableCollection(SYMBOLS.values());
    }

    public static SymbolProfiler.@Nullable Profile profile(Identifier id) {
        return PROFILES.get(id);
    }

    /** Symbols whose profile shows they register logic implementing {@code type}. */
    public static List<AgeSymbol> implementing(Class<?> type) {
        List<AgeSymbol> out = new ArrayList<>();
        for (Map.Entry<Identifier, SymbolProfiler.Profile> e : PROFILES.entrySet()) {
            if (e.getValue().provides(type)) out.add(SYMBOLS.get(e.getKey()));
        }
        return out;
    }

    public static List<Identifier> erroredSymbols() {
        return Collections.unmodifiableList(ERRORED);
    }

    /** Registers every built-in symbol. Called once from common setup. */
    public static void bootstrapBuiltins() {
        com.techbucketdivision.mystcraft.symbol.symbols.BuiltinSymbols.registerAll();
        com.techbucketdivision.mystcraft.symbol.modifiers.ModifierSymbols.registerAll();
        BlockSymbols.registerAll();
    }

    public static synchronized void freeze() {
        frozen = true;
        CardRanks.rebuild(SYMBOLS.values());
    }

    public static boolean isFrozen() {
        return frozen;
    }
}
