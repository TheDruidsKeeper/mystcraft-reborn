package com.techbucketdivision.mystcraft.block;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.blockentity.BookReceptacleBlockEntity;
import com.techbucketdivision.mystcraft.linking.PortalUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Link Portal (REQUIREMENTS §3.7): the "field" block grown between crystals by {@link PortalUtils}. Entities touching
 * it are linked with the book in the powering receptacle. Colour is provided client-side from
 * {@link BookReceptacleBlockEntity#getPortalColor()} of {@link PortalUtils#getReceptacle}.
 */
public class LinkPortalBlock extends Block {
    public static final EnumProperty<Direction> SOURCE = EnumProperty.create("source", Direction.class);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final VoxelShape CORE = Block.box(4, 4, 4, 12, 12, 12);

    public LinkPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(SOURCE, Direction.DOWN).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SOURCE, ACTIVE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    /** Portal blocks merge into one translucent volume: faces shared with another portal block are not drawn. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return neighborState.is(this) || super.skipRendering(state, neighborState, direction);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** Visual box: the central 0.25–0.75 cube, extended to touch any adjacent crystal / portal block. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double minX = 4, minY = 4, minZ = 4, maxX = 12, maxY = 12, maxZ = 12;
        if (touches(level, pos, Direction.WEST)) minX = 0;
        if (touches(level, pos, Direction.EAST)) maxX = 16;
        if (touches(level, pos, Direction.DOWN)) minY = 0;
        if (touches(level, pos, Direction.UP)) maxY = 16;
        if (touches(level, pos, Direction.NORTH)) minZ = 0;
        if (touches(level, pos, Direction.SOUTH)) maxZ = 16;
        if (minX == 4 && minY == 4 && minZ == 4 && maxX == 12 && maxY == 12 && maxZ == 12) return CORE;
        return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    private static boolean touches(BlockGetter level, BlockPos pos, Direction dir) {
        return PortalUtils.isValidLinkPortalBlock(level.getBlockState(pos.relative(dir))) > 0;
    }

    /**
     * Render helper for models / BERs: the axis whose four orthogonal neighbours are all crystal/portal, if exactly one
     * such axis exists (the original {@code hasface}/{@code renderface}).
     */
    public static Direction.@Nullable Axis renderAxis(BlockGetter level, BlockPos pos) {
        Direction.Axis found = null;
        for (Direction.Axis axis : Direction.Axis.values()) {
            boolean valid = true;
            for (Direction d : Direction.values()) {
                if (d.getAxis() == axis) continue;
                if (!touches(level, pos, d)) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                if (found != null) return null;
                found = axis;
            }
        }
        return found;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        PortalUtils.validatePortal(level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        PortalUtils.validatePortal(level, pos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level.isClientSide()) return;
        BookReceptacleBlockEntity receptacle = PortalUtils.getReceptacle(level, pos);
        if (receptacle == null || receptacle.getBook().isEmpty()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        ItemStack book = receptacle.getBook();
        if (book.getItem() instanceof ItemBehaviours.PortalActivator activator) {
            activator.onPortalCollision(book, level, entity, pos);
            receptacle.setChanged(); // a Descriptive Book binds to its Age on first portal use
        }
    }
}
