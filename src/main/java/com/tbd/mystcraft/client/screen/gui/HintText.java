package com.tbd.mystcraft.client.screen.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Word-wrapped, centred instruction text drawn over an area while a condition holds (e.g. an empty page surface or
 * page strip): tells the player what to put where instead of leaving a blank box.
 */
public class HintText extends GuiElement {
    private final Supplier<Component> text;
    private final BooleanSupplier shown;
    private final int color;
    private final boolean shadow;

    public HintText(int x, int y, int width, int height, Supplier<Component> text, BooleanSupplier shown, int argb, boolean shadow) {
        super(x, y, width, height);
        this.text = text;
        this.shown = shown;
        this.color = argb;
        this.shadow = shadow;
    }

    @Override
    public boolean isVisible() {
        return super.isVisible() && shown.getAsBoolean();
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(text.get(), Math.max(20, width - 8));
        int total = lines.size() * font.lineHeight;
        int ty = y + Math.max(2, (height - total) / 2);
        for (FormattedCharSequence line : lines) {
            int tw = font.width(line);
            g.text(font, line, x + (width - tw) / 2, ty, color, shadow);
            ty += font.lineHeight;
        }
    }
}
