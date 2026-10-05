package com.tbd.mystcraft.menu;

import com.mojang.serialization.DynamicOps;
import com.tbd.mystcraft.api.item.ItemBehaviours;
import com.tbd.mystcraft.api.symbol.AgeSymbol;
import com.tbd.mystcraft.blockentity.WritingDeskBlockEntity;
import com.tbd.mystcraft.registry.ModMenus;
import com.tbd.mystcraft.symbol.SymbolRegistry;
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
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Writing Desk container (original spec §8.1, Reborn rework: world-building plan §4). Slot indices: 0 target (folder),
 * 1 paper, 2 ink container in, 3 container out, 4–30 player inventory, 31–39 hotbar. The symbol surface is not an
 * inventory: it lists the player's known symbols (or all of them at a Scholar's desk) from the synced knowledge.
 * <p>
 * Messages client→server: {@code SetTitle(Title)}, {@code WriteSymbol(Symbol)}, {@code AttachModifier(Symbol, Index)},
 * {@code DetachModifier(Index)}, {@code TakeFromSlider(Index)}, {@code InsertHeldAt(Index, Single)},
 * {@code RemovePage(Index)} (right-click: a draft is erased with its paper and ink refunded, other pages go to the cursor).
 * Server→client: {@code SetFluid(Fluid)}, {@code SetTitle(Title)}.
 */
public class WritingDeskMenu extends AbstractMystcraftMenu {
    public static final String MSG_SET_TITLE = "SetTitle";
    public static final String MSG_WRITE_SYMBOL = "WriteSymbol";
    public static final String MSG_ATTACH_MODIFIER = "AttachModifier";
    public static final String MSG_DETACH_MODIFIER = "DetachModifier";
    public static final String MSG_SET_FLUID = "SetFluid";
    public static final String MSG_TAKE_FROM_SLIDER = "TakeFromSlider";
    public static final String MSG_INSERT_HELD_AT = "InsertHeldAt";
    public static final String MSG_REMOVE_PAGE = "RemovePage";

    public static final int X_SHIFT = 228 + 5;
    public static final int Y_SHIFT = 20;
    public static final int SLOT_TARGET = 0;
    public static final int SLOT_PAPER = 1;
    public static final int SLOT_CONTAINER_IN = 2;
    public static final int SLOT_CONTAINER_OUT = 3;
    public static final int INV_START = 4;
    public static final int MAX_TITLE = 21;

    private final WritingDeskBlockEntity desk;

    private FluidStack cachedFluid = FluidStack.EMPTY;
    private String cachedTitle = "";

    public WritingDeskMenu(int containerId, Inventory inv, WritingDeskBlockEntity desk) {
        super(ModMenus.WRITING_DESK.get(), containerId, inv);
        this.desk = desk;
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
        return quickMove(player, index, 0, INV_START, INV_START, null);
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
            case MSG_SET_TITLE -> {
                cachedTitle = data.getStringOr("Title", "");
                if (cachedTitle.length() > MAX_TITLE) cachedTitle = cachedTitle.substring(0, MAX_TITLE);
                desk.setBookTitle(player, cachedTitle);
            }
            case MSG_WRITE_SYMBOL -> {
                AgeSymbol symbol = symbol(data);
                if (symbol != null && isServer()) desk.writeSymbol(player, symbol);
            }
            case MSG_ATTACH_MODIFIER -> {
                AgeSymbol modifier = symbol(data);
                if (modifier != null && isServer()) desk.attachModifier(player, data.getIntOr("Index", -1), modifier);
            }
            case MSG_DETACH_MODIFIER -> {
                if (isServer()) desk.detachLastModifier(player, data.getIntOr("Index", -1));
            }
            case MSG_SET_FLUID -> {
                if (isClient()) {
                    cachedFluid = data.read("Fluid", FluidStack.OPTIONAL_CODEC, ops()).orElse(FluidStack.EMPTY);
                    desk.setInk(cachedFluid);
                }
            }
            case MSG_REMOVE_PAGE -> {
                if (!isServer() || !cursor().isEmpty()) return;
                setCursor(player, desk.removePage(player, data.getIntOr("Index", -1)));
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

    private static @Nullable AgeSymbol symbol(CompoundTag data) {
        Identifier id = Identifier.tryParse(data.getStringOr("Symbol", ""));
        return id == null ? null : SymbolRegistry.get(id);
    }

    // --- screen state -----------------------------------------------------------------------------------------------

    public WritingDeskBlockEntity getDesk() {
        return desk;
    }

    /** The folder in the target slot, or empty. */
    public ItemStack getTarget() {
        return desk.getTarget();
    }

    /** Name shown in the "ItemName" field (synced from the server). */
    public String getTargetName() {
        return cachedTitle;
    }

    /** Pages of the target folder, else null. */
    public @Nullable List<ItemStack> getBookPageList() {
        return desk.getBookPageList(player);
    }

    public FluidStack getInk() {
        return isClient() ? cachedFluid : desk.getInk();
    }

    public int getInkCapacity() {
        return WritingDeskBlockEntity.TANK_CAPACITY;
    }

    public boolean hasInk() {
        return getInk().getAmount() >= WritingDeskBlockEntity.INK_COST;
    }

    public boolean hasPaper() {
        return !getSlot(SLOT_PAPER).getItem().isEmpty();
    }

    /** Whether a click on a primary symbol can write a page right now (folder, paper and ink present). */
    public boolean canWriteSymbol() {
        return hasInk() && hasPaper() && !getTarget().isEmpty();
    }

    /** Whether the desk offers every symbol (Scholar's desk) instead of the player's knowledge. */
    public boolean isScholar() {
        return desk.isScholar();
    }

    /** Draft pages of the target (written here, not yet permanent), synced through the block entity. */
    public List<WritingDeskBlockEntity.Draft> getDrafts() {
        return desk.getDrafts();
    }

    public boolean isDraftPage(int index) {
        return desk.isDraft(index);
    }
}
