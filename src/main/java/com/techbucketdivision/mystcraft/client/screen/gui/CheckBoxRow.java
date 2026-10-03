package com.techbucketdivision.mystcraft.client.screen.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A labelled check box row: a small box (optionally colour-coded) followed by text, with a tooltip explaining the
 * option. Clicking anywhere on the row toggles it. Replaces the unlabelled coloured squares of the first Link Modifier
 * design.
 */
public class CheckBoxRow extends GuiElement {
    private static final int BOX = 9;

    private final Component label;
    private final BooleanSupplier state;
    private final Runnable onToggle;
    private final int swatch;
    private @Nullable List<Component> tooltip;
    private boolean hovered;

    public CheckBoxRow(int x, int y, int width, Component label, int swatchArgb, BooleanSupplier state, Runnable onToggle) {
        super(x, y, width, BOX + 2);
        this.label = label;
        this.swatch = swatchArgb;
        this.state = state;
        this.onToggle = onToggle;
    }

    public CheckBoxRow tooltip(@Nullable List<Component> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hovered = contains(mouseX, mouseY);
        boolean checked = state.getAsBoolean();
        boolean enabled = isEnabled();
        int border = enabled ? 0xFF373737 : 0xFF777777;
        int face = enabled ? (hovered ? 0xFFFFFFFF : 0xFFE8E8E8) : 0xFFB0B0B0;
        g.fill(x, y + 1, x + BOX, y + 1 + BOX, border);
        g.fill(x + 1, y + 2, x + BOX - 1, y + BOX, face);
        if (checked) {
            // tick mark: filled inner square in the property colour (dark outline keeps it readable on light boxes)
            g.fill(x + 2, y + 3, x + BOX - 2, y + BOX - 1, 0xFF202020);
            g.fill(x + 3, y + 4, x + BOX - 3, y + BOX - 2, swatch);
        }
        Font font = Minecraft.getInstance().font;
        int color = enabled ? (checked ? 0xFF202020 : 0xFF404040) : 0xFF8A8A8A;
        g.text(font, label, x + BOX + 3, y + 1, color, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isEnabled() || button != 0 || !contains(mx, my)) return false;
        onToggle.run();
        return true;
    }

    @Override
    public @Nullable List<Component> tooltip() {
        return hovered && isVisible() ? tooltip : null;
    }
}
