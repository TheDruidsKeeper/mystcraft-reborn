package com.tbd.mystcraft.client.screen;

import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.client.screen.gui.BookElement;
import com.tbd.mystcraft.menu.BookMenu;
import com.tbd.mystcraft.util.MystIds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Book GUI (original spec §8.5) for held books, displays, receptacles and book entities. With a book present the
 * 327×199 book element is shown (book slot at 41,21 on page 0); without one, the single-slot inventory texture so a
 * book can be inserted. The screen keeps the 327×199 frame in both modes (slot coordinates are menu-defined).
 */
public class BookScreen extends AbstractMystcraftScreen<BookMenu> {
    private static final Identifier SINGLE_SLOT = MystIds.id("textures/gui/single_slot.png");

    private @Nullable BookElement bookElement;

    public BookScreen(BookMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BookElement.DESIGN_W, BookElement.DESIGN_H);
    }

    @Override
    protected void buildElements() {
        bookElement = addElement(new BookElement(leftPos, topPos, BookElement.DESIGN_W, BookElement.DESIGN_H, container));
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (menu.getBook().isEmpty() && menu.hasBookSlot()) {
            g.blit(RenderPipelines.GUI_TEXTURED, SINGLE_SLOT, leftPos, topPos, 0, 0, 176, 166, 256, 256);
        }
    }

    /** Adapter over {@link BookMenu}. */
    private final MenuBookContainer container = new MenuBookContainer();

    private final class MenuBookContainer implements BookElement.Container {
        @Override public ItemStack getBook() { return menu.getBook(); }
        @Override public @Nullable LinkInfo getLinkInfo() { return menu.getLinkInfo(); }
        @Override public int getCurrentPageIndex() { return menu.getCurrentPageIndex(); }
        @Override public ItemStack getCurrentPage() { return menu.getCurrentPage(); }
        @Override public int getPageCount() { return menu.getPageCount(); }
        @Override public com.tbd.mystcraft.age.@Nullable AgeSummary getSummary() { return menu.getSummary(); }
        @Override public boolean isLinkPermitted() { return menu.isLinkPermitted(); }
        @Override public boolean isTargetWorldVisited() { return menu.isTargetWorldVisited(); }
        @Override public String getBookTitle() { return menu.getBookTitle(); }
        @Override public List<String> getBookAuthors() { return menu.getBookAuthors(); }
        @Override public boolean hasBookSlot() { return menu.hasBookSlot(); }

        @Override
        public void setCurrentPageIndex(int index) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Index", index);
            send(BookMenu.MSG_SET_CURRENT_PAGE, tag);
        }

        @Override
        public void onLink() {
            sendOnly(BookMenu.MSG_LINK);
        }
    }

    /** Test hook (client self-check): turns to a page as a click on the page edge would. */
    public void jumpToPage(int index) {
        container.setCurrentPageIndex(index);
    }

    /** Test hook (client self-check): clicks the left or right cover trim (first / last page). */
    public boolean clickTrim(boolean right) {
        if (bookElement == null) return false;
        int x = leftPos + (right ? BookElement.DESIGN_W - 10 : 10);
        return bookElement.mouseClicked(x, topPos + 100, 0);
    }
}
