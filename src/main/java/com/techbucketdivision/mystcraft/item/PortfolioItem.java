package com.techbucketdivision.mystcraft.item;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.item.component.PageList;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Symbol Portfolio (REQUIREMENTS §2.6): unordered page collection in {@link ModDataComponents#PAGES} (one entry per
 * page, duplicates allowed), optional title in {@link ModDataComponents#ITEM_TITLE}.
 */
public class PortfolioItem extends Item implements ItemBehaviours.PageCollection, ItemBehaviours.Renameable,
        ItemBehaviours.OnLoadable {

    public PortfolioItem(Item.Properties properties) {
        super(properties);
    }

    /** New portfolio; stacks of N pages are stored as N entries. */
    public static ItemStack create(String title, List<ItemStack> pages) {
        ItemStack portfolio = new ItemStack(ModItems.SYMBOL_PORTFOLIO.get());
        setTitle(portfolio, title);
        List<ItemStack> entries = new ArrayList<>();
        for (ItemStack page : pages) {
            if (!isItemValid(page)) continue;
            for (int i = 0; i < page.getCount(); i++) entries.add(page.copyWithCount(1));
        }
        portfolio.set(ModDataComponents.PAGES.get(), new PageList(entries));
        return portfolio;
    }

    public static boolean isPortfolio(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PortfolioItem;
    }

    public static boolean isItemValid(ItemStack stack) {
        return PageItem.isPage(stack);
    }

    public static @Nullable String getTitle(ItemStack stack) {
        return stack.get(ModDataComponents.ITEM_TITLE.get());
    }

    public static void setTitle(ItemStack stack, @Nullable String title) {
        if (title == null || title.isEmpty()) stack.remove(ModDataComponents.ITEM_TITLE.get());
        else stack.set(ModDataComponents.ITEM_TITLE.get(), title);
    }

    private static PageList getCollection(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.PAGES.get(), PageList.EMPTY);
    }

    private static void setCollection(ItemStack stack, List<ItemStack> entries) {
        stack.set(ModDataComponents.PAGES.get(), new PageList(entries));
    }

    // --- Item overrides ----------------------------------------------------------------------------------------

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
    public List<ItemStack> getItems(@Nullable Player player, ItemStack stack) {
        return getCollection(stack).copies();
    }

    /** Removes up to {@code page.getCount()} entries equal to {@code page}; returns them as one stack. */
    @Override
    public ItemStack remove(@Nullable Player player, ItemStack portfolio, ItemStack page) {
        if (portfolio.isEmpty() || page.isEmpty()) return ItemStack.EMPTY;
        List<ItemStack> entries = getCollection(portfolio).copies();
        int wanted = page.getCount();
        int removed = 0;
        for (int i = 0; i < entries.size() && removed < wanted; ) {
            if (ItemStack.isSameItemSameComponents(entries.get(i), page)) {
                entries.remove(i);
                removed++;
            } else {
                i++;
            }
        }
        if (removed == 0) return ItemStack.EMPTY;
        setCollection(portfolio, entries);
        return page.copyWithCount(removed);
    }

    /**
     * Adds a page stack (all entries), or moves every page out of another folder/portfolio (count 1). Returns the
     * remainder (the untouched stack when rejected, empty when fully accepted).
     */
    @Override
    public ItemStack addPage(@Nullable Player player, ItemStack portfolio, ItemStack page) {
        if (portfolio.isEmpty() || page.isEmpty()) return page;
        if (page.getItem() instanceof ItemBehaviours.PageCollection other) {
            if (page.getCount() != 1) return page;
            for (ItemStack p : other.getItems(player, page)) {
                ItemStack taken = other.remove(player, page, p);
                if (taken.isEmpty()) continue;
                ItemStack out = addPage(player, portfolio, taken);
                if (!out.isEmpty()) other.addPage(player, page, out);
            }
            return page;
        }
        if (page.getItem() instanceof ItemBehaviours.OrderablePageProvider other) {
            if (page.getCount() != 1) return page;
            int largest = other.getLargestPageIndex(page);
            for (int i = 0; i <= largest; i++) {
                ItemStack taken = other.removePage(player, page, i);
                if (taken.isEmpty()) continue;
                ItemStack out = addPage(player, portfolio, taken);
                if (!out.isEmpty() && page.getItem() instanceof ItemBehaviours.PageAcceptor acceptor) {
                    acceptor.addPage(player, page, out);
                }
            }
            return page;
        }
        if (!isItemValid(page)) return page;
        List<ItemStack> entries = getCollection(portfolio).copies();
        for (int i = 0; i < page.getCount(); i++) entries.add(page.copyWithCount(1));
        setCollection(portfolio, entries);
        return ItemStack.EMPTY;
    }

    @Override
    public void onLoad(ItemStack stack) {
        if (!stack.has(ModDataComponents.PAGES.get())) return;
        List<ItemStack> entries = getCollection(stack).copies();
        List<ItemStack> mapped = new ArrayList<>(entries.size());
        for (ItemStack page : entries) {
            for (ItemStack result : PageItem.remap(page)) mapped.add(result.copyWithCount(1));
        }
        setCollection(stack, mapped);
    }
}
