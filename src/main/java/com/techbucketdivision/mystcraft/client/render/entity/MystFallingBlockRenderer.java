package com.techbucketdivision.mystcraft.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.techbucketdivision.mystcraft.client.render.ItemRenderHelper;
import com.techbucketdivision.mystcraft.entity.MystFallingBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Falling block (instability crumble / meteors). Phase 1 renders the block's item model (a unit cube for full blocks),
 * which avoids the unverified {@code MovingBlockRenderState} API; blocks without an item render nothing.
 * TODO Phase 2: submitMovingBlock / submitBlockModel with the real block state model.
 */
public class MystFallingBlockRenderer extends EntityRenderer<MystFallingBlockEntity, MystFallingBlockRenderer.State> {

    public static class State extends EntityRenderState {
        public final ItemStackRenderState block = new ItemStackRenderState();
    }

    public MystFallingBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MystFallingBlockEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.shadowRadius = 0.5f; // EntityRenderState#shadowRadius (API_CHEATSHEET K, verified field)
        BlockState bs = entity.getBlockState();
        ItemStack stack = bs.isAir() ? ItemStack.EMPTY : new ItemStack(bs.getBlock());
        ItemRenderHelper.extract(state.block, stack, ItemDisplayContext.FIXED, entity.level(), entity.getId());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        if (state.block.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.0, 0.5, 0.0); // block items are centred cubes; the entity origin is its bottom
        ItemRenderHelper.submit(state.block, poseStack, collector, state.lightCoords);
        poseStack.popPose();
    }
}
