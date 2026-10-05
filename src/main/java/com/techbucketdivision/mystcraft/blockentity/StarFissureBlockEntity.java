package com.techbucketdivision.mystcraft.blockentity;

import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Star Fissure block entity (original spec §3.11): render-only anchor for the end-portal style BER. */
public class StarFissureBlockEntity extends BlockEntity {
    public StarFissureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STAR_FISSURE.get(), pos, state);
    }
}
