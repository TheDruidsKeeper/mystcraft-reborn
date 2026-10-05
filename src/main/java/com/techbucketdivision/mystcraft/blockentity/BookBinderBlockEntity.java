package com.techbucketdivision.mystcraft.blockentity;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.item.DescriptiveBookItem;
import com.techbucketdivision.mystcraft.item.FolderItem;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.menu.BookBinderMenu;
import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import com.techbucketdivision.mystcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Book Binder (original spec §3.3): slot 0 cover (leather or an empty Collation Folder), an ordered pending page list
 * and a pending title. Crafts a Descriptive Book.
 */
public class BookBinderBlockEntity extends MystBlockEntity implements MenuProvider {
    public static final int SLOT_COVER = 0;
    public static final int MAX_TITLE = 21;

    public final FilteredItemHandler inventory = new FilteredItemHandler(1, (slot, res) -> isValidCover(res.toStack()), this::markForUpdate);

    private List<ItemStack> pages = new ArrayList<>();
    private String pendingTitle = "";

    public BookBinderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOOK_BINDER.get(), pos, state);
    }

    public static boolean isValidCover(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.LEATHER)) return true;
        if (stack.getItem() instanceof FolderItem && stack.getItem() instanceof ItemBehaviours.OrderablePageProvider p) {
            return p.getLargestPageIndex(stack) < 0;
        }
        return false;
    }

    // --- persistence ------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putChild("items", inventory);
        output.store("pages", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(pages));
        output.putString("title", pendingTitle);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.readChild("items", inventory);
        pages = new ArrayList<>(input.read("pages", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        pages.removeIf(ItemStack::isEmpty);
        pendingTitle = input.getStringOr("title", "");
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        dropContents(inventory);
        if (isServer()) {
            for (ItemStack page : pages) BookUtil.drop(getLevel(), pos, page);
            pages.clear();
        }
        super.preRemoveSideEffects(pos, state);
    }

    // --- title / pages ------------------------------------------------------------------------------------------------

    public String getPendingTitle() {
        return pendingTitle;
    }

    public void setPendingTitle(@Nullable String title) {
        this.pendingTitle = title == null ? "" : title;
        markForUpdate();
    }

    /** Read-only view of the pending page list. */
    public List<ItemStack> getPageList() {
        return Collections.unmodifiableList(pages);
    }

    /** Client-side replacement of the page list (sync). */
    public void setPages(List<ItemStack> list) {
        pages = new ArrayList<>(list);
    }

    /**
     * Inserts pages at {@code index}: paper is converted page by page into blank pages, page items are inserted
     * one-by-one, anything else is rejected.
     *
     * @return the remainder (empty when everything was inserted)
     */
    public ItemStack insertPage(ItemStack stack, int index) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        index = Math.max(0, Math.min(index, pages.size()));
        if (stack.is(Items.PAPER)) {
            while (stack.getCount() > 0) {
                ItemStack blank = PageItem.createBlankPage();
                if (blank.isEmpty() || !insertPage(blank, index).isEmpty()) return stack;
                index++;
                stack.shrink(1);
            }
            return ItemStack.EMPTY;
        }
        if (!stack.is(ModItems.PAGE.get())) return stack;
        while (stack.getCount() > 0) {
            pages.add(index, stack.copyWithCount(1));
            stack.shrink(1);
        }
        markForUpdate();
        return ItemStack.EMPTY;
    }

    /**
     * Inserts the pages of a folder at {@code index}. An empty folder instead receives the binder's pages.
     *
     * @return the (possibly modified) folder stack
     */
    public ItemStack insertFromFolder(ItemStack folder, int index) {
        if (!(folder.getItem() instanceof FolderItem) || !(folder.getItem() instanceof ItemBehaviours.OrderablePageProvider provider)) return folder;
        int largest = provider.getLargestPageIndex(folder);
        if (largest < 0) {
            if (folder.getItem() instanceof ItemBehaviours.PageAcceptor acceptor) {
                for (ItemStack page : pages) acceptor.addPage(null, folder, page);
                pages.clear();
            }
        } else {
            for (int slot = 0; slot <= largest; slot++) {
                ItemStack page = provider.removePage(null, folder, slot);
                if (page.isEmpty()) continue;
                ItemStack rest = insertPage(page, index);
                if (rest.isEmpty()) {
                    index++;
                } else {
                    provider.setPage(null, folder, rest, slot);
                }
            }
        }
        markForUpdate();
        return folder;
    }

    public ItemStack removePage(int index) {
        if (index < 0 || index >= pages.size()) return ItemStack.EMPTY;
        ItemStack page = pages.remove(index);
        markForUpdate();
        return page;
    }

    // --- crafting ------------------------------------------------------------------------------------------------------

    public boolean canBuildItem() {
        ItemStack cover = inventory.getStack(SLOT_COVER);
        if (cover.isEmpty() || !isValidCover(cover)) return false;
        if (pages.isEmpty()) return false;
        if (!PageItem.isLinkPanel(pages.get(0))) return false;
        if (pendingTitle.isEmpty()) return false;
        for (int i = 1; i < pages.size(); i++) {
            if (PageItem.isLinkPanel(pages.get(i))) return false;
        }
        return true;
    }

    /** Whether the first page is missing / not a link panel (for the GUI warning icon). */
    public boolean isMissingLinkPanel() {
        return pages.isEmpty() || !PageItem.isLinkPanel(pages.get(0));
    }

    public ItemStack getCraftedItem() {
        return canBuildItem() ? new ItemStack(ModItems.DESCRIPTIVE_BOOK.get()) : ItemStack.EMPTY;
    }

    /** Called when the player takes the crafted book. */
    public void buildItem(ItemStack result, Player player) {
        if (!canBuildItem()) return;
        if (!isServer()) return; // the server resyncs the carried stack and the binder state
        if (!(result.getItem() instanceof DescriptiveBookItem)) {
            result.setCount(0);
            return;
        }
        List<ItemStack> copies = new ArrayList<>(pages.size());
        for (ItemStack p : pages) copies.add(p.copy());
        ItemStack book = DescriptiveBookItem.create(player, copies, pendingTitle);
        result.applyComponents(book.getComponentsPatch());
        pages.clear();
        pendingTitle = "";
        ItemStack cover = inventory.getStack(SLOT_COVER);
        cover.shrink(1);
        inventory.set(SLOT_COVER, ItemResource.of(cover), cover.getCount());
        markForUpdate();
    }

    // --- menu -------------------------------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mystcraft.book_binder");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BookBinderMenu(containerId, inventory, this);
    }
}
