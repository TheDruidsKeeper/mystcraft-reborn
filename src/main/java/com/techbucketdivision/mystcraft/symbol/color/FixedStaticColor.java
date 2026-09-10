package com.techbucketdivision.mystcraft.symbol.color;

import com.techbucketdivision.mystcraft.api.symbol.logic.ColorKind;
import com.techbucketdivision.mystcraft.api.symbol.logic.StaticColorProvider;
import com.techbucketdivision.mystcraft.util.Colors;
import org.jspecify.annotations.Nullable;

/**
 * Static grass / foliage / water colour (REQUIREMENTS §4.3.1). A {@code null} colour means "use the biome's own
 * colour" — this is also how the *Natural* variants (ColorGrassNat, ColorFoliageNat, ColorWaterNat) are expressed: they
 * register a provider so the Age counts as having one, and defer to the biome tint at render time.
 */
public final class FixedStaticColor implements StaticColorProvider {
    private final ColorKind kind;
    private final @Nullable Colors.RGB color;

    public FixedStaticColor(ColorKind kind, @Nullable Colors.RGB color) {
        if (kind.isDynamic()) throw new IllegalArgumentException("Not a static colour kind: " + kind);
        this.kind = kind;
        this.color = color;
    }

    /** Provider that defers to the biome colour. */
    public static FixedStaticColor natural(ColorKind kind) {
        return new FixedStaticColor(kind, null);
    }

    @Override
    public ColorKind kind() {
        return kind;
    }

    @Override
    public @Nullable Colors.RGB getStaticColor() {
        return color;
    }

    public boolean isNatural() {
        return color == null;
    }
}
