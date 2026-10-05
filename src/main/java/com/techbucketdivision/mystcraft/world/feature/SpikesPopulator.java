package com.tbd.mystcraft.world.feature;

import com.tbd.mystcraft.api.symbol.logic.Populator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * "Spikes" (original spec §4.3.8): 1/18 per chunk (skipped when {@code flag}); at the surface, if the whole base circle
 * (width 1–4, r² ≤ w²+1) is supported, columns of height {@code rand(rand(6..37)+1)+1} of the structure block.
 */
public final class SpikesPopulator implements Populator {
    private final BlockState state;

    public SpikesPopulator(BlockState state) {
        this.state = state;
    }

    public BlockState state() {
        return state;
    }

    @Override
    public boolean populate(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag) {
        if (flag || random.nextInt(18) != 0) return false;
        int x = (chunkX << 4) + random.nextInt(16);
        int z = (chunkZ << 4) + random.nextInt(16);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        generate(level, random, new BlockPos(x, y, z));
        return false;
    }

    private boolean generate(WorldGenLevel level, RandomSource rand, BlockPos base) {
        if (!level.isEmptyBlock(base)) return false;
        int height = rand.nextInt(32) + 6;
        int width = rand.nextInt(4) + 1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = base.getX() - width; x <= base.getX() + width; ++x) {
            for (int z = base.getZ() - width; z <= base.getZ() + width; ++z) {
                int dx = x - base.getX();
                int dz = z - base.getZ();
                at.set(x, base.getY() - 1, z);
                if (dx * dx + dz * dz <= width * width + 1 && level.isEmptyBlock(at)) return false;
            }
        }
        int maxWorldY = level.getMinY() + level.getHeight();
        for (int x = base.getX() - width; x <= base.getX() + width; ++x) {
            for (int z = base.getZ() - width; z <= base.getZ() + width; ++z) {
                int maxHeight = base.getY() + rand.nextInt(rand.nextInt(height) + 1) + 1;
                for (int y = base.getY(); y < maxHeight && y < maxWorldY; ++y) {
                    int dx = x - base.getX();
                    int dz = z - base.getZ();
                    if (dx * dx + dz * dz <= width * width + 1) {
                        at.set(x, y, z);
                        level.setBlock(at, state, 2);
                    }
                }
            }
        }
        return true;
    }
}
