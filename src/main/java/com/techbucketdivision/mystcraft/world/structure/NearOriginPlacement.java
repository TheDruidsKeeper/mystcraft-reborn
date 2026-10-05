package com.tbd.mystcraft.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tbd.mystcraft.registry.ModStructures;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

/**
 * Structure placement {@code mystcraft:near_origin}: exactly one candidate chunk per level, chosen deterministically
 * from the level seed (for Ages: the Age seed, see {@code AgeChunkGenerator#createState}) in the ring
 * {@code min_chunk_radius..max_chunk_radius} chunks around chunk (0,0). Used by the Facility structure set so every
 * Age that writes the Vault symbol gets one Facility within walking distance of the arrival point
 * (docs/plans/FACILITY_PLAN.md §2.4). {@link #candidate(long)} is the shared math for the spawn search and
 * {@code /myst locate facility}.
 */
public final class NearOriginPlacement extends StructurePlacement {
    public static final MapCodec<NearOriginPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> placementCodec(i)
            .and(i.group(
                    Codec.intRange(0, 64).fieldOf("min_chunk_radius").forGetter(p -> p.minChunkRadius),
                    Codec.intRange(0, 64).fieldOf("max_chunk_radius").forGetter(p -> p.maxChunkRadius)))
            .apply(i, NearOriginPlacement::new));

    private final int minChunkRadius;
    private final int maxChunkRadius;

    public NearOriginPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency,
                               int salt, Optional<ExclusionZone> exclusionZone, int minChunkRadius, int maxChunkRadius) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
        this.minChunkRadius = Math.min(minChunkRadius, maxChunkRadius);
        this.maxChunkRadius = Math.max(minChunkRadius, maxChunkRadius);
    }

    public int minChunkRadius() {
        return minChunkRadius;
    }

    public int maxChunkRadius() {
        return maxChunkRadius;
    }

    /** The single chunk this placement selects for {@code levelSeed}. */
    public ChunkPos candidate(long levelSeed) {
        RandomSource rand = RandomSource.create(levelSeed ^ (salt() * 0x9E3779B97F4A7C15L));
        int distance = minChunkRadius + rand.nextInt(maxChunkRadius - minChunkRadius + 1);
        double angle = rand.nextDouble() * Math.PI * 2;
        int cx = (int) Math.round(Math.cos(angle) * distance);
        int cz = (int) Math.round(Math.sin(angle) * distance);
        // Never chunk (0,0): the Star Fissure (and the Age's origin) live there.
        if (cx == 0 && cz == 0) cx = Math.max(1, minChunkRadius);
        return new ChunkPos(cx, cz);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
        ChunkPos c = candidate(state.getLevelSeed());
        return c.x() == sourceX && c.z() == sourceZ;
    }

    @Override
    public StructurePlacementType<?> type() {
        return ModStructures.NEAR_ORIGIN.get();
    }
}
