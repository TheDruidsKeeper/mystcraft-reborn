package com.techbucketdivision.mystcraft.client.screen;

import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.api.symbol.ColorGradient;
import com.techbucketdivision.mystcraft.client.screen.gui.GuiElement;
import com.techbucketdivision.mystcraft.linking.InkEffects;
import com.techbucketdivision.mystcraft.menu.InkMixerMenu;
import com.techbucketdivision.mystcraft.registry.ModItems;
import com.techbucketdivision.mystcraft.util.Colors;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ink Mixer screen (original spec §8.3, 176×181, {@code inkmixer.png}) with the clickable basin. Every slot carries a
 * ghost item + tooltip saying what it takes; the basin's tooltip explains the mixing and lists the current effects.
 */
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
        hintSlot(0, ModItems.INK_VIAL.get(), "gui.mystcraft.ink_mixer.slot.ink");
        hintSlot(1, Items.PAPER, "gui.mystcraft.ink_mixer.slot.paper");
        hintSlot(2, Items.GLASS_BOTTLE, "gui.mystcraft.ink_mixer.slot.empty");
        hintSlot(InkMixerMenu.SLOT_OUTPUT, ModItems.PAGE.get(), "gui.mystcraft.ink_mixer.slot.output");
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
        caption(g, title, leftPos + 8, topPos + 6);
        caption(g, Component.translatable("container.inventory"), leftPos + 8, topPos + imageHeight - 96 + 2);
        // arrows of intent: in -> basin, basin -> out
        caption(g, "gui.mystcraft.ink_mixer.in", leftPos + 27, topPos + 31);
        captionRight(g, Component.translatable("gui.mystcraft.ink_mixer.out"), leftPos + 149, topPos + 31);
        if (!menu.hasInk()) {
            Component empty = Component.translatable("gui.mystcraft.ink_mixer.empty");
            g.text(font, empty, leftPos + 88 - font.width(empty) / 2, topPos + 45, CAPTION_LIGHT, true);
        }
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
        private boolean hovered;

        Basin(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        private boolean inDisc(double mx, double my) {
            double dx = mx - leftPos - BASIN_X;
            double dy = my - topPos - BASIN_Y;
            return dx * dx + dy * dy < BASIN_R_SQ;
        }

        @Override
        public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            hovered = inDisc(mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!inDisc(mx, my) || carried().isEmpty()) return false;
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Single", button == 1);
            send(InkMixerMenu.MSG_CONSUME, tag);
            return true;
        }

        @Override
        public @Nullable List<Component> tooltip() {
            if (!hovered) return null;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.mystcraft.ink_mixer.basin"));
            if (!menu.hasInk()) {
                lines.add(Component.translatable("gui.mystcraft.ink_mixer.basin.empty").withStyle(ChatFormatting.GRAY));
                return lines;
            }
            ItemStack held = carried();
            if (!held.isEmpty()) {
                InkEffects.Ingredient ingredient = InkEffects.ingredientFor(held);
                if (ingredient == null) {
                    lines.add(Component.translatable("gui.mystcraft.ink_mixer.basin.not_modifier", held.getHoverName()).withStyle(ChatFormatting.RED));
                    addIngredientList(lines);
                } else if (ingredient.clears()) {
                    lines.add(Component.translatable("gui.mystcraft.ink_mixer.basin.clear", held.getHoverName()).withStyle(ChatFormatting.YELLOW));
                } else {
                    LinkProperty property = ingredient.effect();
                    boolean already = menu.getEffects().contains(property);
                    lines.add(Component.translatable("gui.mystcraft.ink_mixer.basin.add", held.getHoverName()).withStyle(ChatFormatting.YELLOW));
                    lines.add(Component.literal("  + ").append(Component.translatable(property.descriptionId()))
                            .append(already ? Component.translatable("gui.mystcraft.ink_mixer.basin.already") : Component.empty())
                            .withStyle(already ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
                }
            } else {
                lines.add(Component.translatable("gui.mystcraft.ink_mixer.basin.hint").withStyle(ChatFormatting.GRAY));
                addIngredientList(lines);
            }
            lines.add(Component.translatable("gui.mystcraft.ink_mixer.properties").withStyle(ChatFormatting.AQUA));
            if (menu.getEffects().isEmpty()) {
                lines.add(Component.translatable("gui.mystcraft.ink_mixer.properties.none").withStyle(ChatFormatting.DARK_GRAY));
            }
            for (LinkProperty property : menu.getEffects()) {
                lines.add(Component.literal("  ").append(Component.translatable(property.descriptionId())));
            }
            return lines;
        }

        /** "Ingredients (one item each):" followed by one line per item ("Gunpowder: Disarm"); the clearing item last. */
        private void addIngredientList(List<Component> lines) {
            lines.add(Component.translatable("gui.mystcraft.ink_mixer.ingredients").withStyle(ChatFormatting.YELLOW));
            for (InkEffects.Ingredient ingredient : InkEffects.getIngredients()) {
                lines.add(Component.literal("  ").append(ingredient.example().getHoverName().copy().withStyle(ChatFormatting.WHITE)).append(": ")
                        .append(ingredient.clears()
                                ? Component.translatable("gui.mystcraft.ink_mixer.ingredient.clears")
                                : Component.translatable(ingredient.effect().descriptionId()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
