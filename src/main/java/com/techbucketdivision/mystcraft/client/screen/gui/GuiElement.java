package com.techbucketdivision.mystcraft.client.screen.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Lightweight, screen-owned element (port of the original {@code GuiElement}): absolute screen coordinates, rendered
 * from the screen's background pass and fed input by {@code AbstractMystcraftScreen}. Not a vanilla widget — these
 * elements draw custom content (page grids, book pages, tanks) that has no vanilla equivalent.
 */
public abstract class GuiElement {
    protected int x, y, width, height;
    private boolean visible = true;
    private boolean enabled = true;

    protected GuiElement(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Modifier keys of the input event currently being dispatched. 26.1 removed {@code Screen.hasShiftDown()}
     * (modifiers travel with the event), so the owning screen latches them here before forwarding mouse events.
     */
    private static boolean shiftHeld, controlHeld;

    public static void setModifiers(boolean shift, boolean control) {
        shiftHeld = shift;
        controlHeld = control;
    }

    public static boolean isShiftHeld() { return shiftHeld; }
    public static boolean isControlHeld() { return controlHeld; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isEnabled() { return enabled && isVisible(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    public static boolean contains(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Called every client tick. */
    public void tick() {}

    /** Draws the element (only called while visible). */
    public abstract void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick);

    /** @return true when the click was consumed */
    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double scrollY) {
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    /** Tooltip lines for the current hover position (computed during {@link #render}), or {@code null}. */
    public @Nullable List<Component> tooltip() {
        return null;
    }
}
