package com.techbucketdivision.mystcraft.world.gen;

import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainContext;
import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainGenerator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * "Flat World" terrain (REQUIREMENTS §4.3.6): bedrock at the bottom, terrain block below the average ground level
 * (default 64), sea block up to and including sea level.
 */
public final class TerrainFlatGen implements TerrainGenerator, HeightEstimator {
    private final long seed;
    private final BlockState terrain;
    private final BlockState sea;

    public TerrainFlatGen(long seed, BlockState terrain, BlockState sea) {
        this.seed = seed;
        this.terrain = terrain;
        this.sea = sea;
    }

    @Override
    public void generateTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {
        ChunkBlocks blocks = new ChunkBlocks(chunk);
        int plane = ctx.averageGroundLevel();
        int seaLevel = ctx.seaLevel();
        int minY = blocks.minY();
        int top = Math.min(blocks.maxY(), Math.max(plane, seaLevel));
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        for (int y = minY; y <= top; ++y) {
            BlockState state;
            if (y == minY) {
                state = bedrock;
            } else if (y < plane) {
                state = terrain;
            } else if (y <= seaLevel) {
                state = sea;
            } else {
                continue;
            }
            if (state.isAir()) continue;
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    blocks.set(x, y, z, state);
                }
            }
        }
    }

    public long seed() {
        return seed;
    }

    @Override
    public BlockState[] sampleColumn(TerrainContext ctx, int blockX, int blockZ) {
        int minY = ctx.minY();
        int maxY = ctx.maxY();
        int plane = ctx.averageGroundLevel();
        int seaLevel = ctx.seaLevel();
        BlockState[] column = new BlockState[maxY - minY + 1];
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = minY; y <= maxY; y++) {
            BlockState s;
            if (y == minY) s = Blocks.BEDROCK.defaultBlockState();
            else if (y < plane) s = terrain;
            else if (y <= seaLevel) s = sea;
            else s = air;
            column[y - minY] = s;
        }
        return column;
    }
}
