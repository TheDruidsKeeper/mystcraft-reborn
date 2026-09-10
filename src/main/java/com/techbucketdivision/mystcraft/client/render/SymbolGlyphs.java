package com.techbucketdivision.mystcraft.client.render;

import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.WordData;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Draws Narayan word glyphs, symbol diamonds and page tiles in GUIs (port of the original {@code GuiUtils} drawing
 * code, REQUIREMENTS §17). Glyph components come from {@code textures/misc/symbolcomponents.png}: 512×512, an 8×8 grid
 * of 64 px cells, component {@code i} at cell {@code (i % 8, i / 8)}.
 */
public final class SymbolGlyphs {
    private SymbolGlyphs() {}

    public static final Identifier COMPONENTS = MystIds.id("textures/misc/symbolcomponents.png");
    public static final Identifier PAGE_LEFT = MystIds.id("textures/gui/bookui_pagel.png");

    private static final int SHEET = 512;
    private static final int CELL = 64;
    private static final int PER_ROW = SHEET / CELL;
    private static final float SQRT2 = 1.4142135f;
    private static final float DIAMOND = 1f + SQRT2;

    public static final int WHITE = 0xFFFFFFFF;
    /** Glyphs are drawn black (the original tinted components with colour 0). */
    public static final int DEFAULT = 0xFF000000;

    // --- components / words --------------------------------------------------------------------------------------

    /** Draws one glyph component scaled to {@code size} px at (x, y), tinted with an ARGB colour. */
    public static void drawComponent(GuiGraphicsExtractor g, int index, float x, float y, float size, int argb) {
        int cellX = (index % PER_ROW) * CELL;
        int cellY = ((index / PER_ROW) % PER_ROW) * CELL;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        float s = size / CELL;
        g.pose().scale(s, s);
        g.blit(RenderPipelines.GUI_TEXTURED, COMPONENTS, 0, 0, cellX, cellY, CELL, CELL, SHEET, SHEET, argb);
        g.pose().popMatrix();
    }

    /** Draws a word (all of its components stacked) at {@code size} px. {@code null}/empty word → component 0 ("?"). */
    public static void drawWord(GuiGraphicsExtractor g, @Nullable String word, float x, float y, float size, int argb) {
        List<Integer> components = word == null || word.isEmpty() ? List.of(0) : WordData.components(word);
        if (components.isEmpty()) components = List.of(0);
        for (int c : components) drawComponent(g, c, x, y, size, argb);
    }

    public static void drawWord(GuiGraphicsExtractor g, @Nullable String word, int x, int y, int size) {
        drawWord(g, word, x, y, size, DEFAULT);
    }

    // --- symbols -------------------------------------------------------------------------------------------------

    /**
     * Draws a four-word poem as a diamond (top, right, bottom, left) inside a {@code size}×{@code size} square whose
     * top-left corner is (x, y). Each word is drawn at half scale like the original.
     */
    public static void drawSymbol(GuiGraphicsExtractor g, @Nullable List<String> poem, float x, float y, float size, int argb) {
        if (poem == null || poem.isEmpty()) {
            drawWord(g, null, x, y, size, argb);
            return;
        }
        float half = size / 2f;
        float s = half / DIAMOND;   // quarter-ish word size
        float o = s * SQRT2;        // offset step
        float word = 2f * s;
        if (poem.size() > 0) drawWord(g, poem.get(0), x + o, y, word, argb);
        if (poem.size() > 1) drawWord(g, poem.get(1), x + o * 2f, y + o, word, argb);
        if (poem.size() > 2) drawWord(g, poem.get(2), x + o, y + o * 2f, word, argb);
        if (poem.size() > 3) drawWord(g, poem.get(3), x, y + o, word, argb);
    }

    public static void drawSymbol(GuiGraphicsExtractor g, @Nullable List<String> poem, int x, int y, int size) {
        drawSymbol(g, poem, x, y, size, DEFAULT);
    }

    public static void drawSymbol(GuiGraphicsExtractor g, @Nullable AgeSymbol symbol, int x, int y, int size) {
        drawSymbol(g, symbol == null ? null : symbol.poem(), x, y, size, DEFAULT);
    }

    // --- pages ---------------------------------------------------------------------------------------------------

    /**
     * Draws a page tile of {@code w}×{@code h} px: the parchment background (dimmed for an empty stack), then the
     * symbol glyph, or the black link-panel rectangle for link panels.
     */
    public static void drawPage(GuiGraphicsExtractor g, ItemStack page, float x, float y, float w, float h) {
        drawPageBackground(g, page.isEmpty(), x, y, w, h);
        if (page.isEmpty()) return;
        AgeSymbol symbol = PageItem.getSymbol(page);
        if (symbol != null) {
            drawSymbol(g, symbol.poem(), x + 0.5f, y + (h + 1f - w) / 2f, w - 1f, DEFAULT);
        } else if (PageItem.getSymbolId(page) != null) {
            drawWord(g, null, x + 0.5f, y + (h + 1f - w) / 2f, w - 1f, DEFAULT); // unknown symbol id
        } else if (PageItem.isLinkPanel(page)) {
            g.fill(Math.round(x + w * 0.15f), Math.round(y + h * 0.15f), Math.round(x + w * 0.85f), Math.round(y + h * 0.5f), 0xFF000000);
        }
    }

    public static void drawPage(GuiGraphicsExtractor g, ItemStack page, int x, int y, int w, int h) {
        drawPage(g, page, (float) x, (float) y, (float) w, (float) h);
    }

    /** Parchment background (bookui_pagel.png region 156,0 30×40) stretched to the tile. */
    public static void drawPageBackground(GuiGraphicsExtractor g, boolean dimmed, float x, float y, float w, float h) {
        int color = dimmed ? 0x33333333 : WHITE;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(w / 30f, h / 40f);
        g.blit(RenderPipelines.GUI_TEXTURED, PAGE_LEFT, 0, 0, 156, 0, 30, 40, 256, 256, color);
        g.pose().popMatrix();
    }
}
