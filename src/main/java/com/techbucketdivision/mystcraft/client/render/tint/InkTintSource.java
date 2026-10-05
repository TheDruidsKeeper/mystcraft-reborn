package com.techbucketdivision.mystcraft.client.render.tint;

import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.fluid.FluidTintSource;

/**
 * Black ink tint (original spec §17: 0x191919) for the fluid renderer, the placed {@code black_ink} block model and
 * the dynamic bucket item model. Opaque alpha is required: 26.1 multiplies the whole ARGB vertex colour
 * ({@code ARGB.multiply} / {@code ARGB.scaleRGB}), so a bare {@code 0xRRGGBB} renders fully transparent.
 */
public final class InkTintSource implements FluidTintSource {
    public static final int INK = 0xFF191919;

    @Override
    public int color(FluidState state) {
        return INK;
    }
}
