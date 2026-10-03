package com.techbucketdivision.mystcraft.linking;

import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.api.symbol.ColorGradient;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import com.techbucketdivision.mystcraft.util.Colors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ink effect registry (REQUIREMENTS §7.2, §19.1): which items add which link-property probabilities in the Ink
 * Mixer. Bindings by exact stack (item + components, count ignored), by item tag and by item; lookup order
 * stack → tag → item. The sum of one binding's probabilities may not exceed 1.
 * <p>
 * The original bound black dye to the empty property {@code ""} (dilution). That is modelled by the non-inkable
 * {@link #DILUTION} property.
 */
public final class InkEffects {
    private InkEffects() {}

    /** Dilution ("" in the original): lowers every other probability without adding a flag. */
    public static final LinkProperty DILUTION = LinkProperty.register("dilution", null, false);

    private static final Colors.RGB DEFAULT_COLOR = Colors.RGB.WHITE;
    private static final Colors.RGB EMPTY_COLOR = Colors.RGB.BLACK;

    private static final Map<StackKey, Map<LinkProperty, Float>> STACK_BINDINGS = new LinkedHashMap<>();
    private static final Map<TagKey<Item>, Map<LinkProperty, Float>> TAG_BINDINGS = new LinkedHashMap<>();
    private static final Map<Item, Map<LinkProperty, Float>> ITEM_BINDINGS = new LinkedHashMap<>();

    /** Exact-stack key: item + component patch, count ignored. */
    private record StackKey(Item item, int componentsHash) {
        static StackKey of(ItemStack stack) {
            return new StackKey(stack.getItem(), stack.getComponentsPatch().hashCode());
        }
    }

    // --- registration ------------------------------------------------------------------------------------------

    public static synchronized void addPropertyToItem(ItemStack stack, LinkProperty property, float probability) {
        add(STACK_BINDINGS.computeIfAbsent(StackKey.of(stack), k -> new LinkedHashMap<>()), property, probability);
    }

    public static synchronized void addPropertyToItem(TagKey<Item> tag, LinkProperty property, float probability) {
        add(TAG_BINDINGS.computeIfAbsent(tag, k -> new LinkedHashMap<>()), property, probability);
    }

    public static synchronized void addPropertyToItem(Item item, LinkProperty property, float probability) {
        add(ITEM_BINDINGS.computeIfAbsent(item, k -> new LinkedHashMap<>()), property, probability);
    }

    private static void add(Map<LinkProperty, Float> map, LinkProperty property, float probability) {
        map.merge(property, probability, Float::sum);
        float total = 0;
        for (float f : map.values()) total += f;
        if (total > 1.0001f) {
            throw new IllegalStateException("Total of all ink property probabilities from an item cannot exceed 1 (" + map + ")");
        }
    }

    // --- lookup ------------------------------------------------------------------------------------------------

    /** Property probabilities contributed by one item; empty map when the item is not an ink modifier. */
    public static Map<LinkProperty, Float> getItemEffects(ItemStack stack) {
        if (stack.isEmpty()) return Map.of();
        Map<LinkProperty, Float> map = STACK_BINDINGS.get(StackKey.of(stack));
        if (map == null) {
            for (Map.Entry<TagKey<Item>, Map<LinkProperty, Float>> e : TAG_BINDINGS.entrySet()) {
                if (stack.is(e.getKey())) {
                    map = e.getValue();
                    break;
                }
            }
        }
        if (map == null) map = ITEM_BINDINGS.get(stack.getItem());
        return map == null ? Map.of() : Collections.unmodifiableMap(map);
    }

    public static boolean isInkModifier(ItemStack stack) {
        return !getItemEffects(stack).isEmpty();
    }

    /** One usable ingredient for display: a representative stack (empty for tags with no items loaded) and its effects. */
    public record Ingredient(ItemStack example, @Nullable TagKey<Item> tag, Map<LinkProperty, Float> effects) {
        public boolean available() {
            return !example.isEmpty();
        }
    }

    /**
     * Every registered ingredient in registration order (stack bindings, then item bindings, then tag bindings).
     * Tag bindings whose tag has no items in this game are included with an empty example so GUIs can skip them.
     */
    public static synchronized List<Ingredient> getIngredients() {
        List<Ingredient> out = new ArrayList<>();
        for (Map.Entry<StackKey, Map<LinkProperty, Float>> e : STACK_BINDINGS.entrySet()) {
            out.add(new Ingredient(new ItemStack(e.getKey().item()), null, Collections.unmodifiableMap(e.getValue())));
        }
        for (Map.Entry<Item, Map<LinkProperty, Float>> e : ITEM_BINDINGS.entrySet()) {
            out.add(new Ingredient(new ItemStack(e.getKey()), null, Collections.unmodifiableMap(e.getValue())));
        }
        for (Map.Entry<TagKey<Item>, Map<LinkProperty, Float>> e : TAG_BINDINGS.entrySet()) {
            ItemStack example = BuiltInRegistries.ITEM.get(e.getKey())
                    .flatMap(named -> named.stream().findFirst())
                    .map(holder -> new ItemStack(holder.value()))
                    .orElse(ItemStack.EMPTY);
            out.add(new Ingredient(example, e.getKey(), Collections.unmodifiableMap(e.getValue())));
        }
        return out;
    }

    /** Properties the mixer can attach (inkable, coloured), in registration order. */
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

    /** Relative is inkable but excluded from creative listings and trades (REQUIREMENTS §7.2). */
    public static boolean isCraftable(LinkProperty property) {
        return property.inkable() && property != LinkProperty.RELATIVE && property != DILUTION;
    }

    public static Colors.RGB getPropertyColor(LinkProperty property) {
        Colors.RGB color = property.color();
        return color == null ? DEFAULT_COLOR : color;
    }

    /**
     * GUI gradient (REQUIREMENTS §7.2): every property with p ≥ 0.001 pushes its colour with interval p (split as
     * (p − 0.3) + 0.3 when p > 0.3); the remaining (1 − Σ) is pushed as black the same way.
     */
    public static ColorGradient getPropertiesGradient(Map<LinkProperty, Float> properties) {
        ColorGradient gradient = new ColorGradient();
        float max = 1.0f;
        float total = 0;
        for (Map.Entry<LinkProperty, Float> e : properties.entrySet()) {
            float value = e.getValue();
            if (value < 0.001f) continue;
            Colors.RGB color = e.getKey() == DILUTION ? EMPTY_COLOR : getPropertyColor(e.getKey());
            float interval = value * max;
            total += interval;
            if (interval > 0.3f) {
                gradient.pushColor(color, interval - 0.3f);
                interval = 0.3f;
            }
            gradient.pushColor(color, interval);
        }
        if (total < max - 0.01f) {
            float interval = max - total;
            if (interval > 0.3f) {
                gradient.pushColor(EMPTY_COLOR, interval - 0.3f);
                interval = 0.3f;
            }
            gradient.pushColor(EMPTY_COLOR, interval);
        }
        return gradient;
    }

    // --- defaults ----------------------------------------------------------------------------------------------

    private static TagKey<Item> dust(String metal) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "dusts/" + metal));
    }

    /** Registers the built-in bindings (REQUIREMENTS §7.2). Called once from common setup. */
    public static synchronized void registerDefaults() {
        if (!ITEM_BINDINGS.isEmpty()) return;

        addPropertyToItem(Items.GUNPOWDER, LinkProperty.DISARM, 0.2f);
        addPropertyToItem(Items.MUSHROOM_STEW, LinkProperty.DISARM, 0.05f);
        addPropertyToItem(Items.CLAY_BALL, LinkProperty.GENERATE_PLATFORM, 0.25f);
        addPropertyToItem(Items.EXPERIENCE_BOTTLE, LinkProperty.INTRA_LINKING, 0.15f);
        addPropertyToItem(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "dyes/black")), DILUTION, 0.5f);
        addPropertyToItem(Items.BLACK_DYE, DILUTION, 0.5f);
        addPropertyToItem(Items.ENDER_PEARL, LinkProperty.INTRA_LINKING, 0.15f);
        addPropertyToItem(Items.ENDER_PEARL, LinkProperty.DISARM, 0.15f);
        addPropertyToItem(Items.FEATHER, LinkProperty.MAINTAIN_MOMENTUM, 0.15f);
        addPropertyToItem(Items.FIRE_CHARGE, LinkProperty.DISARM, 0.25f);
        // Reborn addition: vanilla nuggets stand in for the metal dusts of the original (which only other mods
        // provide), at roughly half the dust's odds so a nugget is not worth more than a dust.
        addPropertyToItem(Items.GOLD_NUGGET, LinkProperty.INTRA_LINKING, 0.12f);
        addPropertyToItem(Items.GOLD_NUGGET, LinkProperty.GENERATE_PLATFORM, 0.05f);
        addPropertyToItem(Items.GOLD_NUGGET, LinkProperty.DISARM, 0.05f);
        addPropertyToItem(Items.IRON_NUGGET, LinkProperty.GENERATE_PLATFORM, 0.08f);
        addPropertyToItem(Items.IRON_NUGGET, LinkProperty.INTRA_LINKING, 0.08f);

        addPropertyToItem(dust("brass"), LinkProperty.DISARM, 0.15f);
        addPropertyToItem(dust("bronze"), LinkProperty.DISARM, 0.15f);
        addPropertyToItem(dust("tin"), LinkProperty.GENERATE_PLATFORM, 0.1f);
        addPropertyToItem(dust("tin"), LinkProperty.INTRA_LINKING, 0.1f);
        addPropertyToItem(dust("iron"), LinkProperty.GENERATE_PLATFORM, 0.15f);
        addPropertyToItem(dust("iron"), LinkProperty.INTRA_LINKING, 0.15f);
        addPropertyToItem(dust("lead"), LinkProperty.DISARM, 0.2f);
        addPropertyToItem(dust("lead"), LinkProperty.INTRA_LINKING, 0.2f);
        addPropertyToItem(dust("silver"), LinkProperty.GENERATE_PLATFORM, 0.2f);
        addPropertyToItem(dust("silver"), LinkProperty.INTRA_LINKING, 0.2f);
        addPropertyToItem(dust("diamond"), LinkProperty.INTRA_LINKING, 0.25f);
        addPropertyToItem(dust("diamond"), LinkProperty.MAINTAIN_MOMENTUM, 0.1f);
        addPropertyToItem(dust("diamond"), LinkProperty.GENERATE_PLATFORM, 0.1f);
        addPropertyToItem(dust("gold"), LinkProperty.INTRA_LINKING, 0.25f);
        addPropertyToItem(dust("gold"), LinkProperty.GENERATE_PLATFORM, 0.1f);
        addPropertyToItem(dust("gold"), LinkProperty.DISARM, 0.1f);
    }
}
