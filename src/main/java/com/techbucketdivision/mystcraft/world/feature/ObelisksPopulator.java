package com.techbucketdivision.mystcraft.world.feature;

import com.techbucketdivision.mystcraft.api.symbol.logic.Populator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * "Obelisks" (REQUIREMENTS §4.3.9): 1/128 per chunk; a 4×4 base dug down up to 5 layers until supported, then a
 * 2×2×12 pillar of the structure block (default obsidian).
 */
public final class ObelisksPopulator implements Populator {
    private final BlockState state;

    public ObelisksPopulator(BlockState state) {
        this.state = state;
    }

    public BlockState state() {
        return state;
    }

    @Override
    public boolean populate(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag) {
        if (random.nextInt(128) != 0) return false;
        int x = (chunkX << 4) + random.nextInt(16);
        int z = (chunkZ << 4) + random.nextInt(16);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        generate(level, new BlockPos(x, y, z));
        return false;
    }

    private void generate(WorldGenLevel level, BlockPos base) {
        if (base.getY() <= level.getMinY()) return;
        int height = 12;
        int width = 4;
        int maxDeep = 5;
        base = base.above();
        boolean foundBase = false;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int y = base.getY(); y > base.getY() - maxDeep && !foundBase; --y) {
            foundBase = true;
            for (int z = 0; z < width; ++z) {
                for (int x = 0; x < width; ++x) {
                    at.set(base.getX() + x, y, base.getZ() + z);
                    level.setBlock(at, state, 2);
                    below.set(at.getX(), at.getY() - 1, at.getZ());
                    BlockState under = level.getBlockState(below);
                    if (under.isAir() || under.is(Blocks.SNOW) || under.liquid()) {
                        foundBase = false;
                    }
                }
            }
        }
        base = base.offset(1, 0, 1);
        width = 2;
        for (int y = 0; y < height; ++y) {
            for (int z = 0; z < width; ++z) {
                for (int x = 0; x < width; ++x) {
                    at.set(base.getX() + x, base.getY() + y, base.getZ() + z);
                    level.setBlock(at, state, 2);
                }
            }
        }
    }
}
