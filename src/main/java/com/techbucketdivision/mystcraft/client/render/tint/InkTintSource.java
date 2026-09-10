package com.techbucketdivision.mystcraft.client.render.tint;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;

/** Black ink fluid block tint (REQUIREMENTS §17: 0x191919). */
public final class InkTintSource implements BlockTintSource {
    public static final int INK = 0x191919;

    @Override
    public int color(BlockState state) {
        return INK;
    }
}
