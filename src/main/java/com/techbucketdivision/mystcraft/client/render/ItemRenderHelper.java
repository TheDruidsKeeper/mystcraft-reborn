package com.techbucketdivision.mystcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Renders {@link ItemStack}s from block-entity / entity renderers through the 26.1 item model resolver
 * (the reliable path recommended by API_CHEATSHEET §K instead of a hand-written book model).
 */
public final class ItemRenderHelper {
    private ItemRenderHelper() {}

    /** Resolves the model layers of {@code stack} into {@code state} (call from {@code extractRenderState}). */
    public static void extract(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, @Nullable Level level, int seed) {
        state.clear();
        if (stack.isEmpty()) return;
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, seed);
    }

    /** Submits a previously extracted state (call from {@code submit}). */
    public static void submit(ItemStackRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        if (state.isEmpty()) return;
        state.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
    }
}
