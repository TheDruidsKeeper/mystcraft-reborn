# API_CHEATSHEET — Minecraft 26.1 + NeoForge 26.1.2.x (Mojang mappings)

Verified signature reference for writing mod code without a compiler. Compiled 2026-09-06 from:
- Javadoc mirror (NeoForge 26.1.2.76, **public members only**): `JD = https://lexxie.dev/neoforge/26.1/`
- NeoForge 26.1.x patches (vanilla context for protected members): `PATCH = https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/patches/net/minecraft/…java.patch`
- NeoForge 26.1.x sources / tests: `NF = https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/…`
- docs.neoforged.net, 26.1 / 1.21.11 primers, FancyModLoader, Infiniverse (26.1), misode/mcmeta vanilla data.

Each section keeps its own legend (`V`/`VERIFIED (url)` vs `U`/`UNVERIFIED`). Companion document: `TOOLCHAIN.md` §4 (registration, dimensions, networking, BER, attachments, fluids) — this file supersedes §4 wherever the two disagree.

## 0. Top-level surprises / renames vs. 1.21.x (read first)

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

## Contents
- A/B Block, BlockState, Properties, EntityBlock, BlockEntity
- C/D Item, ItemStack, DataComponents, Component, Player, Inventory
- E/F Level, ServerLevel, chunks, Entity, LivingEntity, Vec3/AABB/BlockPos
- G Menus, Screens, GuiGraphicsExtractor, NeoForge transfer API & capabilities
- H/I Networking, ChunkGenerator/BiomeSource, noise, DimensionType/Timeline, features/carvers
- J/K Environment attributes, sky/rendering, tints, BER, dynamic textures, SpecialModelRenderer
- L1 Identifier/registries/holders, creative tabs, Direction/Rotation/structures, NBT/Codec/StreamCodec, loot, advancements, FML/config/event bus
- L2 NeoForge events, commands, explosions/particles/tickets, villagers, game rules, weather/time

---

---

# Cheatsheet A/B: Block, BlockState, Properties, EntityBlock, BlockEntity (MC 26.1 / NeoForge 26.1.2.x)

Legend: V = VERIFIED (source url), U = UNVERIFIED (best guess). JD = https://lexxie.dev/neoforge/26.1/ ; PATCH = https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/patches/net/minecraft/

## A1. Block / BlockBehaviour overridable methods
`net.minecraft.world.level.block.Block` extends `net.minecraft.world.level.block.state.BlockBehaviour`.
Imports used below: `net.minecraft.core.BlockPos`, `net.minecraft.core.Direction`, `net.minecraft.world.level.Level`, `net.minecraft.world.level.LevelReader`, `net.minecraft.world.level.BlockGetter`, `net.minecraft.world.level.LevelAccessor`, `net.minecraft.world.level.ScheduledTickAccess`, `net.minecraft.server.level.ServerLevel`, `net.minecraft.world.level.block.state.BlockState`, `net.minecraft.world.entity.player.Player`, `net.minecraft.world.entity.Entity`, `net.minecraft.world.InteractionHand`, `net.minecraft.world.InteractionResult`, `net.minecraft.world.item.ItemStack`, `net.minecraft.world.phys.BlockHitResult`, `net.minecraft.util.RandomSource`, `net.minecraft.world.level.redstone.Orientation`, `net.minecraft.world.entity.InsideBlockEffectApplier`, `net.minecraft.world.level.block.state.StateDefinition`, `net.minecraft.world.phys.shapes.VoxelShape`, `net.minecraft.world.phys.shapes.CollisionContext`, `net.minecraft.world.level.block.RenderShape`, `net.minecraft.world.level.block.Rotation`, `net.minecraft.world.level.block.Mirror`, `com.mojang.serialization.MapCodec`, `org.jspecify.annotations.Nullable` (NeoForge 26.1 uses jspecify).

```java
public Block(BlockBehaviour.Properties properties)                       // V (JD Block.html)
protected abstract MapCodec<? extends Block> codec();                     // in BlockBehaviour; Block overrides it returning Block.CODEC -> plain Block subclasses need NOT override. U (protected hidden; NeoForge test TestBlock extends Block w/o codec() compiles: V https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/tests/src/main/java/net/neoforged/neoforge/debug/block/BlockEntityTests.java)
public static <B extends Block> MapCodec<B> simpleCodec(Function<BlockBehaviour.Properties, B> constructor)   // V (JD BlockBehaviour.html)
public static final MapCodec<Block> CODEC;                                // V (JD Block.html)

protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)   // V (PATCH world/level/block/NoteBlock.java.patch)
protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult)   // V order (BlockBehaviour patch + TntBlock patch super call). Return InteractionResult; ItemInteractionResult does NOT exist in 26.1 (V: JD page 404, see A6)
protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston)   // V (PATCH LiquidBlock/TntBlock). Orientation = net.minecraft.world.level.redstone.Orientation (V JD BlockStateBase index)
protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)   // V (PATCH TntBlock)
protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston)   // V (PATCH AbstractCauldronBlock). onRemove(...) NO LONGER EXISTS (no wrapper in BlockStateBase). Block-entity cleanup moved to BlockEntity#preRemoveSideEffects (see B3).
protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)   // V (PATCH CropBlock)
protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)         // U (wrapper tick(ServerLevel,BlockPos,RandomSource) V JD)
protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise)   // V (PATCH CropBlock) -- NOTE extra boolean vs 1.21.5
protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)   // V (PATCH TntBlock)
protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)            // U (wrapper getShape(BlockGetter,BlockPos,CollisionContext) V JD)
protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)   // U (wrapper V JD)
protected RenderShape getRenderShape(BlockState state)               // U (wrapper getRenderShape() V JD)
protected boolean hasAnalogOutputSignal(BlockState state)            // U (wrapper V JD)
protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction)   // U; wrapper is getAnalogOutputSignal(Level,BlockPos,Direction) V JD -- NOTE new Direction param
protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)   // V (PATCH CropBlock)
protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random)   // U order (wrapper updateShape(LevelReader,ScheduledTickAccess,BlockPos,Direction,BlockPos,BlockState,RandomSource) V JD)
protected boolean propagatesSkylightDown(BlockState state)           // U (wrapper propagatesSkylightDown() V JD; no BlockGetter/BlockPos since 1.21.2)
protected boolean isRandomlyTicking(BlockState state)                // U (wrapper V JD)
protected BlockState rotate(BlockState state, Rotation rotation)     // U (wrapper rotate(Rotation) V JD, deprecated by Neo in favour of rotate(LevelAccessor,BlockPos,Rotation))
protected BlockState mirror(BlockState state, Mirror mirror)         // V (PATCH ChestBlock)
protected long getSeed(BlockState state, BlockPos pos)               // U (wrapper getSeed(BlockPos) V JD)
public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)   // V (JD Block.html) - public, client-side
protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1)   // V (PATCH NoteBlock)
protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos)   // U (wrapper V JD); MenuProvider = net.minecraft.world.MenuProvider
protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData)   // V (BlockBehaviour patch; deprecated by Neo -> IBlockExtension#getCloneItemStack(LevelReader,BlockPos,BlockState,boolean,Player))
protected boolean isPathfindable(BlockState state, PathComputationType type)   // U (wrapper V JD); net.minecraft.world.level.pathfinder.PathComputationType
public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)   // V (JD Block.html)
public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)   // V (JD Block.html)
public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)   // V (JD + TntBlock patch)
public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool)   // V (JD Block.html)
public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity)                   // V (JD)
public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance)   // V (JD) double!
public static List<ItemStack> getDrops(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity)   // V (JD)
public static List<ItemStack> getDrops(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity entity, ItemInstance tool)   // V (JD); ItemInstance = net.minecraft.world.item.ItemInstance (new 26.1 super-interface of ItemStack)
public static void dropResources(BlockState state, Level level, BlockPos pos)   // V (JD); also (BlockState, LevelAccessor, BlockPos, BlockEntity) and (BlockState, Level, BlockPos, BlockEntity, Entity, ItemStack)
public static void popResource(Level level, BlockPos pos, ItemStack stack)      // V (JD)
public final BlockState defaultBlockState(); public StateDefinition<Block, BlockState> getStateDefinition(); protected final void registerDefaultState(BlockState state) /*U protected*/; public Item asItem(); public float getFriction(); public float getExplosionResistance(); public float getSpeedFactor(); public float getJumpFactor(); public BlockState withPropertiesOf(BlockState state)   // V (JD Block.html)
```
Light: no overridable getLightEmission on Block — use `Properties.lightLevel(ToIntFunction<BlockState>)` (V JD Properties) or NeoForge `IBlockExtension#getLightEmission(BlockState, BlockGetter, BlockPos)` (U).
Shape helpers (all `public static VoxelShape`, pixel units 0..16, V JD Block.html):
`Block.box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ)`, `Block.cube(double size)`, `Block.cube(double sizeX,double sizeY,double sizeZ)`, `Block.column(double sizeXZ,double minY,double maxY)`, `Block.column(double sizeX,double sizeZ,double minY,double maxY)`, `Block.boxZ(double sizeXY,double minZ,double maxZ)`, `Block.boxZ(double sizeX,double sizeY,double minZ,double maxZ)`, `Block.boxZ(double sizeX,double minY,double maxY,double minZ,double maxZ)`, `Block.boxes(int endInclusive, IntFunction<VoxelShape>)`, `Block.isShapeFullBlock(VoxelShape)`.
`net.minecraft.world.phys.shapes.Shapes`: `Shapes.block()`, `Shapes.empty()`, `Shapes.or(VoxelShape, VoxelShape...)`, `Shapes.box(double x6)`, `Shapes.join(VoxelShape,VoxelShape,BooleanOp)`, `Shapes.joinIsNotEmpty(...)` (V CampfireBlock patch for joinIsNotEmpty; rest U-standard).

Update flags (`public static final int`, names V JD Block.html; values U = unchanged from 1.21):
`UPDATE_NEIGHBORS=1, UPDATE_CLIENTS=2, UPDATE_INVISIBLE=4, UPDATE_IMMEDIATE=8, UPDATE_KNOWN_SHAPE=16, UPDATE_SUPPRESS_DROPS=32, UPDATE_MOVE_BY_PISTON=64, UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE=128, UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS=256, UPDATE_SKIP_ON_PLACE=512, UPDATE_NONE=4, UPDATE_ALL=3, UPDATE_ALL_IMMEDIATE=11, UPDATE_SKIP_ALL_SIDEEFFECTS=768`.

## A2. BlockBehaviour.Properties (`net.minecraft.world.level.block.state.BlockBehaviour.Properties`) -- ALL V (JD BlockBehaviour.Properties.html)
```java
static Properties of(); static Properties ofFullCopy(BlockBehaviour block); @Deprecated static Properties ofLegacyCopy(BlockBehaviour block);
Properties mapColor(MapColor); mapColor(DyeColor); mapColor(Function<BlockState, MapColor>);
Properties noCollision();   // NOTE: spelled noCollision (not noCollission) in 26.1
Properties noOcclusion(); friction(float); speedFactor(float); jumpFactor(float); sound(SoundType);
Properties lightLevel(ToIntFunction<BlockState> lightEmission);
Properties strength(float destroyTime, float explosionResistance); strength(float destroyTime); instabreak(); destroyTime(float); explosionResistance(float);
Properties randomTicks(); dynamicShape(); noLootTable(); overrideLootTable(Optional<ResourceKey<LootTable>>); ignitedByLava(); liquid();
Properties forceSolidOn(); @Deprecated forceSolidOff(); pushReaction(PushReaction); air(); replaceable();
Properties isValidSpawn(BlockBehaviour.StateArgumentPredicate<EntityType<?>>); isRedstoneConductor(BlockBehaviour.StatePredicate); isSuffocating(StatePredicate); isViewBlocking(StatePredicate); emissiveRendering(StatePredicate);
Properties postProcess(BlockBehaviour.PostProcess); requiresCorrectToolForDrops(); offsetType(BlockBehaviour.OffsetType); noTerrainParticles(); requiredFeatures(FeatureFlag...); instrument(NoteBlockInstrument);
Properties setId(ResourceKey<Block> id); overrideDescription(String descriptionId);
// StatePredicate: boolean test(BlockState, BlockGetter, BlockPos); StateArgumentPredicate<A>: boolean test(BlockState, BlockGetter, BlockPos, A)  (U shapes)
```
Packages: `net.minecraft.world.level.material.MapColor`, `net.minecraft.world.level.material.PushReaction` (enum NORMAL, DESTROY, BLOCK, IGNORE, PUSH_ONLY - U values), `net.minecraft.world.item.DyeColor`, `net.minecraft.world.level.block.SoundType`, `net.minecraft.world.level.storage.loot.LootTable`, `net.minecraft.resources.ResourceKey`, `net.minecraft.world.level.block.state.properties.NoteBlockInstrument`.

## A3. BlockState (`net.minecraft.world.level.block.state.BlockState` extends `BlockBehaviour.BlockStateBase` extends `net.minecraft.world.level.block.state.StateHolder<Block,BlockState>`) -- V (JD BlockBehaviour.BlockStateBase.html index) unless marked
```java
Block getBlock(); Holder<Block> typeHolder();   // typeHolder() replaces getBlockHolder() (TypedInstance<Block>)
boolean isAir(); boolean liquid(); boolean ignitedByLava(); boolean blocksMotion(); boolean isSolid(); boolean isSolidRender(); boolean canOcclude();
boolean isRandomlyTicking(); boolean hasBlockEntity(); boolean propagatesSkylightDown(); int getLightEmission() /*@Deprecated by Neo*/; int getLightDampening();
MapColor getMapColor(BlockGetter level, BlockPos pos); float getDestroySpeed(BlockGetter level, BlockPos pos); float getDestroyProgress(Player, BlockGetter, BlockPos);
PushReaction getPistonPushReaction(); SoundType getSoundType() /*@Deprecated by Neo*/; RenderShape getRenderShape(); long getSeed(BlockPos);
VoxelShape getShape(BlockGetter, BlockPos); getShape(BlockGetter, BlockPos, CollisionContext); getCollisionShape(BlockGetter, BlockPos); getCollisionShape(BlockGetter, BlockPos, CollisionContext);
VoxelShape getOcclusionShape(); getFaceOcclusionShape(Direction); getBlockSupportShape(BlockGetter, BlockPos); getVisualShape(BlockGetter, BlockPos, CollisionContext); getInteractionShape(BlockGetter, BlockPos);
boolean isCollisionShapeFullBlock(BlockGetter, BlockPos); boolean isFaceSturdy(BlockGetter, BlockPos, Direction); isFaceSturdy(BlockGetter, BlockPos, Direction, SupportType);
boolean isRedstoneConductor(BlockGetter, BlockPos); boolean isSignalSource(); int getSignal(BlockGetter, BlockPos, Direction); int getDirectSignal(BlockGetter, BlockPos, Direction);
boolean hasAnalogOutputSignal(); int getAnalogOutputSignal(Level, BlockPos, Direction);
boolean canBeReplaced(BlockPlaceContext); canBeReplaced(Fluid); canBeReplaced(); boolean canSurvive(LevelReader, BlockPos);
boolean isSuffocating(BlockGetter, BlockPos); isViewBlocking(BlockGetter, BlockPos); isPathfindable(PathComputationType); isValidSpawn(BlockGetter, BlockPos, EntityType<?>);
BlockState rotate(Rotation) /*@Deprecated Neo -> rotate(LevelAccessor,BlockPos,Rotation) in IBlockStateExtension*/; BlockState mirror(Mirror);
FluidState getFluidState(); ItemStack getCloneItemStack(LevelReader, BlockPos, boolean includeData); boolean requiresCorrectToolForDrops(); List<ItemStack> getDrops(LootParams.Builder);
void onPlace(Level, BlockPos, BlockState oldState, boolean movedByPiston); void affectNeighborsAfterRemoval(ServerLevel, BlockPos, boolean movedByPiston);
void tick(ServerLevel, BlockPos, RandomSource); void randomTick(ServerLevel, BlockPos, RandomSource);
void entityInside(Level, BlockPos, Entity, InsideBlockEffectApplier, boolean isPrecise);
InteractionResult useItemOn(ItemStack, Level, Player, InteractionHand, BlockHitResult); InteractionResult useWithoutItem(Level, Player, BlockHitResult); void attack(Level, BlockPos, Player);
void handleNeighborChanged(Level, BlockPos, Block neighborBlock, @Nullable Orientation, boolean movedByPiston);   // wrapper for Block#neighborChanged (renamed)
void updateNeighbourShapes(LevelAccessor, BlockPos, int flags); updateNeighbourShapes(LevelAccessor, BlockPos, int flags, int recursionLeft); updateIndirectNeighbourShapes(...same...);
BlockState updateShape(LevelReader, ScheduledTickAccess, BlockPos, Direction, BlockPos neighborPos, BlockState neighborState, RandomSource);
boolean triggerEvent(Level, BlockPos, int, int); @Nullable MenuProvider getMenuProvider(Level, BlockPos);
<T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level, BlockEntityType<T>);
boolean is(TagKey<Block>, Predicate<BlockStateBase>);  // plus is(Block), is(TagKey<Block>), is(Holder<Block>), is(HolderSet<Block>), is(ResourceKey<Block>) from TypedInstance<Block> (V JD BlockState.html), see A5
void spawnAfterBreak(ServerLevel, BlockPos, ItemStack tool, boolean dropExperience); void onExplosionHit(ServerLevel, BlockPos, Explosion, BiConsumer<ItemStack, BlockPos>);
// StateHolder (U, unchanged): <T extends Comparable<T>> T getValue(Property<T>); <T,V extends T> BlockState setValue(Property<T>, V); boolean hasProperty(Property<?>); BlockState cycle(Property<T>); Optional<T> getOptionalValue(Property<T>); T getValueOrElse(Property<T>, T) (V CropBlock patch); Collection<Property<?>> getProperties()
```
NeoForge `IBlockStateExtension` (net.neoforged.neoforge.common.extensions): `getLightEmission(BlockGetter, BlockPos)`, `getSoundType(LevelReader, BlockPos, @Nullable Entity)`, `rotate(LevelAccessor, BlockPos, Rotation)`, `getFlammability/isFlammable/getFireSpreadSpeed(BlockGetter, BlockPos, Direction)`, `onCaughtFire(Level, BlockPos, @Nullable Direction, @Nullable LivingEntity)`, `canSustainPlant(...)`, `isFertile(BlockGetter, BlockPos)`, `canDropFromExplosion(...)`, `onBlockExploded(Level, BlockPos, Explosion)` -- V names via patches (BlockBehaviour/FireBlock/TntBlock/CropBlock); exact params U.

## A4. Block-side NeoForge hooks (`net.neoforged.neoforge.common.extensions.IBlockExtension`, Block implements it) -- V (https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/src/main/java/net/neoforged/neoforge/common/extensions/IBlockExtension.java)
```java
default int getLightEmission(BlockState state, BlockGetter level, BlockPos pos)
default float getFriction(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity)
default float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion)
default SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity)
default ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player)
default MapColor getMapColor(BlockState state, BlockGetter level, BlockPos pos, MapColor defaultColor)
default PushReaction getPistonPushReaction(BlockState state)           // return null => use Properties value
default boolean onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction direction, @Nullable LivingEntity igniter)
default void onBlockExploded(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion)
default boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid)
default boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player)
default int getExpDrop(BlockState state, LevelAccessor level, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool)
```
`IBlockStateExtension` (on BlockState; V names+params from JD BlockState.html inherited list): `getLightEmission(BlockGetter, BlockPos)`, `getSoundType(LevelReader, BlockPos, Entity)`, `getFriction(LevelReader, BlockPos, Entity)`, `getExplosionResistance(BlockGetter, BlockPos, Explosion)`, `rotate(LevelAccessor, BlockPos, Rotation)`, `getCloneItemStack(BlockPos, LevelReader, boolean, Player)` (NOTE arg order pos-first), `onCaughtFire(Level, BlockPos, Direction, LivingEntity)`, `isFlammable/getFlammability/getFireSpreadSpeed(BlockGetter, BlockPos, Direction)`, `canSustainPlant(BlockGetter, BlockPos, Direction, BlockState)` -> TriState, `isFertile(BlockGetter, BlockPos)`, `canHarvestBlock(BlockGetter, BlockPos, Player)`, `onDestroyedByPlayer(Level, BlockPos, Player, ItemStack, boolean, FluidState)`, `getExpDrop(LevelAccessor, BlockPos, BlockEntity, Entity, ItemStack)`, `onBlockExploded(ServerLevel, BlockPos, Explosion)`, `canDropFromExplosion(BlockGetter, BlockPos, Explosion)`, `onNeighborChange(LevelReader, BlockPos, BlockPos neighbor)`, `hasDynamicLightEmission()`, `getToolModifiedState(UseOnContext, ItemAbility, boolean simulate)`.

## A5. Properties / BlockStateProperties (`net.minecraft.world.level.block.state.properties.*`)
```java
// EnumProperty<T extends Enum<T> & StringRepresentable>  V (JD EnumProperty.html)
static <T> EnumProperty<T> create(String name, Class<T> clazz); create(String, Class<T>, Predicate<T> filter); @SafeVarargs create(String, Class<T>, T... values); create(String, Class<T>, List<T> values)
static BooleanProperty BooleanProperty.create(String name)             // U (unchanged)
static IntegerProperty IntegerProperty.create(String name, int min, int max)   // U (unchanged)
// DirectionProperty REMOVED (JD 404): use EnumProperty<Direction>.  V
// BlockStateProperties constants (V JD BlockStateProperties.html):
EnumProperty<Direction> FACING, HORIZONTAL_FACING, FACING_HOPPER; EnumProperty<Direction.Axis> AXIS, HORIZONTAL_AXIS;
BooleanProperty POWERED, LIT, WATERLOGGED, OPEN, ENABLED, TRIGGERED, ATTACHED; IntegerProperty LEVEL, AGE_1, AGE_2, AGE_3, AGE_4, AGE_5, AGE_7, AGE_15, AGE_25;
EnumProperty<Half> HALF; EnumProperty<DoubleBlockHalf> DOUBLE_BLOCK_HALF; EnumProperty<SlabType> SLAB_TYPE
```
StateHolder (V JD BlockState inherited): `<T extends Comparable<T>> T getValue(Property<T>)`, `<T extends Comparable<T>, V extends T> BlockState setValue(Property<T>, V)`, `trySetValue(Property<T>, V)`, `hasProperty(Property<?>)`, `cycle(Property<T>)`, `getOptionalValue(Property<T>)`, `getValueOrElse(Property<T>, T)`, `getProperties()`, `getValues()`.
TypedInstance<Block> (V JD): `boolean is(Block)`, `is(Holder<Block>)`, `is(HolderSet<Block>)`, `is(TagKey<Block>)`, `is(ResourceKey<Block>)`, `Stream<TagKey<Block>> tags()`, `Holder<Block> typeHolder()`.

## A6. BlockPlaceContext / UseOnContext (`net.minecraft.world.item.context`) -- V (JD BlockPlaceContext.html)
```java
BlockPlaceContext(Player player, InteractionHand hand, ItemStack itemInHand, BlockHitResult hitResult); BlockPlaceContext(UseOnContext); BlockPlaceContext(Level, @Nullable Player, InteractionHand, ItemStack, BlockHitResult)
static BlockPlaceContext at(BlockPlaceContext context, BlockPos pos, Direction direction)
BlockPos getClickedPos(); boolean canPlace(); boolean replacingClickedOnBlock(); Direction getNearestLookingDirection(); Direction getNearestLookingVerticalDirection(); Direction[] getNearestLookingDirections()
// inherited UseOnContext: Direction getClickedFace(); Vec3 getClickLocation(); InteractionHand getHand(); Direction getHorizontalDirection(); ItemStack getItemInHand(); Level getLevel(); @Nullable Player getPlayer(); float getRotation(); boolean isInside(); boolean isSecondaryUseActive()
// UseOnContext ctor (V BlockBehaviour patch): new UseOnContext(Level, Player, InteractionHand, ItemStack, BlockHitResult)
```
Misc: `RenderShape` = `net.minecraft.world.level.block.RenderShape { INVISIBLE, MODEL }` -- ENTITYBLOCK_ANIMATED REMOVED (V JD). `InsideBlockEffectApplier` = `net.minecraft.world.entity.InsideBlockEffectApplier` (V JD; `apply(InsideBlockEffectType)`, `runBefore/runAfter(InsideBlockEffectType, Consumer<Entity>)`, `NOOP`). `InteractionResult` = `net.minecraft.world.InteractionResult` (SUCCESS, CONSUME, PASS, FAIL, TRY_WITH_EMPTY_HAND, SUCCESS_SERVER - U values); `ItemInteractionResult` does NOT exist (JD 404, V). SoundType constants (`SoundType.STONE, WOOD, METAL, GLASS, GRASS, AMETHYST...`) and MapColor (`MapColor.STONE, COLOR_PURPLE, ...`) unchanged (U). `Blocks.AIR, Blocks.STONE, Blocks.SAND` etc. (V patches).

## B1. EntityBlock / BaseEntityBlock (`net.minecraft.world.level.block`)
```java
public interface EntityBlock {                                                   // V (JD EntityBlock.html)
    @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState);
    default <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type);
    default <T extends BlockEntity> @Nullable GameEventListener getListener(ServerLevel level, T blockEntity);
}
@FunctionalInterface public interface BlockEntityTicker<T extends BlockEntity> { void tick(Level level, BlockPos pos, BlockState state, T entity); }   // V (JD)  package net.minecraft.world.level.block.entity
public abstract class BaseEntityBlock extends Block implements EntityBlock { protected BaseEntityBlock(BlockBehaviour.Properties) }   // V exists (JD BaseEntityBlock.html; no public members)
protected abstract MapCodec<? extends BaseEntityBlock> codec();   // U (must override in BaseEntityBlock subclasses; simplest: `return simpleCodec(MyBlock::new);`)
protected static <E extends BlockEntity, A extends BlockEntity> @Nullable BlockEntityTicker<A> createTickerHelper(BlockEntityType<A> serverType, BlockEntityType<E> clientType, BlockEntityTicker<? super E> ticker)   // U (protected, hidden from JD; unchanged since 1.17)
```
Real 26.1 usage (V NeoForge test): `class TestBlock extends Block implements EntityBlock` with `public BlockEntity newBlockEntity(BlockPos pos, BlockState state)` and `public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return (beLevel, bePos, beState, be) -> ((TestBlockEntity) be).tick(); }` -- getRenderShape override not needed for plain Block (default MODEL). Recommended static ticker: `public static void tick(Level level, BlockPos pos, BlockState state, MyBE be)` and `getTicker(...) { return level.isClientSide() ? null : createTickerHelper(type, MY_TYPE.get(), MyBE::tick); }` (createTickerHelper U; or cast lambda as in test, V).

## B2. BlockEntityType (`net.minecraft.world.level.block.entity.BlockEntityType<T extends BlockEntity>`) -- V (JD BlockEntityType.html)
```java
public BlockEntityType(BlockEntityType.BlockEntitySupplier<? extends T> factory, Set<Block> validBlocks)
public BlockEntityType(BlockEntityType.BlockEntitySupplier<? extends T> factory, Set<Block> validBlocks, boolean onlyOpCanSetNbt)
public BlockEntityType(BlockEntityType.BlockEntitySupplier<? extends T> factory, Block... validBlocks)
public BlockEntityType(BlockEntityType.BlockEntitySupplier<? extends T> factory, boolean onlyOpCanSetNbt, Block... validBlocks)
// BlockEntitySupplier is the nested interface BlockEntityType.BlockEntitySupplier<T>: T create(BlockPos pos, BlockState state)  (V nesting; method U)
@Nullable T create(BlockPos, BlockState); Set<Block> getValidBlocks(); boolean isValid(BlockState); boolean onlyOpCanSetNbt(); @Nullable T getBlockEntity(BlockGetter, BlockPos); Holder.Reference<BlockEntityType<?>> builtInRegistryHolder()
// NOTE: BlockEntityType.Builder no longer exists in JD index (U); construct directly: new BlockEntityType<>(MyBE::new, MY_BLOCK.get())
```

## B3. BlockEntity (`net.minecraft.world.level.block.entity.BlockEntity`) -- V (JD BlockEntity.html) unless marked
```java
public BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState)
protected void saveAdditional(ValueOutput output);   // U (protected; NeoForge test/IBlockEntityExtension imply ValueOutput)
protected void loadAdditional(ValueInput input);     // U (protected)
public void setChanged(); public BlockPos getBlockPos(); public BlockState getBlockState(); public @Nullable Level getLevel(); public boolean hasLevel(); public void setLevel(Level);
public CompoundTag getUpdateTag(HolderLookup.Provider registries);
public @Nullable Packet<ClientGamePacketListener> getUpdatePacket();   // Packet = net.minecraft.network.protocol.Packet, ClientGamePacketListener = net.minecraft.network.protocol.game.ClientGamePacketListener; return ClientboundBlockEntityDataPacket.create(this) (net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket, U)
public final CompoundTag saveWithoutMetadata(HolderLookup.Provider); public final void saveWithoutMetadata(ValueOutput);
public final CompoundTag saveWithFullMetadata(HolderLookup.Provider); saveWithFullMetadata(ValueOutput); saveCustomOnly(HolderLookup.Provider); saveCustomOnly(ValueOutput); saveWithId(ValueOutput);
public final void loadWithComponents(ValueInput); public final void loadCustomOnly(ValueInput);
public boolean isRemoved(); public void setRemoved(); public void clearRemoved();
public void preRemoveSideEffects(BlockPos pos, BlockState state);   // replaces old Block#onRemove cleanup (drop inventory here)
public boolean triggerEvent(int id, int type); public BlockEntityType<?> getType(); public Holder<BlockEntityType<?>> typeHolder();
public boolean isValidBlockState(BlockState); public void setBlockState(BlockState); public DataComponentMap components(); protected void applyImplicitComponents(...) /*U*/; public void applyComponentsFromItemStack(ItemStack);
// NeoForge IBlockEntityExtension (V raw source): default void onDataPacket(Connection net, ValueInput valueInput); default void handleUpdateTag(ValueInput input); CompoundTag getPersistentData(); default void onLoad(); default void onChunkUnloaded(); default void requestModelDataUpdate(); default ModelData getModelData(); default void invalidateCapabilities(); default void applyStructureRotation(Mirror, Rotation)
// attachments (V JD): <T> T setData(AttachmentType<T>, T); removeData(AttachmentType<T>); syncData(AttachmentType<?>); getData/hasData/getExistingData via IAttachmentHolder (U)
// Level: void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags)  U (unchanged) -> level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL)
```

## B4. ValueOutput / ValueInput (`net.minecraft.world.level.storage`) -- V (JD)
```java
// ValueOutput
<T> void store(String name, Codec<T> codec, T value); <T> void storeNullable(String name, Codec<T> codec, @Nullable T value); @Deprecated <T> void store(MapCodec<T>, T);
void putBoolean/putByte/putShort/putInt/putLong/putFloat/putDouble(String, x); void putString(String, String); void putIntArray(String, int[]);
ValueOutput child(String name); ValueOutput.ValueOutputList childrenList(String name); <T> ValueOutput.TypedOutputList<T> list(String name, Codec<T> codec); void discard(String); boolean isEmpty();
// NeoForge ValueOutputExtension: putChild(String, ValueIOSerializable); store(CompoundTag)
// ValueInput
<T> Optional<T> read(String name, Codec<T> codec); @Deprecated <T> Optional<T> read(MapCodec<T>);
Optional<ValueInput> child(String); ValueInput childOrEmpty(String); Optional<ValueInput.ValueInputList> childrenList(String); ValueInput.ValueInputList childrenListOrEmpty(String);
<T> Optional<ValueInput.TypedInputList<T>> list(String, Codec<T>); <T> ValueInput.TypedInputList<T> listOrEmpty(String, Codec<T>);
boolean getBooleanOr(String, boolean); byte getByteOr(String, byte); int getShortOr(String, short); Optional<Integer> getInt(String); int getIntOr(String, int); Optional<Long> getLong(String); long getLongOr(String, long); float getFloatOr(String, float); double getDoubleOr(String, double); Optional<String> getString(String); String getStringOr(String, String); Optional<int[]> getIntArray(String); @Deprecated HolderLookup.Provider lookup();
// NeoForge ValueInputExtension: keySet(); rawChildOrEmpty(String); readChild(String, ValueIOSerializable)
// Bridge (V JD TagValueOutput.html): TagValueOutput.createWithContext(ProblemReporter problemReporter, HolderLookup.Provider provider); createWithoutContext(ProblemReporter); CompoundTag buildResult()
//   ProblemReporter = net.minecraft.util.ProblemReporter (use ProblemReporter.DISCARDING - U). TagValueInput.create(ProblemReporter, HolderLookup.Provider, CompoundTag) - U.
```

## B5. ItemStack codecs & saving stacks (`net.minecraft.world.item.ItemStack`) -- fields V (JD ItemStack.html Field index)
Existing: `MAP_CODEC`, `CODEC`, `OPTIONAL_CODEC`, `STREAM_CODEC`, `OPTIONAL_STREAM_CODEC`, `OPTIONAL_UNTRUSTED_STREAM_CODEC`, `OPTIONAL_LIST_STREAM_CODEC`, `EMPTY`. NOT present: `SINGLE_ITEM_CODEC`, `STRICT_CODEC` (absent from index, V). `ItemStack` implements `net.minecraft.world.item.ItemInstance` (V primer) -> `typeHolder()`, `getMaxStackSize()`.
```java
// single slot (CODEC rejects empty stacks -> guard; U pattern, consistent with vanilla 1.21.5+):
if (!stack.isEmpty()) output.store("Item", ItemStack.CODEC, stack);            // or output.storeNullable("Item", ItemStack.OPTIONAL_CODEC, stack)
this.stack = input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
// inventory list (V NeoForge ItemStackHandler 26.1.x + JD ContainerHelper/ItemStackWithSlot):
ContainerHelper.saveAllItems(ValueOutput output, NonNullList<ItemStack> itemStacks); saveAllItems(ValueOutput, NonNullList<ItemStack>, boolean alsoWhenEmpty); loadAllItems(ValueInput input, NonNullList<ItemStack> itemStacks)   // net.minecraft.world.ContainerHelper, key "Items" (TAG_ITEMS)
// manual: ValueOutput.TypedOutputList<ItemStackWithSlot> l = output.list("Items", ItemStackWithSlot.CODEC); l.add(new ItemStackWithSlot(i, stack)); input.listOrEmpty("Items", ItemStackWithSlot.CODEC).forEach(s -> { if (s.isValidInContainer(size)) stacks.set(s.slot(), s.stack()); });   // net.minecraft.world.ItemStackWithSlot(int slot, ItemStack stack)
NonNullList<ItemStack> items = NonNullList.withSize(int size, ItemStack.EMPTY);   // net.minecraft.core.NonNullList (V ItemStackHandler usage) - build in ctor, not static init
```

---

# C/D. Item, ItemStack, DataComponents, Component, Player, Inventory — MC 26.1 / NeoForge 26.1.2.76

Legend: **V** = VERIFIED, **U** = UNVERIFIED (best guess, reason given). JD = `https://lexxie.dev/neoforge/26.1/` + path. P = `https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/patches/` + path. NF = `https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/src/main/java/` + path.
Everything in a code block below is V for the class named in the heading unless a line says `// U`.

## C1. `net.minecraft.world.item.Item`  — V (JD net/minecraft/world/item/Item.html ; P net/minecraft/world/item/Item.java.patch)
Item implements `ItemLike, FeatureElement, IItemExtension(Neo)`. `public static final int DEFAULT_MAX_STACK_SIZE = 64`.
```java
public Item(Item.Properties properties)
public InteractionResult use(Level level, Player player, InteractionHand hand)      // net.minecraft.world.InteractionResult (sealed interface), net.minecraft.world.InteractionHand (enum)
public InteractionResult useOn(UseOnContext context)                                // net.minecraft.world.item.context.UseOnContext
public void inventoryTick(ItemStack itemStack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot)   // ServerLevel = net.minecraft.server.level.ServerLevel; EquipmentSlot = net.minecraft.world.entity.EquipmentSlot
public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag)
public Component getName(ItemStack itemStack);  public final String getDescriptionId()
public boolean isFoil(ItemStack itemStack)
public int getDefaultMaxStackSize()          // NO vanilla Item#getMaxStackSize(); NeoForge IItemExtension adds getMaxStackSize(ItemStack)
public void onCraftedBy(ItemStack itemStack, Player player);  public void onCraftedPostProcess(ItemStack itemStack, Level level)
@Deprecated public final @Nullable ItemStackTemplate getCraftingRemainder()   // vanilla; NF prefers IItemExtension#getCraftingRemainder(ItemInstance). No hasCraftingRemainingItem() any more.
public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity entity)
public int getUseDuration(ItemStack itemStack, LivingEntity user)
public ItemUseAnimation getUseAnimation(ItemStack itemStack)                   // net.minecraft.world.item.ItemUseAnimation (enum)
public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int ticksRemaining)
public boolean releaseUsing(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime)
public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity target, InteractionHand type)
public void hurtEnemy(ItemStack itemStack, LivingEntity mob, LivingEntity attacker);  public void postHurtEnemy(ItemStack, LivingEntity, LivingEntity)
public boolean mineBlock(ItemStack itemStack, Level level, BlockState state, BlockPos pos, LivingEntity owner)
public boolean canDestroyBlock(ItemStack itemStack, BlockState state, Level level, BlockPos pos, LivingEntity user)   // RENAMED from canAttackBlock
public boolean isCorrectToolForDrops(ItemStack itemStack, BlockState state);  public float getDestroySpeed(ItemStack itemStack, BlockState state)
public int getBarWidth(ItemStack stack); public int getBarColor(ItemStack stack); public boolean isBarVisible(ItemStack stack)
public ItemStack getDefaultInstance();  public DataComponentMap components();  public boolean useOnRelease(ItemStack itemStack)
public static BlockHitResult getPlayerPOVHitResult(Level level, Player player, ClipContext.Fluid fluid)
public boolean isCombineRepairable(ItemStack stack)   // Neo
```
- NOT on Item: `isEnchantable`, `getRarity`, `getMaxStackSize()`, `canAttackBlock`, `hasCraftingRemainingItem` (use ItemStack / IItemExtension). V
- NF `net.neoforged.neoforge.common.extensions.IItemExtension` (overridable defaults): `getCraftingRemainder(ItemInstance)`, `getMaxStackSize(ItemStack)`, `getMaxDamage(ItemStack)`, `isDamageable(ItemStack)`, `getBurnTime(ItemStack, RecipeType<?>, FuelValues)`, `canPerformAction(ItemInstance, ItemAbility)`, `onItemUseFirst(ItemStack, UseOnContext)`, `onEntitySwing(ItemStack, LivingEntity, InteractionHand)`, `onDroppedByPlayer(ItemStack, Player)`, `onStopUsing(ItemStack, LivingEntity, int)`, `canEquip(ItemStack, EquipmentSlot, LivingEntity)`, `getEquipmentSlot(ItemStack)`, `<T extends LivingEntity> damageItem(ItemStack, int, T, Consumer<Item>)`, `onDestroyed(ItemEntity, DamageSource)`, `onLeftClickEntity(ItemStack, Player, Entity)`, `getEntityLifespan(ItemStack, Level)`. V (JD Item.html inherited list)

### `net.minecraft.world.item.Item.Properties` — V (JD net/minecraft/world/item/Item.Properties.html)
```java
public Properties()
public Item.Properties stacksTo(int max); durability(int maxDamage); rarity(Rarity rarity); fireResistant()
public Item.Properties craftRemainder(Item craftingRemainingItem); craftRemainder(ItemStackTemplate craftingRemainingItem)
public <T> Item.Properties component(DataComponentType<T> type, T value)
public <T> Item.Properties delayedComponent(DataComponentType<T> type, DataComponentInitializers.SingleComponentInitializer<T> initializer)   // net.minecraft.core.component.DataComponentInitializers
public <T> Item.Properties delayedHolderComponent(DataComponentType<Holder<T>> type, ResourceKey<T> valueKey)
public Item.Properties setId(ResourceKey<Item> id)
public Item.Properties useItemDescriptionPrefix(); useBlockDescriptionPrefix(); overrideDescription(String descriptionId)
public Item.Properties food(FoodProperties foodProperties); food(FoodProperties foodProperties, Consumable consumable)   // net.minecraft.world.food.FoodProperties ; net.minecraft.world.item.component.Consumable
public Item.Properties usingConvertsTo(Item item); useCooldown(float seconds)
public Item.Properties requiredFeatures(FeatureFlag... flags); requiredFeatures(FeatureFlagSet flags)   // net.minecraft.world.flag.*
public Item.Properties attributes(ItemAttributeModifiers attributes)   // net.minecraft.world.item.component.ItemAttributeModifiers
public Item.Properties repairable(Item repairItem); repairable(TagKey<Item> repairItems); enchantable(int value)
public Item.Properties equippable(EquipmentSlot slot); equippableUnswappable(EquipmentSlot slot)
public Item.Properties tool(ToolMaterial material, TagKey<Block> minesEfficiently, float attackDamageBaseline, float attackSpeedBaseline, float disableBlockingSeconds)  // net.minecraft.world.item.ToolMaterial
public Item.Properties pickaxe|axe|hoe|shovel|sword(ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline)
public Item.Properties humanoidArmor(ArmorMaterial material, ArmorType type)   // net.minecraft.world.item.equipment.ArmorMaterial / ArmorType
public Item.Properties spawnEgg(EntityType<?> type); jukeboxPlayable(ResourceKey<JukeboxSong> song); trimMaterial(ResourceKey<TrimMaterial> material)
public Item.Properties setNoCombineRepair()   // Neo-added
public Identifier effectiveModel()
// Neo IItemPropertiesExtensions: <T> Item.Properties component(Supplier<? extends DataComponentType<T>>, T)
```

### `Item.TooltipContext` (interface) — V (P Item.java.patch)
```java
static Item.TooltipContext of(@Nullable Level level);  static Item.TooltipContext of(@Nullable Level level, @Nullable Player player) /*Neo*/;  Item.TooltipContext EMPTY
HolderLookup.Provider registries(); float tickRate(); boolean isPeaceful(); @Nullable Level level() /*Neo*/; @Nullable Player player() /*Neo*/
```
- `net.minecraft.world.item.component.TooltipDisplay` — `record TooltipDisplay(boolean hideTooltip, SequencedSet<DataComponentType<?>> hiddenComponents)`; `TooltipDisplay.DEFAULT`; `boolean shows(DataComponentType<?> component)`; `TooltipDisplay withHidden(DataComponentType<?>, boolean)`; `CODEC`, `STREAM_CODEC`. V (JD)
- `net.minecraft.world.item.TooltipFlag` — interface: `boolean isAdvanced(); boolean isCreative();` Neo defaults `hasShiftDown()/hasControlDown()/hasAltDown()`; constants `TooltipFlag.NORMAL`, `TooltipFlag.ADVANCED` (type `TooltipFlag.Default`). V (JD)
- `net.minecraft.world.item.BlockItem`: `public BlockItem(Block block, Item.Properties properties)`; `Block getBlock()`; `InteractionResult place(BlockPlaceContext)`. V (JD)
- `net.minecraft.world.item.ItemStackTemplate` — `record ItemStackTemplate(Holder<Item> item, int count, DataComponentPatch components)` implements ItemInstance; ctors `(Item)`, `(Item,int)`, `(Item,DataComponentPatch)`, `(Item,int,DataComponentPatch)` + Holder variants; `ItemStack create()`; `ItemStack apply(DataComponentPatch)`; `withCount(int)`; `static fromNonEmptyStack(ItemStack)`; `CODEC`, `MAP_CODEC`, `STREAM_CODEC`. V (JD)

## C2. `net.minecraft.world.item.ItemStack` — V (JD net/minecraft/world/item/ItemStack.html)
`public final class ItemStack implements ItemInstance, DataComponentHolder, MutableDataComponentHolder(Neo), IItemStackExtension(Neo)`.
```java
public static final MapCodec<ItemStack> MAP_CODEC;  Codec<ItemStack> CODEC;  Codec<ItemStack> OPTIONAL_CODEC        // SINGLE_ITEM_CODEC / STRICT_CODEC gone
public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC, OPTIONAL_STREAM_CODEC, OPTIONAL_UNTRUSTED_STREAM_CODEC;  StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC
public static final ItemStack EMPTY
public ItemStack(ItemLike item); ItemStack(ItemLike item, int count); ItemStack(Holder<Item> item); ItemStack(Holder<Item> item, int count); ItemStack(Holder<Item>, int, DataComponentPatch)
public int getCount(); int count(); void setCount(int count); void grow(int amount); void shrink(int amount); void limitSize(int)
public ItemStack copy(); ItemStack copyWithCount(int count); ItemStack split(int amount); ItemStack copyAndClear(); ItemStack transmuteCopy(ItemLike); transmuteCopy(ItemLike, int)
public boolean isEmpty(); Item getItem(); Holder<Item> typeHolder()      // getItemHolder() REMOVED
public boolean is(Item); is(TagKey<Item>); is(Holder<Item>); is(HolderSet<Item>); is(ResourceKey<Item>); is(Predicate<Holder<Item>>)   // from TypedInstance<Item>
public <T> @Nullable T get(DataComponentType<? extends T>); <T> T getOrDefault(DataComponentType<? extends T>, T); boolean has(DataComponentType<?>)   // DataComponentHolder; Neo adds Supplier<DataComponentType> overloads
public <T> @Nullable T set(DataComponentType<T> type, @Nullable T value);  <T> @Nullable T remove(DataComponentType<? extends T> type)
public <T> @Nullable T update(DataComponentType<T> type, T defaultValue, UnaryOperator<T> function)
public <T,U> @Nullable T update(DataComponentType<T> type, T defaultValue, U value, BiFunction<T,U,T> combiner)
public DataComponentMap getComponents(); DataComponentPatch getComponentsPatch(); DataComponentMap getPrototype(); void applyComponents(DataComponentPatch); applyComponents(DataComponentMap)
public Component getHoverName(); Component getDisplayName(); @Nullable Component getCustomName(); Component getItemName(); Component getStyledHoverName()
public int getDamageValue(); void setDamageValue(int); boolean isDamageableItem(); boolean isDamaged(); int getMaxDamage(); boolean isBroken(); boolean nextDamageWillBreak()
public void hurtAndBreak(int amount, LivingEntity owner, EquipmentSlot slot);  hurtAndBreak(int amount, LivingEntity owner, InteractionHand hand)
public void hurtAndBreak(int amount, ServerLevel level, @Nullable ServerPlayer player, Consumer<Item> onBreak);  hurtAndBreak(int, ServerLevel, @Nullable LivingEntity, Consumer<Item>)
public void hurtWithoutBreaking(int, Player);  void hurtAndConvertOnBreak(int, ItemLike, LivingEntity, EquipmentSlot)
public int getMaxStackSize(); boolean isStackable()
public static boolean isSameItem(ItemStack a, ItemStack b); isSameItemSameComponents(ItemStack a, ItemStack b); matches(ItemStack, ItemStack)   // + (ItemStack, ItemStackTemplate) overloads
public static int hashItemAndComponents(@Nullable ItemStack)
public void consume(int amount, @Nullable LivingEntity owner);  ItemStack consumeAndReturn(int, @Nullable LivingEntity)
public boolean isEnchanted(); boolean isEnchantable(); void enchant(Holder<Enchantment>, int); ItemEnchantments getEnchantments(); boolean hasFoil(); Rarity getRarity()
public List<Component> getTooltipLines(Item.TooltipContext context, @Nullable Player player, TooltipFlag tooltipFlag)
public ItemUseAnimation getUseAnimation(); int getUseDuration(LivingEntity user); ItemStack finishUsingItem(Level, LivingEntity); boolean releaseUsing(Level, LivingEntity, int)
public InteractionResult use(Level, Player, InteractionHand); useOn(UseOnContext); onItemUseFirst(UseOnContext) /*Neo*/
public void inventoryTick(Level level, Entity owner, @Nullable EquipmentSlot slot);  void onCraftedBy(Player player, int craftCount);  void onCraftedBySystem(Level)
public boolean mineBlock(Level, BlockState, BlockPos, Player); boolean isCorrectToolForDrops(BlockState); float getDestroySpeed(BlockState); boolean canDestroyBlock(BlockState, Level, BlockPos, Player)
public void hurtEnemy(LivingEntity mob, LivingEntity attacker); void postHurtEnemy(LivingEntity, LivingEntity)
public boolean isValidRepairItem(ItemStack); boolean canBeHurtBy(DamageSource); boolean isItemEnabled(FeatureFlagSet)
// Neo ItemInstanceExtension (on ItemStack AND ItemStackTemplate):
public default @Nullable ItemStackTemplate getCraftingRemainder();  int getEnchantmentLevel(Holder<Enchantment>);  ItemEnchantments getTagEnchantments();  boolean canPerformAction(ItemAbility)
```
(NF net/neoforged/neoforge/common/extensions/ItemInstanceExtension.java — V.) Note the 26.1 remainder is an `ItemStackTemplate`, not an `ItemStack`; call `.create()` to get a stack.

## C3. `net.minecraft.core.component.DataComponents` — V (JD net/minecraft/core/component/DataComponents.html)
```java
DataComponentType<Component> CUSTOM_NAME, ITEM_NAME;  DataComponentType<Boolean> ENCHANTMENT_GLINT_OVERRIDE;  DataComponentType<Integer> MAX_STACK_SIZE, DAMAGE, MAX_DAMAGE
DataComponentType<Unit> UNBREAKABLE, GLIDER;               // net.minecraft.util.Unit (enum) — NOT Boolean/Unbreakable record
DataComponentType<CustomData> CUSTOM_DATA;  DataComponentType<ItemLore> LORE;  DataComponentType<Rarity> RARITY;  DataComponentType<FoodProperties> FOOD;  DataComponentType<Consumable> CONSUMABLE
DataComponentType<TooltipDisplay> TOOLTIP_DISPLAY;  DataComponentType<Identifier> TOOLTIP_STYLE, ITEM_MODEL;  DataComponentType<ItemEnchantments> ENCHANTMENTS   // net.minecraft.world.item.enchantment.ItemEnchantments
DataComponentType<DyedItemColor> DYED_COLOR;  DataComponentType<DyeColor> DYE;  DataComponentType<CustomModelData> CUSTOM_MODEL_DATA;  DataComponentType<Tool> TOOL;  DataComponentType<Equippable> EQUIPPABLE  // net.minecraft.world.item.equipment.Equippable
DataComponentType<UseCooldown> USE_COOLDOWN;  DataComponentType<UseRemainder> USE_REMAINDER;  DataComponentType<DamageResistant> DAMAGE_RESISTANT;  DataComponentType<Repairable> REPAIRABLE  // net.minecraft.world.item.enchantment.Repairable
DataComponentType<WrittenBookContent> WRITTEN_BOOK_CONTENT;  DataComponentType<WritableBookContent> WRITABLE_BOOK_CONTENT;  DataComponentType<ItemContainerContents> CONTAINER
DataComponentType<MapItemColor> MAP_COLOR;  DataComponentType<MapId> MAP_ID;  DataComponentType<ItemAttributeModifiers> ATTRIBUTE_MODIFIERS
```
Value classes live in `net.minecraft.world.item.component` unless noted (Rarity/DyeColor: `net.minecraft.world.item`; FoodProperties: `net.minecraft.world.food`).
- `CustomData` (final class) — V: `static CustomData EMPTY; Codec<CustomData> CODEC; static CustomData of(CompoundTag tag); CompoundTag copyTag(); boolean contains(String key); boolean isEmpty(); CustomData update(Consumer<CompoundTag>); boolean matchedBy(CompoundTag); static void update(DataComponentType<CustomData>, ItemStack, Consumer<CompoundTag>); static void set(DataComponentType<CustomData>, ItemStack, CompoundTag)`. **`getUnsafe()` and `read(Codec)` are NOT public in 26.1** (absent from public javadoc).
- `ItemLore` — V: `record ItemLore(List<Component> lines, List<Component> styledLines)`; `ItemLore(List<Component> lines)`; `ItemLore.EMPTY`; `ItemLore withLineAdded(Component)`; `MAX_LINES`.
- `CustomModelData` — V: `record CustomModelData(List<Float> floats, List<Boolean> flags, List<String> strings, List<Integer> colors)`; `EMPTY`; `@Nullable Float getFloat(int)`, `getBoolean(int)`, `getString(int)`, `getColor(int)`.
- `ItemContainerContents` — V: `static fromItems(List<ItemStack>)`; `EMPTY`; `void copyInto(NonNullList<ItemStack>)`; `Stream<ItemStack> allItemsCopyStream()`, `nonEmptyItemCopyStream()`; `Iterable<ItemStackTemplate> nonEmptyItems()`; `ItemStack copyOne()`; Neo `int getSlots()`, `ItemStack getStackInSlot(int)`. (No plain `stream()`.)

## C4. Text — `net.minecraft.network.chat` — V (JD Component.html, MutableComponent.html, Style.html, ComponentSerialization.html)
```java
// Component (interface)
static MutableComponent literal(String text); translatable(String key); translatable(String key, Object... args); translatableWithFallback(String key, @Nullable String fallback[, Object...]); empty(); keybind(String)
static Component nullToEmpty(@Nullable String text);  static Component translationArg(Identifier|UUID|Date|URI|ChunkPos|Message)
default String getString(); String getString(int limit); default MutableComponent copy(); plainCopy(); Style getStyle(); List<Component> getSiblings(); List<Component> toFlatList()
// MutableComponent (final class)
public MutableComponent append(String text); append(Component component); setStyle(Style); withStyle(Style patch); withStyle(ChatFormatting format); withStyle(ChatFormatting... formats); withStyle(UnaryOperator<Style>); withColor(int color); withoutShadow()
// Style (final class)
public static final Style EMPTY
public Style withColor(@Nullable TextColor); withColor(@Nullable ChatFormatting); withColor(int color); withBold(@Nullable Boolean); withItalic(@Nullable Boolean); withUnderlined(@Nullable Boolean); withStrikethrough(@Nullable Boolean); withObfuscated(@Nullable Boolean)
public Style withClickEvent(@Nullable ClickEvent); withHoverEvent(@Nullable HoverEvent); withInsertion(@Nullable String); withFont(@Nullable FontDescription); withShadowColor(int); applyFormat(ChatFormatting); applyFormats(ChatFormatting...)
public @Nullable TextColor getColor(); boolean isBold(); isItalic(); ...
// ComponentSerialization
public static final Codec<Component> CODEC;  StreamCodec<RegistryFriendlyByteBuf, Component> STREAM_CODEC, TRUSTED_STREAM_CODEC;  StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPTIONAL_STREAM_CODEC, TRUSTED_OPTIONAL_STREAM_CODEC;  StreamCodec<ByteBuf, Component> TRUSTED_CONTEXT_FREE_STREAM_CODEC
```
- `net.minecraft.ChatFormatting` (enum): `BLACK, DARK_BLUE, ..., GOLD, GRAY, DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE, OBFUSCATED, BOLD, STRIKETHROUGH, UNDERLINE, ITALIC, RESET`; `@Nullable Integer getColor()`; `boolean isColor()`; `char getChar()`. U (not fetched; referenced as `net.minecraft.ChatFormatting` enum by MutableComponent/Style javadoc — package V, members unchanged since 1.16).

## D1. `net.minecraft.world.entity.player.Inventory` — V (JD net/minecraft/world/entity/player/Inventory.html)
```java
public static final int INVENTORY_SIZE, SELECTION_SIZE, SLOT_OFFHAND, SLOT_BODY_ARMOR, SLOT_SADDLE, NOT_FOUND_INDEX, POP_TIME_DURATION;  public final Player player
public boolean add(ItemStack itemStack); boolean add(int slot, ItemStack itemStack)
public ItemStack getSelectedItem(); ItemStack setSelectedItem(ItemStack); int getSelectedSlot(); void setSelectedSlot(int selected); static int getSelectionSize(); static boolean isHotbarSlot(int)   // getSelected() RENAMED -> getSelectedItem()
public ItemStack getItem(int slot); void setItem(int slot, ItemStack itemStack); int getContainerSize(); boolean isEmpty(); void clearContent()
public ItemStack removeItem(int slot, int count); void removeItem(ItemStack itemStack); ItemStack removeItemNoUpdate(int slot); ItemStack removeFromSelected(boolean all)
public NonNullList<ItemStack> getNonEquipmentItems()        // replaces the old public `items` field (36 main+hotbar slots)
public boolean contains(ItemStack searchStack); contains(TagKey<Item> tag); contains(Predicate<ItemStack>)
public int findSlotMatchingItem(ItemStack); int getFreeSlot(); int getSlotWithRemainingSpace(ItemStack); int getSuitableHotbarSlot()
public void placeItemBackInInventory(ItemStack itemStack); placeItemBackInInventory(ItemStack itemStack, boolean shouldSendSetSlotPacket)   // "give or drop" helper
public void dropAll(); void setChanged(); int getTimesChanged(); void tick(); Component getName()
public int clearOrCountMatchingItems(Predicate<ItemStack>, int amountToRemove, Container craftSlots)
```

## D2. `net.minecraft.world.entity.player.Player` — V (JD net/minecraft/world/entity/player/Player.html). Hierarchy: `Player extends Avatar extends LivingEntity` (new `net.minecraft.world.entity.Avatar`).
```java
public Inventory getInventory(); Abilities getAbilities(); ItemCooldowns getCooldowns(); FoodData getFoodData()   // net.minecraft.world.item.ItemCooldowns ; net.minecraft.world.food.FoodData
public boolean addItem(ItemStack itemStack)
public @Nullable ItemEntity drop(ItemStack itemStack, boolean thrownFromHand)       // net.minecraft.world.entity.item.ItemEntity ; 3-arg version is on ServerPlayer only (see D3)
public OptionalInt openMenu(@Nullable MenuProvider provider);  void closeContainer();  public AbstractContainerMenu containerMenu; public final InventoryMenu inventoryMenu
public void sendSystemMessage(Component message);  public void sendOverlayMessage(Component message)   // displayClientMessage(Component, boolean) REMOVED -> sendOverlayMessage for action bar
public void awardStat(Stat<?> stat); awardStat(Stat<?> stat, int count); awardStat(Identifier location); awardStat(Identifier location, int count)   // net.minecraft.stats.Stat ; net.minecraft.resources.Identifier
public void giveExperiencePoints(int i); void giveExperienceLevels(int); int getXpNeededForNextLevel(); public int experienceLevel, totalExperience; public float experienceProgress
public void causeFoodExhaustion(float amount)
public Component getName(); String getPlainTextName(); Component getDisplayName(); GameProfile getGameProfile(); NameAndId nameAndId(); ResolvableProfile getProfile()   // com.mojang.authlib.GameProfile ; net.minecraft.server.players.NameAndId
public boolean isCreative(); boolean isSpectator(); boolean hasInfiniteMaterials(); boolean isLocalPlayer(); abstract @Nullable GameType gameMode()   // net.minecraft.world.level.GameType
public float getDestroySpeed(BlockState state, @Nullable BlockPos pos);  boolean hasCorrectToolForDrops(BlockState state, Level level, BlockPos pos)   // 1-arg versions @Deprecated
public float getAttackStrengthScale(float a); void resetAttackStrengthTicker()
public int getSleepTimer(); Optional<GlobalPos> getLastDeathLocation(); void setLastDeathLocation(Optional<GlobalPos>)
public PermissionSet permissions(); boolean mayBuild(); boolean mayUseItemAt(BlockPos, Direction, ItemStack)
public boolean isSecondaryUseActive(); double blockInteractionRange(); double entityInteractionRange()
// Neo IPlayerExtension (NF .../common/extensions/IPlayerExtension.java — V):
default OptionalInt openMenu(@Nullable MenuProvider menuProvider, BlockPos pos);  default OptionalInt openMenu(@Nullable MenuProvider, @Nullable Consumer<RegistryFriendlyByteBuf> extraDataWriter);  default boolean mayFly();  default boolean isFakePlayer()
```
- `Abilities` (`net.minecraft.world.entity.player.Abilities`) — V: public fields `invulnerable, flying, mayfly (@Deprecated by Neo -> use player.mayFly()), instabuild, mayBuild`; `getFlyingSpeed()/setFlyingSpeed(float)/getWalkingSpeed()/setWalkingSpeed(float)`.
- `GameType` (`net.minecraft.world.level.GameType`, enum) — V: `SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR`; `DEFAULT_MODE`; `isCreative(); isSurvival(); isBlockPlacingRestricted(); getName(); getId(); static byName(String); byId(int)`.
- `ItemCooldowns` — V: `void addCooldown(ItemStack item, int time); addCooldown(Identifier cooldownGroup, int time); boolean isOnCooldown(ItemStack); float getCooldownPercent(ItemStack, float); Identifier getCooldownGroup(ItemStack); removeCooldown(Identifier)`.
- Inherited from `LivingEntity`/`Entity` (not on the Player page; **U**, standard since 1.20 and still referenced by Neo javadoc): `ItemStack getItemInHand(InteractionHand); void setItemInHand(InteractionHand, ItemStack); ItemStack getMainHandItem(); getOffhandItem(); getItemBySlot(EquipmentSlot); setItemSlot(EquipmentSlot, ItemStack)`; `Level level()`; `BlockPos blockPosition(); Vec3 position(); double getX()/getY()/getZ(); Vec3 getEyePosition(); double getEyeY(); Vec3 getLookAngle(); Vec3 getViewVector(float); float getYRot()/getXRot()/getYHeadRot(); boolean isShiftKeyDown(); boolean isCrouching(); UUID getUUID(); boolean isSleeping(); void swing(InteractionHand); @Nullable MinecraftServer getServer()`.

## D3. `net.minecraft.server.level.ServerPlayer` — V (JD net/minecraft/server/level/ServerPlayer.html)
```java
public ServerGamePacketListenerImpl connection;   // net.minecraft.server.network.ServerGamePacketListenerImpl ; connection.send(Packet<?>)  (send() U: not on fetched page, unchanged API)
public final ServerPlayerGameMode gameMode;  public boolean seenCredits;  public boolean wonGame
public ServerLevel level()                      // covariant override; serverLevel() REMOVED
public GameType gameMode();  public boolean setGameMode(GameType mode)   // replaces gameMode.getGameModeForPlayer() (still exists on ServerPlayerGameMode — U)
public OptionalInt openMenu(@Nullable MenuProvider provider);  OptionalInt openMenu(@Nullable MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extraDataWriter)
public ItemEntity drop(ItemStack itemStack, boolean randomly, boolean thrownFromHand);  void drop(boolean all)
public void sendSystemMessage(Component message);  sendSystemMessage(Component message, boolean overlay);  sendOverlayMessage(Component message)
public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<Relative> relatives, float yRot, float xRot, boolean setCamera);  void teleportTo(double x, double y, double z)   // net.minecraft.world.entity.Relative
public @Nullable ServerPlayer.RespawnConfig getRespawnConfig();  void setRespawnPosition(@Nullable ServerPlayer.RespawnConfig respawnConfig, boolean showMessage);  void copyRespawnPosition(ServerPlayer)
public record ServerPlayer.RespawnConfig(LevelData.RespawnData respawnData, boolean forced) { static ResourceKey<Level> getDimensionOrDefault(@Nullable RespawnConfig); boolean isSamePosition(@Nullable RespawnConfig); Codec CODEC }   // net.minecraft.world.level.storage.LevelData.RespawnData
public boolean hasDisconnected();  void disconnect();  ServerStatsCounter getStats();  PlayerAdvancements getAdvancements();  void swing(InteractionHand hand)
public void awardStat(Stat<?> stat, int count);  void giveExperiencePoints(int);  void setExperiencePoints(int);  void setExperienceLevels(int)
public CommandSourceStack createCommandSourceStack();  void closeContainer();  void doCloseContainer();  boolean mayInteract(ServerLevel, BlockPos);  String getIpAddress();  ClientInformation clientInformation()
```
- `getLastDeathLocation()` lives on Player (D2). `getServer()` is inherited from Entity (U).

## D4. Giving items to a player in 26.1 (NeoForge)
- `net.neoforged.neoforge.items.ItemHandlerHelper` **still exists but is `@Deprecated(forRemoval)`** (whole `net.neoforged.neoforge.items` package is deprecated in favour of `net.neoforged.neoforge.transfer.*`). V (JD net/neoforged/neoforge/items/package-summary.html)
- Vanilla way (preferred): `player.getInventory().placeItemBackInInventory(stack)` (adds, and drops the remainder), or `if (!player.addItem(stack)) player.drop(stack, false);`. V
- New transfer API: `net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper` (ResourceHandler<ItemResource> over Inventory) + `net.neoforged.neoforge.transfer.item.ItemUtil`: `static ItemStack insertItemReturnRemaining(ResourceHandler<ItemResource> handler, ItemStack stack, boolean simulate, @Nullable TransactionContext transaction)`; `static ItemStack getStack(ResourceHandler<ItemResource>, int index)`; `ItemResource.of(ItemStack)`. V (JD ItemUtil.html, transfer/item package). PlayerInventoryWrapper factory/ctor signature U (page not fetched).

---

# E/F — Level, ServerLevel, Chunks, Entity, LivingEntity (MC 26.1 / NeoForge 26.1.2.76)

Legend: `V` = VERIFIED against https://lexxie.dev/neoforge/26.1/... javadoc (URL suffix given), `U` = UNVERIFIED (best guess).
Javadoc base: `https://lexxie.dev/neoforge/26.1/` (JD). Javadoc is public-only; protected members come from NeoForge patches (`NF-patch`).

## E1. Level — `net.minecraft.world.level.Level` (V: JD net/minecraft/world/level/Level.html)
`public abstract class Level extends net.neoforged.neoforge.attachment.AttachmentHolder implements LevelAccessor, AutoCloseable, ILevelExtension`
Many methods are inherited from interfaces: `LevelReader`, `LevelAccessor`, `LevelWriter`, `LevelHeightAccessor`, `BlockGetter`, `BlockAndLightGetter`, `EntityGetter`, `ScheduledTickAccess`, `CollisionGetter` (all `net.minecraft.world.level`).

```java
// Blocks (V: LevelWriter.html + Level.html)
boolean setBlock(BlockPos pos, BlockState blockState, int updateFlags);              // default in LevelWriter
boolean setBlock(BlockPos pos, BlockState blockState, int updateFlags, int updateLimit);
boolean setBlockAndUpdate(BlockPos pos, BlockState state);
boolean removeBlock(BlockPos pos, boolean movedByPiston);
boolean destroyBlock(BlockPos pos, boolean dropResources);                             // default
boolean destroyBlock(BlockPos pos, boolean dropResources, @Nullable Entity breaker);   // default
boolean destroyBlock(BlockPos pos, boolean dropResources, @Nullable Entity breaker, int updateLimit);
BlockState getBlockState(BlockPos pos);           // BlockGetter
FluidState getFluidState(BlockPos pos);           // BlockGetter
boolean isEmptyBlock(BlockPos pos);               // LevelReader default
@Nullable BlockEntity getBlockEntity(BlockPos pos);
<T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type); // BlockGetter (V name/params; Optional<T> return U — matches 1.21)
void setBlockEntity(BlockEntity be); void removeBlockEntity(BlockPos pos);
boolean isLoaded(BlockPos pos);
// Side / random / time
boolean isClientSide();                           // METHOD (V). The public field `isClientSide` is gone from javadoc field list -> use the method.
RandomSource getRandom();                         // `random` field not listed (U: probably still public final RandomSource random — javadoc omits? treat as UNVERIFIED; use getRandom())
long getGameTime();                               // LevelAccessor default (V LevelAccessor.html)
// !! getDayTime() is NOT in Level 26.1 (V absent). Replacements:
long getDefaultClockTime(); long getOverworldClockTime();          // V Level.html
abstract net.minecraft.world.clock.ClockManager clockManager();     // V (ServerLevel returns ServerClockManager)
boolean isBrightOutside(); boolean isDarkOutside();                  // V — replace isDay()/isNight() (absent)
int getSkyDarken(); int getMoonPhase() /*U: not in Level index; use dimensionType().moonPhase(getDayTime) equivalent or environmentAttributes()*/;
// Ticks
void scheduleTick(BlockPos pos, Block block, int delay);            // ScheduledTickAccess (V via LevelAccessor inherited list)
void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority);
void scheduleTick(BlockPos pos, Fluid fluid, int delay);
LevelTickAccess<Block> getBlockTicks(); LevelTickAccess<Fluid> getFluidTicks();       // V names (return type U: net.minecraft.world.ticks.LevelTickAccess)
// Entities
boolean addFreshEntity(Entity entity);                                                // LevelWriter default (V)
List<Entity> getEntities(@Nullable Entity except, AABB area, Predicate<? super Entity> pred);
List<Entity> getEntities(@Nullable Entity except, AABB area);                          // EntityGetter
<T extends Entity> List<T> getEntities(EntityTypeTest<Entity,T> type, AABB area, Predicate<? super T> pred);
<T extends Entity> List<T> getEntitiesOfClass(Class<T> cls, AABB area);                // EntityGetter
<T extends Entity> List<T> getEntitiesOfClass(Class<T> cls, AABB area, Predicate<? super T> pred);
abstract @Nullable Entity getEntity(int id);  @Nullable Entity getEntity(UUID uuid);   // V both on Level
@Nullable Entity getEntityInAnyDimension(UUID uuid); @Nullable Player getPlayerInAnyDimension(UUID uuid); // V (return of 2nd U)
List<? extends Player> players();                                                      // EntityGetter
@Nullable Player getNearestPlayer(Entity e, double dist);                              // EntityGetter
// Sound (V all)
void playSound(@Nullable Entity except, BlockPos pos, SoundEvent sound, SoundSource src, float vol, float pitch);
void playSound(@Nullable Entity except, BlockPos pos, SoundEvent sound, SoundSource src);            // LevelAccessor
void playSound(@Nullable Entity except, double x, double y, double z, SoundEvent sound, SoundSource src, float vol, float pitch);
void playSound(@Nullable Entity except, double x, double y, double z, SoundEvent sound, SoundSource src);
void playSound(@Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource src, float vol, float pitch);
void playSound(@Nullable Entity except, Entity source, SoundEvent sound, SoundSource src, float vol, float pitch);
abstract void playSeededSound(@Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource src, float vol, float pitch, long seed);
void playLocalSound(BlockPos pos, SoundEvent s, SoundSource src, float vol, float pitch, boolean distanceDelay);
void playLocalSound(double x, double y, double z, SoundEvent s, SoundSource src, float vol, float pitch, boolean distanceDelay);
void playLocalSound(Entity e, SoundEvent s, SoundSource src, float vol, float pitch);
// NOTE: the `Player` first-arg overloads are gone; first param is `@Nullable Entity except` (V).
// Particles / events (V)
void addParticle(ParticleOptions p, double x, double y, double z, double dx, double dy, double dz);
void addParticle(ParticleOptions p, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, double dx, double dy, double dz);
void addAlwaysVisibleParticle(ParticleOptions p, double x, double y, double z, double dx, double dy, double dz);
void levelEvent(@Nullable Entity source, int type, BlockPos pos, int data);  void levelEvent(int type, BlockPos pos, int data); // LevelAccessor
void globalLevelEvent(int id, BlockPos pos, int data);
void blockEvent(BlockPos pos, Block block, int b0, int b1);
void broadcastEntityEvent(Entity e, byte event);  void broadcastDamageEvent(Entity e, DamageSource src);
void gameEvent(@Nullable Entity source, Holder<GameEvent> event, Vec3 pos);   void gameEvent(@Nullable Entity source, Holder<GameEvent> event, BlockPos pos);
void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context ctx);      void gameEvent(ResourceKey<GameEvent> event, BlockPos pos, GameEvent.Context ctx);
// Explosions (V) — enum net.minecraft.world.level.Level.ExplosionInteraction { NONE, BLOCK, MOB, TNT, TRIGGER } (V)
void explode(@Nullable Entity source, double x, double y, double z, float radius, Level.ExplosionInteraction mode);
void explode(@Nullable Entity source, double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction mode);
void explode(@Nullable Entity source, @Nullable DamageSource ds, @Nullable ExplosionDamageCalculator calc, Vec3 pos, float radius, boolean fire, Level.ExplosionInteraction mode);
void explode(@Nullable Entity source, @Nullable DamageSource ds, @Nullable ExplosionDamageCalculator calc, double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction mode);
abstract void explode(@Nullable Entity source, @Nullable DamageSource ds, @Nullable ExplosionDamageCalculator calc, double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction mode, ParticleOptions small, ParticleOptions large, WeightedList<?> blockParticles /*U generic*/, Holder<SoundEvent> sound);
// Dimension / registries (V)
ResourceKey<Level> dimension();  DimensionType dimensionType();  Holder<DimensionType> dimensionTypeRegistration();
RegistryAccess registryAccess();  <T> HolderLookup<T> holderLookup(ResourceKey<? extends Registry<? extends T>> key);
FeatureFlagSet enabledFeatures();  DamageSources damageSources();  @Nullable MinecraftServer getServer();
LevelData getLevelData();  abstract TickRateManager tickRateManager();  Difficulty getDifficulty();  int getSeaLevel();
abstract net.minecraft.world.attribute.EnvironmentAttributeSystem environmentAttributes(); // Level (V); LevelReader declares EnvironmentAttributeReader (V)
WorldBorder getWorldBorder();  BiomeManager getBiomeManager();  Holder<Biome> getBiome(BlockPos pos);  Holder<Biome> getNoiseBiome(int qx,int qy,int qz);
// !! getGameRules() is NOT on Level in 26.1 (only ServerLevel.getGameRules() -> net.minecraft.world.level.gamerules.GameRules, V). Package moved: gamerules.
// Weather (V — still on Level; backing data is WeatherData SavedData on server)
boolean isRaining(); boolean isThundering(); float getRainLevel(float partial); float getThunderLevel(float partial);
void setRainLevel(float); void setThunderLevel(float); boolean canHaveWeather(); boolean isRainingAt(BlockPos); Biome.Precipitation precipitationAt(BlockPos);
// Height / chunks (V LevelHeightAccessor.html, LevelReader.html)
int getMinY(); int getMaxY(); int getHeight(); int getSectionsCount(); int getMinSectionY(); int getMaxSectionY(); int getSectionIndex(int blockY);
boolean isInsideBuildHeight(BlockPos|int); boolean isOutsideBuildHeight(BlockPos|int);   // getMinBuildHeight/getMaxBuildHeight REMOVED
int getHeight(Heightmap.Types type, int x, int z);  BlockPos getHeightmapPos(Heightmap.Types, BlockPos);
LevelChunk getChunk(int chunkX, int chunkZ);  LevelChunk getChunkAt(BlockPos pos);  ChunkAccess getChunk(BlockPos pos);
@Nullable ChunkAccess getChunk(int cx, int cz, ChunkStatus targetStatus, boolean loadOrGenerate);
boolean hasChunk(int,int) /*@Deprecated*/;  boolean hasChunkAt(BlockPos) /*@Deprecated*/;  boolean isLoaded(BlockPos);
ChunkSource getChunkSource();   // LevelAccessor; ServerLevel overrides -> ServerChunkCache
// Light (V BlockAndLightGetter / BlockGetter)
int getBrightness(LightLayer layer, BlockPos pos); int getRawBrightness(BlockPos, int); boolean canSeeSky(BlockPos); int getLightEmission(BlockPos);
int getMaxLocalRawBrightness(BlockPos); int getMaxLocalRawBrightness(BlockPos, int skyDarkening);
// Misc (V)
@Nullable MapItemSavedData getMapData(MapId id);  boolean noSave(); boolean isDebug(); double getMaxEntityRadius(); boolean mayInteract(Entity e, BlockPos pos);
abstract LevelData.RespawnData getRespawnData();  void setRespawnData(LevelData.RespawnData d);   // replaces getSharedSpawnPos (V)
// NeoForge ILevelExtension (V net/neoforged/neoforge/common/extensions/ILevelExtension.html)
<T, C> @Nullable T getCapability(BlockCapability<T,C> cap, BlockPos pos, C context);
<T, C> @Nullable T getCapability(BlockCapability<T,C> cap, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity be, C context);
<T> @Nullable T getCapability(BlockCapability<T,Void> cap, BlockPos pos);
void invalidateCapabilities(BlockPos pos); void invalidateCapabilities(ChunkPos pos); Component getDescription();
// Attachments (AttachmentHolder, V): <T> T getData(AttachmentType<T>); <T> T setData(AttachmentType<T>, T); boolean hasData(AttachmentType<?>); removeData(...)
// getPartEntities: NOT listed on Level (U: removed; Level has `dragonParts()` V).  getProfiler: absent (V) — use Profiler.get().
```
`LevelData.RespawnData` = `record RespawnData(GlobalPos globalPos, float yaw, float pitch)` with `static of(ResourceKey<Level> dim, BlockPos pos, float yaw, float pitch)`, `dimension()`, `pos()`, `DEFAULT`, `CODEC` (V JD net/minecraft/world/level/storage/LevelData.RespawnData.html).

## E2. ServerLevel — `net.minecraft.server.level.ServerLevel` (V: JD net/minecraft/server/level/ServerLevel.html)
```java
long getSeed(); OptionalLong getSeedOverride();
ServerChunkCache getChunkSource();  net.minecraft.world.clock.ServerClockManager clockManager();
EnvironmentAttributeSystem environmentAttributes(); void setEnvironmentAttributes(EnvironmentAttributeSystem sys);
net.minecraft.world.level.saveddata.WeatherData getWeatherData();  void resetWeatherCycle();
// !! setDayTime(long) and setWeatherParameters(int,int,boolean,boolean) are GONE (V absent). Use:
//   getWeatherData().setRaining(bool)/setThundering(bool)/setRainTime(int)/setThunderTime(int)/setClearWeatherTime(int)  (V WeatherData.html)
//   clockManager().setTotalTicks(Holder<WorldClock> clock, long ticks) / addTicks(Holder<WorldClock>, int) / setRate(Holder<WorldClock>, float)
//   getTotalTicks(Holder<WorldClock>)   (V ServerClockManager.html; WorldClock in net.minecraft.world.clock; overworld clock Holder: U — registry `Registries.WORLD_CLOCK`?, key likely `WorldClocks.OVERWORLD`)
@Nullable MinecraftServer getServer(); LevelData.RespawnData getRespawnData(); void setRespawnData(LevelData.RespawnData);
WorldBorder getWorldBorder(); @Nullable Entity getEntity(int id);  (getEntity(UUID) inherited from Level)
List<ServerPlayer> players(); List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> sel); List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer>, int limit); @Nullable ServerPlayer getRandomPlayer();
TickRateManager tickRateManager(); SavedDataStorage getDataStorage();   // net.minecraft.world.level.storage.SavedDataStorage (renamed from DimensionDataStorage, V)
StructureTemplateManager getStructureManager(); StructureManager structureManager(); PoiManager getPoiManager();
LongSet getForceLoadedChunks(); boolean setChunkForced(int chunkX, int chunkZ, boolean forced);   // V names (return of setChunkForced U:boolean)
<T extends ParticleOptions> int sendParticles(T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed);
<T extends ParticleOptions> int sendParticles(T particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, int count, double dx, double dy, double dz, double speed);
<T extends ParticleOptions> boolean sendParticles(ServerPlayer player, T particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, int count, double dx, double dy, double dz, double speed);
void explode(@Nullable Entity src, @Nullable DamageSource ds, @Nullable ExplosionDamageCalculator calc, double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction mode, ParticleOptions small, ParticleOptions large, WeightedList<?> blockParticles, Holder<SoundEvent> sound); // + Level overloads
boolean addFreshEntity(Entity e); boolean tryAddFreshEntityWithPassengers(Entity e); void addDuringTeleport(Entity e); boolean addWithUUID(Entity e);
// addFreshEntityWithPassengers(Entity) : NOT in index (U: removed; use tryAddFreshEntityWithPassengers)
<T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity,T> type, Predicate<? super T> pred);
Iterable<Entity> getAllEntities(); LevelEntityGetter<Entity> getEntities();
@Nullable BlockPos findNearestMapStructure(TagKey<Structure> tag, BlockPos pos, int radius, boolean skipKnown);
boolean isFlat(); boolean isDebug(); boolean isHandlingTick(); boolean noSave(); @Nullable EnderDragonFight getDragonFight();
void save(@Nullable ProgressListener listener, boolean flush, boolean skipSave);
MapId getFreeMapId(); void setMapData(MapId, MapItemSavedData); @Nullable MapItemSavedData getMapData(MapId);
net.minecraft.world.level.gamerules.GameRules getGameRules();   // V; usage `level.getGameRules().get(GameRules.MOB_GRIEFING)` -> boolean (V via LivingEntity.java.patch context)
boolean isPositionEntityTicking(BlockPos); boolean isNaturalSpawningAllowed(BlockPos) /*U: not in index -> use canSpawnEntitiesInChunk(ChunkPos)/anyPlayerCloseEnoughForSpawning(BlockPos) (V)*/;
BlockPos getBlockRandomPos(int xo, int yo, int zo, int mask); // Level (V)
int getLogicalHeight(); PortalForcer getPortalForcer(); boolean isAllowedToEnterPortal(Level target); boolean isPvpAllowed();
```
`getLevelData()` is NOT overridden on ServerLevel in 26.1 index -> returns `LevelData` (cast to `ServerLevelData` if needed; U).

## E3. ServerChunkCache — `net.minecraft.server.level.ServerChunkCache` (V: JD net/minecraft/server/level/ServerChunkCache.html)
```java
public final ServerLevel level; public final ChunkMap chunkMap;
@Nullable ChunkAccess getChunk(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate);  @Nullable LevelChunk getChunkNow(int x, int z);
CompletableFuture<ChunkResult<ChunkAccess>> getChunkFuture(int x, int z, ChunkStatus status, boolean loadOrGenerate);
void addTicketWithRadius(TicketType type, ChunkPos pos, int radius);  void removeTicketWithRadius(TicketType type, ChunkPos pos, int radius);
CompletableFuture<?> addTicketAndLoadWithRadius(TicketType type, ChunkPos pos, int radius);  void addTicket(Ticket ticket, ChunkPos pos);
boolean updateChunkForced(ChunkPos pos, boolean forced); LongSet getForceLoadedChunks();
ChunkGenerator getGenerator(); ChunkGeneratorStructureState getGeneratorState(); RandomState randomState();
int getLoadedChunksCount(); void blockChanged(BlockPos pos); boolean hasChunk(int x, int z); SavedDataStorage getDataStorage(); PoiManager getPoiManager();
```
`TicketType` = `record TicketType(long timeout, int flags, boolean forceNaturalSpawning)` in `net.minecraft.server.level`; constants: `PLAYER_SPAWN, SPAWN_SEARCH, DRAGON, PLAYER_LOADING, PLAYER_SIMULATION, FORCED, PORTAL, ENDER_PEARL, UNKNOWN`; flags `FLAG_PERSIST, FLAG_LOADING, FLAG_SIMULATION, FLAG_KEEP_DIMENSION_ACTIVE, FLAG_CAN_EXPIRE_IF_UNLOADED`, `NO_TIMEOUT` (V). No `TicketType.PLAYER`; no generics (`TicketType<T>` gone).

## E4. Chunks — package `net.minecraft.world.level.chunk` (V: JD .../chunk/ChunkAccess.html, LevelChunk.html, LevelChunkSection.html, ProtoChunk.html)
```java
// ChunkAccess (abstract; implements BlockGetter, LightChunk, StructureAccess, IAttachmentHolder)
ChunkPos getPos(); LevelChunkSection[] getSections(); LevelChunkSection getSection(int sectionIndex);
int getMinY(); int getHeight();   // + inherited LevelHeightAccessor: getMaxY(), getSectionsCount(), getMinSectionY(), getMaxSectionY(), getSectionIndex(int blockY)
@Nullable BlockState setBlockState(BlockPos pos, BlockState state);                      // convenience
abstract @Nullable BlockState setBlockState(BlockPos pos, BlockState state, int flags);  // int flags — NO boolean overload
abstract void setBlockEntity(BlockEntity be);  @Nullable BlockEntity getBlockEntity(BlockPos);  Set<BlockPos> getBlockEntitiesPos();
long getInhabitedTime(); void setInhabitedTime(long t); void incrementInhabitedTime(long);
boolean isUnsaved(); void markUnsaved(); boolean tryMarkSaved();                        // setUnsaved(boolean) REMOVED
int getHeight(Heightmap.Types type, int x, int z); Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types); boolean hasPrimedHeightmap(Heightmap.Types);
Collection<Map.Entry<Heightmap.Types, Heightmap>> getHeightmaps();
abstract ChunkStatus getPersistedStatus(); ChunkStatus getHighestGeneratedStatus();     // getStatus() REMOVED; ChunkStatus in net.minecraft.world.level.chunk.status
@Nullable ShortList[] getPostProcessing(); void markPosForPostprocessing(BlockPos);
Holder<Biome> getNoiseBiome(int qx, int qy, int qz); void fillBiomesFromNoise(BiomeResolver resolver, Climate.Sampler sampler);
ChunkSkyLightSources getSkyLightSources(); final void findBlockLightSources(BiConsumer<BlockPos, BlockState> c); boolean isLightCorrect(); void setLightCorrect(boolean);
@Nullable Level getLevel(); Map<Structure, StructureStart> getAllStarts(); @Nullable StructureStart getStartForStructure(Structure s);
// ctor: ChunkAccess(ChunkPos, UpgradeData, LevelHeightAccessor, PalettedContainerFactory, long inhabitedTime, LevelChunkSection[], BlendingData)  (PalettedContainerFactory replaces Registry<Biome>)
// LevelChunk extends ChunkAccess
LevelChunk(Level level, ChunkPos pos);  LevelChunk(ServerLevel, ProtoChunk, LevelChunk.PostLoadProcessor);
Level getLevel(); BlockState getBlockState(BlockPos); FluidState getFluidState(BlockPos);
@Nullable BlockState setBlockState(BlockPos pos, BlockState state, int flags);
@Nullable BlockEntity getBlockEntity(BlockPos pos); @Nullable BlockEntity getBlockEntity(BlockPos pos, LevelChunk.EntityCreationType type);
void addAndRegisterBlockEntity(BlockEntity be); void setBlockEntity(BlockEntity); void removeBlockEntity(BlockPos); Map<BlockPos, BlockEntity> getBlockEntities();
net.minecraft.server.level.FullChunkStatus getFullStatus(); ChunkStatus getPersistedStatus(); boolean isEmpty(); void runPostLoad(); void setLoaded(boolean);
// LevelChunkSection
LevelChunkSection(PalettedContainer<BlockState> states, PalettedContainerRO<Holder<Biome>> biomes);  LevelChunkSection(PalettedContainerFactory f);
BlockState getBlockState(int x, int y, int z); FluidState getFluidState(int,int,int);
BlockState setBlockState(int x, int y, int z, BlockState state);  BlockState setBlockState(int x, int y, int z, BlockState state, boolean checkThreading);
boolean hasOnlyAir(); boolean hasFluid(); boolean isRandomlyTicking(); boolean isRandomlyTickingBlocks(); boolean isRandomlyTickingFluids();
PalettedContainer<BlockState> getStates(); PalettedContainerRO<Holder<Biome>> getBiomes(); void recalcBlockCounts(); boolean maybeHas(Predicate<BlockState> p);
void acquire(); void release(); Holder<Biome> getNoiseBiome(int qx,int qy,int qz); void fillBiomesFromNoise(BiomeResolver, Climate.Sampler, int qMinX, int qMinY, int qMinZ);
// bottomBlockY(): NOT present (U: removed; compute via SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(i)))
// ProtoChunk extends ChunkAccess
ProtoChunk(ChunkPos, UpgradeData, LevelHeightAccessor, PalettedContainerFactory, @Nullable BlendingData);
void setPersistedStatus(ChunkStatus s);  @Nullable BlockState setBlockState(BlockPos, BlockState, int flags);
@Nullable CarvingMask getCarvingMask(); CarvingMask getOrCreateCarvingMask(); void setCarvingMask(CarvingMask m);   // NO GenerationStep.Carving param
// setBiomes(...) and addLight(...) REMOVED (V absent). GenerationStep.Carving REMOVED (404 V); only GenerationStep.Decoration remains:
// RAW_GENERATION, LAKES, LOCAL_MODIFICATIONS, UNDERGROUND_STRUCTURES, SURFACE_STRUCTURES, STRONGHOLDS, UNDERGROUND_ORES, UNDERGROUND_DECORATION, FLUID_SPRINGS, VEGETAL_DECORATION, TOP_LAYER_MODIFICATION
// ChunkStatus (class, not enum) constants: EMPTY, STRUCTURE_STARTS, STRUCTURE_REFERENCES, BIOMES, NOISE, SURFACE, CARVERS, FEATURES, INITIALIZE_LIGHT, LIGHT, SPAWN, FULL (no LIQUID_CARVERS)
// PalettedContainer<T>: PalettedContainer(T initial, net.minecraft.world.level.chunk.Strategy<T> strategy); T get(int,int,int); void set(int,int,int,T); T getAndSet(int,int,int,T)
// Heightmap (net.minecraft.world.level.levelgen.Heightmap): static void primeHeightmaps(ChunkAccess chunk, Set<Heightmap.Types> types); int getFirstAvailable(int x,int z);
// Heightmap.Types: WORLD_SURFACE_WG, WORLD_SURFACE, OCEAN_FLOOR_WG, OCEAN_FLOOR, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES
```

## E5. WorldGenRegion / ChunkPos / SectionPos (V: JD net/minecraft/server/level/WorldGenRegion.html, net/minecraft/world/level/ChunkPos.html, net/minecraft/core/SectionPos.html)
```java
// net.minecraft.server.level.WorldGenRegion implements WorldGenLevel
WorldGenRegion(ServerLevel level, StaticCache2D<GenerationChunkHolder> cache, ChunkStep step, ChunkAccess center);
boolean setBlock(BlockPos pos, BlockState state, int updateFlags, int updateLimit);  // 3-arg setBlock is LevelWriter default
long getSeed(); ChunkPos getCenter(); RandomSource getRandom(); @Deprecated ServerLevel getLevel();
ChunkAccess getChunk(int cx, int cz); @Nullable ChunkAccess getChunk(int cx, int cz, ChunkStatus status, boolean loadOrGenerate); boolean hasChunk(int,int);
int getMinY(); int getHeight(); int getHeight(Heightmap.Types, int x, int z); int getSeaLevel(); boolean ensureCanWrite(BlockPos pos); boolean addFreshEntity(Entity e);
BlockState getBlockState(BlockPos); FluidState getFluidState(BlockPos); @Nullable BlockEntity getBlockEntity(BlockPos);
ChunkSource getChunkSource(); BiomeManager getBiomeManager(); RegistryAccess registryAccess(); @Nullable MinecraftServer getServer(); LevelData getLevelData();
DifficultyInstance getCurrentDifficultyAt(BlockPos); boolean isClientSide(); DimensionType dimensionType(); FeatureFlagSet enabledFeatures(); EnvironmentAttributeReader environmentAttributes();
Holder<Biome> getBiome(BlockPos);   // inherited LevelReader default
// net.minecraft.world.level.ChunkPos — now a RECORD: record ChunkPos(int x, int z)
// pos.x / pos.z field access GONE -> pos.x() / pos.z(). Only ctor: ChunkPos(int x, int z).
static ChunkPos containing(BlockPos pos); static ChunkPos unpack(long key);            // replaces new ChunkPos(BlockPos) / new ChunkPos(long)
long pack(); static long pack(int x, int z); static long pack(BlockPos pos);           // replaces toLong()/asLong(..)
static int getX(long packed); static int getZ(long packed);
int getMinBlockX(); getMinBlockZ(); getMaxBlockX(); getMaxBlockZ(); getMiddleBlockX(); getMiddleBlockZ(); int getBlockX(int off); int getBlockZ(int off);
BlockPos getBlockAt(int x, int y, int z); BlockPos getWorldPosition(); BlockPos getMiddleBlockPosition(int y); boolean contains(BlockPos);
int getRegionX(); int getRegionZ(); int getRegionLocalX(); int getRegionLocalZ(); int getChessboardDistance(ChunkPos); int getChessboardDistance(int x, int z); int distanceSquared(ChunkPos);
static Stream<ChunkPos> rangeClosed(ChunkPos center, int radius); static Stream<ChunkPos> rangeClosed(ChunkPos from, ChunkPos to);
static final ChunkPos ZERO; static final long INVALID_CHUNK_POS; CODEC; STREAM_CODEC;
// net.minecraft.core.SectionPos extends Vec3i
static SectionPos of(int x,int y,int z); of(BlockPos); of(ChunkPos pos, int sectionY); of(EntityAccess); of(Position); of(long); static SectionPos bottomOf(ChunkAccess);
static int blockToSectionCoord(int); static int blockToSectionCoord(double); static int posToSectionCoord(double);
static int sectionToBlockCoord(int sectionCoord); static int sectionToBlockCoord(int sectionCoord, int offset); static int sectionRelative(int);
int x(); int y(); int z(); BlockPos origin(); BlockPos center(); ChunkPos chunk(); int minBlockX()..maxBlockZ(); long asLong(); static long asLong(int,int,int); static long asLong(BlockPos);
// LevelHeightAccessor (net.minecraft.world.level): int getHeight(); int getMinY(); default int getMaxY(); getSectionsCount(); getMinSectionY(); getMaxSectionY(); getSectionIndex(int blockY);
//   getSectionIndexFromSectionY(int); getSectionYFromSectionIndex(int); isInsideBuildHeight(int|BlockPos); isOutsideBuildHeight(int|BlockPos); static create(int minY, int height)  (V)
```

## E6. Vec3 / AABB / BlockPos / Direction (V: JD net/minecraft/world/phys/Vec3.html, AABB.html, net/minecraft/core/BlockPos.html, BlockPos.MutableBlockPos.html, Vec3i.html, Direction.html)
```java
// net.minecraft.world.phys.Vec3 implements Position — public final double x, y, z; also x()/y()/z()
static final Vec3 ZERO, X_AXIS, Y_AXIS, Z_AXIS; static final Codec<Vec3> CODEC; STREAM_CODEC;
Vec3(double x, double y, double z); Vec3(org.joml.Vector3fc v); Vec3(Vec3i v);
static Vec3 atCenterOf(Vec3i); atBottomCenterOf(Vec3i); atLowerCornerOf(Vec3i); atLowerCornerWithOffset(Vec3i,double,double,double); upFromBottomCenterOf(Vec3i, double yOff);
static Vec3 directionFromRotation(float xRot, float yRot); directionFromRotation(Vec2);
Vec3 add(Vec3); add(double,double,double); add(double); subtract(Vec3); subtract(double,double,double); scale(double); normalize(); reverse(); multiply(Vec3); multiply(double,double,double);
Vec3 lerp(Vec3 to, double t); xRot(float rad); yRot(float rad); zRot(float rad); with(Direction.Axis, double); relative(Direction, double); vectorTo(Vec3); cross(Vec3); horizontal(); offsetRandom(RandomSource, float);
double length(); lengthSqr(); horizontalDistance(); horizontalDistanceSqr(); distanceTo(Vec3); distanceToSqr(Vec3); distanceToSqr(double,double,double); dot(Vec3); get(Direction.Axis);
boolean closerThan(Position p, double d); org.joml.Vector3f toVector3f();   // fromRGB24(int) REMOVED (V absent)
// net.minecraft.world.phys.AABB — public final double minX, minY, minZ, maxX, maxY, maxZ; static final AABB INFINITE
AABB(double x1,double y1,double z1,double x2,double y2,double z2); AABB(BlockPos pos); AABB(Vec3 a, Vec3 b);
static AABB of(BoundingBox box); ofSize(Vec3 center, double sx, double sy, double sz); unitCubeFromLowerCorner(Vec3); encapsulatingFullBlocks(BlockPos a, BlockPos b);
AABB inflate(double); inflate(double,double,double); deflate(double); deflate(double,double,double); expandTowards(Vec3); expandTowards(double,double,double); move(double,double,double); move(Vec3); move(BlockPos); intersect(AABB); minmax(AABB);
boolean contains(Vec3); contains(double,double,double); intersects(AABB); intersects(double x6); intersects(Vec3, Vec3); intersects(BlockPos);
Vec3 getCenter(); getBottomCenter(); getMinPosition(); getMaxPosition(); double getSize(); getXsize(); getYsize(); getZsize(); distanceToSqr(Vec3); Optional<Vec3> clip(Vec3 from, Vec3 to);
// net.minecraft.core.BlockPos extends Vec3i (immutable) — ZERO, CODEC, STREAM_CODEC
BlockPos(int x,int y,int z); BlockPos(Vec3i v);
static BlockPos containing(double x, double y, double z); containing(Position p); static BlockPos of(long packed); long asLong(); static long asLong(int,int,int); static int getX(long)/getY(long)/getZ(long);
BlockPos offset(int,int,int); offset(Vec3i); subtract(Vec3i); multiply(int); above(); above(int); below(); below(int); north()/south()/east()/west() (+int); relative(Direction); relative(Direction,int); relative(Direction.Axis,int);
BlockPos rotate(Rotation); atY(int y); immutable(); BlockPos.MutableBlockPos mutable(); Vec3 getCenter(); Vec3 getBottomCenter();
int getX()/getY()/getZ(); double distSqr(Vec3i); int distManhattan(Vec3i); int distChessboard(Vec3i); boolean closerThan(Vec3i, double); boolean closerToCenterThan(Position, double); String toShortString();  // from Vec3i
static Iterable<BlockPos> betweenClosed(BlockPos a, BlockPos b); betweenClosed(int x6); betweenClosed(AABB); withinManhattan(BlockPos origin,int rx,int ry,int rz); randomInCube(RandomSource, int limit, BlockPos center, int size);
static Iterable<BlockPos.MutableBlockPos> spiralAround(BlockPos center, int radius, Direction first, Direction second);
static Stream<BlockPos> betweenClosedStream(BlockPos, BlockPos); betweenClosedStream(int x6); betweenClosedStream(AABB); betweenClosedStream(BoundingBox);
static Optional<BlockPos> findClosestMatch(BlockPos start, int hRadius, int vRadius, Predicate<BlockPos> p);
// BlockPos.MutableBlockPos: MutableBlockPos(); (int,int,int); (double,double,double); set(int,int,int); set(double,double,double); set(Vec3i); set(long);
//   setWithOffset(Vec3i pos, Direction d); setWithOffset(Vec3i,int,int,int); move(Direction); move(Direction,int); move(int,int,int); move(Vec3i); setX/setY/setZ(int); clamp(Direction.Axis,int,int)  (set(Vec3i,Direction) does NOT exist)
// net.minecraft.core.Direction: DOWN, UP, NORTH, SOUTH, WEST, EAST; getOpposite(); getClockWise(); getCounterClockWise(); getAxis(); getAxisDirection(); getStepX/Y/Z(); toYRot(); get2DDataValue(); getName();
//   Vec3i getUnitVec3i() (getNormal() REMOVED); Vec3 getUnitVec3(); static fromYRot(double); from2DDataValue(int); getRandom(RandomSource); getApproximateNearest(double,double,double) / (Vec3) (getNearest(double x3) REMOVED);
//   Direction.Axis { X, Y, Z } isVertical()/isHorizontal(); Direction.Plane { HORIZONTAL, VERTICAL } getRandomDirection(RandomSource), stream(), iterator()
```

## F1. Entity — `net.minecraft.world.entity.Entity` (V: JD net/minecraft/world/entity/Entity.html + NF-patch Entity.java.patch)
`public abstract class Entity extends net.neoforged.neoforge.attachment.AttachmentHolder implements Nameable, EntityAccess, ScoreHolder, SyncedDataHolder, IEntityExtension, ...`
```java
protected Entity(EntityType<?> type, Level level);
// Fields (V javadoc field list): public double fallDistance;  public int tickCount;  public boolean horizontalCollision, verticalCollision, noPhysics;  public double xo, yo, zo;
// Teleport / position
@Nullable Entity teleport(net.minecraft.world.level.portal.TeleportTransition transition);      // returns null if NeoForge EntityTravelToDimensionEvent cancels (V patch)
boolean teleportTo(ServerLevel level, double x, double y, double z, Set<Relative> relatives, float yRot, float xRot, boolean setCamera);  // V params; boolean return U (matches 1.21)
void teleportTo(double x, double y, double z);  void teleportRelative(double dx,double dy,double dz);  void dismountTo(double,double,double);
void setPos(double x, double y, double z); void setPos(Vec3 pos);
// moveTo(...) / absMoveTo(...) RENAMED (V absent -> present):
void snapTo(double x, double y, double z, float yRot, float xRot); void snapTo(double,double,double); void snapTo(Vec3); void snapTo(Vec3, float yRot, float xRot); void snapTo(BlockPos, float yRot, float xRot);
void absSnapTo(double x, double y, double z, float yRot, float xRot); void absSnapTo(double,double,double); void absSnapRotationTo(float yRot, float xRot);
void move(MoverType type, Vec3 movement);  // enum net.minecraft.world.entity.MoverType { SELF, PLAYER, PISTON, SHULKER_BOX, SHULKER } (U values)
Vec3 getDeltaMovement(); void setDeltaMovement(Vec3); void setDeltaMovement(double,double,double); void addDeltaMovement(Vec3); void push(double x,double y,double z); void push(Vec3); void push(Entity other);
void lerpMotion(Vec3 motion);  // lerpTo(...) replaced by moveOrInterpolateTo(Vec3, float yRot, float xRot) / moveOrInterpolateTo(Vec3) / getInterpolation() (V)
void resetFallDistance(); boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source);   // fallDistance is DOUBLE (V)
float getYRot(); float getXRot(); void setYRot(float); void setXRot(float); float getYRot(float partial); float getXRot(float partial); void setYHeadRot(float); float getYHeadRot(); void setYBodyRot(float); void turn(double yaw, double pitch);
Vec3 position(); BlockPos blockPosition(); ChunkPos chunkPosition(); double getX(); getY(); getZ(); double getX(double scale)...; double getEyeY(); Vec3 getEyePosition(); Vec3 getEyePosition(float partial);
final float getEyeHeight(); final float getEyeHeight(Pose); EntityDimensions getDimensions(Pose); float getBbWidth(); float getBbHeight(); final AABB getBoundingBox(); void setBoundingBox(AABB);
BlockPos getOnPos(); BlockState getBlockStateOn(); BlockState getInBlockState(); Direction getDirection(); Direction getMotionDirection(); Vec3 getLookAngle(); final Vec3 getViewVector(float partial); Vec3 getForward();
boolean onGround(); void setOnGround(boolean); boolean isFree(double dx,double dy,double dz); Vec3 getKnownMovement();
// Damage (V index)
boolean hurt(DamageSource source, float amount);            // BACK in 26.1 (dispatches to hurtServer/hurtClient); hurtOrSimulate(DamageSource,float) also exists
boolean hurtServer(ServerLevel level, DamageSource source, float amount);  boolean hurtClient(DamageSource source);   // return types U (boolean per LivingEntity V)
boolean isInvulnerable(); void setInvulnerable(boolean);    // isInvulnerableTo(ServerLevel, DamageSource) is on LivingEntity (V), Entity has protected final isInvulnerableToBase(DamageSource)
void kill(ServerLevel level); void discard(); void remove(Entity.RemovalReason reason); boolean isRemoved(); @Nullable Entity.RemovalReason getRemovalReason(); boolean isAlive();
// Entity.RemovalReason { KILLED, DISCARDED, UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER, CHANGED_DIMENSION } (U values, page exists V)
// Riding
final List<Entity> getPassengers(); @Nullable Entity getVehicle(); Entity getRootVehicle(); @Nullable Entity getControlledVehicle(); @Nullable LivingEntity getControllingPassenger(); @Nullable Entity getFirstPassenger();
boolean startRiding(Entity vehicle); boolean startRiding(Entity vehicle, boolean force, boolean sendPacket /*U name*/); void stopRiding(); void removeVehicle(); void ejectPassengers();
boolean isPassenger(); boolean isVehicle(); boolean hasPassenger(Entity); boolean hasPassenger(Predicate<Entity>); Stream<Entity> getSelfAndPassengers(); Iterable<Entity> getIndirectPassengers();
boolean isLocalInstanceAuthoritative(); boolean isClientAuthoritative();   // isControlledByLocalInstance() RENAMED (V)
// Identity / naming
UUID getUUID(); String getStringUUID(); int getId(); EntityType<?> getType(); Holder<EntityType<?>> typeHolder() /*U generic*/; Level level(); @Nullable MinecraftServer getServer() /*U: not in truncated index*/; DamageSources damageSources(); RandomSource getRandom();
Component getName(); Component getDisplayName(); @Nullable Component getCustomName(); void setCustomName(@Nullable Component); boolean hasCustomName(); void setCustomNameVisible(boolean); boolean isCustomNameVisible();
Set<String> entityTags(); boolean addTag(String); boolean removeTag(String);   // getTags() RENAMED -> entityTags() (V)
// State
boolean isInWater(); isInWaterOrRain(); isUnderWater(); isInLava(); isInLiquid(); isInClouds(); isOnFire(); isInWall(); isSpectator(); isShiftKeyDown(); isCrouching(); isSprinting(); isSwimming(); isInvisible(); isCurrentlyGlowing();
void igniteForSeconds(float); void igniteForTicks(int); void setRemainingFireTicks(int); int getRemainingFireTicks(); void clearFire(); void extinguishFire();
boolean isSilent(); void setSilent(boolean); boolean isNoGravity(); void setNoGravity(boolean); double getGravity(); void setInvisible(boolean); void setGlowingTag(boolean); void setSprinting(boolean); void setShiftKeyDown(boolean);
Pose getPose(); void setPose(Pose); boolean hasPose(Pose);
// Fluid (NeoForge, V via patches): boolean isEyeInFluid(net.neoforged.neoforge.fluids.FluidType); double getFluidHeight(FluidType); EntityFluidInteraction getFluidInteraction();
//   @Deprecated isEyeInFluid(TagKey<Fluid>), getFluidHeight(TagKey<Fluid>). Also used in LivingEntity patch: isInFluidType(FluidState), getFluidTypeHeight(FluidType) (V by usage).
// Interaction / picking
InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation);   // replaces interactAt (V params; return U InteractionResult)
@Nullable ItemStack getPickResult();  boolean canBeCollidedWith(@Nullable Entity other); boolean canCollideWith(Entity); boolean isPickable(); boolean isPushable(); float getPickRadius(); boolean canBeHitByProjectile();
float distanceTo(Entity); double distanceToSqr(Entity); double distanceToSqr(Vec3); double distanceToSqr(double,double,double); boolean closerThan(Entity, double); boolean closerThan(Entity, double dXZ, double dY);
// Portals / dimensions
void setPortalCooldown(); void setPortalCooldown(int); int getPortalCooldown(); boolean isOnPortalCooldown(); void setAsInsidePortal(net.minecraft.world.level.block.Portal portal, BlockPos pos); boolean canUsePortal(boolean ignorePassenger);
boolean canTeleport(Level from, Level to);   // canChangeDimensions(Level, Level) RENAMED -> canTeleport (V)
int getDimensionChangingDelay(); void placePortalTicket(BlockPos);
// Sound / events / drops
void playSound(SoundEvent s, float volume, float pitch); void playSound(SoundEvent s); SoundSource getSoundSource(); void gameEvent(Holder<GameEvent> e); void gameEvent(Holder<GameEvent> e, @Nullable Entity source);
@Nullable ItemEntity spawnAtLocation(ServerLevel level, ItemStack stack); spawnAtLocation(ServerLevel, ItemStack, float yOffset); spawnAtLocation(ServerLevel, ItemStack, Vec3 offset); spawnAtLocation(ServerLevel, ItemLike item);
boolean ignoreExplosion(Explosion e); boolean mayInteract(ServerLevel level, BlockPos pos); void thunderHit(ServerLevel, LightningBolt);
// Tick / data
void tick(); void baseTick(); SynchedEntityData getEntityData();
protected abstract void defineSynchedData(SynchedEntityData.Builder builder);            // V patch ctor context
protected abstract void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input);   // V patch
protected abstract void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output);  // V patch
boolean save(ValueOutput out); boolean saveAsPassenger(ValueOutput); void saveWithoutId(ValueOutput); void load(ValueInput in); boolean shouldBeSaved(); boolean isAlwaysTicking();
// NeoForge IEntityExtension (V): CompoundTag getPersistentData(); boolean isAddedToLevel(); void onAddedToLevel(); void onRemovedFromLevel(); void revive();
//   @Nullable Collection<ItemEntity> captureDrops(); captureDrops(@Nullable Collection<ItemEntity>); boolean canTrample(ServerLevel, BlockState, BlockPos, double fallDistance);
//   final <T> T setData(AttachmentType<T> type, T data); <T> T getData(AttachmentType<T>); boolean hasData(AttachmentType<?>); final void syncData(AttachmentType<?>);
//   final <T,C> @Nullable T getCapability(EntityCapability<T,C> cap, C ctx); final <T> @Nullable T getCapability(EntityCapability<T,Void> cap);
// getCommandSenderWorld()/getFeetBlockState()/interactAt()/getTags()/moveTo()/absMoveTo()/lerpTo(): all ABSENT from 26.1 index (V absent).
```
`Relative` (net.minecraft.world.entity.Relative, V): enum `X, Y, Z, Y_ROT, X_ROT, DELTA_X, DELTA_Y, DELTA_Z, ROTATE_DELTA`; `static Set<Relative> ALL, ROTATION, DELTA`; `static Set<Relative> union(Set<Relative>...)`, `rotation(boolean,boolean)`, `position(boolean,boolean,boolean)`.
`SynchedEntityData` (net.minecraft.network.syncher, V): `static <T> EntityDataAccessor<T> defineId(Class<? extends SyncedDataHolder> clazz, EntityDataSerializer<T> ser)`; `<T> T get(EntityDataAccessor<T>)`; `<T> void set(EntityDataAccessor<T>, T)`; `set(acc, T, boolean forceDirty)`; `Builder.define(EntityDataAccessor<T>, T)`.
`EntityDataSerializers` (V): `BYTE, INT, LONG, FLOAT, STRING, COMPONENT, OPTIONAL_COMPONENT, ITEM_STACK, BLOCK_STATE, OPTIONAL_BLOCK_STATE, BOOLEAN, PARTICLE, PARTICLES, ROTATIONS, BLOCK_POS, OPTIONAL_BLOCK_POS, DIRECTION, OPTIONAL_LIVING_ENTITY_REFERENCE, OPTIONAL_GLOBAL_POS, POSE, VECTOR3 (org.joml.Vector3fc), QUATERNION`. **`OPTIONAL_UUID` and `COMPOUND_TAG` REMOVED** (V absent) — store UUIDs via `OPTIONAL_LIVING_ENTITY_REFERENCE` or custom `EntityDataSerializer`.

## F2. LivingEntity — `net.minecraft.world.entity.LivingEntity` (V: JD .../LivingEntity.html + NF-patch LivingEntity.java.patch)
```java
static AttributeSupplier.Builder createLivingAttributes();
final boolean addEffect(MobEffectInstance e); boolean addEffect(MobEffectInstance e, @Nullable Entity source); void forceAddEffect(MobEffectInstance, @Nullable Entity);
boolean removeEffect(Holder<MobEffect> e); @Nullable MobEffectInstance removeEffectNoUpdate(Holder<MobEffect>); boolean hasEffect(Holder<MobEffect>); @Nullable MobEffectInstance getEffect(Holder<MobEffect>);
Collection<MobEffectInstance> getActiveEffects(); Map<Holder<MobEffect>, MobEffectInstance> getActiveEffectsMap(); boolean removeAllEffects();
@Deprecated @ApiStatus.OverrideOnly boolean canBeAffected(MobEffectInstance);   // call CommonHooks.canMobEffectBeApplied(...) instead (NF)
float getHealth(); void setHealth(float); final float getMaxHealth(); void heal(float); boolean isDeadOrDying(); float getAbsorptionAmount(); void setAbsorptionAmount(float);
boolean hurtServer(ServerLevel level, DamageSource source, float amount); boolean isInvulnerableTo(ServerLevel level, DamageSource source); @Nullable DamageSource getLastDamageSource();
protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg);   // VOID (V patch)
protected void dropAllDeathLoot(ServerLevel level, DamageSource source);           // V patch
void die(DamageSource source); void knockback(double power, double xd, double zd); int getArmorValue(); boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source);
ItemStack getItemBySlot(EquipmentSlot slot); void setItemSlot(EquipmentSlot slot, ItemStack stack); void setItemSlot(EquipmentSlot, ItemStack, boolean insideTransaction /*NF*/);
ItemStack getMainHandItem(); ItemStack getOffhandItem(); ItemStack getItemInHand(InteractionHand hand); void setItemInHand(InteractionHand hand, ItemStack stack); ItemStack getWeaponItem();
// getEquipment() is NOT public (protected `equipment` field, type net.minecraft.world.entity.EntityEquipment: ItemStack get(EquipmentSlot); ItemStack set(EquipmentSlot, ItemStack))
@Nullable AttributeInstance getAttribute(Holder<Attribute>); double getAttributeValue(Holder<Attribute>); double getAttributeBaseValue(Holder<Attribute>); AttributeMap getAttributes();
boolean isUsingItem(); ItemStack getUseItem(); void startUsingItem(InteractionHand); void stopUsingItem(); void releaseUsingItem(); int getUseItemRemainingTicks(); InteractionHand getUsedItemHand();
int getAirSupply(); void setAirSupply(int); int getMaxAirSupply();   // (declared on Entity, V)
boolean isSleeping(); Optional<BlockPos> getSleepingPos(); void startSleeping(BlockPos); void stopSleeping(); boolean isSensitiveToWater(); boolean isBaby(); boolean isFallFlying(); boolean isBlocking();
void jumpFromGround(); void travel(Vec3 input); float getSpeed(); void setSpeed(float); boolean isInvertedHealAndHarm(); float getYHeadRot(); void setYHeadRot(float); void setYBodyRot(float);
void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 target); boolean hasLineOfSight(Entity); boolean randomTeleport(double x,double y,double z, boolean particles); Brain<? extends LivingEntity> getBrain();
@Nullable ItemEntity drop(ItemStack stack, boolean randomly, boolean thrownFromHand); void swing(InteractionHand); boolean doHurtTarget(ServerLevel, Entity target);
// getDamageAfterArmorAbsorb / getDamageAfterMagicAbsorb: protected float (DamageSource, float) (V patch usage)
```
`MobEffectInstance` ctors (V): `(Holder<MobEffect>)`, `(Holder<MobEffect>, int duration)`, `(Holder<MobEffect>, int, int amplifier)`, `(..., boolean ambient, boolean visible)`, `(..., boolean showIcon)`, `(..., @Nullable MobEffectInstance hidden)`. `MobEffects.*` are `Holder<MobEffect>` (V): `SPEED, SLOWNESS, NAUSEA, REGENERATION, RESISTANCE, FIRE_RESISTANCE, WATER_BREATHING, INVISIBILITY, BLINDNESS, NIGHT_VISION, WEAKNESS, POISON, WITHER, GLOWING, LEVITATION` (old MOVEMENT_SLOWDOWN/DAMAGE_RESISTANCE/CONFUSION gone).
`Attributes.*` are `Holder<Attribute>` (V): `MAX_HEALTH, MOVEMENT_SPEED, ATTACK_DAMAGE, ATTACK_SPEED, ARMOR, ARMOR_TOUGHNESS, KNOCKBACK_RESISTANCE, FOLLOW_RANGE, GRAVITY, SCALE, STEP_HEIGHT, SAFE_FALL_DISTANCE, FALL_DAMAGE_MULTIPLIER, FLYING_SPEED, JUMP_STRENGTH`.
`EquipmentSlot` (V): `MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD, BODY, SADDLE`; `EquipmentSlot.Type { HAND, HUMANOID_ARMOR, ANIMAL_ARMOR, SADDLE }`.

## F3. Mob / EntityType / misc entities (V: JD .../Mob.html, EntityType.html, EntityType.Builder.html, item/ItemEntity.html, item/FallingBlockEntity.html, LightningBolt.html)
```java
// net.minecraft.world.entity.Mob
public final GoalSelector goalSelector, targetSelector;   // public (NF AT); net.minecraft.world.entity.ai.goal.GoalSelector: addGoal(int prio, Goal), removeGoal(Goal), removeAllGoals(Predicate<Goal>)
protected void registerGoals();  protected @Nullable SoundEvent getAmbientSound();   // U (protected, unchanged names; not in patch)
static AttributeSupplier.Builder createMobAttributes(); PathNavigation getNavigation(); LookControl getLookControl(); MoveControl getMoveControl();
@Nullable LivingEntity getTarget(); void setTarget(@Nullable LivingEntity t); int getMaxHeadYRot(); void setPersistenceRequired(); boolean isPersistenceRequired(); boolean isNoAi(); void setNoAi(boolean); void setBaby(boolean); void setDropChance(EquipmentSlot, float);
@Deprecated @ApiStatus.OverrideOnly @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data);  // callers use net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(...)
boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason); boolean checkSpawnObstruction(LevelReader level);
static boolean checkMobSpawnRules(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random);
<T extends Mob> @Nullable T convertTo(EntityType<T> type, ConversionParams params, EntitySpawnReason reason, ConversionParams.AfterConversion<T> after);
// net.minecraft.world.entity.EntitySpawnReason (renamed from MobSpawnType): NATURAL, CHUNK_GENERATION, SPAWNER, STRUCTURE, BREEDING, MOB_SUMMONED, JOCKEY, EVENT, CONVERSION, REINFORCEMENT, TRIGGERED, BUCKET, SPAWN_ITEM_USE, COMMAND, DISPENSER, PATROL, TRIAL_SPAWNER, LOAD, DIMENSION_TRAVEL
// net.minecraft.world.entity.EntityType<T>
@Nullable T create(Level level, EntitySpawnReason reason);            // takes Level (ServerLevel is fine)
@Nullable T spawn(ServerLevel level, BlockPos pos, EntitySpawnReason reason);
@Nullable T spawn(ServerLevel level, @Nullable ItemStack stack, @Nullable LivingEntity user, BlockPos pos, EntitySpawnReason reason, boolean tryMoveDown, boolean movedUp);
@Nullable T spawn(ServerLevel level, @Nullable Consumer<T> config, BlockPos pos, EntitySpawnReason reason, boolean tryMoveDown, boolean movedUp);
String getDescriptionId(); Component getDescription(); String toShortString(); MobCategory getCategory(); EntityDimensions getDimensions(); static Optional<EntityType<?>> byString(String); static net.minecraft.resources.Identifier getKey(EntityType<?>);
static final EntityType<Villager> VILLAGER (net.minecraft.world.entity.npc.villager.Villager); EntityType<ItemEntity> ITEM; EntityType<LightningBolt> LIGHTNING_BOLT; EntityType<FallingBlockEntity> FALLING_BLOCK; EntityType<Player> PLAYER;
// EntityType.Builder<T>: static of(EntityType.EntityFactory<T> factory, MobCategory cat); sized(float w, float h); eyeHeight(float); clientTrackingRange(int); updateInterval(int); fireImmune(); noSummon(); noSave(); immuneTo(Block...);
//   canSpawnFarFromPlayer(); noLootTable(); notInPeaceful(); passengerAttachments(float...); requiredFeatures(FeatureFlag...); EntityType<T> build(ResourceKey<EntityType<?>> key)
// EntityFactory<T>: @Nullable T create(EntityType<T> type, Level level).  MobCategory: MONSTER, CREATURE, AMBIENT, AXOLOTLS, UNDERGROUND_WATER_CREATURE, WATER_CREATURE, WATER_AMBIENT, MISC
// net.minecraft.world.entity.item.ItemEntity
ItemEntity(Level level, double x, double y, double z, ItemStack stack); ItemEntity(Level, double x,double y,double z, ItemStack stack, double dx,double dy,double dz);
ItemStack getItem(); void setItem(ItemStack); void setPickUpDelay(int); void setDefaultPickUpDelay(); void setNoPickUpDelay(); void setNeverPickUp(); boolean hasPickUpDelay(); void setThrower(Entity); void setTarget(@Nullable UUID); @Nullable Entity getOwner(); int getAge(); void setUnlimitedLifetime(); void setExtendedLifetime(); public int lifespan;
// net.minecraft.world.entity.item.FallingBlockEntity
static FallingBlockEntity fall(Level level, BlockPos pos, BlockState state); void setHurtsEntities(float damagePerDistance, int damageMax); void disableDrop(); BlockState getBlockState(); public int time; public boolean dropItem; public @Nullable CompoundTag blockData;
// net.minecraft.world.entity.LightningBolt — create via EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED); then bolt.snapTo(Vec3.atBottomCenterOf(pos)); level.addFreshEntity(bolt)
void setVisualOnly(boolean); void setCause(@Nullable ServerPlayer); @Nullable ServerPlayer getCause(); void setDamage(float) / float getDamage() (NeoForge); public long seed;
```

## F4. DamageSources / DamageSource (V: JD net/minecraft/world/damagesource/DamageSources.html, DamageSource.html, DamageTypes.html, net/minecraft/tags/DamageTypeTags.html)
```java
// net.minecraft.world.damagesource.DamageSources (obtain via level.damageSources() / entity.damageSources()) — all return DamageSource:
source(ResourceKey<DamageType> key); source(ResourceKey<DamageType> key, @Nullable Entity cause); source(ResourceKey<DamageType> key, @Nullable Entity direct, @Nullable Entity causing);
inFire(); onFire(); lava(); hotFloor(); campfire(); lightningBolt(); inWall(); cramming(); drown(); starve(); cactus(); fall(); flyIntoWall(); fellOutOfWorld(); generic(); genericKill(); magic(); wither(); dragonBreath(); dryOut(); sweetBerryBush(); freeze(); stalagmite(); outOfBorder(); enderPearl();
fallingBlock(Entity); anvil(Entity); fallingStalactite(Entity); sting(LivingEntity); mobAttack(LivingEntity); noAggroMobAttack(LivingEntity); playerAttack(Player); arrow(AbstractArrow, @Nullable Entity owner); trident(Entity, @Nullable Entity); mobProjectile(Entity, @Nullable LivingEntity);
thrown(Entity, @Nullable Entity); indirectMagic(Entity, @Nullable Entity); thorns(Entity); explosion(@Nullable Explosion); explosion(@Nullable Entity entity, @Nullable Entity cause); sonicBoom(Entity); badRespawnPointExplosion(Vec3); mace(Entity owner);
// outOfWorld() does NOT exist -> fellOutOfWorld(); DamageTypes.OUT_OF_WORLD does NOT exist -> DamageTypes.FELL_OUT_OF_WORLD
// net.minecraft.world.damagesource.DamageSource
DamageSource(Holder<DamageType> type, @Nullable Entity direct, @Nullable Entity causing, @Nullable Vec3 pos); DamageSource(Holder<DamageType>, @Nullable Entity direct, @Nullable Entity causing); DamageSource(Holder<DamageType>, @Nullable Entity causing); DamageSource(Holder<DamageType>);
@Nullable Entity getEntity(); @Nullable Entity getDirectEntity(); boolean is(TagKey<DamageType>); boolean is(ResourceKey<DamageType>); DamageType type(); Holder<DamageType> typeHolder(); @Nullable Vec3 getSourcePosition(); boolean isCreativePlayer(); String getMsgId();
// DamageTypes (ResourceKey<DamageType>): MAGIC, IN_FIRE, ON_FIRE, LAVA, DROWN, STARVE, FALL, GENERIC, FELL_OUT_OF_WORLD, EXPLOSION, PLAYER_EXPLOSION, LIGHTNING_BOLT, WITHER, FREEZE, MOB_ATTACK, PLAYER_ATTACK, INDIRECT_MAGIC, OUTSIDE_BORDER, GENERIC_KILL
// DamageTypeTags (TagKey<DamageType>): BYPASSES_ARMOR, BYPASSES_SHIELD, BYPASSES_INVULNERABILITY, BYPASSES_EFFECTS, BYPASSES_RESISTANCE, IS_FIRE, IS_PROJECTILE, IS_EXPLOSION, IS_FALL, IS_DROWNING, IS_FREEZING, IS_LIGHTNING, NO_KNOCKBACK, IS_PLAYER_ATTACK
```

## Key renames / surprises (26.1 vs 1.21)
- `Level.isClientSide()` method; `getDayTime()`/`isDay()`/`isNight()` GONE -> `getDefaultClockTime()`/`getOverworldClockTime()`/`clockManager()`, `isBrightOutside()`/`isDarkOutside()`. `getGameTime()` remains (LevelAccessor default).
- `ServerLevel.setDayTime`/`setWeatherParameters` GONE -> `getWeatherData()` (SavedData `WeatherData`) setters + `resetWeatherCycle()`; time via `ServerClockManager` (`clockManager()`), `Holder<WorldClock>`-keyed.
- `getSharedSpawnPos` -> `getRespawnData()`/`setRespawnData(LevelData.RespawnData)`. `getGameRules()` only on ServerLevel; package `net.minecraft.world.level.gamerules`. `DimensionDataStorage` -> `SavedDataStorage`. `ResourceLocation` -> `net.minecraft.resources.Identifier`.
- `Level.playSound` first param is `@Nullable Entity except` (no Player overloads). `destroyBlock` 4-arg has `int updateLimit`. `addFreshEntityWithPassengers` -> `tryAddFreshEntityWithPassengers`. `getForcedChunks` -> `getForceLoadedChunks()`.
- `Entity.moveTo`/`absMoveTo` -> `snapTo`/`absSnapTo`; `lerpTo` -> `moveOrInterpolateTo`; `getTags()` -> `entityTags()`; `isControlledByLocalInstance` -> `isLocalInstanceAuthoritative`; `canChangeDimensions` -> `canTeleport(Level, Level)`; `interactAt` -> `interact(Player, InteractionHand, Vec3)`; `fallDistance` is `double`; `hurt(DamageSource,float)` is back alongside `hurtServer`.
- `ChunkPos` is a record (`x()`, `z()`, `pack()`, `containing()`, `unpack()`); `GenerationStep.Carving` removed; `ChunkAccess.getStatus()` -> `getPersistedStatus()`; `setUnsaved(boolean)` -> `markUnsaved()`.
- `Direction.getNormal()` -> `getUnitVec3i()`; `Direction.getNearest(double x3)` -> `getApproximateNearest`. `Vec3.fromRGB24` removed. `EntityDataSerializers.OPTIONAL_UUID`/`COMPOUND_TAG` removed. `MobSpawnType` -> `EntitySpawnReason`. `MobEffects.MOVEMENT_SLOWDOWN`/`DAMAGE_RESISTANCE`/`CONFUSION` -> `SLOWNESS`/`RESISTANCE`/`NAUSEA`.

---

# G. Menus, Screens, Transfer API (MC 26.1 / NeoForge 26.1.2.76)

Legend: `V(url)` = VERIFIED at that URL; `U(reason)` = UNVERIFIED best guess. JD = `https://lexxie.dev/neoforge/26.1/` (public-only javadoc: protected members are marked U unless seen in a patch/test/docs). GH = `https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/`.

## G1. Menus (`net.minecraft.world.inventory`)

### AbstractContainerMenu — V(JD net/minecraft/world/inventory/AbstractContainerMenu.html)
```java
package net.minecraft.world.inventory;
public abstract class AbstractContainerMenu {
  public final NonNullList<Slot> slots;  public final int containerId;        // net.minecraft.core.NonNullList
  public static final int SLOT_SIZE, SLOTS_PER_ROW, SLOT_CLICKED_OUTSIDE, CARRIED_SLOT_SIZE, QUICKCRAFT_*;
  protected AbstractContainerMenu(@Nullable MenuType<?> menuType, int containerId);   // V(docs menus page: super(MY_MENU.get(), containerId))
  protected Slot addSlot(Slot slot);                                                   // V(docs menus page + tests ContainerTypeTest use this.addSlot)
  protected DataSlot addDataSlot(DataSlot intValue); protected void addDataSlots(ContainerData array); // V(docs menus page usage)
  protected void addStandardInventorySlots(Inventory inventory, int x, int y);        // V(docs menus page: addStandardInventorySlots(playerInventory, 8, 84))
  protected void addInventoryHotbarSlots(Inventory inv, int x, int y); protected void addInventoryExtendedSlots(Inventory inv, int x, int y); // U(vanilla 1.21.4+, not in public javadoc)
  protected boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection); // V(docs menus page)
  protected static void checkContainerSize(Container c, int min); protected static void checkContainerDataCount(ContainerData d, int min); // V(docs: checkContainerDataCount)
  protected static boolean stillValid(ContainerLevelAccess access, Player player, Block targetBlock); // V(docs menus page)
  public MenuType<?> getType();  public Slot getSlot(int index);  public boolean isValidSlotIndex(int slotIndex);
  public abstract ItemStack quickMoveStack(Player player, int slotIndex);
  public abstract boolean stillValid(Player player);
  public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player); // ClickType -> ContainerInput (enum, same pkg): PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL
  public boolean clickMenuButton(Player player, int buttonId);
  public void addSlotListener(ContainerListener l); public void removeSlotListener(ContainerListener l); public void setSynchronizer(ContainerSynchronizer s);
  public void sendAllDataToRemote(); public void broadcastChanges(); public void broadcastFullState();
  public void removed(Player player); public void slotsChanged(Container container);
  public void setData(int id, int value); public void setItem(int slot, int stateId, ItemStack itemStack);
  public ItemStack getCarried(); public void setCarried(ItemStack carried); public NonNullList<ItemStack> getItems();
  public boolean canTakeItemForPickAll(ItemStack carried, Slot target); public boolean canDragTo(Slot slot);
  public static int getQuickcraftType(int mask); public static boolean isValidQuickcraftType(int type, Player player);
  public static boolean canItemQuickReplace(@Nullable Slot slot, ItemStack itemStack, boolean ignoreSize);
  public static int getRedstoneSignalFromBlockEntity(@Nullable BlockEntity be); public static int getRedstoneSignalFromContainer(@Nullable Container c);
  public int getStateId(); public int incrementStateId();
}
```
quickMoveStack pattern (V docs/inventories/menus): copy `slots.get(i).getItem()`; `moveItemStackTo(raw, from, toExclusive, reverse)`; if `raw.isEmpty()` → `slot.setByPlayer(ItemStack.EMPTY)` else `slot.setChanged()`; then `slot.onTake(player, raw)`; return copy (or `ItemStack.EMPTY` if nothing moved).

### Slot — V(JD .../inventory/Slot.html)
```java
package net.minecraft.world.inventory;
public class Slot {
  public final Container container; public int index; public final int x; public final int y;   // x/y are public final
  public Slot(Container container, int slot, int x, int y);
  public ItemStack getItem(); public boolean hasItem(); public void set(ItemStack itemStack);
  public void setByPlayer(ItemStack itemStack); public void setByPlayer(ItemStack itemStack, ItemStack previous);
  public boolean mayPlace(ItemStack itemStack); public boolean mayPickup(Player player);
  public int getMaxStackSize(); public int getMaxStackSize(ItemStack itemStack);
  public ItemStack remove(int amount); public void onTake(Player player, ItemStack carried); public void onQuickCraft(ItemStack picked, ItemStack original);
  public void setChanged(); public boolean isActive(); public boolean isHighlightable(); public boolean isFake();
  public int getContainerSlot(); public int getSlotIndex();                     // getSlotIndex/isSameInventory/setBackground = NeoForge additions
  public @Nullable Identifier getNoItemIcon();                                   // name unchanged; returns net.minecraft.resources.Identifier (ResourceLocation renamed)
  public Slot setBackground(Identifier sprite); public boolean isSameInventory(Slot other); public boolean allowModification(Player player);
  public ItemStack safeInsert(ItemStack stack); public ItemStack safeInsert(ItemStack s, int amount); public ItemStack safeTake(int amount, int max, Player p); public Optional<ItemStack> tryRemove(int amount, int max, Player p);
}
```

### ResourceHandlerSlot — V(JD net/neoforged/neoforge/transfer/item/ResourceHandlerSlot.html)
```java
package net.neoforged.neoforge.transfer.item;
public class ResourceHandlerSlot extends net.neoforged.neoforge.world.inventory.StackCopySlot {   // ONLY constructor:
  public ResourceHandlerSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> slotModifier, int handlerSlot, int xPosition, int yPosition);
  public ResourceHandler<ItemResource> getResourceHandler(); // + overrides mayPlace/mayPickup/getMaxStackSize/onQuickCraft/isSameInventory
}
// net.neoforged.neoforge.transfer.IndexModifier<T>: functional shape (index, resource, amount) -> void; StacksResourceHandler#set(int,T,int) matches it.
// Usage (V docs): this.addSlot(new ResourceHandlerSlot(inv, inv::set, i, x, y));   // inv is an ItemStacksResourceHandler
```

### DataSlot / ContainerData / ContainerLevelAccess / MenuType — all V(JD)
```java
public abstract class DataSlot { public DataSlot(); public abstract int get(); public abstract void set(int value); public boolean checkAndClearUpdateFlag();
  public static DataSlot standalone(); public static DataSlot shared(int[] storage, int index); public static DataSlot forContainer(ContainerData container, int dataId); }
public interface ContainerData { int get(int index); void set(int index, int value); int getCount(); }   // U(unchanged vanilla)
public class SimpleContainerData implements ContainerData { public SimpleContainerData(int size); }        // V(docs menus page usage)
public interface ContainerLevelAccess { static final ContainerLevelAccess NULL; static ContainerLevelAccess create(Level level, BlockPos pos);
  <T> Optional<T> evaluate(BiFunction<Level,BlockPos,T> action); default <T> T evaluate(BiFunction<Level,BlockPos,T> action, T defaultValue); default void execute(BiConsumer<Level,BlockPos> action); }
public class MenuType<T extends AbstractContainerMenu> implements FeatureElement, IMenuTypeExtension<T> {
  public MenuType(MenuType.MenuSupplier<T> constructor, FeatureFlagSet requiredFeatures);   // MenuSupplier: T create(int containerId, Inventory inventory)
  public T create(int containerId, Inventory inventory); public T create(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData); }
// net.neoforged.neoforge.common.extensions.IMenuTypeExtension: static <T extends AbstractContainerMenu> MenuType<T> create(IContainerFactory<T> factory);  V(docs+test: IMenuTypeExtension.create(TestContainer::new))
// net.neoforged.neoforge.network.IContainerFactory<T extends AbstractContainerMenu> extends MenuType.MenuSupplier<T> { T create(int windowId, Inventory inv, RegistryFriendlyByteBuf data); }  V(GH src/.../network/IContainerFactory.java)
// net.minecraft.world.MenuProvider { @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player); Component getDisplayName(); }  V(test ContainerTypeTest)
// net.minecraft.world.SimpleMenuProvider(MenuConstructor constructor, Component title); MenuConstructor = (containerId, inv, player) -> menu  V(docs)
// IPlayerExtension#openMenu(MenuProvider) / openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf> extraDataWriter)  V(IContainerFactory javadoc link + test: player.openMenu(provider, buf -> buf.writeUtf(text)))
// Identifier: net.minecraft.resources.Identifier.fromNamespaceAndPath(ns, path) V(Capabilities.java + test); 1.21.11 primer shows withNamespaceAndPath -> prefer fromNamespaceAndPath (used by 26.1.x sources)
// Register client screen (mod bus): RegisterMenuScreensEvent#register(MenuType<T>, MenuScreens.ScreenConstructor<T,U>)  V(test: event.register(TYPE.get(), TestGui::new))  pkg net.neoforged.neoforge.client.event
```

### Container / SimpleContainer — V(JD net/minecraft/world/Container.html, SimpleContainer.html)
```java
package net.minecraft.world;
public interface Container extends Clearable, Iterable<ItemStack>, SlotProvider, ContainerExtension {
  static final float DEFAULT_DISTANCE_BUFFER;   // (LARGE_MAX_STACK_SIZE not present in 26.1 JD)
  int getContainerSize(); boolean isEmpty(); ItemStack getItem(int slot); ItemStack removeItem(int slot, int count); ItemStack removeItemNoUpdate(int slot); void setItem(int slot, ItemStack itemStack);
  void setChanged(); boolean stillValid(Player player); default int getMaxStackSize(); default int getMaxStackSize(ItemStack itemStack);
  default void startOpen(ContainerUser containerUser); default void stopOpen(ContainerUser containerUser); default List<ContainerUser> getEntitiesWithContainerOpen();   // ContainerUser (net.minecraft.world.entity), NOT Player
  default boolean canPlaceItem(int slot, ItemStack itemStack); default boolean canTakeItem(Container into, int slot, ItemStack itemStack); default int countItem(Item item); default boolean hasAnyOf(Set<Item>); default boolean hasAnyMatching(Predicate<ItemStack>);
  static boolean stillValidBlockEntity(BlockEntity be, Player player); static boolean stillValidBlockEntity(BlockEntity be, Player player, float distanceBuffer); default @Nullable SlotAccess getSlot(int slot); void clearContent(); }
public class SimpleContainer implements Container, StackedContentsCompatible {
  public SimpleContainer(int size); public SimpleContainer(ItemStack... itemstacks);
  public ItemStack addItem(ItemStack s); public boolean canAddItem(ItemStack s); public NonNullList<ItemStack> getItems(); public List<ItemStack> removeAllItems();
  public void fromItemList(ValueInput.TypedInputList<ItemStack> items); public void storeAsItemList(ValueOutput.TypedOutputList<ItemStack> output);  // replaces fromTag/createTag
  public void setItem(int slot, ItemStack s, boolean insideTransaction); // NeoForge ContainerExtension
  // addListener(ContainerListener)/removeListener: NOT in 26.1 public JD -> U(likely removed; use ContainerExtension#onTransfer or a BE hook)
}
// net.minecraft.world.inventory.ContainerListener#slotChanged(AbstractContainerMenu, int, ItemStack) + dataChanged(AbstractContainerMenu,int,int)  U(vanilla)
// Inventory (net.minecraft.world.entity.player.Inventory) implements Container  U(vanilla)
```

## G2. Transfer API (`net.neoforged.neoforge.transfer.*`) — V(JD package-summary pages)

Packages/classes (one line each):
- `transfer`: `ResourceHandler<T>` (core interface), `ResourceHandlerUtil` (move/insertStacking/extractFirst/isEmpty/isFull/indexOf), `StacksResourceHandler<S,T>` (abstract list-backed base, ValueIO serializable), `ResourceStacksResourceHandler<R>`, `CombinedResourceHandler`, `DelegatingResourceHandler`, `RangedResourceHandler`, `EmptyResourceHandler`, `InfiniteResourceHandler`, `VoidingResourceHandler`, `ItemAccessResourceHandler`, `IndexModifier<T>`, `TransferPreconditions`.
- `transfer.item`: `ItemResource`, `ItemStacksResourceHandler` (N-slot inventory, replaces ItemStackHandler), `ItemStackResourceHandler` (single stack), `ResourceHandlerSlot`, `ItemUtil`, `PlayerInventoryWrapper`, `VanillaContainerWrapper`, `WorldlyContainerWrapper`, `CarriedSlotWrapper`, `ComposterWrapper`, `BundleItemHandler`, `ItemAccessItemHandler`, `LivingEntityEquipmentWrapper`, `ContainerOrHandler`, `TransactionalRandom`, `VanillaInventoryCodeHooks`.
- `transfer.fluid`: `FluidResource`, `FluidStacksResourceHandler` (N tanks w/ capacity — the "FluidTank" replacement), `FluidUtil`, `BucketResourceHandler`, `CauldronWrapper`, `ItemAccessFluidHandler`, `DispenseFluidContainer`.
- `transfer.transaction`: `Transaction` (final, AutoCloseable), `TransactionContext`, `SnapshotJournal<T>`, `RootCommitJournal`, `Transaction.Lifecycle`.
- `transfer.access`: `ItemAccess` (context type for item caps), `HandlerItemAccess`.
- `transfer.energy`: `EnergyHandler` (cap type; U: methods insert/extract(int, TransactionContext), getAmountAsLong/getCapacityAsLong), `transfer.resource`: `Resource`, `ResourceStack<T>` record (resource, amount), `RegisteredResource`, `DataComponentHolderResource`.
- Legacy `net.neoforged.neoforge.items.ItemStackHandler`/`IItemHandler`/`SlotItemHandler` STILL EXIST but `@Deprecated(since="1.21.9", forRemoval=true)` → "Use ItemStacksResourceHandler instead". V(JD items/ItemStackHandler.html). `net.neoforged.neoforge.fluids.FluidStack`/`FluidType` remain (not deprecated).

```java
package net.neoforged.neoforge.transfer;                                     // V(JD ResourceHandler.html)
public interface ResourceHandler<T extends Resource> {
  int size(); T getResource(int index); long getAmountAsLong(int index); default int getAmountAsInt(int index);
  long getCapacityAsLong(int index, T resource); default int getCapacityAsInt(int index, T resource); boolean isValid(int index, T resource);
  int insert(int index, T resource, int amount, TransactionContext transaction); default int insert(T resource, int amount, TransactionContext transaction);
  int extract(int index, T resource, int amount, TransactionContext transaction); default int extract(T resource, int amount, TransactionContext transaction);
  static <T extends Resource> Class<ResourceHandler<T>> asClass();
}
public abstract class StacksResourceHandler<S, T extends Resource> implements ResourceHandler<T>, ValueIOSerializable {   // V(GH src StacksResourceHandler.java)
  public static final String VALUE_IO_KEY = "stacks"; protected NonNullList<S> stacks;
  protected StacksResourceHandler(int size, S emptyStack, Codec<S> stackCodec); protected StacksResourceHandler(NonNullList<S> stacks, S emptyStack, Codec<S> stackCodec);
  public void serialize(ValueOutput output); public void deserialize(ValueInput input);    // output.store("stacks", codec, stacks)
  public void set(int index, T resource, int amount);            // direct overwrite; usable as IndexModifier
  public NonNullList<S> copyToList(); protected void setStacks(NonNullList<S> stacks);
  protected abstract int getCapacity(int index, T resource);     // ItemStacks default: max stack size; FluidStacks default: ctor capacity
  protected void onContentsChanged(int index, S previousContents) {}   // called immediately on set(); at root-commit for insert/extract
  public boolean isValid(int index, T resource) { return true; }  protected boolean matches(S stack, T resource);
}
package net.neoforged.neoforge.transfer.item;                                // V(JD ItemStacksResourceHandler.html)
public class ItemStacksResourceHandler extends StacksResourceHandler<ItemStack, ItemResource> {
  public ItemStacksResourceHandler(int size); public ItemStacksResourceHandler(NonNullList<ItemStack> stacks);
  public ItemResource getResourceFrom(ItemStack s); public int getAmountFrom(ItemStack s); public boolean matches(ItemStack s, ItemResource r); }
  // read a slot as ItemStack: handler.getResource(i).toStack(handler.getAmountAsInt(i))  (no getStackInSlot); ItemUtil may have a helper -> U
public final class ItemResource implements DataComponentHolderResource<Item> {                 // V(JD)
  public static final ItemResource EMPTY; CODEC; OPTIONAL_CODEC; STREAM_CODEC;
  public static ItemResource of(ItemStack stack); of(ItemLike item); of(ItemLike item, DataComponentPatch p); of(Holder<Item> h); of(Holder<Item> h, DataComponentPatch p); of(@Nullable ItemStackTemplate t);
  public Item getItem(); public Item value(); public Holder<Item> typeHolder(); public boolean isEmpty(); public boolean is(ItemLike item);
  public boolean matches(ItemStack stack); public boolean test(Predicate<ItemStack> p); public ItemStack toStack(); public ItemStack toStack(int count); public int getMaxStackSize(); public Component getHoverName();
  public DataComponentMap getComponents(); public DataComponentPatch getComponentsPatch(); public <D> ItemResource with(DataComponentType<D> t, @Nullable D d); public ItemResource without(DataComponentType<?> t); // + get/has from DataComponentHolder
}
package net.neoforged.neoforge.transfer.fluid;                               // V(JD FluidResource.html, FluidStacksResourceHandler.html)
public final class FluidResource implements DataComponentHolderResource<Fluid> {
  public static final FluidResource EMPTY; CODEC; OPTIONAL_CODEC; STREAM_CODEC;
  public static FluidResource of(FluidStack stack); of(Fluid fluid); of(Fluid fluid, DataComponentPatch p); of(Holder<Fluid> h); of(Holder<Fluid> h, DataComponentPatch p); of(@Nullable FluidStackTemplate t);
  public Fluid getFluid(); public Fluid value(); public Holder<Fluid> typeHolder(); public FluidType getFluidType(); public boolean isEmpty(); public boolean is(FluidType t);
  public boolean matches(FluidStack s); public FluidStack toStack(int amount); public Component getHoverName(); // + with/without/getComponents
}
public class FluidStacksResourceHandler extends StacksResourceHandler<FluidStack, FluidResource> {
  public FluidStacksResourceHandler(int size, int capacity); public FluidStacksResourceHandler(NonNullList<FluidStack> stacks, int capacity); }
  // single tank: new FluidStacksResourceHandler(1, 4000); contents: FluidUtil.getStack(tank, 0) or tank.getResource(0).toStack(tank.getAmountAsInt(0))
public final class FluidUtil {                                               // V(JD transfer/fluid/FluidUtil.html) -- MOVED from net.neoforged.neoforge.fluids
  public static FluidStack getStack(ResourceHandler<FluidResource> handler, int index);
  public static FluidStack getFirstStackContained(ItemStack stack);
  public static boolean interactWithFluidHandler(Player player, InteractionHand hand, Level level, BlockPos pos, @Nullable Direction side, @Nullable TransactionContext transaction);
  public static boolean interactWithFluidHandler(Player player, InteractionHand hand, @Nullable BlockPos pos, ResourceHandler<FluidResource> handler, @Nullable TransactionContext transaction);
  // overloads WITHOUT the TransactionContext param exist but are @Deprecated(forRemoval). Also tryPickupFluid(...)/tryPlaceFluid(...) overloads, triggerSoundAndGameEvent(FluidResource, Level, Vec3, Player, boolean).
}
package net.neoforged.neoforge.fluids;                                       // V(JD fluids/FluidStack.html index)
public final class FluidStack { public static final FluidStack EMPTY; CODEC; OPTIONAL_CODEC; STREAM_CODEC; OPTIONAL_STREAM_CODEC; MAP_CODEC;
  public FluidStack(Fluid fluid, int amount); FluidStack(Holder<Fluid> fluid, int amount); FluidStack(Fluid, int, DataComponentPatch); FluidStack(Holder<Fluid>, int, DataComponentPatch);
  public Fluid getFluid(); public int getAmount(); public int amount(); public void setAmount(int); public boolean isEmpty(); public FluidStack copy(); public FluidStack copyWithAmount(int);
  public void grow(int); public void shrink(int); public FluidStack split(int); public static boolean isSameFluid(FluidStack, FluidStack); isSameFluidSameComponents(FluidStack, FluidStack); public Component getHoverName(); public static Codec<FluidStack> fixedAmountCodec(int); }
// net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME = 1000  V(ResourceHandlerUtil javadoc example uses FluidType.BUCKET_VOLUME)
package net.neoforged.neoforge.transfer.transaction;                         // V(JD Transaction.html)
public final class Transaction implements AutoCloseable, TransactionContext {
  public static Transaction openRoot(); public static Transaction open(@Nullable TransactionContext parent);  // null == openRoot()
  public void commit(); public void close(); public int depth(); public static Transaction.Lifecycle getLifecycle(); @Deprecated public static @Nullable TransactionContext getCurrentOpenedTransaction(); }
// Pattern:  try (Transaction tx = Transaction.openRoot()) { int n = handler.insert(res, amt, tx); if (n == amt) tx.commit(); }   // not committing == rollback
public final class ResourceHandlerUtil {                                     // V(JD)
  public static <T extends Resource> int move(@Nullable ResourceHandler<T> from, @Nullable ResourceHandler<T> to, Predicate<T> filter, int amount, @Nullable TransactionContext tx); // null tx => own root tx, auto-commit
  public static <T> int moveStacking(...same...); public static <T> @Nullable ResourceStack<T> moveFirst(...same...); moveFirstStacking(...);
  public static <T> int insertStacking(@Nullable ResourceHandler<T> h, T resource, int amount, @Nullable TransactionContext tx);
  public static <T> @Nullable ResourceStack<T> extractFirst(@Nullable ResourceHandler<T> h, Predicate<T> filter, int amount, @Nullable TransactionContext tx);
  public static boolean isEmpty(ResourceHandler<?> h); public static <T> boolean isFull(ResourceHandler<T> h); public static boolean isEmpty(Resource r, int amount);
  public static <T> boolean contains(ResourceHandler<T> h, T r); public static <T> int indexOf(ResourceHandler<T> h, T r); public static <T> int getRedstoneSignalFromResourceHandler(ResourceHandler<T> h); }
package net.neoforged.neoforge.transfer.access;                              // V(JD ItemAccess.html)
public interface ItemAccess { static ItemAccess forPlayerInteraction(Player player, InteractionHand hand); static ItemAccess forPlayerSlot(Player p, int slot); static ItemAccess forPlayerCursor(Player p, AbstractContainerMenu menu);
  static ItemAccess forStack(ItemStack stack); static ItemAccess forHandlerIndex(ResourceHandler<ItemResource> h, int index); static ItemAccess forInfiniteMaterials(Player p, ItemStack contents);
  ItemResource getResource(); int getAmount(); int insert(ItemResource r, int amount, TransactionContext tx); int extract(ItemResource r, int amount, TransactionContext tx);
  default int exchange(ItemResource newResource, int amount, @Nullable TransactionContext tx); default <T> @Nullable T getCapability(ItemCapability<T, ItemAccess> capability); }
```

### Capabilities — V(GH src/main/java/net/neoforged/neoforge/capabilities/Capabilities.java)
```java
package net.neoforged.neoforge.capabilities;
public final class Capabilities {
  public static final class Item {  BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction> BLOCK;  EntityCapability<ResourceHandler<ItemResource>, @Nullable Void> ENTITY;
                                     EntityCapability<ResourceHandler<ItemResource>, @Nullable Direction> ENTITY_AUTOMATION;  ItemCapability<ResourceHandler<ItemResource>, ItemAccess> ITEM; }
  public static final class Fluid { BlockCapability<ResourceHandler<FluidResource>, @Nullable Direction> BLOCK; EntityCapability<ResourceHandler<FluidResource>, @Nullable Direction> ENTITY; ItemCapability<ResourceHandler<FluidResource>, ItemAccess> ITEM; }
  public static final class Energy { BlockCapability<EnergyHandler, @Nullable Direction> BLOCK; EntityCapability<EnergyHandler, @Nullable Direction> ENTITY; ItemCapability<EnergyHandler, ItemAccess> ITEM; }
}
// BlockCapability.create(Identifier, Class<T>, Class<C>) / createSided(Identifier, Class<T>) / createVoid(Identifier, Class<T>)  V(docs capabilities)
// RegisterCapabilitiesEvent (V JD):
public <T, C extends @Nullable Object, BE extends BlockEntity> void registerBlockEntity(BlockCapability<T,C> capability, BlockEntityType<BE> blockEntityType, ICapabilityProvider<? super BE, C, T> provider);
public <T, C> void registerBlock(BlockCapability<T,C> capability, IBlockCapabilityProvider<T,C> provider, Block... blocks);   // provider: (level, pos, state, be, ctx) -> T
public <T, C, E extends Entity> void registerEntity(EntityCapability<T,C> capability, EntityType<E> entityType, ICapabilityProvider<? super E, C, T> provider);
public <T, C> void registerItem(ItemCapability<T,C> capability, ICapabilityProvider<ItemStack, C, T> provider, ItemLike... items);   // provider: (stack, itemAccess) -> T
// ICapabilityProvider<O, C, T> { @Nullable T getCapability(O object, C context); }  V(docs lambda shape (be, side) -> ...)
// Query: level.getCapability(Capabilities.Item.BLOCK, pos, side)  or  level.getCapability(cap, pos, state, be, ctx); stack.getCapability(cap, ctx); entity.getCapability(cap, ctx)  V(docs)
// BlockCapabilityCache (V JD): static <T,C> BlockCapabilityCache<T,C> create(BlockCapability<T,C> cap, ServerLevel level, BlockPos pos, C context);
//   create(cap, ServerLevel level, BlockPos pos, C context, BooleanSupplier isValid, Runnable invalidationListener);  @Nullable T getCapability();  -- NOTE: ServerLevel, not Level
// level.invalidateCapabilities(pos) after cap changes (ILevelExtension).
```

### Recommended 26.1 patterns
```java
// (a) BE with 3-slot inventory
public class MyBE extends BlockEntity {
  public final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(3) {
    @Override protected void onContentsChanged(int index, ItemStack previous) { MyBE.this.setChanged(); }
    // optional: @Override public boolean isValid(int index, ItemResource r) { ... }
  };
  @Override protected void saveAdditional(ValueOutput output) { super.saveAdditional(output); output.putChild("inventory", inventory); }   // V(JD ValueOutputExtension#putChild(String, ValueIOSerializable)); ValueOutput#child(String) also exists
  @Override protected void loadAdditional(ValueInput input) { super.loadAdditional(input); input.readChild("inventory", inventory); }        // V(JD ValueInputExtension#readChild(String, ValueIOSerializable)) reads only if present
}
// Read a slot as ItemStack: ItemUtil.getStack(handler, i)  V(JD transfer/item/ItemUtil.html); ItemUtil.insertItemReturnRemaining(handler[, index], ItemStack, boolean simulate, @Nullable TransactionContext) -> leftover
// Wrap vanilla: VanillaContainerWrapper.of(Container); PlayerInventoryWrapper.of(Player); LivingEntityEquipmentWrapper.of(entity, EquipmentSlot)  V(docs transactions)
// Energy: net.neoforged.neoforge.transfer.energy.EnergyHandler { long getAmountAsLong(); default int getAmountAsInt(); long getCapacityAsLong(); default int getCapacityAsInt(); int insert(int amount, TransactionContext tx); int extract(int amount, TransactionContext tx); }  V(JD); impl: new SimpleEnergyHandler(int capacity) V(docs)
// mod bus: event.registerBlockEntity(Capabilities.Item.BLOCK, MY_BE_TYPE.get(), (be, side) -> be.inventory);
// (b) 4000 mB single tank
public final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 4000) { @Override protected void onContentsChanged(int i, FluidStack prev) { setChanged(); } };
// event.registerBlockEntity(Capabilities.Fluid.BLOCK, MY_BE_TYPE.get(), (be, side) -> be.tank);
// (c) bucket -> tank on right click (server side, in Block#useItemOn):
if (FluidUtil.interactWithFluidHandler(player, hand, pos, be.tank, null)) return InteractionResult.SUCCESS;   // V(JD signature); handles fill+drain, swaps bucket in hand
// manual variant: ItemAccess access = ItemAccess.forPlayerInteraction(player, hand); ResourceHandler<FluidResource> src = access.getCapability(Capabilities.Fluid.ITEM);
//   if (src != null) ResourceHandlerUtil.move(src, be.tank, r -> true, FluidType.BUCKET_VOLUME, null);
```

## G3. Screens (client)

```java
package net.minecraft.client.gui.screens;                                    // V(JD Screen.html)
public abstract class Screen extends AbstractContainerEventHandler implements Renderable {
  public int width, height; public final List<Renderable> renderables; protected final Component title; protected Font font; protected Minecraft minecraft; // title/font/minecraft: U(protected, vanilla-stable; test uses this.font)
  protected Screen(Component title);                                               // U(protected)
  protected void init(); public final void init(int width, int height); public void tick(); public void removed(); public void added(); public void resize(int w, int h);  // init(): U(protected)
  protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);  // V(GH Screen.java.patch context)
  protected <T extends Renderable> T addRenderableOnly(T r); protected <T extends GuiEventListener & NarratableEntry> T addWidget(T w); protected void removeWidget(GuiEventListener w); protected void clearWidgets(); protected void rebuildWidgets(); protected void repositionElements(); protected void setInitialFocus(); // rebuildWidgets/clearWidgets/removeWidget/init()/repositionElements V(patch); others U
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);          // was render()
  public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);           // was renderBackground()
  public void extractTransparentBackground(GuiGraphicsExtractor graphics);
  public static void extractMenuBackgroundTexture(GuiGraphicsExtractor g, Identifier menuBackground, int x, int y, float u, float v, int width, int height);
  public boolean keyPressed(KeyEvent event); public boolean shouldCloseOnEsc(); public void onClose(); public boolean isPauseScreen(); public boolean isInGameUi();
  public Font getFont(); public Minecraft getMinecraft(); public Component getTitle(); public List<? extends GuiEventListener> children(); public void clearFocus();
  public static List<Component> getTooltipFromItem(Minecraft mc, ItemStack stack);
}
// GuiEventListener (net.minecraft.client.gui.components.events) 26.1 signatures — V(JD AbstractContainerScreen/EditBox overrides):
boolean mouseClicked(MouseButtonEvent event, boolean doubleClick); boolean mouseReleased(MouseButtonEvent event); boolean mouseDragged(MouseButtonEvent event, double dx, double dy);
boolean mouseScrolled(double x, double y, double scrollX, double scrollY); void mouseMoved(double x, double y);
boolean keyPressed(KeyEvent event); boolean keyReleased(KeyEvent event); boolean charTyped(CharacterEvent event); boolean preeditUpdated(@Nullable PreeditEvent event);
package net.minecraft.client.input;                                          // V(JD)
public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) implements InputWithModifiers { int button(); int modifiers(); int input(); }
public record KeyEvent(int key, int scancode, int modifiers) implements InputWithModifiers {}     // key() = GLFW key code (org.lwjgl.glfw.GLFW.GLFW_KEY_*)
public record CharacterEvent(int codepoint) { String codepointAsString(); boolean isAllowedChatCharacter(); }
// InputWithModifiers: hasShiftDown(), hasControlDown(), hasAltDown(), isEscape(), isConfirmation(), isLeft(), isRight(), isUp(), isDown(), isCopy(), isPaste(), isCut(), isSelectAll(), isSelection(), isCycleFocus(), getDigit()
// com.mojang.blaze3d.platform.InputConstants.getKey(KeyEvent) -> InputConstants.Key; KeyMapping#isActiveAndMatches(Key)  V(ACS patch)

package net.minecraft.client.gui.screens.inventory;                          // V(JD AbstractContainerScreen.html + GH patch + tests ContainerTypeTest)
public abstract class AbstractContainerScreen<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T> {
  public static final Identifier INVENTORY_LOCATION;
  protected final T menu; protected int imageWidth, imageHeight, leftPos, topPos, titleLabelX, titleLabelY, inventoryLabelX, inventoryLabelY; protected @Nullable Slot hoveredSlot; // U(protected; patch uses this.leftPos/topPos/imageWidth/hoveredSlot)
  public AbstractContainerScreen(T menu, Inventory inventory, Component title);                       // defaults 176x166
  public AbstractContainerScreen(T menu, Inventory inventory, Component title, int imageWidth, int imageHeight);
  public abstract/override void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);  // <-- THIS is where you blit the GUI texture (renderBg merged in). V(test: TestGui overrides public extractBackground)
  public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);  public void extractContents(GuiGraphicsExtractor g, int mx, int my, float a);
  protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY);            // V(patch) title/inventory labels
  protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY);           // V(patch call this.extractTooltip)
  protected void renderSlotContents(GuiGraphicsExtractor g, ItemStack stack, Slot slot, @Nullable String itemCount); // NeoForge hook V(patch)
  protected void slotClicked(Slot slot, int slotId, int button, ContainerInput type);              // V(patch usage)
  protected boolean isHovering(int x, int y, int w, int h, double mouseX, double mouseY);           // U(protected, unchanged)
  protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop);              // V(patch usage)
  protected void containerTick();                                                                   // U — NOTE public tick() is FINAL in 26.1 (V JD); override containerTick()
  public final void tick(); public void onClose(); public void removed(); public boolean isPauseScreen();
  public T getMenu(); public @Nullable Slot getHoveredSlot(); public int getLeftPos(); public int getTopPos(); public int getImageWidth(); public int getImageHeight();  // NeoForge getters (getGuiLeft/getXSize etc. deprecated 26.1.2)
}
// Renderable#extractRenderState(GuiGraphicsExtractor, int mouseX, int mouseY, float a)  V(JD Screen "Specified by"); AbstractWidget#extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) V(JD EditBox override)
package net.minecraft.client.gui.components;                                 // V(JD Button, Button.Builder, Button.OnPress, EditBox)
Button.builder(Component message, Button.OnPress onPress) -> Button.Builder { pos(int x,int y); width(int); size(int w,int h); bounds(int x,int y,int w,int h); tooltip(@Nullable Tooltip); createNarration(Button.CreateNarration); Button build(); }
interface Button.OnPress { void onPress(Button button); }     // Button.onPress(InputWithModifiers) is the instance method; Button.DEFAULT_WIDTH=150? DEFAULT_HEIGHT=20 U(values)
public class EditBox extends AbstractWidget {
  public EditBox(Font font, int width, int height, Component narration); public EditBox(Font font, int x, int y, int width, int height, Component narration);
  public EditBox(Font font, int x, int y, int width, int height, @Nullable EditBox oldBox, Component narration);
  setMaxLength(int); setValue(String); String getValue(); setResponder(Consumer<String>); setBordered(boolean); setFocused(boolean); setEditable(boolean); setHint(Component); setFilter(Predicate<String>); setTextColor(int); setTextColorUneditable(int); setCanLoseFocus(boolean); setVisible(boolean); setSuggestion(@Nullable String); setCentered(boolean); setTextShadow(boolean); insertText(String); getCursorPosition(); }
// AbstractWidget: setTooltip(Tooltip), setPosition(int,int), setX/setY/setWidth/setHeight, getX/getY/getWidth/getHeight, active, visible, isHovered(), setMessage(Component), setAlpha(float)  V(JD inherited list)
```
Minecraft / Font:
```java
net.minecraft.client.Minecraft extends ReentrantBlockableEventLoop<Runnable>:  // V(JD Minecraft.html index: fields font, gameRenderer, options, level, player, screen; methods below present)
  static Minecraft getInstance(); public @Nullable LocalPlayer player; public @Nullable ClientLevel level; public final Font font; public @Nullable Screen screen; public final Options options; public final GameRenderer gameRenderer;
  void setScreen(@Nullable Screen); @Nullable ClientPacketListener getConnection(); TextureManager getTextureManager(); ItemModelResolver getItemModelResolver(); BlockModelResolver getBlockModelResolver(); Window getWindow(); EntityRenderDispatcher getEntityRenderDispatcher();
  DeltaTracker getDeltaTracker(); long getFrameTimeNs(); SoundManager getSoundManager(); boolean hasSingleplayerServer(); @Nullable IntegratedServer getSingleplayerServer(); RenderTarget getMainRenderTarget();
  // from BlockableEventLoop: execute(Runnable), submit(Runnable)->CompletableFuture, isSameThread()  U(inherited, names unchanged)
  // NeoForge IMinecraftExtension: pushGuiLayer(Screen), popGuiLayer()  V(JD) -- NOTE Screen#onClose() now calls minecraft.popGuiLayer() (V Screen patch)
  // Window: getGuiScaledWidth()/getGuiScaledHeight()  U(unchanged)
net.minecraft.client.gui.Font:  // V(JD Font.html)
  public final int lineHeight; int width(String str); int width(FormattedText text); int width(FormattedCharSequence text); List<FormattedCharSequence> split(FormattedText input, int maxWidth);
  int wordWrapHeight(FormattedText input, int textWidth); String plainSubstrByWidth(String str, int width[, boolean reverse]); FormattedText substrByWidth(FormattedText, int); List<FormattedText> splitIgnoringLanguage(FormattedText, int);
  // NO drawString/draw: use GuiGraphicsExtractor.text(...). Batch: drawInBatch(String|Component|FormattedCharSequence, float x, float y, int color, boolean dropShadow, Matrix4fc pose, MultiBufferSource, Font.DisplayMode, int backgroundColor, int packedLight)
```

## G4. GuiGraphicsExtractor — V(JD net/minecraft/client/gui/GuiGraphicsExtractor.html) full method list
```java
package net.minecraft.client.gui;
public class GuiGraphicsExtractor {
  public GuiGraphicsExtractor(Minecraft mc, GuiRenderState state, int w, int h);
  int guiWidth(); int guiHeight(); org.joml.Matrix3x2fStack pose(); void nextStratum(); void blurBeforeThisStratum();
  void enableScissor(int x0, int y0, int x1, int y1); void disableScissor(); boolean containsPointInScissor(int x, int y); peekScissorStack();
  void horizontalLine(int x0, int x1, int y, int color); void verticalLine(int x, int y0, int y1, int color);   // param names U
  void fill(int x0, int y0, int x1, int y1, int col); void fill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int col); void fill(RenderPipeline p, TextureSetup t, int x0, int y0, int x1, int y1);
  void fillGradient(int x0, int y0, int x1, int y1, int colorFrom, int colorTo); void outline(int x, int y, int width, int height, int color); void textHighlight(int, int, int, int, boolean);
  void text(Font font, @Nullable String str, int x, int y, int color); void text(Font, @Nullable String, int x, int y, int color, boolean dropShadow);
  void text(Font, FormattedCharSequence, int x, int y, int color[, boolean dropShadow]); void text(Font, Component, int x, int y, int color[, boolean dropShadow]);
  void centeredText(Font, String|Component|FormattedCharSequence, int x, int y, int color);
  void textWithWordWrap(Font font, FormattedText string, int x, int y, int width, int col[, boolean dropShadow]); void textWithBackdrop(Font, Component, int textX, int textY, int, int);
  void blit(RenderPipeline renderPipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight);
  void blit(RenderPipeline, Identifier, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, int color);
  void blit(RenderPipeline, Identifier, int x, int y, float u, float v, int width, int height, int srcWidth, int srcHeight, int textureWidth, int textureHeight[, int color]);
  void blit(Identifier location, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1); void blit(GpuTextureView, GpuSampler, int x0,int y0,int x1,int y1, float u0,float u1,float v0,float v1);
  void blitSprite(RenderPipeline, Identifier location, int x, int y, int width, int height); ...(+ float alpha) ...(+ int color);
  void blitSprite(RenderPipeline, Identifier, int spriteWidth, int spriteHeight, int textureX, int textureY, int x, int y, int width, int height[, int color]);
  void blitSprite(RenderPipeline, TextureAtlasSprite sprite, int x, int y, int width, int height[, int color]);
  void item(ItemStack itemStack, int x, int y); void item(ItemStack, int x, int y, int seed); void item(LivingEntity owner, ItemStack, int x, int y, int seed);
  void fakeItem(ItemStack, int x, int y[, int seed]); void itemDecorations(Font font, ItemStack, int x, int y[, @Nullable String countText]);
  void setTooltipForNextFrame(Component, int x, int y); setTooltipForNextFrame(List<Component>, int, int); setTooltipForNextFrame(Font, Component, int, int[, Identifier style]);
  void setTooltipForNextFrame(Font, ItemStack, int, int); setTooltipForNextFrame(Font, List<Component>, int, int[, Identifier]); setTooltipForNextFrame(Font, List<Component>, Optional<TooltipComponent>, [ItemStack,] int, int[, Identifier]);
  void setTooltipForNextFrame(Font, List<FormattedCharSequence>, ClientTooltipPositioner, int, int, boolean); setTooltipForNextFrame(Font, List<Component>, Optional<TooltipComponent>, ClientTooltipPositioner, int, int, boolean, Identifier);
  void setComponentTooltipForNextFrame(Font, List<Component>, int, int[, ItemStack][, Identifier]); setComponentTooltipFromElementsForNextFrame(Font, List<Either<FormattedText,TooltipComponent>>, int, int, ItemStack[, Identifier]);
  void tooltip(Font, List<ClientTooltipComponent>, int x, int y, ClientTooltipPositioner, Identifier[, ItemStack]);   // immediate; list elem types U
  void map(MapRenderState); void entity(EntityRenderState, float, Vector3f, Quaternionf, Quaternionf, int, int, int, int); skin(...); book(...); bannerPattern(...); sign(...); profilerChart(...);
  void submitGuiElementRenderState(GuiElementRenderState state); void submitPictureInPictureRenderState(PictureInPictureRenderState state);
  void requestCursor(CursorType); void applyCursor(Window); void setPreeditOverlay(Renderable); void extractDeferredElements(int, int, float); componentHoverEffect(Font, Style, int, int);
  TextureAtlasSprite getSprite(SpriteId); textRenderer(); textRenderer(HoveredTextEffects[, Consumer]); textRendererForWidget(AbstractWidget, HoveredTextEffects);
}
// net.minecraft.client.renderer.RenderPipelines (fields of type com.mojang.blaze3d.pipeline.RenderPipeline): GUI, GUI_TEXTURED, GUI_TEXTURED_PREMULTIPLIED_ALPHA, GUI_INVERT, GUI_TEXT, GUI_TEXT_INTENSITY, GUI_TEXT_HIGHLIGHT, GUI_OPAQUE_TEXTURED_BACKGROUND, GUI_NAUSEA_OVERLAY  V(JD RenderPipelines.html)
// Typical background: graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);  V(docs screens)
```

---

# H/I — Networking & Worldgen (MC 26.1 / NeoForge 26.1.2.76, Mojang mappings)

Legend: `V` = VERIFIED (URL follows), `U` = UNVERIFIED (best guess + reason).
NF = `https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/`; JD = `https://lexxie.dev/neoforge/26.1/` (public-only javadoc: protected members invisible).
Global rename reminders seen everywhere below: `ResourceLocation` -> `net.minecraft.resources.Identifier`; `GenerationStep.Carving` is GONE (JD `.../levelgen/GenerationStep.Carving.html` 404s).

## H. Networking

### H1. NeoForge payload API
`V` NF `src/main/java/net/neoforged/neoforge/network/handling/IPayloadContext.java`, `IPayloadHandler.java`
```java
package net.neoforged.neoforge.network.handling;
public interface IPayloadContext {                       // @ApiStatus.NonExtendable
    ICommonPacketListener listener();                    // net.neoforged.neoforge.common.extensions.ICommonPacketListener
    default Connection connection();                     // net.minecraft.network.Connection
    Player player();                                     // ServerPlayer (serverbound) / LocalPlayer (clientbound); throws UnsupportedOperationException in CONFIGURATION
    default void reply(CustomPacketPayload payload);
    default void disconnect(Component reason);
    CompletableFuture<Void> enqueueWork(Runnable task);   // main thread; auto exceptionally()-guarded on network thread
    <T> CompletableFuture<T> enqueueWork(Supplier<T> task);
    PacketFlow flow();                                   // net.minecraft.network.protocol.PacketFlow
    default ConnectionProtocol protocol();               // net.minecraft.network.ConnectionProtocol
    default void handle(Packet<?> packet);
    void handle(CustomPacketPayload payload);
    void finishCurrentTask(ConfigurationTask.Type type); // net.minecraft.server.network.ConfigurationTask
    default ChannelHandlerContext channelHandlerContext();
}
@FunctionalInterface public interface IPayloadHandler<T extends CustomPacketPayload> { void handle(T payload, IPayloadContext context); }
```
`V` NF `.../network/registration/PayloadRegistrar.java`, `HandlerThread.java`, `.../network/event/RegisterPayloadHandlersEvent.java`
```java
package net.neoforged.neoforge.network.registration;
public enum HandlerThread { MAIN, NETWORK }
public class PayloadRegistrar {
    public PayloadRegistrar(String version);
    // PLAY (RegistryFriendlyByteBuf). Each returns `this`.
    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler);
    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec); // handler later via RegisterClientPayloadHandlersEvent
    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler);
    public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> serverHandler, @Nullable IPayloadHandler<T> clientHandler);
    public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> serverHandler);
    // CONFIGURATION / COMMON: same shapes but StreamCodec<? super FriendlyByteBuf, T>: configurationToClient/ToServer/Bidirectional, commonToClient/ToServer/Bidirectional
    public PayloadRegistrar executesOn(HandlerThread thread); // returns a COPY (default MAIN wraps handlers in MainThreadPayloadHandler)
    public PayloadRegistrar versioned(String version);        // copy
    public PayloadRegistrar optional();                       // copy
}
package net.neoforged.neoforge.network.event;
public class RegisterPayloadHandlersEvent extends Event implements IModBusEvent { public PayloadRegistrar registrar(String version); }
```
`V` NF `src/client/java/net/neoforged/neoforge/client/network/event/RegisterClientPayloadHandlersEvent.java` — EXISTS (mod bus, client source set):
```java
package net.neoforged.neoforge.client.network.event;
public class RegisterClientPayloadHandlersEvent extends Event implements IModBusEvent {
    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, IPayloadHandler<T> handler);                       // MAIN
    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, HandlerThread thread, IPayloadHandler<T> handler);
}
```
`V` NF `.../network/PacketDistributor.java`, `src/client/.../client/network/ClientPacketDistributor.java`
```java
package net.neoforged.neoforge.network;
public final class PacketDistributor {
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... payloads);
    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload, CustomPacketPayload... payloads);
    public static void sendToPlayersNear(ServerLevel level, @Nullable ServerPlayer excluded, double x, double y, double z, double radius, CustomPacketPayload payload, CustomPacketPayload... payloads);
    public static void sendToAllPlayers(CustomPacketPayload payload, CustomPacketPayload... payloads);
    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload, CustomPacketPayload... payloads);
    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload, CustomPacketPayload... payloads);
    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload, CustomPacketPayload... payloads);
}   // multiple payloads -> one ClientboundBundlePacket
package net.neoforged.neoforge.client.network;
public final class ClientPacketDistributor { public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads); }
```

### H2. Vanilla payload / buffer types
`V` JD `net/minecraft/network/protocol/common/custom/CustomPacketPayload.html`
```java
package net.minecraft.network.protocol.common.custom;
public interface CustomPacketPayload {
    CustomPacketPayload.Type<? extends CustomPacketPayload> type();
    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B,T> codec(StreamMemberEncoder<B,T> writer, StreamDecoder<B,T> reader);
    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> createType(String id);       // "modid:name"
    record Type<T extends CustomPacketPayload>(Identifier id) {}   // U: component type Identifier — ctor page not fetched; consistent with global rename
}
```
`V` JD `net/minecraft/network/FriendlyByteBuf.html` — `package net.minecraft.network;` `class FriendlyByteBuf extends ByteBuf`; `RegistryFriendlyByteBuf extends FriendlyByteBuf` (`registryAccess()` — U, not fetched). Static `(ByteBuf, …)` twins exist for most:
```java
BlockPos readBlockPos(); FriendlyByteBuf writeBlockPos(BlockPos);  ChunkPos readChunkPos(); writeChunkPos(ChunkPos);  GlobalPos readGlobalPos(); writeGlobalPos(GlobalPos);
String readUtf(); String readUtf(int max); writeUtf(String); writeUtf(String,int);   int readVarInt(); writeVarInt(int); long readVarLong(); writeVarLong(long);
Identifier readIdentifier(); writeIdentifier(Identifier);                                   // NOT readResourceLocation
<T> ResourceKey<T> readResourceKey(ResourceKey<? extends Registry<T>> registry); writeResourceKey(ResourceKey<?>); ResourceKey<? extends Registry<?>> readRegistryKey();
<T> T readWithCodecTrusted(DynamicOps<Tag>, Codec<T>); readWithCodec(DynamicOps<Tag>, Codec<T>, NbtAccounter); writeWithCodec(DynamicOps<Tag>, Codec<T>, T);
<T> T readLenientJsonWithCodec(Codec<T>); writeJsonWithCodec(Codec<T>, T);                 // readJsonWithCodec GONE -> readLenientJsonWithCodec
Tag readNbt(); Tag readNbt(NbtAccounter); writeNbt(@Nullable Tag);   UUID readUUID(); writeUUID(UUID);   <T extends Enum<T>> T readEnum(Class<T>); writeEnum(Enum<?>);
<T,C extends Collection<T>> C readCollection(IntFunction<C>, StreamDecoder<? super FriendlyByteBuf,T>); <T> void writeCollection(Collection<T>, StreamEncoder<? super FriendlyByteBuf,T>); <T> List<T> readList(StreamDecoder);
readMap/writeMap, readOptional/writeOptional, readNullable/writeNullable, readEither/writeEither, readEnumSet/writeEnumSet, readByteArray/writeByteArray, readLongArray/writeLongArray, readVarIntArray/writeVarIntArray, readBitSet/writeBitSet, readInstant/writeInstant, readBlockHitResult/writeBlockHitResult, readContainerId/writeContainerId
Vector3f readVector3f(); writeVector3f(Vector3f); Quaternionf readQuaternion(); writeQuaternion(Quaternionf);   // NO readVec3/writeVec3 — use 3x double or ByteBufCodecs
```

### H3. Weather / time packets
`V` JD `net/minecraft/network/protocol/game/ClientboundGameEventPacket.html`, `ClientboundSetTimePacket.html`
```java
package net.minecraft.network.protocol.game;
public class ClientboundGameEventPacket implements Packet<ClientGamePacketListener> {
    public ClientboundGameEventPacket(ClientboundGameEventPacket.Type event, float param);
    public static final ClientboundGameEventPacket.Type NO_RESPAWN_BLOCK_AVAILABLE, START_RAINING, STOP_RAINING, CHANGE_GAME_MODE, WIN_GAME, DEMO_EVENT, PLAY_ARROW_HIT_SOUND,
        RAIN_LEVEL_CHANGE, THUNDER_LEVEL_CHANGE, PUFFER_FISH_STING, GUARDIAN_ELDER_EFFECT, IMMEDIATE_RESPAWN, LIMITED_CRAFTING, LEVEL_CHUNKS_LOAD_START;
    public ClientboundGameEventPacket.Type getEvent(); public float getParam();
}
// CHANGED: no dayTime/tickDayTime — time is per WorldClock now
public record ClientboundSetTimePacket(long gameTime, Map<Holder<WorldClock>, ClockNetworkState> clockUpdates) implements Packet<ClientGamePacketListener>
// net.minecraft.world.clock.WorldClock (empty record `WorldClock()`), net.minecraft.world.clock.ClockNetworkState(long totalTicks, float partialTick, float rate)
```

## I. Worldgen

### I1. ChunkGenerator / NoiseBasedChunkGenerator / ChunkAccess
`V` JD `net/minecraft/world/level/chunk/ChunkGenerator.html` (+ NF patch `patches/net/minecraft/world/level/chunk/ChunkGenerator.java.patch` only adds `refreshFeaturesPerStep`)
```java
package net.minecraft.world.level.chunk;
public abstract class ChunkGenerator {
    public static final Codec<ChunkGenerator> CODEC;
    public ChunkGenerator(BiomeSource biomeSource);
    public ChunkGenerator(BiomeSource biomeSource, Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter);
    protected abstract MapCodec<? extends ChunkGenerator> codec();   // U: protected, invisible in JD; unchanged since 1.20.5 (MapCodec) and Registries.CHUNK_GENERATOR is Registry<MapCodec<? extends ChunkGenerator>>
    public void validate();
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long legacyLevelSeed);
    public Optional<Identifier> getTypeNameForDataFixer();
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess protoChunk);
    public abstract void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk);
    public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> wantedStructures, BlockPos pos, int maxSearchRadius, boolean createReference);
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager);
    public abstract void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess protoChunk);
    public abstract void spawnOriginalMobs(WorldGenRegion worldGenRegion);
    public int getSpawnHeight(LevelHeightAccessor heightAccessor);
    public BiomeSource getBiomeSource();
    public abstract int getGenDepth();
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory mobCategory, BlockPos pos);   // net.minecraft.util.random.WeightedList
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager, ChunkAccess centerChunk, StructureTemplateManager structureTemplateManager, ResourceKey<Level> level);
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess centerChunk);
    public abstract CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess centerChunk);
    public abstract int getSeaLevel();  public abstract int getMinY();
    public abstract int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState);
    public abstract NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState);
    public int getFirstFreeHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState);
    public int getFirstOccupiedHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState);
    public abstract void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos);
    @Deprecated public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> biome);
    @ApiStatus.Internal public void refreshFeaturesPerStep();   // NeoForge
}
```
`V` JD `net/minecraft/core/registries/Registries.html`, `BuiltInRegistries.html`: `Registries.CHUNK_GENERATOR : ResourceKey<Registry<MapCodec<? extends ChunkGenerator>>>`, `Registries.BIOME_SOURCE : ResourceKey<Registry<MapCodec<? extends BiomeSource>>>`; `BuiltInRegistries.CHUNK_GENERATOR : Registry<MapCodec<? extends ChunkGenerator>>`, `BuiltInRegistries.BIOME_SOURCE : Registry<MapCodec<? extends BiomeSource>>`.

`V` JD `net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.html`, `NoiseGeneratorSettings.html`, `NoiseSettings.html`, `RandomState.html`
```java
package net.minecraft.world.level.levelgen;
public class NoiseBasedChunkGenerator extends ChunkGenerator {
    public static final MapCodec<NoiseBasedChunkGenerator> CODEC;
    public NoiseBasedChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings);
    public Holder<NoiseGeneratorSettings> generatorSettings();  public boolean stable(ResourceKey<NoiseGeneratorSettings> expectedPreset);
    public double getInterpolatedNoiseValue(RandomState randomState, DensityFunction.FunctionContext context);
    public void buildSurface(ChunkAccess protoChunk, WorldGenerationContext context, RandomState randomState, StructureManager structureManager, BiomeManager biomeManager, Registry<Biome> biomeRegistry, Blender blender); // extra public overload
    // doFill(...) is PRIVATE (not overridable) — note only. No public createRandomState.
}
public record NoiseGeneratorSettings(NoiseSettings noiseSettings, BlockState defaultBlock, BlockState defaultFluid, NoiseRouter noiseRouter, SurfaceRules.RuleSource surfaceRule,
        List<Climate.ParameterPoint> spawnTarget, int seaLevel, @Deprecated boolean disableMobGeneration, boolean aquifersEnabled, boolean oreVeinsEnabled, boolean useLegacyRandomSource) {
    public static final Codec<NoiseGeneratorSettings> DIRECT_CODEC; public static final Codec<Holder<NoiseGeneratorSettings>> CODEC;
    public static final ResourceKey<NoiseGeneratorSettings> OVERWORLD, LARGE_BIOMES, AMPLIFIED, NETHER, END, CAVES, FLOATING_ISLANDS;
    public boolean isAquifersEnabled(); public WorldgenRandom.Algorithm getRandomSource();
    public static NoiseGeneratorSettings overworld(BootstrapContext<?> context, boolean isAmplified, boolean largeBiomes); nether(ctx); end(ctx); caves(ctx); floatingIslands(ctx); dummy();
}
public record NoiseSettings(int minY, int height, int noiseSizeHorizontal, int noiseSizeVertical) { public static NoiseSettings create(int,int,int,int); int getCellWidth(); int getCellHeight(); NoiseSettings clampToHeightAccessor(LevelHeightAccessor); }
public final class RandomState {
    public static RandomState create(HolderGetter.Provider holders, ResourceKey<NoiseGeneratorSettings> noiseSettings, long seed);   // NOT RegistryAccess (RegistryAccess implements HolderGetter.Provider, so passing one works)
    public static RandomState create(NoiseGeneratorSettings settings, HolderGetter<NormalNoise.NoiseParameters> noises, long seed);
    public NormalNoise getOrCreateNoise(ResourceKey<NormalNoise.NoiseParameters> noise);  public PositionalRandomFactory getOrCreateRandomFactory(Identifier name);
    public NoiseRouter router(); public Climate.Sampler sampler(); public SurfaceSystem surfaceSystem(); public PositionalRandomFactory aquiferRandom(); public PositionalRandomFactory oreRandom();
}
```
`V` JD `.../levelgen/blending/Blender.html`, `.../level/NoiseColumn.html`, `.../level/StructureManager.html`, `.../level/WorldGenLevel.html`, `.../level/NaturalSpawner.html`
```java
package net.minecraft.world.level.levelgen.blending; public class Blender { public static Blender empty(); public static Blender of(@Nullable WorldGenRegion region); public boolean isEmpty(); }
package net.minecraft.world.level; public final class NoiseColumn implements BlockColumn { public NoiseColumn(int minY, BlockState[] column); public BlockState getBlock(int blockY); public void setBlock(int blockY, BlockState state); }
public class StructureManager {   // net.minecraft.world.level
    public StructureManager(LevelAccessor level, WorldOptions worldOptions, StructureCheck structureCheck);  public StructureManager forWorldGenRegion(WorldGenRegion region);
    public boolean shouldGenerateStructures();  public List<StructureStart> startsForStructure(ChunkPos pos, Predicate<Structure> matcher);  public List<StructureStart> startsForStructure(SectionPos pos, Structure structure);
    public @Nullable StructureStart getStartForStructure(SectionPos pos, Structure structure, StructureAccess chunk);  public void setStartForStructure(SectionPos, Structure, StructureStart, StructureAccess);  public void addReferenceForStructure(SectionPos, Structure, long reference, StructureAccess);
    public StructureStart getStructureAt(BlockPos, Structure);  public StructureStart getStructureWithPieceAt(BlockPos, Structure | TagKey<Structure> | HolderSet<Structure> | Predicate<Holder<Structure>>);
    public boolean hasAnyStructureAt(BlockPos);  public Map<Structure, LongSet> getAllStructuresAt(BlockPos);  public StructureCheckResult checkStructurePresence(ChunkPos, Structure, StructurePlacement, boolean createReference);  public RegistryAccess registryAccess();
}
public interface WorldGenLevel extends ServerLevelAccessor { long getSeed(); default boolean ensureCanWrite(BlockPos pos); default void setCurrentlyGenerating(@Nullable Supplier<String> currentlyGenerating); /* inherited: ServerLevel getLevel() */ }
public final class NaturalSpawner { public static void spawnMobsForChunkGeneration(ServerLevelAccessor level, Holder<Biome> biome, ChunkPos chunkPos, RandomSource random); }
```
`V` JD `net/minecraft/world/level/chunk/ChunkAccess.html`, `ProtoChunk.html`, `.../levelgen/Heightmap.html`, `Heightmap.Types.html`
```java
package net.minecraft.world.level.chunk;
public abstract class ChunkAccess implements BlockGetter, BiomeManager.NoiseBiomeSource, LightChunk, StructureAccess, IAttachmentHolder /*NF*/ {
    public ChunkAccess(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, PalettedContainerFactory containerFactory, long inhabitedTime, LevelChunkSection @Nullable [] sections, @Nullable BlendingData blendingData);
    public @Nullable BlockState setBlockState(BlockPos pos, BlockState state);                 // NEW convenience (no flags)
    public abstract @Nullable BlockState setBlockState(BlockPos pos, BlockState state, int flags); // was (pos,state,boolean isMoving) in 1.21.x
    public abstract void setBlockEntity(BlockEntity blockEntity); public abstract void addEntity(Entity entity); public abstract void removeBlockEntity(BlockPos pos);
    public ChunkPos getPos(); public LevelChunkSection[] getSections(); public LevelChunkSection getSection(int sectionIndex); public int getHighestFilledSectionIndex();
    public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type); public boolean hasPrimedHeightmap(Heightmap.Types type); public int getHeight(Heightmap.Types type, int x, int z); public void setHeightmap(Heightmap.Types key, long[] data);
    public @Nullable StructureStart getStartForStructure(Structure structure); public void setStartForStructure(Structure structure, StructureStart structureStart); public Map<Structure, StructureStart> getAllStarts();
    public LongSet getReferencesForStructure(Structure structure); public void addReferenceForStructure(Structure structure, long reference); public Map<Structure, LongSet> getAllReferences();
    public void markUnsaved(); public boolean tryMarkSaved(); public boolean isUnsaved();                   // NO setUnsaved(boolean)
    public abstract ChunkStatus getPersistedStatus(); public ChunkStatus getHighestGeneratedStatus();      // NO getStatus()
    public @Nullable ShortList[] getPostProcessing(); public void addPackedPostProcess(ShortList packedOffsets, int sectionIndex); public void markPosForPostprocessing(BlockPos blockPos);
    public void setBlockEntityNbt(CompoundTag entityTag); public @Nullable CompoundTag getBlockEntityNbt(BlockPos blockPos); public Set<BlockPos> getBlockEntitiesPos();
    public final void findBlockLightSources(BiConsumer<BlockPos, BlockState> consumer); public ChunkSkyLightSources getSkyLightSources(); public void initializeLightSources();
    public long getInhabitedTime(); public void setInhabitedTime(long); public void incrementInhabitedTime(long);  public boolean isUpgrading(); public UpgradeData getUpgradeData(); public @Nullable BlendingData getBlendingData();
    public int getMinY(); public int getHeight(); public LevelHeightAccessor getHeightAccessorForGeneration();  // getMaxY(), getSectionsCount(), getSectionIndex(int y) inherited from LevelHeightAccessor (U: not re-fetched)
    public Holder<Biome> getNoiseBiome(int x, int y, int z); public void fillBiomesFromNoise(BiomeResolver resolver, Climate.Sampler sampler);   // NO setBiomes
    public NoiseChunk getOrCreateNoiseChunk(Function<ChunkAccess, NoiseChunk> factory); public Holder<Biome> carverBiome(Supplier<Holder<Biome>> supplier);
    // NeoForge: getLevel() (@Nullable Level), attachments hasData/getData/setData/removeData(AttachmentType)
    // NOTE: carving-mask methods are NOT on ChunkAccess — they live on ProtoChunk:
}
public class ProtoChunk extends ChunkAccess {
    public ProtoChunk(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, PalettedContainerFactory containerFactory, @Nullable BlendingData blendingData);   // NO Registry<Biome> param any more
    public ProtoChunk(ChunkPos chunkPos, UpgradeData upgradeData, LevelChunkSection @Nullable [] sections, ProtoChunkTicks<Block> blockTicks, ProtoChunkTicks<Fluid> fluidTicks, LevelHeightAccessor levelHeightAccessor, PalettedContainerFactory containerFactory, ... @Nullable BlendingData blendingData); // U: middle params elided (long line omitted by tool)
    public @Nullable CarvingMask getCarvingMask(); public CarvingMask getOrCreateCarvingMask(); public void setCarvingMask(CarvingMask mask);   // no GenerationStep.Carving param
    public void setPersistedStatus(ChunkStatus status);
}
package net.minecraft.world.level.levelgen;
public class Heightmap { public Heightmap(ChunkAccess chunk, Heightmap.Types heightmapType); public static void primeHeightmaps(ChunkAccess chunk, Set<Heightmap.Types> types);
    public boolean update(int localX, int localY, int localZ, BlockState state); public int getFirstAvailable(int x, int z); public int getHighestTaken(int x, int z);
    public enum Types { WORLD_SURFACE_WG, WORLD_SURFACE, OCEAN_FLOOR_WG, OCEAN_FLOOR, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES } }
```

### I2. BiomeSource / Biome
`V` JD `net/minecraft/world/level/biome/BiomeSource.html` (+subclasses); protected members `U` (JD hides them; 1.21.x javadoc shows the same two, and JD still lists all 4 vanilla subclasses implementing them — NF has no BiomeSource patch)
```java
package net.minecraft.world.level.biome;
public abstract class BiomeSource implements BiomeResolver {
    public static final Codec<BiomeSource> CODEC;
    protected BiomeSource();                                                     // U
    protected abstract MapCodec<? extends BiomeSource> codec();                  // U (see note)
    protected abstract Stream<Holder<Biome>> collectPossibleBiomes();            // U (see note)
    public Set<Holder<Biome>> possibleBiomes();
    public abstract Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler);
    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int r, Climate.Sampler sampler);
    public @Nullable Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int x, int y, int z, int searchRadius, Predicate<Holder<Biome>> allowed, RandomSource random, Climate.Sampler sampler);
    public @Nullable Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int originX, int originY, int originZ, int searchRadius, int skipSteps, Predicate<Holder<Biome>> allowed, RandomSource random, boolean findClosest, Climate.Sampler sampler);
    public @Nullable Pair<BlockPos, Holder<Biome>> findClosestBiome3d(BlockPos origin, int searchRadius, int sampleResolutionHorizontal, int sampleResolutionVertical, Predicate<Holder<Biome>> allowed, Climate.Sampler sampler, LevelReader level);
    public void addDebugInfo(List<String> result, BlockPos feetPos, Climate.Sampler sampler);
}
public class FixedBiomeSource extends BiomeSource implements BiomeManager.NoiseBiomeSource { public static final MapCodec<FixedBiomeSource> CODEC; public FixedBiomeSource(Holder<Biome> biome); }
public class MultiNoiseBiomeSource extends BiomeSource { public static final MapCodec<MultiNoiseBiomeSource> CODEC;
    public static MultiNoiseBiomeSource createFromList(Climate.ParameterList<Holder<Biome>> parameters); public static MultiNoiseBiomeSource createFromPreset(Holder<MultiNoiseBiomeSourceParameterList> preset);
    public boolean stable(ResourceKey<MultiNoiseBiomeSourceParameterList> expected); public Holder<Biome> getNoiseBiome(Climate.TargetPoint target); }
public class MultiNoiseBiomeSourceParameterLists { public static final ResourceKey<MultiNoiseBiomeSourceParameterList> OVERWORLD, NETHER; }
public class CheckerboardColumnBiomeSource extends BiomeSource { public CheckerboardColumnBiomeSource(HolderSet<Biome> allowedBiomes, int size); }
public class TheEndBiomeSource extends BiomeSource { public static TheEndBiomeSource create(HolderGetter<Biome> biomes); }
public class BiomeManager { public BiomeManager(BiomeManager.NoiseBiomeSource noiseBiomeSource, long seed); public static long obfuscateSeed(long seed);
    public Holder<Biome> getBiome(BlockPos pos); public Holder<Biome> getNoiseBiomeAtPosition(BlockPos); getNoiseBiomeAtPosition(double,double,double); getNoiseBiomeAtQuart(int,int,int); }
// Climate (all nested in net.minecraft.world.level.biome.Climate):
public static Climate.TargetPoint target(float temperature, float humidity, float continentalness, float erosion, float depth, float weirdness);
public static Climate.ParameterPoint parameters(float temperature, float humidity, float continentalness, float erosion, float depth, float weirdness, float offset);
public static Climate.ParameterPoint parameters(Climate.Parameter temperature, Climate.Parameter humidity, Climate.Parameter continentalness, Climate.Parameter erosion, Climate.Parameter depth, Climate.Parameter weirdness, float offset);
public static Climate.Sampler empty();
record Parameter(long min, long max) { static Parameter point(float min); static Parameter span(float min, float max); static Parameter span(Parameter, Parameter); }
record ParameterPoint(Parameter temperature, Parameter humidity, Parameter continentalness, Parameter erosion, Parameter depth, Parameter weirdness, long offset)
class ParameterList<T> { public ParameterList(List<Pair<Climate.ParameterPoint, T>> values); public List<Pair<ParameterPoint,T>> values(); public T findValue(Climate.TargetPoint target); }
record TargetPoint(long temperature, long humidity, long continentalness, long erosion, long depth, long weirdness)
record Sampler(DensityFunction temperature, DensityFunction humidity, DensityFunction continentalness, DensityFunction erosion, DensityFunction depth, DensityFunction weirdness, List<Climate.ParameterPoint> spawnTarget) { public Climate.TargetPoint sample(int quartX, int quartY, int quartZ); }
```
`V` JD `.../biome/Biome.html`, `Biome.BiomeBuilder.html`, `BiomeSpecialEffects.html`, `BiomeGenerationSettings.Builder.html`, `MobSpawnSettings.Builder.html`, `MobSpawnSettings.SpawnerData.html`, `.../data/worldgen/biome/OverworldBiomes.html`, `Biomes.html`, `.../levelgen/GenerationStep.Decoration.html`
```java
public final class Biome {   // net.minecraft.world.level.biome
    public boolean hasPrecipitation(); public float getBaseTemperature(); public Biome.Precipitation getPrecipitationAt(BlockPos pos, int seaLevel);
    public boolean coldEnoughToSnow(BlockPos pos, int seaLevel); public boolean warmEnoughToRain(BlockPos pos, int seaLevel); public boolean shouldMeltFrozenOceanIcebergSlightly(BlockPos pos, int seaLevel);
    public boolean shouldFreeze(LevelReader level, BlockPos pos); public boolean shouldFreeze(LevelReader level, BlockPos pos, boolean checkNeighbors); public boolean shouldSnow(LevelReader level, BlockPos pos);
    public MobSpawnSettings getMobSettings(); public BiomeGenerationSettings getGenerationSettings(); public BiomeSpecialEffects getSpecialEffects(); public EnvironmentAttributeMap getAttributes();   // net.minecraft.world.attribute
    public int getGrassColor(double x, double z); public int getFoliageColor(); public int getDryFoliageColor(); public int getWaterColor();   // NO getSkyColor/getFogColor/getTemperature(BlockPos)
    public enum Precipitation { NONE, RAIN, SNOW }  public enum TemperatureModifier { NONE, FROZEN }
    public static class BiomeBuilder { public BiomeBuilder(); hasPrecipitation(boolean); temperature(float); downfall(float); temperatureAdjustment(Biome.TemperatureModifier);
        putAttributes(EnvironmentAttributeMap); putAttributes(EnvironmentAttributeMap.Builder); <V> setAttribute(EnvironmentAttribute<V> attribute, V value); <V,P> modifyAttribute(EnvironmentAttribute<V>, AttributeModifier<V,P>, P value);
        specialEffects(BiomeSpecialEffects); mobSpawnSettings(MobSpawnSettings); generationSettings(BiomeGenerationSettings); public Biome build(); }
}
// BiomeSpecialEffects is now a RECORD with only colours; fog/sky/water-fog colour, particles, sounds, music moved to EnvironmentAttributes:
public record BiomeSpecialEffects(int waterColor, Optional<Integer> foliageColorOverride, Optional<Integer> dryFoliageColorOverride, Optional<Integer> grassColorOverride, BiomeSpecialEffects.GrassColorModifier grassColorModifier)
  { public static class Builder { waterColor(int); foliageColorOverride(int); dryFoliageColorOverride(int); grassColorOverride(int); grassColorModifier(GrassColorModifier); BiomeSpecialEffects build(); } }
public static class BiomeGenerationSettings.PlainBuilder { addFeature(GenerationStep.Decoration step, Holder<PlacedFeature> feature); addFeature(int index, Holder<PlacedFeature>); addCarver(Holder<ConfiguredWorldCarver<?>> carver); BiomeGenerationSettings build(); }
public static class BiomeGenerationSettings.Builder extends PlainBuilder { public Builder(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers);
    public Builder addFeature(GenerationStep.Decoration step, ResourceKey<PlacedFeature> feature); public Builder addCarver(ResourceKey<ConfiguredWorldCarver<?>> carver); }   // NO step param
public enum GenerationStep.Decoration { RAW_GENERATION, LAKES, LOCAL_MODIFICATIONS, UNDERGROUND_STRUCTURES, SURFACE_STRUCTURES, STRONGHOLDS, UNDERGROUND_ORES, UNDERGROUND_DECORATION, FLUID_SPRINGS, VEGETAL_DECORATION, TOP_LAYER_MODIFICATION }
public static class MobSpawnSettings.Builder { public Builder(); addSpawn(MobCategory category, int weight, MobSpawnSettings.SpawnerData spawnerData); addMobCharge(EntityType<?> type, double charge, double energyBudget); creatureGenerationProbability(float); MobSpawnSettings build(); }
public record MobSpawnSettings.SpawnerData(EntityType<?> type, int minCount, int maxCount)   // weight lives in addSpawn
public class OverworldBiomes { /* net.minecraft.data.worldgen.biome; all (HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> carvers, ...) -> Biome */
    public static Biome plains(pf, c, boolean sunflower, boolean snowy, boolean spikes); theVoid(pf, c); forest(pf, c, boolean birch, boolean tall, boolean flower); desert(pf, c); ocean(pf, c, boolean deep); swamp(pf, c); taiga(pf, c, boolean); }
public abstract class Biomes { public static final ResourceKey<Biome> THE_VOID, PLAINS, SUNFLOWER_PLAINS, SNOWY_PLAINS, DESERT, SWAMP, FOREST, TAIGA, SAVANNA, JUNGLE, BADLANDS, RIVER, BEACH, OCEAN, DEEP_OCEAN, MUSHROOM_FIELDS, NETHER_WASTES, THE_END, ...; }
```

### I3. Noise / random / math
`V` JD `net/minecraft/world/level/levelgen/synth/{PerlinNoise,NormalNoise,NormalNoise.NoiseParameters,SimplexNoise,ImprovedNoise,PerlinSimplexNoise,BlendedNoise}.html`
```java
package net.minecraft.world.level.levelgen.synth;
public class PerlinNoise {   // no public ctor; NO firstOctave() accessor (only on NoiseParameters)
    public static PerlinNoise create(RandomSource random, IntStream octaves); create(RandomSource random, List<Integer> octaveSet);
    public static PerlinNoise create(RandomSource random, int firstOctave, double firstAmplitude, double... amplitudes); create(RandomSource random, int firstOctave, DoubleList amplitudes);
    @Deprecated public static PerlinNoise createLegacyForBlendedNoise(RandomSource random, IntStream octaves); @Deprecated createLegacyForLegacyNetherBiome(RandomSource random, int firstOctave, DoubleList amplitudes);
    public double getValue(double x, double y, double z); @Deprecated public double getValue(double x, double y, double z, double yScale, double yFudge);   // 5-arg only (no 6-arg boolean form)
    public double maxBrokenValue(double yScale); public @Nullable ImprovedNoise getOctaveNoise(int i); public static double wrap(double x);
}
public class NormalNoise { public static NormalNoise create(RandomSource random, int firstOctave, double... amplitudes); create(RandomSource random, NormalNoise.NoiseParameters parameters);
    public double getValue(double x, double y, double z); public double maxValue(); public NoiseParameters parameters();
    public record NoiseParameters(int firstOctave, DoubleList amplitudes) { NoiseParameters(int, List<Double>); NoiseParameters(int firstOctave, double firstAmplitude, double... amplitudes); static Codec<NoiseParameters> DIRECT_CODEC; static Codec<Holder<NoiseParameters>> CODEC; } }
public class SimplexNoise { public SimplexNoise(RandomSource random); public double getValue(double xin, double yin); public double getValue(double xin, double yin, double zin); public final double xo, yo, zo; }
public final class ImprovedNoise { public ImprovedNoise(RandomSource random); public double noise(double x, double y, double z); @Deprecated noise(double,double,double,double yScale,double yFudge); public double noiseWithDerivative(double x, double y, double z, double[] derivativeOut); }
public class PerlinSimplexNoise { public PerlinSimplexNoise(RandomSource random, List<Integer> octaveSet); public double getValue(double x, double y, boolean useNoiseStart); }   // NO getSurfaceNoiseValue
public class BlendedNoise implements DensityFunction.SimpleFunction { public BlendedNoise(RandomSource random, double xzScale, double yScale, double xzFactor, double yFactor, double smearScaleMultiplier); static createUnseeded(same 5 doubles); BlendedNoise withNewRandom(RandomSource); double compute(DensityFunction.FunctionContext); }
```
`V` JD `net/minecraft/util/RandomSource.html`, `.../levelgen/{PositionalRandomFactory,LegacyRandomSource,XoroshiroRandomSource,WorldgenRandom,WorldgenRandom.Algorithm}.html`
```java
package net.minecraft.util;
public interface RandomSource { static RandomSource create(); create(long seed); createThreadLocalInstance(); createThreadLocalInstance(long seed); @Deprecated createThreadSafe();   // NOT createNewThreadLocalInstance
    RandomSource fork(); PositionalRandomFactory forkPositional(); void setSeed(long); int nextInt(); int nextInt(int bound); default int nextInt(int origin, int bound /*excl*/); default int nextIntBetweenInclusive(int min, int maxInclusive);
    long nextLong(); boolean nextBoolean(); float nextFloat(); double nextDouble(); double nextGaussian(); default double triangle(double mean, double spread); default float triangle(float, float); default void consumeCount(int rounds); }
package net.minecraft.world.level.levelgen;
public interface PositionalRandomFactory { RandomSource at(int x, int y, int z); default RandomSource at(BlockPos pos); RandomSource fromHashOf(String name); default RandomSource fromHashOf(Identifier name); RandomSource fromSeed(long seed); }
public class LegacyRandomSource implements BitRandomSource { public LegacyRandomSource(long seed); }
public class XoroshiroRandomSource implements RandomSource { public XoroshiroRandomSource(long seed); public XoroshiroRandomSource(long seedLo, long seedHi); }
public class WorldgenRandom extends LegacyRandomSource { public WorldgenRandom(RandomSource randomSource);
    public long setDecorationSeed(long seed, int chunkX, int chunkZ); public void setFeatureSeed(long seed, int index, int step); public void setLargeFeatureSeed(long seed, int chunkX, int chunkZ); public void setLargeFeatureWithSalt(long seed, int x, int z, int blend);
    public static RandomSource seedSlimeChunk(int x, int z, long seed, long salt);  public enum Algorithm { LEGACY, XOROSHIRO; public RandomSource newInstance(long seed); } }
```
`V` JD `net/minecraft/util/Mth.html`, `net/minecraft/util/ARGB.html`
```java
package net.minecraft.util;  public class Mth {  // constants (float): PI, HALF_PI, TWO_PI, DEG_TO_RAD, RAD_TO_DEG, EPSILON, SQRT_OF_TWO
    static float sin(double); float cos(double); float sqrt(float); int floor(float|double); long lfloor(double); int ceil(float|double); float abs(float); int abs(int);
    static int clamp(int,int,int); long clamp(long,long,long); float clamp(float,float,float); double clamp(double,double,double); double clampedLerp(double factor,double min,double max); float clampedLerp(float,float,float);
    static float lerp(float alpha, float p0, float p1); double lerp(double,double,double); Vec3 lerp(double, Vec3, Vec3); int lerpInt(float,int,int); double lerp2(...); double lerp3(...);
    static double inverseLerp(double value,double min,double max); float inverseLerp(float,float,float); double map(double value,double fromMin,double fromMax,double toMin,double toMax) (+float); clampedMap(same);
    static int wrapDegrees(int); float wrapDegrees(long); float wrapDegrees(float); double wrapDegrees(double);
    static int hsvToRgb(float hue, float saturation, float value); int hsvToArgb(float hue, float saturation, float value, int alpha);   // NO Mth.color(...) -> ARGB.color
    static float square(float); double square(double); int square(int); long square(long); int positiveModulo(int,int) (+float,double); int floorDiv(int,int);   // NO floorMod -> Math.floorMod
    static float randomBetween(RandomSource random, float min, float maxExclusive); int randomBetweenInclusive(RandomSource, int min, int maxInclusive); int nextInt(RandomSource, int minInclusive, int maxInclusive); float nextFloat(RandomSource,float,float); double nextDouble(RandomSource,double,double); float normal(RandomSource, float mean, float deviation);
    @Deprecated static long getSeed(int x, int y, int z); getSeed(Vec3i); double atan2(double y, double x); int roundToward(int input, int multiple); int ceillog2(int); int log2(int); boolean isPowerOfTwo(int); int smallestEncompassingPowerOfTwo(int);
    static int absMax(int,int) (+float,double); int sign(double); double lengthSquared(double x,double y); lengthSquared(double,double,double); float lengthSquared(float,float,float); double length(double,double); length(double,double,double);
    static double smoothstep(double); float triangleWave(float index, float period); float rotLerp(float a, float from, float to); double rotLerp(double,double,double); int murmurHash3Mixer(int); boolean isMultipleOf(int dividend, int divisor); float frac(float); double frac(double); }
public class ARGB { static int alpha(int); red(int); green(int); blue(int); float alphaFloat/redFloat/greenFloat/blueFloat(int);
    static int color(int alpha, int red, int green, int blue); int color(int red, int green, int blue); int color(Vec3); int color(int alpha, int rgb); int color(float alpha, int rgb); int colorFromFloat(float alpha, float r, float g, float b);
    static int multiply(int lhs, int rhs); addRgb; subtractRgb; int multiplyAlpha(int color, float alphaMultiplier); int scaleRGB(int color, float scale); scaleRGB(int, float r, float g, float b); scaleRGB(int, int scale); int greyscale(int); int alphaBlend(int destination, int source);
    static int srgbLerp(float alpha, int p0, int p1); int linearLerp(float alpha, int p0, int p1);   // NO plain lerp(...)
    static int opaque(int); int transparent(int); int white(float alpha); white(int alpha); black(float|int); int gray(float brightness); int average(int,int); int as8BitChannel(float value); int toABGR(int); int fromABGR(int); int setBrightness(int color, float brightness);
    static float srgbToLinearChannel(int srgb); int linearToSrgbChannel(float linear); int meanLinear(int,int,int,int); }   // NO from8BitChannel
```

### I4. Dimension / level stem / clocks
`V` JD `net/minecraft/world/level/dimension/DimensionType.html`, `DimensionType.MonsterSettings.html`, `DimensionType.Skybox.html`, `.../level/CardinalLighting.Type.html`, `BuiltinDimensionTypes.html`, `LevelStem.html`, `.../level/Level.html`, `.../core/registries/Registries.html`
```java
package net.minecraft.world.level.dimension;
public record DimensionType(boolean hasFixedTime, boolean hasSkyLight, boolean hasCeiling, boolean hasEnderDragonFight, double coordinateScale, int minY, int height, int logicalHeight,
        TagKey<Block> infiniburn, float ambientLight, DimensionType.MonsterSettings monsterSettings, DimensionType.Skybox skybox, CardinalLighting.Type cardinalLightType,
        EnvironmentAttributeMap attributes, HolderSet<Timeline> timelines, Optional<Holder<WorldClock>> defaultClock) {
    // GONE vs 1.21: ultrawarm, natural, bedWorks, respawnAnchorWorks, fixedTime (OptionalLong), effectsLocation, piglinSafe, hasRaids -> now EnvironmentAttributes / timelines
    public static final int BITS_FOR_Y, MIN_HEIGHT, Y_SIZE, MAX_Y, MIN_Y, WAY_ABOVE_MAX_Y, WAY_BELOW_MIN_Y; public static final float[] MOON_BRIGHTNESS_PER_PHASE;
    public static final Codec<DimensionType> DIRECT_CODEC, NETWORK_CODEC; public static final Codec<Holder<DimensionType>> CODEC; public static final StreamCodec<RegistryFriendlyByteBuf, Holder<DimensionType>> STREAM_CODEC;
    public static double getTeleportationScale(DimensionType last, DimensionType next); public static Path getStorageFolder(ResourceKey<Level> name, Path baseFolder);
    public IntProvider monsterSpawnLightTest(); public int monsterSpawnBlockLightLimit(); public boolean hasEndFlashes();
    public record MonsterSettings(IntProvider monsterSpawnLightTest, int monsterSpawnBlockLightLimit) {}   // piglinSafe/hasRaids removed
    public enum Skybox { NONE, OVERWORLD, END }
}
package net.minecraft.world.level; public enum CardinalLighting.Type { DEFAULT, NETHER }
public class BuiltinDimensionTypes { public static final ResourceKey<DimensionType> OVERWORLD, NETHER, END, OVERWORLD_CAVES; }   // net.minecraft.world.level.dimension
public record LevelStem(Holder<DimensionType> type, ChunkGenerator generator, OptionalLong seedOverride) { public LevelStem(Holder<DimensionType> type, ChunkGenerator generator); public static final Codec<LevelStem> CODEC; public static final ResourceKey<LevelStem> OVERWORLD, NETHER, END; }
public abstract class Level { public static final ResourceKey<Level> OVERWORLD, NETHER, END; }   // net.minecraft.world.level
// net.minecraft.core.registries.Registries (all ResourceKey<Registry<X>>): DIMENSION -> Level, DIMENSION_TYPE -> DimensionType, LEVEL_STEM -> LevelStem, TIMELINE -> Timeline, WORLD_CLOCK -> WorldClock,
//   ENVIRONMENT_ATTRIBUTE -> EnvironmentAttribute<?>, BIOME, CONFIGURED_FEATURE -> ConfiguredFeature<?,?>, PLACED_FEATURE, CONFIGURED_CARVER -> ConfiguredWorldCarver<?>, STRUCTURE, STRUCTURE_SET, NOISE_SETTINGS -> NoiseGeneratorSettings, NOISE -> NormalNoise.NoiseParameters, DENSITY_FUNCTION, MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST
```
`V` JD `net/minecraft/world/timeline/Timeline.html`, `.../world/clock/WorldClock.html`, `ClockNetworkState.html`, package summaries, `.../level/storage/LevelData.RespawnData.html`, `LevelData.html`
```java
package net.minecraft.world.timeline;   // classes: AttributeTrack<Value,Argument>, AttributeTrackSampler<Value,Argument>, Timeline, Timeline.Builder, Timelines (interface of vanilla keys)
public class Timeline {   // NOT a record; no public ctor
    public static final Codec<Holder<Timeline>> CODEC; public static final Codec<Timeline> DIRECT_CODEC, NETWORK_CODEC;
    public static Timeline.Builder builder(Holder<WorldClock> clock);
    public Holder<WorldClock> clock(); public Optional<Integer> periodTicks(); public Set<EnvironmentAttribute<?>> attributes();
    public int getPeriodCount(ClockManager clockManager); public long getCurrentTicks(ClockManager); public long getTotalTicks(ClockManager);
    public void registerTimeMarkers(BiConsumer<ResourceKey<ClockTimeMarker>, ClockTimeMarker> output); public <V> AttributeTrackSampler<V,?> createTrackSampler(EnvironmentAttribute<V> attribute, ClockManager clockManager);
}   // Timeline.Builder methods: U (not fetched). JSON shape U (no primer fetched): expected {"clock": "<id>", "period_ticks": int?, "tracks": {"<attribute id>": {...keyframes}}, "time_markers": {...}} — treat as guess; verify against a vanilla datapack dump (data/minecraft/timeline/*.json).
package net.minecraft.world.clock;   // ClockManager (iface), ClockNetworkState, ClockState, ClockTimeMarker, ClockTimeMarkers, PackedClockStates, ServerClockManager, WorldClock, WorldClocks
public record WorldClock() { public static final Codec<Holder<WorldClock>> CODEC; public static final Codec<WorldClock> DIRECT_CODEC; public static final StreamCodec<RegistryFriendlyByteBuf, Holder<WorldClock>> STREAM_CODEC; }   // EMPTY record: a clock is just a registry id
public record ClockNetworkState(long totalTicks, float partialTick, float rate) { static StreamCodec<ByteBuf, ClockNetworkState> STREAM_CODEC; }
package net.minecraft.world.level.storage;
public record LevelData.RespawnData(GlobalPos globalPos, float yaw, float pitch) { public static final RespawnData DEFAULT; static MapCodec MAP_CODEC; static Codec CODEC; static StreamCodec<ByteBuf,RespawnData> STREAM_CODEC;
    public static RespawnData of(ResourceKey<Level> dimension, BlockPos pos, float yaw, float pitch); public ResourceKey<Level> dimension(); public BlockPos pos(); }
public interface LevelData { LevelData.RespawnData getRespawnData(); long getGameTime(); boolean isHardcore(); Difficulty getDifficulty(); boolean isDifficultyLocked(); }   // NO getDayTime()/getSpawnPos()
```

### I5. Features, placement, carvers, structures
`V` JD `.../levelgen/placement/PlacedFeature.html`, `.../levelgen/feature/{ConfiguredFeature,Feature,FeaturePlaceContext,LakeFeature,LakeFeature.Configuration}.html`, `.../feature/configurations/OreConfiguration.html`, `.../structure/templatesystem/{BlockMatchTest,TagMatchTest}.html`, `.../feature/package-summary.html`
```java
package net.minecraft.world.level.levelgen.placement;
public record PlacedFeature(Holder<ConfiguredFeature<?,?>> feature, List<PlacementModifier> placement) {
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin); public boolean placeWithBiomeCheck(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin); }
package net.minecraft.world.level.levelgen.feature;
public record ConfiguredFeature<FC extends FeatureConfiguration, F extends Feature<FC>>(F feature, FC config) { public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin); }
public abstract class Feature<FC extends FeatureConfiguration> { public Feature(Codec<FC> codec); public abstract boolean place(FeaturePlaceContext<FC> context);
    public boolean place(FC config, WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin);
    public static final Feature<OreConfiguration> ORE; Feature<TreeConfiguration> TREE; Feature<NoneFeatureConfiguration> MONSTER_ROOM, NO_OP; Feature<LakeFeature.Configuration> LAKE; Feature<SimpleBlockConfiguration> SIMPLE_BLOCK;
    Feature<SpringConfiguration> SPRING; Feature<DiskConfiguration> DISK; Feature<GeodeConfiguration> GEODE; Feature<BlockPileConfiguration> BLOCK_PILE; Feature<VegetationPatchConfiguration> VEGETATION_PATCH; SCATTERED_ORE, RANDOM_SELECTOR, SIMPLE_RANDOM_SELECTOR, RANDOM_BOOLEAN_SELECTOR, FALLEN_TREE ...
    // REMOVED in 26.1: Feature.RANDOM_PATCH, Feature.FLOWER, Feature.NO_BONEMEAL_FLOWER (RandomPatchFeature.html 404s; not in package summary). Vanilla "patches" are now data-driven via placement modifiers + SIMPLE_BLOCK / RANDOM_SELECTOR.
}
public class FeaturePlaceContext<FC extends FeatureConfiguration> { public FeaturePlaceContext(Optional<ConfiguredFeature<?,?>> topFeature, WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin, FC config); /* accessors: topFeature() level() chunkGenerator() random() origin() config() */ }
@Deprecated public class LakeFeature extends Feature<LakeFeature.Configuration> { public record Configuration(BlockStateProvider fluid, BlockStateProvider barrier) implements FeatureConfiguration {} }
public class OreConfiguration implements FeatureConfiguration {   // net.minecraft.world.level.levelgen.feature.configurations
    public OreConfiguration(List<OreConfiguration.TargetBlockState> targetBlockStates, int size, float discardChanceOnAirExposure); OreConfiguration(List<TargetBlockState>, int size); OreConfiguration(RuleTest target, BlockState state, int size[, float discardChanceOnAirExposure]);
    public static OreConfiguration.TargetBlockState target(RuleTest rule, BlockState state); public final List<TargetBlockState> targetStates; public final int size; public final float discardChanceOnAirExposure; }
public class BlockMatchTest extends RuleTest { public BlockMatchTest(Block block); }  public class TagMatchTest extends RuleTest { public TagMatchTest(TagKey<Block> tag); }   // net.minecraft.world.level.levelgen.structure.templatesystem
```
`V` JD `net/minecraft/data/worldgen/features/{OreFeatures,TreeFeatures,MiscOverworldFeatures}.html`, `.../data/worldgen/placement/{OrePlacements,TreePlacements,VegetationPlacements,PlacementUtils}.html`, `.../levelgen/placement/*.html`, `.../levelgen/VerticalAnchor.html`
```java
// ResourceKey<ConfiguredFeature<?,?>>: OreFeatures.ORE_IRON, ORE_COAL, ORE_GOLD, ORE_LAPIS, ORE_DIAMOND_SMALL/MEDIUM/LARGE/BURIED (NO ORE_DIAMOND); TreeFeatures.OAK, BIRCH, FANCY_OAK, SPRUCE; MiscOverworldFeatures.LAKE_LAVA, SPRING_WATER, SPRING_LAVA_OVERWORLD, SPRING_LAVA_FROZEN (NO SPRING_LAVA)
// ResourceKey<PlacedFeature>: OrePlacements.ORE_IRON_UPPER, ORE_IRON_MIDDLE, ORE_COAL_UPPER, ORE_COAL_LOWER, ORE_GOLD, ORE_DIAMOND, ORE_LAPIS; TreePlacements.OAK_CHECKED, BIRCH_CHECKED; VegetationPlacements.TREES_PLAINS, FLOWER_PLAINS
public class PlacementUtils {   // net.minecraft.data.worldgen.placement
    public static final PlacementModifier HEIGHTMAP, HEIGHTMAP_NO_LEAVES, HEIGHTMAP_TOP_SOLID, HEIGHTMAP_WORLD_SURFACE, HEIGHTMAP_OCEAN_FLOOR, FULL_RANGE, RANGE_10_10, RANGE_8_8, RANGE_4_4, RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT;
    public static ResourceKey<PlacedFeature> createKey(String name);
    public static void register(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> id, Holder<ConfiguredFeature<?,?>> feature, List<PlacementModifier> placementModifiers);  register(ctx, id, feature, PlacementModifier... placementModifiers);
    public static PlacementModifier countExtra(int count, float chance, int extra); public static PlacementFilter isEmpty(); public static BlockPredicateFilter filteredByBlockSurvival(Block block);
    public static Holder<PlacedFeature> inlinePlaced(Holder<ConfiguredFeature<?,?>> configuredFeature, PlacementModifier... placedFeatures); public static <FC,F> Holder<PlacedFeature> inlinePlaced(F feature, FC config, PlacementModifier... mods); onlyWhenEmpty(F, FC); filtered(F, FC, BlockPredicate); }
// package net.minecraft.world.level.levelgen.placement:
public static CountPlacement CountPlacement.of(int count); of(IntProvider count);   public static InSquarePlacement InSquarePlacement.spread();
public static HeightRangePlacement HeightRangePlacement.of(HeightProvider height); uniform(VerticalAnchor minInclusive, VerticalAnchor maxInclusive); triangle(VerticalAnchor minInclusive, VerticalAnchor maxInclusive);
public static BiomeFilter BiomeFilter.biome();  public static RarityFilter RarityFilter.onAverageOnceEvery(int chance);  public static SurfaceWaterDepthFilter SurfaceWaterDepthFilter.forMaxDepth(int maxWaterDepth);  public static HeightmapPlacement HeightmapPlacement.onHeightmap(Heightmap.Types heightmap);
public interface VerticalAnchor { static VerticalAnchor absolute(int value); aboveBottom(int offset); belowTop(int offset); bottom(); top(); int resolveY(WorldGenerationContext ctx); }   // net.minecraft.world.level.levelgen
```
`V` JD `.../levelgen/carver/{WorldCarver,ConfiguredWorldCarver,CarvingContext,CarverConfiguration,CaveCarverConfiguration,CanyonCarverConfiguration}.html`, `.../data/worldgen/Carvers.html`, `.../chunk/CarvingMask.html`, `.../levelgen/Aquifer*.html`
```java
package net.minecraft.world.level.levelgen.carver;
public abstract class WorldCarver<C extends CarverConfiguration> {
    public static final WorldCarver<CaveCarverConfiguration> CAVE, NETHER_CAVE; public static final WorldCarver<CanyonCarverConfiguration> CANYON;
    public WorldCarver(Codec<C> codec); public ConfiguredWorldCarver<C> configured(C configuration); public MapCodec<ConfiguredWorldCarver<C>> configuredCodec(); public int getRange();
    public abstract boolean carve(CarvingContext context, C configuration, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, RandomSource random, Aquifer aquifer, ChunkPos sourceChunkPos, CarvingMask mask);
    public abstract boolean isStartChunk(C configuration, RandomSource random);
}
public record ConfiguredWorldCarver<WC extends CarverConfiguration>(WorldCarver<WC> worldCarver, WC config) {   // RECORD now
    public boolean isStartChunk(RandomSource random); public boolean carve(CarvingContext context, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, RandomSource random, Aquifer aquifer, ChunkPos sourceChunkPos, CarvingMask mask); }
public class CarvingContext extends WorldGenerationContext { public CarvingContext(NoiseBasedChunkGenerator generator, RegistryAccess registryAccess, LevelHeightAccessor heightAccessor, NoiseChunk noiseChunk, RandomState randomState, SurfaceRules.RuleSource surfaceRule);
    public RandomState randomState(); @Deprecated public RegistryAccess registryAccess(); @Deprecated public Optional<BlockState> topMaterial(Function<BlockPos,Holder<Biome>> biomeGetter, ChunkAccess chunk, BlockPos pos, boolean underFluid); /* inherited int getMinGenY(); int getGenDepth() */ }
public class CarverConfiguration extends ProbabilityFeatureConfiguration { public CarverConfiguration(float probability, HeightProvider y, FloatProvider yScale, VerticalAnchor lavaLevel, CarverDebugSettings debugSettings, HolderSet<Block> replaceable); }
public class CaveCarverConfiguration extends CarverConfiguration {
    public CaveCarverConfiguration(float probability, HeightProvider y, FloatProvider yScale, VerticalAnchor lavaLevel, CarverDebugSettings debugSettings, HolderSet<Block> replaceable, FloatProvider horizontalRadiusMultiplier, FloatProvider verticalRadiusMultiplier, FloatProvider floorLevel);
    public CaveCarverConfiguration(float probability, HeightProvider y, FloatProvider yScale, VerticalAnchor lavaLevel, HolderSet<Block> replaceable, FloatProvider horizontalRadiusMultiplier, FloatProvider verticalRadiusMultiplier, FloatProvider floorLevel); }   // NOTE HolderSet<Block> replaceable is REQUIRED (e.g. BlockTags.OVERWORLD_CARVER_REPLACEABLES via HolderGetter<Block>.getOrThrow)
public class CanyonCarverConfiguration extends CarverConfiguration { public CanyonCarverConfiguration(float probability, HeightProvider y, FloatProvider yScale, VerticalAnchor lavaLevel, CarverDebugSettings debugSettings, HolderSet<Block> replaceable, FloatProvider verticalRotation, CanyonCarverConfiguration.CanyonShapeConfiguration shape); }
public class Carvers { public static final ResourceKey<ConfiguredWorldCarver<?>> CAVE, CAVE_EXTRA_UNDERGROUND, CANYON, NETHER_CAVE; }   // net.minecraft.data.worldgen
public class CarvingMask { public CarvingMask(int height, int minY); public CarvingMask(long[] array, int minY); public void set(int x, int y, int z); public boolean get(int x, int y, int z); public Stream<BlockPos> stream(ChunkPos pos); public long[] toArray(); }   // net.minecraft.world.level.chunk; no isEmpty()
public interface Aquifer {   // net.minecraft.world.level.levelgen
    static Aquifer create(NoiseChunk noiseChunk, ChunkPos pos, NoiseRouter router, PositionalRandomFactory positionalRandomFactory, int minBlockY, int yBlockSize, Aquifer.FluidPicker fluidRule);
    static Aquifer createDisabled(Aquifer.FluidPicker fluidRule); @Nullable BlockState computeSubstance(DensityFunction.FunctionContext context, double density); boolean shouldScheduleFluidUpdate();
    interface FluidPicker { Aquifer.FluidStatus computeFluid(int blockX, int blockY, int blockZ); }   record FluidStatus(int fluidLevel, BlockState fluidType) { public BlockState at(int blockY); } }
```
`V` JD `.../levelgen/structure/{StructureSet,StructureSet.StructureSelectionEntry,BuiltinStructureSets,BuiltinStructures}.html`, `.../structure/placement/{RandomSpreadStructurePlacement,ConcentricRingsStructurePlacement,RandomSpreadType}.html`, `.../chunk/ChunkGeneratorStructureState.html`
```java
package net.minecraft.world.level.levelgen.structure;
public record StructureSet(List<StructureSet.StructureSelectionEntry> structures, StructurePlacement placement) { public StructureSet(Holder<Structure> singleEntry, StructurePlacement placement);
    public static StructureSelectionEntry entry(Holder<Structure> structure, int weight); entry(Holder<Structure> structure); public record StructureSelectionEntry(Holder<Structure> structure, int weight) {} }
public interface BuiltinStructureSets { ResourceKey<StructureSet> VILLAGES, DESERT_PYRAMIDS, IGLOOS, JUNGLE_TEMPLES, SWAMP_HUTS, PILLAGER_OUTPOSTS, OCEAN_MONUMENTS, WOODLAND_MANSIONS, BURIED_TREASURES, MINESHAFTS, RUINED_PORTALS, SHIPWRECKS, OCEAN_RUINS, NETHER_COMPLEXES, NETHER_FOSSILS, END_CITIES, ANCIENT_CITIES, STRONGHOLDS, TRAIL_RUINS, TRIAL_CHAMBERS; }
public interface BuiltinStructures { ResourceKey<Structure> VILLAGE_PLAINS, VILLAGE_DESERT, VILLAGE_SAVANNA, VILLAGE_SNOWY, VILLAGE_TAIGA, STRONGHOLD, MINESHAFT, MINESHAFT_MESA, PILLAGER_OUTPOST, WOODLAND_MANSION, JUNGLE_TEMPLE, DESERT_PYRAMID, IGLOO, SWAMP_HUT, OCEAN_MONUMENT, FORTRESS, BASTION_REMNANT, END_CITY, ANCIENT_CITY, TRAIL_RUINS, TRIAL_CHAMBERS, RUINED_PORTAL_STANDARD, ...; }
package net.minecraft.world.level.levelgen.structure.placement;
public class RandomSpreadStructurePlacement extends StructurePlacement { public RandomSpreadStructurePlacement(int spacing, int separation, RandomSpreadType spreadType, int salt);
    public RandomSpreadStructurePlacement(Vec3i locateOffset, StructurePlacement.FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt, Optional<StructurePlacement.ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType); }
public class ConcentricRingsStructurePlacement extends StructurePlacement { public ConcentricRingsStructurePlacement(int distance, int spread, int count, HolderSet<Biome> preferredBiomes); }
public enum RandomSpreadType { LINEAR, TRIANGULAR }
public class ChunkGeneratorStructureState {   // net.minecraft.world.level.chunk; no public ctor
    public static ChunkGeneratorStructureState createForFlat(RandomState randomState, long levelSeed, BiomeSource biomeSource, Stream<Holder<StructureSet>> structureOverrides);
    public static ChunkGeneratorStructureState createForNormal(RandomState randomState, long levelSeed, BiomeSource biomeSource, HolderLookup<StructureSet> allStructures);
    public List<Holder<StructureSet>> possibleStructureSets(); public List<StructurePlacement> getPlacementsForStructure(Holder<Structure> structure); public boolean hasStructureChunkInRange(Holder<StructureSet> structureSet, int sourceX, int sourceZ, int range);
    public long getLevelSeed(); public RandomState randomState(); public void ensureStructuresGenerated(); public @Nullable List<ChunkPos> getRingPositionsFor(ConcentricRingsStructurePlacement placement); }
```

---

# J/K — Environment Attributes & Client Rendering (MC 26.1 / NeoForge 26.1.2.76, Mojang mappings)

Legend: `VERIFIED (url)` = seen verbatim in javadoc/source. `UNVERIFIED` = best guess. JD = `https://lexxie.dev/neoforge/26.1/`.

## J. Environment attributes — package `net.minecraft.world.attribute`

### EnvironmentAttribute<Value> — VERIFIED (JD net/minecraft/world/attribute/EnvironmentAttribute.html)
```java
public class EnvironmentAttribute<Value> {
  public static <Value> EnvironmentAttribute.Builder<Value> builder(AttributeType<Value> type);
  public AttributeType<Value> type();
  public Value defaultValue();
  public Codec<Value> valueCodec();
  public Value sanitizeValue(Value value);
  public boolean isSyncable();
  public boolean isPositional();
  public boolean isSpatiallyInterpolated();
}
// EnvironmentAttribute.Builder<Value> — VERIFIED (JD .../EnvironmentAttribute.Builder.html)
public static class Builder<Value> {
  public Builder(AttributeType<Value> type);
  public Builder<Value> defaultValue(Value defaultValue);
  public Builder<Value> valueRange(AttributeRange<Value> valueRange);   // net.minecraft.world.attribute.AttributeRange<Value>
  public Builder<Value> syncable();
  public Builder<Value> notPositional();
  public Builder<Value> spatiallyInterpolated();
  public EnvironmentAttribute<Value> build();
}
```
No `sanitizer(...)` builder method; sanitizing comes from `valueRange`.

### AttributeTypes (interface, `net.minecraft.world.attribute.AttributeTypes`) — VERIFIED (JD .../AttributeTypes.html)
```java
AttributeType<Boolean> BOOLEAN;  AttributeType<TriState> TRI_STATE;  // net.minecraft.util.TriState
AttributeType<Float> FLOAT;      AttributeType<Float> ANGLE_DEGREES;
AttributeType<Integer> RGB_COLOR; AttributeType<Integer> ARGB_COLOR; AttributeType<Integer> INTEGER;
AttributeType<MoonPhase> MOON_PHASE;            // net.minecraft.world.level.MoonPhase (enum)
AttributeType<Activity> ACTIVITY;               // net.minecraft.world.entity.schedule.Activity
AttributeType<BedRule> BED_RULE;                // net.minecraft.world.attribute.BedRule
AttributeType<ParticleOptions> PARTICLE;        // net.minecraft.core.particles.ParticleOptions
AttributeType<List<AmbientParticle>> AMBIENT_PARTICLES;   // net.minecraft.world.attribute.AmbientParticle
AttributeType<BackgroundMusic> BACKGROUND_MUSIC;          // net.minecraft.world.attribute.BackgroundMusic
AttributeType<AmbientSounds> AMBIENT_SOUNDS;              // net.minecraft.world.attribute.AmbientSounds
Codec<AttributeType<?>> CODEC;
static <Value> AttributeType<Value> register(String name, AttributeType<Value> type);
```
No IDENTIFIER / SOUND / COLOR type exists in vanilla (NeoForge registers its own Identifier type for CUSTOM_SKYBOX etc. — see NeoForgeEnvironmentAttributes below). `AttributeType<Value>` is a class in same package.

### EnvironmentAttributes (interface, `net.minecraft.world.attribute.EnvironmentAttributes`) — VERIFIED (JD .../EnvironmentAttributes.html), full list verbatim
```java
EnvironmentAttribute<Integer> FOG_COLOR, WATER_FOG_COLOR, SKY_COLOR, SUNRISE_SUNSET_COLOR, CLOUD_COLOR,
                              BLOCK_LIGHT_TINT, SKY_LIGHT_COLOR, NIGHT_VISION_COLOR, AMBIENT_LIGHT_COLOR;
EnvironmentAttribute<Float>   FOG_START_DISTANCE, FOG_END_DISTANCE, SKY_FOG_END_DISTANCE, CLOUD_FOG_END_DISTANCE,
                              WATER_FOG_START_DISTANCE, WATER_FOG_END_DISTANCE, CLOUD_HEIGHT,
                              SUN_ANGLE, MOON_ANGLE, STAR_ANGLE, STAR_BRIGHTNESS, SKY_LIGHT_FACTOR, MUSIC_VOLUME,
                              SKY_LIGHT_LEVEL, TURTLE_EGG_HATCH_CHANCE, SURFACE_SLIME_SPAWN_CHANCE, CAT_WAKING_UP_GIFT_CHANCE;
EnvironmentAttribute<MoonPhase> MOON_PHASE;
EnvironmentAttribute<ParticleOptions> DEFAULT_DRIPSTONE_PARTICLE;
EnvironmentAttribute<List<AmbientParticle>> AMBIENT_PARTICLES;
EnvironmentAttribute<BackgroundMusic> BACKGROUND_MUSIC;
EnvironmentAttribute<AmbientSounds> AMBIENT_SOUNDS;
EnvironmentAttribute<Boolean> FIREFLY_BUSH_SOUNDS, CAN_START_RAID, WATER_EVAPORATES, RESPAWN_ANCHOR_WORKS,
                              NETHER_PORTAL_SPAWNS_PIGLINS, FAST_LAVA, INCREASED_FIRE_BURNOUT, PIGLINS_ZOMBIFY,
                              SNOW_GOLEM_MELTS, CREAKING_ACTIVE, BEES_STAY_IN_HIVE, MONSTERS_BURN, CAN_PILLAGER_PATROL_SPAWN;
EnvironmentAttribute<TriState> EYEBLOSSOM_OPEN;
EnvironmentAttribute<BedRule> BED_RULE;
EnvironmentAttribute<Activity> VILLAGER_ACTIVITY, BABY_VILLAGER_ACTIVITY;
Codec<EnvironmentAttribute<?>> CODEC;
```
CLOUD_HEIGHT is plain `Float` (not Optional). Angles are `Float` degrees (ANGLE_DEGREES type).

### EnvironmentAttributeReader (interface) — VERIFIED (JD .../EnvironmentAttributeReader.html)
```java
public interface EnvironmentAttributeReader {
  EnvironmentAttributeReader EMPTY;
  <Value> Value getDimensionValue(EnvironmentAttribute<Value> attribute);
  default <Value> Value getValue(EnvironmentAttribute<Value> attribute, BlockPos pos);
  default <Value> Value getValue(EnvironmentAttribute<Value> attribute, Vec3 pos);
  <Value> Value getValue(EnvironmentAttribute<Value> attribute, Vec3 pos, @Nullable SpatialAttributeInterpolator biomeInterpolator);
  default <Value> Value getValue(LootContext context, EnvironmentAttribute<Value> attribute);
}
```
### EnvironmentAttributeSystem — VERIFIED (JD .../EnvironmentAttributeSystem.html, .Builder.html)
```java
public class EnvironmentAttributeSystem implements EnvironmentAttributeReader {
  public static EnvironmentAttributeSystem.Builder builder();
  public void invalidateTickCache();
  public <Value> Value getDimensionValue(EnvironmentAttribute<Value> attribute);
  public <Value> Value getValue(EnvironmentAttribute<Value> attribute, Vec3 pos, @Nullable SpatialAttributeInterpolator biomeInterpolator);
}
public static class EnvironmentAttributeSystem.Builder {
  public Builder addDefaultLayers(Level level);
  public Builder addConstantLayer(EnvironmentAttributeMap attributeMap);
  public <Value> Builder addConstantLayer(EnvironmentAttribute<Value> attribute, EnvironmentAttributeLayer.Constant<Value> layer);
  public <Value> Builder addTimeBasedLayer(EnvironmentAttribute<Value> attribute, EnvironmentAttributeLayer.TimeBased<Value> layer);
  public <Value> Builder addPositionalLayer(EnvironmentAttribute<Value> attribute, EnvironmentAttributeLayer.Positional<Value> layer);
  public Builder addTimelineLayer(Holder<Timeline> timeline, ClockManager clockManager); // net.minecraft.world.timeline.Timeline, net.minecraft.world.clock.ClockManager
  public EnvironmentAttributeSystem build();
}
```

### Layers — VERIFIED (JD .../EnvironmentAttributeLayer*.html). Sealed; all three are @FunctionalInterface
```java
public sealed interface EnvironmentAttributeLayer<Value> permits Constant, TimeBased, Positional {
  @FunctionalInterface interface Constant<Value>   extends EnvironmentAttributeLayer<Value> { Value applyConstant(Value baseValue); }
  @FunctionalInterface interface TimeBased<Value>  extends EnvironmentAttributeLayer<Value> { Value applyTimeBased(Value baseValue, int cacheTickId); }
  @FunctionalInterface interface Positional<Value> extends EnvironmentAttributeLayer<Value> { Value applyPositional(Value baseValue, Vec3 pos, @Nullable SpatialAttributeInterpolator biomeInterpolator); }
}
```
Vanilla implementer of TimeBased: `net.minecraft.world.timeline.AttributeTrackSampler`. Layers are applied in insertion order on top of the base (default → dimension → biome → timelines → weather → client sky-flash → sanitize) — VERIFIED (1.21.11 primer "Timeline of Environment Attributes").

### EnvironmentAttributeMap — VERIFIED (JD .../EnvironmentAttributeMap.html, .Builder.html)
```java
public final class EnvironmentAttributeMap {
  public static final EnvironmentAttributeMap EMPTY;
  public static final Codec<EnvironmentAttributeMap> CODEC, NETWORK_CODEC, CODEC_ONLY_POSITIONAL;
  public static EnvironmentAttributeMap.Builder builder();
  public <Value> @Nullable EnvironmentAttributeMap.Entry<Value,?> get(EnvironmentAttribute<Value> attribute);   // record Entry<Value,Argument>
  public <Value> Value applyModifier(EnvironmentAttribute<Value> attribute, Value baseValue);
  public boolean contains(EnvironmentAttribute<?> attribute);
  public Set<EnvironmentAttribute<?>> keySet();
  public static class Builder {
    public Builder putAll(EnvironmentAttributeMap map);
    public <Value,Parameter> Builder modify(EnvironmentAttribute<Value> attribute, AttributeModifier<Value,Parameter> modifier, Parameter value);
    public <Value> Builder set(EnvironmentAttribute<Value> attribute, Value value);
    public EnvironmentAttributeMap build();
  }
}
```
### Modifiers — package `net.minecraft.world.attribute.modifier` — VERIFIED (JD package-summary, AttributeModifier.OperationId.html, FloatModifier.html)
Classes: `AttributeModifier<Subject,Argument>` (interface; `apply(Subject,Argument)`, `argumentCodec(EnvironmentAttribute)`, `argumentKeyframeLerp(EnvironmentAttribute)`; static libraries `FLOAT_LIBRARY, INTEGER_LIBRARY, BOOLEAN_LIBRARY, RGB_COLOR_LIBRARY, ARGB_COLOR_LIBRARY`), `AttributeModifier.OverrideModifier<Value>`, `AttributeModifier.OperationId` enum (StringRepresentable, serialized lowercase): `OVERRIDE, ALPHA_BLEND, ADD, SUBTRACT, MULTIPLY, BLEND_TO_GRAY, MINIMUM, MAXIMUM, AND, NAND, OR, NOR, XOR, XNOR`; `BooleanModifier` (enum), `ColorModifier<Argument>` (+ `.ArgbModifier`, `.RgbModifier`, `.BlendToGray`), `FloatModifier<Argument>` (constants `FloatModifier<Float> ADD, SUBTRACT, MULTIPLY, MINIMUM, MAXIMUM; FloatModifier<FloatWithAlpha> ALPHA_BLEND`; `.Simple`), `IntegerModifier<Argument>` (+ `.Simple`), `FloatWithAlpha`. No "LERP" modifier — interpolation is done by timeline keyframes/`ease`.
Java usage: `EnvironmentAttributeMap.builder().modify(EnvironmentAttributes.CLOUD_HEIGHT, FloatModifier.ADD, 60f).build()`.
JSON (biome `attributes` / dimension_type `attributes`): plain value = override, or `{"modifier":"add","argument":60}` — VERIFIED (1.21.11 primer).

### Level / ServerLevel accessors — VERIFIED (JD Level.html, ServerLevel.html; ServerLevel.java.patch; 26.1 primer)
```java
// net.minecraft.world.level.Level
public abstract EnvironmentAttributeSystem environmentAttributes();
public abstract ClockManager clockManager();            // net.minecraft.world.clock.ClockManager
public long getDefaultClockTime();                       // ticks of this dimension's DimensionType#defaultClock
public long getOverworldClockTime();                     // replaces getDayTime() (26.1 primer: "getDayTime -> getOverworldClockTime, not one-to-one")
public int getSkyDarken(); public boolean isBrightOutside(); public boolean isDarkOutside();
// net.minecraft.server.level.ServerLevel
public ServerClockManager clockManager();
@Deprecated public void setEnvironmentAttributes(EnvironmentAttributeSystem environmentAttributes); // vanilla, @Deprecated but public — replaces the level's system
public LevelData.RespawnData getRespawnData(); public void setRespawnData(LevelData.RespawnData respawnData);
// vanilla ctor: this.environmentAttributes = EnvironmentAttributeSystem.builder().addDefaultLayers(this).build();
// ClientLevel: this.environmentAttributes = this.addEnvironmentAttributeLayers(EnvironmentAttributeSystem.builder()).build();  (private; no NeoForge hook/event)
```
`ServerLevel#setDayTime`, `getDayCount`, `LevelData#getDayTime`, `ServerLevelData#setDayTime` are REMOVED in 26.1 (primer). Time advances via `ServerClockManager#tick()`; gamerule is `GameRules.ADVANCE_TIME` (VERIFIED ServerLevel.java.patch: `this.getGameRules().get(GameRules.ADVANCE_TIME)`); on sleep vanilla calls `server.clockManager().moveToTimeMarker(defaultClock.get(), ClockTimeMarkers.WAKE_UP_FROM_SLEEP)`.
`LevelData.RespawnData` — record exists (`net.minecraft.world.level.storage.LevelData.RespawnData`); components UNVERIFIED (best guess: `(ResourceKey<Level> dimension, BlockPos pos, float yaw, float pitch)`).
`ClientboundGameEventPacket` still exists with `Type` constants `START_RAINING, STOP_RAINING, RAIN_LEVEL_CHANGE, THUNDER_LEVEL_CHANGE` and ctor `(ClientboundGameEventPacket.Type, float)` — VERIFIED (ServerLevel.java.patch).

### Custom dynamic server-side layer — pattern (VERIFIED pieces)
```java
EnvironmentAttributeSystem sys = EnvironmentAttributeSystem.builder()
    .addDefaultLayers(serverLevel)                                  // must come first (dimension/biome/timeline/weather)
    .addConstantLayer(EnvironmentAttributes.SKY_COLOR, base -> 0xFF8040)
    .addTimeBasedLayer(EnvironmentAttributes.SUN_ANGLE, (base, tick) -> myAngle(serverLevel))
    .addPositionalLayer(EnvironmentAttributes.FOG_COLOR, (base, pos, interp) -> ...)
    .build();
serverLevel.setEnvironmentAttributes(sys);
```
CLIENT SYNC: there is NO packet for ad-hoc attribute values (no `ClientboundUpdateEnvironmentAttributesPacket` in `net.minecraft.network.protocol.game` — VERIFIED package listing). `syncable()` only means the attribute is included in the DimensionType/biome/timeline registry data sent on login (`EnvironmentAttributeMap.NETWORK_CODEC`, `Timeline.NETWORK_CODEC`); NeoForge attributes are "filtered out when syncing to vanilla clients" (NeoForgeEnvironmentAttributes javadoc). Clock state is synced via `ClientboundSetTimePacket(long gameTime, Map<Holder<WorldClock>, ClockNetworkState> clockUpdates)` (VERIFIED JD). Anything a mod sets via `setEnvironmentAttributes` on the server must be mirrored client-side by the mod's own payload + a client-side layer (or by reading mod data inside a `CustomSkyboxRenderer`).

### Clocks & timelines — VERIFIED (JD net/minecraft/world/clock/*, net/minecraft/world/timeline/*, Registries.html, 26.1 primer)
```java
// net.minecraft.core.registries.Registries
ResourceKey<Registry<EnvironmentAttribute<?>>> ENVIRONMENT_ATTRIBUTE;  ResourceKey<Registry<AttributeType<?>>> ATTRIBUTE_TYPE;  // static (BuiltInRegistries.ENVIRONMENT_ATTRIBUTE / ATTRIBUTE_TYPE)
ResourceKey<Registry<Timeline>> TIMELINE;  ResourceKey<Registry<WorldClock>> WORLD_CLOCK;                                  // datapack registries
// net.minecraft.world.clock.WorldClock — EMPTY record used as a registry key:  public record WorldClock() { CODEC (Holder), DIRECT_CODEC, STREAM_CODEC }
// net.minecraft.world.clock.WorldClocks — interface of vanilla keys (OVERWORLD, THE_END: names UNVERIFIED); ClockTimeMarkers has WAKE_UP_FROM_SLEEP (VERIFIED)
public interface ClockManager { long getTotalTicks(Holder<WorldClock> definition); default float getPartialTick(Holder<WorldClock>); default void setRate(Holder<WorldClock>, float rate); }
public class ServerClockManager extends SavedData implements ClockManager {   // MinecraftServer#clockManager(), ServerLevel#clockManager()
  public void tick(); public void setTotalTicks(Holder<WorldClock> clock, long totalTicks); public void addTicks(Holder<WorldClock> clock, int ticks);
  public void setPaused(Holder<WorldClock> clock, boolean paused); public void setRate(Holder<WorldClock>, float rate) /*NeoForge; <0 resets*/; public float getRate(Holder<WorldClock>);
  public boolean moveToTimeMarker(Holder<WorldClock> clock, ResourceKey<ClockTimeMarker> timeMarkerId); public boolean isAtTimeMarker(Holder<WorldClock>, ResourceKey<ClockTimeMarker>);
  public ClientboundSetTimePacket createFullSyncPacket(); public PackedClockStates packState();
}
// net.minecraft.world.timeline.Timeline (class, not record)
public static Timeline.Builder builder(Holder<WorldClock> clock);  public Holder<WorldClock> clock();  public Optional<Integer> periodTicks();
public Set<EnvironmentAttribute<?>> attributes();  public long getCurrentTicks(ClockManager); public long getTotalTicks(ClockManager); public int getPeriodCount(ClockManager);
public <Value> AttributeTrackSampler<Value,?> createTrackSampler(EnvironmentAttribute<Value>, ClockManager);
Timeline.Builder: setPeriodTicks(int); addTrack(EnvironmentAttribute<Value>, Consumer<KeyframeTrack.Builder<Value>>); addModifierTrack(EnvironmentAttribute<Value>, AttributeModifier<Value,Argument>, Consumer<KeyframeTrack.Builder<Argument>>); addTimeMarker(ResourceKey<ClockTimeMarker>, int ticks[, boolean showInCommands]); build()
Codecs: Timeline.CODEC (Holder), DIRECT_CODEC, NETWORK_CODEC
```
Clock lookup: `Holder.Reference<WorldClock> clock = level.registryAccess().getOrThrow(ResourceKey.create(Registries.WORLD_CLOCK, id))` (primer). A clock JSON is `data/<ns>/world_clock/<name>.json` = `{}` (VERIFIED misode mcmeta). Timelines activate via tag `#minecraft:in_overworld` etc. in `dimension_type.timelines`.

### Vanilla JSON (VERIFIED from misode/mcmeta branch `data`, 26.1)
`data/minecraft/dimension_type/overworld.json`:
```json
{"ambient_light":0.0,"attributes":{
 "minecraft:audio/ambient_sounds":{"mood":{"block_search_extent":8,"offset":2.0,"sound":"minecraft:ambient.cave","tick_delay":6000}},
 "minecraft:audio/background_music":{"creative":{"max_delay":24000,"min_delay":12000,"sound":"minecraft:music.creative"},"default":{"max_delay":24000,"min_delay":12000,"sound":"minecraft:music.game"}},
 "minecraft:gameplay/bed_rule":{"can_set_spawn":"always","can_sleep":"when_dark","error_message":{"translate":"block.minecraft.bed.no_sleep"}},
 "minecraft:gameplay/nether_portal_spawns_piglin":true,"minecraft:gameplay/respawn_anchor_works":false,
 "minecraft:gameplay/straw_bed_rule":{"can_set_spawn":"never","can_sleep":"when_dark","destroy_on_leave":true,"error_message":{"translate":"block.minecraft.bed.no_sleep"}},
 "minecraft:visual/ambient_light_color":"#0a0a0a","minecraft:visual/cloud_color":"#ccffffff","minecraft:visual/cloud_height":192.33,
 "minecraft:visual/fog_color":"#c0d8ff","minecraft:visual/sky_color":"#78a7ff"},
 "coordinate_scale":1.0,"default_clock":"minecraft:overworld","has_ceiling":false,"has_ender_dragon_fight":false,"has_skylight":true,"height":384,
 "infiniburn":"#minecraft:infiniburn_overworld","logical_height":384,"min_y":-64,"monster_spawn_block_light_limit":0,
 "monster_spawn_light_level":{"type":"minecraft:uniform","max_inclusive":7,"min_inclusive":0},"timelines":"#minecraft:in_overworld"}
```
`the_nether.json` attributes block: `"minecraft:gameplay/bed_rule":{"can_set_spawn":"never","can_sleep":"never","explodes":true}, "gameplay/can_start_raid":false, "gameplay/fast_lava":true, "gameplay/piglins_zombify":false, "gameplay/respawn_anchor_works":true, "gameplay/sky_light_level":4.0, "gameplay/snow_golem_melts":true, "gameplay/water_evaporates":true, "visual/ambient_light_color":"#302821", "visual/default_dripstone_particle":{"type":"minecraft:dripping_dripstone_lava"}, "visual/fog_end_distance":96.0, "visual/fog_start_distance":10.0, "visual/sky_light_color":"#7a7aff", "visual/sky_light_factor":0.0`; plus top-level `"cardinal_light":"nether"`, `"has_fixed_time":true`, `"skybox":"none"` (no `default_clock`), `"timelines":"#minecraft:in_nether"`.
Attribute id scheme: `minecraft:visual/*`, `minecraft:audio/*`, `minecraft:gameplay/*` (e.g. `visual/sun_angle`, `visual/moon_angle`, `visual/star_angle`, `visual/star_brightness`, `visual/moon_phase`, `visual/sunrise_sunset_color`, `visual/sky_light_color`, `visual/sky_light_factor`, `gameplay/sky_light_level`, `gameplay/monsters_burn`, `audio/firefly_bush_sounds`). Note `nether_portal_spawns_piglin` (singular) in JSON vs Java `NETHER_PORTAL_SPAWNS_PIGLINS`.
`data/minecraft/timeline/day.json` (excerpt, VERIFIED): `{"clock":"minecraft:overworld","period_ticks":24000,"time_markers":{"minecraft:day":{"show_in_commands":true,"ticks":1000},"minecraft:noon":{...6000},"minecraft:night":{...13000},"minecraft:midnight":{...18000},"minecraft:roll_village_siege":18000,"minecraft:wake_up_from_sleep":0},"tracks":{"minecraft:visual/sun_angle":{"ease":{"cubic_bezier":[0.362,0.241,0.638,0.759]},"keyframes":[{"ticks":6000,"value":360.0},{"ticks":6000,"value":0.0}]},"minecraft:visual/sky_color":{"keyframes":[{"ticks":133,"value":"#ffffff"},{"ticks":11867,"value":"#ffffff"},{"ticks":13670,"value":"#000000"},{"ticks":22330,"value":"#000000"}],"modifier":"multiply"},"minecraft:visual/star_brightness":{"keyframes":[{"ticks":92,"value":0.037},...,{"ticks":13228,"value":0.5},{"ticks":22772,"value":0.5},...],"modifier":"maximum"},"minecraft:gameplay/monsters_burn":{"keyframes":[{"ticks":12542,"value":false},{"ticks":23460,"value":true}],"modifier":"or"},"minecraft:visual/moon_angle":{...[{"ticks":6000,"value":540.0},{"ticks":6000,"value":180.0}]}, ...}}`. Keyframe key is `"ticks"` (not `"tick"`), `ease` is `"constant"` or `{"cubic_bezier":[..]}`; omitted `modifier` = override. `moon.json`: `period_ticks:192000`, track `minecraft:visual/moon_phase` with values `full_moon, waning_gibbous, third_quarter, waning_crescent, new_moon, waxing_crescent, first_quarter, waxing_gibbous` (ticks n*24000).

## K. Client rendering

### SkyRenderer — `net.minecraft.client.renderer.SkyRenderer` — VERIFIED (JD SkyRenderer.html); public-only list
```java
public class SkyRenderer implements AutoCloseable {
  public SkyRenderer(TextureManager textureManager, AtlasManager atlasManager);   // net.minecraft.client.resources.model.sprite.AtlasManager
  public void extractRenderState(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state);
  public void renderSkyDisc(int skyColor);
  public void renderDarkDisc();
  public void renderSunMoonAndStars(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness);
  public void renderSunriseAndSunset(PoseStack poseStack, float sunAngle, int sunriseAndSunsetColor);
  public void renderEndSky();
  public void renderEndFlash(PoseStack poseStack, float intensity, float xAngle, float yAngle);
  public void close();
}
```
No separate public renderSun/renderMoon/renderStars (they are private inside renderSunMoonAndStars). Instance: `Minecraft.getInstance().levelRenderer` field is private — obtain via `LevelRenderer#getSkyRenderer()` UNVERIFIED (LevelRenderer exposes `getCloudRenderer()` VERIFIED in patch; sky getter not seen). Sky pass (VERIFIED LevelRenderer.java.patch): inside a FrameGraph pass, `if (customSkyboxRenderer == null || !customSkyboxRenderer.renderSky(levelRenderState, state, modelViewMatrix, () -> RenderSystem.setShaderFog(skyFog))) { RenderSystem.setShaderFog(skyFog); if (state.skybox == DimensionType.Skybox.END) skyRenderer.renderEndSky(); else { ...renderSkyDisc/renderSunriseAndSunset/renderSunMoonAndStars/renderDarkDisc... } }` then `RenderLevelStageEvent.AfterSky` fires. The `Runnable` passed to your skybox renderer = "apply vanilla sky fog" (`RenderSystem.setShaderFog(GpuBufferSlice)`). Sky pass is skipped when camera fog is POWDER_SNOW/LAVA or mob effect blocks sky.

### SkyRenderState — `net.minecraft.client.renderer.state.level.SkyRenderState` — VERIFIED (JD), all public fields
```java
public DimensionType.Skybox skybox;  // enum net.minecraft.world.level.dimension.DimensionType.Skybox (values UNVERIFIED: NONE, OVERWORLD, END — JSON "skybox":"none" seen)
public boolean shouldRenderDarkDisc; public float sunAngle, moonAngle, starAngle, rainBrightness, starBrightness;
public int sunriseAndSunsetColor; public MoonPhase moonPhase; public int skyColor;
public float endFlashIntensity, endFlashXAngle, endFlashYAngle;  public void reset();
```
### LevelRenderState — `net.minecraft.client.renderer.state.level.LevelRenderState extends net.neoforged.neoforge.client.renderstate.BaseRenderState` — VERIFIED (JD)
Fields: `CameraRenderState cameraRenderState` (`.pos` Vec3, `.fogType`, `.entityRenderState`, `.depthFar` seen in patch); `final List<EntityRenderState> entityRenderStates; final List<BlockEntityRenderState> blockEntityRenderStates; boolean haveGlowingEntities; @Nullable BlockOutlineRenderState blockOutlineRenderState; final List<BlockBreakingRenderState> blockBreakingRenderStates; final WeatherRenderState weatherRenderState; final WorldBorderRenderState worldBorderRenderState; final SkyRenderState skyRenderState; final ParticlesRenderState particlesRenderState; long gameTime; int lastEntityRenderStateCount; int cloudColor; float cloudHeight; @Nullable ChunkSectionsToRender chunkSectionsToRender; @Nullable CustomSkyboxRenderer customSkyboxRenderer; @Nullable CustomCloudsRenderer customCloudsRenderer; @Nullable CustomWeatherEffectRenderer customWeatherEffectRenderer`. No `level`/`partialTick` field. NeoForge data: `<T> @Nullable T getRenderData(ContextKey<T>)`, `<T> void setRenderData(ContextKey<T>, T)`, `resetRenderData()`, `getRenderDataOrDefault(ContextKey<T>, T)`, `getRenderDataOrThrow(ContextKey<T>)` (`net.minecraft.util.context.ContextKey`).
### ExtractLevelRenderStateEvent — `net.neoforged.neoforge.client.event` — VERIFIED (source)
`getLevelRenderer()`, `getRenderState()` (LevelRenderState — NOT getLevelRenderState), `getLevel()` (ClientLevel), `getCamera()`, `getFrustum()`, `getDeltaTracker()` (DeltaTracker → `getGameTimeDeltaPartialTick(boolean)`), `getRenderTick()`. Fired on NeoForge.EVENT_BUS after vanilla extraction and after custom sky/cloud/weather renderers were resolved from `CustomEnvironmentEffectsRendererManager.getCustomSkyboxRenderer(level, cameraPos)`.

### RenderType / RenderTypes / RenderPipelines — VERIFIED (JD rendertype/RenderType.html, rendertype/RenderTypes.html, renderer/RenderPipelines.html)
`RenderType` MOVED to `net.minecraft.client.renderer.rendertype.RenderType` (class, no static factories): `static RenderType create(String name, RenderSetup state); void draw(MeshData mesh); VertexFormat format(); VertexFormat.Mode mode(); RenderPipeline pipeline(); boolean hasBlending(); OutputTarget outputTarget(); Optional<RenderType> outline(); boolean sortOnUpload()`.
Factories live in `net.minecraft.client.renderer.rendertype.RenderTypes` (static): `entitySolid(Identifier)`, `entityCutout(Identifier[, boolean affectsOutline])`, `entityCutoutCull`, `entityCutoutZOffset`, `entityTranslucent(Identifier[, boolean])`, `entityTranslucentEmissive`, `entityTranslucentCullItemTarget`, `itemCutout(Identifier)`, `itemTranslucent(Identifier)`, `beaconBeam(Identifier, boolean translucent)`, `eyes(Identifier)`, `energySwirl(Identifier, float uOffset, float vOffset)`, `text(Identifier)`, `textSeeThrough`, `textBackground()`, `lines()`, `linesTranslucent()`, `endPortal()`, `endGateway()`, `glint()`, `entityGlint()`, `crumbling(Identifier)`, `outline(Identifier)`, `solidMovingBlock()/cutoutMovingBlock()/translucentMovingBlock()`, `debugQuads()`, `debugFilledBox()`, `lightning()`, `dragonRays()`; constants `LINES, LINES_TRANSLUCENT, SECONDARY_BLOCK_OUTLINE`. There is NO `RenderType.celestial/stars/solid()` — the sky (sun/moon/stars/sky disc) is drawn by `SkyRenderer` with `RenderPipelines` + `RenderPass` directly, not via RenderType.
`net.minecraft.client.renderer.RenderPipelines` (static `RenderPipeline` constants; `com.mojang.blaze3d.pipeline.RenderPipeline`): sky-related `SKY, END_SKY, SUNRISE_SUNSET, STARS, CELESTIAL`; also `SOLID_BLOCK, CUTOUT_BLOCK, TRANSLUCENT_BLOCK, ENTITY_SOLID, ENTITY_CUTOUT, ENTITY_TRANSLUCENT, ENTITY_TRANSLUCENT_EMISSIVE, ITEM_CUTOUT, ITEM_TRANSLUCENT, TEXT, GUI, GUI_TEXTURED, GUI_TEXTURED_PREMULTIPLIED_ALPHA, GUI_TEXT, LINES, LINES_TRANSLUCENT, CLOUDS, FLAT_CLOUDS, WEATHER_DEPTH_WRITE, WEATHER_NO_DEPTH_WRITE, OPAQUE_PARTICLE, TRANSLUCENT_PARTICLE, END_PORTAL, END_GATEWAY, GLINT, CRUMBLING, LIGHTNING, PANORAMA, ...` + snippets (`ENTITY_SNIPPET`, `GUI_TEXTURED_SNIPPET`, `FOG_SNIPPET`, `MATRICES_FOG_SNIPPET`, ...). Register own pipelines via `RegisterRenderPipelinesEvent` (exists in `net.neoforged.neoforge.client.event`, VERIFIED listing; method signature UNVERIFIED — best guess `registerPipeline(RenderPipeline)`).

### Drawing a textured quad in world space — VERIFIED building blocks
```java
// com.mojang.blaze3d.vertex.VertexConsumer (BufferBuilder implements it)
VertexConsumer addVertex(float x, float y, float z);  default addVertex(PoseStack.Pose pose, float,float,float);  default addVertex(Matrix4fc pose, float,float,float);  default addVertex(Vector3fc)
VertexConsumer setColor(int r,int g,int b,int a); setColor(int argb); default setColor(float r,float g,float b,float a);
VertexConsumer setUv(float u,float v); setUv1(int,int) /*overlay*/; setUv2(int,int) /*light*/; default setLight(int packedLightCoords); default setOverlay(int packedOverlayCoords);
VertexConsumer setNormal(float,float,float); default setNormal(PoseStack.Pose, float,float,float); setLineWidth(float);
default void addVertex(float x,float y,float z,int color,float u,float v,int overlayCoords,int lightCoords,float nx,float ny,float nz);
// com.mojang.blaze3d.vertex.Tesselator / BufferBuilder / MeshData
Tesselator.getInstance();  BufferBuilder Tesselator#begin(VertexFormat.Mode mode, VertexFormat format);  @Nullable MeshData BufferBuilder#build();  MeshData BufferBuilder#buildOrThrow();
RenderType#draw(MeshData mesh)   // immediate draw through a RenderType (binds its pipeline/texture)
```
Preferred pattern (VERIFIED names): from `SubmitCustomGeometryEvent` / a BER `submit`: `submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(myTexture), (pose, buffer) -> { buffer.addVertex(pose, x,y,z).setColor(0xFFFFFFFF).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,1,0); ... })` (`SubmitNodeCollector.CustomGeometryRenderer#render(PoseStack.Pose pose, VertexConsumer buffer)`). Immediate-mode alternative in `RenderLevelStageEvent.AfterSky`: `MultiBufferSource.BufferSource src = Minecraft.getInstance().renderBuffers().bufferSource(); VertexConsumer vc = src.getBuffer(RenderTypes.entityTranslucentEmissive(tex)); ...; src.endBatch();` — `RenderBuffers#bufferSource()` returns `MultiBufferSource.BufferSource` (VERIFIED); `Minecraft#renderBuffers()` and `BufferSource#getBuffer(RenderType)/endBatch()` UNVERIFIED (unchanged names, high confidence). Custom sky in `CustomSkyboxRenderer#renderSky(...)`: build a `MeshData` with `Tesselator` (format `DefaultVertexFormat.POSITION_TEX_COLOR`, UNVERIFIED name) and `RenderType.create("mymod:celestial", new RenderSetup(...))` then `renderType.draw(mesh)` — `RenderSetup` builder API UNVERIFIED (look at `net.minecraft.client.renderer.rendertype.RenderSetup`). For a simple modded sun/moon the safest VERIFIED route is to reuse vanilla: set `state.sunAngle/moonAngle/starAngle/moonPhase/skyColor/...` in `ExtractLevelRenderStateEvent` and let vanilla `SkyRenderer` draw.
`com.mojang.blaze3d.systems.RenderSystem` still exists (VERIFIED): `setShaderFog(GpuBufferSlice)`, `getShaderFog()`, `getModelViewMatrix()` (Matrix4f), `getModelViewStack()` (Matrix4fStack), `getDevice()` (GpuDevice), `getProjectionMatrixBuffer()`, `getDynamicUniforms()`, `bindDefaultUniforms(RenderPass)`, `getSequentialBuffer(VertexFormat.Mode)`; NO `setShaderTexture`, `setShader`, `enableBlend` etc. `RenderPass`/`GpuBuffer` in `com.mojang.blaze3d.systems`/`com.mojang.blaze3d.buffers`; `RenderSystem.getDevice().createCommandEncoder().createRenderPass(...)` signature UNVERIFIED.

### Block / item tints — VERIFIED (JD BlockTintSource.html, ItemTintSource.html; NeoForge RegisterColorHandlersEvent.java)
```java
// net.minecraft.client.color.block.BlockTintSource
int color(BlockState state);
default int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos);   // net.minecraft.client.renderer.block.BlockAndTintGetter
default int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos);
default Set<Property<?>> relevantProperties();
// net.neoforged.neoforge.client.event.RegisterColorHandlersEvent  (mod bus, client)
public static class BlockTintSources { public void register(List<BlockTintSource> tintSources, Block... blocks); public BlockColors getBlockColors(); }   // NOT ".Block"; tint index = list index
public static class ItemTintSources  { public void register(Identifier location, MapCodec<? extends ItemTintSource> source); }
public static class ColorResolvers   { public void register(ColorResolver resolver); }
// net.minecraft.client.color.item.ItemTintSource
int calculate(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner);  MapCodec<? extends ItemTintSource> type();
```
Item model JSON tint: `{"model":{"type":"minecraft:model","model":"mymod:item/page","tints":[{"type":"mymod:page"}]}}` — `tints` field UNVERIFIED-by-fetch (unchanged since 1.21.4; vanilla types `minecraft:constant`, `dye`, `potion`, `map_color`, `grass`, `team`, `custom_model_data`, `firework` all VERIFIED as classes).

### BlockEntityRenderer — VERIFIED (JD blockentity/BlockEntityRenderer.html, state/BlockEntityRenderState.html, BlockEntityRendererProvider.Context.html)
```java
public interface BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState> extends IBlockEntityRendererExtension<T> {
  S createRenderState();
  default void extractRenderState(T blockEntity, S state, float partialTicks, Vec3 cameraPosition, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress);
  void submit(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera);   // net.minecraft.client.renderer.state.level.CameraRenderState
  default boolean shouldRenderOffScreen(); default int getViewDistance(); default boolean shouldRender(T blockEntity, Vec3 cameraPosition);
  // NeoForge: AABB getRenderBoundingBox(T)
}
public class BlockEntityRenderState { public BlockPos blockPos; public BlockEntityType<?> blockEntityType; public int lightCoords; public @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress;
  public static void extractBase(BlockEntity blockEntity, BlockEntityRenderState state, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress); }   // NO blockState field
public record BlockEntityRendererProvider.Context(BlockEntityRenderDispatcher blockEntityRenderDispatcher, BlockModelResolver blockModelResolver, ItemModelResolver itemModelResolver,
  EntityRenderDispatcher entityRenderer, EntityModelSet entityModelSet, Font font, SpriteGetter sprites, PlayerSkinRenderCache playerSkinRenderCache) { public ModelPart bakeLayer(ModelLayerLocation id); }
```
Call `BlockEntityRenderState.extractBase(be, state, breakProgress)` first in your `extractRenderState` (default impl does this). Register with `EntityRenderersEvent.RegisterRenderers#registerBlockEntityRenderer(BlockEntityType<T>, BlockEntityRendererProvider<T,S>)` (UNVERIFIED generic arity, name VERIFIED in prior research).

### SubmitNodeCollector — `net.minecraft.client.renderer.SubmitNodeCollector extends OrderedSubmitNodeCollector` — VERIFIED (JD), full list
```java
OrderedSubmitNodeCollector order(int order);   // only method declared on SubmitNodeCollector itself
// OrderedSubmitNodeCollector:
void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces);
void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords, double distanceToCameraSq, CameraRenderState camera);
void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor, int outlineColor);
void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation);
void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState);
<S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, int tintedColor, @Nullable TextureAtlasSprite sprite, int outlineColor, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay);
default <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, int outlineColor, @Nullable CrumblingOverlay);
default <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, Identifier texture, int lightCoords, int overlayCoords, int outlineColor, @Nullable CrumblingOverlay);
default <S> void submitModel(Model<S> model, S state, PoseStack poseStack, int lightCoords, int overlayCoords, int tintedColor, SpriteId sprite, SpriteGetter sprites, int outlineColor, @Nullable CrumblingOverlay);
void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, @Nullable TextureAtlasSprite sprite, boolean sheeted, boolean hasFoil, int tintedColor, @Nullable CrumblingOverlay crumblingOverlay, int outlineColor);
default void submitModelPart(ModelPart, PoseStack, RenderType, int lightCoords, int overlayCoords, @Nullable TextureAtlasSprite sprite);   // + (…, sprite, int tintedColor, @Nullable CrumblingOverlay) and (…, sprite, boolean sheeted, boolean hasFoil)
void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState);
void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor);
void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress);
void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads, ItemStackRenderState.FoilType foilType);
void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer);   // CustomGeometryRenderer: void render(PoseStack.Pose pose, VertexConsumer buffer)
void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer);
// NeoForge extension: void submitMultiLayerBlockModel(PoseStack, List<BlockStateModelPart>, boolean, int[], int, int, int)
```
No `submitBlock`, `submitBlockEntity`, `submitFlatItem`, `submitNameDisplay`. To render an ItemStack: `ItemStackRenderState s = new ItemStackRenderState(); Minecraft.getInstance().getItemModelResolver().updateForTopItem(s, stack, ItemDisplayContext.FIXED, level, null, seed); s.submit(poseStack, collector, light, overlay, outlineColor);` (`ItemStackRenderState#submit(PoseStack, SubmitNodeCollector, int lightCoords, int overlayCoords, int outlineColor)` VERIFIED; `Minecraft#getItemModelResolver()` UNVERIFIED name).

### ItemModelResolver — `net.minecraft.client.renderer.item.ItemModelResolver` — VERIFIED (JD)
```java
public void updateForTopItem(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext, @Nullable Level level, @Nullable ItemOwner owner, int seed);  // net.minecraft.world.entity.ItemOwner (interface; LivingEntity implements it — UNVERIFIED)
public void appendItemLayers(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext, @Nullable Level level, @Nullable ItemOwner owner, int seed);
public void updateForLiving(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext, LivingEntity entity);
public void updateForNonLiving(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext, Entity entity);
public boolean shouldPlaySwapAnimation(ItemStack stack); public float swapAnimationScale(ItemStack stack);
```
`ItemStackRenderState` (VERIFIED): `newLayer()`, `clear()`, `isEmpty()`, `isAnimated()/setAnimated()`, `usesBlockLight()`, `visitExtents(Consumer<Vector3fc>)`, `getModelBoundingBox()`, `submit(PoseStack, SubmitNodeCollector, int light, int overlay, int outlineColor)`, `setOversizedInGui(boolean)`; enum `ItemStackRenderState.FoilType`.
`net.minecraft.world.item.ItemDisplayContext` (VERIFIED): `NONE, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND, HEAD, GUI, GROUND, FIXED, ON_SHELF` (extensible enum; `firstPerson()`, `leftHand()`, `fallback()`).

### EntityRenderState — `net.minecraft.client.renderer.entity.state.EntityRenderState extends BaseRenderState` — VERIFIED (JD), fields
`EntityType<?> entityType; double x, y, z; float ageInTicks, boundingBoxWidth, boundingBoxHeight, eyeHeight; double distanceToCameraSq; boolean isInvisible, isDiscrete, displayFireAnimation; int lightCoords, outlineColor (NO_OUTLINE const); @Nullable Vec3 passengerOffset; @Nullable Component nameTag, scoreText; @Nullable Vec3 nameTagAttachment; @Nullable List<LeashState> leashStates; float shadowRadius; final List<ShadowPiece> shadowPieces; float partialTick; boolean appearsGlowing()`. Plus NeoForge `getRenderData/setRenderData(ContextKey)`.

### Dynamic textures — VERIFIED (JD texture/DynamicTexture.html, TextureManager.html, blaze3d/platform/NativeImage.html)
```java
// net.minecraft.client.renderer.texture.DynamicTexture extends AbstractTexture implements Dumpable
public DynamicTexture(Supplier<String> label, NativeImage image);
public DynamicTexture(String label, int width, int height, boolean zero);
public DynamicTexture(Supplier<String> label, int width, int height, boolean zero);
public NativeImage getPixels(); public void setPixels(NativeImage pixels); public void upload(); public void close();
// AbstractTexture: getTexture() (GpuTexture), getTextureView() (GpuTextureView), getSampler()
// net.minecraft.client.renderer.texture.TextureManager  (Minecraft.getInstance().getTextureManager() — name UNVERIFIED but standard)
public void register(Identifier location, AbstractTexture texture);      // returns void; there is NO register(String, DynamicTexture) -> Identifier overload
public void registerAndLoad(Identifier textureId, ReloadableTexture texture); public void registerForNextReload(Identifier location);
public AbstractTexture getTexture(Identifier location); public void release(Identifier location);
public static final Identifier INTENTIONAL_MISSING_TEXTURE;
// com.mojang.blaze3d.platform.NativeImage (final)
public NativeImage(int width, int height, boolean zero);  public NativeImage(NativeImage.Format format, int width, int height, boolean zero);
public static NativeImage read(InputStream) / read(ByteBuffer) / read(byte[]) / read(@Nullable NativeImage.Format, InputStream|ByteBuffer) throws IOException;
public int getPixel(int x, int y);                 // ARGB (26.1: getPixel/setPixel are ARGB; *ABGR variants are the raw byte order)
public void setPixel(int x, int y, int pixel);      // ARGB
public void setPixelABGR(int x, int y, int pixel);  public int[] getPixels() /*ARGB*/; public int[] getPixelsABGR();
public void fillRect(int xs, int ys, int width, int height, int pixel); public void copyFrom(NativeImage from); public NativeImage mappedCopy(IntUnaryOperator);
public void copyRect(NativeImage target, int sourceX, int sourceY, int targetX, int targetY, int sizeX, int sizeY, boolean swapX, boolean swapY);
public int getWidth(); public int getHeight(); public NativeImage.Format format(); public void writeToFile(Path); public void close();
```
No `upload(int,int,int,boolean)` on NativeImage in the public API — upload via `DynamicTexture#upload()` after editing `getPixels()`. GUI blit of a registered texture: `GuiGraphicsExtractor#blit(RenderPipeline renderPipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight[, int color])` and `blit(Identifier location, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1)` (VERIFIED JD) — these take a TextureManager texture id (any registered `AbstractTexture`, e.g. your DynamicTexture), whereas `blitSprite(RenderPipeline, Identifier sprite, ...)` expects a GUI atlas sprite. Use `RenderPipelines.GUI_TEXTURED`.
`RegisterTextureAtlasSpriteLoadersEvent` does NOT exist in 26.1; related events are `RegisterSpriteSourcesEvent`, `RegisterTextureAtlasesEvent`, `TextureAtlasStitchedEvent` (VERIFIED client/event package listing).

### SpecialModelRenderer — `net.minecraft.client.renderer.special.SpecialModelRenderer<T>` — VERIFIED (JD)
```java
public interface SpecialModelRenderer<T> {
  void submit(@Nullable T argument, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor);  // NO ItemDisplayContext param
  void getExtents(Consumer<Vector3fc> output);
  @Nullable T extractArgument(ItemStack stack);
  interface Unbaked<T> { @Nullable SpecialModelRenderer<T> bake(SpecialModelRenderer.BakingContext context); MapCodec<? extends SpecialModelRenderer.Unbaked<T>> type(); }
  interface BakingContext { ... }   // members UNVERIFIED (has entityModelSet()/materials()/playerSkinRenderCache() in 1.21.x)
}
// net.minecraft.client.renderer.special.NoDataSpecialModelRenderer (+ .Unbaked) exists for T=Void-style renderers.
// net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent (mod bus): public void register(Identifier location, MapCodec<? extends SpecialModelRenderer.Unbaked<?>> source);
// net.minecraft.client.renderer.item.SpecialModelWrapper.Unbaked(Identifier base, Optional<Transformation> transformation, SpecialModelRenderer.Unbaked<?> specialModel) — MAP_CODEC
```
Item model JSON: `{"model":{"type":"minecraft:special","base":"mymod:item/page_base","model":{"type":"mymod:page"}}}` — field names `base` and `model` match record components `base`/`specialModel` (codec key for specialModel is `"model"` in vanilla 1.21.4+; VERIFIED component name, JSON key by convention).

---

# L1 Misc Core — verified signatures (MC 26.1 / NeoForge 26.1.2.76, Mojang mappings)

Legend: `V` = VERIFIED (javadoc https://lexxie.dev/neoforge/26.1/<pkg>/<Class>.html unless another URL given); `U` = UNVERIFIED (best guess). JD = https://lexxie.dev/neoforge/26.1

## L1a. Identifier / ResourceKey / Registries / Registry / Holder / Tags / Sounds

### net.minecraft.resources.Identifier  — V (JD/net/minecraft/resources/Identifier.html)
```java
public final class Identifier implements Comparable<Identifier>
public static final Codec<Identifier> CODEC;
public static final StreamCodec<ByteBuf, Identifier> STREAM_CODEC;
public static final String DEFAULT_NAMESPACE;          // "minecraft"
public static final char NAMESPACE_SEPARATOR;          // ':'
public static Identifier fromNamespaceAndPath(String namespace, String path); // throws IdentifierException on invalid chars
public static Identifier parse(String identifier);     // "ns:path" or "path" (default ns)
public static @Nullable Identifier tryParse(String identifier);
public static Identifier withDefaultNamespace(String path);
public static @Nullable Identifier tryBuild(String namespace, String path);
public static Identifier bySeparator(String identifier, char separator);
public static DataResult<Identifier> read(String input);
public static boolean isValidNamespace(String namespace); public static boolean isValidPath(String path);
public String getNamespace(); public String getPath(); public String toString();   // "ns:path"
public Identifier withPath(String newPath); public Identifier withPath(UnaryOperator<String> modifier);
public Identifier withPrefix(String prefix); public Identifier withSuffix(String suffix);
public int compareTo(Identifier o); public int compareNamespaced(Identifier o);
public String toLanguageKey();                          // "ns.path"
public String toLanguageKey(String prefix);             // "prefix.ns.path"
public String toLanguageKey(String prefix, String suffix); // "prefix.ns.path.suffix"
public String toShortLanguageKey();                     // "path" if minecraft ns, else "ns.path"
public String toDebugFileName(); public Path resolveAgainst(Path root);
```
NOTE: no `location()`-style aliases; `ResourceLocation` class does not exist in 26.1.

### net.minecraft.resources.ResourceKey<T>  — V (JD/net/minecraft/resources/ResourceKey.html)
```java
public class ResourceKey<T> implements Comparable<ResourceKey<?>>
public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registryName, Identifier location);
public static <T> ResourceKey<Registry<T>> createRegistryKey(Identifier identifier);
public static <T> Codec<ResourceKey<T>> codec(ResourceKey<? extends Registry<T>> registryName);
public static <T> StreamCodec<ByteBuf, ResourceKey<T>> streamCodec(ResourceKey<? extends Registry<T>> registryName);
public Identifier identifier();      // RENAMED from location() in 1.21.11+
public Identifier registry();        // the registry's Identifier
public ResourceKey<Registry<T>> registryKey();
public boolean isFor(ResourceKey<? extends Registry<?>> registry);
public <E> Optional<ResourceKey<E>> cast(ResourceKey<? extends Registry<E>> registry);
```

### net.minecraft.core.registries.Registries  — V (JD/net/minecraft/core/registries/Registries.html)
All are `public static final ResourceKey<Registry<X>> NAME`. Element types X:
```java
BLOCK -> Block; ITEM -> Item; ENTITY_TYPE -> EntityType<?>; BLOCK_ENTITY_TYPE -> BlockEntityType<?>; MENU -> MenuType<?>;
SOUND_EVENT -> SoundEvent; MOB_EFFECT -> MobEffect; PARTICLE_TYPE -> ParticleType<?>; DATA_COMPONENT_TYPE -> DataComponentType<?>;
RECIPE_SERIALIZER -> RecipeSerializer<?>; RECIPE_TYPE -> RecipeType<?>; CREATIVE_MODE_TAB -> CreativeModeTab;
DIMENSION -> Level; DIMENSION_TYPE -> DimensionType; LEVEL_STEM -> LevelStem; BIOME -> Biome; STRUCTURE -> Structure;
STRUCTURE_SET -> StructureSet; CONFIGURED_FEATURE -> ConfiguredFeature<?,?>; PLACED_FEATURE -> PlacedFeature;
CARVER -> WorldCarver<?>; CONFIGURED_CARVER -> ConfiguredWorldCarver<?>; NOISE_SETTINGS -> NoiseGeneratorSettings;
NOISE -> NormalNoise.NoiseParameters; DENSITY_FUNCTION -> DensityFunction; DENSITY_FUNCTION_TYPE -> MapCodec<? extends DensityFunction>;
CHUNK_GENERATOR -> MapCodec<? extends ChunkGenerator>; BIOME_SOURCE -> MapCodec<? extends BiomeSource>;
LOOT_TABLE -> LootTable; DAMAGE_TYPE -> DamageType; ENCHANTMENT -> Enchantment; ATTRIBUTE -> Attribute;
POINT_OF_INTEREST_TYPE -> PoiType; VILLAGER_PROFESSION -> VillagerProfession; VILLAGER_TYPE -> VillagerType; FLUID -> Fluid;
TRIGGER_TYPE -> CriterionTrigger<?>; ENVIRONMENT_ATTRIBUTE -> EnvironmentAttribute<?>  (net.minecraft.world.attribute);
ATTRIBUTE_TYPE -> AttributeType<?> (net.minecraft.world.attribute); TIMELINE -> Timeline (net.minecraft.world.timeline);
WORLD_CLOCK -> WorldClock (net.minecraft.world.clock); GAME_RULE -> GameRule<?> (net.minecraft.world.level.gamerules);
TEST_INSTANCE -> GameTestInstance; DIALOG -> Dialog (net.minecraft.server.dialog); TEMPLATE_POOL -> StructureTemplatePool;
PROCESSOR_LIST -> StructureProcessorList;   // NOT "STRUCTURE_PROCESSOR_LIST"
CUSTOM_STAT -> Identifier; ENTITY_SUB_PREDICATE_TYPE -> MapCodec<? extends EntitySubPredicate>;
DATA_COMPONENT_PREDICATE_TYPE -> DataComponentPredicate.Type<?>;  ADVANCEMENT, RECIPE, PREDICATE, ITEM_MODIFIER also exist.
public static String elementsDirPath(ResourceKey<? extends Registry<?>> registryKey);  public static String tagsDirPath(...);
public static ResourceKey<Level> levelStemToLevel(ResourceKey<LevelStem>); public static ResourceKey<LevelStem> levelToLevelStem(ResourceKey<Level>);
```
FLAG: `ITEM_SUB_PREDICATE_TYPE` does NOT exist (item sub-predicates became DATA_COMPONENT_PREDICATE_TYPE). `STRUCTURE_PROCESSOR_LIST` -> `PROCESSOR_LIST`. Element types of CHUNK_GENERATOR/BIOME_SOURCE/ENTITY_SUB_PREDICATE_TYPE are best-guess (long lines truncated) — U.

### net.minecraft.core.Registry<T>  — V (JD/net/minecraft/core/Registry.html)
`interface Registry<T> extends IdMap<T>, Keyable, HolderLookup.RegistryLookup<T>, IRegistryExtension<T>`
```java
ResourceKey<? extends Registry<T>> key();
@Nullable Identifier getKey(T thing);   Optional<ResourceKey<T>> getResourceKey(T thing);   int getId(@Nullable T thing);
@Nullable T getValue(Identifier key);   @Nullable T getValue(ResourceKey<T> key);           // was get(...) pre-1.21.2
default T getValueOrThrow(ResourceKey<T> key);
default Optional<T> getOptional(@Nullable Identifier key);  default Optional<T> getOptional(@Nullable ResourceKey<T> key);
Optional<Holder.Reference<T>> get(Identifier id);  Optional<Holder.Reference<T>> get(int id);
// from HolderGetter<T>: Optional<Holder.Reference<T>> get(ResourceKey<T>); Holder.Reference<T> getOrThrow(ResourceKey<T>);
//   Optional<HolderSet.Named<T>> get(TagKey<T>); HolderSet.Named<T> getOrThrow(TagKey<T>); Optional<Holder.Reference<T>> getRandomElementOf(TagKey<T>, RandomSource)
boolean containsKey(Identifier key); boolean containsKey(ResourceKey<T> key);
Set<Identifier> keySet(); Set<Map.Entry<ResourceKey<T>,T>> entrySet(); Set<ResourceKey<T>> registryKeySet();
Optional<Holder.Reference<T>> getRandom(RandomSource random);  default Stream<T> stream();  int size(); /*IdMap*/ @Nullable T byId(int id);
Holder<T> wrapAsHolder(T value);  default Iterable<Holder<T>> getTagOrEmpty(TagKey<T> id);  Stream<HolderSet.Named<T>> getTags();
Stream<Holder.Reference<T>> listElements(); /*HolderLookup*/  default Codec<T> byNameCodec(); default Codec<Holder<T>> holderByNameCodec();
static <T> T register(Registry<? super T> registry, String name, T value);
static <V, T extends V> T register(Registry<V> registry, Identifier location, T value);
static <V, T extends V> T register(Registry<V> registry, ResourceKey<V> key, T value);
static <R, T extends R> Holder.Reference<T> registerForHolder(Registry<R> registry, Identifier location, T value);
```
BuiltInRegistries (net.minecraft.core.registries.BuiltInRegistries): `public static final DefaultedRegistry<Block> BLOCK; DefaultedRegistry<Item> ITEM; DefaultedRegistry<EntityType<?>> ENTITY_TYPE; Registry<BlockEntityType<?>> BLOCK_ENTITY_TYPE; Registry<SoundEvent> SOUND_EVENT; Registry<MobEffect> MOB_EFFECT; Registry<Fluid> FLUID; Registry<MenuType<?>> MENU; Registry<DataComponentType<?>> DATA_COMPONENT_TYPE; Registry<CreativeModeTab> CREATIVE_MODE_TAB; Registry<CriterionTrigger<?>> TRIGGER_TYPES` — U (field types best guess from 1.21 pattern; page not fetched; `DefaultedRegistry#getKey(T)` returns non-null default key).

### net.minecraft.core.HolderLookup.Provider — V (JD/net/minecraft/core/HolderLookup.Provider.html)
```java
<T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key);
default <T> HolderLookup.RegistryLookup<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> key);
default <V> RegistryOps<V> createSerializationContext(DynamicOps<V> parent);
static HolderLookup.Provider create(Stream<HolderLookup.RegistryLookup<?>> lookups);
default Lifecycle allRegistriesLifecycle(); Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys(); default Stream<RegistryLookup<?>> listRegistries();
// NeoForge ext: Optional<Holder.Reference<T>> holder(ResourceKey<T>); Holder.Reference<T> holderOrThrow(ResourceKey<T>)
```
`net.minecraft.core.RegistryAccess extends HolderLookup.Provider` — `RegistryAccess.EMPTY` (`RegistryAccess.Frozen`), `Stream<RegistryAccess.RegistryEntry<?>> registries()`, `RegistryAccess.Frozen freeze()`, `<E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>>)`, `<E> Registry<E> lookupOrThrow(...)` — U (not fetched; consistent with 1.21.x).

### net.minecraft.core.Holder<T> — V (JD/net/minecraft/core/Holder.html)
```java
static <T> Holder<T> direct(T value);   T value();   boolean isBound();
boolean is(Identifier key); boolean is(ResourceKey<T> key); boolean is(TagKey<T> tag); boolean is(Predicate<ResourceKey<T>>); @Deprecated boolean is(Holder<T>);
Stream<TagKey<T>> tags(); Either<ResourceKey<T>, T> unwrap(); Optional<ResourceKey<T>> unwrapKey(); Holder.Kind kind(); /*REFERENCE, DIRECT*/
default String getRegisteredName();  DataComponentMap components();
// NeoForge IHolderExtension: ResourceKey<T> getKey() (nullable for direct), Holder<T> getDelegate(), HolderLookup.RegistryLookup<T> unwrapLookup()
// Holder.Reference<T> (class): key() -> ResourceKey<T>
```

### net.minecraft.core.HolderSet<T> — V (JD/net/minecraft/core/HolderSet.html)
```java
@SafeVarargs static <T> HolderSet.Direct<T> direct(Holder<T>... values);  static <T> HolderSet.Direct<T> direct(List<? extends Holder<T>> values);
static <E,T> HolderSet.Direct<T> direct(Function<E, Holder<T>> holderGetter, E... elements);  static <T> HolderSet<T> empty();
Stream<Holder<T>> stream(); int size(); boolean contains(Holder<T> value); Holder<T> get(int index);
Optional<Holder<T>> getRandomElement(RandomSource random); Optional<TagKey<T>> unwrapKey(); Either<TagKey<T>, List<Holder<T>>> unwrap(); boolean isBound();
```

### net.minecraft.tags.TagKey<T> — V (JD/net/minecraft/tags/TagKey.html)
```java
public record TagKey<T>(ResourceKey<? extends Registry<T>> registry, Identifier location)   // ctor @Deprecated; use create
public static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> registry, Identifier location);
public Identifier location();   // STILL location() in 26.1 (not renamed to identifier())
public ResourceKey<? extends Registry<T>> registry();  public boolean isFor(ResourceKey<? extends Registry<?>> registry);
public static <T> Codec<TagKey<T>> codec(ResourceKey<? extends Registry<T>>); public static <T> StreamCodec<ByteBuf, TagKey<T>> streamCodec(...);
```
Tag constant holders: `net.minecraft.tags.BlockTags` (`TagKey<Block> LOGS, LEAVES, DIRT, SAND, STONE_ORE_REPLACEABLES, MINEABLE_WITH_PICKAXE`), `ItemTags` (`TagKey<Item> LOGS, PLANKS, BOOKSHELF_BOOKS`), `EntityTypeTags`, `BiomeTags` (`TagKey<Biome> IS_OVERWORLD, IS_NETHER`), `FluidTags` (`TagKey<Fluid> WATER, LAVA`) — U (names stable since 1.19; not individually re-fetched).

### net.minecraft.sounds.SoundEvent — V (JD/net/minecraft/sounds/SoundEvent.html)
```java
public record SoundEvent(Identifier location, Optional<Float> fixedRange)
public static SoundEvent createVariableRangeEvent(Identifier location);  public static SoundEvent createFixedRangeEvent(Identifier location, float range);
public Identifier location();   // accessor is STILL location() (not identifier())
public float getRange(float volume);
public static final Codec<SoundEvent> DIRECT_CODEC; Codec<Holder<SoundEvent>> CODEC; StreamCodec<ByteBuf,SoundEvent> DIRECT_STREAM_CODEC; StreamCodec<RegistryFriendlyByteBuf,Holder<SoundEvent>> STREAM_CODEC;
```
`net.minecraft.sounds.SoundEvents` constants are `public static final SoundEvent X` for most (e.g. `BOOK_PAGE_TURN, ENDERMAN_TELEPORT, PORTAL_TRAVEL, EXPERIENCE_ORB_PICKUP, ITEM_PICKUP, NOTE_BLOCK_*`), but some are `Holder<SoundEvent>` (e.g. `NOTE_BLOCK_*`, `GOAT_HORN_SOUND_VARIANTS`) — U (not fetched; check page before using a specific one).
`net.minecraft.sounds.SoundSource` enum — V: `MASTER, MUSIC, RECORDS, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE, UI`; `String getName()`.
`net.minecraft.world.level.block.SoundType` — U (not fetched; unchanged since 1.16): `public SoundType(float volume, float pitch, SoundEvent breakSound, SoundEvent stepSound, SoundEvent placeSound, SoundEvent hitSound, SoundEvent fallSound)`; constants `SoundType.STONE, WOOD, GRAVEL, GRASS, METAL, GLASS, WOOL, SAND, SNOW, LADDER, ANVIL, NETHERRACK, AMETHYST, DEEPSLATE`; `getBreakSound()/getStepSound()/getPlaceSound()/getHitSound()/getFallSound()/getVolume()/getPitch()`. NeoForge `DeferredSoundType(float, float, Supplier<SoundEvent> x5)` in net.neoforged.neoforge.common.util.

## L1b. CreativeModeTab / Rarity / MobEffects / Blocks / Direction / Structures

### net.minecraft.world.item.CreativeModeTab — V (JD/net/minecraft/world/item/CreativeModeTab.html, .Builder.html, .Output.html)
```java
public static CreativeModeTab.Builder builder();                       // NeoForge no-arg (vanilla builder(Row,int) is @Deprecated on NeoForge)
public static final class Builder {
  public Builder(CreativeModeTab.Row row, int column);
  public Builder title(Component displayName);  public Builder icon(Supplier<ItemStack> iconGenerator);
  public Builder displayItems(CreativeModeTab.DisplayItemsGenerator displayItemsGenerator);
  public Builder displayItems(Collection<? extends Holder<? extends ItemLike>> collection);   // NeoForge: e.g. DeferredRegister.getEntries()
  public Builder alignedRight(); hideTitle(); noScrollBar(); withSearchBar(); withSearchBar(int searchBarWidth);
  public Builder backgroundTexture(Identifier backgroundTexture); withScrollBarSpriteLocation(Identifier); withTabsImage(Identifier); withLabelColor(int);
  public Builder withTabsBefore(Identifier... tabs); withTabsAfter(Identifier... tabs);
  @SafeVarargs public final Builder withTabsBefore(ResourceKey<CreativeModeTab>... tabs); @SafeVarargs public final Builder withTabsAfter(ResourceKey<CreativeModeTab>... tabs);
  public Builder withTabFactory(Function<CreativeModeTab.Builder, CreativeModeTab> tabFactory);   public CreativeModeTab build();
}
public interface Output {   // takes ItemStack / ItemLike (NOT ItemStackTemplate)
  void accept(ItemStack stack, CreativeModeTab.TabVisibility tabVisibility);  default void accept(ItemStack stack);
  default void accept(ItemLike item, CreativeModeTab.TabVisibility tabVisibility); default void accept(ItemLike item);
  default void acceptAll(Collection<ItemStack> stacks); default void acceptAll(Collection<ItemStack> stacks, TabVisibility);
}
@FunctionalInterface public interface DisplayItemsGenerator { void accept(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output); }  // U (not fetched, standard)
public record ItemDisplayParameters(FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider holders)  // U (record, unchanged since 1.20)
enum TabVisibility { PARENT_AND_SEARCH_TABS, PARENT_TAB_ONLY, SEARCH_TAB_ONLY }  // U
public Component getDisplayName(); public ItemStack getIconItem(); public Collection<ItemStack> getDisplayItems(); public boolean contains(ItemStack stack);
```
`net.minecraft.world.item.CreativeModeTabs`: `public static final ResourceKey<CreativeModeTab> BUILDING_BLOCKS, COLORED_BLOCKS, NATURAL_BLOCKS, FUNCTIONAL_BLOCKS, REDSTONE_BLOCKS, TOOLS_AND_UTILITIES, COMBAT, FOOD_AND_DRINKS, INGREDIENTS, SPAWN_EGGS, OP_BLOCKS, SEARCH, HOTBAR, INVENTORY` — U (names stable since 1.20).
NeoForge `net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent extends Event implements IModBusEvent, CreativeModeTab.Output` — V (JD/net/neoforged/neoforge/event/BuildCreativeModeTabContentsEvent.html): `CreativeModeTab getTab(); ResourceKey<CreativeModeTab> getTabKey(); FeatureFlagSet getFlags(); CreativeModeTab.ItemDisplayParameters getParameters(); boolean hasPermissions(); ObjectSortedSet<ItemStack> getParentEntries(); getSearchEntries(); void accept(ItemStack newEntry, TabVisibility visibility); void insertAfter(ItemStack existingEntry, ItemStack newEntry, TabVisibility visibility); void insertBefore(ItemStack existingEntry, ItemStack newEntry, TabVisibility); void insertFirst(ItemStack newEntry, TabVisibility); void remove(ItemStack existingEntry, TabVisibility); void removeIf(Predicate<? super ItemStack>, TabVisibility)` (+ inherited `accept(ItemStack)`, `accept(ItemLike)`, `acceptAll(...)`). MOD bus.

### Rarity / ChatFormatting — U (not fetched; stable)
`net.minecraft.world.item.Rarity` enum: `COMMON, UNCOMMON, RARE, EPIC`; `public ChatFormatting color()`; `Rarity.CODEC`, `Rarity.STREAM_CODEC`. NeoForge: `Rarity` is an extensible enum (`IExtensibleEnum` via `Rarity.create(...)`? not verified).
`net.minecraft.ChatFormatting` enum: `BLACK, DARK_BLUE, ..., GOLD, GRAY, DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE, OBFUSCATED, BOLD, STRIKETHROUGH, UNDERLINE, ITALIC, RESET`; `public @Nullable Integer getColor(); public boolean isColor(); public boolean isFormat(); public char getChar(); public String getName(); public static @Nullable ChatFormatting getByName(@Nullable String); public static @Nullable ChatFormatting getById(int)`.

### net.minecraft.world.effect.MobEffects — V (JD/net/minecraft/world/effect/MobEffects.html). All `public static final Holder<MobEffect>`:
```java
SPEED, SLOWNESS, HASTE, MINING_FATIGUE, STRENGTH, INSTANT_HEALTH, INSTANT_DAMAGE, JUMP_BOOST, NAUSEA, REGENERATION, RESISTANCE, FIRE_RESISTANCE,
WATER_BREATHING, INVISIBILITY, BLINDNESS, NIGHT_VISION, HUNGER, WEAKNESS, POISON, WITHER, HEALTH_BOOST, ABSORPTION, SATURATION, GLOWING, LEVITATION,
LUCK, UNLUCK, SLOW_FALLING, CONDUIT_POWER, DOLPHINS_GRACE, BAD_OMEN, HERO_OF_THE_VILLAGE, DARKNESS, TRIAL_OMEN, RAID_OMEN, WIND_CHARGED, WEAVING, OOZING, INFESTED, BREATH_OF_THE_NAUTILUS
```
Old names MOVEMENT_SPEED/MOVEMENT_SLOWDOWN/DIG_SPEED/DIG_SLOWDOWN/DAMAGE_BOOST/HEAL/HARM/JUMP/CONFUSION/DAMAGE_RESISTANCE are GONE.

### Blocks / Items / Fluids — U (not fetched; these constants have existed unchanged for years)
`net.minecraft.world.level.block.Blocks`: `public static final Block AIR, STONE, WATER, LAVA, BEDROCK, GRASS_BLOCK, DIRT, SAND, OAK_LOG, OAK_LEAVES, SNOW_BLOCK, ICE, NETHERRACK, END_STONE, OBSIDIAN, CRYING_OBSIDIAN, BOOKSHELF, LECTERN, GLASS, GLOWSTONE, TORCH, FIRE`.
`net.minecraft.world.item.Items`: `public static final Item AIR, BUCKET, WATER_BUCKET, LAVA_BUCKET, BOOK, WRITABLE_BOOK, PAPER, ...`.
`net.minecraft.world.level.material.Fluids`: `public static final Fluid EMPTY; FlowingFluid WATER, FLOWING_WATER, LAVA, FLOWING_LAVA`.

### net.minecraft.core.Direction — V (JD/net/minecraft/core/Direction.html?v=1)
```java
public enum Direction implements StringRepresentable { DOWN, UP, NORTH, SOUTH, WEST, EAST }
public static final StringRepresentable.EnumCodec<Direction> CODEC; Codec<Direction> VERTICAL_CODEC; StreamCodec<ByteBuf, Direction> STREAM_CODEC; IntFunction<Direction> BY_ID;
public Direction getOpposite(); getClockWise(); getCounterClockWise(); getClockWise(Direction.Axis axis); getCounterClockWise(Direction.Axis axis);
public int getStepX(); getStepY(); getStepZ(); public int get2DDataValue(); public int get3DDataValue();
public Direction.Axis getAxis(); public Direction.AxisDirection getAxisDirection();
public Vec3i getUnitVec3i();   // RENAMED from getNormal() (1.21.2+)
public Vec3 getUnitVec3(); public Vector3fc getUnitVec3f(); public Vector3f step(); public Quaternionf getRotation();
public String getName(); public String getSerializedName(); public float toYRot(); public static float getYRot(Direction direction);  // NO instance getYRot()
public static Direction fromYRot(double yRot); from3DDataValue(int); from2DDataValue(int); fromAxisAndDirection(Direction.Axis, Direction.AxisDirection);
public static Direction get(Direction.AxisDirection axisDirection, Direction.Axis axis); public static @Nullable Direction byName(String name);
public static Direction getApproximateNearest(double dx, double dy, double dz); getApproximateNearest(float,float,float); getApproximateNearest(Vec3 vec);  // RENAMED from getNearest(double,double,double)
public static @Nullable Direction getNearest(int x, int y, int z, @Nullable Direction orElse); getNearest(Vec3i vec, @Nullable Direction orElse);  // exact-axis only
public static Direction getRandom(RandomSource random); public static Stream<Direction> stream(); public static Collection<Direction> allShuffled(RandomSource);
public static Direction[] orderedByNearest(Entity entity); public boolean isFacingAngle(float yAngle);
```
`Direction.Axis` enum `X, Y, Z` (`boolean isHorizontal(); boolean isVertical(); int choose(int x,int y,int z); double choose(double,double,double); Direction.Plane getPlane(); static Axis getRandom(RandomSource)`), `Direction.AxisDirection` `POSITIVE(1), NEGATIVE(-1)` (`int getStep(); AxisDirection opposite()`), `Direction.Plane` enum `HORIZONTAL, VERTICAL` implements `Iterable<Direction>, Predicate<Direction>` (`Direction getRandomDirection(RandomSource); Axis getRandomAxis(RandomSource); Stream<Direction> stream(); List<Direction> shuffledCopy(RandomSource); int length()`) — U (inner pages not fetched; stable).

### Rotation / Mirror / Structure templates
`net.minecraft.world.level.block.Rotation` enum `NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90` (`Rotation getRotated(Rotation); Direction rotate(Direction); int rotate(int, int); static Rotation getRandom(RandomSource); static List<Rotation> getShuffled(RandomSource); OctahedralGroup rotation()`); `Mirror` enum `NONE, LEFT_RIGHT, FRONT_BACK` (`Rotation getRotation(Direction); Direction mirror(Direction); int mirror(int, int)`) — U (not fetched; stable).
`net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings` — V (JD/.../StructurePlaceSettings.html?v=1): `public StructurePlaceSettings()`; fluent `setMirror(Mirror) setRotation(Rotation) setRotationPivot(BlockPos) setIgnoreEntities(boolean) setBoundingBox(BoundingBox) setRandom(@Nullable RandomSource) setLiquidSettings(LiquidSettings) setKnownShape(boolean) addProcessor(StructureProcessor) popProcessor(StructureProcessor) clearProcessors() setFinalizeEntities(boolean) copy()`; getters `getMirror() getRotation() getRotationPivot() getRandom(@Nullable BlockPos) getBoundingBox() getProcessors() isIgnoreEntities() getKnownShape() shouldApplyWaterlogging()`. `LiquidSettings` enum: `IGNORE_WATERLOGGING, APPLY_WATERLOGGING` (U).
`StructureTemplate` — V (JD/.../StructureTemplate.html?v=1):
```java
public boolean placeInWorld(ServerLevelAccessor level, BlockPos position, BlockPos referencePos, StructurePlaceSettings settings, RandomSource random, int updateMode);
public Vec3i getSize(); public Vec3i getSize(Rotation rotation);
public BoundingBox getBoundingBox(StructurePlaceSettings settings, BlockPos pos); public BoundingBox getBoundingBox(BlockPos pos, Rotation rot, BlockPos pivot, Mirror mirror);
public static BlockPos transform(BlockPos target, Mirror mirror, Rotation rotation, BlockPos pivot);  public static Vec3 transform(Vec3, Mirror, Rotation, BlockPos);
public void fillFromWorld(Level level, BlockPos position, Vec3i size, boolean inludeEntities, List<Block> ignoreBlocks);   // CHANGED: List<Block> (was @Nullable Block)
public BlockPos calculateRelativePosition(StructurePlaceSettings settings, BlockPos pos); public static BlockPos getZeroPositionWithTransform(BlockPos, Mirror, Rotation);
public CompoundTag save(CompoundTag tag); public void load(HolderGetter<Block> blockLookup, CompoundTag tag); public void setAuthor(String); public String getAuthor();
```
`StructureTemplateManager` — V (JD/.../StructureTemplateManager.html?v=1): `Optional<StructureTemplate> get(Identifier id); StructureTemplate getOrCreate(Identifier id); boolean save(Identifier id); static boolean save(Path file, StructureTemplate t, boolean asText) throws IOException; Stream<Identifier> listTemplates(); void remove(Identifier id)`. Obtain via `MinecraftServer#getStructureManager()` (U; `ServerLevel#getStructureManager()` also exists in 1.21.x — U).

## L1c. NBT / Codecs / StreamCodecs

### net.minecraft.nbt.CompoundTag — V (JD/net/minecraft/nbt/CompoundTag.html?v=1)
```java
public CompoundTag();  public CompoundTag(int expectedSize);   // U on 2nd (index shows CompoundTag(int))
public @Nullable Tag put(String key, Tag value); putByte(String, byte); putShort(String, short); putInt(String, int); putLong(String, long); putFloat(String, float); putDouble(String, double);
putString(String, String); putByteArray(String, byte[]); putIntArray(String, int[]); putLongArray(String, long[]); putBoolean(String, boolean);   // NO putUUID (use store(key, UUIDUtil.CODEC, uuid))
public @Nullable Tag get(String key); public boolean contains(String key);            // NO contains(String,int) overload any more
public Optional<Integer> getInt(String name);  public int getIntOr(String name, int defaultValue);    // same pattern for Byte/Short/Long/Float/Double/String/Boolean: getXxx -> Optional<X>, getXxxOr(name, default) -> x
public Optional<String> getString(String name); public String getStringOr(String name, String defaultValue); public Optional<Boolean> getBoolean(String); public boolean getBooleanOr(String, boolean);
public Optional<int[]> getIntArray(String name); Optional<byte[]> getByteArray(String); Optional<long[]> getLongArray(String);
public Optional<CompoundTag> getCompound(String name); public CompoundTag getCompoundOrEmpty(String name);
public Optional<ListTag> getList(String name); public ListTag getListOrEmpty(String name);        // ListTag no longer takes an element-type int
public void remove(String key); public Set<String> keySet(); public Set<Map.Entry<String,Tag>> entrySet(); public int size(); public boolean isEmpty(); public CompoundTag copy(); public CompoundTag merge(CompoundTag other);
public <T> void store(String name, Codec<T> codec, T value);  public <T> void store(String name, Codec<T> codec, DynamicOps<Tag> ops, T value);
public <T> void storeNullable(String name, Codec<T> codec, @Nullable T value);  public <T> void store(MapCodec<T> codec, T value);
public <T> Optional<T> read(String name, Codec<T> codec);  public <T> Optional<T> read(String name, Codec<T> codec, DynamicOps<Tag> ops);  public <T> Optional<T> read(MapCodec<T> codec);
```
`ListTag` (U, 1.21.5+ shape): `add(Tag)`, `int size()`, `Tag get(int)`, `Optional<CompoundTag> getCompound(int)`, `CompoundTag getCompoundOrEmpty(int)`, `Optional<String> getString(int)`, `Optional<Integer> getInt(int)`. `StringTag.valueOf(String)`, `IntTag.valueOf(int)`, `Tag#asString()` returns `Optional<String>` (U). `net.minecraft.nbt.NbtOps.INSTANCE` (`DynamicOps<Tag>`) — U.
`net.minecraft.nbt.NbtIo` (U): `static CompoundTag readCompressed(Path path, NbtAccounter accounter) throws IOException; static void writeCompressed(CompoundTag tag, Path path) throws IOException; static CompoundTag read(Path); static void write(CompoundTag, Path)`; `NbtAccounter.unlimitedHeap()`.
`net.minecraft.nbt.NbtUtils` (U): `static Optional<BlockPos> readBlockPos(CompoundTag tag, String key); static Tag writeBlockPos(BlockPos pos); static BlockState readBlockState(HolderGetter<Block> lookup, CompoundTag tag); static CompoundTag writeBlockState(BlockState state)`.

### com.mojang.serialization.Codec (DFU) — U (library API, unchanged since DFU 6/7)
`Codec.BOOL/BYTE/SHORT/INT/LONG/FLOAT/DOUBLE/STRING`; `Codec.list(Codec<E>)`, `Codec.unboundedMap(Codec<K>, Codec<V>)`, `Codec.either(Codec<F>, Codec<S>)`, `Codec.intRange(int,int)`, `Codec.unit(T)`, `Codec.lazyInitialized(Supplier<Codec<A>>)`, `Codec.recursive(String, Function)`; instance: `fieldOf(String) -> MapCodec<A>`, `optionalFieldOf(String) -> MapCodec<Optional<A>>`, `optionalFieldOf(String, A def) -> MapCodec<A>`, `listOf()`, `xmap(Function,Function)`, `flatXmap`, `comapFlatMap`, `dispatch(Function<A,K> type, Function<K, MapCodec<? extends A>> codec)`, `codec.encodeStart(DynamicOps<T>, A) -> DataResult<T>`, `codec.parse(DynamicOps<T>, T) -> DataResult<A>`; `DataResult#getOrThrow()`, `getOrThrow(Function<String,E>)`, `result() -> Optional<R>`, `resultOrPartial(Consumer<String>)`, `error()`. `RecordCodecBuilder.create(instance -> instance.group(...).apply(instance, Ctor::new))` and `RecordCodecBuilder.mapCodec(...)` -> `MapCodec<A>`. `StringRepresentable.fromEnum(Supplier<E[]>)` -> `StringRepresentable.EnumCodec<E>`; `StringRepresentable.fromValues(Supplier<E[]>)`.
`net.minecraft.util.ExtraCodecs` — V (JD/net/minecraft/util/ExtraCodecs.html?v=1): fields `JSON, NBT, VECTOR2F, VECTOR3F, VECTOR3I, VECTOR4F, QUATERNIONF, AXISANGLE4F, MATRIX4F, RGB_COLOR_CODEC, ARGB_COLOR_CODEC, STRING_RGB_COLOR, STRING_ARGB_COLOR, UNSIGNED_BYTE, NON_NEGATIVE_INT, POSITIVE_INT, NON_NEGATIVE_LONG, POSITIVE_LONG, NON_NEGATIVE_FLOAT, POSITIVE_FLOAT, PATTERN, INSTANT_ISO8601, BASE64_STRING, ESCAPED_STRING, TAG_OR_ELEMENT_ID, BIT_SET, NON_EMPTY_STRING, CODEPOINT, RESOURCE_PATH_CODEC, UNTRUSTED_URI, CHAT_STRING, PLAYER_NAME`; methods `intRange(int,int) longRange(int,int) floatRange(float,float) nonEmptyList(Codec<List<T>>) nonEmptyHolderSet(Codec) nonEmptyMap(Codec) idResolverCodec(ToIntFunction<I>, IntFunction<I>, int notFound) idResolverCodec(Codec<I>, Function<I,E>, Function<E,I>) strictUnboundedMap(Codec,Codec) compactListCodec(Codec) orCompressed(...) overrideLifecycle(...) sizeLimitedMap(Codec,int) optionalEmptyMap(Codec) legacyEnum(Function) catchDecoderException(Codec) dispatchOptionalValue(...)`. NO `strictOptionalField` (removed), NO `UUID` here (use `net.minecraft.core.UUIDUtil.CODEC` / `UUIDUtil.STREAM_CODEC` — U).
Other codecs (U): `Vec3.CODEC` (net.minecraft.world.phys), `BlockPos.CODEC`/`BlockPos.STREAM_CODEC` (net.minecraft.core), `BlockState.CODEC` (net.minecraft.world.level.block.state), `ItemStack.CODEC`/`ItemStack.OPTIONAL_CODEC`/`ItemStack.STREAM_CODEC`/`ItemStack.OPTIONAL_STREAM_CODEC` (`StreamCodec<RegistryFriendlyByteBuf, ItemStack>`), `ComponentSerialization.CODEC`/`STREAM_CODEC`/`TRUSTED_STREAM_CODEC` (net.minecraft.network.chat), `ChunkPos.STREAM_CODEC`, `GlobalPos.CODEC`/`STREAM_CODEC`, `Vec3.STREAM_CODEC`, `RegistryCodecs.homogeneousList(ResourceKey<? extends Registry<E>>)` (net.minecraft.resources), `RegistryFixedCodec.create(ResourceKey<? extends Registry<E>>)` -> `Codec<Holder<E>>`, `RegistryOps.create(DynamicOps<T>, HolderLookup.Provider)` (net.minecraft.resources). Preferred: `registries.createSerializationContext(NbtOps.INSTANCE)` (V above).

### net.minecraft.network.codec.StreamCodec<B, V> — V (JD/net/minecraft/network/codec/StreamCodec.html?v=1)
```java
static <B, V> StreamCodec<B, V> of(StreamEncoder<B, V> encoder, StreamDecoder<B, V> decoder);   static <B, V> StreamCodec<B, V> ofMember(StreamMemberEncoder<B, V>, StreamDecoder<B, V>);
static <B, V> StreamCodec<B, V> unit(V value);   default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> factory, Function<? super O, ? extends V> getter);
default <O extends ByteBuf> StreamCodec<O, V> mapStream(Function<O, ? extends B> bufferFactory);   default <U> StreamCodec<B, U> apply(StreamCodec.CodecOperation<B, V, U> op);
default <U> StreamCodec<B, U> dispatch(Function<? super U, ? extends V> keyGetter, Function<? super V, ? extends StreamCodec<? super B, ? extends U>> codecGetter);
static <B, C, T1..Tn> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1, ..., FunctionN<T1..Tn, C> factory);   // n = 1..12 (Function2=BiFunction, then Function3..Function12 from com.mojang.datafixers.util)
static <B, T> StreamCodec<B, T> recursive(UnaryOperator<StreamCodec<B, T>> modifier);   default <S extends B> StreamCodec<S, V> cast();
```
NO `StreamCodec.STRING`; use `ByteBufCodecs.STRING_UTF8`.

### net.minecraft.network.codec.ByteBufCodecs — V (JD/net/minecraft/network/codec/ByteBufCodecs.html?v=1)
Fields (`StreamCodec<ByteBuf, X>` unless noted): `BOOL, BYTE, ROTATION_BYTE, SHORT, UNSIGNED_SHORT, INT, VAR_INT, OPTIONAL_VAR_INT (Optional<Integer>), LONG, VAR_LONG, FLOAT, DOUBLE, BYTE_ARRAY, LONG_ARRAY, STRING_UTF8, TAG, TRUSTED_TAG, COMPOUND_TAG, TRUSTED_COMPOUND_TAG, OPTIONAL_COMPOUND_TAG (Optional<CompoundTag>), VECTOR3F, QUATERNIONF, CONTAINER_ID, GAME_PROFILE_PROPERTIES, PLAYER_NAME, GAME_PROFILE, RGB_COLOR`; `int MAX_INITIAL_COLLECTION_SIZE`.
```java
static StreamCodec<ByteBuf, byte[]> byteArray(int maxSize);  static StreamCodec<ByteBuf, String> stringUtf8(int maxLength);
static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec);  fromCodec(DynamicOps<Tag>, Codec<T>);  fromCodec(Codec<T>, Supplier<NbtAccounter>);  fromCodecTrusted(Codec<T>);
static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(Codec<T> codec);  fromCodecWithRegistries(Codec<T>, Supplier<NbtAccounter>);  fromCodecWithRegistriesTrusted(Codec<T>);
static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(StreamCodec<B, V> codec);
static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> factory, StreamCodec<? super B, V> codec);  collection(IntFunction<C>, StreamCodec, int maxSize);
static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list();  list(int maxSize);      // use: ByteBufCodecs.INT.apply(ByteBufCodecs.list())
static <B extends ByteBuf, K, V, M extends Map<K,V>> StreamCodec<B, M> map(IntFunction<? extends M> factory, StreamCodec<? super B, K> keyCodec, StreamCodec<? super B, V> valueCodec);  map(..., int maxSize);
static <B extends ByteBuf, L, R> StreamCodec<B, Either<L, R>> either(StreamCodec<? super B, L> left, StreamCodec<? super B, R> right);
static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, V> lengthPrefixed(int maxSize);  registryFriendlyLengthPrefixed(int);
static <T> StreamCodec<ByteBuf, T> idMapper(IntFunction<T> idToValue, ToIntFunction<T> valueToId);  static <T> StreamCodec<ByteBuf, T> idMapper(IdMap<T> idMap);
static <T> StreamCodec<RegistryFriendlyByteBuf, T> registry(ResourceKey<? extends Registry<T>> registryKey);
static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holderRegistry(ResourceKey<? extends Registry<T>> registryKey);
static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holder(ResourceKey<? extends Registry<T>> registryKey, StreamCodec<? super RegistryFriendlyByteBuf, T> directCodec);
static <T> StreamCodec<RegistryFriendlyByteBuf, HolderSet<T>> holderSet(ResourceKey<? extends Registry<T>> registryKey);
static StreamCodec<ByteBuf, JsonElement> lenientJson(int maxSize);
```
(Param names/generics for these are U — only the method/param-type index was verified.)
NeoForge `net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs` (U): `static <B extends ByteBuf, V extends Enum<V>> StreamCodec<B, V> enumCodec(Class<V>)`, `static <B, V> StreamCodec<B, V> lazy(Supplier<StreamCodec<B, V>>)`, `static <B extends ByteBuf, V> StreamCodec<B, V> nullable(StreamCodec<B, V>)`, `static <B extends ByteBuf, T> StreamCodec<B, Set<T>> set(StreamCodec<? super B, T>)`.

## L1d. Loot / Advancements / Stats / Interaction / NeoForge core

### Loot
`net.minecraft.server.ReloadableServerRegistries.Holder` — V (JD/net/minecraft/server/ReloadableServerRegistries.Holder.html?v=1): `public LootTable getLootTable(ResourceKey<LootTable> id); public HolderLookup.Provider lookup();` obtained via `MinecraftServer#reloadableRegistries()` (U, 1.21.x).
`net.minecraft.world.level.storage.loot.LootParams.Builder` — V (JD/.../LootParams.Builder.html?v=1): `public Builder(ServerLevel level); <T> Builder withParameter(ContextKey<T> param, T value); <T> Builder withOptionalParameter(ContextKey<T> param, @Nullable T value); Builder withLuck(float luck); Builder withDynamicDrop(Identifier location, LootParams.DynamicDrop drop); LootParams create(ContextKeySet contextKeySet); ServerLevel getLevel();` (`ContextKey`/`ContextKeySet` in `net.minecraft.util.context`).
`LootTable` (U): `public ObjectArrayList<ItemStack> getRandomItems(LootParams params); getRandomItems(LootParams params, long seed); getRandomItems(LootParams, RandomSource); void fill(Container, LootParams, long seed); static LootTable.Builder lootTable(); static final LootTable EMPTY; static final ResourceKey<LootTable>? no — `LootTable.DIRECT_CODEC`, `LootTable.CODEC` (`Codec<Holder<LootTable>>`)`.
`LootContextParams` (net.minecraft.world.level.storage.loot.parameters; U): `ContextKey<Entity> THIS_ENTITY; ContextKey<Player> LAST_DAMAGE_PLAYER; ContextKey<DamageSource> DAMAGE_SOURCE; ContextKey<Entity> ATTACKING_ENTITY, DIRECT_ATTACKING_ENTITY; ContextKey<Vec3> ORIGIN; ContextKey<BlockState> BLOCK_STATE; ContextKey<BlockEntity> BLOCK_ENTITY; ContextKey<ItemStack> TOOL; ContextKey<Float> EXPLOSION_RADIUS; ContextKey<Integer> ENCHANTMENT_LEVEL`. `LootContextParamSets` (U): `ContextKeySet EMPTY, CHEST, COMMAND, SELECTOR, FISHING, ENTITY, EQUIPMENT, ARCHAEOLOGY, GIFT, PIGLIN_BARTER, VAULT, ADVANCEMENT_REWARD, ADVANCEMENT_ENTITY, ADVANCEMENT_LOCATION, BLOCK_USE, ENTITY_USE, BLOCK, SHEARING, ENCHANTED_DAMAGE, ...`. `BuiltInLootTables` (U): `ResourceKey<LootTable> EMPTY, SPAWN_BONUS_CHEST, END_CITY_TREASURE, SIMPLE_DUNGEON, VILLAGE_WEAPONSMITH, ..., ABANDONED_MINESHAFT, NETHER_BRIDGE, STRONGHOLD_LIBRARY, DESERT_PYRAMID, JUNGLE_TEMPLE, IGLOO_CHEST, WOODLAND_MANSION, ...`.
Loot builder DSL (U; unchanged since 1.21): `LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(ItemLike).setWeight(int).apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 3.0F))))).build()`; `LootItem.lootTableItem(ItemLike)` still takes `ItemLike`. Packages: `...loot.entries.LootItem`, `...loot.functions.SetItemCountFunction`, `...loot.providers.number.{ConstantValue,UniformGenerator}`, `...loot.LootPool`.
`net.minecraft.world.RandomizableContainer` (U): `static void setBlockEntityLootTable(BlockGetter level, RandomSource random, BlockPos pos, ResourceKey<LootTable> lootTable)`; `void setLootTable(ResourceKey<LootTable>, long seed)`.

### Advancements / Criteria — PACKAGE RENAMED: `net.minecraft.advancements.critereon` -> `net.minecraft.advancements.criterion` (V: all trigger classes link there)
`net.minecraft.advancements.CriteriaTriggers` — V (JD/net/minecraft/advancements/CriteriaTriggers.html): `public static final PlayerTrigger LOCATION, TICK, SLEPT_IN_BED, RAID_WIN, RAID_OMEN, AVOID_VIBRATION; ConsumeItemTrigger CONSUME_ITEM; ItemUsedOnLocationTrigger PLACED_BLOCK, ITEM_USED_ON_BLOCK, ALLAY_DROP_ITEM_ON_BLOCK; InventoryChangeTrigger INVENTORY_CHANGED; EnterBlockTrigger ENTER_BLOCK; ChangeDimensionTrigger CHANGED_DIMENSION; ImpossibleTrigger IMPOSSIBLE; KilledTrigger PLAYER_KILLED_ENTITY, ENTITY_KILLED_PLAYER; DistanceTrigger NETHER_TRAVEL, FALL_FROM_HEIGHT; LootTableTrigger GENERATE_LOOT; RecipeUnlockedTrigger RECIPE_UNLOCKED; ...`; `public static final Codec<CriterionTrigger<?>> CODEC; public static <T extends CriterionTrigger<?>> T register(String name, T criterion);` (registers into `Registries.TRIGGER_TYPE`; mods should use `DeferredRegister<CriterionTrigger<?>>` on `Registries.TRIGGER_TYPE`).
`net.minecraft.advancements.criterion.SimpleCriterionTrigger<T extends SimpleCriterionTrigger.SimpleInstance>` — V: `public abstract class ... implements CriterionTrigger<T>`; public: `final void addPlayerListener(PlayerAdvancements, CriterionTrigger.Listener<T>)`, `removePlayerListener(...)`, `removePlayerListeners(PlayerAdvancements)`; from `CriterionTrigger`: `Codec<T> codec()` (abstract, you implement), `default Criterion<T> createCriterion(T instance)`. Protected (not in public javadoc; U but standard since 1.20.2): `protected void trigger(ServerPlayer player, Predicate<T> testTrigger)`. `SimpleInstance` (U): `Optional<ContextAwarePredicate> player(); default void validate(CriterionValidator validator)`.
`net.minecraft.advancements.criterion.PlayerTrigger` — V: `public Codec<PlayerTrigger.TriggerInstance> codec(); public void trigger(ServerPlayer player);` `ChangeDimensionTrigger#trigger(ServerPlayer player, ResourceKey<Level> fromLevel, ResourceKey<Level> toLevel)` — U.
`net.minecraft.advancements.AdvancementHolder` record `(Identifier id, Advancement value)` (U). `ServerPlayer#getAdvancements()` -> `PlayerAdvancements`; `boolean award(AdvancementHolder advancement, String criterionKey)`, `boolean revoke(AdvancementHolder, String)`, `AdvancementProgress getOrStartProgress(AdvancementHolder)` (U). `MinecraftServer#getAdvancements()` -> `ServerAdvancementManager`; `@Nullable AdvancementHolder get(Identifier id)`, `Collection<AdvancementHolder> getAllAdvancements()`, `AdvancementTree tree()` (U).
Stats (U, unchanged): `net.minecraft.stats.Stats`: `StatType<Item> ITEM_USED, ITEM_CRAFTED, ITEM_BROKEN, ITEM_PICKED_UP, ITEM_DROPPED; StatType<Block> BLOCK_MINED; StatType<EntityType<?>> ENTITY_KILLED, ENTITY_KILLED_BY; StatType<Identifier> CUSTOM;` + `Identifier` constants (`Stats.JUMP, Stats.PLAY_TIME, ...`). `Stats.ITEM_USED.get(Item) -> Stat<Item>`, `Stats.CUSTOM.get(Identifier) -> Stat<Identifier>`. `Player#awardStat(Stat<?> stat)`, `awardStat(Stat<?>, int)`, `awardStat(Identifier)`, `awardStat(Identifier, int)`. Custom stats are registered in `Registries.CUSTOM_STAT` (`Registry<Identifier>`, V above).

### net.minecraft.world.InteractionResult — V (JD/net/minecraft/world/InteractionResult.html?v=1)
```java
public sealed interface InteractionResult permits InteractionResult.Success, InteractionResult.Fail, InteractionResult.Pass, InteractionResult.TryEmptyHandInteraction
static final InteractionResult.Success SUCCESS;          // client swings, server: swing + stats
static final InteractionResult.Success SUCCESS_SERVER;   // swing only on server
static final InteractionResult.Success CONSUME;          // no swing
static final InteractionResult.Fail FAIL;  static final InteractionResult.Pass PASS;  static final InteractionResult.TryEmptyHandInteraction TRY_WITH_EMPTY_HAND;
default boolean consumesAction();
public record Success(InteractionResult.SwingSource swingSource, InteractionResult.ItemContext itemContext) implements InteractionResult {
  public Success heldItemTransformedTo(ItemStack itemStack); public Success withoutItem(); public boolean wasItemInteraction(); public @Nullable ItemStack heldItemTransformedTo(); public boolean consumesAction(); }
enum SwingSource { NONE, CLIENT, SERVER }  // U
```
`indicateItemUse()` does NOT exist. `net.minecraft.world.ItemInteractionResult` — REMOVED (not in javadoc; 1.21.2+). `net.minecraft.world.InteractionHand` enum `MAIN_HAND, OFF_HAND` (U).

### NeoForge / FML core
`TriState` — MOVED TO VANILLA: `net.minecraft.util.TriState` — V (JD/net/minecraft/util/TriState.html). `net.neoforged.neoforge.common.util.TriState` does NOT exist in 26.1 (absent from package listing).
```java
public enum TriState implements StringRepresentable { TRUE, FALSE, DEFAULT }
public static final Codec<TriState> CODEC;  public static TriState from(boolean value);   // NOT of(Boolean)
public boolean toBoolean(boolean defaultValue); public boolean isTrue(); public boolean isFalse(); public boolean isDefault(); public String getSerializedName();
```
`net.neoforged.neoforge.common.NeoForge` — `public static final IEventBus EVENT_BUS` (U; referenced as `NeoForge.EVENT_BUS.post(...)` in ServerLifecycleHooks source — V usage).
`net.neoforged.bus.api.IEventBus` — V (JD/net/neoforged/bus/api/IEventBus.html):
```java
void register(Object target);  void unregister(Object object);
<T extends Event> void addListener(Consumer<T> consumer);  <T extends Event> void addListener(Class<T> eventType, Consumer<T> consumer);
<T extends Event> void addListener(EventPriority priority, Consumer<T> consumer);  addListener(EventPriority priority, Class<T> eventType, Consumer<T> consumer);
<T extends Event> void addListener(EventPriority priority, boolean receiveCanceled, Consumer<T> consumer);  addListener(EventPriority, boolean, Class<T>, Consumer<T>);
<T extends Event> void addListener(boolean receiveCanceled, Consumer<T> consumer);  addListener(boolean receiveCanceled, Class<T> eventType, Consumer<T> consumer);
<T extends Event> T post(T event);  <T extends Event> T post(EventPriority phase, T event);  void start();
```
`net.neoforged.bus.api.Event` (abstract class, protected/public no-arg ctor), `net.neoforged.bus.api.ICancellableEvent` (`default void setCanceled(boolean)`, `default boolean isCanceled()`), `net.neoforged.bus.api.EventPriority` enum `HIGHEST, HIGH, NORMAL, LOW, LOWEST`, `@net.neoforged.bus.api.SubscribeEvent(EventPriority priority() default NORMAL, boolean receiveCanceled() default false)` — U (unchanged bus 8.x API).
`@net.neoforged.fml.common.EventBusSubscriber` — V (https://raw.githubusercontent.com/neoforged/FancyModLoader/main/loader/src/main/java/net/neoforged/fml/common/EventBusSubscriber.java): members are ONLY `Dist[] value() default {Dist.CLIENT, Dist.DEDICATED_SERVER}` and `String modid() default ""`. NO `bus` member — bus is chosen automatically: events implementing `IModBusEvent` go to the mod bus, others to `NeoForge.EVENT_BUS`. Scans **static** `@SubscribeEvent` methods only.
`@net.neoforged.fml.common.Mod` — V (JD/net/neoforged/fml/common/Mod.html): `String value()` (required, lowercase modid), `Dist[] dist() default {CLIENT, DEDICATED_SERVER}`, `String[] depends() default {}`. Mod ctor injectable params (U, documented at docs.neoforged.net/docs/gettingstarted/modfiles): any subset/order of `IEventBus modBus`, `ModContainer container`, `Dist dist`, `FMLModContainer`.
`net.neoforged.fml.ModContainer` — V (JD/net/neoforged/fml/ModContainer.html): `String getModId(); String getNamespace(); IModInfo getModInfo(); abstract @Nullable IEventBus getEventBus(); void registerConfig(ModConfig.Type type, IConfigSpec configSpec); void registerConfig(ModConfig.Type type, IConfigSpec configSpec, String fileName); <T extends IExtensionPoint> void registerExtensionPoint(Class<T> point, T extension) / (Class<T>, Supplier<T>)`. `net.neoforged.fml.ModLoadingContext` still exists — V: `static ModLoadingContext get(); ModContainer getActiveContainer(); String getActiveNamespace();` (no `registerConfig` here any more — use ModContainer). `net.neoforged.fml.config.ModConfig.Type` enum `COMMON, CLIENT, SERVER, STARTUP` (U).
`net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent` / `FMLClientSetupEvent` (mod bus; U): `CompletableFuture<Void> enqueueWork(Runnable work); <T> CompletableFuture<T> enqueueWork(Supplier<T> work)`.
`net.neoforged.neoforge.common.ModConfigSpec` + `ModConfigSpec.Builder` — V (JD/net/neoforged/neoforge/common/ModConfigSpec.Builder.html):
```java
public Builder();
public <T> ModConfigSpec.ConfigValue<T> define(String path, T defaultValue);  define(String path, T defaultValue, Predicate<Object> validator);  define(List<String> path, T defaultValue);
public ModConfigSpec.BooleanValue define(String path, boolean defaultValue);  define(String path, Supplier<Boolean> defaultSupplier);
public ModConfigSpec.IntValue defineInRange(String path, int defaultValue, int min, int max);  ModConfigSpec.LongValue defineInRange(String, long, long, long);  ModConfigSpec.DoubleValue defineInRange(String, double, double, double);
public <T> ModConfigSpec.ConfigValue<T> defineInList(String path, T defaultValue, Collection<? extends T> acceptableValues);
public <V extends Enum<V>> ModConfigSpec.EnumValue<V> defineEnum(String path, V defaultValue);  defineEnum(String, V, V... acceptableValues);  defineEnum(String, V, Collection<V>);  defineEnum(String, V, Predicate<Object>);  (+ EnumGetMethod overloads)
public <T> ModConfigSpec.ConfigValue<List<? extends T>> defineList(String path, List<? extends T> defaultValue, Supplier<T> newElementSupplier, Predicate<Object> elementValidator);   // 3-arg (no Supplier) overload is @Deprecated
public <T> ModConfigSpec.ConfigValue<List<? extends T>> defineListAllowEmpty(String path, List<? extends T> defaultValue, Supplier<T> newElementSupplier, Predicate<Object> elementValidator);
public Builder comment(String comment); comment(String... comment); translation(String translationKey); worldRestart(); gameRestart();
public Builder push(String path); push(List<String> path); pop(); pop(int count);  public <T> Pair<T, ModConfigSpec> configure(Function<Builder, T> builder);  public ModConfigSpec build();
```
`ModConfigSpec.ConfigValue<T>`: `T get(); void set(T value); T getDefault(); List<String> getPath(); void save()`; `IntValue#getAsInt()`, `LongValue#getAsLong()`, `DoubleValue#getAsDouble()`, `BooleanValue#getAsBoolean()`, `EnumValue<T>` — U (stable). `ModConfigSpec implements IConfigSpec` (so it's accepted by `ModContainer#registerConfig`) — U.
FML environment — V (https://raw.githubusercontent.com/neoforged/FancyModLoader/main/loader/src/main/java/net/neoforged/fml/loading/FMLEnvironment.java, JD/net/neoforged/fml/loading/FMLLoader.html): `net.neoforged.fml.loading.FMLEnvironment` has ONLY `public static Dist getDist()` and `public static boolean isProduction()` (fields `FMLEnvironment.dist` / `.production` are GONE). `FMLLoader.getCurrent()` -> `FMLLoader` instance with `Dist getDist(); boolean isProduction(); Path getGameDir(); LoadingModList getLoadingModList()` (instance methods, not static). `net.neoforged.api.distmarker.Dist` enum `CLIENT, DEDICATED_SERVER` with `boolean isClient(); boolean isDedicatedServer()` (U). `net.neoforged.fml.ModList.get().isLoaded(String modid)` and `Optional<? extends ModContainer> getModContainerById(String)` (U).
`net.neoforged.fml.DistExecutor` — REMOVED (javadoc 404) — V. Use `FMLEnvironment.getDist()` checks + separate client classes / `@Mod(dist = Dist.CLIENT)` entrypoints.
`net.neoforged.fml.loading.FMLPaths` enum — V (JD/net/neoforged/fml/loading/FMLPaths.html): `GAMEDIR, JIJ_CACHEDIR, MODSDIR, CONFIGDIR, FMLCONFIG`; `public Path get(); public Path relative(); public static Path getOrCreateGameRelativePath(Path path)`.
`net.neoforged.neoforge.server.ServerLifecycleHooks` — V (source 26.1.x): `public static @Nullable MinecraftServer getCurrentServer();` (also `handleServerAboutToStart/Starting/Started/Stopping/Stopped(MinecraftServer)` internal).
`net.neoforged.neoforge.common.util.Lazy<T>` — V exists (package listing): `static <T> Lazy<T> of(Supplier<T>); static <T> Lazy<T> concurrentOf(Supplier<T>); T get()` (member shapes U). `net.neoforged.neoforge.common.util.DeferredSoundType` — V exists. `NeoForgeExtraCodecs` — V exists in same package.
`net.neoforged.neoforge.common.NeoForgeMod` attributes (U): `Holder<Attribute> SWIM_SPEED, NAMETAG_DISTANCE, CREATIVE_FLIGHT` (vanilla now owns BLOCK_INTERACTION_RANGE/ENTITY_INTERACTION_RANGE/STEP_HEIGHT/GRAVITY in `Attributes`).
`net.minecraft.world.level.storage.LevelResource` (U, unchanged): `public LevelResource(String id); public String getId(); static final LevelResource ROOT, PLAYER_DATA_DIR, LEVEL_DATA_FILE, ...`; `MinecraftServer#getWorldPath(LevelResource) -> Path` (V usage in ServerLifecycleHooks: `server.getWorldPath(SERVERCONFIG)`); `MinecraftServer#getServerDirectory() -> Path` (U).

---

# L2 — Events, Commands, Explosions/Particles/Chunks/Villagers/Weather/Time (MC 26.1 / NeoForge 26.1.2.x)

Legend: `V(url)` = VERIFIED from that page; `U(...)` = UNVERIFIED best guess. Javadoc mirror base `JD = https://lexxie.dev/neoforge/26.1/`; NeoForge raw source base `NF = https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/src/main/java/net/neoforged/neoforge/`.

## L2a-0. Event API basics
```java
// net.neoforged.fml.common.EventBusSubscriber  V(https://raw.githubusercontent.com/neoforged/FancyModLoader/main/loader/src/main/java/net/neoforged/fml/common/EventBusSubscriber.java)
public @interface EventBusSubscriber { Dist[] value() default {Dist.CLIENT, Dist.DEDICATED_SERVER}; String modid() default ""; }
// NO `bus=` parameter any more. Routing is automatic: events implementing net.neoforged.fml.event.IModBusEvent go to the mod bus,
// everything else to net.neoforged.neoforge.common.NeoForge.EVENT_BUS. Handlers must be `static` + @net.neoforged.bus.api.SubscribeEvent.
// net.neoforged.bus.api.{Event, ICancellableEvent, IEventBus, EventPriority, SubscribeEvent}
// Custom event: public class MyEvent extends Event implements ICancellableEvent { ... }   post: NeoForge.EVENT_BUS.post(new MyEvent(..)).isCanceled()
// ICancellableEvent: default void setCanceled(boolean); default boolean isCanceled();   (PlayerInteractEvent overrides setCanceled -> V(NF event/entity/player/PlayerInteractEvent.java))
// IEventBus#addListener(Consumer<T>) / addListener(EventPriority, Consumer<T>) / addListener(EventPriority, boolean receiveCanceled, Class<T>, Consumer<T>)  U(bus API unchanged since 21.x)
```

## L2a-1. Game-bus events (NeoForge.EVENT_BUS) — package `net.neoforged.neoforge.event...`
```java
// --- server lifecycle: net.neoforged.neoforge.event.server.*   U(files not fetched; class names unchanged since 20.x)
ServerStartingEvent / ServerStartedEvent / ServerStoppingEvent / ServerStoppedEvent extends ServerLifecycleEvent { MinecraftServer getServer(); }

// --- ticks: net.neoforged.neoforge.event.tick.*   V(NF event/tick/ServerTickEvent.java)
abstract class ServerTickEvent extends Event { boolean hasTime(); MinecraftServer getServer(); }   static class Pre / Post extends ServerTickEvent
// LevelTickEvent.Pre/Post { Level getLevel(); boolean hasTime(); }  PlayerTickEvent.Pre/Post { Player getEntity(); }  EntityTickEvent.Pre/Post { Entity getEntity(); }  U(pattern; only ServerTickEvent fetched)

// --- players: net.neoforged.neoforge.event.entity.player.PlayerEvent   V(NF event/entity/player/PlayerEvent.java)  all not cancellable unless noted
abstract class PlayerEvent extends LivingEvent { Player getEntity(); }
PlayerEvent.PlayerLoggedInEvent(Player) / PlayerLoggedOutEvent(Player)
PlayerEvent.PlayerRespawnEvent { boolean isEndConquered(); }
PlayerEvent.PlayerChangedDimensionEvent { ResourceKey<Level> getFrom(); ResourceKey<Level> getTo(); }
PlayerEvent.Clone { Player getOriginal(); boolean isWasDeath(); }
PlayerEvent.ItemCraftedEvent { ItemStack getCrafting(); Container getInventory(); }
PlayerEvent.ItemSmeltedEvent { ItemStack getSmelting(); int getAmountRemoved(); }
PlayerEvent.BreakSpeed implements ICancellableEvent { BlockState getState(); float getOriginalSpeed(); float getNewSpeed(); void setNewSpeed(float); Optional<BlockPos> getPosition(); }
PlayerEvent.HarvestCheck { BlockState getTargetBlock(); BlockGetter getLevel(); BlockPos getPos(); boolean canHarvest(); void setCanHarvest(boolean); }
PlayerEvent.PlayerChangeGameModeEvent implements ICancellableEvent { GameType getCurrentGameMode(); GameType getNewGameMode(); void setNewGameMode(GameType); }
PlayerEvent.StartTracking / StopTracking { Entity getTarget(); }   PlayerEvent.NameFormat / TabListNameFormat
// ItemPickupEvent lives in its own file net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre/Post (Pre: canPickup TriState; Post: getOriginalStack/getCurrentStack) U(21.x shape)

// --- interaction: net.neoforged.neoforge.event.entity.player.PlayerInteractEvent   V(NF .../PlayerInteractEvent.java)
abstract class PlayerInteractEvent extends PlayerEvent { InteractionHand getHand(); ItemStack getItemStack(); BlockPos getPos(); @Nullable Direction getFace(); Level getLevel(); LogicalSide getSide(); }
RightClickBlock implements ICancellableEvent { TriState getUseBlock()/getUseItem(); void setUseBlock(TriState)/setUseItem(TriState); BlockHitResult getHitVec(); InteractionResult getCancellationResult(); void setCancellationResult(InteractionResult); }
RightClickItem implements ICancellableEvent { get/setCancellationResult }   RightClickEmpty (client only, not cancellable)
LeftClickBlock implements ICancellableEvent { TriState getUseBlock()/getUseItem(); setUseBlock/setUseItem; LeftClickBlock.Action getAction(); /* START, STOP, ABORT, CLIENT_HOLD */ }   LeftClickEmpty
EntityInteract implements ICancellableEvent { Entity getTarget(); get/setCancellationResult }   EntityInteractSpecific { Vec3 getLocalPos(); Entity getTarget(); ... }

// --- living/damage: net.neoforged.neoforge.event.entity.living.*
LivingIncomingDamageEvent extends LivingEvent implements ICancellableEvent  V(NF .../living/LivingIncomingDamageEvent.java)
  { DamageContainer getContainer(); DamageSource getSource(); float getAmount(); float getOriginalAmount(); void setAmount(float); void addReductionModifier(DamageContainer.Reduction, IReductionFunction); void setInvulnerabilityTicks(int); }
LivingDamageEvent.Pre  V(NF .../living/LivingDamageEvent.java) { DamageContainer getContainer(); DamageSource getSource(); float getNewDamage(); float getOriginalDamage(); void setNewDamage(float); }  (NOT cancellable; set 0 instead)
LivingDamageEvent.Post { float getOriginalDamage(); DamageSource getSource(); float getInflictedDamage(); float getHealthDamage(); float getBlockedDamage(); float getShieldDamage(); int getPostAttackInvulnerabilityTicks(); float getReduction(DamageContainer.Reduction); }
LivingDeathEvent extends LivingEvent implements ICancellableEvent { DamageSource getSource(); }   U(unchanged)
LivingHealEvent extends LivingEvent implements ICancellableEvent { float getAmount(); void setAmount(float); }   U(unchanged)
LivingFallEvent implements ICancellableEvent { float getDistance()/setDistance; float getDamageMultiplier()/setDamageMultiplier }   U(unchanged)

// --- entity: net.neoforged.neoforge.event.entity.*   U(unchanged 21.x shapes)
EntityJoinLevelEvent extends EntityEvent implements ICancellableEvent { Level getLevel(); boolean loadedFromDisk(); }
EntityLeaveLevelEvent extends EntityEvent { Level getLevel(); }
EntityTravelToDimensionEvent extends EntityEvent implements ICancellableEvent { ResourceKey<Level> getDimension(); }
EntityTeleportEvent extends EntityEvent implements ICancellableEvent { double getTargetX/Y/Z(); void setTargetX/Y/Z(double); Vec3 getTarget(); double getPrevX/Y/Z(); }  subclasses: TeleportCommand, SpreadPlayersCommand, EnderEntity, EnderPearl, ChorusFruit

// --- chunks/level: net.neoforged.neoforge.event.level.*
ChunkEvent<T extends ChunkAccess> extends LevelEvent { T getChunk(); }  ChunkEvent.Load { boolean isNewChunk(); }  ChunkEvent.Unload   U(only ChunkDataEvent fetched)
ChunkDataEvent  V(NF event/level/ChunkDataEvent.java): abstract class ChunkDataEvent extends ChunkEvent<ChunkAccess> { SerializableChunkData getData(); }
  ChunkDataEvent.Load(ChunkAccess, SerializableChunkData) { ChunkType getType(); }   ChunkDataEvent.Save(ChunkAccess, LevelAccessor, SerializableChunkData)   // no ValueInput; custom data => data attachments
LevelEvent { LevelAccessor getLevel(); }  LevelEvent.Load / Unload / Save / CreateSpawnPosition(implements ICancellableEvent; ServerLevelData getSettings())   U
BlockEvent { LevelAccessor getLevel(); BlockPos getPos(); BlockState getState(); }  BlockEvent.BreakEvent(cancellable; Player getPlayer())  EntityPlaceEvent(cancellable; @Nullable Entity getEntity(); BlockSnapshot getBlockSnapshot(); BlockState getPlacedBlock()/getPlacedAgainst())  EntityMultiPlaceEvent { List<BlockSnapshot> getReplacedBlockSnapshots(); }  NeighborNotifyEvent(cancellable; EnumSet<Direction> getNotifiedSides(); boolean getForceRedstoneUpdate())   U
ExplosionEvent  V(NF event/level/ExplosionEvent.java): abstract class ExplosionEvent extends Event { Level getLevel(); ServerExplosion getExplosion(); }
  ExplosionEvent.Start implements ICancellableEvent      ExplosionEvent.Detonate { List<BlockPos> getAffectedBlocks(); List<Entity> getAffectedEntities(); }
ExplosionKnockbackEvent extends ExplosionEvent { Entity getAffectedEntity(); Vec3 getKnockbackVelocity(); void setKnockbackVelocity(Vec3); }   U

// --- misc game-bus
RegisterCommandsEvent extends Event  V(NF event/RegisterCommandsEvent.java) { CommandDispatcher<CommandSourceStack> getDispatcher(); Commands.CommandSelection getCommandSelection(); CommandBuildContext getBuildContext(); }
AddReloadListenerEvent is GONE -> net.neoforged.neoforge.event.AddServerReloadListenersEvent extends SortedReloadListenerEvent  V(JD event/package-summary.html, AddClientReloadListenersEvent.html for base methods): void addListener(Identifier id, PreparableReloadListener); void addDependency(Identifier first, Identifier second); Identifier getLastVanillaListener(); getNameLookup(); getGraph(); getRegistry()  ; server variant (GAME bus, V JD AddServerReloadListenersEvent.html): ReloadableServerResources getServerResources(); ICondition.IContext getConditionContext(); @Deprecated RegistryAccess getRegistryAccess() (use ContextAwareReloadListener#getRegistryLookup()); <T extends PreparableReloadListener> void addRetainedListener(ListenerKey<T>, T)
OnDatapackSyncEvent  V(NF event/OnDatapackSyncEvent.java): PlayerList getPlayerList(); @Nullable ServerPlayer getPlayer(); Stream<ServerPlayer> getRelevantPlayers(); void sendRecipes(RecipeType<?>...)/sendRecipes(Iterable<RecipeType<?>>); Set<RecipeType<?>> getRecipeTypesToSend()   // NO sendPacket(); use PacketDistributor.sendToPlayer(player, payload)
AdvancementEvent.AdvancementEarnEvent / AdvancementProgressEvent { AdvancementHolder getAdvancement(); }  U
VillagerTradesEvent / WandererTradesEvent: NOT present in 26.1 javadoc — package net.neoforged.neoforge.event.village only contains VillageSiegeEvent  V(JD net/neoforged/neoforge/event/village/package-summary.html). Villager trades are datapack-driven in 26.1 (see L2c villager section).
FinalizeSpawnEvent extends MobSpawnEvent implements ICancellableEvent  V(NF .../living/FinalizeSpawnEvent.java) { Mob getEntity(); ServerLevelAccessor getLevel(); double getX/Y/Z(); DifficultyInstance getDifficulty(); void setDifficulty(DifficultyInstance); EntitySpawnReason getSpawnType(); @Nullable SpawnGroupData getSpawnData(); void setSpawnData(@Nullable SpawnGroupData); @Nullable Either<BlockEntity,Entity> getSpawner(); void setSpawnCancelled(boolean); boolean isSpawnCancelled(); }  // MobSpawnEvent.PositionCheck / SpawnPlacementCheck also exist (V pkg listing)
PlayerSetSpawnEvent extends PlayerEvent implements ICancellableEvent  V(NF source): ctor (Player, ServerPlayer.@Nullable RespawnConfig); boolean isForced(); @Nullable BlockPos getNewSpawn(); ResourceKey<Level> getSpawnLevel()
PlayerRespawnPositionEvent extends PlayerEvent  V(NF source): TeleportTransition getTeleportTransition(); void setTeleportTransition(TeleportTransition); void setRespawnLevel(ResourceKey<Level>); TeleportTransition getOriginalTeleportTransition(); boolean copyOriginalSpawnPosition(); void setCopyOriginalSpawnPosition(boolean); boolean isFromEndFight()
CanPlayerSleepEvent / CanContinueSleepingEvent / PlayerWakeUpEvent / SleepFinishedTimeEvent(event.level; "adjusts the clock of a level after sleep") exist  V(pkg listings) — getters U.
GameRuleChangedEvent (event.level)  V(NF source) — see L2c-4.
```

## L2b. Commands (Brigadier `com.mojang.brigadier.*`, vanilla `net.minecraft.commands.*`)
```java
// Commands  V(JD net/minecraft/commands/Commands.html)  package net.minecraft.commands
public static LiteralArgumentBuilder<CommandSourceStack> literal(String literal)
public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type)
public static <T extends PermissionSetSupplier> PermissionProviderCheck<T> hasPermission(PermissionCheck permission)   // PermissionProviderCheck<T> implements Predicate<T>
public static final PermissionCheck LEVEL_ALL, LEVEL_MODERATORS, LEVEL_GAMEMASTERS, LEVEL_ADMINS, LEVEL_OWNERS;       // old int levels 0..4
public void performPrefixedCommand(CommandSourceStack sender, String command)      // server.getCommands().performPrefixedCommand(src, "say hi")
public CommandDispatcher<CommandSourceStack> getDispatcher()
// 26.1 PERMISSIONS: `src.hasPermission(2)` is GONE. Use:  .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
// or manual: src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)   (net.minecraft.server.permissions.{Permissions,Permission,PermissionSet,PermissionCheck,PermissionLevel})  V(JD .../server/permissions/Permissions.html, PermissionSet.html)
// PermissionSet: boolean hasPermission(Permission); static PermissionSet NO_PERMISSIONS / ALL_PERMISSIONS.  Permissions.COMMANDS_MODERATOR/GAMEMASTER/ADMIN/OWNER/ENTITY_SELECTORS (type Permission).

// CommandSourceStack  V(JD net/minecraft/commands/CommandSourceStack.html) — method index verbatim:
withSource(CommandSource) withEntity(Entity) withPosition(Vec3) withRotation(Vec2) withCallback(CommandResultCallback) withSuppressedOutput() withPermission(PermissionSet) withMaximumPermission(PermissionSet)
withAnchor(EntityAnchorArgument.Anchor) withLevel(ServerLevel) facing(Entity, EntityAnchorArgument.Anchor) facing(Vec3)
Component getDisplayName(); String getTextName(); PermissionSet permissions(); Vec3 getPosition(); ServerLevel getLevel(); @Nullable Entity getEntity(); Entity getEntityOrException() throws CommandSyntaxException;
ServerPlayer getPlayerOrException() throws CommandSyntaxException; @Nullable ServerPlayer getPlayer(); boolean isPlayer(); Vec2 getRotation(); MinecraftServer getServer(); EntityAnchorArgument.Anchor getAnchor();
void sendSystemMessage(Component); void sendSuccess(Supplier<Component> message, boolean allowLogging); void sendFailure(Component); Collection<String> getOnlinePlayerNames(); Set<ResourceKey<Level>> levels(); RegistryAccess registryAccess(); FeatureFlagSet enabledFeatures(); CommandDispatcher<CommandSourceStack> dispatcher(); boolean isSilent();
// U: MinecraftServer#createCommandSourceStack() -> CommandSourceStack (unchanged); server.getCommands() -> Commands.

// Argument types (package net.minecraft.commands.arguments unless noted)  V(JD .../commands/arguments/package-summary.html for class list)
IdentifierArgument.id() ; static Identifier IdentifierArgument.getId(CommandContext<CommandSourceStack> ctx, String name)          V(JD IdentifierArgument.html)  — ResourceLocationArgument is GONE (Identifier replaces ResourceLocation)
DimensionArgument.dimension() ; static ServerLevel DimensionArgument.getDimension(CommandContext<CommandSourceStack>, String) throws CommandSyntaxException   V(JD DimensionArgument.html)  (ArgumentType<Identifier>)
EntityArgument.entity()/entities()/player()/players() ; static Entity getEntity(ctx,name) / Collection<? extends Entity> getEntities / ServerPlayer getPlayer / Collection<ServerPlayer> getPlayers / getOptionalEntities / getOptionalPlayers  (all throws CommandSyntaxException)  V(JD EntityArgument.html)
// classes present in 26.1 (V by package listing): AngleArgument, ColorArgument, ComponentArgument, CompoundTagArgument, GameModeArgument, GameProfileArgument, HexColorArgument, MessageArgument, ParticleArgument, ResourceArgument<T>, ResourceKeyArgument<T>, ResourceOrTagArgument<T>, ResourceOrTagKeyArgument<T>, ResourceSelectorArgument<T>, SlotArgument, TimeArgument, UuidArgument, StyleArgument, TeamArgument, WaypointArgument
// U (unchanged 21.x shapes): ResourceArgument.resource(CommandBuildContext, ResourceKey<? extends Registry<T>>) + getResource(ctx, name, ResourceKey<Registry<T>>) -> Holder.Reference<T>; ResourceKeyArgument.key(ResourceKey<? extends Registry<T>>) + getRegistryKey(ctx,name,registryKey, DynamicCommandExceptionType); ComponentArgument.textComponent(CommandBuildContext)+getResolvedComponent(ctx,name); UuidArgument.uuid()/getUuid; TimeArgument.time()/time(int min) -> int via IntegerArgumentType.getInteger; GameModeArgument.gameMode()/getGameMode(ctx,name); ColorArgument.color()/getColor -> ChatFormatting.
// net.minecraft.commands.arguments.coordinates: Vec3Argument.vec3()/vec3(boolean centerCorrect) + getVec3(ctx,name) Vec3 / getCoordinates(ctx,name) Coordinates; BlockPosArgument.blockPos() + getBlockPos(ctx,name)/getLoadedBlockPos(ctx,name)/getLoadedBlockPos(ctx, ServerLevel, name)  U
// net.minecraft.commands.arguments.item: ItemArgument.item(CommandBuildContext) + getItem(ctx,name) -> ItemInput; ItemInput#createItemStack(int count, boolean allowOversized)  U
// net.minecraft.commands.arguments.blocks: BlockStateArgument.block(CommandBuildContext) + getBlock(ctx,name) -> BlockInput  U
// Brigadier: IntegerArgumentType.integer()/integer(min)/integer(min,max) + getInteger(ctx,name); StringArgumentType.word()/string()/greedyString() + getString; BoolArgumentType.bool()/getBool; FloatArgumentType.floatArg(..)/getFloat; DoubleArgumentType.doubleArg(..)/getDouble; LongArgumentType  U(Brigadier stable)
// ArgumentBuilder<S,T>: then(ArgumentBuilder), then(CommandNode), executes(Command<S>), requires(Predicate<S>), redirect(CommandNode); RequiredArgumentBuilder#suggests(SuggestionProvider<S>); Command.SINGLE_SUCCESS = 1; CommandContext#getSource()/getArgument(String, Class<V>); CommandSyntaxException; SimpleCommandExceptionType(Message) / DynamicCommandExceptionType(Function<Object,Message>) — net.minecraft.network.chat.Component implements com.mojang.brigadier.Message  U(stable)
// SharedSuggestionProvider (net.minecraft.commands): static CompletableFuture<Suggestions> suggest(Iterable<String>, SuggestionsBuilder) / suggest(Stream<String>, ..) / suggestResource(Iterable<Identifier>, SuggestionsBuilder)  U(name of suggestResource may be unchanged; verify)
// CommandBuildContext (interface, net.minecraft.commands): HolderLookup.Provider-like; lookupOrThrow(ResourceKey<? extends Registry<T>>); static Commands.createValidationContext(HolderLookup.Provider) V(JD Commands.html)
```

## L2c-1. Explosions / particles / chunks / raycast
```java
// Level (net.minecraft.world.level.Level)  V(JD ServerLevel.html "Methods inherited from class Level" anchors)
public void explode(@Nullable Entity source, double x, double y, double z, float radius, Level.ExplosionInteraction mode)
public void explode(@Nullable Entity source, double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction mode)
public void explode(@Nullable Entity source, @Nullable DamageSource ds, @Nullable ExplosionDamageCalculator calc, double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction mode)
public void explode(@Nullable Entity source, @Nullable DamageSource ds, @Nullable ExplosionDamageCalculator calc, Vec3 pos, float radius, boolean fire, Level.ExplosionInteraction mode)
// ServerLevel override (net.minecraft.server.level.ServerLevel)  V(JD ServerLevel.html #explode)
public void explode(@Nullable Entity source, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float r, boolean fire,
                    Level.ExplosionInteraction interactionType, ParticleOptions smallExplosionParticles, ParticleOptions largeExplosionParticles,
                    WeightedList<ExplosionParticleInfo> blockParticles, Holder<SoundEvent> explosionSound)   // NEW param: WeightedList<net.minecraft.core.particles.ExplosionParticleInfo> blockParticles (net.minecraft.util.random.WeightedList)
enum Level.ExplosionInteraction { NONE, BLOCK, MOB, TNT, TRIGGER }  V(JD Level.ExplosionInteraction.html)  — no STANDARD
// Explosion (interface, net.minecraft.world.level.Explosion) U: ServerLevel level(); Vec3 center(); float radius(); boolean canTriggerBlocks(); boolean shouldAffectBlocklikeEntities(); @Nullable LivingEntity getIndirectSourceEntity(); @Nullable Entity getDirectSourceEntity(); Explosion.BlockInteraction getBlockInteraction();
// ServerExplosion (net.minecraft.world.level.ServerExplosion) U: ctor (ServerLevel, @Nullable Entity, @Nullable DamageSource, @Nullable ExplosionDamageCalculator, Vec3, float, boolean fire, Explosion.BlockInteraction); void explode(); Map<Player,Vec3> getHitPlayers(); List<BlockPos> getToBlow()? (26.1: check; NeoForge ExplosionEvent.Detonate passes List<BlockPos>)

// Particles — ServerLevel  V(JD ServerLevel.html)
public <T extends ParticleOptions> int sendParticles(T particle, double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed)
public <T extends ParticleOptions> int sendParticles(T particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed)
public <T extends ParticleOptions> boolean sendParticles(ServerPlayer player, T particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed)
// Level (client-side visual)  V(anchors):
public void addParticle(ParticleOptions, double x, double y, double z, double dx, double dy, double dz)
public void addParticle(ParticleOptions, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, double dx, double dy, double dz)
public void addAlwaysVisibleParticle(ParticleOptions, double x,y,z, double dx,dy,dz)  /  addAlwaysVisibleParticle(ParticleOptions, boolean overrideLimiter, double x,y,z, dx,dy,dz)

// Chunk forcing / tickets  V(JD ServerLevel.html, TicketType.html)
public boolean setChunkForced(int chunkX, int chunkZ, boolean forced)      // ServerLevel
public LongSet getForceLoadedChunks()                                       // ServerLevel (name kept; NOT getForcedChunks)
public record TicketType(long timeout, int flags, boolean forceNaturalSpawning)   // net.minecraft.server.level.TicketType
  public TicketType(long timeout, int flags)
  static final long NO_TIMEOUT; static final int FLAG_PERSIST, FLAG_LOADING, FLAG_SIMULATION, FLAG_KEEP_DIMENSION_ACTIVE, FLAG_CAN_EXPIRE_IF_UNLOADED;
  static final TicketType PLAYER_SPAWN, SPAWN_SEARCH, DRAGON, PLAYER_LOADING, PLAYER_SIMULATION, FORCED, PORTAL, ENDER_PEARL, UNKNOWN;   // no PLAYER/START/POST_TELEPORT
  boolean persist(); doesLoad(); doesSimulate(); shouldKeepDimensionActive(); canExpireIfUnloaded(); hasTimeout();
// custom: public static final TicketType MY_TICKET = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_PERSIST);
// ServerChunkCache (net.minecraft.server.level): addTicketWithRadius(TicketType, ChunkPos, int radius) / removeTicketWithRadius(TicketType, ChunkPos, int) / addTicket(TicketType, ChunkPos, int level) / removeTicket(TicketType, ChunkPos, int level)  U(21.5+ shape; verify)
// new ChunkPos(BlockPos) ; LevelChunk#getInhabitedTime() long  U
```

## L2c-2. Explosion objects, particle options, raycast, POI
```java
// ServerExplosion (net.minecraft.world.level.ServerExplosion implements Explosion)  V(JD ServerExplosion.html)
public ServerExplosion(ServerLevel level, @Nullable Entity source, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator damageCalculator, Vec3 center, float radius, boolean fire, Explosion.BlockInteraction blockInteraction)
public int explode();  public Map<Player,Vec3> getHitPlayers();  public ServerLevel level();  public Vec3 center();  public float radius();  public DamageSource getDamageSource();
public @Nullable LivingEntity getIndirectSourceEntity();  public @Nullable Entity getDirectSourceEntity();  public Explosion.BlockInteraction getBlockInteraction();  public boolean canTriggerBlocks();  public boolean shouldAffectBlocklikeEntities();  public boolean isSmall();
public static float getSeenPercent(Vec3 center, Entity entity)      // NOTE: no public getToBlow() in 26.1 — use ExplosionEvent.Detonate#getAffectedBlocks()

// Particle options (net.minecraft.core.particles)  V(JD DustParticleOptions.html, ParticleTypes.html)
public DustParticleOptions(int color, float scale)   extends ScalableParticleOptionsBase (getScale()); static final DustParticleOptions REDSTONE; static final int REDSTONE_PARTICLE_COLOR; Vector3f getColor(); ParticleType<DustParticleOptions> getType()
ParticleTypes: SimpleParticleType FLAME (and all plain ones: SMOKE, LARGE_SMOKE, WHITE_SMOKE, PORTAL, REVERSE_PORTAL, END_ROD, ENCHANT, HAPPY_VILLAGER, CLOUD, EXPLOSION, EXPLOSION_EMITTER, SOUL_FIRE_FLAME, SOUL, ASH, WHITE_ASH, CAMPFIRE_COSY_SMOKE, CAMPFIRE_SIGNAL_SMOKE, WITCH, CRIT, ENCHANTED_HIT, ELECTRIC_SPARK, GLOW, SNOWFLAKE, SCULK_SOUL, BUBBLE, DRIPPING_WATER, FALLING_WATER, RAIN, SPLASH, POOF, LAVA, FIREFLY, SMALL_FLAME, COPPER_FIRE_FLAME, DUST_PLUME, ...)
  ParticleType<DustParticleOptions> DUST;  ParticleType<BlockParticleOption> BLOCK (also FALLING_DUST, BLOCK_MARKER, BLOCK_CRUMBLE, DUST_PILLAR);  ParticleType<ItemParticleOption> ITEM;  ParticleType<ColorParticleOption> ENTITY_EFFECT (also TINTED_LEAVES);  ParticleType<DustColorTransitionOptions> DUST_COLOR_TRANSITION;  ParticleType<TrailParticleOption> TRAIL
// U(unchanged): ColorParticleOption.create(ParticleType<ColorParticleOption>, int argb) / create(type, float r, float g, float b); new BlockParticleOption(ParticleType<BlockParticleOption>, BlockState); new ItemParticleOption(ParticleType<ItemParticleOption>, ItemStack)
// Custom ParticleType<T extends ParticleOptions> U: abstract class; ctor ParticleType(boolean overrideLimiter); abstract MapCodec<T> codec(); abstract StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec(); SimpleParticleType(boolean overrideLimiter) is both type+options. Registry: Registries.PARTICLE_TYPE.

// Raycast  V(JD ClipContext.html)
public ClipContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity)
public ClipContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, CollisionContext collisionContext)
// enums U: ClipContext.Block { COLLIDER, OUTLINE, VISUAL, FALLDAMAGE_RESETTING }  ClipContext.Fluid { NONE, SOURCE_ONLY, ANY, WATER }
// BlockGetter#clip(ClipContext) -> BlockHitResult (default method on net.minecraft.world.level.BlockGetter; Level inherits)  U
// BlockHitResult (net.minecraft.world.phys): BlockPos getBlockPos(); Direction getDirection(); Vec3 getLocation(); HitResult.Type getType() {MISS, BLOCK, ENTITY}; boolean isInside(); withPosition(BlockPos); withDirection(Direction)  U
// Player#pick(double hitDistance, float partialTicks, boolean hitFluids) -> HitResult ; ProjectileUtil.getHitResultOnViewVector(Entity, Predicate<Entity>, double range) -> HitResult  U

// POI (net.minecraft.world.entity.ai.village.poi)  V(JD PoiType.html)
public record PoiType(Set<BlockState> matchingStates, int maxTickets, int validRange) { boolean is(BlockState); static final Predicate<Holder<PoiType>> NONE; }
// PoiTypes: static final ResourceKey<PoiType> HOME, MEETING, ARMORER, BUTCHER, CARTOGRAPHER, CLERIC, FARMER, FISHERMAN, FLETCHER, LEATHERWORKER, LIBRARIAN, MASON, SHEPHERD, TOOLSMITH, WEAPONSMITH, BEEHIVE, BEE_NEST, NETHER_PORTAL, LODESTONE, LIGHTNING_ROD  U(keys; 21.x names)
// ServerLevel#getPoiManager() -> PoiManager  V ; PoiManager: Optional<BlockPos> find(Predicate<Holder<PoiType>>, Predicate<BlockPos>, BlockPos, int distance, PoiManager.Occupancy); Optional<BlockPos> findClosest(Predicate<Holder<PoiType>>, BlockPos, int, PoiManager.Occupancy); Stream<PoiRecord> getInRange(Predicate<Holder<PoiType>>, BlockPos, int, PoiManager.Occupancy); void add(BlockPos, Holder<PoiType>); void remove(BlockPos); boolean exists(BlockPos, Predicate<Holder<PoiType>>); Optional<Holder<PoiType>> getType(BlockPos); Occupancy { HAS_SPACE, IS_OCCUPIED, ANY }  U(unchanged)
```

## L2c-3. Villagers & trades (26.1 = datapack-driven)
```java
// package net.minecraft.world.entity.npc.villager  (moved from net.minecraft.world.entity.npc)  V(JD VillagerProfession.html, VillagerData.html; AbstractVillager linked from event pkg)
public record VillagerProfession(Component name, Predicate<Holder<PoiType>> heldJobSite, Predicate<Holder<PoiType>> acquirableJobSite,
        ImmutableSet<Item> requestedItems, ImmutableSet<Block> secondaryPoi, @Nullable SoundEvent workSound, Int2ObjectMap<ResourceKey<TradeSet>> tradeSetsByLevel)
  public @Nullable ResourceKey<TradeSet> getTrades(int level);  static final Predicate<Holder<PoiType>> ALL_ACQUIRABLE_JOBS;
  static final ResourceKey<VillagerProfession> NONE, ARMORER, BUTCHER, CARTOGRAPHER, CLERIC, FARMER, FISHERMAN, FLETCHER, LEATHERWORKER, LIBRARIAN, MASON, NITWIT, SHEPHERD, TOOLSMITH, WEAPONSMITH;
  // Registered in BUILT-IN registry BuiltInRegistries.VILLAGER_PROFESSION / Registries.VILLAGER_PROFESSION (V primer 26.1 "Datapack Villager Trades": Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, id, new VillagerProfession(...)))
public record VillagerData(Holder<VillagerType> type, Holder<VillagerProfession> profession, int level)
  VillagerData withType(Holder<VillagerType>) / withType(HolderGetter.Provider registries, ResourceKey<VillagerType>); withProfession(Holder<VillagerProfession>) / withProfession(HolderGetter.Provider, ResourceKey<VillagerProfession>); withLevel(int);
  static int MIN_VILLAGER_LEVEL, MAX_VILLAGER_LEVEL; static boolean canLevelUp(int); static int getMinXpPerLevel(int)/getMaxXpPerLevel(int); CODEC, STREAM_CODEC
// Villager (net.minecraft.world.entity.npc.villager.Villager extends AbstractVillager): VillagerData getVillagerData(); void setVillagerData(VillagerData); int getVillagerXp(); void setVillagerXp(int); void restock(); MerchantOffers getOffers(); void overrideOffers(MerchantOffers) (via Merchant)  U(names unchanged; only package verified)
// Merchant (net.minecraft.world.item.trading.Merchant) U: getOffers(); overrideOffers(MerchantOffers); notifyTrade(MerchantOffer); setTradingPlayer(@Nullable Player); getTradingPlayer(); openTradingScreen(Player, Component, int level); isClientSide(); getVillagerXp(); overrideXp(int); showProgressBar()
// MerchantOffer  V(JD MerchantOffer.html)  package net.minecraft.world.item.trading — result is still ItemStack (NOT ItemStackTemplate)
public MerchantOffer(ItemCost buy, ItemStack result, int maxUses, int xp, float priceMultiplier)
public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int maxUses, int xp, float priceMultiplier)
public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int uses, int maxUses, int xp, float priceMultiplier)
public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int uses, int maxUses, int xp, float priceMultiplier, int demand)
  ItemStack getBaseCostA()/getCostA()/getCostB()/getResult()/assemble(); ItemCost getItemCostA(); Optional<ItemCost> getItemCostB(); int getUses()/getMaxUses()/getXp()/getDemand()/getSpecialPriceDiff(); float getPriceMultiplier(); boolean isOutOfStock()/needsRestock()/shouldRewardExp(); void setToOutOfStock()/resetUses()/increaseUses()/updateDemand()/setSpecialPriceDiff(int)/addToSpecialPriceDiff(int)/resetSpecialPriceDiff(); MerchantOffer copy(); CODEC, STREAM_CODEC
// ItemCost (record) U: ItemCost(Holder<Item> item, int count, DataComponentPredicate components, ItemStack itemStack); ItemCost(ItemLike item) ; ItemCost(ItemLike item, int count) ; ItemCost(Holder<Item>, int)
// MerchantOffers extends ArrayList<MerchantOffer> U: CODEC, STREAM_CODEC; copy(); @Nullable MerchantOffer getRecipeFor(ItemStack a, ItemStack b, int index)
// TradeSet  V(JD TradeSet.html): public TradeSet(HolderSet<VillagerTrade> trades, NumberProvider amount, boolean allowDuplicates, Optional<Identifier> randomSequence); HolderSet<VillagerTrade> getTrades(); int calculateNumberOfTrades(LootContext); CODEC
// Datapack registries: Registries.VILLAGER_TRADE (data/<ns>/villager_trade/*.json) and Registries.TRADE_SET (data/<ns>/trade_set/*.json); vanilla sets reference tags (#minecraft:farmer/level_1) so add trades by tagging: data/minecraft/tags/villager_trade/farmer/level_1.json  V(primer 26.1)
// VillagerTrade#getOffer(LootContext) -> MerchantOffer (LootContextParamSets.VILLAGER_TRADE: THIS_ENTITY + ORIGIN)  V(primer text); VillagerTrades.ItemListing / VillagerTrades.TRADES map and NeoForge VillagerTradesEvent / WandererTradesEvent are GONE (see L2a-1).
// TradeWithVillagerEvent (net.neoforged.neoforge.event.entity.player) still exists: getMerchantOffer(), getAbstractVillager()  V(package listing) / U(getters)
// EntityType.VILLAGER (EntityType<Villager>) U. VillagerType now Holder<VillagerType> (VillagerType.PLAINS etc are ResourceKey<VillagerType>)  U
```

## L2c-4. Game rules, weather, time (26.1: all SavedData on the SERVER, not per level)
```java
// net.minecraft.world.level.gamerules.GameRules / GameRule<T>  V(JD GameRules.html, GameRule.html)
public <T> T get(GameRule<T> gameRule);    public <T> void set(GameRule<T> gameRule, T value, MinecraftServer server);
public static GameRule<Boolean> registerBoolean(String id, GameRuleCategory category, boolean defaultValue);   registerInteger(String, GameRuleCategory, int defaultValue, int min [, int max [, FeatureFlagSet]])
public static <T> GameRule<T> register(String, GameRuleCategory, GameRuleType, ArgumentType<T>, Codec<T>, T, FeatureFlagSet, GameRules.VisitorCaller<T>, ToIntFunction<T>)
// constants (renamed!): ADVANCE_TIME (was DO_DAYLIGHT_CYCLE), ADVANCE_WEATHER (was DO_WEATHER_CYCLE), SPAWN_MOBS (was DO_MOB_SPAWNING), MOB_GRIEFING, KEEP_INVENTORY, RANDOM_TICK_SPEED, FIRE_SPREAD_RADIUS_AROUND_PLAYER (int; no DO_FIRE_TICK), NATURAL_HEALTH_REGENERATION, IMMEDIATE_RESPAWN, PVP, RAIDS, SPAWN_MONSTERS, SPAWN_PHANTOMS, SPAWN_WANDERING_TRADERS, TNT_EXPLODES, PLAYERS_SLEEPING_PERCENTAGE, RESPAWN_RADIUS, BLOCK_DROPS, MOB_DROPS, ENTITY_DROPS, ...
// obtain: level.getGameRules() (ServerLevel V) or server.getGameRules() (MinecraftServer V); read: boolean b = rules.get(GameRules.ADVANCE_TIME); write: rules.set(GameRules.ADVANCE_TIME, false, server);
// GameRule is a registry object (Registries.GAME_RULE) built by GameRules.bootstrap(Registry) V(index) — custom rule: register a GameRule<T> into that registry via RegisterEvent/DeferredRegister  U(NeoForge helper name unknown)
// NeoForge: net.neoforged.neoforge.event.level.GameRuleChangedEvent  V(NF source): MinecraftServer getServer(); GameRules getGameRules(); GameRule<?> getGameRule(); Object getNewValue(); <T> void runIfMatching(GameRule<T>, Consumer<T>)

// WEATHER — global per server (net.minecraft.world.level.saveddata.WeatherData extends SavedData)  V(JD WeatherData.html, MinecraftServer.html, primer)
public void MinecraftServer#setWeatherParameters(int clearTime, int rainTime, boolean raining, boolean thundering)   // moved from ServerLevel
public WeatherData MinecraftServer#getWeatherData()  /  public WeatherData ServerLevel#getWeatherData()
WeatherData: int getClearWeatherTime()/getRainTime()/getThunderTime(); boolean isRaining()/isThundering(); void setClearWeatherTime(int)/setRainTime(int)/setThunderTime(int)/setRaining(boolean)/setThundering(boolean); WeatherData(int clearWeatherTime,int rainTime,int thunderTime,boolean raining,boolean thundering); TYPE, CODEC
Level (read-only, V anchors): boolean isRaining(); boolean isThundering(); float getRainLevel(float partialTick); float getThunderLevel(float partialTick); boolean isRainingAt(BlockPos)   // ServerLevel#resetWeatherCycle() V(index)
// Per-dimension weather is NOT vanilla in 26.1; dimension rain visuals depend on DimensionType/biome precipitation. Mystcraft-style per-age weather must be modded (ServerLevel is patched/overridden or use env attributes).

// TIME — per WorldClock (datapack registry Registries.WORLD_CLOCK), NOT per level. setDayTime/getDayTime are REMOVED  V(primer 26.1 "World Clocks", JD ServerClockManager.html, WorldClocks.html)
interface net.minecraft.world.clock.WorldClocks { ResourceKey<WorldClock> OVERWORLD, THE_END; }      // custom clock = empty JSON data/<ns>/world_clock/<name>.json ; DimensionType now carries a default Holder<WorldClock>
Level: ClockManager clockManager(); long getDefaultClockTime(); long getOverworldClockTime()  (replaces getDayTime)     ServerLevel: ServerClockManager clockManager()   MinecraftServer: ServerClockManager clockManager()
public class ServerClockManager extends SavedData implements ClockManager {   // net.minecraft.world.clock
  public long getTotalTicks(Holder<WorldClock> definition); public float getPartialTick(Holder<WorldClock>); public float getRate(Holder<WorldClock>);
  public void setTotalTicks(Holder<WorldClock> clock, long totalTicks); public void addTicks(Holder<WorldClock> clock, int ticks); public void setPaused(Holder<WorldClock> clock, boolean paused);
  public void setRate(Holder<WorldClock> clock, float rate);   // NeoForge addition (also `/neoforge day`)
  public boolean moveToTimeMarker(Holder<WorldClock> clock, ResourceKey<ClockTimeMarker> timeMarkerId); public boolean isAtTimeMarker(Holder<WorldClock>, ResourceKey<ClockTimeMarker>); Stream<ResourceKey<ClockTimeMarker>> commandTimeMarkersForClock(Holder<WorldClock>); ClientboundSetTimePacket createFullSyncPacket(); }
// VERIFIED way to "set time of day" for a level:
//   Holder<WorldClock> clock = level.dimensionType().defaultClock()  /* U: accessor name */  or level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
//   level.clockManager().setTotalTicks(clock, (level.clockManager().getTotalTicks(clock) / 24000L) * 24000L + 1000L);   // "/time set day" equivalent
//   or level.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.<DAY|NOON|NIGHT|MIDNIGHT>) — marker constant names U (net.minecraft.world.clock.ClockTimeMarkers)
// A separate Mystcraft "age" dimension gets independent time only if its DimensionType JSON points at its own world_clock.
```

## L2a-2. Mod-bus events (implement `net.neoforged.fml.event.IModBusEvent`; auto-routed by @EventBusSubscriber)
```java
// net.neoforged.fml.event.lifecycle.{FMLCommonSetupEvent, FMLClientSetupEvent, FMLDedicatedServerSetupEvent} { void enqueueWork(Runnable); ModContainer getContainer(); }  U(unchanged)
// net.neoforged.neoforge.registries.RegisterEvent  V(JD registries/RegisterEvent.html)
public <T> void register(ResourceKey<? extends Registry<T>> registryKey, Identifier name, Supplier<T> valueSupplier)
public <T> void register(ResourceKey<? extends Registry<T>> registryKey, Consumer<RegisterEvent.RegisterHelper<T>> consumer)   // helper.register(Identifier, T)  U
ResourceKey<? extends Registry<?>> getRegistryKey(); Registry<?> getRegistry(); <T> @Nullable Registry<T> getRegistry(ResourceKey<? extends Registry<T>>)
// net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry  V(JD):
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec)
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec, @Nullable Codec<T> networkCodec)
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec, @Nullable Codec<T> networkCodec, Consumer<RegistryBuilder<T>> consumer)   // JSON path data/<pack_ns>/<registry_ns>/<registry_path>/
// NewRegistryEvent { <T> Registry<T> create(RegistryBuilder<T>); void register(Registry<?>) }  U
// net.neoforged.neoforge.event.{BuildCreativeModeTabContentsEvent, ModifyDefaultComponentsEvent, AddPackFindersEvent, RegisterGameRuleCategoryEvent}  V(names, event/package-summary.html)
//   BuildCreativeModeTabContentsEvent: accept(ItemLike)/accept(ItemStack)/accept(ItemStack, TabVisibility), insertAfter/insertBefore/insertFirst(...), getTabKey(), getParameters(), getTab()  U(page fetched by sibling agent; 26.1 may add ItemStackTemplate overloads)
// net.neoforged.neoforge.event.entity.{EntityAttributeCreationEvent (put(EntityType<? extends LivingEntity>, AttributeSupplier)), EntityAttributeModificationEvent (add(EntityType, Holder<Attribute>[, double])), RegisterSpawnPlacementsEvent (register(EntityType<T>, SpawnPlacementType, Heightmap.Types, SpawnPlacements.SpawnPredicate<T>, Operation))}  U
// net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent; net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent { PayloadRegistrar registrar(String version) }; RegisterConfigurationTasksEvent  U
// net.neoforged.fml.event.config.ModConfigEvent.Loading / Reloading / Unloading { ModConfig getConfig(); }  U
// net.neoforged.neoforge.data.event.GatherDataEvent.Client / Server { DataGenerator getGenerator(); PackOutput / createProvider(...); getLookupProvider(); getExistingFileHelper()? }  U

// --- CLIENT mod-bus (package net.neoforged.neoforge.client.event; all names V(JD client/event/package-summary.html))
RegisterGuiLayersEvent  V(JD): void registerAbove(Identifier other, Identifier id, GuiLayer layer); registerBelow(Identifier other, Identifier id, GuiLayer); registerAboveAll(Identifier id, GuiLayer); registerBelowAll(Identifier id, GuiLayer); replaceLayer(Identifier, GuiLayer); wrapLayer(Identifier, UnaryOperator<GuiLayer>)
  @FunctionalInterface net.neoforged.neoforge.client.gui.GuiLayer { void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker); }   V(JD client/gui/GuiLayer.html)  — note GuiGraphicsExtractor (net.minecraft.client.gui), NOT GuiGraphics
  vanilla ids: net.neoforged.neoforge.client.gui.VanillaGuiLayers.*  V(referenced) ; RenderGuiLayerEvent.Pre/Post (game bus) { Identifier getName(); GuiLayer getLayer(); GuiGraphicsExtractor getGuiGraphics(); DeltaTracker getPartialTick() }  U(getters)
RegisterParticleProvidersEvent  V(JD): <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type, ParticleResources.SpriteParticleRegistration<T> registration); <T extends ParticleOptions> void registerSpecial(ParticleType<T> type, ParticleProvider<T> provider)
  // ParticleResources.SpriteParticleRegistration<T> (net.minecraft.client.particle; was ParticleEngine.SpriteParticleRegistration): ParticleProvider<T> create(SpriteSet sprites)  U ; ParticleProvider<T>: @Nullable Particle createParticle(T, ClientLevel, double x,y,z, double dx,dy,dz, RandomSource)  U(26.1 may add RandomSource param — verify)
  // TextureSheetParticle/ParticleRenderType: 26.1 renamed SingleQuadParticle.Layer (OPAQUE/TRANSLUCENT/TERRAIN?) — U; check net.minecraft.client.particle package.
AddClientReloadListenersEvent (was RegisterClientReloadListenersEvent)  V(JD): extends SortedReloadListenerEvent implements IModBusEvent; inherited: void addListener(Identifier, PreparableReloadListener); void addDependency(Identifier, Identifier); getLastVanillaListener(); getNameLookup(); getGraph(); getRegistry()
ModelEvent.RegisterStandalone (was RegisterAdditional), ModelEvent.ModifyBakingResult, ModelEvent.BakingCompleted, ModelEvent.RegisterLoaders  V(names)
RegisterRenderPipelinesEvent (replaces RegisterShadersEvent), RegisterRenderBuffersEvent, RegisterMenuScreensEvent, RegisterKeyMappingsEvent, RegisterClientCommandsEvent, RegisterColorHandlersEvent.{BlockTintSources, ItemTintSources, ColorResolvers}, RegisterDimensionTransitionScreenEvent, RegisterCustomEnvironmentEffectRendererEvent, RegisterItemModelsEvent, RegisterBlockStateModels, RegisterSpecialModelRendererEvent, RegisterSpriteSourcesEvent, RegisterTextureAtlasesEvent, InitializeClientRegistriesEvent  V(names)
EntityRenderersEvent.RegisterRenderers / RegisterLayerDefinitions / AddLayers / CreateSkullModels  V(names)
// --- CLIENT game-bus  V(names): ClientTickEvent.Pre/Post; ClientPlayerNetworkEvent.LoggingIn/LoggingOut/Clone; ClientChatReceivedEvent.Player/System; ScreenEvent.Init.Pre/Post, ScreenEvent.Render.*, ScreenEvent.Opening/Closing; RenderGuiEvent.Pre/Post; RenderGuiLayerEvent.Pre/Post; RenderTooltipEvent.{GatherComponents, Pre, Texture}; ViewportEvent.{ComputeFogColor, RenderFog, ComputeFov, ComputeCameraAngles} (still exist); RenderLevelStageEvent now has SUBCLASSES instead of Stage enum: RenderLevelStageEvent.{AfterSky, AfterOpaqueBlocks, AfterOpaqueFeatures, AfterTranslucentBlocks, AfterTranslucentFeatures, AfterTranslucentParticles, AfterWeather, AfterLevel}; new: ExtractLevelRenderStateEvent, SubmitCustomGeometryEvent, RenderFrameEvent.Pre/Post, FrameGraphSetupEvent, ClientPauseChangeEvent, InputEvent.{Key, MouseButton.Pre/Post, MouseScrollingEvent, InteractionKeyMappingTriggered}, RenderLivingEvent.Pre/Post, RenderPlayerEvent.Pre/Post, RenderNameTagEvent.{CanRender, DoRender}, SelectMusicEvent, ClientResourceLoadFinishedEvent
// ItemTooltipEvent (net.neoforged.neoforge.event.entity.player, game bus) V(name): List<Component> getToolTip(); ItemStack getItemStack(); Item.TooltipContext getContext(); TooltipFlag getFlags(); @Nullable Player getEntity()  U(getters)
```

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
