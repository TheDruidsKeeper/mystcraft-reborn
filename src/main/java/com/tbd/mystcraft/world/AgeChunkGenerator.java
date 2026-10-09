package com.tbd.mystcraft.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.age.AgeController;
import com.tbd.mystcraft.age.AgeControllers;
import com.tbd.mystcraft.api.symbol.logic.ChunkFinalizer;
import com.tbd.mystcraft.api.symbol.logic.Populator;
import com.tbd.mystcraft.api.symbol.logic.TerrainAlteration;
import com.tbd.mystcraft.api.symbol.logic.TerrainGenerator;
import com.tbd.mystcraft.world.biome.BiomeHeights;
import com.tbd.mystcraft.world.biome.SurfaceBlocks;
import com.tbd.mystcraft.config.MystcraftConfig;
import com.tbd.mystcraft.registry.ModStructures;
import com.tbd.mystcraft.world.feature.VanillaStructurePopulator;
import com.tbd.mystcraft.world.gen.ChunkBlocks;
import com.tbd.mystcraft.world.gen.HeightEstimator;
import com.tbd.mystcraft.world.gen.LegacyNoise;
import com.tbd.mystcraft.world.structure.MystcraftLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.server.level.ServerLevel;
import com.tbd.mystcraft.creature.CreatureRules;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Chunk generator of an Age (ARCHITECTURE §1.4, original spec §5.4). A thin shell: every phase is delegated to the
 * Age's {@link AgeController} (terrain generator → alterations → finalizers in {@link #fillFromNoise}; a simple
 * biome-keyed surface pass in {@link #buildSurface}; vanilla biome features + structures, then the Age's populators and
 * the Mystcraft Library in {@link #applyBiomeDecoration}). The codec stores only the Age id and the biome source.
 *
 * <p>Structures: {@link #createStructures}/{@link #createReferences} are inherited. The structure state built in
 * {@link #createState} only contains the vanilla structure sets enabled by {@link VanillaStructurePopulator}s on the Age
 * (villages, strongholds, mineshafts, nether complexes), so vanilla structure starts/pieces are handled entirely by the
 * inherited machinery.
 */
public final class AgeChunkGenerator extends ChunkGenerator {
    public static final MapCodec<AgeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            AgeBiomeSource.UUID_STRING_CODEC.fieldOf("age_id").forGetter(g -> g.ageId),
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(i, AgeChunkGenerator::new));

    public static final int MIN_Y = AgeController.MIN_Y;
    public static final int GEN_DEPTH = AgeController.HEIGHT;
    private static final int DEFAULT_SEA_LEVEL = 63;

    private final UUID ageId;
    private volatile @Nullable AgeController controller;
    private volatile @Nullable LegacyNoise stoneNoise;
    private volatile @Nullable MystcraftLibrary library;

    public AgeChunkGenerator(UUID ageId, BiomeSource biomeSource) {
        super(biomeSource);
        this.ageId = ageId;
    }

    public UUID ageId() {
        return ageId;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // --- controller access ---------------------------------------------------------------------------------------

    /** The Age controller, resolved lazily from the current server; {@code null} when unavailable (nothing generates). */
    public @Nullable AgeController controller() {
        AgeController c = controller;
        if (c != null) {
            c.ensureCurrent();
            return c;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        c = AgeControllers.server(server, ageId);
        if (c != null) controller = c;
        return c;
    }

    private LegacyNoise stoneNoise(AgeController c) {
        LegacyNoise n = stoneNoise;
        if (n == null) {
            n = new LegacyNoise(new Random(c.seed()), 4);
            stoneNoise = n;
        }
        return n;
    }

    private MystcraftLibrary library(AgeController c) {
        MystcraftLibrary l = library;
        if (l == null) {
            l = new MystcraftLibrary(c.seed());
            library = l;
        }
        return l;
    }

    // --- structures ----------------------------------------------------------------------------------------------

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long legacyLevelSeed) {
        Set<ResourceKey<StructureSet>> enabled = new HashSet<>();
        AgeController c = controller();
        if (c != null) {
            for (Populator p : c.populators()) {
                if (p instanceof VanillaStructurePopulator vsp && vsp.structureSet() != null) {
                    if (vsp.structureSet() == ModStructures.FACILITY_SET && !MystcraftConfig.FACILITY_ENABLED.get()) continue;
                    enabled.add(vsp.structureSet());
                }
            }
        }
        // Explicit-override factory (the "flat" one) so that only the symbol-enabled sets exist in this Age.
        // Caveat: the concentric-ring (stronghold) seed extension is 0 in this factory, so ring angles are shared between Ages.
        // The Age seed (not the shared level seed) drives placement and piece selection, so two Ages never get the
        // same structure layout; NearOriginPlacement / FacilityLocator rely on this being the state's level seed.
        long structureSeed = c != null ? c.seed() : legacyLevelSeed;
        return ChunkGeneratorStructureState.createForFlat(randomState, structureSeed, getBiomeSource(),
                structureSets.listElements().filter(ref -> enabled.contains(ref.key())).map(ref -> (Holder<StructureSet>) ref));
    }

    // --- terrain -------------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        AgeController c = controller();
        if (c == null) {
            Mystcraft.LOGGER.warn("No AgeController for age {}; chunk {} left empty", ageId, chunk.getPos());
            return CompletableFuture.completedFuture(chunk);
        }
        ChunkPos pos = chunk.getPos();
        int chunkX = pos.x();
        int chunkZ = pos.z();
        TerrainGenerator terrain = c.terrainGenerator();
        if (terrain != null) {
            terrain.generateTerrain(c, chunk, chunkX, chunkZ);
        }
        for (TerrainAlteration alteration : c.alterations()) {
            try {
                alteration.alterTerrain(c, chunk, chunkX, chunkZ);
            } catch (RuntimeException e) {
                Mystcraft.LOGGER.error("Terrain alteration {} failed in chunk {} of age {}", alteration.getClass().getSimpleName(), pos, ageId, e);
            }
        }
        for (ChunkFinalizer finalizer : c.finalizers()) {
            try {
                finalizer.finalizeChunk(c, chunk, chunkX, chunkZ);
            } catch (RuntimeException e) {
                Mystcraft.LOGGER.error("Chunk finalizer {} failed in chunk {} of age {}", finalizer.getClass().getSimpleName(), pos, ageId, e);
            }
        }
        Heightmap.primeHeightmaps(chunk, Set.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        AgeController c = controller();
        if (c == null) return;
        ChunkPos pos = chunk.getPos();
        int chunkX = pos.x();
        int chunkZ = pos.z();
        ChunkBlocks blocks = new ChunkBlocks(chunk);
        double[] noise = stoneNoise(c).generate2d(null, chunkX * 16, chunkZ * 16, 16, 16, 0.0625D, 0.0625D);
        Random rand = new Random((chunkX * 0x4f9939f508L + chunkZ * 0x1ef1565bd5L) ^ c.seed());
        int seaLevel = c.seaLevel();
        BlockState terrain = c.terrainBlock();
        BlockState seaBlock = c.seaBlock();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int minY = blocks.minY();
        int maxY = blocks.maxY();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = (chunkX << 4) + x;
                int wz = (chunkZ << 4) + z;
                int surfaceY = Math.max(minY, Math.min(maxY, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)));
                if (chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) <= minY) continue; // empty column (void)
                at.set(wx, surfaceY, wz);
                Holder<Biome> biome = level.getBiome(at);
                SurfaceBlocks.Pair surface = SurfaceBlocks.of(biome);
                boolean cold = SurfaceBlocks.isCold(biome);
                BlockState top = surface.top();
                BlockState filler = surface.filler();
                int depth = (int) (noise[x * 16 + z] / 3.0D + 3.0D + rand.nextDouble() * 0.25D);
                int j = -1;
                for (int y = surfaceY; y >= minY; --y) {
                    BlockState state = blocks.get(x, y, z);
                    if (state.isAir()) {
                        j = -1;
                        continue;
                    }
                    if (state == terrain) {
                        if (j == -1) {
                            if (depth <= 0) {
                                top = air;
                                filler = terrain;
                            } else if (y >= seaLevel - 4 && y <= seaLevel + 1) {
                                top = surface.top();
                                filler = surface.filler();
                            }
                            if (y < seaLevel && top.isAir()) {
                                top = cold ? Blocks.ICE.defaultBlockState() : seaBlock;
                            }
                            j = depth;
                            if (y >= seaLevel - 1) {
                                blocks.set(x, y, z, top);
                            } else if (y < seaLevel - 7 - depth) {
                                top = air;
                                filler = terrain;
                                blocks.set(x, y, z, gravel);
                            } else {
                                blocks.set(x, y, z, filler);
                            }
                        } else if (j > 0) {
                            --j;
                            blocks.set(x, y, z, filler);
                            if (j == 0 && depth > 1) {
                                if (filler.is(Blocks.SAND)) {
                                    j = rand.nextInt(4) + Math.max(0, y - 63);
                                    filler = Blocks.SANDSTONE.defaultBlockState();
                                } else if (filler.is(Blocks.RED_SAND)) {
                                    j = rand.nextInt(4) + Math.max(0, y - 63);
                                    filler = Blocks.RED_SANDSTONE.defaultBlockState();
                                }
                            }
                        }
                    } else if (state.is(surface.top().getBlock()) || state.is(surface.filler().getBlock())) {
                        // Already surfaced by an alteration (floating islands): do not top the terrain below it again.
                        j = 0;
                    }
                }
            }
        }
        // Direct section writes bypass heightmap updates; a few columns may have lost their top block (depth <= 0).
        Heightmap.primeHeightmaps(chunk, Set.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
        // Caves and ravines are terrain alterations (symbols); vanilla carvers are intentionally not applied.
    }

    // --- decoration ----------------------------------------------------------------------------------------------

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        AgeController c = controller();
        if (c == null) return;
        ChunkPos pos = chunk.getPos();
        int chunkX = pos.x();
        int chunkZ = pos.z();
        long seed = c.seed();
        RandomSource rand = RandomSource.create(seed);
        long k = rand.nextLong() / 2L * 2L + 1L;
        long l = rand.nextLong() / 2L * 2L + 1L;
        rand.setSeed((long) chunkX * k + (long) chunkZ * l ^ seed);
        boolean flag = false;
        for (Populator populator : c.populators()) {
            try {
                if (populator.populate(level, rand, chunkX, chunkZ, flag)) flag = true;
            } catch (RuntimeException e) {
                Mystcraft.LOGGER.error("Populator {} failed in chunk {} of age {}", populator.getClass().getSimpleName(), pos, ageId, e);
            }
        }
        try {
            library(c).populate(level, rand, chunkX, chunkZ, flag);
        } catch (RuntimeException e) {
            Mystcraft.LOGGER.error("Library generation failed in chunk {} of age {}", pos, ageId, e);
        }
        BlockPos spawn = c.ageData().spawn();
        if (spawn != null && (spawn.getX() >> 4) == chunkX && (spawn.getZ() >> 4) == chunkZ) {
            AgeSpawn.placePlatform(level, spawn);
        }
    }

    /** Biome spawn lists rescaled by the Age's creature controllers (plan §10); frenzy is read from the live level. */
    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos) {
        WeightedList<MobSpawnSettings.SpawnerData> spawns = super.getMobsAt(biome, structureManager, category, pos);
        AgeController controller = controller();
        if (controller == null || spawns.isEmpty()) return spawns;
        boolean frenzy = false;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerLevel level = server.getLevel(controller.ageData().levelKey());
            frenzy = level != null && CreatureRules.frenzy(level);
        }
        return CreatureRules.scaleSpawns(spawns, controller::creatures, frenzy);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos center = region.getCenter();
        Holder<Biome> biome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
        WorldgenRandom rand = new WorldgenRandom(new LegacyRandomSource(region.getSeed()));
        rand.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biome, center, rand);
    }

    // --- geometry ------------------------------------------------------------------------------------------------

    @Override
    public int getSeaLevel() {
        AgeController c = controller();
        return c == null ? DEFAULT_SEA_LEVEL : c.seaLevel();
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getGenDepth() {
        return GEN_DEPTH;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        AgeController c = controller();
        if (c == null) return DEFAULT_SEA_LEVEL + 1;
        boolean ignoreFluids = type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG;
        if (c.terrainGenerator() instanceof HeightEstimator estimator) {
            return estimator.estimateSurface(c, x, z, ignoreFluids);
        }
        // Cheap fallback: classic biome base height mapped onto the 0..128 column.
        int estimate = Math.round(64F + BiomeHeights.baseHeight(c.biomeAt(x, z)) * 17F);
        if (!ignoreFluids) estimate = Math.max(estimate, c.seaLevel());
        return estimate + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        AgeController c = controller();
        if (c != null && c.terrainGenerator() instanceof HeightEstimator estimator) {
            return new NoiseColumn(c.minY(), estimator.sampleColumn(c, x, z));
        }
        int height = getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
        int minY = getMinY();
        BlockState[] column = new BlockState[Math.max(0, height - minY)];
        BlockState terrain = c == null ? Blocks.STONE.defaultBlockState() : c.terrainBlock();
        java.util.Arrays.fill(column, terrain);
        return new NoiseColumn(minY, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
        AgeController c = controller();
        if (c == null) {
            result.add("Age: " + ageId + " (no controller)");
            return;
        }
        result.add("Age: " + c.ageData().name() + " seed " + c.seed() + " sea " + c.seaLevel() + " ground " + c.averageGroundLevel());
        result.add("Age biome: " + c.biomeAt(feetPos.getX(), feetPos.getZ()).getRegisteredName());
    }
}
