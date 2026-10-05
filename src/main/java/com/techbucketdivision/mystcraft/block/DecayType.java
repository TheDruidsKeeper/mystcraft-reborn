package com.techbucketdivision.mystcraft.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.material.MapColor;

/** Decay variants (original spec §3.8). Green and yellow exist as blocks but use the black handler. */
public enum DecayType implements StringRepresentable {
    BLACK("black", MapColor.COLOR_BLACK, 0.5f, 2.5f, 12),
    RED("red", MapColor.COLOR_RED, 1.0f, 10f, 1),
    GREEN("green", MapColor.COLOR_GREEN, 0.5f, 2.5f, 12),
    BLUE("blue", MapColor.COLOR_BLUE, 5.0f, 2.0f, 4),
    PURPLE("purple", MapColor.COLOR_PURPLE, 50f, 100f, 3),
    YELLOW("yellow", MapColor.COLOR_YELLOW, 0.5f, 2.5f, 12),
    WHITE("white", MapColor.SNOW, 50f, 100f, 1);

    private final String name;
    private final MapColor mapColor;
    private final float hardness;
    private final float explosionResistance;
    private final int legacyMeta;

    DecayType(String name, MapColor mapColor, float hardness, float explosionResistance, int legacyMeta) {
        this.name = name;
        this.mapColor = mapColor;
        this.hardness = hardness;
        this.explosionResistance = explosionResistance;
        this.legacyMeta = legacyMeta;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public MapColor mapColor() {
        return mapColor;
    }

    public float hardness() {
        return hardness;
    }

    public float explosionResistance() {
        return explosionResistance;
    }

    public static DecayType byName(String name) {
        for (DecayType t : values()) if (t.name.equals(name)) return t;
        return BLACK;
    }
}
