# Architecture

Mod id `mystcraft`, root package `com.tbd.mystcraft`, NeoForge 26.1 / Java 25. Read the classes named
here before changing a subsystem; this page only says what each part is for.

## Principles
* **Data where vanilla is data.** Dimension type, loot, recipes, tags, trades, structures and the Facility rooms are
  JSON/NBT under `src/main/resources` (datagen output under `src/generated/resources`). Java is behaviour.
* **One dimension type, many dimensions.** Every Age is a runtime dimension `mystcraft:age_<uuid>` of type
  `mystcraft:age`, created by `dimension/DynamicDimensions` (Infiniverse technique, MIT).
* **Ages are described, then compiled.** `age/AgeData` (persistent) holds the pages; `age/AgeController` compiles
  them on both sides into terrain, biomes, celestials, weather, effects, creatures. Rendering reads the client
  controller, generation the server one.
* **The chunk generator is a shell.** `world/AgeChunkGenerator` delegates each phase to the controller's
  `TerrainGenerator`, `TerrainAlteration`s and `Populator`s; its codec stores only the Age id.
* **Symbols are code in a mod registry** (`symbol/SymbolRegistry`), each in one `SymbolCategory`; ids are
  lower-case `mystcraft:<name>`.
* **Server-authoritative.** Client-only code lives in `client/**` and is referenced only from client entry points.

## How an Age comes to be
```
pages (PageItem, SymbolPage component)            player writes some; the rest is "discovered"
  → DescriptiveBookItem.checkFirstLink            first link binds the book
  → age/AgeBlueprint.fill                         completes every category deterministically from the seed
  → age/AgeData (AgeDataStorage, SavedData)       pages, symbols, seed, time, spawn, per-subsystem data
  → age/AgeController                             runs every symbol's registerLogic(AgeDirector) → logic objects
  → dimension/DynamicDimensions                   level stem + ServerLevel for mystcraft:age_<uuid>
  → world/AgeChunkGenerator, AgeBiomeSource       terrain / biomes / features from the controller
  → linking/LinkController, world/AgeSpawn        arrival point (Facility-relative when present), platform
  → network AgeDataSyncPayload → client/ClientAgeData → client AgeController → sky, tints, weather renderers
```

## Packages
| Package | Owns |
|---|---|
| `Mystcraft`, `MystcraftClient` | entry points: registries, config, payloads, built-in datapack, client setup |
| `registry/` | `DeferredRegister`s only (`ModBlocks`, `ModItems`, `ModBlockEntities`, `ModMenus`, `ModEntities`, `ModStructures`, …) |
| `config/` | `MystcraftConfig` (common), `BalanceConfig` (instability), `WorldBuildingConfig` (blueprint fill), `ClientConfig` |
| `api/symbol/` | `AgeSymbol`, `AgeDirector`, `SymbolCategory`, modifier model, the logic interfaces (`TerrainGenerator`, `Populator`, `WeatherController`, `Celestial`, …) |
| `api/linking/`, `api/instability/`, `api/item/` | `LinkInfo`/`LinkProperty`/`LinkEvent`; instability providers; item behaviour interfaces |
| `symbol/` | registry, remapper, card ranks, `symbols/` (built-ins by family), `modifiers/`, biome/block/fluid symbol tables |
| `age/` | `AgeData`, `AgeBlueprint`, `AgeController(s)`, `AgeManager`, `weather/`, `celestial/` |
| `dimension/` | dynamic dimensions, dimension type keys, `AgeEnvironment` (env attributes), `AgeTicker` (Age clock) |
| `world/` | chunk generator, biome source, `gen/` terrain generators, `feature/` populators and alterations, `structure/` (Library, Facility placement + locator) |
| `instability/` | manager/decks, per-Age controller and score, chunk and baseline profiling, `effects/`, `decay/` |
| `linking/` | travel, listeners/permissions, ink effects, crystal portals, star fissure |
| `knowledge/` | per-player symbol knowledge (attachment + sync) |
| `creature/` | creature groups, spawn scaling, caps, difficulty |
| `item/`, `block/`, `blockentity/`, `menu/`, `entity/`, `villager/` | content |
| `network/` | payload records, registration, client handlers |
| `command/` | `/myst`, `/myst-dev`, the QA base, the QA worlds |
| `client/` | setup, `screen/`, `render/` (sky, clouds, weather, BERs, glyphs), tints, client Age cache, `ClientSelfCheck` |
| `data/` | datagen providers |
| `util/` | `MystIds` and small helpers |

Tests: `src/gametest` (companion mod `mystcraft_tests`), `src/test` (unit, asset integrity), `SelfCheck` (server
smoke), `client/ClientSelfCheck` (client smoke). See [`docs/DEVELOPMENT.md`](DEVELOPMENT.md).

## Conventions
* Java 25 idioms; `org.jspecify.annotations.Nullable`.
* Registry and translation keys: `item|block.mystcraft.<name>`, `symbol.mystcraft.<id>[.desc]`,
  `commands.mystcraft.*`, `gui.mystcraft.*`.
* Block entities persist with `ValueOutput`/`ValueInput`; inventories/tanks are resource handlers; no `ItemStack`
  in static initialisers.
* Log through `Mystcraft.LOGGER` with the `[tag]` markers from [`docs/DEVELOPMENT.md`](DEVELOPMENT.md#log-markers).
* 26.1 API differences from 1.21 are collected in [`docs/API_NOTES.md`](API_NOTES.md); when in doubt read the decompiled source.
