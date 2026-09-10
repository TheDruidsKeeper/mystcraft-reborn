package com.techbucketdivision.mystcraft.client.screen.gui;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.client.render.SymbolGlyphs;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Left column of the Writing Desk: 4 visible notebook tabs (of 25) with up/down arrows, each showing the numeral
 * glyph and the notebook's name (port of {@code GuiElementSurfaceTabs}, REQUIREMENTS §8.1).
 */
public class NotebookTabs extends GuiElement {
    public interface Handler {
        ItemStack tabItem(int slot);

        int firstTab();

        int activeTab();

        int maxTabs();

        void setFirstTab(int first);

        /** Click on a tab (button 0/1). */
        void tabClicked(int slot, int button);
    }

    private static final Identifier DESK = MystIds.id("textures/gui/writingdesk.png");
    private static final int TAB_W = 58, TAB_H = 37, ARROW_H = 9, VISIBLE = 4, WINDOW_Y = 166;

    private final Handler handler;

    public NotebookTabs(int x, int y, int height, Handler handler) {
        super(x, y, TAB_W, height);
        this.handler = handler;
    }

    private void cycleUp() {
        int first = handler.firstTab();
        if (first > 0) handler.setFirstTab(first - 1);
    }

    private void cycleDown() {
        int first = handler.firstTab();
        if (first < handler.maxTabs() - VISIBLE) handler.setFirstTab(first + 1);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_W) {
            cycleUp();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_S) {
            cycleDown();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isEnabled()) return false;
        int first = handler.firstTab();
        int ty = y;
        if (contains(mx, my, x, ty, TAB_W, ARROW_H)) {
            cycleUp();
            return true;
        }
        ty += ARROW_H;
        for (int slot = first; slot < first + VISIBLE; slot++) {
            // the tab body minus the 19×19 slot square at (35, 2)
            if (contains(mx, my, x, ty + 1, TAB_W, 35) && !contains(mx, my, x + 35, ty + 2, 19, 19)) {
                handler.tabClicked(slot, button);
                return true;
            }
            ty += TAB_H;
        }
        if (contains(mx, my, x, ty, TAB_W, ARROW_H)) {
            cycleDown();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!contains(mx, my)) return false;
        if (scrollY > 0) cycleUp(); else cycleDown();
        return true;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int first = handler.firstTab();
        int active = handler.activeTab();
        int ty = y;
        int color = active < first ? 0xFF8080FF : 0xFFFFFFFF;
        if (first == 0) color = 0xFF666666;
        g.blit(RenderPipelines.GUI_TEXTURED, DESK, x, ty, 0, WINDOW_Y + TAB_H, TAB_W, ARROW_H, 256, 256, color);
        ty += ARROW_H;
        for (int slot = first; slot < first + VISIBLE; slot++) {
            int tabColor = slot == active ? 0xFF8080FF : 0xFFFFFFFF;
            g.blit(RenderPipelines.GUI_TEXTURED, DESK, x, ty, 0, WINDOW_Y, TAB_W, TAB_H, 256, 256, tabColor);
            SymbolGlyphs.drawWord(g, Integer.toString(slot), x + 8, ty + 3, 19);
            ItemStack item = handler.tabItem(slot);
            if (!item.isEmpty()) {
                String name = item.getItem() instanceof ItemBehaviours.Renameable r ? r.getDisplayName(item) : null;
                if (name == null) name = item.getHoverName().getString();
                float scale = 1f;
                int w = font.width(name) + 16;
                if (w > TAB_W) scale = TAB_W / (float) w;
                g.pose().pushMatrix();
                g.pose().translate(x + 4, ty + 25);
                g.pose().scale(scale, scale);
                g.text(font, name, 0, 0, 0xFF404040, false);
                g.pose().popMatrix();
            }
            ty += TAB_H;
        }
        color = active >= first + VISIBLE ? 0xFF8080FF : 0xFFFFFFFF;
        if (first + VISIBLE >= handler.maxTabs()) color = 0xFF666666;
        g.blit(RenderPipelines.GUI_TEXTURED, DESK, x, ty, 0, WINDOW_Y + TAB_H + ARROW_H, TAB_W, ARROW_H, 256, 256, color);
    }
}
