package com.tbd.mystcraft.facility;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tbd.mystcraft.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code mystcraft:facility_element}: a {@link SinglePoolElement} whose DATA markers are resolved by
 * {@link FacilityMarkers} after the piece is placed. The generated template pools use it for every Facility piece
 * (build.gradle {@code generateStructurePools}); the JSON fields are those of {@code minecraft:single_pool_element}.
 */
public class FacilityPoolElement extends SinglePoolElement {
    public static final MapCodec<FacilityPoolElement> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(templateCodec(), processorsCodec(), projectionCodec(), overrideLiquidSettingsCodec()).apply(i, FacilityPoolElement::new));

    public FacilityPoolElement(Either<Identifier, StructureTemplate> template, Holder<StructureProcessorList> processors,
                               StructureTemplatePool.Projection projection, Optional<LiquidSettings> overrideLiquidSettings) {
        super(template, processors, projection, overrideLiquidSettings);
    }

    @Override
    public boolean place(StructureTemplateManager templates, WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                         BlockPos position, BlockPos referencePos, Rotation rotation, BoundingBox chunkBB, RandomSource random,
                         LiquidSettings liquidSettings, boolean keepJigsaws) {
        StructureTemplate template = this.template.map(templates::getOrCreate, t -> t);
        StructurePlaceSettings settings = getSettings(rotation, chunkBB, liquidSettings, keepJigsaws);
        if (!template.placeInWorld(level, position, referencePos, settings, random, 18)) return false;
        // Absolute marker positions straight from the template: vanilla's processBlockInfos would drop them, because
        // getSettings() adds BlockIgnoreProcessor.STRUCTURE_BLOCK (that is also why a StructureProcessor cannot do this).
        List<FacilityMarkers.Marker> markers = getDataMarkers(templates, position, rotation, true).stream()
                .map(FacilityMarkers.Marker::of).toList();
        if (!markers.isEmpty()) {
            BoundingBox pieceBox = getBoundingBox(templates, position, rotation);
            FacilityMarkers.place(level, markers, position.asLong(), pieceBox, earlierPieces(level, structureManager, pieceBox), chunkBB, random);
        }
        return true;
    }

    /**
     * Bounding boxes of the pieces generated before this one in the same Facility start (parents come first in a
     * jigsaw start): a doorway touching one of them is the way the player came in and gets no door.
     */
    static List<BoundingBox> earlierPieces(WorldGenLevel level, StructureManager structureManager, BoundingBox pieceBox) {
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(ModStructures.FACILITY);
        if (structure == null) return List.of();
        StructureStart start = structureManager.getStructureAt(pieceBox.getCenter(), structure);
        if (!start.isValid()) return List.of();
        List<BoundingBox> out = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (box.equals(pieceBox)) break;
            out.add(box);
        }
        return out;
    }

    /** The template id this element places (empty for an inline template). */
    public Optional<Identifier> templateId() {
        return template.left();
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return ModStructures.FACILITY_ELEMENT.get();
    }

    @Override
    public String toString() {
        return "Facility[" + template.map(Identifier::toString, t -> "[template]") + "]";
    }
}
