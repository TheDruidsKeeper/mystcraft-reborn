package com.techbucketdivision.mystcraft.block;

import com.techbucketdivision.mystcraft.blockentity.BookBinderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Book Binder (REQUIREMENTS §3.3). Right-click opens {@code BookBinderMenu}; breaking drops the cover and pages. */
public class BookBinderBlock extends FacingEntityBlock {
    public BookBinderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BookBinderBlockEntity(pos, state);
    }
}
