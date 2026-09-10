package com.techbucketdivision.mystcraft.item;

import com.techbucketdivision.mystcraft.block.WritingDeskBlock;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
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
 * Writing Desk item (REQUIREMENTS §2.8). {@code backboard == false} places the two-block desk (head + foot);
 * {@code backboard == true} extends an existing desk upwards with the two backboard blocks.
 * <p>
 * The foot block lies in the desk's facing direction: offsets (0,0,+1)/(−1,0,0)/(0,0,−1)/(+1,0,0) for horizontal
 * indices 0..3 are exactly {@code Direction#getUnitVec3i()} of SOUTH/WEST/NORTH/EAST.
 * <p>
 * Block state properties come from package C2 per IMPLEMENTATION_CONTRACTS: {@code WritingDeskBlock.FACING}
 * (DirectionProperty-like {@code EnumProperty<Direction>}), {@code TOP}, {@code FOOT} (BooleanProperty).
 */
public class WritingDeskItem extends Item {
    private final boolean backboard;

    public WritingDeskItem(boolean backboard, Item.Properties properties) {
        super(properties);
        this.backboard = backboard;
    }

    public boolean isBackboard() {
        return backboard;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide()) return InteractionResult.PASS;
        if (player == null) return InteractionResult.FAIL;
        ItemStack held = context.getItemInHand();
        if (held.isEmpty()) return InteractionResult.FAIL;
        return backboard
                ? extendDesk(held, player, level, context.getClickedPos(), context.getClickedFace())
                : placeDesk(held, player, level, context.getClickedPos(), context.getClickedFace());
    }

    private InteractionResult placeDesk(ItemStack stack, Player player, Level level, BlockPos pos, Direction face) {
        if (isReplaceable(level, pos)) {
            pos = pos.below();
            face = Direction.UP;
        }
        if (face != Direction.UP) return InteractionResult.PASS;
        Direction facing = player.getDirection().getClockWise();
        BlockPos head = pos.above();
        BlockPos foot = head.relative(facing);
        if (!player.mayUseItemAt(head, face, stack) || !player.mayUseItemAt(foot, face, stack)) {
            return InteractionResult.PASS;
        }
        if (!isReplaceable(level, head) || !isReplaceable(level, foot)) return InteractionResult.PASS;

        BlockState base = ModBlocks.WRITING_DESK.get().defaultBlockState()
                .setValue(WritingDeskBlock.FACING, facing)
                .setValue(WritingDeskBlock.TOP, false)
                .setValue(WritingDeskBlock.FOOT, false);
        level.setBlockAndUpdate(head, base);
        if (level.getBlockState(head).is(ModBlocks.WRITING_DESK.get())) {
            level.setBlockAndUpdate(foot, base.setValue(WritingDeskBlock.FOOT, true));
        }
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }

    private InteractionResult extendDesk(ItemStack stack, Player player, Level level, BlockPos pos, Direction face) {
        BlockState at = level.getBlockState(pos);
        if (!at.is(ModBlocks.WRITING_DESK.get())) return InteractionResult.PASS;
        if (at.getValue(WritingDeskBlock.TOP)) return InteractionResult.PASS;
        Direction facing = at.getValue(WritingDeskBlock.FACING);
        BlockPos head = at.getValue(WritingDeskBlock.FOOT) ? pos.relative(facing.getOpposite()) : pos;

        BlockPos up = head.above();
        BlockPos upFoot = up.relative(facing);
        if (!player.mayUseItemAt(up, face, stack) || !player.mayUseItemAt(upFoot, face, stack)) {
            return InteractionResult.PASS;
        }
        if (!isReplaceable(level, up) || !isReplaceable(level, upFoot)) return InteractionResult.PASS;

        BlockState top = ModBlocks.WRITING_DESK.get().defaultBlockState()
                .setValue(WritingDeskBlock.FACING, facing)
                .setValue(WritingDeskBlock.TOP, true)
                .setValue(WritingDeskBlock.FOOT, false);
        level.setBlockAndUpdate(up, top);
        if (level.getBlockState(up).is(ModBlocks.WRITING_DESK.get())) {
            level.setBlockAndUpdate(upFoot, top.setValue(WritingDeskBlock.FOOT, true));
        }
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean isReplaceable(Level level, BlockPos pos) {
        if (!level.isInsideBuildHeight(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }
}
