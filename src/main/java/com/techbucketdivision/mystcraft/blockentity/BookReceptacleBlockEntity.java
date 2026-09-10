package com.techbucketdivision.mystcraft.blockentity;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.linking.PortalUtils;
import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Book Receptacle (REQUIREMENTS §3.5): holds a portal-activator book; any change shuts the portal down and, when a
 * book is present, fires it again.
 */
public class BookReceptacleBlockEntity extends BookDisplayBlockEntity {
    public BookReceptacleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOOK_RECEPTACLE.get(), pos, state);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemBehaviours.PortalActivator;
    }

    @Override
    protected void onBookChanged() {
        super.onBookChanged();
        Level level = getLevel();
        if (level == null || level.isClientSide()) return;
        PortalUtils.shutdownPortal(level, getBlockPos());
        if (!getBook().isEmpty()) {
            PortalUtils.firePortal(level, getBlockPos());
        }
    }

    /** 0xRRGGBB tint of the portal powered by this receptacle (white when empty). */
    public int getPortalColor() {
        ItemStack book = getBook();
        Level level = getLevel();
        if (!book.isEmpty() && level != null && book.getItem() instanceof ItemBehaviours.PortalActivator activator) {
            return activator.getPortalColor(book, level);
        }
        return 0xFFFFFF;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            PortalUtils.shutdownPortal(level, pos);
        }
        super.preRemoveSideEffects(pos, state);
    }
}
