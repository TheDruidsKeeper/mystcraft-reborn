package com.tbd.mystcraft.api.symbol.logic;

import com.tbd.mystcraft.util.Colors;
import org.jspecify.annotations.Nullable;

/** Static grass/foliage/water colour. {@code null} means "use the biome's colour". */
public interface StaticColorProvider {
    ColorKind kind();

    Colors.@Nullable RGB getStaticColor();
}
