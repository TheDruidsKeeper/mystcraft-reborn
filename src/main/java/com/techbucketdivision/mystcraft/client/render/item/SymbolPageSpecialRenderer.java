package com.tbd.mystcraft.client.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import com.tbd.mystcraft.api.symbol.AgeSymbol;
import com.tbd.mystcraft.api.symbol.WordData;
import com.tbd.mystcraft.client.render.SymbolGlyphs;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.item.component.SymbolPage;
import com.tbd.mystcraft.symbol.SymbolRegistry;
import com.tbd.mystcraft.util.MystIds;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Item model for pages: the parchment background with the page's symbol glyph composed on it from
 * {@code textures/misc/symbolcomponents.png} (the same drawing as {@link SymbolGlyphs#drawSymbol} in GUIs), or the
 * link-panel icon for link panels. Drawn as geometry instead of baked sprites so every symbol - including the biome
 * symbols registered when a world starts - has an icon without re-stitching the item atlas. Registered as the
 * {@code mystcraft:symbol_page} special model, used by {@code items/page.json} with {@code item/page} as the
 * display-transform base.
 */
public final class SymbolPageSpecialRenderer implements SpecialModelRenderer<SymbolPageSpecialRenderer.Page> {
    public static final Identifier ID = MystIds.id("symbol_page");
    private static final Identifier BACKGROUND = MystIds.id("textures/item/page_background.png");

    private static final RenderType BACKGROUND_TYPE = RenderTypes.entityTranslucent(BACKGROUND);
    private static final RenderType GLYPH_TYPE = RenderTypes.entityTranslucent(SymbolGlyphs.COMPONENTS);

    /** Flat item: front face at z = 8.5/16, back face at 7.5/16 like {@code item/generated}. */
    private static final float FRONT_Z = 8.5f / 16f;
    private static final float BACK_Z = 7.5f / 16f;
    private static final float GLYPH_Z = FRONT_Z + 0.002f;
    /** Glyph square inside the 16 px icon (GUI pixel units, y down). */
    private static final float GLYPH_MARGIN = 1.5f;
    private static final float GLYPH_SIZE = 13f;

    private static final int SHEET = 512;
    private static final int CELL = 64;
    private static final int PER_ROW = SHEET / CELL;
    private static final float SQRT2 = 1.4142135f;
    private static final float DIAMOND = 1f + SQRT2;

    /** One glyph to draw: its poem (null = unknown symbol -> "?") and ink colour. */
    public record Glyph(@Nullable List<String> poem, int argb) {}

    /**
     * What a page shows: the primary glyph with up to four modifier overlays (same corners as
     * {@link SymbolGlyphs#drawSymbolPage}), a link panel, or nothing (blank).
     */
    public record Page(@Nullable Glyph symbol, List<Glyph> overlays, boolean linkPanel) {}

    @Override
    public @Nullable Page extractArgument(ItemStack stack) {
        if (PageItem.isLinkPanel(stack)) return new Page(null, List.of(), true);
        SymbolPage page = PageItem.getSymbolPage(stack);
        if (page == null) return null;
        AgeSymbol symbol = page.resolve();
        List<Glyph> overlays = new ArrayList<>(4);
        for (int i = 0; i < Math.min(4, page.modifiers().size()); i++) {
            AgeSymbol modifier = SymbolRegistry.get(page.modifiers().get(i));
            overlays.add(new Glyph(modifier == null ? null : modifier.poem(), SymbolGlyphs.modifierTint(modifier)));
        }
        return new Page(new Glyph(symbol == null ? null : symbol.poem(), page.discovered() ? SymbolGlyphs.DISCOVERED : SymbolGlyphs.DEFAULT),
                overlays, false);
    }

    @Override
    public void submit(@Nullable Page page, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                       boolean hasFoil, int outlineColor) {
        collector.submitCustomGeometry(poseStack, BACKGROUND_TYPE, (pose, buffer) -> {
            rect(pose, buffer, 0f, 0f, 16f, 16f, FRONT_Z, BACK_Z, 0f, 0f, 1f, 1f, SymbolGlyphs.WHITE, light, overlay);
            if (page != null && page.linkPanel()) {
                // the link panel: a black rectangle over the upper part of the page (same as SymbolGlyphs.drawPage);
                // an opaque patch of the parchment tinted black gives a solid fill without another texture
                rect(pose, buffer, 16f * 0.15f, 16f * 0.15f, 16f * 0.7f, 16f * 0.35f, GLYPH_Z, BACK_Z - 0.002f,
                        0.3f, 0.3f, 0.7f, 0.7f, SymbolGlyphs.DEFAULT, light, overlay);
            }
        });
        if (page == null || page.linkPanel() || page.symbol() == null) return; // blank page / link panel
        collector.submitCustomGeometry(poseStack, GLYPH_TYPE, (pose, buffer) -> {
            symbol(pose, buffer, page.symbol(), GLYPH_MARGIN, GLYPH_MARGIN, GLYPH_SIZE, light, overlay);
            float size = GLYPH_SIZE * SymbolGlyphs.OVERLAY_SCALE;
            for (int i = 0; i < page.overlays().size(); i++) {
                symbol(pose, buffer, page.overlays().get(i), SymbolGlyphs.overlayX(i, GLYPH_MARGIN, GLYPH_SIZE),
                        SymbolGlyphs.overlayY(i, GLYPH_MARGIN, GLYPH_SIZE), size, light, overlay);
            }
        });
    }

    /** Same layout as {@link SymbolGlyphs#drawSymbol}: four words on a diamond, each at half scale. */
    private static void symbol(PoseStack.Pose pose, VertexConsumer buffer, Glyph glyph, float x, float y, float size,
                               int light, int overlay) {
        List<String> poem = glyph.poem();
        if (poem == null || poem.isEmpty()) {
            word(pose, buffer, null, x, y, size, glyph.argb(), light, overlay);
            return;
        }
        float half = size / 2f;
        float s = half / DIAMOND;
        float o = s * SQRT2;
        float wordSize = 2f * s;
        if (poem.size() > 0) word(pose, buffer, poem.get(0), x + o, y, wordSize, glyph.argb(), light, overlay);
        if (poem.size() > 1) word(pose, buffer, poem.get(1), x + o * 2f, y + o, wordSize, glyph.argb(), light, overlay);
        if (poem.size() > 2) word(pose, buffer, poem.get(2), x + o, y + o * 2f, wordSize, glyph.argb(), light, overlay);
        if (poem.size() > 3) word(pose, buffer, poem.get(3), x, y + o, wordSize, glyph.argb(), light, overlay);
    }

    private static void word(PoseStack.Pose pose, VertexConsumer buffer, @Nullable String word, float gx, float gy, float size,
                             int argb, int light, int overlay) {
        List<Integer> components = word == null || word.isEmpty() ? List.of(0) : WordData.components(word);
        if (components.isEmpty()) components = List.of(0);
        for (int c : components) {
            float u0 = (c % PER_ROW) * CELL / (float) SHEET;
            float v0 = ((c / PER_ROW) % PER_ROW) * CELL / (float) SHEET;
            float u1 = u0 + CELL / (float) SHEET;
            float v1 = v0 + CELL / (float) SHEET;
            sheet(pose, buffer, gx, gy, size, GLYPH_Z, BACK_Z - 0.002f, u0, v0, u1, v1, argb, light, overlay);
        }
    }

    /**
     * A double-sided square: GUI-pixel rectangle ({@code gx, gy} top-left, {@code size}, y down) mapped onto the
     * 16-unit item space (y up), front face at {@code frontZ} facing +Z and back face at {@code backZ} facing -Z.
     */
    private static void sheet(PoseStack.Pose pose, VertexConsumer buffer, float gx, float gy, float size, float frontZ,
                              float backZ, float u0, float v0, float u1, float v1, int argb, int light, int overlay) {
        rect(pose, buffer, gx, gy, size, size, frontZ, backZ, u0, v0, u1, v1, argb, light, overlay);
    }

    private static void rect(PoseStack.Pose pose, VertexConsumer buffer, float gx, float gy, float w, float h, float frontZ,
                             float backZ, float u0, float v0, float u1, float v1, int argb, int light, int overlay) {
        float x0 = gx / 16f, x1 = (gx + w) / 16f;
        float y1 = 1f - gy / 16f, y0 = 1f - (gy + h) / 16f;
        // front (+Z): counter-clockwise seen from +Z
        vertex(pose, buffer, x0, y0, frontZ, u0, v1, argb, light, overlay, 0f, 0f, 1f);
        vertex(pose, buffer, x1, y0, frontZ, u1, v1, argb, light, overlay, 0f, 0f, 1f);
        vertex(pose, buffer, x1, y1, frontZ, u1, v0, argb, light, overlay, 0f, 0f, 1f);
        vertex(pose, buffer, x0, y1, frontZ, u0, v0, argb, light, overlay, 0f, 0f, 1f);
        // back (-Z): mirrored so the glyph reads correctly from behind too
        vertex(pose, buffer, x1, y0, backZ, u1, v1, argb, light, overlay, 0f, 0f, -1f);
        vertex(pose, buffer, x0, y0, backZ, u0, v1, argb, light, overlay, 0f, 0f, -1f);
        vertex(pose, buffer, x0, y1, backZ, u0, v0, argb, light, overlay, 0f, 0f, -1f);
        vertex(pose, buffer, x1, y1, backZ, u1, v0, argb, light, overlay, 0f, 0f, -1f);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v,
                               int argb, int light, int overlay, float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0f, 0f, BACK_Z));
        output.accept(new Vector3f(1f, 0f, BACK_Z));
        output.accept(new Vector3f(1f, 1f, BACK_Z));
        output.accept(new Vector3f(0f, 1f, BACK_Z));
        output.accept(new Vector3f(0f, 0f, FRONT_Z));
        output.accept(new Vector3f(1f, 0f, FRONT_Z));
        output.accept(new Vector3f(1f, 1f, FRONT_Z));
        output.accept(new Vector3f(0f, 1f, FRONT_Z));
    }

    /** {@code {"type": "mystcraft:symbol_page"}} */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<Page> {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public @Nullable SpecialModelRenderer<Page> bake(BakingContext context) {
            return new SymbolPageSpecialRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
