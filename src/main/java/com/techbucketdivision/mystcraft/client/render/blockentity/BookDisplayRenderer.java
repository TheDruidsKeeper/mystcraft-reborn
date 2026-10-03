package com.techbucketdivision.mystcraft.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.techbucketdivision.mystcraft.blockentity.BookDisplayBlockEntity;
import com.techbucketdivision.mystcraft.block.BookstandBlock;
import com.techbucketdivision.mystcraft.block.LecternBlock;
import com.techbucketdivision.mystcraft.client.render.model.LegacyModels;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.Direction;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.state.BlockState;
import com.techbucketdivision.mystcraft.client.render.ItemRenderHelper;
import com.techbucketdivision.mystcraft.client.render.LabelRenderer;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bookstand / lectern renderer: the displayed item lies flat on the top surface, rotated by the block entity's yaw and
 * tilted by its pitch (bookstand book scale 1.05, lectern 1.22 per REQUIREMENTS §17; here relative item scales).
 */
public class BookDisplayRenderer<T extends BookDisplayBlockEntity> implements BlockEntityRenderer<T, BookDisplayRenderer.State> {

    public static class State extends BlockEntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        /** 0 = plain display (no model), 1 = bookstand, 2 = lectern. */
        public int kind;
        public int rotationIndex;
        public float modelYaw;
        public float yaw;
        public float pitch;
        public float surfaceHeight = 12f / 16f;
        public float scale = 1.05f;
        public @Nullable String label;
        public double distanceSq;
    }

    private final Model.Simple bookstand;
    private final Model.Simple lectern;
    private final SpriteGetter sprites;

    public BookDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.bookstand = new Model.Simple(context.bakeLayer(LegacyModels.BOOKSTAND), RenderTypes::entityCutout);
        this.lectern = new Model.Simple(context.bakeLayer(LegacyModels.LECTERN), RenderTypes::entityCutout);
        this.sprites = context.sprites();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T be, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        ItemStack item = be.getDisplayItem();
        ItemRenderHelper.extract(state.item, item, ItemDisplayContext.FIXED, be.getLevel(), 0);
        state.yaw = be.getYaw();
        state.pitch = be.getPitch();
        BlockState bs = be.getBlockState();
        boolean lectern = bs.is(ModBlocks.LECTERN.get());
        if (lectern) {
            state.kind = 2;
            // Original RenderLectern: facings on the Z axis are flipped, then rotate by (horizontal angle + 90).
            Direction facing = bs.hasProperty(LecternBlock.FACING) ? bs.getValue(LecternBlock.FACING) : Direction.NORTH;
            if (facing.getAxis() == Direction.Axis.Z) facing = facing.getOpposite();
            state.modelYaw = facing.toYRot() + 90f;
        } else if (bs.is(ModBlocks.BOOKSTAND.get())) {
            state.kind = 1;
            state.rotationIndex = bs.hasProperty(BookstandBlock.ROTATION) ? bs.getValue(BookstandBlock.ROTATION) : 0;
        } else {
            state.kind = 0;
        }
        state.surfaceHeight = lectern ? 7f / 16f : 12f / 16f;
        state.scale = lectern ? 1.22f * 0.5f : 1.05f * 0.5f;
        state.label = LabelRenderer.enabled() ? be.getBookTitle() : null;
        double dx = cameraPosition.x - (be.getBlockPos().getX() + 0.5);
        double dy = cameraPosition.y - (be.getBlockPos().getY() + 0.5);
        double dz = cameraPosition.z - (be.getBlockPos().getZ() + 0.5);
        state.distanceSq = dx * dx + dy * dy + dz * dz;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.kind == 1) {
            // Original RenderBookstand: translate(x+.5, y+.5, z+.5), rotate 180 about Z, rotate 45*index about Y.
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            poseStack.mulPose(Axis.YP.rotationDegrees(45f * state.rotationIndex));
            collector.submitModel(bookstand, Unit.INSTANCE, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
                    LegacyModels.BOOKSTAND_TEXTURE, sprites, 0, state.breakProgress);
            poseStack.popPose();
        } else if (state.kind == 2) {
            poseStack.pushPose();
            poseStack.translate(0.5, 0.0, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.modelYaw));
            collector.submitModel(lectern, Unit.INSTANCE, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
                    LegacyModels.LECTERN_TEXTURE, sprites, 0, state.breakProgress);
            poseStack.popPose();
        }
        if (!state.item.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5, state.surfaceHeight + 0.03, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(90f - state.pitch)); // lay the item flat, tilt by pitch
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
