package com.tbd.mystcraft.api.symbol.logic;

import net.minecraft.world.level.chunk.ChunkAccess;

/** Fills the raw terrain of a chunk (stone/sea/bedrock). Exactly one per Age. */
public interface TerrainGenerator {
    /**
     * @param ctx    the Age being generated (biomes, sea level, ground level, seed)
     * @param chunk  target chunk (proto chunk); write with {@code chunk.setBlockState(pos, state, 0)}
     * @param chunkX chunk x
     * @param chunkZ chunk z
     */
    void generateTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ);
}
