package com.techbucketdivision.mystcraft.menu;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.blockentity.WritingDeskBlockEntity;
import com.techbucketdivision.mystcraft.menu.slot.WindowedItemHandler;
import com.techbucketdivision.mystcraft.registry.ModMenus;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import com.mojang.serialization.DynamicOps;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Writing Desk container (REQUIREMENTS §8.1). Slot indices: 0–3 notebook tabs (window over the 25 tab slots),
 * 4 target, 5 paper, 6 ink container in, 7 container out, 8–34 player inventory, 35–43 hotbar.
 * <p>
 * Messages client→server: {@code SetTitle(Title)}, {@code Link}, {@code RemoveFromCollection(Page)},
 * {@code RemoveFromOrderedCollection(Index)}, {@code AddToCollection(Tab, Single)},
 * {@code AddToSurface(Tab, Index, Single)}, {@code WriteSymbol(Symbol)}, {@code SetActiveNotebook(Tab)},
 * {@code SetFirstNotebook(Tab)}, {@code TakeFromSlider(Index)}, {@code InsertHeldAt(Index, Single)}.
 * Server→client: {@code SetFluid(Fluid)}, {@code LinkPermitted(Permitted, Visited)}, {@code SetTitle(Title)},
 * {@code SetCurrentPage(Index)}.
 */
public class WritingDeskMenu extends AbstractMystcraftMenu {
    public static final String MSG_SET_TITLE = "SetTitle";
    public static final String MSG_LINK = BookView.MSG_LINK;
    public static final String MSG_LINK_PERMITTED = BookView.MSG_LINK_PERMITTED;
    public static final String MSG_SET_CURRENT_PAGE = BookView.MSG_SET_CURRENT_PAGE;
    public static final String MSG_REMOVE_FROM_COLLECTION = "RemoveFromCollection";
    public static final String MSG_REMOVE_FROM_ORDERED_COLLECTION = "RemoveFromOrderedCollection";
    public static final String MSG_ADD_TO_TAB = "AddToCollection";
    public static final String MSG_ADD_TO_SURFACE = "AddToSurface";
    public static final String MSG_WRITE_SYMBOL = "WriteSymbol";
    public static final String MSG_SET_ACTIVE_NOTEBOOK = "SetActiveNotebook";
    public static final String MSG_SET_FIRST_NOTEBOOK = "SetFirstNotebook";
    public static final String MSG_SET_FLUID = "SetFluid";
    public static final String MSG_TAKE_FROM_SLIDER = "TakeFromSlider";
    public static final String MSG_INSERT_HELD_AT = "InsertHeldAt";
    public static final String MSG_UNDO_DRAFT = "UndoDraft";

    public static final int X_SHIFT = 228 + 5;
    public static final int Y_SHIFT = 20;
    public static final int TAB_SLOTS = 4;
    public static final int SLOT_TARGET = 4;
    public static final int SLOT_PAPER = 5;
    public static final int SLOT_CONTAINER_IN = 6;
    public static final int SLOT_CONTAINER_OUT = 7;
    public static final int INV_START = 8;
    public static final int MAX_TITLE = 21;

    private final WritingDeskBlockEntity desk;
    private final WindowedItemHandler tabWindow;
    private final BookView book;

    private int activeTab;
    private int firstTab;
    private FluidStack cachedFluid = FluidStack.EMPTY;
    private String cachedTitle = "";

    public WritingDeskMenu(int containerId, Inventory inv, WritingDeskBlockEntity desk) {
        super(ModMenus.WRITING_DESK.get(), containerId, inv);
        this.desk = desk;
        this.tabWindow = new WindowedItemHandler(desk.tabs, TAB_SLOTS, 1);
        this.book = new BookView(desk::getTarget, player);

        for (int i = 0; i < TAB_SLOTS; i++) {
            addSlot(new ResourceHandlerSlot(tabWindow, tabWindow::set, i, 37, 14 + i * 37 + Y_SHIFT));
        }
        addSlot(new ResourceHandlerSlot(desk.main, desk.main::set, WritingDeskBlockEntity.SLOT_TARGET, 8 + X_SHIFT, 60 + Y_SHIFT));
        addSlot(new ResourceHandlerSlot(desk.main, desk.main::set, WritingDeskBlockEntity.SLOT_PAPER, 8 + X_SHIFT, 8 + Y_SHIFT));
        addSlot(new ResourceHandlerSlot(desk.main, desk.main::set, WritingDeskBlockEntity.SLOT_CONTAINER_IN, 152 + X_SHIFT, 8 + Y_SHIFT));
        addSlot(new ResourceHandlerSlot(desk.main, desk.main::set, WritingDeskBlockEntity.SLOT_CONTAINER_OUT, 152 + X_SHIFT, 60 + Y_SHIFT));
        addStandardInventorySlots(inv, 8 + X_SHIFT, 84 + Y_SHIFT);
    }

    public static WritingDeskMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (!(inv.player.level().getBlockEntity(pos) instanceof WritingDeskBlockEntity desk)) {
            throw new IllegalStateException("No writing desk at " + pos);
        }
        return new WritingDeskMenu(containerId, inv, desk);
    }

    // --- container plumbing ----------------------------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(desk, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return quickMove(player, index, 0, INV_START, INV_START, this::receivePages);
    }

    /** Shift-clicked stack from the inventory: try to add it to the active notebook (returns the remainder). */
    private ItemStack receivePages(ItemStack stack) {
        if (getActiveNotebook().isEmpty()) return stack;
        return desk.addPageToTab(player, activeTab, stack);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!isServer()) return;
        FluidStack ink = desk.getInk();
        if (!FluidStack.isSameFluidSameComponents(ink, cachedFluid) || ink.getAmount() != cachedFluid.getAmount()) {
            cachedFluid = ink.copy();
            CompoundTag tag = new CompoundTag();
            tag.store("Fluid", FluidStack.OPTIONAL_CODEC, ops(), cachedFluid);
            sendToClient(MSG_SET_FLUID, tag);
        }
        boolean hadState = !book.needsPermissionSync();
        boolean wasPermitted = book.isLinkPermitted();
        boolean wasVisited = book.isTargetWorldVisited();
        boolean has = book.computeServerState();
        if (has && (!hadState || wasPermitted != book.isLinkPermitted() || wasVisited != book.isTargetWorldVisited())) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Permitted", book.isLinkPermitted());
            tag.putBoolean("Visited", book.isTargetWorldVisited());
            sendToClient(MSG_LINK_PERMITTED, tag);
        }
        String title = desk.getTargetString();
        if (!cachedTitle.equals(title)) {
            cachedTitle = title;
            CompoundTag tag = new CompoundTag();
            tag.putString("Title", title);
            sendToClient(MSG_SET_TITLE, tag);
        }
    }

    private DynamicOps<Tag> ops() {
        return player.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    // --- messages ----------------------------------------------------------------------------------------------------

    @Override
    public void processMessage(Player player, CompoundTag data) {
        switch (messageName(data)) {
            case MSG_LINK_PERMITTED -> book.setPermitted(data.getBooleanOr("Permitted", false), data.getBooleanOr("Visited", false));
            case MSG_SET_TITLE -> {
                cachedTitle = data.getStringOr("Title", "");
                if (cachedTitle.length() > MAX_TITLE) cachedTitle = cachedTitle.substring(0, MAX_TITLE);
                desk.setBookTitle(player, cachedTitle);
            }
            case MSG_LINK -> {
                if (isServer()) desk.link(player);
            }
            case MSG_REMOVE_FROM_COLLECTION -> {
                if (!cursor().isEmpty()) return;
                ItemStack page = data.read("Page", ItemStack.OPTIONAL_CODEC, ops()).orElse(ItemStack.EMPTY);
                if (page.isEmpty()) return;
                setCursor(player, desk.removePageFromSurface(player, activeTab, page));
            }
            case MSG_REMOVE_FROM_ORDERED_COLLECTION -> {
                if (!cursor().isEmpty()) return;
                setCursor(player, desk.removePageFromSurface(player, activeTab, data.getIntOr("Index", 0)));
            }
            case MSG_ADD_TO_TAB -> addToTab(player, data.getIntOr("Tab", activeTab), data.getBooleanOr("Single", false));
            case MSG_ADD_TO_SURFACE -> {
                if (!data.contains("Index")) return;
                addToSurface(player, data.getIntOr("Tab", activeTab), data.getIntOr("Index", 0), data.getBooleanOr("Single", false));
            }
            case MSG_WRITE_SYMBOL -> {
                Identifier id = Identifier.tryParse(data.getStringOr("Symbol", ""));
                AgeSymbol symbol = id == null ? null : SymbolRegistry.get(id);
                if (symbol != null && isServer()) desk.writeSymbol(player, symbol);
            }
            case MSG_SET_ACTIVE_NOTEBOOK -> {
                activeTab = Math.max(0, Math.min(data.getIntOr("Tab", 0), desk.getMaxSurfaceTabCount() - 1));
                book.invalidate();
            }
            case MSG_SET_FIRST_NOTEBOOK -> {
                int first = data.getIntOr("Tab", 0);
                if (first < 0 || first >= desk.getMaxSurfaceTabCount()) first = 0;
                firstTab = first;
                tabWindow.setOffset(first);
            }
            case MSG_SET_FLUID -> {
                if (isClient()) {
                    cachedFluid = data.read("Fluid", FluidStack.OPTIONAL_CODEC, ops()).orElse(FluidStack.EMPTY);
                    desk.setInk(cachedFluid);
                }
            }
            case MSG_SET_CURRENT_PAGE -> {
                book.setCurrentPageIndex(data.getIntOr("Index", 0));
                book.invalidate();
            }
            case MSG_UNDO_DRAFT -> {
                if (isServer()) desk.undoLastDraft(player);
            }
            case MSG_TAKE_FROM_SLIDER -> {
                if (!cursor().isEmpty()) return;
                desk.commitDrafts(); // pages leaving the folder by hand are permanent (and indices shift)
                ItemStack target = desk.getTarget();
                if (target.isEmpty() || !(target.getItem() instanceof ItemBehaviours.OrderablePageProvider p)) return;
                ItemStack removed = p.removePage(player, target, data.getIntOr("Index", 0));
                desk.main.setStack(WritingDeskBlockEntity.SLOT_TARGET, target);
                setCursor(player, removed);
            }
            case MSG_INSERT_HELD_AT -> {
                desk.commitDrafts();
                ItemStack held = cursor();
                ItemStack target = desk.getTarget();
                if (held.isEmpty() || target.isEmpty() || !(target.getItem() instanceof ItemBehaviours.OrderablePageProvider p)) return;
                int index = data.getIntOr("Index", 0);
                if (data.getBooleanOr("Single", false) && held.getCount() > 1) {
                    ItemStack one = held.copyWithCount(1);
                    ItemStack prev = p.setPage(player, target, one, index);
                    if (prev.getCount() != one.getCount() || !ItemStack.isSameItemSameComponents(prev, one)) {
                        held.shrink(1);
                        desk.main.setStack(WritingDeskBlockEntity.SLOT_TARGET, target);
                        if (!prev.isEmpty()) {
                            if (!player.getInventory().add(prev)) player.drop(prev, false);
                        }
                        setCursor(player, held);
                    }
                } else {
                    ItemStack prev = p.setPage(player, target, held, index);
                    desk.main.setStack(WritingDeskBlockEntity.SLOT_TARGET, target);
                    setCursor(player, prev);
                }
            }
            default -> {
            }
        }
    }

    private void addToTab(Player player, int tab, boolean single) {
        ItemStack held = cursor();
        if (held.isEmpty() || desk.getTabItem(tab).isEmpty()) return;
        if (single) {
            ItemStack one = held.copyWithCount(1);
            ItemStack ret = desk.addPageToTab(player, tab, one.copy());
            if (ItemStack.isSameItemSameComponents(ret, one) && ret.getCount() == one.getCount()) return; // rejected
            held.shrink(1);
            if (held.isEmpty()) {
                setCursor(player, ret);
                return;
            }
            if (!ret.isEmpty()) {
                if (ItemStack.isSameItemSameComponents(ret, held) && held.getCount() + ret.getCount() <= held.getMaxStackSize()) {
                    held.grow(ret.getCount());
                } else if (!player.getInventory().add(ret)) {
                    player.drop(ret, false);
                }
            }
            setCursor(player, held);
        } else {
            setCursor(player, desk.addPageToTab(player, tab, held));
        }
    }

    private void addToSurface(Player player, int tab, int index, boolean single) {
        ItemStack held = cursor();
        if (held.isEmpty() || desk.getTabItem(tab).isEmpty()) return;
        if (single) {
            ItemStack one = held.copyWithCount(1);
            ItemStack returned = desk.placePageOnSurface(player, tab, one, index);
            if (returned.isEmpty() || held.getCount() == 1) {
                held.shrink(1);
                setCursor(player, held.isEmpty() ? returned : held);
            } else {
                desk.placePageOnSurface(player, tab, returned, index); // put the displaced page back
            }
        } else {
            setCursor(player, desk.placePageOnSurface(player, tab, held, index));
        }
    }

    // --- screen state -----------------------------------------------------------------------------------------------

    public WritingDeskBlockEntity getDesk() {
        return desk;
    }

    public BookView getBookView() {
        return book;
    }

    /** Notebook in the given tab (0–24), or empty. */
    public ItemStack getTabSlot(int tab) {
        return desk.getTabItem(tab);
    }

    public ItemStack getActiveNotebook() {
        return desk.getTabItem(activeTab);
    }

    public int getActiveTabSlot() {
        return activeTab;
    }

    public int getFirstTabSlot() {
        return firstTab;
    }

    public int getMaxTabCount() {
        return desk.getMaxSurfaceTabCount();
    }

    /** The item in the target slot (book, folder, page, ...), or empty. */
    public ItemStack getTarget() {
        return desk.getTarget();
    }

    /** The target if it is a linking item, else empty. */
    public ItemStack getBook() {
        return book.getBook();
    }

    public @Nullable LinkInfo getLinkInfo() {
        return book.getLinkInfo();
    }

    public boolean isLinkPermitted() {
        return book.isLinkPermitted();
    }

    public boolean isTargetWorldVisited() {
        return book.isTargetWorldVisited();
    }

    public String getBookTitle() {
        return book.getBookTitle();
    }

    public List<String> getBookAuthors() {
        return book.getBookAuthors();
    }

    /** Name shown in the "ItemName" field (synced from the server). */
    public String getTargetName() {
        return cachedTitle;
    }

    /** Pages of the target when it is a page provider (folder / book), else null. */
    public @Nullable List<ItemStack> getBookPageList() {
        return desk.getBookPageList(player);
    }

    public FluidStack getInk() {
        return isClient() ? cachedFluid : desk.getInk();
    }

    public int getInkCapacity() {
        return WritingDeskBlockEntity.TANK_CAPACITY;
    }

    /**
     * Whether a click on a surface symbol can currently write a copy (mirrors {@code WritingDeskBlockEntity.writeSymbol}):
     * enough ink, and either a writable target, a page-accepting target with paper, or paper alone (makes a new page).
     * Used by the screen to decide between "write" and "take the page" on a plain click.
     */
    public boolean canWriteSymbol() {
        if (getInk().getAmount() < WritingDeskBlockEntity.INK_COST) return false;
        ItemStack target = getTarget();
        boolean paper = !getSlot(SLOT_PAPER).getItem().isEmpty();
        if (target.isEmpty()) return paper;
        if (target.getItem() instanceof ItemBehaviours.Writable) return true;
        return paper && target.getItem() instanceof ItemBehaviours.PageAcceptor;
    }

    public boolean hasBookSlot() {
        return false;
    }

    /** Draft pages of the target (written here, not yet permanent), synced through the block entity. */
    public List<WritingDeskBlockEntity.Draft> getDrafts() {
        return desk.getDrafts();
    }

    public boolean isDraftPage(int index) {
        return desk.isDraft(index);
    }
}
