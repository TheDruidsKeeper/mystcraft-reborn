package com.techbucketdivision.mystcraft.block;

import com.techbucketdivision.mystcraft.blockentity.BookReceptacleBlockEntity;
import com.techbucketdivision.mystcraft.linking.PortalUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * Crystal (REQUIREMENTS §3.6): portal frame block. {@code SOURCE} points back toward the powering receptacle while
 * {@code ACTIVE}.
 */
public class CrystalBlock extends Block {
    public static final EnumProperty<Direction> SOURCE = EnumProperty.create("source", Direction.class);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public CrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(SOURCE, Direction.DOWN).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SOURCE, ACTIVE);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (!state.getValue(ACTIVE)) return;
        BookReceptacleBlockEntity receptacle = PortalUtils.getReceptacle(level, pos);
        if (receptacle == null || receptacle.getBook().isEmpty()) {
            level.setBlock(pos, defaultBlockState(), Block.UPDATE_CLIENTS);
            PortalUtils.shutdownPortal(level, pos);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(ACTIVE) ? 15 : 0;
    }
}
