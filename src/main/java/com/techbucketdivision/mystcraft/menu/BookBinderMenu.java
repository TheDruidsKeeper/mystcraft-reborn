package com.techbucketdivision.mystcraft.menu;

import com.techbucketdivision.mystcraft.blockentity.BookBinderBlockEntity;
import com.techbucketdivision.mystcraft.item.FolderItem;
import com.techbucketdivision.mystcraft.menu.slot.CraftOutputSlot;
import com.techbucketdivision.mystcraft.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

import java.util.List;

/**
 * Book Binder container (original spec §8.2). Slots: 0 cover, 1–27 inventory, 28–36 hotbar, 37 craft output.
 * Messages client→server: {@code SetTitle(Title)}, {@code TakeFromSlider(Index)}, {@code InsertHeldAt(Index, Single)}.
 * The page list and title are synced through the block entity update tag.
 */
public class BookBinderMenu extends AbstractMystcraftMenu {
    public static final String MSG_SET_TITLE = "SetTitle";
    public static final String MSG_TAKE_FROM_SLIDER = "TakeFromSlider";
    public static final String MSG_INSERT_HELD_AT = "InsertHeldAt";

    public static final int SLOT_COVER = 0;
    public static final int INV_START = 1;
    public static final int SLOT_OUTPUT = 37;
    public static final int MAX_TITLE = BookBinderBlockEntity.MAX_TITLE;

    private final BookBinderBlockEntity binder;
    private final SimpleContainer result = new SimpleContainer(1);
    private final CraftOutputSlot outputSlot;

    public BookBinderMenu(int containerId, Inventory inv, BookBinderBlockEntity binder) {
        super(ModMenus.BOOK_BINDER.get(), containerId, inv);
        this.binder = binder;
        addSlot(new ResourceHandlerSlot(binder.inventory, binder.inventory::set, BookBinderBlockEntity.SLOT_COVER, 8, 27));
        addStandardInventorySlots(inv, 8, 99);
        this.outputSlot = new CraftOutputSlot(result, 0, 152, 27, binder::buildItem);
        addSlot(outputSlot);
        updateCraftResult();
    }

    public static BookBinderMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (!(inv.player.level().getBlockEntity(pos) instanceof BookBinderBlockEntity be)) {
            throw new IllegalStateException("No book binder at " + pos);
        }
        return new BookBinderMenu(containerId, inv, be);
    }

    public void updateCraftResult() {
        result.setItem(0, binder.getCraftedItem());
    }

    @Override
    public void broadcastChanges() {
        updateCraftResult();
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(binder, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == SLOT_OUTPUT) {
            ItemStack built = outputSlot.build(player);
            if (built.isEmpty()) return ItemStack.EMPTY;
            ItemStack copy = built.copy();
            if (!moveItemStackTo(built, INV_START, INV_START + 36, true) && !built.isEmpty()) {
                player.drop(built, false);
            }
            updateCraftResult();
            return copy;
        }
        return quickMove(player, index, 0, INV_START, INV_START, stack -> binder.insertPage(stack, binder.getPageList().size()));
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != result && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public void processMessage(Player player, CompoundTag data) {
        switch (messageName(data)) {
            case MSG_SET_TITLE -> {
                String title = data.getStringOr("Title", "");
                if (title.length() > MAX_TITLE) title = title.substring(0, MAX_TITLE);
                binder.setPendingTitle(title);
            }
            case MSG_TAKE_FROM_SLIDER -> {
                if (!cursor().isEmpty()) return;
                setCursor(player, binder.removePage(data.getIntOr("Index", 0)));
            }
            case MSG_INSERT_HELD_AT -> {
                ItemStack held = cursor();
                if (held.isEmpty()) return;
                int index = data.getIntOr("Index", 0);
                if (held.getItem() instanceof FolderItem) {
                    setCursor(player, binder.insertFromFolder(held, index));
                } else if (data.getBooleanOr("Single", false)) {
                    ItemStack one = held.copyWithCount(1);
                    if (binder.insertPage(one, index).isEmpty()) {
                        held.shrink(1);
                        setCursor(player, held);
                    }
                } else {
                    setCursor(player, binder.insertPage(held, index));
                }
            }
            default -> {
            }
        }
        updateCraftResult();
    }

    // --- screen state ------------------------------------------------------------------------------------------------

    public BookBinderBlockEntity getBinder() {
        return binder;
    }

    public String getPendingTitle() {
        return binder.getPendingTitle();
    }

    public List<ItemStack> getPageList() {
        return binder.getPageList();
    }

    /** True while page 0 is not a link panel (the pulsing warning icon). */
    public boolean isMissingLinkPanel() {
        return binder.isMissingLinkPanel();
    }
}
