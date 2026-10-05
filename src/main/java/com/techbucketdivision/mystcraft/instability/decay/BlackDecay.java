package com.techbucketdivision.mystcraft.instability.decay;

import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.block.DecayType;
import com.techbucketdivision.mystcraft.config.BalanceConfig;
import com.techbucketdivision.mystcraft.entity.MystFallingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Black decay (original spec §3.8): 1/10 per pulse corrupts the four horizontal neighbours, clears the block below and
 * drops itself as a falling block; else 1/5 only corrupts the neighbours. Liquids are removed, other blocks become
 * black decay. Placement next to black decay above/below drops the column.
 */
public final class BlackDecay extends DecayHandler {
    public BlackDecay() {
        super(DecayType.BLACK);
    }

    @Override
    protected void pulse(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (random.nextInt(10) == 0) {
            decay(level, pos);
        } else if (random.nextInt(5) == 0) {
            for (Direction dir : Direction.Plane.HORIZONTAL) corrupt(level, pos.relative(dir));
        }
    }

    @Override
    public boolean onPlace(ServerLevel level, BlockPos pos, BlockState state) {
        if (super.onPlace(level, pos, state)) return true;
        AgeController age = AgeControllers.server(level);
        boolean instability = BalanceConfig.INSTABILITY_ENABLED.get() && (age == null || age.ageData().instabilityEnabled());
        if (!instability) return false;
        if (level.getBlockState(pos.below()) == state()) {
            MystFallingBlockEntity.fall(level, pos, state);
            return true;
        }
        if (level.getBlockState(pos.above()) == state()) {
            MystFallingBlockEntity.fall(level, pos.above(), state());
        }
        return false;
    }

    private void decay(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) corrupt(level, pos.relative(dir));
        if (!level.isOutsideBuildHeight(pos.below())) {
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        MystFallingBlockEntity.fall(level, pos, level.getBlockState(pos));
    }

    private void corrupt(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return;
        BlockState target = level.getBlockState(pos);
        if (target.liquid()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (!target.isAir() && target != state()) {
            level.setBlock(pos, state(), Block.UPDATE_ALL);
        }
    }
}
