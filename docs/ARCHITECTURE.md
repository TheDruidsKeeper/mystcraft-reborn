# Mystcraft Reborn — Architecture

Target: **NeoForge 26.1.2.104 / Minecraft 26.1 / Java 25**. Mod id `mystcraft`, root package `com.techbucketdivision.mystcraft`.
Companion documents: `REQUIREMENTS.md` (what the original did, with numbers), `TOOLCHAIN.md` (build/versions), `API_CHEATSHEET.md` (verified 26.1 signatures — **consult before writing any vanilla/NeoForge call**).

## 1. Design principles

1. **Data-driven where vanilla is data-driven.** Dimension type, biomes, recipes, loot tables, tags, villager trades, structures, timelines and sounds are JSON under `src/main/resources`. Java is used for behaviour only.
2. **One dimension type, many dimensions.** Every Age is a runtime-created dimension `mystcraft:age_<uuid>` using the static dimension type `mystcraft:age`. Dynamic dimension creation follows the Infiniverse technique (vendored, MIT) in `dimension/DynamicDimensions`.
3. **Ages are described, then compiled.** `AgeData` (persistent, per age) holds the ordered symbol list. `AgeController` compiles it into runtime logic (terrain, biomes, celestials, weather, effects) on both server and client (the client receives `AgeData` via `AgeDataSyncPayload`). Rendering reads the client-side `AgeController`; generation reads the server-side one.
4. **The chunk generator is a thin shell.** `AgeChunkGenerator` looks up the `AgeController` for its level and delegates every phase to the controller's registered `TerrainGenerator`, `TerrainAlteration`s, `Populator`s. The generator's codec stores only the age id.
5. **Symbols are code objects registered in a mod-owned registry** (`SymbolRegistry`), not a vanilla registry, so add-ons can register any time before `FMLCommonSetupEvent` completes. Identifiers are lower-case `mystcraft:<name>` (the original mixed-case ids are remapped, see `symbol/SymbolRemapper`).
6. **Networking is payload-based** (`CustomPacketPayload`); GUIs use one generic `MenuMessagePayload` carrying a `CompoundTag`, mirroring the original's `MPacketGuiMessage`.
7. **Everything server-authoritative** lives in `common` packages; client-only classes are in `client/**` and are only referenced from `MystcraftClient` (`@Mod(dist = Dist.CLIENT)`) or `@EventBusSubscriber(value = Dist.CLIENT)` classes.

## 2. Package layout (`com.techbucketdivision.mystcraft`)

| Package | Responsibility |
|---|---|
| `Mystcraft` | mod entry, registers DeferredRegisters, config, payloads. `MystcraftClient` client entry. |
| `config/` | `MystcraftConfig` (COMMON), `ClientConfig`, `BalanceConfig` (instability difficulty, baselines). ModConfigSpec. |
| `registry/` | `ModBlocks, ModItems, ModBlockEntities, ModMenus, ModEntities, ModSounds, ModDataComponents, ModCreativeTabs, ModAttachments, ModFluids, ModChunkGenerators, ModBiomeSources, ModTriggers, ModParticles, ModCriteria` — all `DeferredRegister`s, no logic. |
| `api/symbol/` | `AgeSymbol` (abstract base), `AgeDirector` (construction interface), `Modifier`, `ModifierUtils`, `BlockCategory`, `BlockDescriptor`, `ColorGradient`, `WordData`/`Poem`. Logic interfaces: `BiomeController`, `TerrainGenerator`, `TerrainAlteration`, `Populator`, `ChunkFinalizer`, `LightingController`, `WeatherController`, `Celestial`, `DynamicColorProvider`, `StaticColorProvider`, `EnvironmentalEffect`, `SpawnModifier`. |
| `api/linking/` | `LinkInfo` (record + codec; replaces `LinkOptions` NBT), `LinkProperty` (flags), `LinkEvent.*` (NeoForge events), `LinkPanelEffect` (client extension point). |
| `api/instability/` | `InstabilityProvider`, `InstabilityBonusProvider`, `DecayHandler`. |
| `api/item/` | `PageProvider`, `PageAcceptor`, `OrderablePageProvider`, `PageCollection`, `WritableItem`, `RenameableItem`, `PortalActivator`, `OnLoadableItem` — item behaviour interfaces implemented by the items. |
| `symbol/` | `SymbolRegistry`, `SymbolProfiler`, `SymbolRemapper`, `CardRanks`, `grammar/` (`Grammar`, `Rule`, `GrammarTree`, `GrammarRules`), `symbols/` (every built-in symbol class), `modifiers/` (direction/phase/length/colour/gradient/block/biome modifier symbols), `BlockSymbols` (built-in block symbol table), `BiomeSymbols`, `FluidSymbols`. |
| `age/` | `AgeData` (record-like mutable state + CODEC), `AgeDataStorage` (server-global `SavedData` `mystcraft/ages`), `AgeController`, `AgeControllers` (cache per level key, both sides), `AgeManager` (create / recycle / mark dead / lookup), `weather/` (`CyclingWeather`, `ToggleableWeather`, `WeatherStorage`), `celestial/` (sun/moon/star maths, `CelestialAngles`). |
| `dimension/` | `DynamicDimensions` (create/unregister levels; vendored Infiniverse logic), `AgeDimensionType` (keys), `AgeLevelStem` factory, `AgeEnvironment` (per-level env-attribute layers on the server), `AgeSpawn` (spawn search), `UpdateDimensionsPayload`. |
| `world/` | `AgeChunkGenerator`, `AgeBiomeSource`, `gen/` (`TerrainNormalGen`, `TerrainFlatGen`, `TerrainNetherGen`, `TerrainEndGen`, `TerrainVoidGen`, `NoiseHelper`), `feature/` (spheres, spikes, obelisks, crystal formations, lakes, tendrils, caves, ravines, floating islands, huge trees, star fissure, dense ores), `biome/` (`BiomeWrapper` helpers, biome controller implementations), `structure/` (Mystcraft Library, Archivist house piece). |
| `instability/` | `InstabilityManager` (providers + decks), `InstabilityController` (per age; score, quantisation, deck walk), `ChunkProfiler` + `ChunkProfileData` (SavedData), `BaselineProfiler`, `InstabilityBonusManager`, `effects/` (`DecayEffect`, `CrumbleEffect`, `ExplosionsEffect`, `LightningEffect`, `MeteorEffect`, `ScorchedEffect`, `ExtraTicksEffect`, potion providers), `decay/` (`BlackDecay`, `RedDecay`, `BlueDecay`, `PurpleDecay`, `WhiteDecay`). |
| `linking/` | `LinkController` (travelEntity/teleport), `LinkListeners` (basic/permissions/effects), `LinkPermissions` (SavedData), `InkEffects` (property probabilities), `PortalUtils` (crystal portal flood-fill/tension/pathing), `StarFissureLinker`. |
| `item/` | `PageItem`, `LinkingItem` (abstract), `DescriptiveBookItem`, `LinkingBookItem`, `UnlinkedBookItem`, `BoosterItem`, `FolderItem`, `PortfolioItem`, `InkVialItem`, `WritingDeskItem`, `DecayBlockItem`. |
| `block/` | `InkMixerBlock`, `BookBinderBlock`, `BookReceptacleBlock`, `BookstandBlock`, `LecternBlock`, `DecayBlock`, `LinkModifierBlock`, `CrystalBlock`, `LinkPortalBlock`, `WritingDeskBlock`, `StarFissureBlock`, `InkFluidBlock`. |
| `blockentity/` | `InkMixerBlockEntity`, `BookBinderBlockEntity`, `BookReceptacleBlockEntity`, `BookDisplayBlockEntity` (stand+lectern), `LinkModifierBlockEntity`, `WritingDeskBlockEntity`, `StarFissureBlockEntity`. |
| `menu/` | `AbstractMystcraftMenu` (implements `MenuMessageHandler`), `WritingDeskMenu`, `BookBinderMenu`, `InkMixerMenu`, `LinkModifierMenu`, `BookMenu`, `FolderMenu`, `ArchivistShopMenu`, `slot/` helpers. |
| `entity/` | `LinkbookEntity`, `MystFallingBlockEntity`, `MeteorEntity`, `ColoredLightningBolt`, `explosion/AdvancedExplosion`. |
| `villager/` | `Archivist` (profession + POI), `ArchivistShop` (inventory attachment + restock), village structure hooks. |
| `network/` | payload records + `Payloads` registration + `ClientPayloadHandlers`. |
| `command/` | `MystcraftCommands` (all `/myst-*`, `/tpx`). |
| `event/` | `CommonEvents` (game-bus handlers: login/dimension change ejection, link advancements, ink block bucket cancel, PvP), `ModBusEvents` (capabilities, attributes, spawn placements). |
| `data/` | datagen providers (`ModDataGen` entry; models, lang, tags, loot, recipes, datapack registries). Output committed to `src/generated/resources`. |
| `client/` | `ClientSetup`, `screen/` (all screens + `gui/` elements), `render/` (BERs, entity renderers, `AgeSkyRenderer`, `AgeCloudRenderer`, `AgeWeatherRenderer`, `SymbolGlyphs` (draws words from `symbolcomponents.png`), `PageTextures`), `tint/`, `ClientAgeData` (client cache + controllers). |
| `util/` | `MystIds` (`id("x")` helper), `NbtUtil`, `Colors`, `MathUtil`, `Cuboid`. |

## 3. Core contracts (implemented in this repo — read the actual sources)

### 3.1 `AgeSymbol` and `AgeDirector`
```java
public abstract class AgeSymbol {
    protected AgeSymbol(Identifier id, Integer cardRank, String... poemWords) // poemWords.length == 4
    public Identifier id(); public @Nullable Integer cardRank(); public List<String> poem();
    public String descriptionId();                   // "symbol.mystcraft.<path>" ; override for computed names
    public Component displayName();                  // Component.translatable(descriptionId()) by default
    public abstract void registerLogic(AgeDirector director, long seed);
    public int instabilityModifier(int count) { return 0; }
    public boolean generatesConfigOption() { return true; }
}
```
`AgeDirector` (see `api/symbol/AgeDirector.java`) exposes: `registerInterface(Object logic)` (dispatches on the logic interfaces), `setModifier(String, Object)`, `popModifier(String)`, `clearModifiers()`, `pushBlock(BlockDescriptor)`, `popBlockMatching(BlockCategory...)`, `pushBiome(Holder<Biome>)`, `popBiome()`, `getAllBiomes()`, `addInstability(int)`, `setCloudHeight/setHorizon/setAverageGroundLevel/setSeaLevel/setDrawHorizon/setDrawVoid/setPvPEnabled`, `getSeed()`, `getAgeData()`. Modifier ids are the constants in `Modifier` (`ANGLE, PHASE, FACTOR ("wavelength"), COLOR, GRADIENT, SUNSET, BLOCKLIST, BIOMELIST`).

### 3.2 `AgeData`
Mutable, per-age, keyed by `UUID`. Fields mirror REQUIREMENTS §5.2 (name, seed, uuid, baseInstability, instabilityEnabled, visited, dead, worldTime, spawn, pages (List<ItemStack>), symbols (List<Identifier>), authors, dataCompound (per-subsystem `CompoundTag`)). Server-side storage: `AgeDataStorage` (SavedData on `MinecraftServer#getDataStorage()`), one file `data/mystcraft/ages.dat` with a `Map<UUID, AgeData>`. Level key convention: `ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("mystcraft", "age_" + uuid.toString().replace('-', '_')))`. `AgeData.levelKey()` returns it.

### 3.3 Age lifecycle
`AgeManager.createAge(server)` → new `AgeData` (recycles a dead age if any) → stored. First link (`DescriptiveBookItem.checkFirstLink`) sets pages/name/seed, expands via `GrammarTree`, stores symbols. `AgeManager.getOrCreateLevel(server, ageData)` → `DynamicDimensions.getOrCreateLevel(server, key, () -> new LevelStem(ageTypeHolder, new AgeChunkGenerator(uuid, biomeSource), OptionalLong.of(seed)))`. On `ServerStartedEvent`, every non-dead age is re-created (no-op if vanilla already reconstituted it). `AgeControllers.get(level)` lazily builds the controller from `AgeData` (server: from storage; client: from the synced cache).

### 3.4 Link flow
`LinkInfo` record: `Optional<ResourceKey<Level>> dimension, Optional<UUID> targetUuid, Optional<BlockPos> spawn, float yaw, String displayName, Set<LinkProperty> flags, Map<String,String> props`. Stored on books in data component `ModDataComponents.LINK_INFO`. `LinkController.travelEntity(Entity, LinkInfo)` implements REQUIREMENTS §7.3 with NeoForge events `LinkEvent.Allow/Alter/Start/End/Failed`.

### 3.5 Menus & GUI messages
`MenuMessagePayload(int containerId, CompoundTag data)` bidirectional. Server: `AbstractMystcraftMenu.processMessage(Player, CompoundTag)`. Client: same method on the client menu (prediction) then send. Message names are the original ones (REQUIREMENTS §8), e.g. `"WriteSymbol"`, `"Link"`, `"SetTitle"`.

### 3.6 Client rendering of Ages
- Dimension type `mystcraft:age` (JSON) sets `neoforge:custom_skybox = mystcraft:age`, `neoforge:custom_clouds = mystcraft:age`, `neoforge:custom_weather_effects = mystcraft:age`; renderers registered via `RegisterCustomEnvironmentEffectRendererEvent`.
- **Phase 1 (this milestone):** `ExtractLevelRenderStateEvent` writes `skyColor`, `sunAngle`, `moonAngle`, `starAngle`, `starBrightness`, `moonPhase`, `sunriseAndSunsetColor`, `cloudColor`, `cloudHeight` from the client `AgeController` and the custom skybox renderer returns `false` (vanilla draws). Fog colour via `ViewportEvent.ComputeFogColor`.
- **Phase 2:** full custom sky (multiple suns/moons, rainbow, end-sky starfield) in `AgeSkyRenderer`.
- Grass/foliage/water colours: `RegisterColorHandlersEvent.BlockTintSources` wrappers over vanilla blocks (Phase 2).

### 3.7 Time & weather
- Age time = `AgeData.worldTime`, ticked by the server in `LevelTickEvent.Post` for age levels and synced (with the whole `AgeData`) every 200 ticks or on change. Celestials use age time, not the vanilla clock.
- Weather is per age (`WeatherController`) stored in `AgeData.dataCompound("weather")`; rain/thunder strength is pushed to `Level#setRainLevel/setThunderLevel` on both sides each tick for age levels; precipitation rendering uses the custom weather renderer. Vanilla's server-global `WeatherData` is not used for ages.

## 4. Coding conventions

- Java 25: records, sealed interfaces, pattern-matching switch. `org.jspecify.annotations.Nullable` for nullability.
- Registry names lower-case snake_case: `descriptive_book`, `linking_book`, `unlinked_book`, `page`, `sealed_notebook`, `collation_folder`, `symbol_portfolio`, `ink_vial`, `writing_desk`, `writing_desk_backboard`, `ink_mixer`, `book_binder`, `book_receptacle`, `bookstand`, `lectern`, `link_modifier`, `crystal`, `link_portal`, `star_fissure`, `decay_black|red|green|blue|purple|yellow|white`, `black_ink` (fluid), `black_ink_bucket`.
- Translation keys: `item.mystcraft.<name>`, `block.mystcraft.<name>`, `symbol.mystcraft.<path>`, `link_property.mystcraft.<name>`, `gui.mystcraft.*`, `commands.mystcraft.*`, `instability.mystcraft.bonus.*`.
- Never create `ItemStack`s in static initialisers (26.1 requires bound registries) — use suppliers.
- Never call `Level#getDayTime`, `ServerLevel#setDayTime`, `setWeatherParameters`, `Player#displayClientMessage`, `Item#canAttackBlock`, `ItemStackHandler` (deprecated) — see `API_CHEATSHEET.md` §0.
- Block entities save with `ValueOutput`/`ValueInput`; inventories are `ItemStacksResourceHandler`; fluid tanks are `FluidStacksResourceHandler`.
- All client classes must compile without being loaded on a dedicated server: keep them in `client/**` and reference only from client entry points.
- Log with `Mystcraft.LOGGER` (slf4j).

## 5. Milestones

| M | Scope | Status |
|---|---|---|
| M0 | Build scaffold, Docker, CI, docs | done |
| M1 | Registries, items, blocks, block entities, menus/screens, data components, lang/models/assets, recipes, loot | in progress |
| M2 | Symbols (all), grammar, AgeData/AgeController, dynamic dimensions, chunk generator, terrain gens, biome controllers, features, celestial/weather maths, phase-1 sky | in progress |
| M3 | Linking (books, portals, star fissure, permissions), instability (profiling, decks, effects, decay), entities (book, meteor, falling block, lightning), commands, villager/library structures | in progress |
| M4 | Phase-2 rendering (custom sky with multiple celestials, rainbows, page glyph item models, block tints), notebook/desk UI polish, gametests | planned |
| M5 | Baseline profiling, add-on API surface (events, IMC-equivalents), translations, CurseForge/Modrinth publishing | planned |
