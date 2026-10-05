package com.tbd.mystcraft.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tbd.mystcraft.client.render.ItemRenderHelper;
import com.tbd.mystcraft.client.render.LabelRenderer;
import com.tbd.mystcraft.entity.LinkbookEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** Dropped linking book: the book item lying flat, slightly raised while hurt, with an optional name label. */
public class LinkbookRenderer extends EntityRenderer<LinkbookEntity, LinkbookRenderer.State> {

    public static class State extends EntityRenderState {
        public final ItemStackRenderState book = new ItemStackRenderState();
        public float yRot;
        public float hurt;
    }

    public LinkbookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LinkbookEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        ItemRenderHelper.extract(state.book, entity.getBook(), ItemDisplayContext.FIXED, entity.level(), entity.getId());
        state.yRot = entity.getYRot();
        state.hurt = entity.hurtTime() > 0 ? entity.hurtTime() / 10f : 0f;
        if (LabelRenderer.enabled() && state.distanceToCameraSq <= LabelRenderer.MAX_DISTANCE_SQ) {
            String name = entity.getAgeName();
            state.nameTag = name == null || name.isEmpty() ? null : Component.literal(name);
            state.nameTagAttachment = new Vec3(0, entity.getBbHeight() + 0.2, 0);
        } else {
            state.nameTag = null;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        if (state.book.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.0, 0.05 + state.hurt * 0.1, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(90f)); // lie flat
        poseStack.scale(0.6f, 0.6f, 0.6f);
        ItemRenderHelper.submit(state.book, poseStack, collector, state.lightCoords);
        poseStack.popPose();
    }
}
