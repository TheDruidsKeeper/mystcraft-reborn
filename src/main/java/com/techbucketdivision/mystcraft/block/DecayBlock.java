package com.tbd.mystcraft.block;

import com.tbd.mystcraft.age.AgeManager;
import com.tbd.mystcraft.instability.decay.DecayHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decay (original spec §3.8). One block per {@link DecayType}; all behaviour is delegated to the instability package's
 * {@code DecayHandlers.get(type)}. Placed outside a Mystcraft Age the block simply vanishes.
 */
public class DecayBlock extends Block {
    private final DecayType type;

    public DecayBlock(DecayType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public DecayType type() {
        return type;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DecayHandlers.get(type).randomTick(level, pos, state, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DecayHandlers.get(type).randomTick(level, pos, state, random);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!AgeManager.isAge(level.dimension())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        DecayHandlers.get(type).onPlace(serverLevel, pos, state);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel serverLevel) {
            DecayHandlers.get(type).onEntityContact(serverLevel, pos, state, entity);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (level instanceof ServerLevel serverLevel) {
            DecayHandlers.get(type).onEntityContact(serverLevel, pos, state, entity);
        }
    }
}
