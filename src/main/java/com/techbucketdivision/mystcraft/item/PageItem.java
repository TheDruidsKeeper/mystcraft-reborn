package com.techbucketdivision.mystcraft.item;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.item.component.SymbolRef;
import com.techbucketdivision.mystcraft.registry.ModDataComponents;
import com.techbucketdivision.mystcraft.registry.ModItems;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import com.techbucketdivision.mystcraft.symbol.SymbolRemapper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A page (REQUIREMENTS §2.2). Three states, encoded in data components:
 * <ul>
 * <li>blank: neither {@link ModDataComponents#SYMBOL} nor {@link ModDataComponents#LINK_PANEL}</li>
 * <li>link panel: {@link ModDataComponents#LINK_PANEL} present (set of link properties, may be empty)</li>
 * <li>symbol page: {@link ModDataComponents#SYMBOL} present</li>
 * </ul>
 */
public class PageItem extends Item implements ItemBehaviours.Writable, ItemBehaviours.PageProvider,
        ItemBehaviours.Renameable, ItemBehaviours.OnLoadable {

    public static final String KEY_BLANK = "item.mystcraft.page.blank";
    public static final String KEY_LINK_PANEL = "item.mystcraft.page.link_panel";
    public static final String KEY_SYMBOL = "item.mystcraft.page.symbol";

    public PageItem(Item.Properties properties) {
        super(properties);
    }

    // --- factories ---------------------------------------------------------------------------------------------

    public static ItemStack createBlankPage() {
        return new ItemStack(ModItems.PAGE.get());
    }

    public static ItemStack createSymbolPage(AgeSymbol symbol) {
        return createSymbolPage(symbol.id());
    }

    /** Symbol page by id (the symbol need not be registered, e.g. for remapping targets). */
    public static ItemStack createSymbolPage(Identifier symbolId) {
        ItemStack stack = createBlankPage();
        stack.set(ModDataComponents.SYMBOL.get(), new SymbolRef(symbolId));
        return stack;
    }

    public static ItemStack createLinkPanel(Set<LinkProperty> properties) {
        ItemStack stack = createBlankPage();
        stack.set(ModDataComponents.LINK_PANEL.get(), new LinkedHashSet<>(properties));
        return stack;
    }

    /** Plain link panel without properties. */
    public static ItemStack createLinkPanel() {
        return createLinkPanel(Set.of());
    }

    /** Converts one sheet of paper from {@code paper} into a blank page (shrinks the paper stack by one). */
    public static ItemStack createFromPaper(ItemStack paper) {
        if (paper.is(Items.PAPER)) {
            paper.shrink(1);
            return createBlankPage();
        }
        return ItemStack.EMPTY;
    }

    // --- queries -----------------------------------------------------------------------------------------------

    public static boolean isPage(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PageItem;
    }

    public static boolean isLinkPanel(ItemStack stack) {
        return isPage(stack) && stack.has(ModDataComponents.LINK_PANEL.get());
    }

    public static boolean isBlank(ItemStack stack) {
        return isPage(stack) && !isLinkPanel(stack) && !stack.has(ModDataComponents.SYMBOL.get());
    }

    public static boolean isSymbolPage(ItemStack stack) {
        return isPage(stack) && !isLinkPanel(stack) && stack.has(ModDataComponents.SYMBOL.get());
    }

    public static @Nullable Identifier getSymbolId(ItemStack stack) {
        if (!isPage(stack)) return null;
        SymbolRef ref = stack.get(ModDataComponents.SYMBOL.get());
        return ref == null ? null : ref.id();
    }

    public static @Nullable AgeSymbol getSymbol(ItemStack stack) {
        Identifier id = getSymbolId(stack);
        return id == null ? null : SymbolRegistry.get(id);
    }

    /** Link properties of a link panel; empty for anything else. */
    public static Set<LinkProperty> getLinkProperties(ItemStack stack) {
        if (!isPage(stack)) return Set.of();
        Set<LinkProperty> props = stack.get(ModDataComponents.LINK_PANEL.get());
        return props == null ? Set.of() : Set.copyOf(props);
    }

    public static void setSymbol(ItemStack stack, @Nullable AgeSymbol symbol) {
        if (symbol == null) stack.remove(ModDataComponents.SYMBOL.get());
        else stack.set(ModDataComponents.SYMBOL.get(), SymbolRef.of(symbol));
    }

    public static void addLinkProperty(ItemStack stack, LinkProperty property) {
        Set<LinkProperty> props = new LinkedHashSet<>(getLinkProperties(stack));
        props.add(property);
        stack.set(ModDataComponents.LINK_PANEL.get(), props);
    }

    // --- remapping (REQUIREMENTS §19.4) -------------------------------------------------------------------------

    /**
     * Applies symbol remappings to a page. Returns the list of resulting pages: the page itself (unchanged) when no
     * remapping applies, several pages when the old symbol split, or an empty list when the symbol was removed.
     */
    public static List<ItemStack> remap(ItemStack page) {
        Identifier id = getSymbolId(page);
        if (id == null || !SymbolRemapper.hasRemapping(id)) return List.of(page);
        List<Identifier> targets = SymbolRemapper.remap(id);
        List<ItemStack> out = new ArrayList<>(targets.size());
        for (Identifier target : targets) {
            ItemStack mapped = createSymbolPage(target);
            mapped.setCount(page.getCount());
            out.add(mapped);
        }
        return out;
    }

    /** Remaps a whole list of pages (order preserved, splits expanded in place). */
    public static List<ItemStack> remapAll(List<ItemStack> pages) {
        List<ItemStack> out = new ArrayList<>(pages.size());
        for (ItemStack page : pages) out.addAll(remap(page));
        return out;
    }

    // --- Item overrides ----------------------------------------------------------------------------------------

    @Override
    public Component getName(ItemStack stack) {
        if (isLinkPanel(stack)) return Component.translatable(KEY_LINK_PANEL);
        Identifier id = getSymbolId(stack);
        if (id == null) return Component.translatable(KEY_BLANK);
        AgeSymbol symbol = SymbolRegistry.get(id);
        if (symbol == null) {
            return Component.translatable(KEY_SYMBOL, Component.literal("Unknown: " + id));
        }
        return Component.translatable(KEY_SYMBOL, symbol.displayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        if (isLinkPanel(stack)) {
            for (LinkProperty property : getLinkProperties(stack)) {
                builder.accept(Component.translatable(property.descriptionId()));
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (level.isClientSide()) return;
        Identifier id = getSymbolId(stack);
        if (id == null || !SymbolRemapper.hasRemapping(id)) return;
        List<ItemStack> mapped = remap(stack);
        if (mapped.size() == 1) {
            ItemStack target = mapped.getFirst();
            stack.set(ModDataComponents.SYMBOL.get(), target.get(ModDataComponents.SYMBOL.get()));
            return;
        }
        if (mapped.isEmpty()) {
            stack.setCount(0);
            return;
        }
        if (owner instanceof Player player) {
            ItemStack folder = FolderItem.create("", mapped);
            replaceInInventory(player, stack, slot, folder);
        }
    }

    /** Replaces {@code old} in the player's inventory with {@code replacement}. */
    static void replaceInInventory(Player player, ItemStack old, @Nullable EquipmentSlot slot, ItemStack replacement) {
        if (slot != null) {
            player.setItemSlot(slot, replacement);
            return;
        }
        int index = player.getInventory().findSlotMatchingItem(old);
        if (index >= 0) {
            player.getInventory().setItem(index, replacement);
        } else {
            old.setCount(0);
            player.getInventory().placeItemBackInInventory(replacement);
        }
    }

    // --- behaviours --------------------------------------------------------------------------------------------

    @Override
    public boolean writeSymbol(Player player, ItemStack stack, AgeSymbol symbol) {
        if (!isBlank(stack)) return false;
        setSymbol(stack, symbol);
        return true;
    }

    @Override
    public List<ItemStack> getPageList(@Nullable Player player, ItemStack stack) {
        return List.of(stack.copy());
    }

    @Override
    public @Nullable String getDisplayName(ItemStack stack) {
        return getName(stack).getString();
    }

    @Override
    public void setDisplayName(Player player, ItemStack stack, String name) {
        // pages cannot be renamed
    }

    @Override
    public void onLoad(ItemStack stack) {
        List<ItemStack> mapped = remap(stack);
        if (mapped.size() == 1 && mapped.getFirst() != stack) {
            stack.set(ModDataComponents.SYMBOL.get(), mapped.getFirst().get(ModDataComponents.SYMBOL.get()));
        }
        // splits / removals are handled by the owning container or by inventoryTick
    }
}
