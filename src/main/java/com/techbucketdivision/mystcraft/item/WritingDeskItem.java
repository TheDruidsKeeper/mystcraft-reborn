package com.tbd.mystcraft.item;

import com.tbd.mystcraft.block.WritingDeskBlock;
import com.tbd.mystcraft.blockentity.WritingDeskBlockEntity;
import com.tbd.mystcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Writing Desk item (original spec §2.8, Reborn: the desk always comes with its backboard). Places the complete
 * four-block desk (head + foot and the two backboard blocks above) on the clicked top face; the foot lies to the
 * placer's right, so the desk faces them. The Scholar's variant marks the block entity scholar (creative only).
 */
public class WritingDeskItem extends Item {
    private final boolean scholar;

    /** @param scholar places a Scholar's desk: the block entity offers every registered symbol (creative) */
    public WritingDeskItem(boolean scholar, Item.Properties properties) {
        super(properties);
        this.scholar = scholar;
    }

    public boolean isScholar() {
        return scholar;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide()) return InteractionResult.PASS;
        if (player == null) return InteractionResult.FAIL;
        ItemStack held = context.getItemInHand();
        if (held.isEmpty()) return InteractionResult.FAIL;
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        if (isReplaceable(level, pos)) {
            pos = pos.below();
            face = Direction.UP;
        }
        if (face != Direction.UP) return InteractionResult.PASS;
        Direction facing = player.getDirection().getClockWise();
        BlockPos head = pos.above();
        for (BlockPos spot : WritingDeskBlock.deskBlocks(head, facing)) {
            if (!player.mayUseItemAt(spot, face, held)) return InteractionResult.PASS;
        }
        if (!WritingDeskBlock.canPlaceDesk(level, head, facing)) return InteractionResult.PASS;

        WritingDeskBlock.placeDesk(level, head, facing, ModBlocks.WRITING_DESK.get());
        if (scholar && level.getBlockEntity(head) instanceof WritingDeskBlockEntity desk) desk.setScholar(true);
        if (!player.hasInfiniteMaterials()) held.shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean isReplaceable(Level level, BlockPos pos) {
        if (!level.isInsideBuildHeight(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }
}
