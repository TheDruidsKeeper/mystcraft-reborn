package com.techbucketdivision.mystcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.techbucketdivision.mystcraft.client.ClientPayloadHandlers;
import com.techbucketdivision.mystcraft.config.ClientConfig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Floating book-name labels above stands, lecterns, receptacles and dropped books (original spec §17, 25 blocks). */
public final class LabelRenderer {
    private LabelRenderer() {}

    public static final double MAX_DISTANCE_SQ = 25.0 * 25.0;

    /** Whether labels are enabled by both the client option and the server. */
    public static boolean enabled() {
        return ClientConfig.RENDER_LABELS.get() && ClientPayloadHandlers.serverLabelsAllowed;
    }

    /** Distance² from the camera to a world position. */
    public static double distanceSq(CameraRenderState camera, double x, double y, double z) {
        Vec3 pos = camera.pos; // CameraRenderState#pos (Vec3) — API_NOTES K LevelRenderState: field seen in the LevelRenderer patch
        double dx = pos.x - x, dy = pos.y - y, dz = pos.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Submits a camera-facing label; the pose stack must already be translated to the label anchor.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, @Nullable String text, int light, double distSq) {
        if (text == null || text.isEmpty() || distSq > MAX_DISTANCE_SQ) return;
        // nameTagAttachment is @Nullable; null anchors the label at the current pose (vanilla EntityRenderer pattern)
        collector.submitNameTag(poseStack, null, 0, Component.literal(text), false, light, distSq, camera);
    }
}
