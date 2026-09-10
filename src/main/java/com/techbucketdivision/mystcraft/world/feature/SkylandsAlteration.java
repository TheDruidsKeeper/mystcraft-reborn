package com.techbucketdivision.mystcraft.world.feature;

import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainAlteration;
import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainContext;
import com.techbucketdivision.mystcraft.world.gen.ChunkBlocks;
import com.techbucketdivision.mystcraft.world.gen.LegacyNoise;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Random;

/**
 * "Skylands" (REQUIREMENTS §4.3.7): every block at {@code y ≤ 76 + noise(x,z)} (7-octave noise) is removed and liquids
 * above the cut are removed too. The original applied this as a primer filter during terrain generation; here it is a
 * post-pass over the freshly generated terrain (same result, and the extended space below y=0 is cleared as well).
 */
public final class SkylandsAlteration implements TerrainAlteration {
    private static final int BASE_CUTOFF = 76;

    private final LegacyNoise noise;

    public SkylandsAlteration(long seed) {
        this.noise = new LegacyNoise(new Random(seed), 7);
    }

    @Override
    public void alterTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {
        ChunkBlocks blocks = new ChunkBlocks(chunk);
        double[] skyNoise = noise.generate2d(null, chunkX * 16, chunkZ * 16, 16, 16, 1.0D, 1.0D);
        BlockState air = Blocks.AIR.defaultBlockState();
        int minY = blocks.minY();
        int maxY = blocks.maxY();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int cutoff = BASE_CUTOFF + (int) skyNoise[x << 4 | z];
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = blocks.get(x, y, z);
                    if (state.isAir()) continue;
                    if (y <= cutoff || state.liquid()) {
                        blocks.set(x, y, z, air);
                    }
                }
            }
        }
    }
}
