package com.techbucketdivision.mystcraft.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.techbucketdivision.mystcraft.blockentity.BookDisplayBlockEntity;
import com.techbucketdivision.mystcraft.block.BookstandBlock;
import com.techbucketdivision.mystcraft.block.LecternBlock;
import com.techbucketdivision.mystcraft.client.render.model.LegacyModels;
import com.techbucketdivision.mystcraft.item.LinkingBookItem;
import com.techbucketdivision.mystcraft.item.LinkingItem;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
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
 * Bookstand / lectern renderer. Books (descriptive / linking) are shown as an open book (vanilla {@link BookModel}
 * with the legacy agebook / linkbook covers) lying on the stand's surface - flat on the bookstand, on the slope of
 * the lectern - the way the vanilla lectern shows its book. Any other displayed item (a page, say) lies flat as an
 * item icon rotated by the block entity's yaw and tilted by its pitch.
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
        /** 0 = not a book (render the item icon), 1 = descriptive book, 2 = linking book. */
        public int book;
    }

    /** Open-book pose: the vanilla lectern's (openness ~1.5 rad, pages held still). */
    private static final BookModel.State OPEN_BOOK = BookModel.State.forAnimation(0f, 0.1f, 0.9f, 1.2f);
    /** The bookstand's arms form a 15 degree V, so its book opens to match (lids 15 degrees above flat). */
    private static final BookModel.State STAND_BOOK = new BookModel.State((float) Math.toRadians(75), 0.1f, 0.9f);
    /** The bookstand's arms are pitched 30 degrees (music-stand style): the book's far edge is raised by that much. */
    private static final float STAND_TILT = 30f;
    private static final float STAND_SPINE_HEIGHT = 0.56f;
    private static final float BOOK_SCALE = 0.75f;

    private final Model.Simple bookstand;
    private final Model.Simple lectern;
    private final BookModel book;
    private final SpriteGetter sprites;

    public BookDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.bookstand = new Model.Simple(context.bakeLayer(LegacyModels.BOOKSTAND), RenderTypes::entityCutout);
        this.lectern = LegacyModels.lecternModel();
        this.book = new BookModel(context.bakeLayer(ModelLayers.BOOK));
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
        state.book = item.getItem() instanceof LinkingBookItem ? 2 : item.getItem() instanceof LinkingItem ? 1 : 0;
        state.yaw = be.getYaw();
        state.pitch = be.getPitch();
        BlockState bs = be.getBlockState();
        boolean lectern = bs.is(ModBlocks.LECTERN.get());
        if (lectern) {
            state.kind = 2;
            // FACING is the reading side (towards the player who placed it). The wedge's low edge / ledge is the
            // model's -X, so rotate -X onto FACING: yaw = 90 - toYRot (south 90, west 0, north -90, east 180).
            Direction facing = bs.hasProperty(LecternBlock.FACING) ? bs.getValue(LecternBlock.FACING) : Direction.SOUTH;
            state.modelYaw = 90f - facing.toYRot();
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
        if (state.book != 0 && !state.item.isEmpty()) {
            submitOpenBook(state, poseStack, collector);
        } else if (!state.item.isEmpty()) {
            poseStack.pushPose();
            if (state.kind == 2) {
                // Original RenderLectern.renderItem: translate(0, 0.255, 0) then rotate 110 about Z in the model's
                // frame = lie on the wedge's slope (the wedge rises towards the model's +X).
                poseStack.translate(0.5, LegacyModels.LECTERN_SURFACE_CENTER + 0.02, 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(state.modelYaw));
                poseStack.mulPose(Axis.ZP.rotationDegrees(LegacyModels.LECTERN_SLOPE_DEGREES));
                // FIXED display transforms turn the icon 180 about Y, so after X+90 its front faces up and Y+90 puts
                // the icon's top at the wedge's high side (+X): the page reads upright from the ledge.
                poseStack.mulPose(Axis.YP.rotationDegrees(90f));
                poseStack.mulPose(Axis.XP.rotationDegrees(90f));
            } else {
                poseStack.translate(0.5, state.surfaceHeight + 0.03, 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
                poseStack.mulPose(Axis.XP.rotationDegrees(90f - state.pitch)); // lay the item flat, tilt by pitch
            }
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

    /**
     * The vanilla lectern's book pose, adapted to the stand: spine runs away from the reader, pages left and right.
     * Lectern: the spine follows the wedge's slope (rises towards the model's +X, the reader stands at -X).
     * Bookstand / plain display: flat on the surface, turned by the entity's yaw and tipped by its pitch.
     */
    private void submitOpenBook(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        // BookModel geometry: spine along model +Y, the open pages fan out around it with their bisector along +X.
        // Z(90 + tilt) therefore lays the spine along the X axis with the pages facing up, tilted by 'tilt' so the
        // reader-side (-X) end of the spine is the low one; X(180) first flips the spine so the page tops end up at
        // the far (high) end. 2 units of lid thickness sit below the spine, hence the small lift.
        if (state.kind == 2) {
            poseStack.translate(0.5, LegacyModels.LECTERN_SURFACE_CENTER + 0.04, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.modelYaw));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90f + LegacyModels.LECTERN_SLOPE_DEGREES));
        } else if (state.kind == 1) {
            // in the stand's V: spine along the arms' crease (the stand's Z, turned with the stand), raised 30 degrees
            poseStack.translate(0.5, STAND_SPINE_HEIGHT, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(-45f * state.rotationIndex));
            poseStack.mulPose(Axis.XP.rotationDegrees(STAND_TILT));
            poseStack.mulPose(Axis.YP.rotationDegrees(90f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90f));
        } else {
            poseStack.translate(0.5, state.surfaceHeight + 0.04, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90f + state.pitch));
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(180f));
        poseStack.scale(BOOK_SCALE, BOOK_SCALE, BOOK_SCALE);
        collector.submitModel(book, state.kind == 1 ? STAND_BOOK : OPEN_BOOK, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
                state.book == 2 ? LegacyModels.LINKBOOK_TEXTURE : LegacyModels.AGEBOOK_TEXTURE, sprites, 0, state.breakProgress);
        poseStack.popPose();
    }
}
