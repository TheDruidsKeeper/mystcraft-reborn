package com.techbucketdivision.mystcraft.menu;

import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.api.symbol.ColorGradient;
import com.techbucketdivision.mystcraft.blockentity.InkMixerBlockEntity;
import com.techbucketdivision.mystcraft.linking.InkEffects;
import com.techbucketdivision.mystcraft.menu.slot.CraftOutputSlot;
import com.techbucketdivision.mystcraft.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ink Mixer container (REQUIREMENTS §8.3). Slots: 0 ink in, 1 paper, 2 empty container out, 3–29 inventory,
 * 30–38 hotbar, 39 craft output. Messages client→server: {@code Consume}. Server→client: {@code SetInk(Ink)},
 * {@code SetEffects(Effects)}.
 */
public class InkMixerMenu extends AbstractMystcraftMenu {
    public static final String MSG_SET_INK = "SetInk";
    public static final String MSG_SET_EFFECTS = "SetEffects";
    public static final String MSG_CONSUME = "Consume";

    public static final int INV_START = 3;
    public static final int SLOT_OUTPUT = 39;

    private final InkMixerBlockEntity mixer;
    private final SimpleContainer result = new SimpleContainer(1);
    private final CraftOutputSlot outputSlot;

    private boolean cachedHasInk;
    private final Set<LinkProperty> effects = new LinkedHashSet<>();
    private @Nullable ColorGradient gradient;

    public InkMixerMenu(int containerId, Inventory inv, InkMixerBlockEntity mixer) {
        super(ModMenus.INK_MIXER.get(), containerId, inv);
        this.mixer = mixer;
        addSlot(new ResourceHandlerSlot(mixer.inventory, mixer.inventory::set, InkMixerBlockEntity.SLOT_INK_IN, 8, 27));
        addSlot(new ResourceHandlerSlot(mixer.inventory, mixer.inventory::set, InkMixerBlockEntity.SLOT_PAPER, 8, 48));
        addSlot(new ResourceHandlerSlot(mixer.inventory, mixer.inventory::set, InkMixerBlockEntity.SLOT_INK_OUT, 152, 27));
        addStandardInventorySlots(inv, 8, 99);
        this.outputSlot = new CraftOutputSlot(result, 0, 152, 48, mixer::buildItem);
        addSlot(outputSlot);
        updateCraftResult();
    }

    public static InkMixerMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (!(inv.player.level().getBlockEntity(pos) instanceof InkMixerBlockEntity be)) {
            throw new IllegalStateException("No ink mixer at " + pos);
        }
        return new InkMixerMenu(containerId, inv, be);
    }

    public void updateCraftResult() {
        result.setItem(0, mixer.getCraftedItem());
    }

    @Override
    public void broadcastChanges() {
        updateCraftResult();
        super.broadcastChanges();
        if (!isServer()) return;
        if (cachedHasInk != mixer.hasInk()) {
            cachedHasInk = mixer.hasInk();
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Ink", cachedHasInk);
            sendToClient(MSG_SET_INK, tag);
        }
        Set<LinkProperty> current = mixer.getEffects();
        if (!current.equals(effects)) {
            effects.clear();
            effects.addAll(current);
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (LinkProperty p : current) list.add(StringTag.valueOf(p.name()));
            tag.put("Effects", list);
            sendToClient(MSG_SET_EFFECTS, tag);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(mixer, player);
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
        return quickMove(player, index, 0, INV_START, INV_START, null);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != result && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public void processMessage(Player player, CompoundTag data) {
        switch (messageName(data)) {
            case MSG_SET_INK -> {
                cachedHasInk = data.getBooleanOr("Ink", false);
                if (isClient()) mixer.setHasInk(cachedHasInk);
            }
            case MSG_SET_EFFECTS -> {
                effects.clear();
                for (Tag entry : data.getListOrEmpty("Effects")) {
                    entry.asString().ifPresent(name -> effects.add(LinkProperty.getOrCreate(name)));
                }
                if (isClient()) mixer.setEffects(effects);
                gradient = InkEffects.getPropertiesGradient(effects);
            }
            case MSG_CONSUME -> {
                ItemStack held = cursor();
                if (held.isEmpty() || !mixer.hasInk()) return;
                // one ingredient per click, whatever the stack size (Reborn: effects are switches, not probabilities)
                setCursor(player, mixer.addItems(held, 1));
            }
            default -> {
            }
        }
        updateCraftResult();
    }

    // --- screen state --------------------------------------------------------------------------------------------------

    public InkMixerBlockEntity getMixer() {
        return mixer;
    }

    public boolean hasInk() {
        return isClient() ? cachedHasInk : mixer.hasInk();
    }

    public Set<LinkProperty> getEffects() {
        return Collections.unmodifiableSet(effects);
    }

    /** Colour gradient of the current effect mix for the basin (null until the effects were synced). */
    public @Nullable ColorGradient getPropertyGradient() {
        return gradient;
    }
}
