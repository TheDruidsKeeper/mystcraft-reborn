package com.tbd.mystcraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Flowing fluids replace any block that does not block motion ({@code FlowingFluid.canHoldAnyFluid}) unless it is a
 * {@link LiquidBlockContainer} that refuses them; vanilla special-cases its own portals. Non-colliding Mystcraft
 * blocks that must survive water / lava / ink reaching them (star fissure, link portal) implement this.
 */
public interface FluidProof extends LiquidBlockContainer {
    @Override
    default boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid type) {
        return false;
    }

    @Override
    default boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }
}
