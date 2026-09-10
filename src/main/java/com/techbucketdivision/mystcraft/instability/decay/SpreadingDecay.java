package com.techbucketdivision.mystcraft.instability.decay;

import com.techbucketdivision.mystcraft.block.DecayType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Base for red/blue/purple/white: each pulse tries to convert all six neighbours. */
public abstract class SpreadingDecay extends DecayHandler {
    protected SpreadingDecay(DecayType type) {
        super(type);
    }

    @Override
    protected void pulse(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        for (Direction dir : Direction.values()) {
            spread(level, pos.relative(dir), random);
        }
    }

    protected void spread(ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isOutsideBuildHeight(pos)) return;
        BlockState target = level.getBlockState(pos);
        if (target == state()) return;
        int difficulty = Math.max(1, conversionDifficulty(level, pos, target));
        if (random.nextInt(difficulty) == 0) {
            level.setBlock(pos, state(), Block.UPDATE_ALL);
        }
    }

    /** {@code random.nextInt(difficulty) == 0} converts the neighbour. */
    protected abstract int conversionDifficulty(ServerLevel level, BlockPos pos, BlockState target);

    protected static float clampHardness(float f) {
        if (f < 0) f = 1000f;
        return Math.min(f, 1000f);
    }
}
