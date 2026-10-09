package com.tbd.mystcraft.client.screen.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Vertical fluid gauge (16×70 in the desk). The ink is drawn as a solid dark fill — the fluid's still sprite is a
 * Phase 2 refinement (sprite lookup through the atlas manager is unverified in 26.1).
 */
public class InkTank extends GuiElement {
    private final Supplier<FluidStack> fluid;
    private final int capacity;
    private boolean hovered;

    public InkTank(int x, int y, int width, int height, Supplier<FluidStack> fluid, int capacity) {
        super(x, y, width, height);
        this.fluid = fluid;
        this.capacity = Math.max(1, capacity);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hovered = contains(mouseX, mouseY);
        g.fill(x, y, x + width, y + height, 0xFF202020);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF404040);
        FluidStack stack = fluid.get();
        if (!stack.isEmpty()) {
            float filled = Math.min(1f, stack.getAmount() / (float) capacity);
            int fillH = (int) ((height - 2) * filled);
            int top = y + height - 1 - fillH;
            g.fill(x + 1, top, x + width - 1, y + height - 1, 0xFF191919);
            g.fill(x + 1, top, x + width - 1, Math.min(top + 1, y + height - 1), 0xFF303030);
        }
        // graduations
        for (int i = 1; i < 4; i++) {
            int ly = y + (height * i) / 4;
            g.fill(x + 1, ly, x + 4, ly + 1, 0xFF606060);
        }
    }

    @Override
    public @Nullable List<Component> tooltip() {
        if (!hovered) return null;
        FluidStack stack = fluid.get();
        if (stack.isEmpty()) return List.of(Component.translatable("gui.mystcraft.tank.empty"));
        return List.of(Component.literal(stack.getHoverName().getString() + ": " + stack.getAmount() + "/" + capacity));
    }
}
