package com.techbucketdivision.mystcraft.world.feature;

import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainAlteration;
import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainContext;
import com.techbucketdivision.mystcraft.world.gen.ChunkBlocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Random;

/**
 * "Caves" (rate 15, size 40, air) and "Tendrils" (rate 15, size 18, structure block) — original spec §4.3.7. Port of
 * {@code MapGenCavesMyst}: per chunk in range 8, {@code nodes = rand(rand(rand(size)+1)+1)} only when
 * {@code rand(rate)==0}; 25% of nodes start with a large room.
 */
public final class CavesAlteration extends AbstractTunnelGen implements TerrainAlteration {
    private final int rate;
    private final int size;

    public CavesAlteration(long seed, int rate, int size, BlockState fill) {
        super(seed, fill, 8);
        this.rate = Math.max(1, rate);
        this.size = Math.max(1, size);
    }

    @Override
    public void alterTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {
        generate(new ChunkBlocks(chunk), chunkX, chunkZ);
    }

    @Override
    protected void recursiveGenerate(Random rand, int x, int z, int chunkX, int chunkZ, ChunkBlocks blocks) {
        int maxNodes = rand.nextInt(rand.nextInt(rand.nextInt(size) + 1) + 1);
        if (rand.nextInt(rate) != 0) maxNodes = 0;
        for (int j = 0; j < maxNodes; ++j) {
            double d = x * 16 + rand.nextInt(16);
            double d1 = rand.nextInt(rand.nextInt(120) + 8);
            double d2 = z * 16 + rand.nextInt(16);
            int k = 1;
            if (rand.nextInt(4) == 0) {
                generateLargeNode(rand, chunkX, chunkZ, blocks, d, d1, d2, 0.5D, Shape.CAVE);
                k += rand.nextInt(4);
            }
            for (int l = 0; l < k; ++l) {
                float f = rand.nextFloat() * (float) Math.PI * 2.0F;
                float f1 = ((rand.nextFloat() - 0.5F) * 2.0F) / 8F;
                float f2 = rand.nextFloat() * 2.0F + rand.nextFloat();
                if (rand.nextInt(10) == 0) {
                    f2 *= rand.nextFloat() * rand.nextFloat() * 3F + 1.0F;
                }
                generateNode(rand.nextLong(), chunkX, chunkZ, blocks, null, d, d1, d2, f2, f, f1, 0, 0, 1.0D, Shape.CAVE, 2.0F);
            }
        }
    }
}
