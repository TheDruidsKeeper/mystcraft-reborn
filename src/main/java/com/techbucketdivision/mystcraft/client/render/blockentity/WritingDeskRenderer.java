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
        /** Tint colour per occupied notebook tab (first {@link #MAX_SHELF_BOOKS}), 0 = empty tab. */
        public final int[] shelfBooks = new int[MAX_SHELF_BOOKS];
        /** Inkwell fill 0..1 (-1 = no inkwell shown: nothing in the tank). */
        public float inkLevel = -1f;
    }

    /** Book spines that fit on the two shelves under the head half of the desk top (14 units wide each, 2 per book). */
    public static final int BOOKS_PER_SHELF = 6;
    public static final int MAX_SHELF_BOOKS = BOOKS_PER_SHELF * 2;
    private static final int TINT_NOTEBOOK = 0xFF9A6A3A, TINT_PORTFOLIO = 0xFF4A6A9A, TINT_FOLDER = 0xFFD8C08A;

    private final Model.Simple desk;
    private final SpriteGetter sprites;
    private final ModelPart[] backing;
    private final ModelPart paper1, paper2, paper3, paperStack1, paperStack2;
    private final Model.Simple shelfBook, inkwellCup;
    private final Model.Simple[] inkFill = new Model.Simple[4];

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
        this.shelfBook = LegacyModels.deskShelfBook();
        this.inkwellCup = LegacyModels.inkwellCup();
        for (int i = 0; i < inkFill.length; i++) inkFill[i] = LegacyModels.inkwellInk(1 + i);
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
        int shown = 0;
        java.util.Arrays.fill(state.shelfBooks, 0);
        for (int tab = 0; tab < be.getMaxSurfaceTabCount() && shown < MAX_SHELF_BOOKS; tab++) {
            ItemStack notebook = be.getTabItem(tab);
            if (notebook.isEmpty()) continue;
            state.shelfBooks[shown++] = notebook.getItem() instanceof com.techbucketdivision.mystcraft.item.PortfolioItem ? TINT_PORTFOLIO
                    : notebook.getItem() instanceof com.techbucketdivision.mystcraft.item.FolderItem ? TINT_FOLDER : TINT_NOTEBOOK;
        }
        int ink = be.getInkAmount();
        state.inkLevel = ink <= 0 ? -1f : Math.min(1f, ink / (float) WritingDeskBlockEntity.TANK_CAPACITY);
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

        // Local frame for the extras: origin at the head block's centre, +Z towards the foot (FACING), +X towards the
        // backboard (derived numerically from the model chain above: the back wall sits at facing.getCounterClockWise()).
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-Direction.from2DDataValue(state.facingIndex).toYRot()));
        // Notebooks on the two shelves under the head half of the desk top (model: middleShelf y15..17 and
        // bottomShelf y23..24 between deskLeft and deskMiddle -> world y 0.56..0.94 and 0.06..0.44, local z -0.44..0.44,
        // depth the whole block). Spines stand with their backs to the reader (-X side), 5 tall, 2 wide.
        for (int i = 0; i < MAX_SHELF_BOOKS; i++) {
            int tint = state.shelfBooks[i];
            if (tint == 0) continue;
            int shelf = i / BOOKS_PER_SHELF, slot = i % BOOKS_PER_SHELF;
            poseStack.pushPose();
            poseStack.translate(-0.42, shelf == 0 ? 0.565 : 0.065, -0.40 + slot * 2.3 / 16.0);
            collector.submitModel(shelfBook, Unit.INSTANCE, poseStack, light, OverlayTexture.NO_OVERLAY, tint,
                    LegacyModels.BOOK_SPINE_TEXTURE, sprites, 0, state.breakProgress);
            poseStack.popPose();
        }
        // Inkwell on the desk top (surface y = 1.0), front corner of the head half, clear of the backboard (+X side).
        if (state.inkLevel >= 0f) {
            poseStack.pushPose();
            poseStack.translate(-0.3, 1.0, -0.33);
            collector.submitModel(inkwellCup, Unit.INSTANCE, poseStack, light, OverlayTexture.NO_OVERLAY, -1,
                    LegacyModels.INKWELL_CUP_TEXTURE, sprites, 0, state.breakProgress);
            int step = Math.max(0, Math.min(3, Math.round(state.inkLevel * 3f)));
            if (state.inkLevel > 0f) {
                collector.submitModel(inkFill[step], Unit.INSTANCE, poseStack, light, OverlayTexture.NO_OVERLAY, -1,
                        LegacyModels.INKWELL_INK_TEXTURE, sprites, 0, state.breakProgress);
            }
            poseStack.popPose();
        }
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
