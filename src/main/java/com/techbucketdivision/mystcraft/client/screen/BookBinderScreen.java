package com.techbucketdivision.mystcraft.client.screen;

import com.techbucketdivision.mystcraft.client.screen.gui.GuiElement;
import com.techbucketdivision.mystcraft.client.screen.gui.ScrollablePages;
import com.techbucketdivision.mystcraft.menu.BookBinderMenu;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Book Binder screen (REQUIREMENTS §8.2, 176×181, {@code pagebinder.png}). */
public class BookBinderScreen extends AbstractMystcraftScreen<BookBinderMenu> {
    private static final Identifier BINDER = MystIds.id("textures/gui/pagebinder.png");

    private @Nullable EditBox titleBox;
    private boolean syncingTitle;

    public BookBinderScreen(BookBinderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 181);
    }

    @Override
    protected void buildElements() {
        int gx = leftPos, gy = topPos;
        titleBox = addEditBox(new EditBox(font, gx + 7, gy + 9, imageWidth - 60, 14, Component.translatable("gui.mystcraft.item_name")));
        titleBox.setMaxLength(BookBinderMenu.MAX_TITLE);
        titleBox.setHint(Component.translatable("gui.mystcraft.book_binder.title.hint"));
        titleBox.setResponder(text -> {
            if (syncingTitle) return;
            CompoundTag tag = new CompoundTag();
            tag.putString("Title", text);
            send(BookBinderMenu.MSG_SET_TITLE, tag);
        });
        syncTitle(true);

        addElement(new ScrollablePages(gx + 7, gy + 45, imageWidth - 14, 40, menu::getPageList, this::carried, new ScrollablePages.Handler() {
            @Override
            public void place(int index, boolean single) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Index", index);
                tag.putBoolean("Single", single);
                send(BookBinderMenu.MSG_INSERT_HELD_AT, tag);
            }

            @Override
            public void remove(int index) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Index", index);
                send(BookBinderMenu.MSG_TAKE_FROM_SLIDER, tag);
            }
        }));
        addElement(new MissingPanelIcon(gx + 27, gy + 26));
        addElement(new com.techbucketdivision.mystcraft.client.screen.gui.HintText(gx + 7, gy + 45, imageWidth - 14, 40,
                () -> Component.translatable("gui.mystcraft.book_binder.pages.hint"), () -> menu.getPageList().isEmpty(), CAPTION_LIGHT, true));
        hintSlot(BookBinderMenu.SLOT_COVER, net.minecraft.world.item.Items.LEATHER, "gui.mystcraft.book_binder.slot.cover");
        hintSlot(BookBinderMenu.SLOT_OUTPUT, com.techbucketdivision.mystcraft.registry.ModItems.DESCRIPTIVE_BOOK.get(), "gui.mystcraft.book_binder.slot.output");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncTitle(false);
    }

    private void syncTitle(boolean force) {
        if (titleBox == null) return;
        String title = menu.getPendingTitle();
        if ((force || !titleBox.isFocused()) && !titleBox.getValue().equals(title)) {
            syncingTitle = true;
            titleBox.setValue(title);
            syncingTitle = false;
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.blit(RenderPipelines.GUI_TEXTURED, BINDER, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        caption(g, Component.translatable("container.inventory"), leftPos + 8, topPos + imageHeight - 96 + 2);
        caption(g, "gui.mystcraft.book_binder.pages", leftPos + 50, topPos + 36);
        if (titleBox != null && titleBox.getValue().isEmpty()) {
            g.outline(titleBox.getX() - 1, titleBox.getY() - 1, titleBox.getWidth() + 2, titleBox.getHeight() + 2, 0xFFFF0000);
        }
    }

    /** Pulsing red icon shown while page 0 is not a link panel. */
    private final class MissingPanelIcon extends GuiElement {
        private boolean hovered;
        private int ticks;

        MissingPanelIcon(int x, int y) {
            super(x, y, 18, 18);
        }

        @Override
        public boolean isVisible() {
            return super.isVisible() && menu.isMissingLinkPanel();
        }

        @Override
        public void tick() {
            ticks++;
        }

        @Override
        public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            hovered = contains(mouseX, mouseY);
            float pulse = 0.6f + 0.4f * Mth.sin((ticks + partialTick) * 0.25f);
            int alpha = Mth.clamp((int) (pulse * 255f), 0, 255);
            int color = (alpha << 24) | 0xFF4040;
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(width / 30f, height / 40f);
            g.blit(RenderPipelines.GUI_TEXTURED, BINDER, 0, 0, 176, 0, 30, 40, 256, 256, color);
            g.pose().popMatrix();
        }

        @Override
        public @Nullable List<Component> tooltip() {
            return hovered ? List.of(Component.translatable("gui.mystcraft.binder.missing_link_panel")) : null;
        }
    }
}
