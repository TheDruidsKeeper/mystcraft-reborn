package com.techbucketdivision.mystcraft.blockentity;

import com.mojang.serialization.Codec;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.linking.InkEffects;
import com.techbucketdivision.mystcraft.menu.InkMixerMenu;
import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import com.techbucketdivision.mystcraft.registry.ModFluids;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Ink Mixer (REQUIREMENTS §3.2). Slots: 0 ink container in, 1 paper, 2 empty container out. Holds one basin of ink
 * the set of link effects mixed into it (stored as a property -> 1.0 map); crafting turns a paper into a Link Panel
 * page carrying exactly those effects. Reborn: deterministic, one ingredient per effect, refilling the ink resets.
 */
public class InkMixerBlockEntity extends MystBlockEntity implements MenuProvider {
    public static final int SLOT_INK_IN = 0;
    public static final int SLOT_PAPER = 1;
    public static final int SLOT_INK_OUT = 2;

    private static final Codec<Map<LinkProperty, Float>> PROBABILITIES_CODEC = Codec.unboundedMap(LinkProperty.CODEC, Codec.FLOAT);

    public final FilteredItemHandler inventory = new FilteredItemHandler(3, this::acceptsItem, this::markForUpdate);

    private boolean hasInk = false;
    private Map<LinkProperty, Float> probabilities = new HashMap<>();
    private long nextSeed;

    public InkMixerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INK_MIXER.get(), pos, state);
        this.nextSeed = new Random().nextLong();
    }

    private boolean acceptsItem(int slot, ItemResource resource) {
        return switch (slot) {
            case SLOT_INK_IN -> BookUtil.isInkContainer(resource.toStack());
            case SLOT_PAPER -> resource.is(Items.PAPER);
            default -> false;
        };
    }

    // --- persistence ------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putChild("inventory", inventory);
        output.putBoolean("ink", hasInk);
        output.putLong("seed", nextSeed);
        output.store("probabilities", PROBABILITIES_CODEC, Map.copyOf(probabilities));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.readChild("inventory", inventory);
        hasInk = input.getBooleanOr("ink", false);
        nextSeed = input.getLongOr("seed", nextSeed);
        probabilities = new HashMap<>(input.read("probabilities", PROBABILITIES_CODEC).orElse(Map.of()));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        dropContents(inventory);
        super.preRemoveSideEffects(pos, state);
    }

    // --- ticking ----------------------------------------------------------------------------------------------------

    /** Drains exactly one bucket of ink from the container in slot 0 when the basin is empty. */
    public void serverTick() {
        if (hasInk) return;
        ItemStack container = inventory.getStack(SLOT_INK_IN);
        if (container.isEmpty()) return;
        FluidStack contained = FluidUtil.getFirstStackContained(container);
        if (contained.isEmpty() || !ModFluids.isInk(contained.getFluid()) || contained.getAmount() != FluidType.BUCKET_VOLUME) return;

        // Drain into a throw-away tank: the basin is a boolean, the container exchange is what matters.
        net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler sink = new net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler(1, FluidType.BUCKET_VOLUME);
        ItemStack out = inventory.getStack(SLOT_INK_OUT);
        InkContainers.Result moved = InkContainers.drainInto(container, FluidResource.of(contained), FluidType.BUCKET_VOLUME, sink, 0);
        if (moved == null) return;
        ItemStack emptied = moved.container();
        if (!emptied.isEmpty() && !out.isEmpty()
                && (!ItemStack.isSameItemSameComponents(out, emptied) || out.getCount() + emptied.getCount() > out.getMaxStackSize())) {
            return; // empty container would not fit in the output slot (nothing was consumed: the scratch sink is discarded)
        }
        hasInk = true;
        probabilities.clear(); // fresh ink: all effects reset
        container.shrink(1);
        inventory.setStack(SLOT_INK_IN, container);
        if (!emptied.isEmpty()) {
            if (out.isEmpty()) inventory.setStack(SLOT_INK_OUT, emptied);
            else inventory.setStack(SLOT_INK_OUT, out.copyWithCount(out.getCount() + emptied.getCount()));
        }
        markForUpdate();
    }

    // --- ink modifiers ------------------------------------------------------------------------------------------------

    /**
     * Consumes exactly one item from the stack and adds its link effects to the basin (Reborn: deterministic — one
     * ingredient switches its effects on; there is no probability). An item that only dilutes (black dye) clears the
     * basin's effects. Effects already present are not consumed again.
     *
     * @return the remaining stack
     */
    public ItemStack addItems(ItemStack stack, int amount) {
        if (!hasInk || stack.isEmpty() || amount <= 0) return stack;
        Map<LinkProperty, Float> itemEffects = InkEffects.getItemEffects(stack);
        if (itemEffects == null || itemEffects.isEmpty()) return stack;
        boolean changed = false;
        for (LinkProperty property : itemEffects.keySet()) {
            if (property == InkEffects.DILUTION) {
                if (!probabilities.isEmpty()) {
                    probabilities.clear();
                    changed = true;
                }
                continue;
            }
            if (!isPropertyAllowed(property)) continue;
            if (probabilities.put(property, 1f) == null) changed = true;
        }
        if (!changed) return stack; // nothing new to add: keep the item
        stack.shrink(1);
        markForUpdate();
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    /** Whether the basin currently carries the effect. */
    public boolean hasEffect(LinkProperty property) {
        return probabilities.getOrDefault(property, 0f) >= 0.5f;
    }

    private static boolean isPropertyAllowed(LinkProperty property) {
        return property.inkable() && MystcraftConfig.isLinkEffectEnabled(property.name());
    }

    // --- crafting -------------------------------------------------------------------------------------------------------

    public boolean canBuildItem() {
        ItemStack paper = inventory.getStack(SLOT_PAPER);
        return hasInk && !paper.isEmpty() && paper.is(Items.PAPER);
    }

    /** The preview item shown in the output slot (a plain link panel), or empty. */
    public ItemStack getCraftedItem() {
        return canBuildItem() ? PageItem.createLinkPanel(Set.of()) : ItemStack.EMPTY;
    }

    /** Called when the player takes the crafted page: rolls the properties onto it and consumes ink + paper. */
    public void buildItem(ItemStack result, Player player) {
        if (!canBuildItem()) return;
        if (!(result.getItem() instanceof PageItem)) {
            result.setCount(0);
            return;
        }
        // Deterministic: every effect in the basin goes onto the panel.
        Set<LinkProperty> props = new LinkedHashSet<>();
        for (Map.Entry<LinkProperty, Float> e : probabilities.entrySet()) {
            if (e.getValue() >= 0.5f) props.add(e.getKey());
        }
        ItemStack panel = PageItem.createLinkPanel(props);
        result.applyComponents(panel.getComponentsPatch());
        nextSeed = new Random(nextSeed).nextLong();
        hasInk = false;
        probabilities.clear();
        ItemStack paper = inventory.getStack(SLOT_PAPER);
        paper.shrink(1);
        inventory.setStack(SLOT_PAPER, paper);
        markForUpdate();
    }

    // --- state ---------------------------------------------------------------------------------------------------------

    public boolean hasInk() {
        return hasInk;
    }

    public void setHasInk(boolean hasInk) {
        this.hasInk = hasInk;
    }

    public long getNextSeed() {
        return nextSeed;
    }

    public void setNextSeed(long seed) {
        this.nextSeed = seed;
    }

    public Map<LinkProperty, Float> getProbabilities() {
        return Collections.unmodifiableMap(probabilities);
    }

    /** Client-side replacement of the probability map (from {@code SetProperties}). */
    public void setProbabilities(Map<LinkProperty, Float> map) {
        probabilities = new HashMap<>(map);
    }

    // --- menu ----------------------------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.mystcraft.ink_mixer");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new InkMixerMenu(containerId, inventory, this);
    }
}
