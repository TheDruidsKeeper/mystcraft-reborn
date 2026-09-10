# Package B — world generation (`com.techbucketdivision.mystcraft.world.**`)

All classes are server-side only (no client imports). Vanilla calls follow `docs/API_CHEATSHEET.md`; assumptions that
could not be verified are marked `// UNVERIFIED:` in the source (grep for them).

## Pipeline (REQUIREMENTS §5.4 → 26.1 chunk statuses)

| Status | `AgeChunkGenerator` method | What happens |
|---|---|---|
| BIOMES | inherited `createBiomes` | `AgeBiomeSource.getNoiseBiome` → `AgeController.biomeController()` (3D for Native, 2D otherwise) |
| NOISE | `fillFromNoise` | `TerrainGenerator.generateTerrain` → every `TerrainAlteration` (symbol order) → every `ChunkFinalizer` → `Heightmap.primeHeightmaps(WORLD_SURFACE_WG, OCEAN_FLOOR_WG)` |
| SURFACE | `buildSurface` | 1.12-style top/filler replacement keyed by biome (`SurfaceBlocks`), 4-octave `LegacyNoise` depth, gravel/ice/sea rules, sandstone under sand; re-primes heightmaps |
| CARVERS | `applyCarvers` | no-op (caves/ravines are alterations) |
| FEATURES | `applyBiomeDecoration` | `super` (vanilla biome features + structure pieces) → `Populator`s in symbol order with the `flag` contract → `MystcraftLibrary` → 5×5 spawn platform if the Age spawn lies in this chunk |
| SPAWN | `spawnOriginalMobs` | `NaturalSpawner.spawnMobsForChunkGeneration` with a `WorldgenRandom` decoration seed |

Structures: `createStructures`/`createReferences` are inherited. `createState` is overridden and builds the
`ChunkGeneratorStructureState` with `createForFlat(...)` from only the vanilla structure sets enabled by
`VanillaStructurePopulator`s on the Age (`villages`→VILLAGES, `strongholds`→STRONGHOLDS, `mineshafts`→MINESHAFTS,
`nether_fortress`→NETHER_COMPLEXES). Caveat: that factory uses seed-extension 0 for concentric rings, so stronghold ring
angles are identical across Ages (positions still depend on the biome search).

Height mapping: the original 0..255 block space is generated unchanged (sea level 63). y < 0 is filled with the terrain
block by the noise/flat generators (`fillBelowZero`), bedrock at y = −64 (+rand(5) for noise generators). The original
random bedrock at y ≤ rand(5) becomes terrain block so the classic floor shape stays. Alterations never write below y=1
or above y=255. End and Void generators leave y < 0 empty.

Threading: chunk generation runs on worker threads. Every generator/alteration/populator keeps only immutable state
(noise permutation tables) plus per-call locals; caches (`LayeredBiomeController` tiles, noise-field LRU used for
`getBaseHeight`, floating-island column flags) are synchronized/concurrent.

## Public classes

### `world.AgeChunkGenerator extends ChunkGenerator`
* `AgeChunkGenerator(UUID ageId, BiomeSource biomeSource)`; `MapCodec<AgeChunkGenerator> CODEC` (fields `age_id` string uuid, `biome_source`).
* `@Nullable AgeController controller()` — lazily resolved via `ServerLifecycleHooks.getCurrentServer()` + `AgeControllers.server(server, uuid)`; cached per instance; `ensureCurrent()` on each access.
* `getSeaLevel()` = controller sea level (63 fallback), `getMinY()` = −64, `getGenDepth()` = 384.
* `getBaseHeight/getBaseColumn`: exact raw column when the terrain generator implements `world.gen.HeightEstimator` (all built-in ones do; noise fields are cached in a 64-entry LRU), otherwise a biome-height estimate.
* `addDebugScreenInfo` prints age name/seed/sea/ground and the biome.

### `world.AgeBiomeSource extends BiomeSource`
* `AgeBiomeSource(UUID ageId)`; `MapCodec CODEC` (`age_id`); `public static final Codec<UUID> UUID_STRING_CODEC`.
* `getNoiseBiome` → `BiomeController.getNoiseBiome(qx,qy,qz)`; `collectPossibleBiomes` → `possibleBiomes()`; fallback `minecraft:plains` when no server/controller.
* Note: vanilla memoizes `possibleBiomes()` on first call (level creation) — the controller must exist by then (it does: `AgeManager.getOrCreateLevel` runs after the `AgeData` is stored). Building the controller marks the Age visited (`AgeController` behaviour), so restoring levels at server start marks every restored Age visited.

### `world.AgeSpawn`
* `static BlockPos findSpawn(ServerLevel, AgeController)` — returns `AgeData.spawn()` if set; else biome search (forest/plains/taiga/jungle family via `BiomeSource.findBiomeHorizontal`, radius 256) → up to 1000 random positions within ±64 needing a non-bedrock solid top block → raise until air; stores the spawn (`AgeData.setSpawn`) and places the platform.
* `static void placePlatform(LevelAccessor, BlockPos spawn)` — 5×5 cobblestone at spawn−1, 4 blocks above cleared.
* `static boolean isSpawnBiome(Holder<Biome>)`.

### `world.biome`
* `BiomeHeights` — `static float baseHeight(Holder<Biome>)`, `heightVariation(Holder<Biome>)`, `int groundLevel(Holder<Biome>)` (= base·64+64). Table keyed by vanilla biome id (1.12 values), tag fallbacks (`IS_OCEAN`… marked UNVERIFIED), default 0.1/0.2.
* `SurfaceBlocks` — `record Pair(BlockState top, BlockState filler)`, `static Pair of(Holder<Biome>)`, `static boolean isCold(Holder<Biome>)` (temperature ≤ 0.15).
* `SingleBiomeController(Holder<Biome>)`.
* `TiledBiomeController(List<Holder<Biome>>, boolean gridScale)` — `list[((x>>s)+(z>>s)) mod n]`, s = 4 (16-block tiles) or 6 when `gridScale` (64-block "generation-scale" tiles; modern chunks have no separate generation biome array, so this is how the Grid variant differs).
* `NativeBiomeController(long seed, HolderLookup.Provider)` — vanilla overworld `MultiNoiseBiomeSource` preset + `RandomState.create(registries, NoiseGeneratorSettings.OVERWORLD, seed)`; 2D query samples quart y = 16.
* `LayeredBiomeController(long seed, int zoom, List<Holder<Biome>>)` — GenLayer port (`LegacyLayers`: random biome (200) → zoom×2 (1000,1001) → zoom×`zoom` (1000+i) → smooth (1000) → Voronoi (10)); 16×16 tile LRU cache (4096 tiles). The original island/fuzzy-zoom prefix layers only fed a random-biome layer that ignored them and were dropped. Callers pad the list to ≥3 biomes as the original did.

### `world.gen`
* `LegacyNoise(Random, int octaves)` — port of `NoiseGeneratorOctaves`/`NoiseGeneratorImproved`; `double[] generate(out, xOff, yOff, zOff, xSize, ySize, zSize, xScale, yScale, zScale)` (layout `[(x*zSize+z)*ySize+y]`), `generate2d(out, xOff, zOff, xSize, zSize, xScale, zScale)` (layout `[x*zSize+z]`). Thread-safe.
* `ChunkBlocks(ChunkAccess)` — fast section-level `get/set(localX, y, localZ)` for proto chunks (no heightmap updates; the generator primes heightmaps).
* `HeightEstimator` — `BlockState[] sampleColumn(TerrainContext, x, z)`, `int estimateSurface(ctx, x, z, ignoreFluids)`.
* `AbstractLegacyTerrainGen(seed, terrain, sea, genBedrock, fillBelowZero)` — 5×17×5 density field, 4×8×4 interpolation (port of `TerrainGeneratorBase`); subclasses implement `initializeNoiseField`.
* `TerrainNormalGen(long seed, boolean amplified, BlockState terrain, BlockState sea)` — octaves 16/16/8/10/16, 684.412 scale, parabolic 5×5 biome weighting, amplified `h=1+2h, v=1+4v`.
* `TerrainNetherGen(long seed, BlockState terrain, BlockState sea)`, `TerrainEndGen(long seed, BlockState terrain, BlockState sea)` (no bedrock, nothing below 0; caller passes AIR as sea by default), `TerrainFlatGen(long seed, BlockState terrain, BlockState sea)` (bedrock at −64, terrain below `averageGroundLevel`, sea ≤ sea level), `TerrainVoidGen()`.

### `world.feature` (all implement the `api.symbol.logic` interfaces named)
* `AbstractMapGen(seed, @Nullable BlockState, range)` / `AbstractTunnelGen` — ports of `MapGenAdvanced` and the shared cave-node walker (`Shape.CAVE/SPHERE/BLOB`). `placeBlock` never replaces bedrock and never puts a non-solid block into a liquid.
* `CavesAlteration(long seed, int rate, int size, BlockState fill)` — Caves = (seed, 15, 40, AIR); Tendrils = (seed, 15, 18, structure block).
* `RavinesAlteration(long seed)` — 1/50 per chunk; carves the vanilla ravine tube (the original iterated y in [0,minY) — a porting bug — REQUIREMENTS asks for the vanilla shape).
* `SpheresAlteration(long seed, BlockState)` — 5 %/chunk, y = 32+rand(rand(192)+1), scalar 1–5.
* `FloatingIslandsAlteration(long seed, Holder<Biome>, BlockState)` — `TerrainAlteration` **and** `ChunkFinalizer` (register the same instance once; `AgeController.registerInterface` files it in both lists). 1/192 per chunk (range 5); island surface gets the island biome's top/filler (the original compared a state to a block and never did this); finalizer rewrites the touched columns' biomes with `ChunkAccess.fillBiomesFromNoise` + snapshot resolver.
* `HugeTreesAlteration(long seed)` — 50 %/chunk giant oak; leaves are `persistent=true` (UNVERIFIED `LeavesBlock.PERSISTENT`).
* `SkylandsAlteration(long seed)` — removes y ≤ 76+noise(7 octaves) and liquids above; runs as a post-pass instead of a primer filter.
* `DenseOresPopulator()` — `Feature.ORE` + `OreConfiguration(TagMatchTest(STONE_ORE_REPLACEABLES) | BlockMatchTest(NETHERRACK), state, size)`, counts/ranges from §4.3.7, via `ConfiguredFeature.place(level, generator, random, pos)` (generator from `level.getLevel().getChunkSource().getGenerator()`).
* `LakesPopulator(BlockState fluid, boolean deep)` — surface: 1/4, y 0–255, skipped when `flag`; deep: 1/8, y = rand(rand(248)+8), only if y < sea level or 1/10; classic lake blob (`generateLake(level, rand, pos)` public).
* `ObelisksPopulator(BlockState)` — 1/128; `SpikesPopulator(BlockState)` — 1/18 unless `flag`; `CrystalFormationPopulator(BlockState)` — 1/15 unless `flag` (origin kept near chunk centre so the ±13-block lines stay inside the decoration region).
* `StarFissurePopulator()` — only in the spawn chunk (`AgeData.spawn()`, chunk (0,0) if unset); `ModBlocks.STAR_FISSURE` rows at y=0, everything above cleared; returns `true`.
* `DungeonsPopulator()` — 8 × `Feature.MONSTER_ROOM` per chunk at y 0–255.
* `VanillaStructurePopulator(String kind)` — marker; `@Nullable ResourceKey<StructureSet> structureSet()`; `populate` returns false. Kinds: `villages|strongholds|mineshafts|nether_fortress`.

### `world.structure.MystcraftLibrary implements Populator`
* `MystcraftLibrary(long seed)` — created by `AgeChunkGenerator` with the Age seed; `boolean isLibraryChunk(chunkX, chunkZ)` (32-chunk regions, offset rand(24), salt 14357617, `regionX*341873128712 + regionZ*132897987541 + seed + salt`), `void place(WorldGenLevel, RandomSource, BlockPos origin, Rotation)`, `static ItemStack randomLecternPage(RandomSource)` (rank ≥ 3 via `CardRanks.ofRankAtLeast(3)` + `weightedRandom`, `PageItem.createSymbolPage`).
* Layout = Appendix B verbatim (`LAYOUT[y][z]` rows of 11 chars); origin at chunk-local (2, ground, 2) so the 11×11 footprint stays inside the chunk; random rotation about the footprint centre; cobblestone foundation; chest at (4,1,2) with loot table `mystcraft:chests/library` (`RandomizableContainer.setBlockEntityLootTable`); lecterns at (6,2,2),(8,2,4),(8,2,5),(8,2,6),(6,2,8) facing their targets, `HORIZONTAL_FACING` set when the lectern state has it, book set through `blockentity.BookDisplayBlockEntity#setBook(ItemStack)` (UNVERIFIED cross-package method name).

## Cross-package dependencies
* Package A must construct: `TerrainNormalGen/Flat/Nether/End/Void`, the biome controllers (padding biome lists: Single → 1 random, Tiled/Grid → ≥2, Layered → ≥3), `CavesAlteration` (caves & tendrils), `RavinesAlteration`, `SpheresAlteration`, `FloatingIslandsAlteration`, `HugeTreesAlteration`, `SkylandsAlteration`, the populators above, and register them with `AgeDirector.registerInterface`. Terrain symbols should also call `AgeController.setTerrainBlocks(terrain, sea)` so `buildSurface` recognises the terrain block (defaults stone/water).
* Package C1: `item.PageItem.createSymbolPage(AgeSymbol)`.
* Package C2: `blockentity.BookDisplayBlockEntity` with `setBook(ItemStack)`; `ModBlocks.LECTERN` state property `HORIZONTAL_FACING` (optional).
* Package E: `AgeSpawn.findSpawn` for respawn/first link; `AgeSpawn.placePlatform` is also invoked by the generator.
* Package F: loot table `data/mystcraft/loot_table/chests/library.json`.

## Lang keys
None (no user-visible text in this package).
