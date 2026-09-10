package com.techbucketdivision.mystcraft.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.techbucketdivision.mystcraft.blockentity.StarFissureBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** Star Fissure: an end-portal style starfield drawn on the top and bottom faces of the thin block. */
public class StarFissureRenderer implements BlockEntityRenderer<StarFissureBlockEntity, BlockEntityRenderState> {

    private static final float TOP = 1.6f / 16f;

    public StarFissureRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public BlockEntityRenderState createRenderState() {
        return new BlockEntityRenderState();
    }

    @Override
    public void submit(BlockEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        collector.submitCustomGeometry(poseStack, RenderTypes.endPortal(), (pose, buffer) -> {
            // top face (counter-clockwise seen from above)
            quad(pose, buffer, 0, TOP, 0, 0, TOP, 1, 1, TOP, 1, 1, TOP, 0);
            // bottom face (reverse winding)
            quad(pose, buffer, 0, 0.001f, 0, 1, 0.001f, 0, 1, 0.001f, 1, 0, 0.001f, 1);
        });
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        // RenderTypes.endPortal() uses a position-only format: extra attributes are ignored by the buffer.
        buffer.addVertex(pose, x0, y0, z0);
        buffer.addVertex(pose, x1, y1, z1);
        buffer.addVertex(pose, x2, y2, z2);
        buffer.addVertex(pose, x3, y3, z3);
    }
}
