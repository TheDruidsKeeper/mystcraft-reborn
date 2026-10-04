package com.techbucketdivision.mystcraft.world.structure;

import com.techbucketdivision.mystcraft.registry.ModStructures;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

/**
 * Where the Facility of a level is, without generating anything: the {@link NearOriginPlacement} of the structure set
 * is deterministic, so the chunk is known from the generator state alone. {@code null} when the level has no Facility
 * (no Vault symbol, or the built-in pack is disabled). Vanilla {@code findNearestMapStructure} only understands the
 * two vanilla placement types, hence this helper.
 */
public final class FacilityLocator {
    private FacilityLocator() {}

    public static @Nullable ChunkPos facilityChunk(ServerLevel level) {
        Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .get(ModStructures.FACILITY).map(h -> (Holder<Structure>) h).orElse(null);
        if (structure == null) return null;
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
            if (placement instanceof NearOriginPlacement nearOrigin) return nearOrigin.candidate(state.getLevelSeed());
        }
        return null;
    }
}
