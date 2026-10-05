package com.techbucketdivision.mystcraft.linking;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.api.symbol.ColorGradient;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import com.techbucketdivision.mystcraft.util.Colors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ink ingredient table (original spec §7.2, Reborn revision): <b>one ingredient per effect, one effect per
 * ingredient</b>. The table comes from the config ({@code inkmixer.ingredients}, {@code inkmixer.clearIngredient});
 * the defaults are the Reborn balance where the price of an ingredient follows what the effect gives.
 * <p>
 * The table is resolved lazily from the config and re-resolved when the config list changes, so a config reload is
 * picked up without a restart. Invalid entries (unknown item, unknown or non-inkable property, duplicate item or
 * effect) are logged under {@code [ink]} and skipped.
 */
public final class InkEffects {
    private InkEffects() {}

    /** Default table, {@code effect=item} per entry (see the plan §8 for the reasoning behind each price). */
    public static final List<String> DEFAULT_INGREDIENTS = List.of(
            "generate_platform=minecraft:clay_ball",
            "maintain_momentum=minecraft:feather",
            "disarm=minecraft:gunpowder",
            "intra_linking_only=minecraft:compass",
            "intra_linking=minecraft:ender_pearl",
            "relative=minecraft:amethyst_shard",
            "following=minecraft:ender_eye");
    /** Default item that clears every effect from the basin (the original's dilution role). */
    public static final String DEFAULT_CLEAR_INGREDIENT = "minecraft:black_dye";

    private static final Colors.RGB DEFAULT_COLOR = Colors.RGB.WHITE;
    private static final Colors.RGB EMPTY_COLOR = Colors.RGB.BLACK;

    /** One usable ingredient: the item and the effect it switches on, or {@code null} when it clears the basin. */
    public record Ingredient(Item item, @Nullable LinkProperty effect) {
        public boolean clears() {
            return effect == null;
        }

        public ItemStack example() {
            return new ItemStack(item);
        }
    }

    private static @Nullable List<? extends String> cachedSource;
    private static @Nullable String cachedClear;
    private static Map<Item, Ingredient> table = Map.of();
    private static List<Ingredient> ordered = List.of();

    // --- resolution ------------------------------------------------------------------------------------------------

    private static synchronized void ensureResolved() {
        List<? extends String> source;
        String clear;
        try {
            source = MystcraftConfig.INK_INGREDIENTS.get();
            clear = MystcraftConfig.INK_CLEAR_INGREDIENT.get();
        } catch (IllegalStateException | NullPointerException e) {
            source = DEFAULT_INGREDIENTS; // config not loaded yet (early client code paths, unit tests)
            clear = DEFAULT_CLEAR_INGREDIENT;
        }
        if (source == cachedSource && clear.equals(cachedClear)) return;
        Map<Item, Ingredient> resolved = new LinkedHashMap<>();
        Set<LinkProperty> usedEffects = new HashSet<>();
        for (String entry : source) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                Mystcraft.LOGGER.warn("[ink] ignoring malformed ingredient entry '{}' (expected effect=item)", entry);
                continue;
            }
            LinkProperty effect = LinkProperty.get(entry.substring(0, eq).trim());
            Item item = parseItem(entry.substring(eq + 1).trim());
            if (effect == null || !effect.inkable()) {
                Mystcraft.LOGGER.warn("[ink] ignoring ingredient entry '{}': unknown or non-inkable effect", entry);
                continue;
            }
            if (item == null) {
                Mystcraft.LOGGER.warn("[ink] ignoring ingredient entry '{}': unknown item", entry);
                continue;
            }
            if (!usedEffects.add(effect)) {
                Mystcraft.LOGGER.warn("[ink] ignoring ingredient entry '{}': effect already has an ingredient", entry);
                continue;
            }
            if (resolved.containsKey(item)) {
                Mystcraft.LOGGER.warn("[ink] ignoring ingredient entry '{}': item already bound to {}", entry, resolved.get(item).effect());
                continue;
            }
            resolved.put(item, new Ingredient(item, effect));
        }
        Item clearItem = parseItem(clear);
        if (clearItem == null) {
            Mystcraft.LOGGER.warn("[ink] unknown clear ingredient '{}', using {}", clear, DEFAULT_CLEAR_INGREDIENT);
            clearItem = Items.BLACK_DYE;
        }
        if (resolved.containsKey(clearItem)) {
            Mystcraft.LOGGER.warn("[ink] clear ingredient {} is also bound to {}; it clears", clear, resolved.get(clearItem).effect());
        }
        resolved.put(clearItem, new Ingredient(clearItem, null));
        table = Map.copyOf(resolved);
        ordered = List.copyOf(resolved.values());
        cachedSource = source;
        cachedClear = clear;
        Mystcraft.LOGGER.info("[ink] ingredient table: {}", ordered.stream()
                .map(i -> BuiltInRegistries.ITEM.getKey(i.item()) + "->" + (i.clears() ? "clear" : i.effect().name())).toList());
    }

    private static @Nullable Item parseItem(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) return null;
        return BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    // --- lookup ------------------------------------------------------------------------------------------------------

    /** The ingredient the stack is, or {@code null} when the item has no effect on the ink. */
    public static @Nullable Ingredient ingredientFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ensureResolved();
        return table.get(stack.getItem());
    }

    public static boolean isInkModifier(ItemStack stack) {
        return ingredientFor(stack) != null;
    }

    /** Every ingredient in table order (effects first, the clearing item last). */
    public static List<Ingredient> getIngredients() {
        ensureResolved();
        return ordered;
    }

    /** Properties the mixer and Link Modifier can attach (inkable, coloured), in registration order. */
    public static List<LinkProperty> getProperties() {
        List<LinkProperty> out = new ArrayList<>();
        for (LinkProperty p : LinkProperty.all().values()) {
            if (p.inkable() && p.color() != null) out.add(p);
        }
        return out;
    }

    /** Whether the config allows the mixer to produce this property. */
    public static boolean isPropertyAllowed(LinkProperty property) {
        return property.inkable() && MystcraftConfig.isLinkEffectEnabled(property.name());
    }

    /** Relative is inkable but excluded from creative listings and trades (original spec §7.2). */
    public static boolean isCraftable(LinkProperty property) {
        return property.inkable() && property != LinkProperty.RELATIVE;
    }

    public static Colors.RGB getPropertyColor(LinkProperty property) {
        Colors.RGB color = property.color();
        return color == null ? DEFAULT_COLOR : color;
    }

    /** Basin gradient: equal bands per effect in the ink; plain ink is black. */
    public static ColorGradient getPropertiesGradient(Collection<LinkProperty> properties) {
        ColorGradient gradient = new ColorGradient();
        if (properties.isEmpty()) {
            gradient.pushColor(EMPTY_COLOR, 1.0f);
            return gradient;
        }
        float interval = 1.0f / properties.size();
        for (LinkProperty property : properties) gradient.pushColor(getPropertyColor(property), interval);
        return gradient;
    }
}
