package com.techbucketdivision.mystcraft.world.feature;

import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainAlteration;
import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainContext;
import com.techbucketdivision.mystcraft.world.gen.ChunkBlocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Random;

/**
 * "Spheres" (REQUIREMENTS §4.3.8): 5% per chunk (range 8), one node at {@code y = 32 + rand(rand(192)+1)}, radius
 * scalar 1–5, made of the structure block (default cobblestone).
 */
public final class SpheresAlteration extends AbstractTunnelGen implements TerrainAlteration {

    public SpheresAlteration(long seed, BlockState state) {
        super(seed, state, 8);
    }

    @Override
    public void alterTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {
        generate(new ChunkBlocks(chunk), chunkX, chunkZ);
    }

    @Override
    protected void recursiveGenerate(Random rand, int x, int z, int chunkX, int chunkZ, ChunkBlocks blocks) {
        float roll = rand.nextFloat();
        if (roll > 0.05F) return;
        double dx = x * 16 + rand.nextInt(16);
        double dy = rand.nextInt(rand.nextInt(192) + 1) + 32;
        double dz = z * 16 + rand.nextInt(16);
        generateNode(rand.nextLong(), chunkX, chunkZ, blocks, null, dx, dy, dz, 1.0F + rand.nextFloat() * 4F, 0.0F, 0.0F,
                -1, -1, 1.0D, Shape.SPHERE, 2.0F);
    }
}
