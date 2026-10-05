package com.tbd.mystcraft.block;

import com.tbd.mystcraft.blockentity.FacilityLockBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Sequence Dial: one glyph of a Facility sequence lock; right-click cycles {@link #GLYPH}. */
public class SequenceDialBlock extends FacilityLockBlock {
    /** 0–7, one of the eight glyph faces (the same faces clue blocks show). */
    public static final IntegerProperty GLYPH = IntegerProperty.create("glyph", 0, 7);

    public SequenceDialBlock(Properties properties) {
        super(FacilityLockBlockEntity.Kind.SEQUENCE, false, properties);
        registerDefaultState(getStateDefinition().any().setValue(GLYPH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLYPH);
    }
}
