package com.techbucketdivision.mystcraft.world.feature;

import com.techbucketdivision.mystcraft.api.symbol.logic.Populator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Surface Lakes" (1/4 per chunk, random y 0–255, only when no earlier populator returned true) and "Deep Lakes"
 * (1/8 per chunk, {@code y = rand(rand(248)+8)}, only if y < sea level or 1/10) — original spec §4.3.9. The lake shape
 * is the classic {@code WorldGenLakes} blob (port of {@code WorldGenLakesAdv}).
 */
public final class LakesPopulator implements Populator {
    private final BlockState fluid;
    private final boolean deep;

    public LakesPopulator(BlockState fluid, boolean deep) {
        this.fluid = fluid;
        this.deep = deep;
    }

    public BlockState fluid() {
        return fluid;
    }

    public boolean isDeep() {
        return deep;
    }

    @Override
    public boolean populate(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag) {
        if (flag) return false;
        int bx = chunkX << 4;
        int bz = chunkZ << 4;
        if (deep) {
            if (random.nextInt(8) == 0) {
                int x = bx + random.nextInt(16) + 8;
                int y = random.nextInt(random.nextInt(248) + 8);
                int z = bz + random.nextInt(16) + 8;
                if (y < level.getSeaLevel() || random.nextInt(10) == 0) {
                    generateLake(level, random, new BlockPos(x, y, z));
                }
            }
        } else if (random.nextInt(4) == 0) {
            int x = bx + random.nextInt(16) + 8;
            int y = random.nextInt(256);
            int z = bz + random.nextInt(16) + 8;
            generateLake(level, random, new BlockPos(x, y, z));
        }
        return false;
    }

    /** Classic lake blob: up to 4–7 overlapping ellipsoids in a 16×8×16 box; fails near foreign liquids. */
    public boolean generateLake(WorldGenLevel level, RandomSource rand, BlockPos origin) {
        BlockPos pos = origin.below(8);
        pos = pos.offset(-8, 0, -8);
        while (pos.getY() > 5 && level.isEmptyBlock(pos)) {
            pos = pos.below();
        }
        if (pos.getY() <= 4) return false;
        pos = pos.below(4);
        boolean[] shape = new boolean[2048];
        int blobs = rand.nextInt(4) + 4;
        for (int i = 0; i < blobs; ++i) {
            double d0 = rand.nextDouble() * 6.0D + 3.0D;
            double d1 = rand.nextDouble() * 4.0D + 2.0D;
            double d2 = rand.nextDouble() * 6.0D + 3.0D;
            double d3 = rand.nextDouble() * (16.0D - d0 - 2.0D) + 1.0D + d0 / 2.0D;
            double d4 = rand.nextDouble() * (8.0D - d1 - 4.0D) + 2.0D + d1 / 2.0D;
            double d5 = rand.nextDouble() * (16.0D - d2 - 2.0D) + 1.0D + d2 / 2.0D;
            for (int x = 1; x < 15; ++x) {
                for (int z = 1; z < 15; ++z) {
                    for (int y = 1; y < 7; ++y) {
                        double d6 = (x - d3) / (d0 / 2.0D);
                        double d7 = (y - d4) / (d1 / 2.0D);
                        double d8 = (z - d5) / (d2 / 2.0D);
                        if (d6 * d6 + d7 * d7 + d8 * d8 < 1.0D) {
                            shape[(x * 16 + z) * 8 + y] = true;
                        }
                    }
                }
            }
        }
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                for (int y = 0; y < 8; ++y) {
                    boolean edge = !shape[(x * 16 + z) * 8 + y]
                            && (x < 15 && shape[((x + 1) * 16 + z) * 8 + y]
                            || x > 0 && shape[((x - 1) * 16 + z) * 8 + y]
                            || z < 15 && shape[(x * 16 + z + 1) * 8 + y]
                            || z > 0 && shape[(x * 16 + (z - 1)) * 8 + y]
                            || y < 7 && shape[(x * 16 + z) * 8 + y + 1]
                            || y > 0 && shape[(x * 16 + z) * 8 + (y - 1)]);
                    if (edge) {
                        at.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                        BlockState state = level.getBlockState(at);
                        if (y >= 4 && state.liquid()) return false;
                        if (y < 4 && !state.isSolid() && state != fluid) return false;
                    }
                }
            }
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                for (int y = 0; y < 8; ++y) {
                    if (shape[(x * 16 + z) * 8 + y]) {
                        at.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                        level.setBlock(at, y >= 4 ? air : fluid, 2);
                    }
                }
            }
        }
        return true;
    }
}
