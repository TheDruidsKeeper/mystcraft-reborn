package com.techbucketdivision.mystcraft.menu;

import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.api.symbol.ColorGradient;
import com.techbucketdivision.mystcraft.blockentity.InkMixerBlockEntity;
import com.techbucketdivision.mystcraft.linking.InkEffects;
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
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Ink Mixer container (REQUIREMENTS §8.3). Slots: 0 ink in, 1 paper, 2 empty container out, 3–29 inventory,
 * 30–38 hotbar, 39 craft output. Messages client→server: {@code Consume(Single)}. Server→client: {@code SetInk(Ink)},
 * {@code SetSeed(Seed)}, {@code SetProperties(Properties)}.
 */
public class InkMixerMenu extends AbstractMystcraftMenu {
    public static final String MSG_SET_SEED = "SetSeed";
    public static final String MSG_SET_INK = "SetInk";
    public static final String MSG_SET_PROPERTIES = "SetProperties";
    public static final String MSG_CONSUME = "Consume";

    public static final int INV_START = 3;
    public static final int SLOT_OUTPUT = 39;

    private final InkMixerBlockEntity mixer;
    private final SimpleContainer result = new SimpleContainer(1);
    private final CraftOutputSlot outputSlot;

    private boolean cachedHasInk;
    private long cachedSeed;
    private final Map<LinkProperty, Float> properties = new HashMap<>();
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
        if (cachedSeed != mixer.getNextSeed()) {
            cachedSeed = mixer.getNextSeed();
            CompoundTag tag = new CompoundTag();
            tag.putLong("Seed", cachedSeed);
            sendToClient(MSG_SET_SEED, tag);
        }
        Map<LinkProperty, Float> probs = mixer.getProbabilities();
        if (!probs.equals(properties)) {
            properties.clear();
            properties.putAll(probs);
            CompoundTag tag = new CompoundTag();
            CompoundTag map = new CompoundTag();
            for (Map.Entry<LinkProperty, Float> e : probs.entrySet()) map.putFloat(e.getKey().name(), e.getValue());
            tag.put("Properties", map);
            sendToClient(MSG_SET_PROPERTIES, tag);
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
            case MSG_SET_SEED -> {
                cachedSeed = data.getLongOr("Seed", 0L);
                if (isClient()) mixer.setNextSeed(cachedSeed);
            }
            case MSG_SET_PROPERTIES -> {
                properties.clear();
                CompoundTag map = data.getCompoundOrEmpty("Properties");
                for (String key : map.keySet()) {
                    properties.put(LinkProperty.getOrCreate(key), map.getFloatOr(key, 0f));
                }
                if (isClient()) mixer.setProbabilities(properties);
                gradient = InkEffects.getPropertiesGradient(properties);
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

    public Map<LinkProperty, Float> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    /** Colour gradient of the current property mix for the basin (null until properties were synced). */
    public @Nullable ColorGradient getPropertyGradient() {
        return gradient;
    }
}
