package com.techbucketdivision.mystcraft.client.screen;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.client.render.SymbolGlyphs;
import com.techbucketdivision.mystcraft.client.screen.gui.BookElement;
import com.techbucketdivision.mystcraft.client.screen.gui.InkTank;
import com.techbucketdivision.mystcraft.client.screen.gui.NotebookTabs;
import com.techbucketdivision.mystcraft.client.screen.gui.PageSurface;
import com.techbucketdivision.mystcraft.client.screen.gui.ScrollablePages;
import com.techbucketdivision.mystcraft.client.screen.gui.ToggleButton;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.menu.WritingDeskMenu;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Writing Desk screen (REQUIREMENTS §8.1): notebook tabs + page surface on the left (228 px), the 176×166 desk window
 * shifted by (233, 20) on the right with the target area (book / page strip / page), name field and ink tank.
 */
public class WritingDeskScreen extends AbstractMystcraftScreen<WritingDeskMenu> {
    private static final Identifier DESK = MystIds.id("textures/gui/writingdesk.png");

    private static final int LEFT_W = 228;
    private static final int WINDOW_W = 176;
    private static final int WINDOW_H = 166;
    private static final int BUTTON = 18;
    private static final int CENTER = WritingDeskMenu.X_SHIFT; // 233
    private static final int MAIN_TOP = WritingDeskMenu.Y_SHIFT; // 20

    private @Nullable EditBox searchBox;
    private @Nullable EditBox nameBox;
    private @Nullable PageSurface surface;
    private @Nullable ScrollablePages pageStrip;
    private @Nullable ToggleButton sortButton, allButton;
    private boolean syncingName;

    public WritingDeskScreen(WritingDeskMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, LEFT_W + WINDOW_W + 5, WINDOW_H + BUTTON + 1);
    }

    @Override
    protected void buildElements() {
        int gx = leftPos, gy = topPos;

        // --- left: surface controls, tabs, page surface
        surface = new PageSurface(gx + 58, gy + MAIN_TOP, LEFT_W - 53, WINDOW_H, menu::getActiveNotebook, this::player, new SurfaceHandler());
        sortButton = addElement(new ToggleButton(gx + 58, gy, BUTTON, "AZ", () -> surface != null && surface.isSortAlphabetical(), () -> surface.toggleSort())
                .tooltip(Component.translatable("gui.mystcraft.surface.sort")));
        allButton = addElement(new ToggleButton(gx + 58 + BUTTON, gy, BUTTON, "ALL", () -> surface != null && surface.isShowAll(), () -> surface.toggleShowAll())
                .tooltip(Component.translatable("gui.mystcraft.surface.show_all")));
        addElement(new NotebookTabs(gx, gy + MAIN_TOP, imageHeight - MAIN_TOP, new TabsHandler()));
        addElement(surface);

        searchBox = addEditBox(new EditBox(font, gx + 58 + (BUTTON + 2) * 2, gy, LEFT_W - 53 - (BUTTON + 2) * 2, BUTTON,
                Component.translatable("gui.mystcraft.surface.search")));
        searchBox.setMaxLength(32);
        searchBox.setHint(Component.translatable("gui.mystcraft.surface.search"));
        searchBox.setResponder(text -> {
            if (surface != null) surface.setSearch(text);
        });

        // --- right: target area
        addElement(new InkTank(gx + CENTER + WINDOW_W - 44, gy + MAIN_TOP + 7, 16, 70, menu::getInk, menu.getInkCapacity()));
        addElement(new BookElement(gx + CENTER + 30, gy + MAIN_TOP + 6, 90, 50, new DeskBookContainer()));
        pageStrip = addElement(new ScrollablePages(gx + CENTER + 27, gy + MAIN_TOP + 6, WINDOW_W - 47 - 9 - 19, 50,
                this::stripPages, this::carried, new StripHandler()));

        nameBox = addEditBox(new EditBox(font, gx + CENTER + 28, gy + MAIN_TOP + 61, WINDOW_W - 48 - 9 - 20, 14,
                Component.translatable("gui.mystcraft.item_name")));
        nameBox.setMaxLength(WritingDeskMenu.MAX_TITLE);
        nameBox.setResponder(text -> {
            if (syncingName) return;
            CompoundTag tag = new CompoundTag();
            tag.putString("Title", text);
            send(WritingDeskMenu.MSG_SET_TITLE, tag);
        });
        syncName(true);
    }

    /** Pages shown in the horizontal strip: only for writable non-book, non-page targets (folders). */
    private @Nullable List<ItemStack> stripPages() {
        ItemStack target = menu.getTarget();
        if (target.isEmpty() || !menu.getBook().isEmpty() || target.getItem() instanceof PageItem) return null;
        if (!(target.getItem() instanceof ItemBehaviours.OrderablePageProvider)) return null;
        return menu.getBookPageList();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncName(false);
        boolean collection = surface != null && surface.isCollection();
        if (sortButton != null) sortButton.setEnabled(collection);
        if (allButton != null) allButton.setEnabled(collection);
    }

    private void syncName(boolean force) {
        if (nameBox == null) return;
        ItemStack target = menu.getTarget();
        boolean editable = !target.isEmpty() && !(target.getItem() instanceof PageItem);
        nameBox.setEditable(editable);
        String name = menu.getTargetName();
        if ((force || !nameBox.isFocused()) && !nameBox.getValue().equals(name)) {
            syncingName = true;
            nameBox.setValue(name);
            syncingName = false;
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.blit(RenderPipelines.GUI_TEXTURED, DESK, leftPos + CENTER, topPos + MAIN_TOP, 0, 0, WINDOW_W, WINDOW_H, 256, 256);
        // target page preview when the target is a single page
        ItemStack target = menu.getTarget();
        if (!target.isEmpty() && target.getItem() instanceof PageItem) {
            SymbolGlyphs.drawPage(g, target, leftPos + CENTER + 32, topPos + MAIN_TOP + 6, 37.5f, 50f);
        }
    }

    // --- handlers ---------------------------------------------------------------------------------------------------

    private final class SurfaceHandler implements PageSurface.Handler {
        @Override
        public void place(int index, boolean single) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Tab", menu.getActiveTabSlot());
            tag.putBoolean("Single", single);
            tag.putInt("Index", index);
            send(WritingDeskMenu.MSG_ADD_TO_SURFACE, tag);
        }

        @Override
        public void pickup(PageSurface.Entry entry) {
            if (entry.count <= 0) return;
            if (surface != null && surface.isCollection()) {
                ItemStack page = entry.stack.copy();
                if (shiftDown()) page.setCount(Math.min(64, entry.count)); else page.setCount(1);
                CompoundTag tag = new CompoundTag();
                tag.store("Page", ItemStack.OPTIONAL_CODEC, ops(), page);
                send(WritingDeskMenu.MSG_REMOVE_FROM_COLLECTION, tag);
            } else {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Index", entry.slotId);
                send(WritingDeskMenu.MSG_REMOVE_FROM_ORDERED_COLLECTION, tag);
            }
        }

        @Override
        public void copy(PageSurface.Entry entry) {
            Identifier symbol = PageItem.getSymbolId(entry.stack);
            if (symbol == null || entry.count <= 0) return;
            CompoundTag tag = new CompoundTag();
            tag.putString("Symbol", symbol.toString());
            send(WritingDeskMenu.MSG_WRITE_SYMBOL, tag);
        }
    }

    private final class StripHandler implements ScrollablePages.Handler {
        @Override
        public void place(int index, boolean single) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Index", index);
            tag.putBoolean("Single", single);
            send(WritingDeskMenu.MSG_INSERT_HELD_AT, tag);
        }

        @Override
        public void remove(int index) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Index", index);
            send(WritingDeskMenu.MSG_TAKE_FROM_SLIDER, tag);
        }
    }

    private final class TabsHandler implements NotebookTabs.Handler {
        @Override public ItemStack tabItem(int slot) { return menu.getTabSlot(slot); }
        @Override public int firstTab() { return menu.getFirstTabSlot(); }
        @Override public int activeTab() { return menu.getActiveTabSlot(); }
        @Override public int maxTabs() { return menu.getMaxTabCount(); }

        @Override
        public void setFirstTab(int first) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Tab", first);
            send(WritingDeskMenu.MSG_SET_FIRST_NOTEBOOK, tag);
        }

        @Override
        public void tabClicked(int slot, int button) {
            if (!carried().isEmpty()) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Tab", slot);
                tag.putBoolean("Single", button == 1);
                send(WritingDeskMenu.MSG_ADD_TO_TAB, tag);
            } else if (menu.getActiveTabSlot() != slot) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Tab", slot);
                send(WritingDeskMenu.MSG_SET_ACTIVE_NOTEBOOK, tag);
                if (surface != null) surface.invalidate();
            }
        }
    }

    /** Adapter over the desk menu's book view. */
    private final class DeskBookContainer implements BookElement.Container {
        @Override public ItemStack getBook() { return menu.getBook(); }
        @Override public @Nullable LinkInfo getLinkInfo() { return menu.getLinkInfo(); }
        @Override public int getCurrentPageIndex() { return menu.getBookView().getCurrentPageIndex(); }
        @Override public ItemStack getCurrentPage() { return menu.getBookView().getCurrentPage(); }
        @Override public int getPageCount() { return menu.getBookView().getPageCount(); }
        @Override public boolean isLinkPermitted() { return menu.isLinkPermitted(); }
        @Override public boolean isTargetWorldVisited() { return menu.isTargetWorldVisited(); }
        @Override public String getBookTitle() { return menu.getBookTitle(); }
        @Override public List<String> getBookAuthors() { return menu.getBookAuthors(); }
        @Override public boolean hasBookSlot() { return menu.hasBookSlot(); }

        @Override
        public void setCurrentPageIndex(int index) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Index", index);
            send(WritingDeskMenu.MSG_SET_CURRENT_PAGE, tag);
        }

        @Override
        public void onLink() {
            sendOnly(WritingDeskMenu.MSG_LINK);
        }
    }
}
