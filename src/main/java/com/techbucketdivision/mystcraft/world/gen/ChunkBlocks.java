package com.techbucketdivision.mystcraft.world.gen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Fast, bounds-checked block access on a chunk being generated. Writes go straight to the chunk sections (like
 * vanilla's noise generator does) and therefore do not update heightmaps; {@code AgeChunkGenerator} primes the
 * heightmaps once after all generators, alterations and finalizers ran.
 */
public final class ChunkBlocks {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ChunkAccess chunk;
    private final int minY;
    private final int maxY;
    private final LevelChunkSection[] sections;
    private final int minSectionY;

    public ChunkBlocks(ChunkAccess chunk) {
        this.chunk = chunk;
        this.minY = chunk.getMinY();
        this.maxY = chunk.getMaxY();
        this.sections = chunk.getSections();
        this.minSectionY = chunk.getMinSectionY();
    }

    public ChunkAccess chunk() {
        return chunk;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public boolean inRange(int y) {
        return y >= minY && y <= maxY;
    }

    /** Local x/z (0..15), absolute y. Returns air outside the build height. */
    public BlockState get(int x, int y, int z) {
        if (y < minY || y > maxY) return AIR;
        LevelChunkSection section = sections[(y >> 4) - minSectionY];
        return section.getBlockState(x & 15, y & 15, z & 15);
    }

    /** Local x/z (0..15), absolute y. Silently ignores positions outside the build height. */
    public void set(int x, int y, int z, BlockState state) {
        if (y < minY || y > maxY) return;
        LevelChunkSection section = sections[(y >> 4) - minSectionY];
        section.setBlockState(x & 15, y & 15, z & 15, state, false);
    }

    public boolean isAir(int x, int y, int z) {
        return get(x, y, z).isAir();
    }

    /** Highest non-air y in the column (local x/z), or {@code minY - 1} when empty. */
    public int topNonAir(int x, int z) {
        for (int y = maxY; y >= minY; --y) {
            if (!get(x, y, z).isAir()) return y;
        }
        return minY - 1;
    }
}
