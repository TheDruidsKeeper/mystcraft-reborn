package com.techbucketdivision.mystcraft.instability.decay;

import com.techbucketdivision.mystcraft.block.DecayType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Purple decay: air → 8, decay → 5, liquid → 3, else {@code max(1, (int)(2 * hardness + explosionResistance)) * 10}
 * (REQUIREMENTS §3.8).
 */
public final class PurpleDecay extends SpreadingDecay {
    public PurpleDecay() {
        super(DecayType.PURPLE);
    }

    @Override
    protected int conversionDifficulty(ServerLevel level, BlockPos pos, BlockState target) {
        if (target.isAir()) return 8;
        if (isAnyDecay(target)) return 5;
        if (target.liquid()) return 3;
        float resist = clampHardness(target.getBlock().getExplosionResistance());
        float hardness = clampHardness(target.getDestroySpeed(level, pos)) * 2;
        return Math.max(1, (int) (hardness + resist)) * 10;
    }
}
