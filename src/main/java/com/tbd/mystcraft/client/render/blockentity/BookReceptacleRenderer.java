package com.tbd.mystcraft.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tbd.mystcraft.block.BookReceptacleBlock;
import com.tbd.mystcraft.blockentity.BookReceptacleBlockEntity;
import com.tbd.mystcraft.client.render.ItemRenderHelper;
import com.tbd.mystcraft.client.render.LabelRenderer;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.tbd.mystcraft.client.render.model.LegacyModels;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Book Receptacle: a book stands closed in the receptacle's slot - the 2 x 10 px dark slit in the middle of the
 * slab face is exactly the spine of the vanilla {@link BookModel} at full size, so the book sits spine-in with its
 * page edges towards the room, upright for wall receptacles and turned by the placement yaw on floor / ceiling
 * ones. Anything else held (a page) lies as an icon on the slab face.
 */
public class BookReceptacleRenderer extends BookDisplayRenderer<BookReceptacleBlockEntity> {

    public static class ReceptacleState extends BookDisplayRenderer.State {
        public Direction facing = Direction.UP;
    }

    /** Shut: lids together, no page flip. */
    private static final BookModel.State CLOSED_BOOK = new BookModel.State(0f, 0f, 0f);
    /** The slab face: 6 px of slab behind it, so 2 px in front of the block centre. */
    private static final float FACE = 0.5f - 6f / 16f;

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
        if (state.book != 0 && !state.item.isEmpty()) {
            poseStack.pushPose();
            // BookModel closed: spine in the x = 0 plane (2 wide along z, 10 tall along y), lids and pages from x = 0
            // to x = 6. Put the spine a hair in front of the slab face over the slot and point +X out of the face.
            float off = FACE + 0.002f;
            poseStack.translate(0.5 + facing.getStepX() * off, 0.5 + facing.getStepY() * off, 0.5 + facing.getStepZ() * off);
            if (facing.getAxis().isHorizontal()) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot())); // south -> facing
                poseStack.mulPose(Axis.YP.rotationDegrees(-90f));             // model +X -> south, Y stays up
            } else {
                poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
                poseStack.mulPose(Axis.ZP.rotationDegrees(facing == Direction.UP ? 90f : -90f)); // model +X -> up / down
            }
            collector.submitModel(book, CLOSED_BOOK, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
                    state.book == 2 ? LegacyModels.LINKBOOK_TEXTURE : LegacyModels.AGEBOOK_TEXTURE, sprites, 0, state.breakProgress);
            poseStack.popPose();
        } else if (!state.item.isEmpty()) {
            poseStack.pushPose();
            // slab occupies the 6 px behind the face; the item lies on the slab surface
            float off = FACE + 0.03f;
            poseStack.translate(0.5 + facing.getStepX() * off, 0.5 + facing.getStepY() * off, 0.5 + facing.getStepZ() * off);
            poseStack.mulPose(facing.getRotation()); // Direction#getRotation() -> Quaternionf (API_NOTES L1b, verified); maps +Y onto the direction
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
