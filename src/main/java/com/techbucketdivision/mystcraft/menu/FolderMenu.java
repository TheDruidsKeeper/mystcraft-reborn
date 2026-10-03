package com.techbucketdivision.mystcraft.menu;

import com.techbucketdivision.mystcraft.api.item.ItemBehaviours;
import com.techbucketdivision.mystcraft.menu.slot.BannedSlot;
import com.techbucketdivision.mystcraft.registry.ModMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Collation Folder container (REQUIREMENTS §8.6) for a held folder. Slots: 0–26 inventory (y 135), 27–35 hotbar
 * (y 193); the hotbar slot of the open item is locked. Messages client→server: {@code AddToSurface(Index, Single)},
 * {@code RemoveFromOrderedCollection(Index)}.
 */
public class FolderMenu extends AbstractMystcraftMenu {
    public static final String MSG_ADD_TO_SURFACE = "AddToSurface";
    public static final String MSG_REMOVE_FROM_ORDERED_COLLECTION = "RemoveFromOrderedCollection";

    public static final int INV_START = 0;

    private final InteractionHand hand;

    public FolderMenu(int containerId, Inventory inv, InteractionHand hand) {
        super(ModMenus.FOLDER.get(), containerId, inv);
        this.hand = hand;
        int banned = hand == InteractionHand.MAIN_HAND ? inv.getSelectedSlot() : -1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 135 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            if (col == banned) addSlot(new BannedSlot(inv, col, 8 + col * 18, 135 + 58));
            else addSlot(new Slot(inv, col, 8 + col * 18, 135 + 58));
        }
    }

    public static void openForHeldItem(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isPageCollection(stack)) return;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new FolderMenu(id, inv, hand),
                Component.translatable("container.mystcraft.folder")), buf -> buf.writeEnum(hand));
    }

    public static FolderMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new FolderMenu(containerId, inv, buf.readEnum(InteractionHand.class));
    }

    public static boolean isPageCollection(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemBehaviours.OrderablePageProvider;
    }

    // --- container plumbing ---------------------------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        return isPageCollection(getInventoryItem());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return quickMove(player, index, 0, 0, INV_START, stack -> placePageOnSurface(player, stack, Integer.MAX_VALUE));
    }

    @Override
    public void processMessage(Player player, CompoundTag data) {
        switch (messageName(data)) {
            case MSG_REMOVE_FROM_ORDERED_COLLECTION -> {
                if (!cursor().isEmpty()) return;
                setCursor(player, removePageFromSurface(player, data.getIntOr("Index", 0)));
            }
            case MSG_ADD_TO_SURFACE -> {
                ItemStack held = cursor();
                if (held.isEmpty() || !data.contains("Index") || getInventoryItem().isEmpty()) return;
                int index = data.getIntOr("Index", 0);
                if (data.getBooleanOr("Single", false)) {
                    ItemStack one = held.copyWithCount(1);
                    ItemStack returned = placePageOnSurface(player, one, index);
                    if (returned.isEmpty() || held.getCount() == 1) {
                        held.shrink(1);
                        setCursor(player, held.isEmpty() ? returned : held);
                    } else {
                        placePageOnSurface(player, returned, index);
                    }
                } else {
                    setCursor(player, placePageOnSurface(player, held, index));
                }
            }
            default -> {
            }
        }
    }

    private ItemStack placePageOnSurface(Player player, ItemStack page, int index) {
        ItemStack item = getInventoryItem();
        if (item.isEmpty()) return page;
        ItemStack result = page;
        if (item.getItem() instanceof ItemBehaviours.OrderablePageProvider p) {
            if (index == Integer.MAX_VALUE) index = p.getLargestPageIndex(item) + 1;
            result = p.setPage(player, item, page, index);
        }
        player.setItemInHand(hand, item);
        return result;
    }

    private ItemStack removePageFromSurface(Player player, int index) {
        ItemStack item = getInventoryItem();
        if (item.isEmpty() || !(item.getItem() instanceof ItemBehaviours.OrderablePageProvider p)) return ItemStack.EMPTY;
        ItemStack result = p.removePage(player, item, index);
        player.setItemInHand(hand, item);
        return result;
    }

    // --- screen state ------------------------------------------------------------------------------------------------

    /** The folder being edited. */
    public ItemStack getInventoryItem() {
        return player.getItemInHand(hand);
    }

    public ItemStack getPageCollection() {
        return getInventoryItem();
    }

    public InteractionHand getHand() {
        return hand;
    }

    /** Display name of the collection (null when not renameable). */
    public @Nullable String getTabItemName() {
        ItemStack item = getInventoryItem();
        if (item.isEmpty() || !(item.getItem() instanceof ItemBehaviours.Renameable r)) return null;
        return r.getDisplayName(item);
    }
}
