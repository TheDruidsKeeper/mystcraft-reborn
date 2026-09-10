package com.techbucketdivision.mystcraft.api.symbol.logic;

import com.techbucketdivision.mystcraft.util.Colors;
import org.jspecify.annotations.Nullable;

/** Static grass/foliage/water colour. {@code null} means "use the biome's colour". */
public interface StaticColorProvider {
    ColorKind kind();

    @Nullable Colors.RGB getStaticColor();
}
