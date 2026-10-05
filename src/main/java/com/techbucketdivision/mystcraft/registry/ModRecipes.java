package com.techbucketdivision.mystcraft.registry;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.item.recipe.LinkingBookRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Special recipe: Link Panel page + Leather → Unlinked Link Book (original spec §2.3.3). */
public final class ModRecipes {
    private ModRecipes() {}

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mystcraft.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LinkingBookRecipe>> LINKING_BOOK = SERIALIZERS.register("linking_book",
            () -> new RecipeSerializer<>(LinkingBookRecipe.MAP_CODEC, LinkingBookRecipe.STREAM_CODEC));
}
