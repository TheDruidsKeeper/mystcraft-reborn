package com.techbucketdivision.mystcraft.symbol;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.symbol.AgeDirector;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.WordData;
import com.techbucketdivision.mystcraft.symbol.grammar.Grammar;
import com.techbucketdivision.mystcraft.symbol.grammar.GrammarRules;
import com.techbucketdivision.mystcraft.util.MystIds;
import com.techbucketdivision.mystcraft.world.biome.BiomeHeights;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Biome modifier symbols (REQUIREMENTS §4.3.12 "Biome"). One symbol per registered biome, created per server from
 * its datapack registry ({@code ServerAboutToStartEvent} in package E, and lazily by {@link #selectableBiomes}).
 * <p>
 * Id: {@code mystcraft:biome_<namespace>_<path>}. Card rank 2 (End / void biomes: {@code null}, and their grammar
 * rule is connect-only). Display name: {@code symbol.mystcraft.biome.wrapper} = "%s Biome". Trade price 1 emerald.
 */
public final class BiomeSymbols {
    private BiomeSymbols() {}

    public static final String WRAPPER_KEY = "symbol.mystcraft.biome.wrapper";

    /** Vanilla biomes excluded from random selection (rank null): the End and the void. */
    private static final Set<String> END_BIOME_PATHS = Set.of("the_end", "end_highlands", "end_midlands", "end_barrens",
            "small_end_islands", "the_void");

    private static HolderLookup.@Nullable Provider registeredFor;
    private static Set<ResourceKey<Biome>> registeredKeys = Set.of();
    private static List<Holder<Biome>> selectable = List.of();

    public static final class BiomeSymbol extends AgeSymbol {
        private final ResourceKey<Biome> key;
        private final Holder<Biome> holder;

        BiomeSymbol(ResourceKey<Biome> key, Holder<Biome> holder, @Nullable Integer rank) {
            super(idFor(key), rank, WordData.NATURE, WordData.NURTURE, WordData.ENCOURAGE, key.identifier().getPath());
            this.key = key;
            this.holder = holder;
        }

        public ResourceKey<Biome> biomeKey() {
            return key;
        }

        /** The holder captured at registration (bound to the registry the symbol was created from). */
        public Holder<Biome> holder() {
            return holder;
        }

        @Override
        public String descriptionId() {
            return WRAPPER_KEY;
        }

        @Override
        public Component displayName() {
            Identifier id = key.identifier();
            return Component.translatable(WRAPPER_KEY, Component.translatable("biome." + id.getNamespace() + "." + id.getPath()));
        }

        @Override
        public Component description() {
            Identifier id = key.identifier();
            return Component.translatable(WRAPPER_KEY + ".desc", Component.translatable("biome." + id.getNamespace() + "." + id.getPath()));
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Holder<Biome> biome = holder;
            if (!director.isProfiling()) {
                biome = resolve(director.registries()).orElse(holder);
                director.setAverageGroundLevel((int) (BiomeHeights.baseHeight(biome) * 64 + 64));
            } else {
                director.setAverageGroundLevel(64);
            }
            director.pushBiome(biome);
        }

        private Optional<Holder<Biome>> resolve(HolderLookup.Provider registries) {
            try {
                return registries.lookupOrThrow(Registries.BIOME).get(key).map(h -> (Holder<Biome>) h);
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
    }

    /** {@code mystcraft:biome_<namespace>_<path>} */
    public static Identifier idFor(ResourceKey<Biome> key) {
        Identifier id = key.identifier();
        return MystIds.id("biome_" + MystIds.pathSafe(id.getNamespace()) + "_" + MystIds.pathSafe(id.getPath()));
    }

    /** Whether the biome is excluded from random generation / trading. */
    public static boolean isEndOrVoid(ResourceKey<Biome> key) {
        Identifier id = key.identifier();
        return id.getNamespace().equals("minecraft") && END_BIOME_PATHS.contains(id.getPath());
    }

    /**
     * (Re)registers a symbol for every biome of the given registries and the fluid symbols. Safe to call repeatedly
     * (each call replaces the symbols of the previous registry set). Also rebuilds the card ranks and grammar.
     */
    public static synchronized void registerAll(HolderLookup.Provider registries) {
        List<Holder<Biome>> list = new ArrayList<>();
        Set<ResourceKey<Biome>> keys = new HashSet<>();
        int count = 0;
        HolderLookup.RegistryLookup<Biome> lookup = registries.lookupOrThrow(Registries.BIOME);
        for (Holder.Reference<Biome> ref : lookup.listElements().toList()) {
            ResourceKey<Biome> key = ref.key();
            boolean end = isEndOrVoid(key);
            BiomeSymbol symbol = new BiomeSymbol(key, ref, end ? null : 2);
            if (SymbolRegistry.registerLate(symbol)) {
                Grammar.addSymbolRule(symbol, GrammarRules.BIOME, end ? null : 1);
                CardRanks.overrideTradePrice(symbol.id(), 1);
                if (!end) list.add(ref);
                keys.add(key);
                count++;
            }
        }
        selectable = Collections.unmodifiableList(list);
        registeredKeys = Set.copyOf(keys);
        registeredFor = registries;
        FluidSymbols.registerAll();
        Mystcraft.LOGGER.info("Registered {} biome symbols", count);
    }

    /**
     * Biomes eligible for random selection, as holders of the given registries. Registers the biome symbols if the
     * registries contain a different biome set than the one registered so far (first call on a server / client).
     */
    public static synchronized List<Holder<Biome>> selectableBiomes(HolderLookup.Provider registries) {
        if (registeredFor == registries) return selectable;
        try {
            HolderLookup.RegistryLookup<Biome> lookup = registries.lookupOrThrow(Registries.BIOME);
            List<Holder.Reference<Biome>> refs = lookup.listElements().toList();
            Set<ResourceKey<Biome>> keys = new HashSet<>();
            for (Holder.Reference<Biome> ref : refs) keys.add(ref.key());
            if (keys.equals(registeredKeys)) {
                // Same biome set from another registry instance (e.g. client vs integrated server): hand out holders
                // bound to the caller's registries without re-registering the symbols.
                List<Holder<Biome>> out = new ArrayList<>();
                for (Holder.Reference<Biome> ref : refs) {
                    if (!isEndOrVoid(ref.key())) out.add(ref);
                }
                return Collections.unmodifiableList(out);
            }
            registerAll(registries);
        } catch (RuntimeException e) {
            Mystcraft.LOGGER.warn("Cannot register biome symbols from the given registries", e);
            return List.of();
        }
        return selectable;
    }

    public static @Nullable BiomeSymbol symbolFor(ResourceKey<Biome> key) {
        return SymbolRegistry.get(idFor(key)) instanceof BiomeSymbol b ? b : null;
    }
}
