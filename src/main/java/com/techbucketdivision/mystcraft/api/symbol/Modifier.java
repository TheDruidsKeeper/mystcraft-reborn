package com.techbucketdivision.mystcraft.api.symbol;

import org.jspecify.annotations.Nullable;

/**
 * A value passed between symbols while an Age is being constructed, with a "dangling" instability cost that is charged
 * if the modifier is never consumed.
 */
public final class Modifier {
    // Well-known modifier ids
    public static final String ANGLE = "angle";
    public static final String PHASE = "phase";
    public static final String FACTOR = "wavelength";
    public static final String COLOR = "color";
    public static final String GRADIENT = "gradient";
    public static final String SUNSET = "sunset";
    public static final String BLOCKLIST = "blocklist";
    public static final String BIOMELIST = "biomelist";
    /** Creature modifiers (plan §10): spawn rate factor, cap factor, {@code CreatureDifficulty}. */
    public static final String RATE = "rate";
    public static final String CAP = "cap";
    public static final String DIFFICULTY = "difficulty";

    public static final int DEFAULT_DANGLING = 100;

    public static final Modifier EMPTY = new Modifier(null, 0);

    private final @Nullable Object value;
    public final int dangling;

    public Modifier(@Nullable Object value) {
        this(value, DEFAULT_DANGLING);
    }

    public Modifier(@Nullable Object value, int dangling) {
        this.value = value;
        this.dangling = dangling;
    }

    public boolean isEmpty() {
        return value == null;
    }

    public @Nullable Object raw() {
        return value;
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T as(Class<T> type) {
        return type.isInstance(value) ? (T) value : null;
    }

    public float asFloat(float fallback) {
        return value instanceof Number n ? n.floatValue() : fallback;
    }
}
