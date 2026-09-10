# Package C2 — blocks, block entities, menus, crystal portals

Root package `com.techbucketdivision.mystcraft` (abbreviated `…`). Everything here is server-authoritative; nothing
imports `net.minecraft.client.*`. **Wiring required elsewhere:** `blockentity.BlockEntityCapabilities.register`
must be added to the mod bus (`RegisterCapabilitiesEvent`, package E `event.ModBusEvents`), and the client package
must call `AbstractMystcraftMenu.setClientSender(ClientNetwork::send)` once.

## Cross-package assumptions (classes / methods this package calls)

| From | Assumed API |
|---|---|
| C1 `item.PageItem` | `static ItemStack createBlankPage()`, `createSymbolPage(AgeSymbol)`, `createLinkPanel(Set<LinkProperty>)`, `static boolean isLinkPanel(ItemStack)`, `static @Nullable AgeSymbol getSymbol(ItemStack)`; class `PageItem` used in `instanceof` |
| C1 `item.LinkingItem` | class used in `instanceof`; instance method `activate(ItemStack, ServerLevel, Entity)` (a static method works too since it is called through the instance) |
| C1 `item.DescriptiveBookItem` | class used in `instanceof`; `static ItemStack create(Player, List<ItemStack>, String title)` |
| C1 `item.FolderItem` | class used in `instanceof` (must implement `ItemBehaviours.OrderablePageProvider` + `PageAcceptor`) |
| C1 `linking.LinkListeners` | `static boolean isLinkPermitted(ServerLevel, Entity, LinkInfo)` |
| C1 `linking.InkEffects` | `static @Nullable Map<LinkProperty,Float> getItemEffects(ItemStack)`, `static ColorGradient getPropertiesGradient(Map<LinkProperty,Float>)`; dilution key = `LinkProperty.get("dilution")` |
| C1 `linking.StarFissureLinker` | `static void link(ServerLevel, Entity)` |
| E `instability.decay.DecayHandlers` | `static <handler> get(DecayType)` with `randomTick(ServerLevel, BlockPos, BlockState, RandomSource)`, `onPlace(ServerLevel, BlockPos, BlockState)`, `onEntityContact(ServerLevel, BlockPos, Entity)` |
| E `entity.LinkbookEntity` | `ItemStack getBook()`, `void setBook(ItemStack)` (entity is `discard()`ed when its book is taken through the GUI) |
| E `villager.ArchivistShop` | `CODEC`, `ItemStack getPageStack(int)`, `int getBoosterCount()`, `boolean purchasePage(ServerPlayer,int)`, `boolean purchaseBooster(ServerPlayer)`, `CompoundTag toTag()`; obtained via `villager.getData(ModAttachments.ARCHIVIST_SHOP.get())` |
| core | `AgeManager.isAge/get/markDead`, `AgeData.visited()/dead()/uuid()`, `ModDataComponents.LINK_INFO/AUTHORS`, `ModCriteria.WRITING_DESK_WRITE`, `MystcraftConfig.isLinkEffectEnabled` |

Link info is read/written directly through `ModDataComponents.LINK_INFO` (`blockentity.BookUtil`), so the exact
`LinkingItem` accessor names do not matter here.

## `linking.PortalUtils` (§7.7)

`static int isValidLinkPortalBlock(BlockState)`, `static BlockPos getReceptacleBase(BlockPos, Direction)`,
`static void validatePortal(Level, BlockPos)`, `static void firePortal(Level, BlockPos receptacle)`,
`static void shutdownPortal(Level, BlockPos receptacle)`,
`static @Nullable BookReceptacleBlockEntity getReceptacle(Level|BlockGetter, BlockPos)` (follows `SOURCE` links,
cycle-safe — use it client-side for the portal colour). Growth (`onPulse`/`expandPortal`/tension), pathing
(`pathTo`/`directPortal`/`repathNeighbors`) and `unpath` are private ports of the original.

## Blocks (`block/`, all ctor `(BlockBehaviour.Properties)`)

| Class | State | Notes |
|---|---|---|
| `FacingEntityBlock` (abstract) | `FACING` = `HORIZONTAL_FACING` | base for Ink Mixer / Book Binder / Link Modifier; right-click → `player.openMenu(be, buf.writeBlockPos)`; `static openMenu(Level, BlockPos, Player)` |
| `InkMixerBlock` | FACING | server ticker → `InkMixerBlockEntity.serverTick()` |
| `BookBinderBlock` | FACING | |
| `LinkModifierBlock` | FACING | comparator 15 when a book is held |
| `BookDisplayBlock` (abstract) | – | stand/lectern right-click logic (§3.4), opens `BookMenu.openForBlock`; comparator |
| `BookstandBlock` | `ROTATION` int 0–7 (45° steps) | shape 2..14 × 0..12; BE yaw = rotation·45 |
| `LecternBlock` | `FACING` | shape 0..16 × 0..7; accepts linking items, pages, filled maps (map packet streaming: `// TODO`) |
| `BookReceptacleBlock` | `ROTATION` EnumProperty\<Direction\> (all 6) | only placeable on a Crystal (behind the clicked face, never DOWN); 6-px slab shapes; drops itself when the crystal is gone; right-click inserts a `PortalActivator` stack / empty hand takes it |
| `CrystalBlock` | `SOURCE` (Direction, default DOWN), `ACTIVE` | neighbour change resets + `shutdownPortal` when the receptacle chain is broken; comparator 15 when active |
| `LinkPortalBlock` | `SOURCE`, `ACTIVE` | shape = 4..12 core extended toward adjacent crystal/portal; no collision; `neighborChanged`/`randomTick` → `validatePortal`; `entityInside` → receptacle book `onPortalCollision` (or becomes air); `static Direction.@Nullable Axis renderAxis(BlockGetter, BlockPos)` for models/BERs |
| `DecayBlock(DecayType, Properties)` | – | delegates to `DecayHandlers.get(type)`; outside an Age → air on place; `type()` |
| `WritingDeskBlock` | `FACING`, `TOP`, `FOOT` | foot at `head.relative(FACING)`; `TOP` shape 12 high, half width toward the desk; neighbour rules; creative harvest removes the tops; drops `writing_desk` / `writing_desk_backboard`; BE + ticker on the head only; `static headPos`, `getBlockEntity(Level, BlockPos)`, `placeBackboard(Level, BlockPos head)`, `hasBackboard`, `isHead/isTop/isFoot`; `RenderShape.INVISIBLE` (BER) |
| `StarFissureBlock` | – | shape 0..1.6 px, no collision, `RenderShape.INVISIBLE`; `entityInside` → `StarFissureLinker.link` |
| `InkFluidBlock` | – | `extends LiquidBlock(ModFluids.BLACK_INK.get(), props)` |

## Block entities (`blockentity/`, ctor `(BlockPos, BlockState)`)

* `MystBlockEntity` (abstract base): `markForUpdate()` (setChanged + `sendBlockUpdated`), full-state
  `getUpdateTag`/`getUpdatePacket`, `dropContents(handler)`.
* `FilteredItemHandler extends ItemStacksResourceHandler`: `(size, BiPredicate<Integer,ItemResource> filter,
  [IntUnaryOperator slotLimit,] Runnable onChange)`; `getStack(i)`, `setStack(i, stack)`, `isEmpty()`.
* `BookUtil`: `isLinkingItem`, `isDescriptiveBook`, `linkInfo(stack)`, `setLinkInfo`, `activate(stack, ServerLevel, Entity)`,
  `title(stack)`, `authors(stack)`, `isInkContainer`, `drop`, `give`.
* `InkMixerBlockEntity` (`MenuProvider`): `inventory` (3: `SLOT_INK_IN`, `SLOT_PAPER`, `SLOT_INK_OUT`), `serverTick()`,
  `addItems(stack, amount)`, `canBuildItem()`, `getCraftedItem()`, `buildItem(stack, player)`, `hasInk()/setHasInk`,
  `getNextSeed()/setNextSeed`, `getProbabilities()` (`Map<LinkProperty,Float>`), `setProbabilities`. Save keys
  `inventory`, `ink`, `seed`, `probabilities`.
* `BookBinderBlockEntity` (`MenuProvider`): `inventory` (1 cover), `getPendingTitle()/setPendingTitle`, `getPageList()`,
  `insertPage(stack, index)`, `insertFromFolder(folder, index)`, `removePage(index)`, `canBuildItem()`,
  `isMissingLinkPanel()`, `getCraftedItem()`, `buildItem`, `static isValidCover`. Save keys `items`, `pages`, `title`.
* `BookDisplayBlockEntity` (stand + lectern; base of receptacle & modifier): `inventory` (1), `accepts(stack)`,
  `getBook()/getDisplayItem()`, `setBook(stack)`, `getBookTitle()`, `link(entity)`, `getYaw()/setYaw(int)`
  (quantised 45°/90°), `getPitch()/setPitch`. Save keys `inventory`, `Yaw`, `Pitch`.
* `BookReceptacleBlockEntity`: accepts `PortalActivator` items; book change → `shutdownPortal` then `firePortal`;
  `getPortalColor()` (0xRRGGBB, white when empty).
* `LinkModifierBlockEntity` (`MenuProvider`): `setBookTitle(player, s)`, `getLinkDimension()`,
  `getLinkDimensionString()`, `isLinkDimensionDead()`, `recycleDimension()`, `getLinkFlag/setLinkFlag(LinkProperty, bool)`,
  `getLinkProperty/setLinkProperty(name, value)`, `getSeedString()`, `hasSeed()`.
* `WritingDeskBlockEntity` (`MenuProvider`): `main` (4: `SLOT_TARGET` limit 1, `SLOT_PAPER`, `SLOT_CONTAINER_IN`,
  `SLOT_CONTAINER_OUT`), `tabs` (25), `inkwell` (`FluidStacksResourceHandler(1, 1000)`, ink only), `INK_COST = 50`;
  `serverTick()` (container ↔ tank), `getInk()/setInk/getInkAmount()`, `getTarget()/getDisplayItem()`,
  `getPaperCount()`, `hasBackboard()`, `getTargetString()`, `setBookTitle(player, s)`, `getBookPageList(player)`,
  `link(entity)`, `writeSymbol(player, AgeSymbol)`, `getTabItem(tab)`, `removePageFromSurface(player, tab, index|page)`,
  `addPageToTab(player, tab, page)`, `placePageOnSurface(player, tab, page, index)`, `static isTargetItem`, `isNotebook`.
  Save keys `fluid`, `items`, `notebooks`.
* `StarFissureBlockEntity`: render anchor only.
* `BlockEntityCapabilities.register(RegisterCapabilitiesEvent)`: `Capabilities.Item.BLOCK` for every inventory BE
  (desk exposes `main`), `Capabilities.Fluid.BLOCK` for the desk inkwell.

## Menus (`menu/`)

All extend `AbstractMystcraftMenu` (implements `MenuMessageHandler`): `sendToServer(String[, CompoundTag])` (via the
static `ClientSender` hook), `sendToClient(ServerPlayer, String, CompoundTag)`, `static messageName(CompoundTag)`,
`addPlayerSlots(x, y, BooleanSupplier)`, `quickMove(...)` routing, `cursor()/setCursor()`. Message names go into the
`msg` key (`MenuMessagePayload.of(id, name, tag)`); the value keys are listed below. Screens apply a message locally
(`processMessage`) and then `sendToServer` (prediction). `menu.BookView` holds book-element state (current page,
page count, `isLinkPermitted`, `isTargetWorldVisited`, title, authors).

Slot helpers in `menu/slot`: `ToggleSlot`, `ToggleHandlerSlot` (activity supplier), `CraftOutputSlot` (output slot
calling the BE builder on take), `BannedSlot`, `WindowedItemHandler` (scrolling window over a handler).

| Menu | Open | Slots | Client→server messages (keys) | Server→client | Getters |
|---|---|---|---|---|---|
| `WritingDeskMenu` | block right-click (`fromNetwork`: BlockPos) | 0–3 tabs (37, 34+i·37), 4 target (241,80), 5 paper (241,28), 6 ink in (385,28), 7 out (385,80), 8–34 inv (241,104), 35–43 hotbar (241,162); `X_SHIFT=233`, `Y_SHIFT=20` | `SetTitle(Title)`, `Link`, `RemoveFromCollection(Page: ItemStack)`, `RemoveFromOrderedCollection(Index)`, `AddToCollection(Tab, Single)`, `AddToSurface(Tab, Index, Single)`, `WriteSymbol(Symbol: id string)`, `SetActiveNotebook(Tab)`, `SetFirstNotebook(Tab)`, `TakeFromSlider(Index)`, `InsertHeldAt(Index, Single)` | `SetFluid(Fluid: FluidStack)`, `LinkPermitted(Permitted, Visited)`, `SetTitle(Title)`, `SetCurrentPage(Index)` | `getDesk()`, `getBookView()`, `getTabSlot(i)`, `getActiveNotebook()`, `getActiveTabSlot()`, `getFirstTabSlot()`, `getMaxTabCount()`, `getTarget()`, `getBook()`, `getLinkInfo()`, `isLinkPermitted()`, `isTargetWorldVisited()`, `getBookTitle()`, `getBookAuthors()`, `getTargetName()`, `getBookPageList()`, `getInk()`, `getInkCapacity()` |
| `BookBinderMenu` | block | 0 cover (8,27), 1–27 inv (8,99), 28–36 hotbar (8,157), 37 output (152,27) | `SetTitle(Title)`, `TakeFromSlider(Index)`, `InsertHeldAt(Index, Single)` (a held folder inserts from the folder) | (BE update tag) | `getBinder()`, `getPendingTitle()`, `getPageList()`, `isMissingLinkPanel()` |
| `InkMixerMenu` | block | 0 ink in (8,27), 1 paper (8,48), 2 out (152,27), 3–29 inv (8,99), 30–38 hotbar, 39 output (152,48) | `Consume(Single)` | `SetInk(Ink)`, `SetSeed(Seed)`, `SetProperties(Properties: compound name→float)` | `getMixer()`, `hasInk()`, `getProperties()`, `getPropertyGradient()` |
| `LinkModifierMenu` | block | 0 book (80,35), 1–27 inv (8,84), 28–36 hotbar | `SetTitle(Title)`, `SetFlag(Flag, Value)`, `SetSeed(Seed: string, "" clears)`, `RecycleDim` | `LinkDead(Dead)` | `getModifier()`, `getBook()`, `getBookTitle()`, `getLinkFlag(LinkProperty)`, `getLinkDimensionId()`, `getItemSeed()`, `hasItemSeed()`, `isLinkDead()` |
| `BookMenu` | `openForHeldBook(ServerPlayer, InteractionHand)`, `openForBlock(ServerPlayer, BlockPos)`, `openForEntity(ServerPlayer, Entity)` | block/entity: 0–26 inv (8,84), 27–35 hotbar (active while no book), 36 book (80,35) (active while no book), 37 book (41,21) (active with book on page 0); held: none | `Link`, `SetCurrentPage(Index)` | `LinkPermitted(Permitted, Visited)`, `SetCurrentPage(Index)` | `getBookView()`, `getBook()`, `getLinkInfo()`, `hasBookSlot()`, `getCurrentPageIndex()`, `getCurrentPage()`, `getPageCount()`, `isLinkPermitted()`, `isTargetWorldVisited()`, `getBookTitle()`, `getBookAuthors()` |
| `FolderMenu` | `openForHeldItem(ServerPlayer, InteractionHand)` | 0–26 inv (8,135), 27–35 hotbar (8,193); held slot banned | `AddToSurface(Index, Single)`, `RemoveFromOrderedCollection(Index)`, `RemoveFromCollection(Page)` | – | `getInventoryItem()`, `getPageCollection()`, `getHand()`, `getTabItemName()` |
| `ArchivistShopMenu` | `open(ServerPlayer, Villager)` | 0–26 inv (8,99), 27–35 hotbar (8,157) | `PB`, `PI(Index)` | `UVC(Shop: compound from ArchivistShop.toTag())` | `getVillager()`, `getShop()`, `getShopItem(i)`, `getShopItemPrice(i)`, `getBoosterCount()`, `getBoosterCost()`, `getPlayerEmeralds()` |

`ItemStack` values inside tags are stored with `tag.store(key, ItemStack.OPTIONAL_CODEC, registryOps, stack)`;
`FluidStack` with `FluidStack.OPTIONAL_CODEC`.

## Lang keys

`block.mystcraft.ink_mixer`, `block.mystcraft.book_binder`, `block.mystcraft.book_receptacle`, `block.mystcraft.bookstand`,
`block.mystcraft.lectern`, `block.mystcraft.link_modifier`, `block.mystcraft.crystal`, `block.mystcraft.link_portal`,
`block.mystcraft.writing_desk`, `block.mystcraft.star_fissure`, `block.mystcraft.black_ink`,
`block.mystcraft.decay_black|red|green|blue|purple|yellow|white`;
`container.mystcraft.ink_mixer`, `container.mystcraft.book_binder`, `container.mystcraft.link_modifier`,
`container.mystcraft.writing_desk`, `container.mystcraft.book`, `container.mystcraft.folder`,
`container.mystcraft.archivist_shop`.

## Unverified API calls (`grep UNVERIFIED`)

`BookstandBlock#getInteractionShape` override signature; `InkFluidBlock` → `LiquidBlock(FlowingFluid, Properties)`.
