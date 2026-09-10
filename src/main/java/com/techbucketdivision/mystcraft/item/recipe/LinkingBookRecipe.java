package com.techbucketdivision.mystcraft.item.recipe;

import com.mojang.serialization.MapCodec;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.item.UnlinkedBookItem;
import com.techbucketdivision.mystcraft.registry.ModRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Special recipe (REQUIREMENTS §2.3.3): exactly one Link Panel page and exactly one Leather anywhere in the grid,
 * nothing else → Unlinked Link Book carrying the panel's properties. Disabled by {@code crafting.linkbook.enabled}.
 * Datapack JSON: {@code {"type": "mystcraft:linking_book"}}.
 */
public class LinkingBookRecipe extends CustomRecipe {

    public static final MapCodec<LinkingBookRecipe> MAP_CODEC = MapCodec.unit(LinkingBookRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkingBookRecipe> STREAM_CODEC = StreamCodec.unit(new LinkingBookRecipe());

    public LinkingBookRecipe() {
        super();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!MystcraftConfig.LINKBOOK_RECIPE.get()) return false;
        return !findPanel(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack panel = findPanel(input);
        return panel.isEmpty() ? ItemStack.EMPTY : UnlinkedBookItem.createFrom(panel);
    }

    /** The single link panel of a valid grid, or EMPTY when the grid does not match. */
    private static ItemStack findPanel(CraftingInput input) {
        ItemStack panel = ItemStack.EMPTY;
        ItemStack cover = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (PageItem.isLinkPanel(stack)) {
                if (!panel.isEmpty()) return ItemStack.EMPTY;
                panel = stack;
            } else if (isValidCover(stack)) {
                if (!cover.isEmpty()) return ItemStack.EMPTY;
                cover = stack;
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (panel.isEmpty() || cover.isEmpty()) return ItemStack.EMPTY;
        return panel;
    }

    private static boolean isValidCover(ItemStack stack) {
        return stack.is(Items.LEATHER);
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipes.LINKING_BOOK.get();
    }
}
