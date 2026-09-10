package com.techbucketdivision.mystcraft.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.techbucketdivision.mystcraft.block.BookReceptacleBlock;
import com.techbucketdivision.mystcraft.blockentity.BookReceptacleBlockEntity;
import com.techbucketdivision.mystcraft.client.render.ItemRenderHelper;
import com.techbucketdivision.mystcraft.client.render.LabelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Book Receptacle: the closed book rests on the outer face of the 6 px slab (facing {@code ROTATION}). */
public class BookReceptacleRenderer extends BookDisplayRenderer<BookReceptacleBlockEntity> {

    public static class ReceptacleState extends BookDisplayRenderer.State {
        public Direction facing = Direction.UP;
    }

    public BookReceptacleRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new ReceptacleState();
    }

    @Override
    public void extractRenderState(BookReceptacleBlockEntity be, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
        state.scale = 0.45f;
        BlockState bs = be.getBlockState();
        if (state instanceof ReceptacleState rs) {
            rs.facing = bs.hasProperty(BookReceptacleBlock.ROTATION) ? bs.getValue(BookReceptacleBlock.ROTATION) : Direction.UP;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Direction facing = state instanceof ReceptacleState rs ? rs.facing : Direction.UP;
        if (!state.item.isEmpty()) {
            poseStack.pushPose();
            // slab occupies the 6 px behind the face; the book lies on the slab surface, 10 px from the block centre
            float off = 0.5f - 6f / 16f + 0.03f;
            poseStack.translate(0.5 + facing.getStepX() * off, 0.5 + facing.getStepY() * off, 0.5 + facing.getStepZ() * off);
            poseStack.mulPose(facing.getRotation()); // Direction#getRotation() -> Quaternionf (API_CHEATSHEET L1b, verified); maps +Y onto the direction
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f)); // FIXED items face +Z: turn them to lie in the slab plane
            poseStack.scale(state.scale, state.scale, state.scale);
            ItemRenderHelper.submit(state.item, poseStack, collector, state.lightCoords);
            poseStack.popPose();
        }
        if (state.label != null) {
            poseStack.pushPose();
            poseStack.translate(0.5, 1.2, 0.5);
            LabelRenderer.submit(poseStack, collector, camera, state.label, state.lightCoords, state.distanceSq);
            poseStack.popPose();
        }
    }
}
