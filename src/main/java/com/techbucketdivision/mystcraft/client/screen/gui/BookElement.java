package com.techbucketdivision.mystcraft.client.screen.gui;

import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.client.render.SymbolGlyphs;
import com.techbucketdivision.mystcraft.item.DescriptiveBookItem;
import com.techbucketdivision.mystcraft.item.PageItem;
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
            AgeSymbol symbol = PageItem.getSymbol(current);
            if (symbol != null || PageItem.getSymbolId(current) != null) {
                int sx = 171, sy = 25, size = 140;
                SymbolGlyphs.drawSymbol(g, symbol == null ? null : symbol.poem(), sx, sy, size, 0xFF000000);
                if (contains(mouseX, mouseY, (int) (x + sx * xScale), (int) (y + sy * yScale), (int) (size * xScale), (int) (size * yScale))) {
                    hoverSymbol = true;
                    hoverText.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), current));
                }
            }
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
        String footer = page + "/" + container.getPageCount();
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
