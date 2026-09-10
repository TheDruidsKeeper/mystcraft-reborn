package com.techbucketdivision.mystcraft.world.feature;

import com.techbucketdivision.mystcraft.api.symbol.logic.Populator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * "Crystalline Formations" (REQUIREMENTS §4.3.9): 1/15 per chunk (skipped when {@code flag}); 1–3 lines starting two
 * blocks below the surface at an angle of 15–155°, length 6–12, each step drawing a 7-block "plus" of the crystal
 * block (default {@code mystcraft:crystal}).
 */
public final class CrystalFormationPopulator implements Populator {
    private static final byte[] OTHER_COORD_PAIRS = {2, 0, 0, 1, 2, 1};
    private static final int RATE = 15;

    private final BlockState crystal;

    public CrystalFormationPopulator(BlockState crystal) {
        this.crystal = crystal;
    }

    public BlockState crystal() {
        return crystal;
    }

    @Override
    public boolean populate(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag) {
        if (flag || random.nextInt(RATE) != 0) return false;
        // Lines reach ±13 blocks; keep the origin near the chunk centre so writes stay within the decoration region.
        int x = (chunkX << 4) + random.nextInt(8) + 4;
        int z = (chunkZ << 4) + random.nextInt(8) + 4;
        BlockPos start = findStart(level, x, z);
        if (start == null) return false;
        int count = random.nextInt(3) + 1;
        for (int i = 0; i < count; ++i) {
            generateLine(level, random, start);
        }
        return false;
    }

    /** Climbs from the bottom of the classic space to the first air, then through non-air non-liquid, and steps 2 down. */
    private static @Nullable BlockPos findStart(WorldGenLevel level, int x, int z) {
        int maxY = level.getMinY() + level.getHeight() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        BlockState state = level.getBlockState(pos);
        while (!state.isAir()) {
            pos.move(Direction.UP);
            if (pos.getY() > maxY) return null;
            state = level.getBlockState(pos);
        }
        while (!state.liquid() && !state.isAir()) {
            pos.move(Direction.UP);
            if (pos.getY() > maxY) return null;
            state = level.getBlockState(pos);
        }
        pos.move(Direction.DOWN, 2);
        return level.getBlockState(pos).isAir() ? null : pos.immutable();
    }

    private void generateLine(WorldGenLevel level, RandomSource rand, BlockPos origin) {
        float angle = (rand.nextFloat() * 140.0F + 15.0F);
        int length = rand.nextInt(7) + 6;
        int[] end = {
                origin.getX() + (int) (length * Math.cos(angle * Math.PI / 180)),
                origin.getY() + (int) (length * Math.sin(angle * Math.PI / 180)),
                origin.getZ() + rand.nextInt(7) - 3
        };
        int[] start = {origin.getX(), origin.getY(), origin.getZ()};
        placeBlockLine(level, start, end);
    }

    private void placeBlockLine(WorldGenLevel level, int[] start, int[] end) {
        int[] delta = {0, 0, 0};
        int major = 0;
        for (byte b = 0; b < 3; ++b) {
            delta[b] = end[b] - start[b];
            if (Math.abs(delta[b]) > Math.abs(delta[major])) major = b;
        }
        if (delta[major] == 0) return;
        byte a1 = OTHER_COORD_PAIRS[major];
        byte a2 = OTHER_COORD_PAIRS[major + 3];
        byte step = (byte) (delta[major] > 0 ? 1 : -1);
        double d = (double) delta[a1] / (double) delta[major];
        double d1 = (double) delta[a2] / (double) delta[major];
        int[] p = {0, 0, 0};
        int k = 0;
        for (int l = delta[major] + step; k != l; k += step) {
            p[major] = Mth.floor((start[major] + k) + 0.5D);
            p[a1] = Mth.floor(start[a1] + k * d + 0.5D);
            p[a2] = Mth.floor(start[a2] + k * d1 + 0.5D);
            drawPlus(level, new BlockPos(p[0], p[1], p[2]));
        }
    }

    private void drawPlus(WorldGenLevel level, BlockPos pos) {
        setBlock(level, pos);
        for (Direction face : Direction.values()) {
            setBlock(level, pos.relative(face));
        }
    }

    private void setBlock(WorldGenLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinY() || pos.getY() >= level.getMinY() + level.getHeight()) return;
        if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
            level.setBlock(pos, crystal, 3);
        }
    }
}
