package com.techbucketdivision.mystcraft.symbol.symbols;

import com.techbucketdivision.mystcraft.api.symbol.logic.BiomeController;
import com.techbucketdivision.mystcraft.api.symbol.logic.ChunkFinalizer;
import com.techbucketdivision.mystcraft.api.symbol.logic.EnvironmentalEffect;
import com.techbucketdivision.mystcraft.api.symbol.logic.Populator;
import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainAlteration;
import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainContext;
import com.techbucketdivision.mystcraft.api.symbol.logic.TerrainGenerator;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

/**
 * Lightweight logic instances registered while a symbol is being <em>profiled</em> (original spec §4.1 step 3), so
 * the {@link com.techbucketdivision.mystcraft.symbol.SymbolProfiler} learns which interfaces the symbol provides without
 * the symbol touching registries or constructing world-gen objects. Never used to generate anything.
 */
public final class Markers {
    private Markers() {}

    public static final BiomeController BIOME_CONTROLLER = new BiomeController() {
        @Override
        public Holder<Biome> getBiomeAt(int blockX, int blockZ) {
            throw new IllegalStateException("profiling marker");
        }

        @Override
        public List<Holder<Biome>> possibleBiomes() {
            return List.of();
        }
    };

    public static final TerrainGenerator TERRAIN_GENERATOR = (TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) -> {};

    public static final TerrainAlteration TERRAIN_ALTERATION = (TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) -> {};

    public static final ChunkFinalizer CHUNK_FINALIZER = (TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) -> {};

    public static final Populator POPULATOR = (WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag) -> false;

    public static final EnvironmentalEffect EFFECT = (ServerLevel level, LevelChunk chunk) -> {};

    /** Alteration that is also a finalizer (floating islands). */
    public static final Object ALTERATION_AND_FINALIZER = new AlterationAndFinalizer();

    private static final class AlterationAndFinalizer implements TerrainAlteration, ChunkFinalizer {
        @Override
        public void alterTerrain(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {}

        @Override
        public void finalizeChunk(TerrainContext ctx, ChunkAccess chunk, int chunkX, int chunkZ) {}
    }
}
