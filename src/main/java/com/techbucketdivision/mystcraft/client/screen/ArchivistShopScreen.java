package com.techbucketdivision.mystcraft.client.screen;

import com.techbucketdivision.mystcraft.client.render.SymbolGlyphs;
import com.techbucketdivision.mystcraft.client.screen.gui.GuiElement;
import com.techbucketdivision.mystcraft.client.screen.gui.ToggleButton;
import com.techbucketdivision.mystcraft.menu.ArchivistShopMenu;
import com.techbucketdivision.mystcraft.registry.ModItems;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Archivist shop screen (original spec §8.8, 176×181, {@code tradeshop.png}). */
public class ArchivistShopScreen extends AbstractMystcraftScreen<ArchivistShopMenu> {
    private static final Identifier SHOP = MystIds.id("textures/gui/tradeshop.png");
    private static final int LABEL_H = 10;
    private static final int BUTTON_H = 12;

    private @Nullable ItemStack emerald;
    private @Nullable ItemStack booster;

    public ArchivistShopScreen(ArchivistShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 181);
    }

    @Override
    protected void buildElements() {
        emerald = new ItemStack(Items.EMERALD);
        booster = new ItemStack(ModItems.SEALED_NOTEBOOK.get());
        int slotLeft = 28, padding = 3;
        int panelW = (imageWidth - slotLeft - padding - 3) / 3 - 1;
        int panelH = panelW * 3 / 2;
        for (int i = 0; i < ArchivistShopMenu.SHOP_SLOTS; i++) {
            addElement(new ShopPanel(i, leftPos + slotLeft, topPos + 4, panelW, panelH));
            int index = i;
            addElement(new ToggleButton(leftPos + slotLeft, topPos + 4 + panelH - BUTTON_H, panelW, BUTTON_H, "Buy", () -> false, () -> buyPage(index))
                    .tooltip(Component.translatable("gui.mystcraft.shop.buy_page")));
            slotLeft += panelW + padding;
        }
        addElement(new ToggleButton(leftPos + 7, topPos + 27, 18, 18, "Buy", () -> false, this::buyBooster)
                .tooltip(Component.translatable("gui.mystcraft.shop.buy_booster", menu.getBoosterCost())));
    }

    private void buyPage(int index) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Index", index);
        sendOnly(ArchivistShopMenu.MSG_PURCHASE_ITEM, tag);
    }

    private void buyBooster() {
        sendOnly(ArchivistShopMenu.MSG_PURCHASE_BOOSTER);
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.blit(RenderPipelines.GUI_TEXTURED, SHOP, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        // booster icon + count
        if (booster != null) {
            g.item(booster, leftPos + 8, topPos + 9);
            g.text(font, Integer.toString(menu.getBoosterCount()), leftPos + 20, topPos + 17, 0xFFFFFFFF, true);
        }
        // emerald balance label
        int labelX = 40, labelY = LABEL_H;
        int lx = leftPos + imageWidth - 4 - labelX, ly = topPos + 81;
        g.fill(lx, ly, lx + labelX, ly + labelY, 0x44000000);
        g.text(font, Integer.toString(menu.getPlayerEmeralds()), lx + 2, ly + 1, 0xFF88FF88, false);
        if (emerald != null) drawSmallItem(g, emerald, lx - labelY, ly, labelY);
        g.text(font, Component.translatable("container.inventory"), leftPos + 8, topPos + imageHeight - 96 + 2, 0xFF404040, false);
    }

    private static void drawSmallItem(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }

    /** One shop slot: page preview, name, price. */
    private final class ShopPanel extends GuiElement {
        private final int index;
        private boolean hovered;

        ShopPanel(int index, int x, int y, int w, int h) {
            super(x, y, w, h);
            this.index = index;
        }

        @Override
        public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            ItemStack page = menu.getShopItem(index);
            int panelY = height - BUTTON_H - LABEL_H * 2;
            g.fill(x, y, x + width, y + panelY, 0x7F000000);
            int pageW = width / 2;
            int pageH = pageW * 4 / 3;
            int px = x + (width - pageW) / 2;
            int py = y + Math.max(0, (panelY - pageH) / 2);
            hovered = !page.isEmpty() && contains(mouseX, mouseY, px, py, pageW, pageH);
            SymbolGlyphs.drawPage(g, page, px, py, pageW, pageH);
            // name
            int ny = y + panelY;
            g.fill(x, ny, x + width, ny + LABEL_H, 0x7F000000);
            if (!page.isEmpty()) {
                String name = page.getHoverName().getString();
                float scale = Math.min(1f, (width - 2) / (float) Math.max(1, font.width(name)));
                g.pose().pushMatrix();
                g.pose().translate(x + 1, ny + 1);
                g.pose().scale(scale, scale);
                g.text(font, name, 0, 0, 0xFFDDDDDD, false);
                g.pose().popMatrix();
            }
            // price
            int pry = ny + LABEL_H;
            g.fill(x, pry, x + width - LABEL_H, pry + LABEL_H, 0x7F000000);
            if (!page.isEmpty()) {
                g.text(font, Integer.toString(menu.getShopItemPrice(index)), x + 2, pry + 1, 0xFF88FF88, false);
                if (emerald != null) drawSmallItem(g, emerald, x + width - LABEL_H, pry, LABEL_H);
            }
        }

        @Override
        public @Nullable List<Component> tooltip() {
            if (!hovered) return null;
            ItemStack page = menu.getShopItem(index);
            return page.isEmpty() ? null : Screen.getTooltipFromItem(Minecraft.getInstance(), page);
        }
    }
}
