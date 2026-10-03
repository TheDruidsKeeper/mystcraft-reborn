package com.techbucketdivision.mystcraft.client.screen.gui;

import com.techbucketdivision.mystcraft.client.render.SymbolGlyphs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Horizontal strip of page tiles with scroll arrows on both ends (port of {@code GuiElementScrollablePages}).
 * Click a page → {@link Handler#remove}; click with an item in hand → {@link Handler#place} at the hovered index
 * (or at the end), right click = single.
 */
public class ScrollablePages extends GuiElement {
    public interface Handler {
        void place(int index, boolean single);

        void remove(int index);
    }

    private final Supplier<@Nullable List<ItemStack>> pages;
    private final Supplier<ItemStack> carried;
    private final Handler handler;
    private final int elementWidth, elementHeight, arrowWidth;
    private int first;
    private int hoverIndex = -1;
    private boolean mouseOver;
    private final List<Component> tooltip = new ArrayList<>();
    /** Page index -> draft (washed-out tile); null when the owner has no drafts. */
    private java.util.function.@Nullable IntPredicate draftCheck;

    public void setDraftCheck(java.util.function.@Nullable IntPredicate check) {
        this.draftCheck = check;
    }

    public ScrollablePages(int x, int y, int width, int height, Supplier<@Nullable List<ItemStack>> pages, Supplier<ItemStack> carried, Handler handler) {
        super(x, y, width, height);
        this.pages = pages;
        this.carried = carried;
        this.handler = handler;
        this.elementHeight = height - 6;
        this.elementWidth = elementHeight * 3 / 4;
        this.arrowWidth = Math.max(6, height / 5);
    }

    @Override
    public boolean isVisible() {
        return super.isVisible() && pages.get() != null;
    }

    private int pageCount() {
        List<ItemStack> list = pages.get();
        return list == null ? 0 : list.size();
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        mouseOver = contains(mouseX, mouseY);
        g.fill(x, y, x + width, y + height, 0xAA000000);
        List<ItemStack> list = pages.get();
        int newHover = -1;
        g.enableScissor(x + 1, y, x + width - 1, y + height);
        if (list != null) {
            first = Math.max(0, Math.min(first, Math.max(0, list.size() - 1)));
            int px = x + 2;
            int py = y + 3;
            for (int i = first; i < list.size(); i++) {
                ItemStack page = list.get(i);
                SymbolGlyphs.drawPage(g, page, px, py, elementWidth, elementHeight);
                if (draftCheck != null && draftCheck.test(i)) {
                    // pencilled draft: washed out until the folder leaves the desk
                    g.fill(px, py, px + elementWidth, py + elementHeight, 0x80D8D0C0);
                }
                if (mouseOver && contains(mouseX, mouseY, px, py, elementWidth, elementHeight)) newHover = i;
                px += elementWidth + 2;
                if (px > x + width) break;
            }
        }
        g.disableScissor();
        int leftColor = first == 0 ? 0x33000000 : 0xAA000000;
        int rightColor = list == null || list.isEmpty() || list.size() - 1 == first ? 0x33000000 : 0xAA000000;
        g.fill(x, y, x + arrowWidth, y + height, leftColor);
        g.fill(x + width - arrowWidth, y, x + width, y + height, rightColor);
        if (newHover != hoverIndex) {
            hoverIndex = newHover;
            tooltip.clear();
            if (hoverIndex >= 0 && list != null && hoverIndex < list.size() && !list.get(hoverIndex).isEmpty()) {
                tooltip.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), list.get(hoverIndex)));
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isEnabled() || !contains(mx, my)) return false;
        if (mx < x + arrowWidth) {
            scrollLeft();
            return true;
        }
        if (mx >= x + width - arrowWidth) {
            scrollRight();
            return true;
        }
        if (!carried.get().isEmpty()) {
            int index = hoverIndex >= 0 ? hoverIndex : pageCount();
            handler.place(index, button == 1);
            return true;
        }
        if (hoverIndex >= 0 && button == 0) {
            handler.remove(hoverIndex);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!isEnabled() || !mouseOver) return false;
        if (scrollY > 0) scrollLeft(); else scrollRight();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isEnabled()) return false;
        if (event.key() == GLFW.GLFW_KEY_LEFT) {
            scrollLeft();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT) {
            scrollRight();
            return true;
        }
        return false;
    }

    private void scrollLeft() {
        if (first > 0) first--;
    }

    private void scrollRight() {
        if (first < pageCount() - 1) first++;
    }

    @Override
    public @Nullable List<Component> tooltip() {
        return hoverIndex >= 0 && !tooltip.isEmpty() ? tooltip : null;
    }
}
