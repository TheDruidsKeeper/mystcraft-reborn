package com.techbucketdivision.mystcraft.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.techbucketdivision.mystcraft.entity.MeteorEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** Meteor: a tumbling cube drawn with the end-portal starfield shader, scaled by the entity scale (REQUIREMENTS §17). */
public class MeteorRenderer extends EntityRenderer<MeteorEntity, MeteorRenderer.State> {

    public static class State extends EntityRenderState {
        public float scale = 1f;
        public float spin;
    }

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MeteorEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.scale = Math.max(0.1f, entity.getScale());
        state.spin = (entity.tickCount + partialTick) * 7f;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        poseStack.pushPose();
        poseStack.translate(0.0, state.scale * 0.5, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.spin * 0.6f));
        float h = state.scale * 0.5f;
        collector.submitCustomGeometry(poseStack, RenderTypes.endPortal(), (pose, buffer) -> cube(pose, buffer, h));
        poseStack.popPose();
    }

    /** Cube of half-size {@code h} centred on the origin (position-only vertices; 6 quads, outward winding). */
    static void cube(PoseStack.Pose pose, VertexConsumer b, float h) {
        // -Y
        v(pose, b, -h, -h, -h); v(pose, b, h, -h, -h); v(pose, b, h, -h, h); v(pose, b, -h, -h, h);
        // +Y
        v(pose, b, -h, h, h); v(pose, b, h, h, h); v(pose, b, h, h, -h); v(pose, b, -h, h, -h);
        // -Z
        v(pose, b, -h, h, -h); v(pose, b, h, h, -h); v(pose, b, h, -h, -h); v(pose, b, -h, -h, -h);
        // +Z
        v(pose, b, -h, -h, h); v(pose, b, h, -h, h); v(pose, b, h, h, h); v(pose, b, -h, h, h);
        // -X
        v(pose, b, -h, -h, -h); v(pose, b, -h, -h, h); v(pose, b, -h, h, h); v(pose, b, -h, h, -h);
        // +X
        v(pose, b, h, h, -h); v(pose, b, h, h, h); v(pose, b, h, -h, h); v(pose, b, h, -h, -h);
    }

    private static void v(PoseStack.Pose pose, VertexConsumer b, float x, float y, float z) {
        b.addVertex(pose, x, y, z);
    }
}
