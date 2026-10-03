package com.techbucketdivision.mystcraft.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.techbucketdivision.mystcraft.block.WritingDeskBlock;
import com.techbucketdivision.mystcraft.blockentity.BookUtil;
import com.techbucketdivision.mystcraft.blockentity.WritingDeskBlockEntity;
import com.techbucketdivision.mystcraft.client.render.ItemRenderHelper;
import com.techbucketdivision.mystcraft.client.render.model.LegacyModels;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.util.Unit;
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
 * Writing Desk renderer. The desk blocks are {@code RenderShape.INVISIBLE}; this renderer (attached to the head
 * block) draws the original {@code ModelWritingDesk} ({@link LegacyModels#writingDesk()}) over head and foot, with
 * the backboard parts when the desk has its top blocks and the paper stack sized by the paper count, plus the target
 * item lying open on the head half. The original GL transform chain is replayed exactly (see {@link #submit}).
 */
public class WritingDeskRenderer implements BlockEntityRenderer<WritingDeskBlockEntity, WritingDeskRenderer.State> {

    public static class State extends BlockEntityRenderState {
        public final ItemStackRenderState target = new ItemStackRenderState();
        public int facingIndex;
        public boolean backboard;
        public int paperCount;
        public boolean targetIsBook;
    }

    private final Model.Simple desk;
    private final SpriteGetter sprites;
    private final ModelPart[] backing;
    private final ModelPart paper1, paper2, paper3, paperStack1, paperStack2;

    public WritingDeskRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LegacyModels.WRITING_DESK);
        this.desk = new Model.Simple(root, RenderTypes::entityCutout);
        this.sprites = context.sprites();
        this.backing = new ModelPart[LegacyModels.DESK_BACKING.length];
        for (int i = 0; i < backing.length; i++) backing[i] = root.getChild(LegacyModels.DESK_BACKING[i]);
        this.paper1 = root.getChild("paper1");
        this.paper2 = root.getChild("paper2");
        this.paper3 = root.getChild("paper3");
        this.paperStack1 = root.getChild("paperStack1");
        this.paperStack2 = root.getChild("paperStack2");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WritingDeskBlockEntity be, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        BlockState bs = be.getBlockState();
        Direction facing = bs.hasProperty(WritingDeskBlock.FACING) ? bs.getValue(WritingDeskBlock.FACING) : Direction.NORTH;
        // Rotation about the model's y axis by 90*k maps the foot direction (+Z at k=0) onto S, W, N, E for k=0..3,
        // which is exactly Direction#get2DDataValue.
        state.facingIndex = facing.get2DDataValue();
        state.backboard = be.hasBackboard();
        state.paperCount = be.getPaperCount();
        ItemStack target = be.getDisplayItem();
        state.targetIsBook = BookUtil.isLinkingItem(target);
        ItemRenderHelper.extract(state.target, target, ItemDisplayContext.FIXED, be.getLevel(), 0);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        for (ModelPart part : backing) part.visible = state.backboard;
        paper2.visible = state.paperCount > 0;
        paper3.visible = state.paperCount > 1;
        paper1.visible = state.paperCount > 2;
        paperStack2.visible = state.paperCount > 27;
        paperStack1.visible = state.paperCount > 47;

        poseStack.pushPose();
        // Original RenderWritingDesk: translate(x+.5, y+1.5, z+.5); rotate 90 X; rotate 90 Y; rotate 90 Z; rotate 90*k Y.
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(90f));
        poseStack.mulPose(Axis.YP.rotationDegrees(90f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90f));
        poseStack.mulPose(Axis.YP.rotationDegrees(90f * state.facingIndex));
        collector.submitModel(desk, Unit.INSTANCE, poseStack, light, OverlayTexture.NO_OVERLAY, -1,
                LegacyModels.DESK_TEXTURE, sprites, 0, state.breakProgress);
        poseStack.popPose();

        // target item lying open on the head half (local frame: head block, foot toward FACING)
        if (!state.target.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5, 1.0 + 0.02, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(-Direction.from2DDataValue(state.facingIndex).toYRot()));
            poseStack.mulPose(Axis.YP.rotationDegrees(90f));
            poseStack.mulPose(Axis.XP.rotationDegrees(90f));
            float s = state.targetIsBook ? 0.6f : 0.45f;
            poseStack.scale(s, s, s);
            ItemRenderHelper.submit(state.target, poseStack, collector, light);
            poseStack.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(WritingDeskBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 2, p.getZ() + 2);
    }
}
