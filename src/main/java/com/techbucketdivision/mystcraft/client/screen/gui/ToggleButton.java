package com.tbd.mystcraft.client.screen.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Small square button with a text label (or none) whose pressed state comes from a supplier. */
public class ToggleButton extends GuiElement {
    private @Nullable String label;
    private @Nullable Component labelText;
    private final BooleanSupplier state;
    private final Runnable onClick;
    private @Nullable List<Component> tooltip;
    private int color = 0xFFFFFFFF;
    private boolean hovered;

    public ToggleButton(int x, int y, int size, @Nullable String label, BooleanSupplier state, Runnable onClick) {
        this(x, y, size, size, label, state, onClick);
    }

    public ToggleButton(int x, int y, int width, int height, @Nullable String label, BooleanSupplier state, Runnable onClick) {
        super(x, y, width, height);
        this.label = label;
        this.state = state;
        this.onClick = onClick;
    }

    public ToggleButton tooltip(@Nullable List<Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public ToggleButton tooltip(Component line) {
        return tooltip(List.of(line));
    }

    /** Text drawn centred on the button (translatable), used for wide buttons. */
    public ToggleButton label(Component text) {
        this.labelText = text;
        return this;
    }

    /** Fill colour (ARGB) of the button face. */
    public ToggleButton color(int argb) {
        this.color = argb;
        return this;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hovered = contains(mouseX, mouseY);
        boolean pressed = state.getAsBoolean();
        int border = isEnabled() ? 0xFF000000 : 0xFF555555;
        int face = !isEnabled() ? 0xFF808080 : pressed ? 0xFF6060C0 : hovered ? 0xFFC0C0FF : color;
        g.fill(x, y, x + width, y + height, border);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, face);
        var font = Minecraft.getInstance().font;
        if (labelText != null) {
            int tw = font.width(labelText);
            g.text(font, labelText, x + (width - tw) / 2, y + (height - font.lineHeight) / 2 + 1, isEnabled() ? 0xFF202020 : 0xFF606060, false);
        } else if (label != null) {
            int tw = font.width(label);
            g.text(font, label, x + (width - tw) / 2, y + (height - font.lineHeight) / 2 + 1, 0xFF202020, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isEnabled() || button != 0 || !contains(mx, my)) return false;
        onClick.run();
        return true;
    }

    @Override
    public @Nullable List<Component> tooltip() {
        return hovered && isVisible() ? tooltip : null;
    }
}
