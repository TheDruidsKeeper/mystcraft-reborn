package com.techbucketdivision.mystcraft.client.screen;

import com.techbucketdivision.mystcraft.api.symbol.ColorGradient;
import com.techbucketdivision.mystcraft.client.screen.gui.GuiElement;
import com.techbucketdivision.mystcraft.menu.InkMixerMenu;
import com.techbucketdivision.mystcraft.util.Colors;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Ink Mixer screen (REQUIREMENTS §8.3, 176×181, {@code inkmixer.png}) with the clickable basin. */
public class InkMixerScreen extends AbstractMystcraftScreen<InkMixerMenu> {
    private static final Identifier MIXER = MystIds.id("textures/gui/inkmixer.png");
    private static final int BASIN_X = 88, BASIN_Y = 49, BASIN_R_SQ = 900;

    private int frame;

    public InkMixerScreen(InkMixerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 181);
    }

    @Override
    protected void buildElements() {
        addElement(new Basin(leftPos + 54, topPos + 16, 66, 65));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        menu.updateCraftResult();
        frame++;
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // basin backdrop (texture region 179,16 66×65), then ink, then the frame with the cut-out
        g.blit(RenderPipelines.GUI_TEXTURED, MIXER, leftPos + 54, topPos + 16, 179, 16, 66, 65, 256, 256);
        if (menu.hasInk()) renderInk(g, leftPos + 54, topPos + 16, 66, 65, partialTick);
        g.blit(RenderPipelines.GUI_TEXTURED, MIXER, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        g.text(font, Component.translatable("container.inventory"), leftPos + 8, topPos + imageHeight - 96 + 2, 0xFF404040, false);
    }

    private void renderInk(GuiGraphicsExtractor g, int left, int top, int w, int h, float partialTick) {
        g.fill(left, top, left + w, top + h, 0xFF191919);
        ColorGradient gradient = menu.getPropertyGradient();
        if (gradient == null || gradient.isEmpty()) return;
        float t = (frame + partialTick) / 300f;
        Colors.RGB c = gradient.getColor(t * Math.max(1f, gradient.totalLength())).clamp();
        int rgb = c.toRGB() & 0xFFFFFF;
        g.fillGradient(left, top, left + w, top + h, 0x40000000 | rgb, 0xB0000000 | rgb);
        // D'ni colour "eye": concentric rings of the current colour (DniColorRenderer port is Phase 2)
        int cx = left + w / 2 + 1, cy = top + h / 2 + 1;
        for (int r = 20; r > 0; r -= 5) {
            int a = 0x60 + (20 - r) * 6;
            drawRing(g, cx, cy, r, (a << 24) | rgb);
        }
    }

    private static void drawRing(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2;
            int px = cx + (int) Math.round(Math.cos(a) * r);
            int py = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(px - 1, py - 1, px + 1, py + 1, color);
        }
    }

    /** Invisible clickable disc over the basin: click with an item to consume it as an ink modifier. */
    private final class Basin extends GuiElement {
        Basin(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {}

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            double dx = mx - leftPos - BASIN_X;
            double dy = my - topPos - BASIN_Y;
            if (dx * dx + dy * dy >= BASIN_R_SQ || carried().isEmpty()) return false;
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Single", button == 1);
            send(InkMixerMenu.MSG_CONSUME, tag);
            return true;
        }
    }
}
