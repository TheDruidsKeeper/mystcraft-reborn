package com.techbucketdivision.mystcraft.world.structure;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.facility.FacilityPoolElement;
import com.techbucketdivision.mystcraft.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

/**
 * Where the Facility of a level is, without generating anything: the {@link NearOriginPlacement} of the structure set
 * is deterministic, so the chunk is known from the generator state alone. {@code null} when the level has no Facility
 * (no Vault symbol, or the built-in pack is disabled). Vanilla {@code findNearestMapStructure} only understands the
 * two vanilla placement types, hence this helper.
 */
public final class FacilityLocator {
    /** Where inside a generated Facility {@link #find} should point. */
    public enum Spot { ENTRANCE, LOBBY, VAULT }

    /**
     * The generated Facility start of {@code level}, generating its start chunk if needed; {@code null} when the Age
     * has no facility or the start is invalid.
     */
    public static @Nullable StructureStart start(ServerLevel level) {
        ChunkPos chunk = facilityChunk(level);
        if (chunk == null) {
            Mystcraft.LOGGER.info("[facility] {} has no facility placement", level.dimension().identifier());
            return null;
        }
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(ModStructures.FACILITY);
        if (structure == null) {
            Mystcraft.LOGGER.info("[facility] the facility structure is not registered (pack disabled?)");
            return null;
        }
        var start = level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
        if (start == null || !start.isValid()) {
            Mystcraft.LOGGER.info("[facility] no valid start in chunk {} of {} ({})", chunk, level.dimension().identifier(), start);
            return null;
        }
        return start;
    }

    /** A viewpoint inside or at the facility: where to stand and where to look. */
    public record View(BlockPos pos, float yaw, float pitch) {}

    /**
     * A viewpoint of the facility: outside the entrance looking at it (a few blocks west, on the surface), or inside
     * the first piece from the lobby / vault pool looking along the room. Pieces are matched by template id
     * ({@code facility/<pool>/...}). Logs the piece list when the spot is missing.
     */
    public static @Nullable View find(ServerLevel level, Spot spot) {
        var start = start(level);
        if (start == null) return null;
        if (spot == Spot.ENTRANCE) {
            var box = start.getPieces().getFirst().getBoundingBox();
            BlockPos c = box.getCenter();
            int x = box.minX() - 8;
            level.getChunk(x >> 4, c.getZ() >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, c.getZ());
            return new View(new BlockPos(x, Math.max(y, box.minY()), c.getZ()), -90f, 12f);
        }
        String pool = spot == Spot.LOBBY ? "facility/lobby/" : "facility/vault/";
        for (var piece : start.getPieces()) {
            if (piece instanceof PoolElementStructurePiece p
                    && p.getElement() instanceof FacilityPoolElement e
                    && e.templateId().map(id -> id.getPath().startsWith(pool)).orElse(false)) {
                var box = piece.getBoundingBox();
                return new View(new BlockPos(box.getCenter().getX(), box.minY() + 1, box.getCenter().getZ()), 45f, 8f);
            }
        }
        Mystcraft.LOGGER.info("[facility] no {} piece among {}", spot, start.getPieces().stream()
                .map(piece -> piece instanceof PoolElementStructurePiece p && p.getElement() instanceof FacilityPoolElement e
                        ? e.templateId().map(id -> id.getPath()).orElse("inline") : piece.getClass().getSimpleName()).toList());
        return null;
    }

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
