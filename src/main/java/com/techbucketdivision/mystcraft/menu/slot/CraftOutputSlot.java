package com.techbucketdivision.mystcraft.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/**
 * Output slot of the Ink Mixer / Book Binder (the original {@code SlotCraftCustom}): nothing can be placed, taking the
 * preview item calls the builder which finalises the stack and consumes the ingredients.
 */
public class CraftOutputSlot extends Slot {
    private final BiConsumer<ItemStack, Player> builder;

    public CraftOutputSlot(Container resultContainer, int index, int x, int y, BiConsumer<ItemStack, Player> builder) {
        super(resultContainer, index, x, y);
        this.builder = builder;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        builder.accept(stack, player);
        super.onTake(player, stack);
    }

    /** Finalises a copy of the preview (used by shift-click). */
    public ItemStack build(Player player) {
        ItemStack result = getItem().copy();
        if (result.isEmpty()) return ItemStack.EMPTY;
        builder.accept(result, player);
        set(ItemStack.EMPTY);
        return result;
    }
}
