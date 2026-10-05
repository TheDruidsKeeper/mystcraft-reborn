package com.tbd.mystcraft.instability.decay;

import com.tbd.mystcraft.block.DecayType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** White decay: converts everything (air → 50, else 1); entity contact deals 1 magic damage (original spec §3.8). */
public final class WhiteDecay extends SpreadingDecay {
    public WhiteDecay() {
        super(DecayType.WHITE);
    }

    @Override
    protected int conversionDifficulty(ServerLevel level, BlockPos pos, BlockState target) {
        if (target.isAir()) return 50;
        return 1;
    }

    @Override
    public void onEntityContact(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel server) {
            entity.hurtServer(server, level.damageSources().magic(), 1f);
        }
    }
}
