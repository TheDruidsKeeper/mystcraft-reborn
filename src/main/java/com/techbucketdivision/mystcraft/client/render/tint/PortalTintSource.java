package com.techbucketdivision.mystcraft.client.render.tint;

import com.techbucketdivision.mystcraft.blockentity.BookReceptacleBlockEntity;
import com.techbucketdivision.mystcraft.linking.PortalUtils;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Link portal blocks are tinted with the colour of the book in the receptacle powering them (original spec §7.6). */
public final class PortalTintSource implements BlockTintSource {
    private static final int WHITE = 0xFFFFFFFF;

    @Override
    public int color(BlockState state) {
        return WHITE;
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        try {
            BookReceptacleBlockEntity receptacle = PortalUtils.getReceptacle(level, pos);
            // getPortalColor() is 0xRRGGBB (original packing); force opaque alpha, see InkTintSource.
            return receptacle == null ? WHITE : (receptacle.getPortalColor() | 0xFF000000);
        } catch (RuntimeException e) {
            return WHITE;
        }
    }
}
