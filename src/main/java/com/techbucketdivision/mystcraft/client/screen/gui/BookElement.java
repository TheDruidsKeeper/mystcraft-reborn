package com.techbucketdivision.mystcraft.client.screen.gui;

import com.techbucketdivision.mystcraft.age.AgeSummary;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.client.render.SymbolGlyphs;
import com.techbucketdivision.mystcraft.item.DescriptiveBookItem;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.item.component.SymbolPage;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The open-book view (REQUIREMENTS §8.5): cover, page 0 with title/authors/link panel, symbol pages, page footer.
 * Drawn in a 327×199 design space scaled to the element size. Port of {@code GuiElementBook}.
 */
public class BookElement extends GuiElement {
    public static final int DESIGN_W = 327;
    public static final int DESIGN_H = 199;

    private static final Identifier COVER = MystIds.id("textures/gui/bookui_cover.png");
    private static final Identifier PAGE_LEFT = MystIds.id("textures/gui/bookui_pagel.png");
    private static final Identifier PAGE_RIGHT = MystIds.id("textures/gui/bookui_pager.png");
    private static final Identifier PAGE_RIGHT_FULL = MystIds.id("textures/gui/bookui_rpage_full.png");

    /** Book state exposed by the owning menu (mirrors {@code menu.BookView}). */
    public interface Container {
        ItemStack getBook();

        @Nullable LinkInfo getLinkInfo();

        int getCurrentPageIndex();

        ItemStack getCurrentPage();

        int getPageCount();

        boolean isLinkPermitted();

        boolean isTargetWorldVisited();

        String getBookTitle();

        List<String> getBookAuthors();

        boolean hasBookSlot();

        /** Client request to change page (the screen sends {@code SetCurrentPage}). */
        void setCurrentPageIndex(int index);

        /** Link panel clicked on page 0. */
        void onLink();

        /** Whether the page at this index is a draft (written at a desk, not yet permanent). */
        default boolean isDraftPage(int index) {
            return false;
        }

        /** The Age summary shown after the last page of a bound Descriptive Book, or {@code null}. */
        default @Nullable AgeSummary getSummary() {
            return null;
        }
    }

    private final Container container;
    private final float xScale, yScale;
    private final List<Component> hoverText = new ArrayList<>();
    private boolean hoverSymbol;

    public BookElement(int x, int y, int width, int height, Container container) {
        super(x, y, width, height);
        this.container = container;
        this.xScale = width / (float) DESIGN_W;
        this.yScale = height / (float) DESIGN_H;
    }

    public boolean isBook() {
        return !container.getBook().isEmpty();
    }

    @Override
    public boolean isVisible() {
        return super.isVisible() && isBook();
    }

    private boolean isAgebook() {
        ItemStack book = container.getBook();
        return !book.isEmpty() && book.getItem() instanceof DescriptiveBookItem;
    }

    // --- rendering -------------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hoverText.clear();
        hoverSymbol = false;
        Font font = Minecraft.getInstance().font;
        int page = container.getCurrentPageIndex();

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(xScale, yScale);

        blit(g, COVER, 0, 7, 152, 0, 34, 192);   // left border
        blit(g, COVER, 34, 7, 49, 0, 103, 192);  // left panel
        blit(g, COVER, 137, 7, 45, 0, 4, 192);
        blit(g, COVER, 141, 7, 0, 0, 186, 192);  // spine + right
        if (isAgebook()) {
            blit(g, COVER, 0, 7, 186, 0, 34, 192);   // gold borders
            blit(g, COVER, 293, 7, 186, 0, 34, 192);
        }
        if (page > 0) blit(g, PAGE_LEFT, 7, 0, 0, 0, 156, 195);

        ItemStack current = container.getCurrentPage();
        if (page == 0 || (!current.isEmpty() && PageItem.isLinkPanel(current))) {
            // link panel
            g.pose().pushMatrix();
            g.pose().translate(173, 20);
            drawLinkPanel(g, 132, 83);
            g.pose().popMatrix();
            blit(g, PAGE_RIGHT, 163, 0, 0, 0, 156, 195);
        } else if (!current.isEmpty()) {
            blit(g, PAGE_RIGHT_FULL, 163, 0, 0, 0, 156, 195);
            SymbolPage symbolPage = PageItem.getSymbolPage(current);
            AgeSymbol symbol = symbolPage == null ? null : symbolPage.resolve();
            if (symbolPage != null) {
                int sx = 171, sy = 25, size = 140;
                boolean draft = container.isDraftPage(page);
                // drafts are pencilled in grey; the ink only dries once the book leaves the desk
                SymbolGlyphs.drawSymbolPage(g, symbolPage, sx, sy, size, draft ? SymbolGlyphs.DRAFT : SymbolGlyphs.DEFAULT);
                if (draft || symbolPage.discovered()) {
                    Component note = Component.translatable(draft ? "gui.mystcraft.book.draft" : "gui.mystcraft.book.discovered");
                    g.text(font, note, 240 - font.width(note) / 2, 170, draft ? 0xFF8A4A1A : SymbolGlyphs.DISCOVERED, false);
                }
                // left page: the symbol's name and what it does (the glyph stays on the right)
                if (symbol != null) {
                    drawSymbolNotes(g, font, symbol, symbolPage);
                } else {
                    Identifier missing = PageItem.getSymbolId(current);
                    drawWrapped(g, font, Component.translatable("gui.mystcraft.book.unknown_symbol", String.valueOf(missing)), 24, 30, 118, 0xFF5A1A1A);
                }
                if (contains(mouseX, mouseY, (int) (x + sx * xScale), (int) (y + sy * yScale), (int) (size * xScale), (int) (size * yScale))) {
                    hoverSymbol = true;
                    hoverText.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), current));
                }
            }
        } else if (page >= container.getPageCount() && page > 0 && container.getSummary() != null) {
            blit(g, PAGE_RIGHT_FULL, 163, 0, 0, 0, 156, 195);
            drawSummary(g, font, container.getSummary());
        } else {
            blit(g, PAGE_RIGHT_FULL, 163, 0, 0, 0, 156, 195);
        }

        if (page == 0) {
            if (container.hasBookSlot()) blit(g, COVER, 40, 20, 156, 0, 18, 18); // slot frame
            g.text(font, container.getBookTitle(), 40, 40, 0xFF000000, false);
            int ay = 50;
            for (String author : container.getBookAuthors()) {
                g.pose().pushMatrix();
                g.pose().translate(50, ay);
                g.pose().scale(0.5f, 0.5f);
                g.text(font, author, 0, 0, 0xFF000000, false);
                g.pose().popMatrix();
                ay += 5;
            }
        }
        String footer = page >= container.getPageCount() && page > 0 && container.getSummary() != null
                ? Component.translatable("gui.mystcraft.book.summary.footer").getString() : page + "/" + container.getPageCount();
        g.text(font, footer, 165 - font.width(footer) / 2, 185, 0xFF000000, false);
        g.pose().popMatrix();
    }

    private void drawLinkPanel(GuiGraphicsExtractor g, int w, int h) {
        // A photograph of the destination (taken by whoever last arrived there, slideshow over the last few) when
        // one exists; otherwise the classic panel: dark gradient for a visited target, black for an unknown one.
        Identifier picture = com.techbucketdivision.mystcraft.client.PanelImages.current(container.getLinkInfo());
        if (picture != null) {
            g.blit(picture, 0, 0, w, h, 0f, 1f, 0f, 1f); // stretch the whole picture over the panel
            g.fillGradient(0, 0, w, h, 0x30000000, 0x60000000); // the panel's ink tint over the picture
        } else if (container.isTargetWorldVisited()) {
            g.fillGradient(0, 0, w, h, 0xFF000044, 0xFF006666);
        } else {
            g.fill(0, 0, w, h, 0xFF000000);
        }
        // Link panel effects (Disarm lightning flashes, LookingGlass view) are Phase 2.
        if (!container.isLinkPermitted()) {
            g.fill(0, 0, w, h, 0xBB888888);
        }
    }

    /**
     * Left page notes for a symbol page: category, name, a thin rule, the description word-wrapped to the page, then
     * one line per attached modifier.
     */
    private void drawSymbolNotes(GuiGraphicsExtractor g, Font font, AgeSymbol symbol, SymbolPage symbolPage) {
        int px = 24, py = 28, pw = 118;
        g.pose().pushMatrix();
        g.pose().translate(px, py - 6);
        g.pose().scale(0.5f, 0.5f);
        g.text(font, symbol.category().displayName(), 0, 0, 0xFF6A5A3A, false);
        g.pose().popMatrix();
        int y = drawWrapped(g, font, symbol.displayName(), px, py, pw, 0xFF1A1A1A);
        g.fill(px, y + 1, px + pw, y + 2, 0x60000000);
        y += 6;
        Component description = symbol.description();
        // a symbol without a written description shows nothing rather than a raw key
        if (net.minecraft.client.resources.language.I18n.exists(symbol.descriptionId() + ".desc")) {
            y = drawWrapped(g, font, description, px, y, pw, 0xFF3A3A3A);
        }
        if (!symbolPage.modifiers().isEmpty()) {
            y += 4;
            y = drawWrapped(g, font, Component.translatable("gui.mystcraft.book.modifiers"), px, y, pw, 0xFF1A1A1A);
            for (Identifier id : symbolPage.modifiers()) {
                AgeSymbol modifier = com.techbucketdivision.mystcraft.symbol.SymbolRegistry.get(id);
                Component name = modifier == null ? Component.literal(id.toString()) : modifier.displayName();
                y = drawWrapped(g, font, Component.literal("+ ").append(name), px + 4, y, pw - 4, SymbolGlyphs.modifierTint(modifier));
            }
        }
    }

    /**
     * The summary page (plan §2 rule 7): seed, instability (base + symbols, the live score when the Age is loaded),
     * the instability effects active in the Age, authors and how many pages were discovered. Right page.
     */
    private void drawSummary(GuiGraphicsExtractor g, Font font, AgeSummary summary) {
        int px = 172, py = 22, pw = 136;
        int y = drawWrapped(g, font, Component.translatable("gui.mystcraft.book.summary"), px, py, pw, 0xFF1A1A1A);
        g.fill(px, y + 1, px + pw, y + 2, 0x60000000);
        y += 6;
        y = drawWrapped(g, font, Component.translatable("gui.mystcraft.book.summary.seed", Long.toString(summary.seed())), px, y, pw, 0xFF3A3A3A);
        y = drawWrapped(g, font, Component.translatable("gui.mystcraft.book.summary.instability", summary.baseInstability(), summary.symbolInstability()), px, y, pw, 0xFF3A3A3A);
        if (summary.score() >= 0) {
            y = drawWrapped(g, font, Component.translatable("gui.mystcraft.book.summary.score", summary.score()), px, y, pw, summary.score() > 0 ? 0xFF7A1A1A : 0xFF3A3A3A);
        }
        if (summary.dead()) y = drawWrapped(g, font, Component.translatable("gui.mystcraft.book.summary.dead"), px, y, pw, 0xFF7A1A1A);
        y += 3;
        y = drawWrapped(g, font, Component.translatable("gui.mystcraft.book.summary.effects"), px, y, pw, 0xFF1A1A1A);
        if (summary.activeEffects().isEmpty()) {
            y = drawWrapped(g, font, Component.translatable(summary.score() >= 0 ? "gui.mystcraft.book.summary.effects.none" : "gui.mystcraft.book.summary.effects.unknown"), px + 4, y, pw - 4, 0xFF5A5A5A);
        } else {
            for (String effect : summary.activeEffects()) {
                String name = effect.replace(",g", " (global)").replace('_', ' ');
                y = drawWrapped(g, font, Component.literal("- " + name), px + 4, y, pw - 4, 0xFF7A1A1A);
            }
        }
        y += 3;
        y = drawWrapped(g, font, Component.translatable("gui.mystcraft.book.summary.discovered", summary.discovered(), summary.total()), px, y, pw, 0xFF3A3A3A);
        if (!summary.authors().isEmpty()) {
            y = drawWrapped(g, font, Component.translatable("gui.mystcraft.book.summary.authors", String.join(", ", summary.authors())), px, y, pw, 0xFF3A3A3A);
        }
    }

    /** Draws word-wrapped text, returns the y below the last line (design-space units). */
    private static int drawWrapped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) {
            g.text(font, line, x, y, color, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    private static void blit(GuiGraphicsExtractor g, Identifier tex, int x, int y, int u, int v, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, 256, 256);
    }

    // --- input -----------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isEnabled() || button != 0) return false;
        double lx = (mx - x) / xScale;
        double ly = (my - y) / yScale;
        if (lx < 0 || ly < 0 || lx > DESIGN_W || ly > DESIGN_H) return false;
        if (container.getCurrentPageIndex() == 0 && lx >= 173 && lx <= 305 && ly >= 20 && ly <= 103) {
            container.onLink();
            return true;
        }
        if (lx <= 156 && ly <= 195) {
            pageLeft();
            return true;
        }
        if (lx >= 158 && lx <= 312 && ly <= 195) {
            pageRight();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isEnabled()) return false;
        int key = event.key();
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            pageLeft();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            pageRight();
            return true;
        }
        return false;
    }

    private void pageLeft() {
        int p = Math.max(0, container.getCurrentPageIndex() - 1);
        if (p != container.getCurrentPageIndex()) container.setCurrentPageIndex(p);
    }

    private void pageRight() {
        int p = Math.min(container.getPageCount(), container.getCurrentPageIndex() + 1);
        if (p != container.getCurrentPageIndex()) container.setCurrentPageIndex(p);
    }

    @Override
    public @Nullable List<Component> tooltip() {
        return hoverSymbol && !hoverText.isEmpty() ? hoverText : null;
    }
}
