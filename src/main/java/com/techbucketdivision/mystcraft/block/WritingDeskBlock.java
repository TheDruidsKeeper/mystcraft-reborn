package com.tbd.mystcraft.block;

import com.tbd.mystcraft.blockentity.WritingDeskBlockEntity;
import com.tbd.mystcraft.registry.ModBlockEntities;
import com.tbd.mystcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Writing Desk (original spec §3.10, Reborn: always with its backboard). A desk is four blocks: head + foot (the foot
 * lies at {@code head.relative(FACING)}) and the two {@code TOP} backboard blocks above them. Only the head block has
 * the block entity; every desk block opens the menu of the head; breaking any of the four removes the whole desk
 * (one desk item drops). Rendered by a BER ({@link RenderShape#INVISIBLE}).
 */
public class WritingDeskBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty FOOT = BooleanProperty.create("foot");

    public WritingDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(TOP, false).setValue(FOOT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TOP, FOOT);
    }

    // --- state helpers -------------------------------------------------------------------------------------------

    public static boolean isTop(BlockState state) {
        return state.getBlock() instanceof WritingDeskBlock && state.getValue(TOP);
    }

    public static boolean isFoot(BlockState state) {
        return state.getBlock() instanceof WritingDeskBlock && state.getValue(FOOT);
    }

    public static boolean isHead(BlockState state) {
        return state.getBlock() instanceof WritingDeskBlock && !state.getValue(TOP) && !state.getValue(FOOT);
    }

    private static boolean isDesk(BlockState state) {
        return state.getBlock() instanceof WritingDeskBlock;
    }

    /** Position of the head base block for any desk block. */
    public static BlockPos headPos(BlockPos pos, BlockState state) {
        BlockPos p = state.getValue(TOP) ? pos.below() : pos;
        return state.getValue(FOOT) ? p.relative(state.getValue(FACING).getOpposite()) : p;
    }

    /** Resolves the desk block entity from any of the four desk blocks. */
    public static @Nullable WritingDeskBlockEntity getBlockEntity(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isDesk(state)) return null;
        return level.getBlockEntity(headPos(pos, state)) instanceof WritingDeskBlockEntity be ? be : null;
    }

    /** The four positions of a desk whose head is at {@code head}: head, foot, head top, foot top. */
    public static BlockPos[] deskBlocks(BlockPos head, Direction facing) {
        BlockPos foot = head.relative(facing);
        return new BlockPos[] {head, foot, head.above(), foot.above()};
    }

    /** Whether all four spots of a desk at {@code head} facing {@code facing} are free (air or replaceable). */
    public static boolean canPlaceDesk(Level level, BlockPos head, Direction facing) {
        for (BlockPos pos : deskBlocks(head, facing)) {
            if (!level.isInsideBuildHeight(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced()) return false;
        }
        return true;
    }

    /**
     * Places a complete desk (head, foot and the two backboard blocks). The blocks are set without neighbour
     * notifications until the last one, because every desk block removes itself as soon as a sibling is missing.
     */
    public static void placeDesk(Level level, BlockPos head, Direction facing, Block desk) {
        BlockState base = desk.defaultBlockState().setValue(FACING, facing);
        BlockPos[] blocks = deskBlocks(head, facing);
        BlockState[] states = {base, base.setValue(FOOT, true), base.setValue(TOP, true), base.setValue(TOP, true).setValue(FOOT, true)};
        for (int i = 0; i < blocks.length; i++) {
            level.setBlock(blocks[i], states[i], i == blocks.length - 1 ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
        }
    }

    /** Whether every block of the desk that {@code state} at {@code pos} belongs to is in place. */
    private static boolean isComplete(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos head = headPos(pos, state);
        BlockPos[] blocks = deskBlocks(head, facing);
        boolean[][] flags = {{false, false}, {false, true}, {true, false}, {true, true}}; // TOP, FOOT
        for (int i = 0; i < blocks.length; i++) {
            BlockState other = level.getBlockState(blocks[i]);
            if (!isDesk(other) || other.getValue(FACING) != facing
                    || other.getValue(TOP) != flags[i][0] || other.getValue(FOOT) != flags[i][1]) return false;
        }
        return true;
    }

    // --- placement / shape -----------------------------------------------------------------------------------------

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        if (!canPlaceDesk(context.getLevel(), context.getClickedPos(), facing)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    /** Placement through a plain block item / command places the head: complete the other three here. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!isHead(state) || level.isClientSide() || isComplete(level, pos, state)) return;
        placeDesk(level, pos, state.getValue(FACING), this);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(TOP)) return Shapes.block();
        double minX = 0, minZ = 0, maxX = 16, maxZ = 16;
        switch (state.getValue(FACING).get2DDataValue()) {
            case 0 -> minX = 8;   // south
            case 1 -> minZ = 8;   // west
            case 2 -> maxX = 8;   // north
            default -> maxZ = 8;  // east
        }
        return Block.box(minX, 0, minZ, maxX, 12, maxZ);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // --- structure integrity ---------------------------------------------------------------------------------------

    /** A desk block without one of its three siblings removes itself (no drops): the whole desk goes together. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (!isComplete(level, pos, state)) level.removeBlock(pos, false);
    }

    /** The desk item drops once, from the block the player broke, while the head's block entity still exists. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.hasInfiniteMaterials()) {
            Block.popResource(level, pos, deskItem(level, pos, state));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static ItemStack deskItem(LevelReader level, BlockPos pos, BlockState state) {
        boolean scholar = level.getBlockEntity(headPos(pos, state)) instanceof WritingDeskBlockEntity desk && desk.isScholar();
        return new ItemStack(scholar ? ModItems.SCHOLARS_WRITING_DESK.get() : ModItems.WRITING_DESK.get());
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return deskItem(level, pos, state);
    }

    // --- block entity ------------------------------------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isHead(state) ? new WritingDeskBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !isHead(state) || type != ModBlockEntities.WRITING_DESK.get()) return null;
        return (lvl, pos, st, be) -> ((WritingDeskBlockEntity) be).serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos head = headPos(pos, state);
        FacingEntityBlock.openMenu(level, head, player);
        return InteractionResult.CONSUME;
    }
}
