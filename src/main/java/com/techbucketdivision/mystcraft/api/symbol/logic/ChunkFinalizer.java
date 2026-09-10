package com.techbucketdivision.mystcraft.api.symbol.logic;

import net.minecraft.world.level.chunk.ChunkAccess;

/** Runs after terrain + alterations (e.g. floating islands rewrite column biomes). */
public interface ChunkFinalizer {
    void finalizeChunk(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ);
}
