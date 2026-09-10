package com.techbucketdivision.mystcraft.instability.decay;

import com.techbucketdivision.mystcraft.block.DecayType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Red decay: air → 20, else {@code max(1, explosionResistance)} (REQUIREMENTS §3.8). */
public final class RedDecay extends SpreadingDecay {
    public RedDecay() {
        super(DecayType.RED);
    }

    @Override
    protected int conversionDifficulty(ServerLevel level, BlockPos pos, BlockState target) {
        if (target.isAir()) return 20;
        float resist = clampHardness(target.getBlock().getExplosionResistance());
        return Math.max(1, (int) resist);
    }
}
