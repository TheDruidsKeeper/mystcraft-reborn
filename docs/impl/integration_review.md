# Integration review (wave 1: packages A, B, C1, C2, E + core)

Scope: every file under `src/main/java/com/techbucketdivision/mystcraft/{age,api,block,blockentity,command,config,
dimension,entity,event,instability,item,linking,menu,network,registry,symbol,util,villager,world}` plus `Mystcraft.java`.
`client/**` and `src/main/resources` were not touched. Method: full read of every cross-package call site against the
callee's declaration, abstract-method coverage of every vanilla subclass against `docs/API_CHEATSHEET.md` /
`docs/TOOLCHAIN.md`, and three scripted passes over the concatenated sources (unresolved simple type names, every
`RepoClass.staticMember(` reference, every `repoTypedVariable.method(` call on repo-only type hierarchies). No compiler
was available; this is a static review.

## Mismatches found and fixed

| # | Caller | Callee | Problem | Fix |
|---|---|---|---|---|
| 1 | `block/DecayBlock#entityInside`, `#stepOn` | `instability/decay/DecayHandler#onEntityContact(Level, BlockPos, BlockState, Entity)` | C2 assumed the 3-arg form `(ServerLevel, BlockPos, Entity)` | caller now passes `(serverLevel, pos, state, entity)` |
| 2 | `entity/LinkbookEntity#linkEntity` | `item/LinkingItem#activate(ItemStack, ServerLevel, Entity)` | E called it as a **static** method (`LinkingItem.activate(...)`); it is an instance method (overridden by `DescriptiveBookItem`) | pattern-bind the item (`instanceof LinkingItem linkingItem`) and call `linkingItem.activate(...)` |
| 3 | `menu/ArchivistShopMenu#broadcastChanges` | `villager/ArchivistShop#toTag(HolderLookup.Provider)` | C2 called the no-arg `toTag()`; the item codec needs registry ops | pass `player.level().registryAccess()` |
| 4 | `menu/ArchivistShopMenu#processMessage` | `villager/ArchivistShop#save(Villager)` | purchases mutated the attachment object without marking it dirty (behavioural, not compile) | call `shop.save(villager)` after a successful purchase |
| 5 | `world/structure/MystcraftLibrary#placeLectern` | `blockentity/BookDisplayBlockEntity#setBook(ItemStack)` | marked `// UNVERIFIED` by B; the method exists with that exact signature | marker removed |
| 6 | `dimension/AgeEnvironment#argb` | `ARGB.color(int alpha, int rgb)` | marked UNVERIFIED; the cheat sheet (I3) lists it verbatim | marker removed |
| 7 | `entity/explosion/AdvancedExplosion#explode` | `Level#playSound(..., SoundEvents.GENERIC_EXPLODE, ...)` | marked UNVERIFIED because the constant's type was unknown; both the `SoundEvent` and the `Holder<SoundEvent>` overloads of `playSound` are verified (E1), so it compiles either way | marker replaced by an explanatory comment |
| 8 | `entity/MystFallingBlockEntity#place` | `TagValueInput.create(ProblemReporter, HolderLookup.Provider, CompoundTag)` | marked UNVERIFIED; `docs/TOOLCHAIN.md` §4.9 lists that exact bridge | marker replaced by a reference to the toolchain doc |

Everything else in the "known suspects" list checked out as consistent:

* `command/MystcraftCommands` ↔ `linking/LinkPermissions.get(server).permitEntry/restrictEntry/permitDepart/restrictDepart(String, @Nullable ResourceKey<Level>)` — exact match.
* `DecayHandler#onPlace` returns `boolean`; `DecayBlock#onPlace` ignores the result (fine) and removes decay outside Ages itself before delegating.
* `LinkbookEntity#getBook/setBook`, `spawnFor`, `spawnAt` — used correctly by `BookMenu.EntitySource`, `LinkingItem#onLink`, `CommonEvents#onEntityJoinLevel`.
* `ArchivistShop.CODEC`, `getPageStack`, `getBoosterCount`, `purchasePage`, `purchaseBooster` — match `ArchivistShopMenu`.
* `AgeSpawn.findSpawn(ServerLevel, AgeController)` / `placePlatform(LevelAccessor, BlockPos)` — match `CommonEvents` / `AgeChunkGenerator`.
* `BiomeSymbols.registerAll(HolderLookup.Provider)`, `FluidSymbols.registerAll()` — match `CommonEvents#onServerAboutToStart`.
* `Grammar.expandAge(List<Identifier>, RandomSource)`, `SymbolRemapper.hasRemapping/remap(Identifier)` — match `DescriptiveBookItem` / `PageItem`.
* All `PageItem`/`DescriptiveBookItem`/`LinkingItem`/`FolderItem`/`PortfolioItem`/`LinkingBookItem`/`BoosterItem`/`UnlinkedBookItem` static factories used by A (`CreativeCollections`), B (`MystcraftLibrary`), C2 (`BookBinderBlockEntity`, `InkMixerBlockEntity`, `WritingDeskBlockEntity`, `ArchivistShopMenu`) and E (`ArchivistShop`, `MystcraftCommands`) exist with the used parameter lists.
* `WritingDeskBlock.FACING/TOP/FOOT` (C2 chose `FACING` over the contract's `HORIZONTAL_FACING`; C1 already uses `FACING`).
* `BookMenu.openForHeldBook/openForBlock/openForEntity`, `FolderMenu.openForHeldItem`, `ArchivistShopMenu.open` — match all callers.
* `ModBusEvents` wires both `BlockEntityCapabilities.register` and `ItemCapabilities.register`.
* `AgeController` accessors (`sky()`, `celestials()`, `effects()`, `weather()`, `isPvPEnabled()`, `setTerrainBlocks`, `currentSymbolSeed`, `celestialAngle`, `timeToSunrise`, `dynamicColor`, `symbolInstability`, `averageGroundLevel`, `biomeAt`, `terrainBlock/seaBlock`, `MIN_Y/HEIGHT`) and `AgeControllers.server(Level|MinecraftServer,UUID)` / `clearServer()` — all present.
* `Mystcraft.java` bootstrap chain (`LinkListeners.registerDefaults`, `InkEffects.registerDefaults`, `InstabilityManager.registerDefaults`, `SymbolRegistry.bootstrapBuiltins` → `BuiltinSymbols/ModifierSymbols/BlockSymbols.registerAll`, `Grammar.bootstrap`, `SymbolRegistry.freeze`) — all present.
* Payload records (`LinkParticlesPayload(x,y,z)`, `LightningPayload(x,y,z,color)`, `ExplosionEffectsPayload(x,y,z,size,affected)`, `ServerConfigPayload(boolean)`, `MenuMessagePayload.of(...)`) match every constructor call.
* `ModDataComponents`, `ModItems`, `ModBlocks`, `ModBlockEntities`, `ModMenus`, `ModEntities`, `ModSounds`, `ModCriteria`, `ModAttachments`, `ModVillagers`, `ModFluids.isInk` — every referenced holder exists with the referenced generic type.
* Config: every `MystcraftConfig.*` / `BalanceConfig.*` reference resolves.
* All 178 distinct cross-package imports resolve to an existing file; no duplicate simple class names exist across packages.

## Java-level checks performed (no problems found)

* Abstract-method coverage: `AgeChunkGenerator` (codec, fillFromNoise, buildSurface, applyCarvers, spawnOriginalMobs,
  getSeaLevel, getMinY, getGenDepth, getBaseHeight, getBaseColumn, addDebugScreenInfo), `AgeBiomeSource` (codec,
  collectPossibleBiomes, getNoiseBiome), all 7 `AbstractMystcraftMenu` subclasses (quickMoveStack, stillValid,
  processMessage), all 4 entities (defineSynchedData, read/addAdditionalSaveData), `LinkingBookRecipe`
  (matches, assemble(CraftingInput) — 26.1 dropped the `HolderLookup.Provider` parameter, TOOLCHAIN §4.x), every
  `ResourceHandler` implementation (`InkVialItem.Handler`, `WindowedItemHandler`).
* Every `@Override` in block/item/entity/menu/blockentity targets a method listed in the cheat sheet with the same
  parameter list (`useItemOn`, `useWithoutItem`, `neighborChanged(…, @Nullable Orientation, boolean)`,
  `entityInside(…, InsideBlockEffectApplier, boolean)`, `getAnalogOutputSignal(…, Direction)`,
  `affectNeighborsAfterRemoval` not used — BE cleanup is in `preRemoveSideEffects`, `inventoryTick(ItemStack,
  ServerLevel, Entity, EquipmentSlot)`, `appendHoverText(…, TooltipDisplay, Consumer, TooltipFlag)`, `use → InteractionResult`,
  `interact(Player, InteractionHand, Vec3)`, `hurtServer`, `canBeCollidedWith(@Nullable Entity)`).
* No `ResourceLocation`, `isClientSide` field, `getDayTime`, `ItemInteractionResult`, `serverLevel()`, `getSelected()`,
  `moveTo`, `getNormal`, `new ChunkPos(BlockPos)`, `toLong`, `getStatus`, `displayClientMessage`, `critereon`,
  `MOVEMENT_SLOWDOWN`, `hasPermission(int)`, `DirectionProperty`, `noCollission`, `onRemove`, `getTags`,
  `ItemStackHandler`, `putUUID`, `EntityDataSerializers.OPTIONAL_UUID`, `TicketType.<generic>` in any reviewed file.
* No `ItemStack` constructed in a static initialiser; every mutable Age write goes through `AgeData` setters;
  `LinkInfo.dimension()/targetUuid()/spawn()` are always treated as `Optional`.
* Static-import overlap: `ModifierSymbols` imports both `WordData.*` and `GrammarRules.*`; the only shared simple name
  (`TERRAIN`) is not used in that file, so no ambiguity error.
* `SavedDataType`/`getDataStorage()` usage in `LinkPermissions`, `ChunkProfiler`, `InstabilityDeckData` matches the
  core `AgeDataStorage` pattern (TOOLCHAIN §4.10).

## Remaining risks (ordered by likelihood of a compile error)

1. **`block/BookstandBlock#getInteractionShape(BlockState, BlockGetter, BlockPos)`** — protected override shape is
   unverified (only the public `BlockState` wrapper is). If it fails, delete the override (cosmetic only).
2. **`block/InkFluidBlock`: `LiquidBlock(FlowingFluid, Properties)`** — ctor arity unverified for 26.1 (TOOLCHAIN §4.12
   flags it). `ModFluids.BLACK_INK` is a `BaseFlowingFluid.Source` (a `FlowingFluid`), so the argument type is right if
   the 2-arg ctor survives.
3. **`world/feature/FloatingIslandsAlteration`: `BiomeResolver` lambda `(x, y, z, sampler)`** and
   `ChunkAccess#fillBiomesFromNoise(BiomeResolver, Climate.Sampler)` (the latter is verified; the functional shape is
   the 1.21 one).
4. **`entity/MeteorEntity`: `EntityDimensions.scalable(float, float)`** — present in 1.21.x; unverified for 26.1.
5. **`dimension/AgeTicker`: `Player#isSleepingLongEnough()`, `Player#stopSleepInBed(boolean, boolean)`,
   `PlayerList#getViewDistance()`**; `event/CommonEvents`: `PlayerList#getPlayer(UUID)`. All 1.21 APIs, not in the
   cheat sheet.
6. **`linking/LinkController`: `CollisionGetter#noCollision(Entity, AABB)`** — 1.21 API, not in the cheat sheet.
7. **`symbol/FluidSymbols`: `FluidState#createLegacyBlock()`, NeoForge `FluidType#isLighterThanAir()`**;
   **`symbol/BlockSymbols`: `Block#getName()`** — long-standing APIs, unverified for 26.1.
8. **`world/biome/BiomeHeights`, `SurfaceBlocks`: `BiomeTags.IS_DEEP_OCEAN/IS_OCEAN/IS_RIVER/IS_BEACH/IS_MOUNTAIN/
   IS_HILL/IS_END`**; **`world/feature/HugeTreesAlteration`: `LeavesBlock.PERSISTENT`** — constant names as in 1.21.
9. **`item/recipe/LinkingBookRecipe`**: `CustomRecipe` no-arg super constructor and `getSerializer()` return type
   `RecipeSerializer<? extends CustomRecipe>` — consistent with TOOLCHAIN's record-based `RecipeSerializer`, but the
   `CustomRecipe` base-class shape (26.1 added `CraftingBookInfo`) was not fetched.
10. **`command/MystcraftCommands`**: `Commands.hasPermission(PermissionCheck)` generic inference in `.requires(...)`
    (cheat sheet says this is the 26.1 pattern; the `PermissionProviderCheck<T>` bound must accept
    `CommandSourceStack`).
11. **`event/CommonEvents#onPlayerRespawnPosition`**: `TeleportTransition#missingRespawnBlock()`/`newLevel()`/`yRot()`/
    `xRot()` record accessors (TOOLCHAIN lists the record components; accessor names follow from them) and
    `ServerPlayer.RespawnConfig.getDimensionOrDefault` (cheat sheet D3, verified).
12. **`villager/ModVillagers`** (core): `VillagerProfession` 7-arg record ctor + `Int2ObjectMap.ofEntries` — matches
    the cheat sheet L2c-3 record but depends on the datapack `trade_set` files existing (package F).
13. **NeoForge transfer generics**: `ResourceHandlerSlot(ResourceHandler<ItemResource>, IndexModifier<ItemResource>, …)`
    receives `handler::set` / `desk.main::set` (`StacksResourceHandler#set(int, T, int)`) — verified shape, but if
    `IndexModifier`'s parameter order differs the six menu classes fail together.
14. **Menus**: `AbstractContainerMenu#addStandardInventorySlots(Inventory, int, int)` is documented as verified;
    `ContainerInput` (renamed `ClickType`) is used in `BookMenu#clicked`.

## Functional gaps noticed (not compile errors, left as-is)

* `symbol/FluidSymbols` does not call `InstabilityBlocks.setFactors(fluidBlock, f1, f2)` as the contract asked; fluid
  blocks therefore carry no instability factor. (E documented the expectation; A never implemented it.)
* `command/MystcraftCommands` `/tpx`: the `subject` entity argument node is declared after the `target` player node, so
  `/tpx <entity> <player>` may be shadowed by Brigadier's first-match rule for player-like selectors.
* `docs/impl/blocks_menus.md` still documents the 3-arg `onEntityContact` and no-arg `toTag()`; the code is now the
  source of truth (see fixes 1 and 3).
