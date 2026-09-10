package com.techbucketdivision.mystcraft.api.symbol.logic;

import com.techbucketdivision.mystcraft.util.Colors;

/** Time-dependent sky/fog/cloud colour. Multiple providers of the same kind are averaged. */
public interface DynamicColorProvider {
    ColorKind kind();

    /**
     * @param time           age time in ticks
     * @param celestialAngle current celestial angle 0..1 (from the suns)
     * @param biomeTemp      biome temperature at the camera (for vanilla-like sky colours)
     */
    Colors.RGB getColor(long time, float partialTick, float celestialAngle, float biomeTemp);
}
