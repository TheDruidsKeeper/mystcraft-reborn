package com.techbucketdivision.mystcraft.api.symbol;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The slots of an Age description (world-building plan §2). Every symbol belongs to exactly one category; the
 * category says whether an Age needs it, how many pages of it an Age takes, and where its pages sit in a book.
 * <p>
 * The enum order is the <b>build order</b>: pages are flattened into the Age's symbol list category by category, so
 * biome pages come before the biome layout that consumes them. It is also the order of the book and of the desk strip.
 * Modifier categories ({@link #MATERIALS}, {@link #MODIFIERS}) never stand on their own page: their symbols are
 * attached to a page of another category.
 */
public enum SymbolCategory {
    TERRAIN("terrain", Kind.REQUIRED, 1, 1),
    BIOMES("biomes", Kind.CONDITIONAL, 1, 4),
    BIOME_LAYOUT("biome_layout", Kind.REQUIRED, 1, 1),
    LIGHTING("lighting", Kind.REQUIRED, 1, 1),
    CELESTIALS("celestials", Kind.REQUIRED, 1, 5),
    SKY_COLORS("sky_colors", Kind.OPTIONAL, 0, 4),
    WORLD_COLORS("world_colors", Kind.OPTIONAL, 0, 3),
    WEATHER("weather", Kind.OPTIONAL, 0, 1),
    STRUCTURES("structures", Kind.OPTIONAL, 0, 3),
    FEATURES("features", Kind.OPTIONAL, 0, 3),
    EFFECTS("effects", Kind.OPTIONAL, 0, 2),
    MATERIALS("materials", Kind.MODIFIER, 0, 0),
    MODIFIERS("modifiers", Kind.MODIFIER, 0, 0);

    public enum Kind {
        /** Every Age has one; the filler adds it when the author did not. */
        REQUIRED,
        /** Required unless another page makes it moot (biomes with the native layout). */
        CONDITIONAL,
        /** May be absent; the Age then behaves like vanilla. */
        OPTIONAL,
        /** Never a page of its own: attached to a page of another category. */
        MODIFIER
    }

    private final String id;
    private final Kind kind;
    private final int defaultMin;
    private final int defaultMax;

    SymbolCategory(String id, Kind kind, int defaultMin, int defaultMax) {
        this.id = id;
        this.kind = kind;
        this.defaultMin = defaultMin;
        this.defaultMax = defaultMax;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isModifier() {
        return kind == Kind.MODIFIER;
    }

    /** Required categories are filled when empty; conditional ones unless the condition waives them. */
    public boolean isRequired() {
        return kind == Kind.REQUIRED || kind == Kind.CONDITIONAL;
    }

    /** Default page count range (config may override). */
    public int defaultMin() {
        return defaultMin;
    }

    public int defaultMax() {
        return defaultMax;
    }

    public String descriptionId() {
        return "category.mystcraft." + id;
    }

    public Component displayName() {
        return Component.translatable(descriptionId());
    }

    /** One line on what the category does and what applies when it is empty. */
    public Component description() {
        return Component.translatable(descriptionId() + ".desc");
    }

    public static SymbolCategory byId(String id) {
        for (SymbolCategory c : values()) if (c.id.equals(id.toLowerCase(Locale.ROOT))) return c;
        throw new IllegalArgumentException("Unknown symbol category " + id);
    }
}
