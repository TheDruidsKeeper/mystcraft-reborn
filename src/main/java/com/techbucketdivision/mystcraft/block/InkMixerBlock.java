package com.tbd.mystcraft.block;

import com.tbd.mystcraft.blockentity.InkMixerBlockEntity;
import com.tbd.mystcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Ink Mixer (original spec §3.2). Non-opaque cube; right-click opens {@code InkMixerMenu}. */
public class InkMixerBlock extends FacingEntityBlock {
    public InkMixerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InkMixerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.INK_MIXER.get()) return null;
        return (lvl, pos, st, be) -> ((InkMixerBlockEntity) be).serverTick();
    }
}
