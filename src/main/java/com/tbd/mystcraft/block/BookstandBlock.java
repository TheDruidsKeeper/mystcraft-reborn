package com.tbd.mystcraft.block;

import com.tbd.mystcraft.blockentity.BookDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Bookstand (original spec §3.4): 8 rotation steps of 45°, accepts linking items only, shape
 * (0.125,0,0.125)-(0.875,0.75,0.875).
 */
public class BookstandBlock extends BookDisplayBlock {
    /** 0–7, 45° each (placer yaw). */
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 7);

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);
    private static final VoxelShape INTERACTION_SHAPE = Shapes.or(
            Block.box(5.6, 0, 5.6, 10.4, 3.2, 10.4),
            Block.box(7.2, 1.6, 7.2, 8.8, 8, 8.8),
            Block.box(2.4, 6.4, 2.4, 13.6, 11.2, 13.6));

    public BookstandBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        int rot = Mth.floor(context.getRotation() * 8.0F / 360.0F + 0.5) & 7;
        return defaultBlockState().setValue(ROTATION, rot);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof BookDisplayBlockEntity be) {
            be.setYaw(state.getValue(ROTATION) * 45);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) { // getInteractionShape(BlockState, BlockGetter, BlockPos) is protected (not in the -public javadoc); BlockState.getInteractionShape(BlockGetter, BlockPos) is the verified public wrapper that delegates here.
        return INTERACTION_SHAPE;
    }
}
