package com.tbd.mystcraft.api.symbol.logic;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/** Decides which biome is at a position. Registered by biome-distribution symbols. */
public interface BiomeController {
    /** Biome at block coordinates. */
    Holder<Biome> getBiomeAt(int blockX, int blockZ);

    /** Biome at "quart" (4-block) resolution; default samples {@link #getBiomeAt}. */
    default Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        return getBiomeAt(quartX << 2, quartZ << 2);
    }

    /** Every biome this controller can produce (used for spawn search and biome source validation). */
    List<Holder<Biome>> possibleBiomes();
}
