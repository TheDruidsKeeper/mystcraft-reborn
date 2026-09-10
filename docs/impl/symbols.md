# Package A — symbols, grammar, age logic (implementation notes)

Root package `com.techbucketdivision.mystcraft`. Everything here is server-safe (no client imports).

## 1. Classes

### `symbol.grammar`
| Class | Key members |
|---|---|
| `Rule` | `Rule(String parent, List<String> values, @Nullable Integer rank)`; `parent()`, `values()`, `rank()`, `size()`, `weight()` (0 for null rank). Tokens are strings: non-terminals keep the original names (`Age`, `TerrainGen`, `BlockTerrain`…), terminals are `symbol.id().toString()`. |
| `Grammar` | `static void bootstrap()` (core rules + pending symbol rules, then builds tables); `registerRule(String parent, @Nullable Integer rank, String... tokens)`; `registerRule(Rule)`; `addSymbolRule(AgeSymbol, String parent, @Nullable Integer rank, String... precedingTokens)` (rule becomes active only once the symbol is in `SymbolRegistry`; may be called any time — tables rebuild lazily); `token(AgeSymbol)`; `parentRules(token)`, `rules(token)` (null = terminal), `randomRule(token, RandomSource)`, `explore(token, RandomSource)`, `shortestPaths(from, to)`; `symbolsExpandingToken(token) -> List<AgeSymbol>`; `tokensProducingToken(token)`; `allTokens()`; `generateFromToken(token, RandomSource)`, `generateFromToken(token, RandomSource, List<Identifier> written)`, `expandAge(List<Identifier> written, RandomSource)`; weighted helpers `totalWeight`, `pickWeighted`, `pickEvenly`. |
| `GrammarRules` | Token constants (`AGE`, `TERRAIN`, `BIOME_CONTROLLER`, `WEATHER`, `LIGHTING`, `VISUAL`, `FEATURE_SMALL/MEDIUM/LARGE`, `EFFECT`, `SUN`, `MOON`, `STARFIELD`, `DOODAD`, `BIOME`, `BIOME_LIST`, `BLOCK_*`, `BLOCK_NONSOLID`, `SUNSET`, `SUNSET_UNCOMMON`, `SUNSET_EXT`, `ANGLE/PERIOD/PHASE/COLOR/GRADIENT` (+`_BASIC`, `_EXT`, `_ADV`), `*_0` sequence tokens, `FEATURE_*_EXT`) and `registerCore()` (REQUIREMENTS §4.4.2 verbatim). |
| `GrammarTree` | `GrammarTree(String rootToken)`; `parseTerminals(List<String>, RandomSource)`; `getExpanded(RandomSource) -> List<String>`; `describe()` debug dump. Faithful port of the original algorithm (§4.4.3). |
| `CreativeCollections` | `portfolios() -> List<ItemStack>` ("Spawned (<name>)" portfolios, §8.7; uses `PortfolioItem.create`, `PageItem.createSymbolPage`); `symbolsFor(List<String> tokens)`. |

### `symbol`
| Class | Key members |
|---|---|
| `BlockSymbols` | `registerAll()` (built-in table §4.3.13); `createBlockSymbol(BlockState, String thirdWord, @Nullable Integer rank, Map<BlockCategory,Integer> categoryRanks)`; `register(AgeSymbol)` (registry + one `Block<Cat> -> symbol` rule per category, works before and after freeze); `idFor(BlockState)` = `mystcraft:block_<path>[_<non-default property values>]` (`block_<ns>_<path>` for third-party namespaces); `blockName(BlockState)`; `ranks(cat, rank, ...)`. Nested `BlockSymbols.BlockSymbol` (`descriptor()`, `state()`, `categoryRanks()`). |
| `BiomeSymbols` | `registerAll(HolderLookup.Provider)` (one symbol per biome via `SymbolRegistry.registerLate`, rank 2 / null for End & void biomes, rule `Biome -> symbol` rank 1 / null, trade price 1 emerald; also calls `FluidSymbols.registerAll()`); `selectableBiomes(HolderLookup.Provider)` (lazy registration; returns holders of the caller's registries); `idFor(ResourceKey<Biome>)` = `mystcraft:biome_<ns>_<path>`; `isEndOrVoid(key)`; `symbolFor(key)`. Nested `BiomeSymbol` (`biomeKey()`, `holder()`); `registerLogic` pushes the biome and `setAverageGroundLevel((int)(BiomeHeights.baseHeight(b)*64+64))`. |
| `FluidSymbols` | `registerAll()` (idempotent; every source fluid of `BuiltInRegistries.FLUID` except empty/water/lava/flowing; block symbol with word Sea, card 4, grammar 4, categories FLUID + SEA (GAS when lighter than air; black ink = card 1 / grammar 0 / sea-banned)); `blacklist(Identifier)`, `banAsSea(Identifier)`. |
| `SymbolRemapper` | `hasRemapping(Identifier)`, `remap(Identifier) -> List<Identifier>`, `remap(String rawLegacyId)`, `legacyToModern(String) -> Identifier`, `snakeCase(String)`. Table = REQUIREMENTS §19.4 + every legacy id of §4.3 → modern id, `ModMat_*` block ids (with 1.12 meta → flattened block names), `Biome<n>` numeric ids, a few legacy biome names. Resolution is recursive. |

### `symbol.symbols` (one class or nested class per symbol)
`SimpleSymbol` (base: id `mystcraft:<path>`), `DummySymbol(path, rank, instability, words…)`, `Markers` (profiling-time logic instances), `BuiltinSymbols.registerAll()` (+ helpers `add(AgeSymbol)`, `rule(AgeSymbol, rank, parent, preceding…)`), `ColorSymbols.*`, `CelestialSymbols.*`, `LightingSymbols.*`, `WeatherSymbols.*`, `BiomeControllerSymbols.*`, `TerrainSymbols.*`, `FeatureSymbols.*`, `EffectSymbols.*`, `MiscSymbols.*`.

### `symbol.modifiers`
`ModifierSymbols.registerAll()`; nested `AngleSymbol`, `PhaseSymbol`, `LengthSymbol`, `GradientSymbol`, `HorizonColorSymbol`, `ColorSymbol`; `COLORS` table (`ColorEntry(display, rgb)`), `colorIds()`, `englishNames()`.

### `symbol.color`
`GradientDynamicColor(kind, gradient, Daylight, avoidPureBlack)` + `sky/nightSky/fog/cloud(gradient)`, `daylightFactor(angle)`; `NaturalDynamicColor(kind)` + `sky()/fog()/cloud()`, `skyColor(angle, temp)`, `fogColor(angle)`; `FixedStaticColor(kind, @Nullable RGB)` + `natural(kind)` (null colour = use the biome tint).

### `age.celestial`
`CelestialMath` (`easedAngle`, `timeToDawn`, `sunriseSunsetAlpha`, `defaultHorizonColor`); `SunCelestial(seed, @Nullable Float periodFactor, @Nullable Float angle, @Nullable Float phase, @Nullable ColorGradient sunset)` (size 30, provides light); `MoonCelestial(same)` (size 20, `phase(time)`, horizon only with a sunset gradient, `HORIZON_ALPHA` 0.3); `StarfieldCelestial(seed, periodFactor, angle, gradient)` (`STAR_SEED` 10842, `STAR_COUNT` 1500, `color(time)`); `TwinkleStarfieldCelestial` (`LAYERS` 10, `STARS_PER_LAYER` 100, `layerSeed(i)`, `layerBrightness(i, time, partial)`); `EndSkyCelestial(gradient)` (`DEFAULT_COLOR`); `RainbowCelestial(seed, @Nullable Float angle)`.

### `age.lighting`
`NormalLighting` (`vanillaBrightness(int)`), `BrightLighting`, `DarkLighting` — implement `LightingController`.

### `age.weather`
`WeatherStorageKeys` (`raining`, `thundering`, `rain_counter`, `thunder_counter`, `disabled`, `reset_counter`, `RESET_COOLDOWN` 12000); `CyclingWeather(8 ints)` + `normal()/fast()/slow()`, `reset()`; `ToggleableWeather(Settings)` + `off()/on()/cloudy()/rain()/snow()/storm()`, `Settings` record, `isDisabled()`, `reset()`. Lightning: `AbstractWeather.strike(ServerLevel, BlockPos)` (`EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED)` + `snapTo(Vec3.atBottomCenterOf(pos))`). `consumeDirty()` is true only after a rain/thunder state flip or toggle (counters do not mark dirty).

## 2. Profiling behaviour
`registerLogic` first pops its modifiers (so the profiler records consumption), then — when `director.isProfiling()` — registers a `Markers.*` instance instead of constructing world-gen / biome objects. Cheap logic (lighting, weather, colours, celestials, void terrain, dense ores, dungeons, effects) is constructed for real.

## 3. Symbol ids and display names (`symbol.mystcraft.<id>`)
| id | name | id | name |
|---|---|---|---|
| color_cloud | Cloud Color | color_cloud_natural | Natural Cloud Color |
| color_fog | Fog Color | color_fog_natural | Natural Fog Color |
| color_foliage | Foliage Color | color_foliage_natural | Natural Foliage Color |
| color_grass | Grass Color | color_grass_natural | Natural Grass Color |
| color_sky | Sky Color | color_sky_natural | Natural Sky Color |
| color_sky_night | Night Sky Color | color_water | Water Color |
| color_water_natural | Natural Water Color | no_horizon | Boundless Sky |
| rainbow | Rainbow | sun_normal | Normal Sun |
| sun_dark | Dark Sun | moon_normal | Normal Moon |
| moon_dark | Dark Moon | stars_normal | Normal Stars |
| stars_twinkle | Twinkling Stars | stars_end_sky | Ender Starfield |
| stars_dark | Dark Stars | lighting_normal | Normal Lighting |
| lighting_bright | Bright Lighting | lighting_dark | Dark Lighting |
| weather_normal | Normal Weather | weather_fast | Fast Weather |
| weather_slow | Slow Weather | weather_off | No Weather |
| weather_on | Eternal Weather | weather_cloudy | Overcast |
| weather_rain | Eternal Rain | weather_snow | Eternal Snow |
| weather_storm | Eternal Storm | biome_native | Native Biome Distribution |
| biome_single | Single | biome_tiled | Tiled |
| biome_grid | Grid-form | biome_tiny | Tiny |
| biome_small | Small | biome_medium | Medium |
| biome_large | Large | biome_huge | Huge |
| terrain_normal | Standard World | terrain_amplified | Amplified Normal World |
| terrain_flat | Flat World | terrain_nether | Cave World |
| terrain_end | Island World | terrain_void | Void World |
| caves | Caves | tendrils | Tendrils |
| skylands | Skylands | floating_islands | Floating Islands |
| huge_trees | Huge Trees | dense_ores | Dense Ores |
| feature_large_dummy | Lacking Large Features | villages | Villages |
| strongholds | Strongholds | mineshafts | Mineshafts |
| nether_fortress | Nether Fortress | ravines | Ravines |
| dungeons | Dungeons | spheres | Spheres |
| spikes | Spikes | feature_medium_dummy | Lacking Medium Features |
| lakes_surface | Surface Lakes | lakes_deep | Deep Lakes |
| obelisks | Obelisks | crystal_formations | Crystalline Formations |
| star_fissure | Star Fissure | feature_small_dummy | Lacking Small Features |
| env_accelerated | Accelerated | env_explosions | Spontaneous Explosions |
| env_lightning | Lightning | env_meteors | Meteors |
| env_scorched | Scorched Surface | pvp_off | Anti-PvP |
| no_sea | No Seas | clear_modifiers | Clear Modifiers |
| mod_north | North Direction | mod_east | East Direction |
| mod_south | South Direction | mod_west | West Direction |
| mod_end | Nadir Phase | mod_rising | Rising Phase |
| mod_noon | Zenith Phase | mod_setting | Setting Phase |
| mod_zero | Zero Length | mod_half | Half Length |
| mod_full | Full Length | mod_double | Double Length |
| mod_gradient | Gradient | color_horizon | Sunset Color |
| mod_color_maroon | Maroon Color | mod_color_red | Red Color |
| mod_color_olive | Olive Color | mod_color_yellow | Yellow Color |
| mod_color_dark_green | Dark Green Color | mod_color_green | Green Color |
| mod_color_teal | Teal Color | mod_color_cyan | Cyan Color |
| mod_color_navy | Navy Color | mod_color_blue | Blue Color |
| mod_color_purple | Purple Color | mod_color_magenta | Magenta Color |
| mod_color_black | Black Color | mod_color_grey | Grey Color |
| mod_color_silver | Silver Color | mod_color_white | White Color |

Block symbols: ids `block_dirt, block_stone, block_granite, block_diorite, block_andesite, block_polished_granite, block_polished_diorite, block_polished_andesite, block_sandstone, block_netherrack, block_end_stone, block_nether_bricks, block_oak_log, block_spruce_log, block_birch_log, block_jungle_log, block_acacia_log, block_dark_oak_log, block_diamond_ore, block_gold_ore, block_iron_ore, block_coal_ore, block_redstone_ore, block_lapis_ore, block_emerald_ore, block_ice, block_packed_ice, block_glass, block_snow_block, block_obsidian, block_glowstone, block_nether_quartz_ore, block_crystal, block_water, block_lava` (+ fluid symbols `block_<fluid block>` e.g. `block_black_ink`). Their names are computed: `symbol.mystcraft.block.wrapper` with the block name. Biome symbols `biome_<ns>_<path>` use `symbol.mystcraft.biome.wrapper` with `biome.<ns>.<path>`.

## 4. Other lang keys used
| key | value |
|---|---|
| `symbol.mystcraft.block.wrapper` | `%s Block` |
| `symbol.mystcraft.biome.wrapper` | `%s Biome` |

Creative portfolio titles are plain strings ("Spawned (All Symbols)", …) as in the original.

## 5. Notes for other packages
* Package E must call `BiomeSymbols.registerAll(server.registryAccess())` on `ServerAboutToStartEvent` (this also registers fluid symbols). The client gets biome symbols lazily through `BiomeSymbols.selectableBiomes(registries)` (`ModifierUtils.randomBiome`) — call `registerAll(clientRegistries)` on login if names are needed before an Age is built.
* `TerrainSymbols` call `AgeController.setTerrainBlocks(terrain, sea)` when the director is an `AgeController`, so `TerrainContext.terrainBlock()/seaBlock()` reflect the written blocks.
* `BiomeControllerSymbols` pad the biome list (2 for tiled/grid, 3 for layered) before constructing the package B controllers.
* `ToggleableWeather.isRaining()` is `rainStrength > 0.2` (the level's rain level is pushed each tick by `AgeTicker`).
* Legacy pages/ids: use `SymbolRemapper.remap(String)` on the raw stored string (legacy ids are not valid `Identifier`s because of upper-case characters).
