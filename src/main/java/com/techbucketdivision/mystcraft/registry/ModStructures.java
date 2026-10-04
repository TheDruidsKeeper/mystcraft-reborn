package com.techbucketdivision.mystcraft.registry;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.util.MystIds;
import com.techbucketdivision.mystcraft.world.structure.NearOriginPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structure placement types and the datapack keys of the Facility (docs/impl/FACILITY_PLAN.md). The structure, its
 * set, pools and templates are data in the built-in {@code mystcraft_facility} pack; only the placement type is code.
 */
public final class ModStructures {
    private ModStructures() {}

    public static final DeferredRegister<StructurePlacementType<?>> PLACEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Mystcraft.MOD_ID);

    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<NearOriginPlacement>> NEAR_ORIGIN =
            PLACEMENT_TYPES.register("near_origin", () -> () -> NearOriginPlacement.CODEC);

    public static final ResourceKey<Structure> FACILITY = ResourceKey.create(Registries.STRUCTURE, MystIds.id("facility"));
    public static final ResourceKey<StructureSet> FACILITY_SET = ResourceKey.create(Registries.STRUCTURE_SET, MystIds.id("facility"));
}
