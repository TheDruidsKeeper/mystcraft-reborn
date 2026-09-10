package com.techbucketdivision.mystcraft.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.techbucketdivision.mystcraft.block.WritingDeskBlock;
import com.techbucketdivision.mystcraft.blockentity.BookUtil;
import com.techbucketdivision.mystcraft.blockentity.WritingDeskBlockEntity;
import com.techbucketdivision.mystcraft.client.render.BoxRenderer;
import com.techbucketdivision.mystcraft.client.render.ItemRenderHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Writing Desk renderer. The desk blocks are {@code RenderShape.INVISIBLE}, so this renderer (attached to the head
 * block) draws the whole desk: table top + legs across head and foot, optional backboard, the paper stack and the
 * target item lying open on the head. Geometry is built from stretched block cubes (see {@link BoxRenderer}); the
 * original {@code ModelWritingDesk}/{@code desk.png} port is a Phase 2 task.
 */
public class WritingDeskRenderer implements BlockEntityRenderer<WritingDeskBlockEntity, WritingDeskRenderer.State> {

    public static class State extends BlockEntityRenderState {
        public final ItemStackRenderState wood = new ItemStackRenderState();
        public final ItemStackRenderState paper = new ItemStackRenderState();
        public final ItemStackRenderState target = new ItemStackRenderState();
        public float yRot;
        public boolean backboard;
        public int paperCount;
        public boolean targetIsBook;
    }

    public WritingDeskRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WritingDeskBlockEntity be, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        BlockState bs = be.getBlockState();
        Direction facing = bs.hasProperty(WritingDeskBlock.FACING) ? bs.getValue(WritingDeskBlock.FACING) : Direction.NORTH;
        state.yRot = facing.toYRot();
        state.backboard = be.hasBackboard();
        state.paperCount = be.getPaperCount();
        BoxRenderer.extractBlock(state.wood, Blocks.DARK_OAK_PLANKS, be.getLevel());
        BoxRenderer.extractBlock(state.paper, Blocks.WHITE_CONCRETE, be.getLevel());
        ItemStack target = be.getDisplayItem();
        state.targetIsBook = BookUtil.isLinkingItem(target);
        ItemRenderHelper.extract(state.target, target, ItemDisplayContext.FIXED, be.getLevel(), 0);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        poseStack.pushPose();
        // Local frame: head block at (0..1, 0..1, 0..1), foot block toward +Z; rotate so +Z maps onto FACING.
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
        poseStack.translate(-0.5, 0.0, -0.5);

        // table top over both blocks
        BoxRenderer.box(state.wood, poseStack, collector, light, 0.0f, 0.625f, 0.0f, 1.0f, 0.875f, 2.0f);
        // legs
        float leg = 0.125f;
        BoxRenderer.box(state.wood, poseStack, collector, light, 0.0f, 0.0f, 0.0f, leg, 0.625f, leg);
        BoxRenderer.box(state.wood, poseStack, collector, light, 1.0f - leg, 0.0f, 0.0f, 1.0f, 0.625f, leg);
        BoxRenderer.box(state.wood, poseStack, collector, light, 0.0f, 0.0f, 2.0f - leg, leg, 0.625f, 2.0f);
        BoxRenderer.box(state.wood, poseStack, collector, light, 1.0f - leg, 0.0f, 2.0f - leg, 1.0f, 0.625f, 2.0f);
        // backboard along the far long edge (top blocks)
        if (state.backboard) {
            BoxRenderer.box(state.wood, poseStack, collector, light, 0.875f, 0.875f, 0.0f, 1.0f, 1.75f, 2.0f);
        }
        // paper stack on the foot half
        if (state.paperCount > 0) {
            float thickness = Math.min(0.15f, 0.004f * state.paperCount + 0.01f);
            BoxRenderer.box(state.paper, poseStack, collector, light, 0.3f, 0.875f, 1.25f, 0.7f, 0.875f + thickness, 1.75f);
        }
        // target item lying open on the head half
        if (!state.target.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5, 0.875 + 0.02, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(90f));
            poseStack.mulPose(Axis.XP.rotationDegrees(90f));
            float s = state.targetIsBook ? 0.6f : 0.45f;
            poseStack.scale(s, s, s);
            ItemRenderHelper.submit(state.target, poseStack, collector, light);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(WritingDeskBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 2, p.getZ() + 2);
    }
}
