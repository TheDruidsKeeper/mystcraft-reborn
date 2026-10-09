package com.tbd.mystcraft.api.symbol;

import com.tbd.mystcraft.api.symbol.logic.BiomeController;
import com.tbd.mystcraft.api.symbol.logic.Celestial;
import com.tbd.mystcraft.api.symbol.logic.ChunkFinalizer;
import com.tbd.mystcraft.api.symbol.logic.DynamicColorProvider;
import com.tbd.mystcraft.api.symbol.logic.EnvironmentalEffect;
import com.tbd.mystcraft.api.symbol.logic.LightingController;
import com.tbd.mystcraft.api.symbol.logic.Populator;
import com.tbd.mystcraft.api.symbol.logic.StaticColorProvider;
import com.tbd.mystcraft.api.symbol.logic.TerrainAlteration;
import com.tbd.mystcraft.api.symbol.logic.TerrainGenerator;
import com.tbd.mystcraft.api.symbol.logic.WeatherController;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The interface symbols use to contribute logic while an Age is being constructed. Implemented by the runtime
 * {@code AgeController} and by the {@code SymbolProfiler} (which only records what a symbol would register).
 */
public interface AgeDirector {

    // --- logic registration ------------------------------------------------------------------------------------

    /**
     * Registers any logic object. The director inspects which logic interfaces the object implements
     * ({@link BiomeController}, {@link TerrainGenerator}, {@link TerrainAlteration}, {@link Populator},
     * {@link ChunkFinalizer}, {@link LightingController}, {@link WeatherController}, {@link Celestial},
     * {@link DynamicColorProvider}, {@link StaticColorProvider}, {@link EnvironmentalEffect}) and files it accordingly.
     */
    void registerInterface(Object logic);

    // --- modifiers ---------------------------------------------------------------------------------------------

    /** Sets (replaces) a modifier. A replaced non-empty modifier charges its dangling cost. */
    void setModifier(String id, Modifier modifier);

    default void setModifier(String id, @Nullable Object value) {
        setModifier(id, new Modifier(value));
    }

    /** Removes and returns the modifier, or {@link Modifier#EMPTY}. */
    Modifier popModifier(String id);

    /** Peeks without removing. */
    Modifier peekModifier(String id);

    /** Clears every pending modifier, charging 20% of each dangling cost. */
    void clearModifiers();

    // --- block & biome lists -----------------------------------------------------------------------------------

    /** Pushes a block to the front of the block list (dangling +50 per block). */
    void pushBlock(BlockDescriptor block);

    /** Removes and returns the first block usable for any of the categories, or {@code null}. */
    @Nullable BlockDescriptor popBlockMatching(BlockCategory... categories);

    /** Pushes a biome to the end of the biome list (dangling +100 per biome). */
    void pushBiome(Holder<Biome> biome);

    /** Pops the last pushed biome or {@code null}. */
    @Nullable Holder<Biome> popBiome();

    /** Removes and returns all pending biomes (may be empty). */
    List<Holder<Biome>> popAllBiomes();

    // --- age-wide settings -------------------------------------------------------------------------------------

    void addInstability(int instability);

    void setCloudHeight(float height);

    void setHorizon(float height);

    void setAverageGroundLevel(int level);

    void setSeaLevel(int level);

    void setDrawHorizon(boolean draw);

    void setDrawVoid(boolean draw);

    void setPvPEnabled(boolean enabled);

    // --- context -----------------------------------------------------------------------------------------------

    /** Age seed. */
    long getSeed();

    /** Registry access for looking up biomes/blocks. */
    HolderLookup.Provider registries();

    /** Current sea level (for symbols that need it while registering). */
    int getSeaLevel();

    /** Whether this director actually builds an Age ({@code false} while profiling). */
    default boolean isProfiling() {
        return false;
    }

    /**
     * {@code true} for the client-side mirror built from synced data. World-generation logic (biome controllers,
     * terrain, populators) must not be constructed there: the client lacks server-only registries such as
     * {@code worldgen/multi_noise_biome_source_parameter_list}.
     */
    default boolean isClientSide() {
        return false;
    }
}
