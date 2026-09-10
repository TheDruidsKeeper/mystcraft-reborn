package com.techbucketdivision.mystcraft.client.render.tint;

import com.techbucketdivision.mystcraft.blockentity.BookReceptacleBlockEntity;
import com.techbucketdivision.mystcraft.linking.PortalUtils;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Link portal blocks are tinted with the colour of the book in the receptacle powering them (REQUIREMENTS §7.6). */
public final class PortalTintSource implements BlockTintSource {
    @Override
    public int color(BlockState state) {
        return 0xFFFFFF;
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        try {
            BookReceptacleBlockEntity receptacle = PortalUtils.getReceptacle(level, pos);
            return receptacle == null ? 0xFFFFFF : receptacle.getPortalColor();
        } catch (RuntimeException e) {
            return 0xFFFFFF;
        }
    }
}
