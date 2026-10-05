package com.tbd.mystcraft.api.symbol.logic;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only view of the Age used by terrain generators and alterations. */
public interface TerrainContext {
    long seed();

    int seaLevel();

    int averageGroundLevel();

    /** World height bounds of the Age dimension type. */
    int minY();

    int maxY();

    Holder<Biome> biomeAt(int blockX, int blockZ);

    /** Vanilla-style biome base height (-1..1 scale, plains = 0.125) and variation for terrain shaping. */
    float biomeBaseHeight(Holder<Biome> biome);

    float biomeHeightVariation(Holder<Biome> biome);

    /** Default terrain / sea blocks chosen from the block list (fallback stone/water). */
    BlockState terrainBlock();

    BlockState seaBlock();
}
