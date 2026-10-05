package com.tbd.mystcraft.registry;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.facility.FacilityPoolElement;
import com.tbd.mystcraft.util.MystIds;
import com.tbd.mystcraft.world.structure.NearOriginPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structure placement and pool element types plus the datapack keys of the Facility (docs/plans/FACILITY_PLAN.md).
 * The structure, its set, pools and templates are data in the built-in {@code mystcraft_facility} pack; only the
 * placement type and the marker-resolving pool element are code.
 */
public final class ModStructures {
    private ModStructures() {}

    public static final DeferredRegister<StructurePlacementType<?>> PLACEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Mystcraft.MOD_ID);

    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<NearOriginPlacement>> NEAR_ORIGIN =
            PLACEMENT_TYPES.register("near_origin", () -> () -> NearOriginPlacement.CODEC);

    public static final DeferredRegister<StructurePoolElementType<?>> POOL_ELEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, Mystcraft.MOD_ID);

    /** Pool element whose DATA markers become puzzle blocks ({@link FacilityPoolElement}). */
    public static final DeferredHolder<StructurePoolElementType<?>, StructurePoolElementType<FacilityPoolElement>> FACILITY_ELEMENT =
            POOL_ELEMENT_TYPES.register("facility_element", () -> () -> FacilityPoolElement.CODEC);

    public static final ResourceKey<Structure> FACILITY = ResourceKey.create(Registries.STRUCTURE, MystIds.id("facility"));
    public static final ResourceKey<StructureSet> FACILITY_SET = ResourceKey.create(Registries.STRUCTURE_SET, MystIds.id("facility"));
}
