package com.techbucketdivision.mystcraft.symbol;

import com.techbucketdivision.mystcraft.api.symbol.AgeDirector;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.BlockCategory;
import com.techbucketdivision.mystcraft.api.symbol.BlockDescriptor;
import com.techbucketdivision.mystcraft.api.symbol.Modifier;
import com.techbucketdivision.mystcraft.api.symbol.logic.BiomeController;
import com.techbucketdivision.mystcraft.api.symbol.logic.Celestial;
import com.techbucketdivision.mystcraft.api.symbol.logic.LightingController;
import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainGenerator;
import com.techbucketdivision.mystcraft.api.symbol.logic.WeatherController;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dry-runs a symbol's {@link AgeSymbol#registerLogic} against a recording director to learn which logic interfaces
 * it provides and which modifiers it consumes/produces. No world state is touched.
 */
public final class SymbolProfiler implements AgeDirector {

    public record Profile(Set<Class<?>> provided, Set<String> consumedModifiers, Set<String> producedModifiers,
                          Set<BlockCategory> consumedBlockCategories) {
        public boolean provides(Class<?> type) {
            for (Class<?> c : provided) {
                if (type.isAssignableFrom(c)) return true;
            }
            return false;
        }

        public boolean isBiomeController() { return provides(BiomeController.class); }
        public boolean isTerrainGenerator() { return provides(TerrainGenerator.class); }
        public boolean isLighting() { return provides(LightingController.class); }
        public boolean isWeather() { return provides(WeatherController.class); }
        public boolean isCelestial() { return provides(Celestial.class); }
    }

    private final Set<Class<?>> provided = new HashSet<>();
    private final Set<String> consumed = new HashSet<>();
    private final Set<String> produced = new HashSet<>();
    private final Set<BlockCategory> consumedCategories = new HashSet<>();
    private final Map<String, Modifier> modifiers = new HashMap<>();

    private SymbolProfiler() {}

    public static Profile profile(AgeSymbol symbol) {
        SymbolProfiler p = new SymbolProfiler();
        symbol.registerLogic(p, 0L);
        return new Profile(Set.copyOf(p.provided), Set.copyOf(p.consumed), Set.copyOf(p.produced), Set.copyOf(p.consumedCategories));
    }

    @Override public void registerInterface(Object logic) { provided.add(logic.getClass()); for (Class<?> i : logic.getClass().getInterfaces()) provided.add(i); }
    @Override public void setModifier(String id, Modifier modifier) { produced.add(id); modifiers.put(id, modifier); }
    @Override public Modifier popModifier(String id) { consumed.add(id); Modifier m = modifiers.remove(id); return m == null ? Modifier.EMPTY : m; }
    @Override public Modifier peekModifier(String id) { Modifier m = modifiers.get(id); return m == null ? Modifier.EMPTY : m; }
    @Override public void clearModifiers() { modifiers.clear(); }
    @Override public void pushBlock(BlockDescriptor block) { produced.add(Modifier.BLOCKLIST); }
    @Override public @Nullable BlockDescriptor popBlockMatching(BlockCategory... categories) { consumed.add(Modifier.BLOCKLIST); consumedCategories.addAll(List.of(categories)); return null; }
    @Override public void pushBiome(Holder<Biome> biome) { produced.add(Modifier.BIOMELIST); }
    @Override public @Nullable Holder<Biome> popBiome() { consumed.add(Modifier.BIOMELIST); return null; }
    @Override public List<Holder<Biome>> popAllBiomes() { consumed.add(Modifier.BIOMELIST); return List.of(); }
    @Override public void addInstability(int instability) {}
    @Override public void setCloudHeight(float height) {}
    @Override public void setHorizon(float height) {}
    @Override public void setAverageGroundLevel(int level) {}
    @Override public void setSeaLevel(int level) {}
    @Override public void setDrawHorizon(boolean draw) {}
    @Override public void setDrawVoid(boolean draw) {}
    @Override public void setPvPEnabled(boolean enabled) {}
    @Override public long getSeed() { return 0; }
    @Override public HolderLookup.Provider registries() { return RegistryAccess.EMPTY; }
    @Override public int getSeaLevel() { return 63; }
    @Override public boolean isProfiling() { return true; }
}
