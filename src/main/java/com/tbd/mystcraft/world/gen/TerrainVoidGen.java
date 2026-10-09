package com.tbd.mystcraft.world.gen;

import com.tbd.mystcraft.api.symbol.logic.TerrainContext;
import com.tbd.mystcraft.api.symbol.logic.TerrainGenerator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Arrays;

/** "Void World" terrain (original spec §4.3.6): generates nothing. */
public final class TerrainVoidGen implements TerrainGenerator, HeightEstimator {
    public TerrainVoidGen() {}

    @Override
    public void generateTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {}

    @Override
    public BlockState[] sampleColumn(TerrainContext ctx, int blockX, int blockZ) {
        BlockState[] column = new BlockState[ctx.maxY() - ctx.minY() + 1];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        return column;
    }
}
