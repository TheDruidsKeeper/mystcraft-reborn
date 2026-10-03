# Package C1 — items & linking (`item/**`, `linking/**` except `PortalUtils`)

All classes in `com.techbucketdivision.mystcraft`. Constructors take `Item.Properties` unless noted. Server logic is
guarded by `!level.isClientSide()` / `ServerLevel` parameters; no client imports.

## item

| Class | Key API |
|---|---|
| `PageItem` (Writable, PageProvider, Renameable(no-op), OnLoadable) | `static ItemStack createBlankPage()`, `createSymbolPage(AgeSymbol)`, `createSymbolPage(Identifier)`, `createLinkPanel(Set<LinkProperty>)`, `createLinkPanel()`, `createFromPaper(ItemStack paper)`; `static boolean isPage/isBlank/isLinkPanel/isSymbolPage(ItemStack)`; `static @Nullable Identifier getSymbolId(ItemStack)`, `@Nullable AgeSymbol getSymbol(ItemStack)`, `Set<LinkProperty> getLinkProperties(ItemStack)`, `setSymbol(ItemStack, @Nullable AgeSymbol)`, `addLinkProperty(ItemStack, LinkProperty)`; `static List<ItemStack> remap(ItemStack)` / `remapAll(List<ItemStack>)` (SymbolRemapper). Name: blank/link panel/`symbol (%s)`; tooltip lists link properties; `inventoryTick` remaps (split → Collation Folder in the same slot, removed → stack deleted). Components: `SYMBOL`, `LINK_PANEL`. |
| `LinkingItem` (abstract; PortalActivator, Renameable) | `static LinkInfo getLinkInfo(ItemStack)`, `setLinkInfo(ItemStack, LinkInfo)`, `hasLinkInfo`, `isLinkingItem`; health: `getBookHealth/getHealth/getMaxHealth(ItemStack)`, `setHealth(ItemStack, float)` (component `BOOK_HEALTH`; durability bar from it, `isDamageable=false`); `void activate(ItemStack, ServerLevel, Entity)` (permission check → fill missing target UUID → leave book behind as `LinkbookEntity` unless FOLLOWING → `LinkController.travelEntity`); `onPortalCollision` (§2.3: MAINTAIN_MOMENTUM, !GENERATE_PLATFORM, EXTERNAL, sound `mystcraft:linking.link_portal`); `getPortalColor` / `static int getLinkColor(LinkInfo)` (§7.6); `isFoil` when FOLLOWING; `use` → `BookMenu.openForHeldBook(ServerPlayer, InteractionHand)`; `inventoryTick` → `validate` (initialises missing `LINK_INFO`/`BOOK_HEALTH`); `getTitle(ItemStack)`, `List<String> getAuthors(ItemStack)` (`AUTHORS`), `getTargetDimension(ItemStack)`; `protected abstract initialize(@Nullable ServerLevel, ItemStack, @Nullable Entity)`. |
| `DescriptiveBookItem` (Writable, PageProvider, OnLoadable) | `static ItemStack create(Player, List<ItemStack> pages, String title)` (GENERATE_PLATFORM + page-0 panel flags, author); `static void initializeForAge(ItemStack, AgeData)`; `static void checkFirstLink(ItemStack, MinecraftServer)` (AgeManager.createAge → dimension/targetUuid, name, seed prop `seed`, remapped pages, authors, `Grammar.expandAge(written, RandomSource.create(seed))` → `AgeData.setSymbols`); `static boolean isNewAgebook(ItemStack)`, `isDescriptiveBook(ItemStack)`; `static List<ItemStack> getPages(ItemStack)`, `setPages(ItemStack, List<ItemStack>)`, `List<Identifier> writtenSymbols(List<ItemStack>)`; `static @Nullable AgeData getAgeData(@Nullable MinecraftServer, ItemStack)`; `static void rename(ItemStack, @Nullable MinecraftServer, String)`; `static void setSeed(ItemStack, @Nullable MinecraftServer, long)`; `writeSymbol` (refused once the Age is visited; first blank page; adds author); `activate` runs `checkFirstLink` first. Components: `LINK_INFO`, `PAGES`, `AUTHORS`, `BOOK_HEALTH`. |
| `LinkingBookItem` (PageProvider) | `static ItemStack createAt(Entity)`, `static LinkInfo linkInfoAt(Entity)` (age name or dimension path; age UUID); `getPageList` = one display link panel with the book's flags. |
| `UnlinkedBookItem` | `static ItemStack createFrom(ItemStack linkPanel)` (copies `LINK_PANEL`), `static Set<LinkProperty> getProperties(ItemStack)`; tooltip lists properties; `use` (server, count 1) → Linking Book at the player's position with the panel flags. |
| `BoosterItem` | `static ItemStack generateBooster(RandomSource)` (7/4/4/1 by rank via `CardRanks.ofRank/ofRankAtLeast/weightedRandom`) + 5-arg overload; `use` consumes one, refunds if the inventory is full. |
| `FolderItem` (Renameable, OrderablePageProvider, PageAcceptor, Writable, OnLoadable) | `static ItemStack create(String title, List<ItemStack> pages)`; `static @Nullable String getTitle`, `setTitle`, `SlotPages getSlots`, `boolean isEmpty(ItemStack)`, `isItemValid(ItemStack)` (page or paper, count 1), `getItem(ItemStack, int)`, `getItemCount`; `getMaxStackSize(ItemStack)` = 32 when empty else 1; `use` → `FolderMenu.openForHeldItem`. Components: `SLOT_PAGES`, `ITEM_TITLE`. |
| `PortfolioItem` (PageCollection, Renameable, OnLoadable) | `static ItemStack create(String title, List<ItemStack> pages)`; `getTitle/setTitle`; `addPage` accepts pages or a whole folder/portfolio (count 1); `remove(player, portfolio, page)` removes up to `page.getCount()` equal entries; `use` → `FolderMenu.openForHeldItem`. Components: `PAGES`, `ITEM_TITLE`. |
| `InkVialItem` | `VOLUME = 1000`; `static boolean isVial(ItemStack)`, `FluidStack contents()`, `FluidResource inkResource()`; nested `Handler(ItemAccess) implements ResourceHandler<FluidResource>` (size 1; extract 1000 mB exchanges vial→glass bottle, insert 1000 mB exchanges glass bottle→vial). |
| `ItemCapabilities` | `static void register(RegisterCapabilitiesEvent)` — registers `Capabilities.Fluid.ITEM` on `ink_vial` and `minecraft:glass_bottle`. **Package E must call it from its mod-bus `RegisterCapabilitiesEvent` handler** (alongside `BlockEntityCapabilities.register`). |
| `WritingDeskItem(boolean backboard, Item.Properties)` | `isBackboard()`; `useOn`: places head+foot desk (facing = player facing rotated clockwise, foot = `head.relative(facing)`) or extends an existing desk with the two backboard blocks. Uses `WritingDeskBlock.FACING/TOP/FOOT`. |
| `DecayBlockItem(Block, DecayType, Item.Properties)` | `getDecayType()`. |
| `recipe.LinkingBookRecipe` | `CustomRecipe`; `MAP_CODEC = MapCodec.unit(...)`, `STREAM_CODEC = StreamCodec.unit(...)`; one link panel + one leather, respects `crafting.linkbook.enabled`; datapack JSON `{"type": "mystcraft:linking_book"}`. |

## linking

| Class | Key API |
|---|---|
| `LinkController` | `static boolean travelEntity(Entity, LinkInfo)` (§7.3: permission → root vehicle → `resolveLevel` (creates Age levels via `AgeManager.getOrCreateLevel`) → spawn default = destination `getRespawnData().pos()` → `LinkEvent.Alter` → recursive passenger teleport with `LinkEvent.Start/End/Failed`, `Entity#teleport(TeleportTransition)` across levels, `teleportTo` inside one level, push-up out of solid geometry, close open menus, re-mount passengers); `static @Nullable ServerLevel resolveLevel(MinecraftServer, ResourceKey<Level>)`. |
| `LinkListeners` | `static void registerDefaults()` (idempotent; game bus); `static boolean isLinkPermitted(ServerLevel, Entity, LinkInfo)` (posts `LinkEvent.Allow`). Listeners: basic rules (unbound, dead/other-level/ridden entity, intra-linking flags, dead Age, UUID mismatch, Disarm for items/book entities), per-player `LinkPermissions` (skipped for OP_TP), Relative alter, Disarm (players `dropAll`, `Container` entities, mob equipment, horse/donkey/llama inventories via `Entity#getSlot(500 + i)` since `AbstractHorse.inventory` is protected), particles (`LinkParticlesPayload` to the dimension) + `LinkSounds` on Start and End, momentum (+0.2 up; minecarts stopped), Generate Platform (stone under spawn), advancements `ModCriteria.ENTER_AGE_SAFE` (Linking Book in inventory) / `ENTER_AGE_QUINN`. |
| `LinkPermissions` (SavedData `mystcraft:link_permissions`, server-global) | `static LinkPermissions get(MinecraftServer)`; `static String keyOf(Player)`; `canEnter/canLeave(String player | Player, ResourceKey<Level>)`; `permitEntry/restrictEntry/permitDepart/restrictDepart(String player, @Nullable ResourceKey<Level> dim)` (`null` = all); read-only views `permittedEntry()`, `restrictedEntry()`, `permittedDepart()`, `restrictedDepart()`. For `/myst-permissions`. |
| `InkEffects` | `static final LinkProperty DILUTION` ("dilution", non-inkable, no colour — the original `""`); `registerDefaults()`; `addPropertyToItem(ItemStack | TagKey<Item> | Item, LinkProperty, float)` (sum ≤ 1 enforced); `Map<LinkProperty,Float> getItemEffects(ItemStack)` (empty map when not a modifier; lookup stack → tag → item); `isInkModifier(ItemStack)`; `List<LinkProperty> getProperties()` (inkable + coloured); `isPropertyAllowed(LinkProperty)` (config `crafting.linkeffects.disabled`); `isCraftable(LinkProperty)` (excludes Relative and dilution); `Colors.RGB getPropertyColor(LinkProperty)`; `ColorGradient getPropertiesGradient(Map<LinkProperty,Float>)` (§7.2). Metal dusts use tags `c:dusts/<brass|bronze|tin|iron|lead|silver|diamond|gold>`; black dye via `c:dyes/black` and `minecraft:black_dye`. |
| `StarFissureLinker` | `static LinkInfo linkInfo(Entity)` (home dimension, NATURAL + EXTERNAL, sound `mystcraft:linking.link_fissure`, entity yaw); `static boolean link(ServerLevel, Entity)` (respects/sets the portal cooldown; passengers refused). |
| `LinkSounds` | `static SoundEvent select(Entity, LinkInfo)` (§7.3 order: pop for items/book entities → disarm → `Sound` prop → following → intra → link); `static @Nullable SoundEvent byId(String)`; `static void play(Entity, LinkInfo)`, `play(Level, x, y, z, SoundEvent)` (0.8 volume, 0.9–1.1 pitch, PLAYERS). |

## Cross-package assumptions
- C2: `menu.BookMenu.openForHeldBook(ServerPlayer, InteractionHand)`, `menu.FolderMenu.openForHeldItem(ServerPlayer, InteractionHand)`, `block.WritingDeskBlock.FACING` (`EnumProperty<Direction>`; C2 chose `FACING` over the contract's `HORIZONTAL_FACING`), `WritingDeskBlock.TOP`, `WritingDeskBlock.FOOT` (`BooleanProperty`).
- E: `entity.LinkbookEntity.spawnFor(ServerLevel, Entity dropper, ItemStack book)`; `LinkbookEntity` type used in `instanceof` checks; E's mod-bus handler must call `item.ItemCapabilities.register(RegisterCapabilitiesEvent)`.
- A: `symbol.SymbolRemapper.hasRemapping(Identifier)` / `remap(Identifier)`; `symbol.grammar.Grammar.expandAge(List<Identifier>, RandomSource)`.
- `LinkEvent.Allow/Alter/Start/End/Failed` are the only link events (no separate portal / fissure events).

## UNVERIFIED lines (grep `UNVERIFIED`)
- `LinkController`: `CollisionGetter#noCollision(Entity, AABB)` used for the push-up loop.

## Lang keys used
```
item.mystcraft.page
item.mystcraft.page.blank
item.mystcraft.page.link_panel
item.mystcraft.page.symbol            # "Page (%s)"
item.mystcraft.descriptive_book
item.mystcraft.linking_book
item.mystcraft.unlinked_book
item.mystcraft.sealed_notebook
item.mystcraft.collation_folder
item.mystcraft.symbol_portfolio
item.mystcraft.ink_vial
item.mystcraft.writing_desk
item.mystcraft.writing_desk_backboard
item.mystcraft.black_ink_bucket
item.mystcraft.decay_black
item.mystcraft.decay_red
item.mystcraft.decay_green
item.mystcraft.decay_blue
item.mystcraft.decay_purple
item.mystcraft.decay_yellow
item.mystcraft.decay_white
link_property.mystcraft.intra_linking
link_property.mystcraft.intra_linking_only
link_property.mystcraft.relative
link_property.mystcraft.disarm
link_property.mystcraft.maintain_momentum
link_property.mystcraft.generate_platform
link_property.mystcraft.natural
link_property.mystcraft.external
link_property.mystcraft.offensive
link_property.mystcraft.op_tp
link_property.mystcraft.following
link_property.mystcraft.dilution
```
Sound ids referenced as strings in link props: `mystcraft:linking.link_portal`, `mystcraft:linking.link_fissure`
(subtitle keys belong to package F's `sounds.json`).
