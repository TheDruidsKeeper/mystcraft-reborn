package com.tbd.mystcraft.client.screen;

import com.tbd.mystcraft.api.symbol.AgeSymbol;
import com.tbd.mystcraft.client.screen.gui.GuiElement;
import com.tbd.mystcraft.client.screen.gui.HintText;
import com.tbd.mystcraft.client.screen.gui.InkTank;
import com.tbd.mystcraft.client.screen.gui.ScrollablePages;
import com.tbd.mystcraft.client.screen.gui.SymbolSurface;
import com.tbd.mystcraft.client.screen.gui.ToggleButton;
import com.tbd.mystcraft.item.PageItem;
import com.tbd.mystcraft.knowledge.SymbolKnowledge;
import com.tbd.mystcraft.menu.WritingDeskMenu;
import com.tbd.mystcraft.registry.ModItems;
import com.tbd.mystcraft.util.MystIds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
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
 * Writing Desk screen (original spec §8.1, Reborn rework: world-building plan §4). Left (228 px): search box, category
 * tabs and the symbol surface listing what the player knows; right: the 176×166 desk window shifted by (233, 20) with
 * the folder's page strip, name field and ink tank. A page selected in the strip takes the modifiers
 * clicked on the surface.
 */
public class WritingDeskScreen extends AbstractMystcraftScreen<WritingDeskMenu> {
    private static final Identifier DESK = MystIds.id("textures/gui/writingdesk.png");

    private static final int LEFT_W = 228;
    private static final int WINDOW_W = 176;
    private static final int WINDOW_H = 166;
    private static final int BUTTON = 18;
    private static final int LEFT_TOP = (BUTTON + 1) * 2;
    private static final int CENTER = WritingDeskMenu.X_SHIFT; // 233
    private static final int MAIN_TOP = WritingDeskMenu.Y_SHIFT; // 20

    private @Nullable EditBox searchBox;
    private @Nullable EditBox nameBox;
    private @Nullable SymbolSurface surface;
    private @Nullable ScrollablePages pageStrip;
    private final List<ToggleButton> tabButtons = new ArrayList<>();
    private boolean syncingName;
    private int selectedPage = -1;

    public WritingDeskScreen(WritingDeskMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, LEFT_W + WINDOW_W + 5, WINDOW_H + LEFT_TOP);
    }

    @Override
    protected void buildElements() {
        int gx = leftPos, gy = topPos;
        tabButtons.clear();

        // --- left: search, tabs, symbol surface
        surface = new SymbolSurface(gx, gy + LEFT_TOP, LEFT_W, imageHeight - LEFT_TOP,
                () -> SymbolKnowledge.known(player()), menu::isScholar, new SurfaceHandler());
        searchBox = addEditBox(new EditBox(font, gx, gy, LEFT_W, BUTTON, Component.translatable("gui.mystcraft.surface.search")));
        searchBox.setMaxLength(32);
        searchBox.setHint(Component.translatable("gui.mystcraft.surface.search"));
        searchBox.setResponder(text -> {
            if (surface != null) surface.setSearch(text);
        });
        int tabW = LEFT_W / SymbolSurface.Tab.values().length;
        int tx = gx;
        for (SymbolSurface.Tab tab : SymbolSurface.Tab.values()) {
            int w = tab.ordinal() == SymbolSurface.Tab.values().length - 1 ? gx + LEFT_W - tx : tabW;
            ToggleButton button = addElement(new ToggleButton(tx, gy + BUTTON + 1, w, BUTTON, null,
                    () -> surface != null && surface.tab() == tab, () -> selectTab(tab))
                    .label(tab.shortLabel())
                    .tooltip(List.of(tab.label(), Component.translatable("gui.mystcraft.writing_desk.tab.tooltip").withStyle(ChatFormatting.GRAY))));
            tabButtons.add(button);
            tx += w;
        }
        addElement(surface);

        // --- right: target area
        addElement(new InkTank(gx + CENTER + WINDOW_W - 44, gy + MAIN_TOP + 7, 16, 70, menu::getInk, menu.getInkCapacity()));
        pageStrip = addElement(new ScrollablePages(gx + CENTER + 27, gy + MAIN_TOP + 6, WINDOW_W - 47 - 9 - 19, 50,
                menu::getBookPageList, this::carried, new StripHandler()));
        pageStrip.setDraftCheck(menu::isDraftPage);
        pageStrip.setSelection(() -> selectedPage);

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

        // --- what goes where
        hintSlot(WritingDeskMenu.SLOT_TARGET, ModItems.COLLATION_FOLDER.get(), "gui.mystcraft.writing_desk.slot.target");
        hintSlot(WritingDeskMenu.SLOT_PAPER, Items.PAPER, "gui.mystcraft.writing_desk.slot.paper");
        hintSlot(WritingDeskMenu.SLOT_CONTAINER_IN, ModItems.INK_VIAL.get(), "gui.mystcraft.writing_desk.slot.ink");
        hintSlot(WritingDeskMenu.SLOT_CONTAINER_OUT, Items.GLASS_BOTTLE, "gui.mystcraft.writing_desk.slot.empty");
        // the empty surface explains itself (no symbols known yet, or none in this tab)
        addElement(new HintText(gx, gy + LEFT_TOP, LEFT_W - SymbolSurface.SCROLLBAR_W, imageHeight - LEFT_TOP,
                () -> Component.translatable(SymbolKnowledge.known(player()).isEmpty() && !menu.isScholar()
                        ? "gui.mystcraft.writing_desk.surface.hint" : "gui.mystcraft.writing_desk.surface.empty_tab"),
                () -> surface != null && surface.tileCount() == 0, 0xFFB0B0B0, true));
        // the empty target area explains itself
        addElement(new HintText(gx + CENTER + 27, gy + MAIN_TOP + 6, WINDOW_W - 47 - 9 - 19, 50,
                () -> Component.translatable("gui.mystcraft.writing_desk.target.hint"),
                () -> menu.getTarget().isEmpty(), 0xFF606060, false));
    }

    /** Test hook (client self-check) and tab buttons. */
    public void selectTab(SymbolSurface.Tab tab) {
        if (surface != null) surface.setTab(tab);
    }

    /** Test hook: symbols currently listed on the surface. */
    public int listedSymbols() {
        return surface == null ? 0 : surface.tileCount();
    }

    /** Test hook: selects a page of the folder strip. */
    public void selectPage(int index) {
        selectedPage = index;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncName(false);
        List<ItemStack> pages = menu.getBookPageList();
        if (pages == null || selectedPage >= pages.size() || (selectedPage >= 0 && !PageItem.isSymbolPage(pages.get(selectedPage)))) {
            selectedPage = -1;
        }
    }

    private void syncName(boolean force) {
        if (nameBox == null) return;
        nameBox.setEditable(!menu.getTarget().isEmpty());
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
        if (menu.isScholar()) {
            // above the desk window (the 20 px band the window is shifted down by)
            caption(g, Component.translatable("gui.mystcraft.writing_desk.scholar"), leftPos + CENTER + 4, topPos + 6);
        }
    }

    private @Nullable ItemStack selectedPageStack() {
        List<ItemStack> pages = menu.getBookPageList();
        if (pages == null || selectedPage < 0 || selectedPage >= pages.size()) return null;
        ItemStack page = pages.get(selectedPage);
        return PageItem.isSymbolPage(page) ? page : null;
    }

    // --- handlers ---------------------------------------------------------------------------------------------------

    private final class SurfaceHandler implements SymbolSurface.Handler {
        @Override
        public void write(AgeSymbol symbol) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Symbol", symbol.id().toString());
            send(WritingDeskMenu.MSG_WRITE_SYMBOL, tag);
        }

        @Override
        public void attach(AgeSymbol modifier) {
            ItemStack page = selectedPageStack();
            AgeSymbol target = page == null ? null : PageItem.getSymbol(page);
            if (target == null || !target.takes(modifier)) return;
            CompoundTag tag = new CompoundTag();
            tag.putString("Symbol", modifier.id().toString());
            tag.putInt("Index", selectedPage);
            send(WritingDeskMenu.MSG_ATTACH_MODIFIER, tag);
        }

        @Override
        public @Nullable ItemStack selectedPage() {
            return selectedPageStack();
        }

        @Override
        public boolean canWrite() {
            return menu.canWriteSymbol();
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
            if (selectedPage == index) selectedPage = -1;
            CompoundTag tag = new CompoundTag();
            tag.putInt("Index", index);
            send(WritingDeskMenu.MSG_TAKE_FROM_SLIDER, tag);
        }

        @Override
        public boolean selectsOnClick() {
            return true;
        }

        @Override
        public void select(int index) {
            List<ItemStack> pages = menu.getBookPageList();
            if (pages == null || index < 0 || index >= pages.size() || !PageItem.isSymbolPage(pages.get(index))) return;
            selectedPage = selectedPage == index ? -1 : index;
        }

        @Override
        public void rightClick(int index) {
            // right-click removes the page (a draft is erased and refunded); shift + right-click detaches its last modifier
            if (selectedPage == index && !GuiElement.isShiftHeld()) selectedPage = -1;
            CompoundTag tag = new CompoundTag();
            tag.putInt("Index", index);
            send(GuiElement.isShiftHeld() ? WritingDeskMenu.MSG_DETACH_MODIFIER : WritingDeskMenu.MSG_REMOVE_PAGE, tag);
        }

        @Override
        public List<Component> actionHints(int index) {
            List<ItemStack> pages = menu.getBookPageList();
            if (pages == null || index < 0 || index >= pages.size() || !PageItem.isSymbolPage(pages.get(index))) return List.of();
            List<Component> out = new ArrayList<>();
            out.add(Component.translatable(selectedPage == index ? "gui.mystcraft.writing_desk.strip.deselect" : "gui.mystcraft.writing_desk.strip.select").withStyle(ChatFormatting.GRAY));
            out.add(Component.translatable(menu.isDraftPage(index) ? "gui.mystcraft.writing_desk.strip.erase" : "gui.mystcraft.writing_desk.strip.remove").withStyle(ChatFormatting.GRAY));
            if (!PageItem.getModifiers(pages.get(index)).isEmpty()) {
                out.add(Component.translatable("gui.mystcraft.writing_desk.strip.detach").withStyle(ChatFormatting.GRAY));
            }
            return out;
        }
    }
}
