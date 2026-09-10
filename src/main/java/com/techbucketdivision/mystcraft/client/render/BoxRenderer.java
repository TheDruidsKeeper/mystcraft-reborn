package com.techbucketdivision.mystcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Draws axis-aligned textured boxes by submitting a block item's model (a unit cube centred at the origin in the
 * {@code FIXED} display context) with a non-uniform scale. Used as a robust stand-in for the original desk model until
 * a proper block/entity model is added (Phase 2).
 */
public final class BoxRenderer {
    private BoxRenderer() {}

    /** Resolves the cube model of {@code block} into {@code state}. */
    public static void extractBlock(ItemStackRenderState state, Block block, @Nullable Level level) {
        ItemRenderHelper.extract(state, new ItemStack(block), ItemDisplayContext.FIXED, level, 0);
    }

    /** Submits the extracted cube stretched over the local-space box (x0,y0,z0)-(x1,y1,z1). */
    public static void box(ItemStackRenderState cube, PoseStack poseStack, SubmitNodeCollector collector, int light,
                           float x0, float y0, float z0, float x1, float y1, float z1) {
        if (cube.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, (z0 + z1) * 0.5f);
        poseStack.scale(Math.max(0.001f, x1 - x0), Math.max(0.001f, y1 - y0), Math.max(0.001f, z1 - z0));
        ItemRenderHelper.submit(cube, poseStack, collector, light);
        poseStack.popPose();
    }
}
