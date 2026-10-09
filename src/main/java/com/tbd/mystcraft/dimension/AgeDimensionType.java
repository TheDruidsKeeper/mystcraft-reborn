package com.tbd.mystcraft.dimension;

import com.tbd.mystcraft.util.MystIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;

/** Keys for the JSON-defined dimension type used by every Age ({@code data/mystcraft/dimension_type/age.json}). */
public final class AgeDimensionType {
    private AgeDimensionType() {}

    public static final ResourceKey<DimensionType> AGE = ResourceKey.create(Registries.DIMENSION_TYPE, MystIds.id("age"));
}
