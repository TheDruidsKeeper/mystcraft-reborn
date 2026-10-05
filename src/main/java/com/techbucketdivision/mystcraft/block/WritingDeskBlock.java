package com.techbucketdivision.mystcraft.block;

import com.techbucketdivision.mystcraft.blockentity.WritingDeskBlockEntity;
import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import com.techbucketdivision.mystcraft.registry.ModItems;
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
 * Writing Desk (original spec §3.10). A desk is two base blocks (head + foot, the foot lies at
 * {@code head.relative(FACING)}) optionally covered by two {@code TOP} backboard blocks. Only the head block has the
 * block entity; every desk block opens the menu of the head. Rendered by a BER ({@link RenderShape#INVISIBLE}).
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

    /**
     * Places the two backboard ({@code TOP}) blocks above a desk whose head is at {@code headPos}. Used by the
     * backboard item.
     *
     * @return false if the desk is incomplete or the space above is occupied
     */
    public static boolean placeBackboard(Level level, BlockPos headPos) {
        BlockState head = level.getBlockState(headPos);
        if (!isHead(head)) return false;
        Direction facing = head.getValue(FACING);
        BlockPos footPos = headPos.relative(facing);
        BlockState foot = level.getBlockState(footPos);
        if (!isFoot(foot) || foot.getValue(TOP) || foot.getValue(FACING) != facing) return false;
        if (!level.getBlockState(headPos.above()).isAir() || !level.getBlockState(footPos.above()).isAir()) return false;
        level.setBlock(headPos.above(), head.setValue(TOP, true), Block.UPDATE_ALL);
        level.setBlock(footPos.above(), foot.setValue(TOP, true), Block.UPDATE_ALL);
        return true;
    }

    public static boolean hasBackboard(Level level, BlockPos headPos) {
        return isTop(level.getBlockState(headPos.above()));
    }

    // --- placement / shape -----------------------------------------------------------------------------------------

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos foot = context.getClickedPos().relative(facing);
        BlockState footState = context.getLevel().getBlockState(foot);
        if (!footState.canBeReplaced(context) && !isFoot(footState)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!isHead(state) || level.isClientSide()) return;
        Direction facing = state.getValue(FACING);
        BlockPos footPos = pos.relative(facing);
        BlockState existing = level.getBlockState(footPos);
        if (isFoot(existing) && existing.getValue(FACING) == facing) return; // the item already placed it
        if (existing.isAir() || existing.canBeReplaced()) {
            level.setBlock(footPos, state.setValue(FOOT, true), Block.UPDATE_ALL);
        } else {
            level.removeBlock(pos, false);
        }
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

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        Direction facing = state.getValue(FACING);
        if (state.getValue(TOP) && !state.getValue(FOOT)) {
            if (!isDesk(level.getBlockState(pos.below()))) {
                Block.popResource(level, pos, new ItemStack(ModItems.WRITING_DESK_BACKBOARD.get()));
                level.removeBlock(pos, false);
                return;
            }
        }
        if (state.getValue(FOOT)) {
            if (!isDesk(level.getBlockState(pos.relative(facing.getOpposite())))) {
                level.removeBlock(pos, false);
            }
        } else if (!isDesk(level.getBlockState(pos.relative(facing)))) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.hasInfiniteMaterials() && !state.getValue(TOP)) {
            BlockPos above = pos.above();
            if (isTop(level.getBlockState(above))) {
                level.removeBlock(above, false);
                Direction facing = state.getValue(FACING);
                BlockPos otherTop = state.getValue(FOOT) ? pos.relative(facing.getOpposite()).above() : pos.relative(facing).above();
                if (isTop(level.getBlockState(otherTop))) level.removeBlock(otherTop, false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide() && !player.hasInfiniteMaterials()) {
            Block.popResource(level, pos, dropFor(state, blockEntity instanceof WritingDeskBlockEntity desk && desk.isScholar()));
        }
    }

    private static ItemStack dropFor(BlockState state, boolean scholar) {
        if (state.getValue(TOP)) return new ItemStack(ModItems.WRITING_DESK_BACKBOARD.get());
        return new ItemStack(scholar ? ModItems.SCHOLARS_WRITING_DESK.get() : ModItems.WRITING_DESK.get());
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        boolean scholar = false;
        if (!state.getValue(TOP)) {
            Direction facing = state.getValue(FACING);
            BlockPos head = state.getValue(FOOT) ? pos.relative(facing.getOpposite()) : pos;
            scholar = level.getBlockEntity(head) instanceof WritingDeskBlockEntity desk && desk.isScholar();
        }
        return dropFor(state, scholar);
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
