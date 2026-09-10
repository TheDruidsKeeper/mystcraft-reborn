package com.techbucketdivision.mystcraft.item;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.item.component.SlotPages;
import com.techbucketdivision.mystcraft.menu.FolderMenu;
import com.techbucketdivision.mystcraft.registry.ModDataComponents;
import com.techbucketdivision.mystcraft.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Collation Folder (REQUIREMENTS §2.5): sparse ordered page slots in {@link ModDataComponents#SLOT_PAGES}, optional
 * title in {@link ModDataComponents#ITEM_TITLE}. Stacks to 32 while empty and unnamed, else 1.
 */
public class FolderItem extends Item implements ItemBehaviours.Renameable, ItemBehaviours.OrderablePageProvider,
        ItemBehaviours.PageAcceptor, ItemBehaviours.Writable, ItemBehaviours.OnLoadable {

    public static final int EMPTY_STACK_SIZE = 32;

    public FolderItem(Item.Properties properties) {
        super(properties);
    }

    /** New folder with the given title (empty = none) and pages filling slots 0..n-1. */
    public static ItemStack create(String title, List<ItemStack> pages) {
        ItemStack folder = new ItemStack(ModItems.COLLATION_FOLDER.get());
        setTitle(folder, title);
        Map<Integer, ItemStack> slots = new TreeMap<>();
        int slot = 0;
        for (ItemStack page : pages) {
            if (page.isEmpty()) continue;
            for (int i = 0; i < page.getCount(); i++) slots.put(slot++, page.copyWithCount(1));
        }
        folder.set(ModDataComponents.SLOT_PAGES.get(), new SlotPages(slots));
        return folder;
    }

    public static boolean isFolder(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof FolderItem;
    }

    // --- title -------------------------------------------------------------------------------------------------

    public static @Nullable String getTitle(ItemStack folder) {
        return folder.get(ModDataComponents.ITEM_TITLE.get());
    }

    public static void setTitle(ItemStack folder, @Nullable String title) {
        if (title == null || title.isEmpty()) folder.remove(ModDataComponents.ITEM_TITLE.get());
        else folder.set(ModDataComponents.ITEM_TITLE.get(), title);
    }

    // --- slots -------------------------------------------------------------------------------------------------

    public static SlotPages getSlots(ItemStack folder) {
        return folder.getOrDefault(ModDataComponents.SLOT_PAGES.get(), SlotPages.EMPTY);
    }

    private static void setSlots(ItemStack folder, SlotPages slots) {
        if (slots.isEmpty()) folder.remove(ModDataComponents.SLOT_PAGES.get());
        else folder.set(ModDataComponents.SLOT_PAGES.get(), slots);
    }

    /** No title and no pages. */
    public static boolean isEmpty(ItemStack folder) {
        return getTitle(folder) == null && getSlots(folder).isEmpty();
    }

    /** Pages or paper, single items only. */
    public static boolean isItemValid(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.getCount() != 1) return false;
        return PageItem.isPage(stack) || stack.is(Items.PAPER);
    }

    public static ItemStack getItem(ItemStack folder, int slot) {
        return getSlots(folder).get(slot);
    }

    public static int getItemCount(ItemStack folder) {
        return getSlots(folder).slots().size();
    }

    // --- Item overrides ----------------------------------------------------------------------------------------

    /** NeoForge {@code IItemExtension#getMaxStackSize(ItemStack)}: 32 while empty, else 1. */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        return isEmpty(stack) ? EMPTY_STACK_SIZE : 1;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        String title = getTitle(stack);
        if (title != null) builder.accept(Component.literal(title));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.getCount() != 1 || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        FolderMenu.openForHeldItem(serverPlayer, hand);
        return InteractionResult.SUCCESS_SERVER;
    }

    // --- behaviours --------------------------------------------------------------------------------------------

    @Override
    public @Nullable String getDisplayName(ItemStack stack) {
        return getTitle(stack);
    }

    @Override
    public void setDisplayName(Player player, ItemStack stack, String name) {
        setTitle(stack, name);
    }

    @Override
    public boolean writeSymbol(Player player, ItemStack folder, AgeSymbol symbol) {
        SlotPages slots = getSlots(folder);
        for (Map.Entry<Integer, ItemStack> e : new TreeMap<>(slots.slots()).entrySet()) {
            ItemStack page = e.getValue();
            if (PageItem.isBlank(page)) {
                ItemStack written = page.copy();
                PageItem.setSymbol(written, symbol);
                setSlots(folder, slots.with(e.getKey(), written));
                return true;
            }
        }
        return false;
    }

    /** Pages in slot order with {@link ItemStack#EMPTY} for gaps. */
    @Override
    public List<ItemStack> getPageList(@Nullable Player player, ItemStack folder) {
        SlotPages slots = getSlots(folder);
        int largest = slots.largestSlot();
        List<ItemStack> out = new ArrayList<>(Math.max(0, largest + 1));
        for (int i = 0; i <= largest; i++) out.add(slots.get(i));
        return out;
    }

    /** Swaps the page at {@code index} for {@code page} (single item); returns the previous page or the rejected stack. */
    @Override
    public ItemStack setPage(@Nullable Player player, ItemStack folder, ItemStack page, int index) {
        if (index < 0) return page;
        ItemStack single = page.isEmpty() ? ItemStack.EMPTY : page.copyWithCount(1);
        if (!isItemValid(single)) return page;
        SlotPages slots = getSlots(folder);
        ItemStack previous = slots.get(index);
        if (page.getCount() > 1 && !previous.isEmpty()) return page; // cannot swap a stack of several
        setSlots(folder, slots.with(index, single));
        ItemStack remainder = page.copy();
        remainder.shrink(1);
        if (!previous.isEmpty()) return previous;
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    @Override
    public ItemStack removePage(@Nullable Player player, ItemStack folder, int index) {
        SlotPages slots = getSlots(folder);
        ItemStack page = slots.get(index);
        setSlots(folder, slots.without(index));
        return page;
    }

    @Override
    public int getLargestPageIndex(ItemStack folder) {
        return getSlots(folder).largestSlot();
    }

    /** Fills the lowest free slots one page at a time; returns what could not be added (always empty for valid items). */
    @Override
    public ItemStack addPage(@Nullable Player player, ItemStack folder, ItemStack page) {
        if (page.isEmpty()) return page;
        if (!PageItem.isPage(page) && !page.is(Items.PAPER)) return page;
        SlotPages slots = getSlots(folder);
        ItemStack remaining = page.copy();
        while (!remaining.isEmpty()) {
            int slot = slots.firstFreeSlot();
            slots = slots.with(slot, remaining.copyWithCount(1));
            remaining.shrink(1);
        }
        setSlots(folder, slots);
        return ItemStack.EMPTY;
    }

    /** Symbol remapping: single results replace in place, splits are removed and appended. */
    @Override
    public void onLoad(ItemStack folder) {
        SlotPages slots = getSlots(folder);
        if (slots.isEmpty()) return;
        List<ItemStack> appended = new ArrayList<>();
        SlotPages updated = slots;
        for (Map.Entry<Integer, ItemStack> e : new TreeMap<>(slots.slots()).entrySet()) {
            List<ItemStack> results = PageItem.remap(e.getValue());
            if (results.size() == 1 && results.getFirst() == e.getValue()) continue;
            if (results.size() == 1) {
                updated = updated.with(e.getKey(), results.getFirst());
            } else {
                updated = updated.without(e.getKey());
                appended.addAll(results);
            }
        }
        for (ItemStack extra : appended) {
            updated = updated.with(updated.firstFreeSlot(), extra.copyWithCount(1));
        }
        setSlots(folder, updated);
    }
}
