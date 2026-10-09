package com.tbd.mystcraft.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tbd.mystcraft.entity.ColoredLightningBolt;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;

/**
 * Coloured lightning: a port of the vanilla bolt geometry ({@code LightningBoltRenderer}) tinted with
 * {@link ColoredLightningBolt#getColor()} (0xRRGGBB) instead of the hard-coded blue-grey.
 */
public class ColoredLightningRenderer extends EntityRenderer<ColoredLightningBolt, ColoredLightningRenderer.State> {

    public static class State extends EntityRenderState {
        public long seed;
        public float r = 0.45f, g = 0.45f, b = 0.5f;
    }

    public ColoredLightningRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ColoredLightningBolt entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.seed = entity.getId() * 0x9E3779B97F4A7C15L; // stable per entity (LightningBolt#seed visibility unverified)
        int color = entity.getColor();
        state.r = ((color >> 16) & 0xFF) / 255f;
        state.g = ((color >> 8) & 0xFF) / 255f;
        state.b = (color & 0xFF) / 255f;
    }

    // NOTE: vanilla also overrides shouldRender(...) to skip frustum culling for tall bolts; the 26.1 signature is
    // unverified so the default culling (entity bounding box) is kept here.

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> render(pose, buffer, state));
    }

    private static void render(PoseStack.Pose pose, VertexConsumer buffer, State state) {
        float[] xs = new float[8];
        float[] zs = new float[8];
        float fx = 0f, fz = 0f;
        RandomSource random = RandomSource.create(state.seed);
        for (int i = 7; i >= 0; --i) {
            xs[i] = fx;
            zs[i] = fz;
            fx += random.nextInt(11) - 5;
            fz += random.nextInt(11) - 5;
        }
        for (int branch = 0; branch < 4; ++branch) {
            RandomSource r2 = RandomSource.create(state.seed);
            for (int k = 0; k < 3; ++k) {
                int top = 7;
                int bottom = 0;
                if (k > 0) top = 7 - k;
                if (k > 0) bottom = top - 2;
                float x = xs[top] - fx;
                float z = zs[top] - fz;
                for (int seg = top; seg >= bottom; --seg) {
                    float px = x;
                    float pz = z;
                    if (k == 0) {
                        x += r2.nextInt(11) - 5;
                        z += r2.nextInt(11) - 5;
                    } else {
                        x += r2.nextInt(31) - 15;
                        z += r2.nextInt(31) - 15;
                    }
                    float w1 = 0.1f + branch * 0.2f;
                    if (k == 0) w1 *= seg * 0.1f + 1f;
                    float w2 = 0.1f + branch * 0.2f;
                    if (k == 0) w2 *= (seg - 1) * 0.1f + 1f;
                    quad(pose, buffer, x, z, seg, px, pz, state, w1, w2, false, false, true, false);
                    quad(pose, buffer, x, z, seg, px, pz, state, w1, w2, true, false, true, true);
                    quad(pose, buffer, x, z, seg, px, pz, state, w1, w2, true, true, false, true);
                    quad(pose, buffer, x, z, seg, px, pz, state, w1, w2, false, true, false, false);
                }
            }
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer b, float x1, float z1, int y, float x2, float z2, State s,
                             float w1, float w2, boolean b1, boolean b2, boolean b3, boolean b4) {
        float a = 0.3f;
        b.addVertex(pose, x1 + (b1 ? w2 : -w2), y * 16f, z1 + (b2 ? w2 : -w2)).setColor(s.r, s.g, s.b, a);
        b.addVertex(pose, x2 + (b1 ? w1 : -w1), (y + 1) * 16f, z2 + (b2 ? w1 : -w1)).setColor(s.r, s.g, s.b, a);
        b.addVertex(pose, x2 + (b3 ? w1 : -w1), (y + 1) * 16f, z2 + (b4 ? w1 : -w1)).setColor(s.r, s.g, s.b, a);
        b.addVertex(pose, x1 + (b3 ? w2 : -w2), y * 16f, z1 + (b4 ? w2 : -w2)).setColor(s.r, s.g, s.b, a);
    }
}
