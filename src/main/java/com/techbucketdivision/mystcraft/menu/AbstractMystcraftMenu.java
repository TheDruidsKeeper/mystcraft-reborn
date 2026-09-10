package com.techbucketdivision.mystcraft.menu;

import com.techbucketdivision.mystcraft.menu.slot.ToggleSlot;
import com.techbucketdivision.mystcraft.network.MenuMessagePayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

/**
 * Base menu: routes {@link MenuMessagePayload}s (both directions) and provides the shift-click routing used by every
 * Mystcraft container (internal slots → player inventory; player inventory → page receiver → internal slots → other
 * inventory half, REQUIREMENTS §8).
 * <p>
 * Message names are the original ones ({@code "WriteSymbol"}, {@code "Link"}, ...); the name travels in the
 * {@code "msg"} key of the tag ({@link MenuMessagePayload#KEY_MESSAGE}). Screens call {@link #sendToServer} after
 * applying the message locally through {@link #processMessage} (prediction).
 */
public abstract class AbstractMystcraftMenu extends AbstractContainerMenu implements MenuMessageHandler {
    private static @Nullable ClientSender clientSender;

    protected final Inventory playerInventory;
    protected final Player player;

    protected AbstractMystcraftMenu(@Nullable MenuType<?> type, int containerId, Inventory playerInventory) {
        super(type, containerId);
        this.playerInventory = playerInventory;
        this.player = playerInventory.player;
    }

    /** Installed once by the client package. */
    public static void setClientSender(ClientSender sender) {
        clientSender = sender;
    }

    // --- messaging ------------------------------------------------------------------------------------------------

    /** Message name of an incoming tag. */
    public static String messageName(CompoundTag data) {
        return data.getStringOr(MenuMessagePayload.KEY_MESSAGE, "");
    }

    /** Client → server (no-op on a dedicated server or before the client hook is installed). */
    public void sendToServer(String message, CompoundTag data) {
        if (clientSender != null && isClient()) clientSender.send(MenuMessagePayload.of(containerId, message, data));
    }

    public void sendToServer(String message) {
        sendToServer(message, new CompoundTag());
    }

    /** Server → a specific client. */
    public void sendToClient(ServerPlayer target, String message, CompoundTag data) {
        PacketDistributor.sendToPlayer(target, MenuMessagePayload.of(containerId, message, data));
    }

    /** Server → the player owning this menu (no-op on the client). */
    protected void sendToClient(String message, CompoundTag data) {
        if (player instanceof ServerPlayer sp) sendToClient(sp, message, data);
    }

    protected boolean isClient() {
        return player.level().isClientSide();
    }

    protected boolean isServer() {
        return !player.level().isClientSide();
    }

    // --- slots -------------------------------------------------------------------------------------------------------

    /** Adds the 27 main + 9 hotbar player slots (main rows at y, hotbar at y+58) with an activity toggle. */
    protected void addPlayerSlots(int x, int y, BooleanSupplier active) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new ToggleSlot(playerInventory, col + row * 9 + 9, x + col * 18, y + row * 18, active));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new ToggleSlot(playerInventory, col, x + col * 18, y + 58, active));
        }
    }

    /**
     * Shift-click routing. Slots {@code [internalStart, internalEnd)} are the container's own; the next 36 slots are the
     * player's main inventory (27) followed by the hotbar (9).
     *
     * @param pageReceiver optional sink tried first for stacks coming from the player inventory (returns remainder)
     */
    protected ItemStack quickMove(Player player, int index, int internalStart, int internalEnd, int invStart, @Nullable UnaryOperator<ItemStack> pageReceiver) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.isActive()) return ItemStack.EMPTY;
        ItemStack raw = slot.getItem();
        ItemStack copy = raw.copy();
        int hotbarStart = invStart + 27;
        int invEnd = invStart + 36;

        if (index >= internalStart && index < internalEnd) {
            if (!moveItemStackTo(raw, invStart, invEnd, false)) return ItemStack.EMPTY;
        } else if (index >= invStart && index < invEnd) {
            if (pageReceiver != null) {
                ItemStack remainder = pageReceiver.apply(raw.copy());
                if (remainder.getCount() != raw.getCount()) {
                    slot.set(remainder);
                    slot.setChanged();
                    return ItemStack.EMPTY;
                }
            }
            boolean moved = internalEnd > internalStart && moveItemStackTo(raw, internalStart, internalEnd, false);
            if (!moved) {
                if (index < hotbarStart) {
                    if (!moveItemStackTo(raw, hotbarStart, invEnd, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(raw, invStart, hotbarStart, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (raw.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (raw.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, raw);
        return copy;
    }

    /** Puts a stack into the player's cursor. */
    protected void setCursor(Player player, ItemStack stack) {
        setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack);
    }

    protected ItemStack cursor() {
        return getCarried();
    }
}
