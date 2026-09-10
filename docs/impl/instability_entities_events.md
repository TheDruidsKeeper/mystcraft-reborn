# Package E — instability, entities, commands, villager, events, age ticking

Root package `com.techbucketdivision.mystcraft` (abbreviated `…`).

## api/instability
| Class | Members |
|---|---|
| `InstabilityDirector` (interface) | `int getInstabilityScore()`, `void registerEffect(EnvironmentalEffect)` |
| `InstabilityProvider` (functional) | `void addEffects(InstabilityDirector, int level)` |
| `InstabilityBonus` (interface) | `String name()`, `int value()`, `void tick(ServerLevel)` |
| `InstabilityBonusProvider` (functional) | `void register(InstabilityBonusManager, ServerLevel)` |

## instability
| Class | Key members |
|---|---|
| `InstabilityManager` | `registerDefaults()` (decks basic/harsh/destructive/eating/death + all §6.4 cards); `register(String id, InstabilityProvider, int cost) -> boolean` (config `instability.disabled`, dry-run profiling); `setDeckCost(String,int)`, `addCards(String deck, String card, int count)`, `getProvider(String)`, `allProviders()`, `cardCost`, `deckCost`, `decks()`, `deckCards(String)`, `smallestCost()`; constants `DECK_*` |
| `InstabilityBlocks` | watched blocks & factors: `setFactors(Block,f1,f2)`, `setFactors(String key,f1,f2)`, `key(Block)` (= registry id string), `keyFor(BlockState)`, `watchedKeys()`, `factor1/factor2(String)`, `registerDefaults()` (ores incl. deepslate, glowstone, quartz, crystal). **Package A FluidSymbols should call `InstabilityBlocks.setFactors(fluidBlock, f1, f2)`.** |
| `BaselineProfiler` | `initialize(MinecraftServer)` (config values; generation mode `// TODO`), `isConstructed()`, `baseline(String)`, `all()`, `setBaseline(Map)`, `clear()`, `fromConfig()` |
| `ChunkProfileData` | `int[] data` (384 layers × 256), `int count`, `CODEC`, `static index(x,y,z)`, `MIN_Y=-64`, `LAYERS=384` |
| `ChunkProfiler` (per-level SavedData `mystcraft:chunk_profile`) | `static get(ServerLevel)`, `profile(LevelChunk)`, `count()`, `clear()`, `calculateInstability()`, `calculateSplitInstability()`, `lastSplit()`; hooks `ChunkEvent.Load` (`isNewChunk` on Age levels) |
| `InstabilityDeckData` (per-level SavedData `mystcraft:instability_decks`) | `static get(ServerLevel)`, `deck(String)`, `updateDeck(String, List<String>)` |
| `InstabilityController` | `static @Nullable get(ServerLevel)`, `static invalidate(ResourceKey<Level>)`, `static clearAll()`; `score()` (§6.1, 0 until 400 chunks profiled; requests a radius-10 chunk ticket around spawn to reach the minimum), `getInstabilityScore()` (quantised), `reconstruct()`, `tick(LevelChunk)`, `isEnabled()`, `isInstabilityEnabled()`, `deck(String)`, `providerLevels()`, `blockInstability()`, `profiledChunks()`, `debugInstability()/setDebugInstability(int)`, `invalidateProfile()`, `ageController()`; `MIN_CHUNKS=400`, `RECOMPUTE_INTERVAL=100` |
| `InstabilityBonusManager` | `static registerProvider(InstabilityBonusProvider)`, `static get(ServerLevel)`, `register(InstabilityBonus)`, `bonuses()`, `total()`, `tick(ServerLevel)`; nested `Listener` (death / login / logout / dimension change hooks); dispatches game events itself |
| `PlayerKilledBonus(InstabilityBonusManager, ServerLevel, String player, int max, float decayRate)` | §6.6 |
| `PlayerTrollPenalty(InstabilityBonusManager, ServerLevel, String player, int max, float rate)` | §6.6 |

### instability/effects (all implement `api.symbol.logic.EnvironmentalEffect`)
`MeteorEffect()`, `LightningEffect(@Nullable ColorGradient)` / `LightningEffect()`, `ExplosionsEffect()`, `ScorchedEffect(int level)` / `(int level, boolean global)`, `ExtraTicksEffect(@Nullable BlockState onlyState)` / `()`, `DecayEffect(DecayType, int minY, @Nullable Integer maxY)` (+ `ban(Predicate<BlockState>)`, `banAirAndFluids()`), `CrumbleEffect()` (+ `static initMappings()`, `block(Block,Block)`, `state(BlockState,BlockState)`, `tag(TagKey<Block>,BlockState)`, `mappingFor(BlockState)`), `PotionEffectProvider(boolean global, Holder<MobEffect>, int duration, boolean enemiesOnly)` (an `InstabilityProvider`), `EffectProviders.{scorched, scorchedGlobal, crumble, explosions, lightning, meteors, decay(DecayType,int,Integer), whiteDecay, blackDecay}()`.

### instability/decay
`DecayHandlers.get(DecayType)` (green/yellow → black). `DecayHandler`: `type()`, `state()`, `randomTick(ServerLevel, BlockPos, BlockState, RandomSource)`, `boolean onPlace(ServerLevel, BlockPos, BlockState)` (returns true if the block was removed; base removes decay outside Ages), `onEntityContact(Level, BlockPos, BlockState, Entity)`, `hardness()`, `explosionResistance()`. Implementations `BlackDecay`, `RedDecay`, `BlueDecay`, `PurpleDecay`, `WhiteDecay` (abstract `SpreadingDecay`). **`block.DecayBlock` should call these from `randomTick`, `onPlace` (server side) and `entityInside`.** Decay blocks need not be randomly ticking: `ExtraTicksEffect(state)` ticks them via `BlockState#randomTick`.

## entity
| Class | Key members |
|---|---|
| `LinkbookEntity extends Entity` | `static @Nullable spawnFor(ServerLevel, Entity dropper, ItemStack)`, `static @Nullable spawnAt(ServerLevel, Vec3, Vec3 motion, float yaw, ItemStack)`, `getBook()/setBook(ItemStack)`, `getAgeName()`, `getHealth()/getMaxHealth()/setHealth(float)` (mirrors `BookHealth` component), `hurtTime()`, `linkEntity(Entity)`; right-click → `menu.BookMenu.openForEntity(ServerPlayer, Entity)`, sneak + empty hand picks up |
| `MystFallingBlockEntity extends Entity` | `static fall(ServerLevel, BlockPos, BlockState)`, `static cascade(ServerLevel, BlockPos)`, `getBlockState()`, `fallTime()` |
| `MeteorEntity extends Entity` | `static @Nullable spawn(ServerLevel, double x, double z, float scale, int penetration)`, `static spawn(ServerLevel, Vec3 pos, Vec3 motion, float, int)`, `getScale()`, `setScale(float,int)` |
| `ColoredLightningBolt extends LightningBolt` | `static @Nullable strike(ServerLevel, BlockPos, int rgb)` (spawns tracked entity + sends `LightningPayload` within 512), `getColor()/setColor(int)`, `DEFAULT_COLOR=0x737380` |
| `explosion.AdvancedExplosion(ServerLevel, @Nullable Entity, x, y, z, float size)` | `addEffect(Effect)`, `explode()`, `affectedBlocks()`, `hitPlayers()`, `size()`, `center()`; effects `BASIC`, `BREAK_NO_DROP`, `BREAK_DROP_ITEMS`, `FIRE`, `PLACE_ORES`; `static registerMeteorBlock(BlockState, float)`; sends `ExplosionEffectsPayload` within 64 |

Renderers (package D) needed for `mystcraft:linkbook`, `mystcraft:falling_block`, `mystcraft:meteor`, `mystcraft:lightning` (reads `getColor()`).

## command
`MystcraftCommands` (registered on `RegisterCommandsEvent`): `/tpx [subject] <player | dimension [pos]>`, `/myst-create [name]`, `/myst-agebook [dimension]`, `/myst-twi|/myst-toggleworldinstability [dimension] [enabled]`, `/myst-spawnmeteor [scale] [penetration] [pos]` (config `commands.spawnmeteor.enabled`), `/myst-permissions <player> <restrict|permit> <entry|depart> <all|dimension>`, `/myst-regenchunk` (stub → not implemented), `/myst-reprofile [dimension]`, `/myst-dbg read|set|run <address> [value]` (addresses: `instability[.score|.debug|.symbol|.blocks|.bonus|.profiled_chunks|.providers|.decks|.enabled]`, `symbols`, `time`, `global.baseline`, `global.providers`; run `experimental.mark_dead`, `instability.reconstruct`), `/myst-time <set day|night|<value> | add <value>> [dimension|all]` (Age time), `/myst-toggledownfall [dimension]`.

## villager
`ArchivistShop` (no-arg ctor, `CODEC`): `static of(Villager)`, `save(Villager)`, `getPageStack(int)`, `ensureStocked(RandomSource)`, `getPagePrice(int)`, `getBoosterCount()`, `getBoosterCost()`, `simulate(long gameTime, RandomSource)`, `purchasePage(ServerPlayer,int)`, `purchaseBooster(ServerPlayer)`, `static countEmeralds(ServerPlayer)`, `toTag(HolderLookup.Provider)`, `static fromTag(HolderLookup.Provider, CompoundTag)`, `copy()`, `lastUpdated()`; constants `SLOTS=3`, `BOOSTER_COST=20`, `MAX_BOOSTERS=8`, `MAX_PAGES=5`, `RESTOCK_STEP=12000`.
`ArchivistEvents`: `EntityInteract` on an archivist (not sneaking) → stock/simulate/save, then `menu.ArchivistShopMenu.open(ServerPlayer, Villager)`; `static isArchivist(Villager)`.

## event / dimension
- `CommonEvents`: ServerAboutToStart (`BiomeSymbols.registerAll`, `FluidSymbols.registerAll`), ServerStarted (`BaselineProfiler.initialize`, `AgeManager.restoreAll`), ServerStopped (clears caches), login/dimension-change checks with scheduled ejection (`scheduleEjection(ServerPlayer)`, `ejectToHome(ServerPlayer)`), `ServerConfigPayload` + `AgeDataSyncPayload` sync, entry advancements, `PlayerRespawnPositionEvent` (respawn in Age via `world.AgeSpawn.findSpawn`), PvP cancel, ink bucket/bottle cancel, dropped `LinkingItem` → `LinkbookEntity`, `LevelEvent.Load` → `AgeEnvironment.apply`, `LevelEvent.Unload` → instability invalidate. `static @Nullable ServerLevel ageLevelOf(Entity)`.
- `ModBusEvents`: `RegisterCapabilitiesEvent` → `blockentity.BlockEntityCapabilities.register`, `item.ItemCapabilities.register`.
- `dimension.AgeTicker`: `LevelTickEvent.Post` for Age levels (time, bonus, weather → `setRainLevel/setThunderLevel`, chunk ticks around players radius ≤ 8 via `getChunkNow`, sleep → `timeToSunrise`, resend AgeData every 200 (weather dirty) / 1200 ticks or on revision change).
- `dimension.AgeEnvironment.apply(ServerLevel, AgeController)`: env-attribute layers CLOUD_HEIGHT, SUN_ANGLE, SKY_COLOR, FOG_COLOR, CLOUD_COLOR.

## Assumed from other packages
`item.LinkingItem` (class; `static LinkInfo getLinkInfo(ItemStack)`, `static void activate(ItemStack, ServerLevel, Entity)`), `item.LinkingBookItem`, `item.PageItem.createSymbolPage(AgeSymbol)`, `createLinkPanel(Set<LinkProperty>)`, `getSymbol(ItemStack)`, `item.DescriptiveBookItem.create(Player, List<ItemStack>, String)`, `initializeForAge(ItemStack, AgeData)`, `item.ItemCapabilities.register(RegisterCapabilitiesEvent)`, `blockentity.BlockEntityCapabilities.register(...)`, `menu.BookMenu.openForEntity(ServerPlayer, Entity)`, `menu.ArchivistShopMenu.open(ServerPlayer, Villager)`, `linking.LinkController.travelEntity(Entity, LinkInfo) -> boolean`, `linking.LinkPermissions.get(MinecraftServer)` with `permitEntry/restrictEntry/permitDepart/restrictDepart(String playerName, @Nullable ResourceKey<Level> dimOrNullForAll)`, `symbol.BiomeSymbols.registerAll(HolderLookup.Provider)`, `symbol.FluidSymbols.registerAll()`, `world.AgeSpawn.findSpawn(ServerLevel, AgeController) -> BlockPos`.

## Lang keys
```
entity.mystcraft.linkbook=Book
entity.mystcraft.falling_block=Falling Block
entity.mystcraft.meteor=Meteor
entity.mystcraft.lightning=Lightning
instability.mystcraft.bonus.death=%s has been killed by %s, activating bonus stability!
instability.mystcraft.bonus.death.partial=%s has died.  Unlocking part of the bonus stability.  Kill them personally to unlock all of it!
instability.mystcraft.bonus.death.alert=%s has entered the age.  Kill them in order to unlock a temporary stability bonus in this age!
instability.mystcraft.bonus.troll.death=%s has been killed, resetting the instability penalty!
instability.mystcraft.bonus.troll.alert=%s has entered the age.  The age will slowly become less stable!  Kill them to reset this!
instability.mystcraft.bonus.troll.left=%s has left the age.  The stability penalty will slowly dissipate.
commands.mystcraft.fail.not_age=That dimension is not a Mystcraft Age
commands.mystcraft.fail.not_implemented=This command is not implemented yet
commands.mystcraft.tpx.fail.nosubject=Teleport subject invalid. No tp.
commands.mystcraft.tpx.fail.noworld=Could not get world for that dimension. No tp.
commands.mystcraft.tpx.fail.refused=The link was refused
commands.mystcraft.tpx.success=Linked %s to %s
commands.mystcraft.create.success=Created Age "%s" (%s)
commands.mystcraft.agebook.success=Gave %s a Descriptive Book for %s
commands.mystcraft.twi.success=Instability in %s is now %s
commands.mystcraft.meteor.success=Spawned a meteor over %d, %d
commands.mystcraft.permissions.success=Set permissions for player %s: %s %s %s
commands.mystcraft.reprofile.success=Cleared the chunk profile of %s
commands.mystcraft.debug.address.invalid=Unknown debug address
commands.mystcraft.debug.marked_dead=Marked %s as dead
commands.mystcraft.time.set=Set the time to %d in %s
commands.mystcraft.time.set.all=Set the time to %d in all Ages
commands.mystcraft.time.added=Added %d to the time in %s
commands.mystcraft.time.added.all=Added %d to the time in all Ages
commands.mystcraft.downfall.success=Toggled downfall in %s
```
