package com.techbucketdivision.mystcraft.block;

import com.techbucketdivision.mystcraft.blockentity.BookDisplayBlockEntity;
import com.techbucketdivision.mystcraft.menu.BookMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Base for Bookstand and Lectern (REQUIREMENTS §3.4). Right-click: no book + acceptable held item → insert one; book
 * present + sneaking with empty hand → take it; otherwise open the book GUI. Comparator output 15 when filled.
 */
public abstract class BookDisplayBlock extends Block implements EntityBlock {
    protected BookDisplayBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BookDisplayBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return interact(level, pos, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return interact(level, pos, player, InteractionHand.MAIN_HAND);
    }

    protected InteractionResult interact(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BookDisplayBlockEntity be)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (be.getBook().isEmpty()) {
            if (!held.isEmpty() && be.accepts(held)) {
                ItemStack one = held.copyWithCount(1);
                held.shrink(1);
                player.setItemInHand(hand, held);
                be.setBook(one);
                return InteractionResult.CONSUME;
            }
        } else if (player.isShiftKeyDown() && held.isEmpty()) {
            ItemStack book = be.getBook();
            be.setBook(ItemStack.EMPTY);
            player.setItemInHand(hand, book);
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer sp) {
            BookMenu.openForBlock(sp, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof BookDisplayBlockEntity be && !be.getBook().isEmpty() ? 15 : 0;
    }
}
