package com.techbucketdivision.mystcraft.block;

import com.techbucketdivision.mystcraft.blockentity.WardedDoorBlockEntity;
import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Facility Warded Door (FACILITY_PLAN.md §2.2): an unbreakable block that vanishes once its lock is solved. */
public class WardedDoorBlock extends Block implements EntityBlock {
    public WardedDoorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WardedDoorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.WARDED_DOOR.get()) return null;
        return (lvl, pos, st, be) -> ((WardedDoorBlockEntity) be).serverTick();
    }
}
