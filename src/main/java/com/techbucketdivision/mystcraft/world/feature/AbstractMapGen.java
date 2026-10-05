package com.tbd.mystcraft.world.feature;

import com.tbd.mystcraft.world.gen.ChunkBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Random;

/**
 * Port of the original {@code MapGenAdvanced}: a vanilla-style "map gen" that, for every chunk within {@code range},
 * seeds a random from the Age seed and lets the subclass carve/place features that may cross into the chunk being
 * generated. The classic 0..255 block space is used; nothing is ever written below y=1 or above y=255, so the
 * extended world below y=0 stays solid.
 *
 * <p>All state is per call (the random is local), which makes one instance safe across generation threads.
 */
public abstract class AbstractMapGen {
    protected static final int LAYERS = 256;

    protected final int range;
    protected final long seed;
    protected final @Nullable BlockState state;

    protected AbstractMapGen(long seed, @Nullable BlockState state, int range) {
        this.seed = seed;
        this.state = state;
        this.range = range;
    }

    public long seed() {
        return seed;
    }

    public void generate(ChunkBlocks blocks, int chunkX, int chunkZ) {
        Random rand = new Random(seed);
        long xseed = rand.nextLong();
        long zseed = rand.nextLong();
        for (int x = chunkX - range; x <= chunkX + range; ++x) {
            for (int z = chunkZ - range; z <= chunkZ + range; ++z) {
                rand.setSeed((x * xseed) ^ (z * zseed) ^ seed);
                recursiveGenerate(rand, x, z, chunkX, chunkZ, blocks);
            }
        }
    }

    /** Called once per chunk in range with a random seeded for that chunk; must only write into {@code blocks}. */
    protected abstract void recursiveGenerate(Random rand, int x, int z, int chunkX, int chunkZ, ChunkBlocks blocks);

    protected boolean placeBlock(ChunkBlocks blocks, int x, int y, int z) {
        return placeBlock(blocks, x, y, z, state);
    }

    /** Never replaces bedrock; a non-solid block never replaces a liquid. */
    protected boolean placeBlock(ChunkBlocks blocks, int x, int y, int z, @Nullable BlockState newState) {
        if (newState == null || y < 1 || y >= LAYERS) return false;
        BlockState current = blocks.get(x, y, z);
        if (current.is(Blocks.BEDROCK)) return false;
        if (!newState.isSolid() && current.liquid()) return false;
        blocks.set(x, y, z, newState);
        return true;
    }
}
