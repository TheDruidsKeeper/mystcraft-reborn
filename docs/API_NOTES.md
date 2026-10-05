# API notes — Minecraft 26.1 / NeoForge 26.1.2.x (Mojang mappings)

Training data is 1.20/1.21-shaped; 26.1 renamed or removed a lot. Check the real source before using any vanilla or
NeoForge API, then add what surprised you here (one line, verified). This file lists only differences and gotchas.

## Where the real sources are
* Minecraft, decompiled with Mojang names (ModDevGradle cache):
  `~/.gradle/caches/neoformruntime/intermediate_results/decompile_<hash>_output.jar` — `unzip -p <jar> net/minecraft/...Class.java`.
* NeoForge sources: `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/<version>/<hash>/neoforge-<version>-sources.jar`.
* Client jar for datapack JSON and built-in packs (structure JSON, tags, `version.json`):
  `<instance>/libraries/com/mojang/minecraft/26.1.2/minecraft-26.1.2-client.jar`.
* Vanilla datapack format in 26.1: `pack.mcmeta` uses `"min_format": [101, 0], "max_format": 101`.

## Renames and removals vs 1.21

| Area | 26.1 reality |
|---|---|
| Names | `ResourceLocation` → `net.minecraft.resources.Identifier`; `ResourceKey#identifier()` (but `TagKey#location()` / `SoundEvent#location()` unchanged); `net.minecraft.advancements.critereon` → `…advancements.criterion`; `MobSpawnType` → `EntitySpawnReason`; `ClickType` → `ContainerInput`; `DimensionDataStorage` → `SavedDataStorage` |
| Removed | `ItemInteractionResult`, `DirectionProperty`, `GenerationStep.Carving`, `DistExecutor`, `RegisterTextureAtlasSpriteLoadersEvent`, `VillagerTradesEvent`/`WandererTradesEvent`/`ItemListing` (trades are datapack registries), `Level#getDayTime/isDay/isNight`, `ServerLevel#setDayTime/setWeatherParameters`, `Player#displayClientMessage` (→ `sendOverlayMessage`), `Item#canAttackBlock` (→ `canDestroyBlock`), `EntityDataSerializers.OPTIONAL_UUID/COMPOUND_TAG`, `RenderType.celestial`, `FluidTank`, NeoForge `TriState` (vanilla `net.minecraft.util.TriState`) |
| Block | `entityInside(BlockState, Level, BlockPos, Entity, InsideBlockEffectApplier, boolean isPrecise)`; `onRemove` → `affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)` + `BlockEntity#preRemoveSideEffects`; `getAnalogOutputSignal(BlockState, Level, BlockPos, Direction)`; `Properties.noCollision()`; `RenderShape` = `INVISIBLE, MODEL`; `Block#fallOn(..., double fallDistance)`; plain `Block` subclasses need no `codec()` |
| Entity | `moveTo/absMoveTo` → `snapTo/absSnapTo`; `lerpTo` → `moveOrInterpolateTo`; `getTags()` → `entityTags()`; `canChangeDimensions` → `canTeleport`; `fallDistance` is `double`; `hurt(DamageSource,float)` exists again alongside `hurtServer` |
| Time / weather | Per-`WorldClock` time: `ServerLevel#clockManager()` → `ServerClockManager#setTotalTicks/addTicks/setPaused/setRate/moveToTimeMarker`; `Level#getDefaultClockTime()/getOverworldClockTime()`, `isBrightOutside()/isDarkOutside()`; weather is server-global `WeatherData` SavedData: `MinecraftServer#setWeatherParameters(int,int,boolean,boolean)`, `ServerLevel#getWeatherData()`; game rules: `GameRules.ADVANCE_TIME`, `ADVANCE_WEATHER`, `SPAWN_MOBS`, `get(GameRule<T>)`/`set(GameRule<T>,T,MinecraftServer)` |
| Env. attributes | `ServerLevel#setEnvironmentAttributes(EnvironmentAttributeSystem)` (deprecated but present); layers `addConstantLayer/addTimeBasedLayer/addPositionalLayer/addTimelineLayer`; **no automatic server→client sync of attribute values** — sync yourself with a payload; `AttributeTypes` has no IDENTIFIER (NeoForge defines its own for `CUSTOM_SKYBOX`) |
| GUI | `GuiGraphics` → `GuiGraphicsExtractor`; `AbstractContainerScreen#tick()` final; background drawn in **public** `extractBackground(GuiGraphicsExtractor,int,int,float)`; `getGuiLeft…` → `getLeftPos/getTopPos/getImageWidth/getImageHeight/getHoveredSlot`; `Screen#onClose()` → `minecraft.popGuiLayer()`; input events are records in `net.minecraft.client.input` (`MouseButtonEvent`, `KeyEvent`, `CharacterEvent`) |
| Transfer | `ItemStackHandler`/`IItemHandler`/`ItemHandlerHelper` exist but `@Deprecated(forRemoval)`; use `ResourceHandler<ItemResource>`, `ItemStacksResourceHandler`, `FluidStacksResourceHandler(size, capacity)`, `Transaction`, `net.neoforged.neoforge.transfer.fluid.FluidUtil`, `ResourceHandlerSlot(ResourceHandler<ItemResource>, IndexModifier<ItemResource>, int, int, int)`; `BlockCapabilityCache.create` takes `ServerLevel` |
| Worldgen | `ChunkAccess#setBlockState(BlockPos, BlockState, int flags)` (+2-arg); carving mask lives on `ProtoChunk`; `ProtoChunk` ctor takes `PalettedContainerFactory`, no `Registry<Biome>`; `RandomState.create(HolderGetter.Provider, …)`; `SpawnerData(type,min,max)` with weight in `addSpawn`; `Feature.RANDOM_PATCH/FLOWER` gone; `BiomeSpecialEffects` colour-only; `DimensionType(…, EnvironmentAttributeMap, HolderSet<Timeline>, Optional<Holder<WorldClock>>)`; `LevelStem(type, generator, OptionalLong seedOverride)` |
| Rendering | `RenderType` in `net.minecraft.client.renderer.rendertype` (factories in `RenderTypes`); sky via `RenderPipelines.CELESTIAL/STARS/SKY`; `SkyRenderState` in `client.renderer.state.level`; `RegisterColorHandlersEvent.BlockTintSources#register(List<BlockTintSource>, Block...)`; `SpecialModelRenderer#submit` has no `ItemDisplayContext`; `NativeImage#setPixel` is ARGB |
| Events / FML | `@EventBusSubscriber` has only `value`/`modid` (no `bus`); mod-bus events implement `IModBusEvent`; `AddReloadListenerEvent` → `AddServerReloadListenersEvent`; `RegisterClientReloadListenersEvent` → `AddClientReloadListenersEvent`; `ModelEvent.RegisterAdditional` → `RegisterStandalone`; `RegisterShadersEvent` → `RegisterRenderPipelinesEvent`; `FMLEnvironment.getDist()/isProduction()` |
| Commands | `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`; `ResourceLocationArgument` → `IdentifierArgument.id()/getId()` |
| Items | `Item#inventoryTick(ItemStack, ServerLevel, Entity, @Nullable EquipmentSlot)`; `getCraftingRemainder()` returns `ItemStackTemplate`; `onCraftedBy(Player, int)`; `DataComponents.UNBREAKABLE` is `DataComponentType<Unit>`; `Inventory#getSelectedItem()`, `getNonEquipmentItems()`; `ServerPlayer#level()` returns `ServerLevel` (no `serverLevel()`) |
| Codecs | `ItemStack.CODEC / OPTIONAL_CODEC / MAP_CODEC` exist (no `SINGLE_ITEM_CODEC`/`STRICT_CODEC`); `StreamCodec.composite` up to 12 args; `Registry#getValue(Identifier)` returns `@Nullable T`, `get(...)` returns `Optional<Holder.Reference<T>>`; `CompoundTag` has no `putUUID`/`contains(String,int)` |


## More renames (Level, Entity, chunks, maths)
- `Level.isClientSide()` method; `getDayTime()`/`isDay()`/`isNight()` GONE -> `getDefaultClockTime()`/`getOverworldClockTime()`/`clockManager()`, `isBrightOutside()`/`isDarkOutside()`. `getGameTime()` remains (LevelAccessor default).
- `ServerLevel.setDayTime`/`setWeatherParameters` GONE -> `getWeatherData()` (SavedData `WeatherData`) setters + `resetWeatherCycle()`; time via `ServerClockManager` (`clockManager()`), `Holder<WorldClock>`-keyed.
- `getSharedSpawnPos` -> `getRespawnData()`/`setRespawnData(LevelData.RespawnData)`. `getGameRules()` only on ServerLevel; package `net.minecraft.world.level.gamerules`. `DimensionDataStorage` -> `SavedDataStorage`. `ResourceLocation` -> `net.minecraft.resources.Identifier`.
- `Level.playSound` first param is `@Nullable Entity except` (no Player overloads). `destroyBlock` 4-arg has `int updateLimit`. `addFreshEntityWithPassengers` -> `tryAddFreshEntityWithPassengers`. `getForcedChunks` -> `getForceLoadedChunks()`.
- `Entity.moveTo`/`absMoveTo` -> `snapTo`/`absSnapTo`; `lerpTo` -> `moveOrInterpolateTo`; `getTags()` -> `entityTags()`; `isControlledByLocalInstance` -> `isLocalInstanceAuthoritative`; `canChangeDimensions` -> `canTeleport(Level, Level)`; `interactAt` -> `interact(Player, InteractionHand, Vec3)`; `fallDistance` is `double`; `hurt(DamageSource,float)` is back alongside `hurtServer`.
- `ChunkPos` is a record (`x()`, `z()`, `pack()`, `containing()`, `unpack()`); `GenerationStep.Carving` removed; `ChunkAccess.getStatus()` -> `getPersistedStatus()`; `setUnsaved(boolean)` -> `markUnsaved()`.
- `Direction.getNormal()` -> `getUnitVec3i()`; `Direction.getNearest(double x3)` -> `getApproximateNearest`. `Vec3.fromRGB24` removed. `EntityDataSerializers.OPTIONAL_UUID`/`COMPOUND_TAG` removed. `MobSpawnType` -> `EntitySpawnReason`. `MobEffects.MOVEMENT_SLOWDOWN`/`DAMAGE_RESISTANCE`/`CONFUSION` -> `SLOWNESS`/`RESISTANCE`/`NAUSEA`.

---

## Notes / surprises
- Permissions: `CommandSourceStack#hasPermission(int)` removed; use `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` or `src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`.
- `ResourceLocationArgument` -> `IdentifierArgument.id()/getId()`; `ResourceLocation` -> `net.minecraft.resources.Identifier` everywhere.
- Weather is server-global `WeatherData` (`MinecraftServer#setWeatherParameters/getWeatherData`); `ServerLevel#setWeatherParameters` gone.
- Time: `setDayTime/getDayTime` gone; per-`WorldClock` via `ServerClockManager` (`setTotalTicks`, `moveToTimeMarker`); `Level#getDefaultClockTime()`.
- Villager trades: `VillagerTrades.ItemListing`, `VillagerTradesEvent`, `WandererTradesEvent` gone; datapack `villager_trade` + `trade_set` registries; `VillagerProfession` record has `Int2ObjectMap<ResourceKey<TradeSet>> tradeSetsByLevel`.
- GameRules renamed (`ADVANCE_TIME`, `ADVANCE_WEATHER`, `SPAWN_MOBS`, `FIRE_SPREAD_RADIUS_AROUND_PLAYER`); `GameRules#get(GameRule<T>)`/`set(GameRule<T>, T, MinecraftServer)`.
- `TicketType` is a record `(long timeout, int flags, boolean forceNaturalSpawning)`; `ServerChunkCache#addTicketWithRadius/removeTicketWithRadius/addTicketAndLoadWithRadius`.
- `ServerLevel#explode` gained `WeightedList<ExplosionParticleInfo> blockParticles`; `ServerExplosion` has no public `getToBlow()`.
- `AddReloadListenerEvent` -> `AddServerReloadListenersEvent(Identifier, listener)`; `RegisterClientReloadListenersEvent` -> `AddClientReloadListenersEvent`; `ModelEvent.RegisterAdditional` -> `RegisterStandalone`; `RegisterShadersEvent` -> `RegisterRenderPipelinesEvent`; `RenderLevelStageEvent` split into per-stage subclasses; `GuiLayer#render(GuiGraphicsExtractor, DeltaTracker)`.
- `@EventBusSubscriber` has NO `bus` param (only `value` Dist[] and `modid`).

## Verified while building

All 28 `// UNVERIFIED:` sites checked against the NeoForge 26.1.2.76 javadoc mirror
(`https://lexxie.dev/neoforge/26.1/`) and NeoForge `26.1.x` raw sources. **Every guess was
correct**; markers removed. Do not re-guess these.

| Class | Verified signature | Status |
| --- | --- | --- |
| `LiquidBlock` | `LiquidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties)` — sole ctor; `public final FlowingFluid fluid` | verified, marker removed |
| `BlockBehaviour.BlockStateBase` | `public VoxelShape getInteractionShape(BlockGetter, BlockPos)` (no `CollisionContext` overload). Block-side `protected getInteractionShape(BlockState, BlockGetter, BlockPos)` is not in the `-public` javadoc; NeoForge's `BlockBehaviour.java.patch` has no hunk for it, so it is unchanged vanilla. | verified, note kept |
| `SkyRenderState` | public fields: `skybox, shouldRenderDarkDisc, sunAngle, moonAngle, starAngle, rainBrightness, starBrightness, sunriseAndSunsetColor, moonPhase, skyColor, endFlashIntensity, endFlashXAngle, endFlashYAngle`; `void reset()` | verified, marker removed |
| `net.minecraft.world.level.MoonPhase` | enum, declaration order `FULL_MOON, WANING_GIBBOUS, THIRD_QUARTER, WANING_CRESCENT, NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS`; `int index()`, `int startTick()`, `String getSerializedName()`; `COUNT`, `PHASE_LENGTH`, `CODEC` | verified, marker removed |
| `ViewportEvent.ComputeFogColor` | `float getRed()/getGreen()/getBlue()`, `void setRed(float)/setGreen(float)/setBlue(float)`. Backing field is a **private `Vector4f`** — no `getColor`/`setColor`, no alpha accessor. Not cancellable. | verified, marker removed |
| `ClientLevel` | `public void addEntity(Entity entity)` (void; no `putNonPlayerEntity`) | verified, marker removed |
| `SmokeParticle.Provider` | `public Provider(SpriteSet sprites)`, `implements ParticleProvider<SimpleParticleType>`; only nested class of `SmokeParticle` | verified, marker removed |
| `OrderedSubmitNodeCollector` | **one** overload: `void submitNameTag(PoseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords, double distanceToCameraSq, CameraRenderState camera)` — attachment is `@Nullable` | verified, marker removed |
| `EditBox` | `public boolean canConsumeInput()` — declared directly on `EditBox` | verified, marker removed |
| `PlayerList` | `public int getViewDistance()`; `public @Nullable ServerPlayer getPlayer(UUID)` (sibling `getPlayer(String)`) | verified, marker removed |
| `Player` | `public boolean isSleepingLongEnough()`; `public void stopSleepInBed(boolean forcefulWakeUp, boolean updateLevelList)`. `isSleeping()` is inherited from `LivingEntity` (26.1: `Player extends Avatar extends LivingEntity`) | verified, marker removed |
| `EntityDimensions` | record; `public static EntityDimensions scalable(float width, float height)` (also `fixed(float, float)`) | verified, marker removed |
| `CollisionGetter` | `default boolean noCollision(@Nullable Entity, AABB)`; also `noCollision(AABB)`, `noCollision(Entity)`, `noCollision(@Nullable Entity, AABB, boolean alwaysCollideWithFluids)` | verified, marker removed |
| `Block` | `public MutableComponent getName()` (returns `MutableComponent`, not `Component`) | verified, marker removed |
| `FluidState` | `public BlockState createLegacyBlock()` | verified, marker removed |
| NeoForge `FluidType` | `public final boolean isLighterThanAir() { return this.getDensity() <= 0; }` — **final**, cannot be overridden; control via `Properties#density(int)` (default 1000) | verified, marker removed |
| `BiomeTags` | `IS_DEEP_OCEAN, IS_OCEAN, IS_RIVER, IS_BEACH, IS_MOUNTAIN, IS_HILL, IS_END` all exist verbatim as `public static final TagKey<Biome>` (siblings incl. `IS_BADLANDS, IS_TAIGA, IS_JUNGLE, IS_FOREST, IS_SAVANNA, IS_OVERWORLD, IS_NETHER`) | verified, markers removed |
| `BiomeResolver` | single abstract method `Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler)`; interface is **not** annotated `@FunctionalInterface` but is still lambda-compatible | verified, marker removed |
| `LeavesBlock` | `public static final BooleanProperty PERSISTENT` declared directly on `LeavesBlock` (siblings `DISTANCE`, `WATERLOGGED`); 26.1: `public abstract class LeavesBlock extends Block implements SimpleWaterloggedBlock, IShearable` | verified, marker removed |

Incidental finding: NeoForge `FluidType#move(FluidState, LivingEntity, Vec3, double)` is
`@Deprecated(forRemoval = true, since = "26.1")` — replaced by
`public boolean move(LivingEntity entity, Vec3 movementVector, double gravity)`.


## Verified in later rounds
- `ChunkPos` is a record: `x()`/`z()`; the fields are private.
- `StructurePlacement`: ctor `(Vec3i locateOffset, FrequencyReductionMethod, float frequency, int salt, Optional<ExclusionZone>)`; abstract `isPlacementChunk(ChunkGeneratorStructureState, int, int)` and `type()`; `placementCodec(i)` builds the shared fields; registry `Registries.STRUCTURE_PLACEMENT`, a type is `() -> MapCodec`. `ChunkGenerator#findNearestMapStructure` only handles RandomSpread and ConcentricRings — custom placements need their own locate (`world/structure/FacilityLocator`).
- `ChunkGeneratorStructureState`: `getLevelSeed()`, `getPlacementsForStructure(Holder<Structure>)`, `possibleStructureSets()`; the seed passed to `createForFlat` is what placements and jigsaw assembly use.
- `StructureTemplatePool#getTemplates()` is `List<Pair<StructurePoolElement, Integer>>`; a `SinglePoolElement` whose `.nbt` is missing resolves to an empty template (`getSize(...)` == `Vec3i.ZERO`).
- `Structure.GenerationContext` has a 9-arg ctor (registryAccess, generator, biomeSource, randomState, templateManager, seed, chunkPos, heightAccessor, biomePredicate); `findValidGenerationPoint(ctx)` is public — handy to diagnose a structure that never starts.
- The GameTest server runs `new WorldOptions(0L, false, false)`: **structures never generate there**; call `ChunkGenerator#createStructures` directly in tests.
- `Level#getHeight(Heightmap.Types, x, z)` answers `minY` for an unloaded chunk and never generates; `getChunk` first.
- `SignBlockEntity#setText(SignText, boolean front)`, `SignText#setMessage(int, Component)` returns a new `SignText`; `setWaxed(true)` stops edits. `StandingSignBlock.ROTATION` (0..15), `WallSignBlock.FACING`.
- NeoForge `AddPackFindersEvent#addPackFinders(Identifier packLocation, PackType, Component name, PackSource, boolean alwaysActive, Pack.Position)`: path is under `resources/`; `PackSource.BUILT_IN` = enabled by default, `FEATURE` = opt-in.
- `DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, modid)` works for placement types; structure/structure_set/template_pool are datapack registries (JSON only).
