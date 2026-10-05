package com.tbd.mystcraft.api.symbol.logic;

import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Modifies terrain after the terrain generator ran but before surface/biome decoration (caves, ravines, spheres,
 * floating islands, tendrils...). Runs in symbol order. Must only write inside {@code chunk}; use the seed and the
 * chunk coordinates of neighbouring chunks to keep features continuous across chunk borders (as vanilla map-gens do).
 */
public interface TerrainAlteration {
    void alterTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ);
}
