package com.tbd.mystcraft.world.gen;

import com.tbd.mystcraft.api.symbol.logic.TerrainContext;
import com.tbd.mystcraft.api.symbol.logic.TerrainGenerator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Port of the original {@code TerrainGeneratorBase}: a 5×17×5 density field per chunk (4×8×4 interpolation over the
 * classic 0..127 noise column) that is expanded into blocks. The original 0..255 block space is kept as-is so sea level
 * stays 63; the new space below y=0 is filled with the terrain block down to bedrock at the bottom of the world.
 *
 * <p>Thread safety: noise generators are immutable; every per-call buffer is local, so one instance can serve all
 * chunk-generation worker threads.
 */
public abstract class AbstractLegacyTerrainGen implements TerrainGenerator, HeightEstimator {
    protected static final int LEGACY_HEIGHT = 128;
    private static final int XZ_STEP = 4;
    private static final int Y_STEP = 8;
    private static final int FIELD_CACHE_SIZE = 64;

    /** Small LRU of density fields used by {@link #sampleColumn} (structure placement queries). */
    private final Map<Long, double[]> fieldCache = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, double[]> eldest) {
            return size() > FIELD_CACHE_SIZE;
        }
    };

    protected final long seed;
    protected final BlockState terrain;
    protected final BlockState sea;
    /** Emulates the original's random bedrock floor (y ≤ rand(5)) with terrain block, and places real bedrock at the bottom. */
    protected final boolean genBedrock;
    /** Fill the space below y=0 with terrain block. */
    protected final boolean fillBelowZero;

    protected AbstractLegacyTerrainGen(long seed, BlockState terrain, BlockState sea, boolean genBedrock, boolean fillBelowZero) {
        this.seed = seed;
        this.terrain = terrain;
        this.sea = sea;
        this.genBedrock = genBedrock;
        this.fillBelowZero = fillBelowZero;
    }

    /** Per-chunk random with a stable seed (the original reused one {@code Random} for bedrock; we make it reproducible). */
    protected Random chunkRandom(int chunkX, int chunkZ) {
        return new Random(seed ^ (chunkX * 0x4f9939f508L + chunkZ * 0x1ef1565bd5L));
    }

    /**
     * Fills the density field. {@code out} is caller-owned (allocate when null). Layout
     * {@code out[(x * sizeZ + z) * sizeY + y]}; positive values are solid.
     */
    protected abstract double[] initializeNoiseField(TerrainContext ctx, double[] out, int subchunkX, int subchunkY, int subchunkZ,
                                                     int sizeX, int sizeY, int sizeZ);

    @Override
    public void generateTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {
        ChunkBlocks blocks = new ChunkBlocks(chunk);
        int width = 16 / XZ_STEP;              // 4
        int height = LEGACY_HEIGHT / Y_STEP;   // 16
        int sizeX = width + 1;                 // 5
        int sizeY = height + 1;                // 17
        int sizeZ = width + 1;                 // 5
        int seaLevel = ctx.seaLevel();
        double yStepFactor = 0.125D;
        double xzStepFactor = 0.25D;
        double[] field = initializeNoiseField(ctx, null, chunkX * width, 0, chunkZ * width, sizeX, sizeY, sizeZ);
        Random bedrockRand = chunkRandom(chunkX, chunkZ);
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();

        for (int largeY = 0; largeY < height; ++largeY) {
            for (int largeZ = 0; largeZ < width; ++largeZ) {
                for (int largeX = 0; largeX < width; ++largeX) {
                    double x0z0y0 = field[((largeX) * sizeZ + (largeZ)) * sizeY + (largeY)];
                    double x0z1y0 = field[((largeX) * sizeZ + (largeZ + 1)) * sizeY + (largeY)];
                    double x1z0y0 = field[((largeX + 1) * sizeZ + (largeZ)) * sizeY + (largeY)];
                    double x1z1y0 = field[((largeX + 1) * sizeZ + (largeZ + 1)) * sizeY + (largeY)];
                    double x0z0yd = (field[((largeX) * sizeZ + (largeZ)) * sizeY + (largeY + 1)] - x0z0y0) * yStepFactor;
                    double x0z1yd = (field[((largeX) * sizeZ + (largeZ + 1)) * sizeY + (largeY + 1)] - x0z1y0) * yStepFactor;
                    double x1z0yd = (field[((largeX + 1) * sizeZ + (largeZ)) * sizeY + (largeY + 1)] - x1z0y0) * yStepFactor;
                    double x1z1yd = (field[((largeX + 1) * sizeZ + (largeZ + 1)) * sizeY + (largeY + 1)] - x1z1y0) * yStepFactor;
                    double x0z0yc = x0z0y0;
                    double x0z1yc = x0z1y0;
                    double x1z0yc = x1z0y0;
                    double x1z1yc = x1z1y0;

                    int y = largeY * Y_STEP;
                    for (int subY = 0; subY < Y_STEP; ++subY) {
                        double x0zcyc = x0z0yc;
                        double x1zcyc = x1z0yc;
                        double x0zdyc = (x0z1yc - x0z0yc) * xzStepFactor;
                        double x1zdyc = (x1z1yc - x1z0yc) * xzStepFactor;

                        int z = largeZ * XZ_STEP;
                        for (int subZ = 0; subZ < XZ_STEP; ++subZ) {
                            double xczcyc = x0zcyc;
                            double xdzcyc = (x1zcyc - x0zcyc) * xzStepFactor;

                            int x = largeX * XZ_STEP;
                            for (int subX = 0; subX < XZ_STEP; ++subX) {
                                BlockState block = null;
                                if (genBedrock && y <= bedrockRand.nextInt(5)) {
                                    block = terrain;
                                } else if (xczcyc > 0.0D) {
                                    block = terrain;
                                } else if (y < seaLevel) {
                                    block = sea;
                                }
                                if (block != null && !block.isAir()) {
                                    blocks.set(x, y, z, block);
                                }
                                xczcyc += xdzcyc;
                                ++x;
                            }
                            x0zcyc += x0zdyc;
                            x1zcyc += x1zdyc;
                            ++z;
                        }
                        x0z0yc += x0z0yd;
                        x0z1yc += x0z1yd;
                        x1z0yc += x1z0yd;
                        x1z1yc += x1z1yd;
                        ++y;
                    }
                }
            }
        }

        // Extended world below the classic space.
        int minY = blocks.minY();
        if (fillBelowZero && minY < 0) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    int bedrockTop = genBedrock ? minY + bedrockRand.nextInt(5) : minY - 1;
                    for (int y = minY; y < 0; ++y) {
                        blocks.set(x, y, z, y <= bedrockTop ? bedrock : terrain);
                    }
                }
            }
        }
    }

    // --- HeightEstimator -----------------------------------------------------------------------------------------

    private double[] fieldFor(TerrainContext ctx, int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        synchronized (fieldCache) {
            double[] f = fieldCache.get(key);
            if (f != null) return f;
        }
        int width = 16 / XZ_STEP;
        double[] f = initializeNoiseField(ctx, null, chunkX * width, 0, chunkZ * width, width + 1, LEGACY_HEIGHT / Y_STEP + 1, width + 1);
        synchronized (fieldCache) {
            fieldCache.putIfAbsent(key, f);
        }
        return f;
    }

    @Override
    public BlockState[] sampleColumn(TerrainContext ctx, int blockX, int blockZ) {
        int minY = ctx.minY();
        int maxY = ctx.maxY();
        BlockState[] column = new BlockState[maxY - minY + 1];
        BlockState air = Blocks.AIR.defaultBlockState();
        java.util.Arrays.fill(column, air);
        double[] field = fieldFor(ctx, blockX >> 4, blockZ >> 4);
        int sizeY = LEGACY_HEIGHT / Y_STEP + 1;
        int sizeZ = 16 / XZ_STEP + 1;
        int lx = blockX & 15;
        int lz = blockZ & 15;
        int largeX = lx / XZ_STEP;
        int largeZ = lz / XZ_STEP;
        double fx = (lx % XZ_STEP) / (double) XZ_STEP;
        double fz = (lz % XZ_STEP) / (double) XZ_STEP;
        int seaLevel = ctx.seaLevel();
        for (int y = 0; y < LEGACY_HEIGHT && y <= maxY; y++) {
            int largeY = y / Y_STEP;
            double fy = (y % Y_STEP) / (double) Y_STEP;
            double c000 = field[((largeX) * sizeZ + (largeZ)) * sizeY + (largeY)];
            double c001 = field[((largeX) * sizeZ + (largeZ)) * sizeY + (largeY + 1)];
            double c010 = field[((largeX) * sizeZ + (largeZ + 1)) * sizeY + (largeY)];
            double c011 = field[((largeX) * sizeZ + (largeZ + 1)) * sizeY + (largeY + 1)];
            double c100 = field[((largeX + 1) * sizeZ + (largeZ)) * sizeY + (largeY)];
            double c101 = field[((largeX + 1) * sizeZ + (largeZ)) * sizeY + (largeY + 1)];
            double c110 = field[((largeX + 1) * sizeZ + (largeZ + 1)) * sizeY + (largeY)];
            double c111 = field[((largeX + 1) * sizeZ + (largeZ + 1)) * sizeY + (largeY + 1)];
            double x0z0 = c000 + (c001 - c000) * fy;
            double x0z1 = c010 + (c011 - c010) * fy;
            double x1z0 = c100 + (c101 - c100) * fy;
            double x1z1 = c110 + (c111 - c110) * fy;
            double x0 = x0z0 + (x0z1 - x0z0) * fz;
            double x1 = x1z0 + (x1z1 - x1z0) * fz;
            double density = x0 + (x1 - x0) * fx;
            BlockState state;
            if (density > 0.0D || (genBedrock && y <= 2)) {
                state = terrain;
            } else if (y < seaLevel) {
                state = sea;
            } else {
                state = air;
            }
            column[y - minY] = state;
        }
        if (fillBelowZero) {
            for (int y = minY; y < 0; y++) {
                column[y - minY] = (genBedrock && y == minY) ? Blocks.BEDROCK.defaultBlockState() : terrain;
            }
        }
        return column;
    }
}
