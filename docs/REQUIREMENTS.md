# Mystcraft — Requirements Specification for a From-Scratch Rewrite

**Source analysed:** Mystcraft-Legacy (`com.xcompwiz.mystcraft`, mod id `mystcraft`, version 0.13.7.06 for Minecraft 1.12.2 / Forge 14.23.5.2770+, LGPL-3.0).
**Target:** NeoForge for Minecraft 26.1.
**Scope of this document:** behavioural and data requirements extracted from the original source (539 Java files + resources). It documents *what* the original does, with exact numbers, so that a reimplementation can reproduce gameplay faithfully. It does not prescribe the new code design.

Conventions used below:

* `mystcraft:Foo` denotes a symbol/registry identifier (the original used mixed-case resource paths such as `mystcraft:TerrainNormal`; the localization key is the lower-cased path, e.g. `myst.symbol.terrainnormal.name`).
* "Ticks" are 20/second. "24000" = one Minecraft day.
* NBT key names are given verbatim; they matter for save compatibility only if compatibility is desired, otherwise they document the data model.
* "Legacy metadata" columns describe the 1.12 block-state → meta packing and are informational only.

---

## 1. Overview & Lore Concepts

### 1.1 Core concepts

| Concept | Meaning in Mystcraft |
|---|---|
| **Age** | A procedurally generated dimension. Each Age is described by an ordered list of **symbols**. Every Age is a separate dimension with its own `AgeData` record (name, seed, UUID, pages/symbols, spawn, instability, visited flag, dead flag, world time, weather storage). Save folder `DIM_MYST<id>`. |
| **Symbol** | A registry object (`IAgeSymbol`) that contributes logic to an Age (terrain generator, biome distribution, colours, weather, celestial objects, populators, effects, or a *modifier* value consumed by later symbols). Every symbol has a 4-word "poem" drawn from a fictional word list, a **card rank** (rarity tier used for loot/trade/booster generation), grammar rules (how randomly generated Ages include it), and optionally an instability contribution. |
| **Page** | Item carrying one symbol (or a **Link Panel**, or blank). Written in a Writing Desk using ink. |
| **Descriptive Book** ("agebook") | A book whose pages describe an Age. First page must be a Link Panel. On first use it creates a new dimension from its pages ("first link"). Links to that Age. |
| **Linking Book** | Created from an Unlinked Link Book by right-clicking; it records the position/dimension where it was activated and links back there. |
| **Link Panel** | A page produced by the Ink Mixer; carries **link properties** (Intra-Linking, Disarm, Maintain Momentum, Generate Platform, Relative, ...) that transfer to the book that is bound with it. |
| **Instability** | A numeric score per Age computed from symbols, the chunk profile of "free resources" (ores), and bonuses. Higher score activates progressively worse **instability effects** drawn from shuffled "decks" (potion effects, decay blocks, meteors, lightning, explosions, crumbling). |
| **Decay** | Spreading corruption blocks (`blockdecay`) with 5 active variants (black, red, blue, purple, white) and two reserved (green, yellow). |
| **Star Fissure** | Natural feature placed near the spawn of an Age that links back to the home dimension (overworld). |
| **Crystal / Book Receptacle / Portal** | Crystal blocks in a frame + a Book Receptacle holding a book create a `linkportal` block field; touching it links the entity. |
| **Archivist** | Custom villager profession that sells symbol pages, link panels and Sealed Notebooks (boosters). |
| **Baseline profiling** | On first run per mod-set (or per save) a hidden control dimension is generated and profiled to calibrate the "free ore" baseline used by instability scoring. |

### 1.2 Player loop

1. Find pages (dungeon/temple/library loot, Archivist trades, Sealed Notebooks).
2. Build a Writing Desk; put an Ink Vial (or bucket of black ink) in the desk, paper, and a Collation Folder as target; write copies of the symbols you know (studied pages, visited Ages) onto new pages (1 page = 50 mB ink) and attach modifiers to them (Reborn, see §3.10).
3. Build an Ink Mixer; fill it with black ink, drop modifiers (gunpowder, feathers, clay, ender pearls, ...) to build link-property probabilities; craft a Link Panel page from paper.
4. Build a Book Binder; insert a Link Panel as page 0 followed by symbol pages, name the book, provide leather (or an empty folder) as cover and take the Descriptive Book.
5. Craft an Unlinked Link Book (Link Panel + leather, shapeless) and right-click it to bind a Linking Book to the current position so you can get back.
6. Use the Descriptive Book (GUI → click the panel) to link; the book is dropped behind as an entity unless it has the *Following* flag.
7. Explore; manage instability; decay; find a Star Fissure to return home.

---

## 2. Items

All items are in mod id `mystcraft`. Unless noted, creative tab is `mystcraft.common` ("Mystcraft"). Two creative tabs exist: `mystcraft.common` (icon: Descriptive Book; also lists spawned symbol-collection portfolios) and `mystcraft.pages` (icon Descriptive Book, has search bar, background `item_search.png`; lists all pages), plus `mystcraft.banners` (all Mystcraft banner patterns, search bar).

### 2.1 Item summary

| Item | Registry name | Unlocalized | Max stack | Rarity | Notes |
|---|---|---|---|---|---|
| Page | `mystcraft:page` | `item.myst.page` (`.blank`, `.panel`, `.symbol` variants) | 64 | common | Blank / Link Panel / Symbol page. Tab `mystcraft.pages`. |
| Descriptive Book | `mystcraft:agebook` | `item.myst.agebook` | 1 | EPIC (RARE if enchanted) | `ItemLinking`. |
| Linking Book | `mystcraft:linkbook` | `item.myst.linkbook` | 1 | RARE | `ItemLinking`. Creative tab: vanilla TRANSPORTATION. |
| Unlinked Link Book | `mystcraft:unlinkedbook` | `item.myst.unlinkedbook` | 16 | common | Right-click converts to Linking Book. |
| Sealed Notebook (booster) | `mystcraft:booster` | `item.myst.booster` | 64 | common | Right-click → Collation Folder with random pages. |
| Collation Folder | `mystcraft:folder` | `item.myst.folder` | 1 (32 while empty & unnamed) | UNCOMMON | Ordered page container (NBT `Pages` compound keyed by slot number). |
| Symbol Portfolio | `mystcraft:portfolio` | `item.myst.portfolio` | 1 | UNCOMMON | Unordered page collection (NBT `Collection` list). |
| Writing Desk (item) | `mystcraft:writingdesk` | `item.myst.writingdesk` (meta 1: `.top` "Writing Desk Backboard") | 64 | common | Places the 2-block desk (meta 0) or the 2-block backboard on top (meta 1). |
| Ink Vial | `mystcraft:vial` | `item.myst.vial` | 16 | common | Fluid container: 1000 mB `myst.ink.black`; container item Glass Bottle. |
| XComp's Glasses | `mystcraft:glasses` | `item.myst.glasses` | 64 | – | Easter egg: gold armor helmet, texture `textures/models/glasses.png`; deleted from inventory of any player not named `XCompWiz`. |
| Decay (item block) | `mystcraft:blockdecay` | `tile.myst.unstable.<type>` | 64 | – | Sub-items per decay type (damage = type index). No creative tab. |

Item block equivalents exist for: `blockinkmixer`, `blockbookbinder`, `blockbookreceptacle`, `blockbookstand`, `blocklectern`, `blocklinkmodifier`, `blockcrystal`, `linkportal`, `blockstarfissure`, `blockdecay` (custom `ItemDecayBlock`). No item block for `writingdesk` (placed by the item) nor for the ink fluid block (bucket is registered via Forge universal bucket).

### 2.2 Page (`ItemPage`)

> **Reborn revision (studying pages):** using a symbol page in hand teaches the player every symbol on it (symbol and modifiers; `SymbolKnowledge.learnPage`) and consumes the page; a page whose symbols are all known is kept ("You already know everything on this page"). The tooltip says "Use: study this page" or "You know these symbols". Knowledge is a player attachment (`mystcraft:knowledge`, kept over death, synced with `KnowledgePayload`), also fed by arriving in an Age (every symbol of the Age). Logged under `[knowledge]`.

> **Reborn revision (symbol pages, `SymbolPage` component):** a symbol page stores `{id, modifiers: [symbol ids], discovered}`. The modifiers attached to the page are applied right before its symbol when the Age is built (the sequential modifier model of §4.2 stays; only the *source order* now comes from pages), so a page is self-contained and can be reordered freely. `discovered` marks pages the Age blueprint added at the first link (§4.4); they are rendered in a different ink and listed as "Discovered in the Age". Page icons draw the attached modifiers as small overlays on the corners of the symbol glyph (colour modifiers in their own colour), in GUIs and as item models (`SymbolGlyphs.drawSymbolPage`, `SymbolPageSpecialRenderer`). Every symbol belongs to one `SymbolCategory` (§4.4) and knows which modifier slots it takes (`AgeSymbol.accepts`, derived from the dry run of its logic) and, for modifiers, which slot it fills (`AgeSymbol.fills`).

**Data model (item NBT, original):**

| Key | Type | Meaning |
|---|---|---|
| `symbol` | String | ResourceLocation of the symbol (absent for blank / link panel). |
| `linkpanel` | Compound | Present ⇒ page is a Link Panel. Contains `properties`: list of strings (link property flags). |
| `Quality` | Compound | Named integer "quality traits" (API only, unused by base gameplay; `getTotalQuality` sums them). |

* A stack with no NBT is a blank page. `isBlank = !isLinkPanel && !has("symbol")`.
* Display name: Link Panel → "Link Panel Page"; blank → "Blank Page"; symbol → `Page (<localized symbol name>)`; unknown symbol id → `Page (Unknown: <id>)`.
* Tooltip: for link panels, lists each link property's localized name (`linkeffect.<property>.name`).
* Every server tick while in a player's inventory (`onUpdate`): blank pages have their NBT stripped; symbol pages are passed through **symbol remapping** (see §19.4). If remapping yields ≠1 result the stack is replaced by a Collation Folder containing the mapped pages; if 0 results the stack is deleted.
* `onLoad` (when loaded from NBT through `ItemStackUtils.loadItemStackFromNBT`) also remaps.
* `ItemPage.createItemstack(prototype)`: converts one Paper into a blank page (shrinks the paper stack by 1).
* Implements `IItemWritable` (`writeSymbol` succeeds only if blank), `IItemPageProvider` (page list = itself), `IItemRenameable` (no-op).
* Creative listing order: plain Link Panel, one Link Panel per registered link property (sorted alphabetically, *excluding* "Relative"), then every symbol page sorted by localized name.
* Rendering: each symbol page gets a dynamically stitched 128×128 texture (`page_<symbolpath>`, `page_blank`, `page_linkpanel`, `page_err_symbol`) built from `textures/items/page_background.png` scaled ×5 (160×160) with the 4 poem-word glyphs stitched at (48,0),(96,48),(48,96),(0,48) — top/right/bottom/left — then scaled to 0.8 (128). A blank word draws component 0 centred (48,48). Link panels have a 110×45 black rectangle at (25,30) of the 160 image. Item frame rendering is overridden to render the page flat (FIXED transform, rotated 180°). Held-item transforms: third person `(0,3,1) scale 0.55`, first person `(1.13,3.2,1.13) rot (0,-90,25) scale 0.68`, head `(0,13,7) rot(0,180,0)`, ground `(0,2,0) scale 0.5`, gui/fixed identity.

### 2.3 Linking items (`ItemLinking` base: Descriptive Book & Linking Book)

Common behaviour:

* Max stack 1, max damage 10, not repairable, not enchantable via books.
* **Health/durability**: NBT `damage` (float) and `MaxHealth` (float, default 10). Displayed damage = MaxHealth − health. The dropped `EntityLinkbook` mirrors its entity health to the item.
* NBT (link info, see §7.2 `LinkOptions`): `DisplayName`, `Dimension`, `TargetUUID`, `SpawnX/Y/Z`, `SpawnYaw`, `Flags{...}`, `Props{...}`, plus book-specific keys below.
* `onUpdate` (server): `validate()` → if the item has no NBT, `initialize()` it.
* Tooltip: adds the display name from NBT if non-empty.
* Right-click: opens GUI `BOOK` (the book GUI, §8.5) — server side only opens; client returns PASS.
* `activate(stack, world, entity)` (called from the book GUI "Link" message, lecterns, stands, entities): server only; if link info exists and `LinkListenerManager.isLinkPermitted`, sets `TargetUUID` on the stack to the target dimension's UUID, then `onLink` (drop the book as `EntityLinkbook` at the player and clear the held slot unless flag *Following* is set — only when the player's current item is this stack), then `LinkController.travelEntity`.
* `onPortalCollision` (book in a receptacle): copies link info, forces flags `Maintain Momentum=true`, `Generate Platform=false`, `External=true`, property `Sound=mystcraft:linking.link-portal`, posts `PortalLinkEvent`, then travels the entity.
* `getPortalColor`: colour derived from display-name hash (see §7.6).
* `hasEffect` (enchant glint) when flag *Following* is set.
* `hasCustomEntity` → dropped item becomes `EntityLinkbook`.
* `getTitle` = DisplayName; `getAuthors` default empty.

#### 2.3.1 Descriptive Book (`ItemAgebook`)

> **Reborn revisions:** on the first link the Age blueprint (§4.4) completes the book: discovered pages (flagged) for everything the author left out, the whole list organised by category, and `AgeData.symbols` derived from the pages (modifiers then symbol per page). The Age is built once immediately so a controller fallback (only possible with failing add-on symbols) is recorded as a discovered page too. The book then describes the whole Age, dangerous symbols included; the Age keeps the same page list. New Ages start at a seeded random point of their day (`AgeTicker.startAtRandomTime`, first ten days).

Extra NBT: `Pages` (list of page-item NBT), `Authors` (list of strings), `Props.Seed` (string long).

| Behaviour | Detail |
|---|---|
| Initialize (no NBT) | Creates NBT, sets flag `Generate Platform=true`, adds default pages (a single Link Panel; or the pages of the target Age if `Dimension` set). |
| Validate | Also ensures a `Pages` list exists. |
| `create(agebook, player, pages, title)` (Book Binder) | Sets NBT with pages, author (player display name), display name; if page 0 is a Link Panel, copies its link properties to the book flags. |
| `initializeCompound(stack, dimId, agedata)` (command) | Sets Dimension, TargetUUID, DisplayName=age name, `Generate Platform=true`, pages from age. |
| `isNewAgebook` | true if NBT exists, no `Dimension`, and page 0 is a Link Panel. Such books are always "link permitted" in GUIs. |
| **First link** (`checkFirstLink`) | If no `Dimension`: `DimensionUtils.createAge()` (recycles a dead dim or allocates a new id), stores Dimension & TargetUUID, sets age name = DisplayName, seed = `Props.Seed` if present else stores the age's seed into `Props.Seed`, remaps page list, `agedata.setPages(pages)`. |
| Rename | Sets DisplayName; if the Age exists, renames the Age. |
| `setSeed` (Link Modifier) | Sets the Age seed (only allowed before visited) and `Props.Seed`. |
| `writeSymbol` | Fails if age visited; writes into the first blank page in `Pages`; adds author. |
| `getPageList` | Pages from NBT. |
| `onLoad` | remap pages, initialize, validate, add default pages if empty. |
| Rarity | EPIC (RARE if enchanted). |

#### 2.3.2 Linking Book (`ItemLinkbook`)

* Initialize: `LinkingAPI.createLinkInfoFromPosition(world, entity)` → Dimension = current dim, TargetUUID = dim UUID, Spawn = entity block pos, SpawnYaw = entity yaw, DisplayName = age name (Mystcraft dims) or dimension type name.
* `getPageList` returns a single freshly created Link Panel (display only).
* Authors: NBT `Author` string if present.
* Rarity RARE, creative tab Transportation.

#### 2.3.3 Unlinked Link Book (`ItemLinkbookUnlinked`)

* Created by the special recipe `mystcraft:internal/linkingbook`: any grid with exactly one Link Panel page (page with `linkpanel` NBT) and exactly one Leather, nothing else (shapeless, needs ≥2 width or height). Result copies the panel's NBT onto the book (so tooltip shows properties). Recipe can be disabled by config `crafting.linkbook.enabled`.
* Right-click (server, stack count must be 1): creates a Linking Book initialized at the player's position, applies the panel's link properties (`Page.applyLinkPanel`: each property → flag true) and replaces the held stack.

### 2.4 Sealed Notebook / Booster (`ItemBoosterPack`)

* Right-click (server): consumes 1 and gives a Collation Folder generated by `generateBooster(rand, verycommon=7, common=4, uncommon=4, rare=1)`:
  * 7 pages from symbols of card rank 0, 4 from rank 1, 4 from rank 2, 1 from rank ≥3 — each chosen by weighted random using the symbol item weight (§4.2).
* If the inventory is full and the held stack was >1, the booster is refunded.
* Used in loot (weight 1000 in `mystcraft_treasure`) and Archivist trades (25 emeralds; shop price 20 emeralds).

### 2.5 Collation Folder (`ItemFolder`) — `IItemOrderablePageProvider`, `IItemWritable`, `IItemRenameable`

* NBT: `Name` (string, optional), `Pages` compound where key = slot index string, value = item NBT of a single page. Slots may be sparse.
* Accepts only pages or Paper (stack of exactly 1 per slot).
* Stack limit: 32 when empty (no name and no pages), else 1.
* Right-click (server, count==1) opens GUI `FOLDER`.
* `writeSymbol`: writes the first writable page (blank page) found.
* `addItem`: fills lowest free slots one item at a time. `setItem(slot)`: swaps.
* `onLoad`: remap all pages; if a page maps to several, remove and append them.
* Tooltip shows the name.

### 2.6 Symbol Portfolio (`ItemPortfolio`) — `IItemPageCollection`

> **Reborn revision:** removed. There are no page collections: symbols are *known* by the player (§2.2 studying pages) and the Writing Desk writes copies of known symbols. `IItemPageCollection` is gone with it; the creative tab offers a Scholar's Writing Desk (§3.10) instead of "Spawned (...)" portfolios.

* NBT: `Name`, `Collection` (list of item NBT compounds; duplicates allowed — a stack of N pages is stored as N entries).
* Only accepts `mystcraft:page` items; `addPage` also accepts a whole Folder/Portfolio (count 1) and moves all its pages in.
* `remove(page)` removes up to `page.count` identical entries and returns them as one stack.
* Right-click (server, count==1) opens GUI `PORTFOLIO` (same GUI class as folder).
* Creative "Spawned (...)" portfolios are generated per grammar token (see §8.7).

### 2.7 Ink Vial (`ItemInkVial`)

* Stack 16; container item Glass Bottle.
* Item fluid handler capability: holds exactly 1000 mB black ink; `drain` returns the full 1000 mB and turns the stack into a Glass Bottle; `fill` (only onto a non-vial with ≥1000 mB black ink) turns it into a vial.
* Recipes (shapeless): `dyeBlack + dyeBlack + minecraft:potion` (water bottle) → 1 vial; `dyeBlack + dyeBlack + glass_bottle + water_bucket` → 1 vial.

### 2.8 Writing Desk item (`ItemWritingDesk`)

* Meta 0 (desk) recipe (shaped, ore-dict): `vf ` / `ppp` / `pp ` with v = glass bottle, f = feather, p = plankWood → 1 desk.
* Meta 1 (backboard) recipe: `ppp` / `pfp` with f = item frame → 1 backboard.
* Place (meta 0): on a top face (or replaceable block → treat as placing above it), computes facing = player horizontal facing rotated around Y; the desk occupies `pos` and `pos + offset` where offset depends on `facing.horizontalIndex`: 0 → (0,0,+1), 1 → (−1,0,0), 2 → (0,0,−1), 3 → (+1,0,0). Both positions must be replaceable and editable. Sets base block at `pos` (ROTATION=facing) and foot block at offset (`IS_FOOT=true`). Consumes 1 unless creative.
* Extend (meta 1): target must be a desk block that is not a top; if foot, walk to head. The two blocks above (head & foot) must be replaceable; sets `IS_TOP=true` above head and `IS_TOP+IS_FOOT` above foot. Consumes 1.

### 2.9 Crafting recipes (JSON, ore-dictionary)

| Result | Pattern | Key |
|---|---|---|
| Book Binder ×1 | `iii` / `ppp` / `pp ` | i=ingotIron, p=plankWood |
| Bookstand ×1 | `ss ` / `p  ` | s=stickWood, p=plankWood |
| Collation Folder ×1 | `l` / `s` / `l` (column) | l=leather, s=string |
| Ink Mixer ×1 | `ss ` / `svs` / `psp` | s=minecraft:stone (meta 0), v=glass bottle, p=plankWood |
| Ink Vial ×1 | shapeless | dyeBlack, dyeBlack, minecraft:potion |
| Ink Vial ×1 | shapeless | dyeBlack, dyeBlack, glass_bottle, water_bucket |
| Lectern ×2 | `p  ` / `pps` / `ppp` | p=plankWood, s=stickWood |
| Symbol Portfolio ×1 | `lll` / `s  ` / `lll` | l=leather, s=string |
| Book Receptacle ×1 | `ccc` / `cc ` / `ccc` | c=mystcraft:blockcrystal |
| Writing Desk ×1 (meta 0) | `vf ` / `ppp` / `pp ` | v=glass bottle, f=feather, p=plankWood |
| Writing Desk Backboard ×1 (meta 1) | `ppp` / `pfp` | f=item_frame, p=plankWood |
| Unlinked Link Book | special `RecipeLinkingbook` | 1 Link Panel page + 1 Leather |

No recipe exists for: Crystal block, Link Modifier, Decay, Star Fissure, Portal, Descriptive/Linking Book (made in Book Binder / by right-clicking the unlinked book), Booster.

### 2.10 Item interfaces (used by GUIs)

| Interface | Implementors | Purpose |
|---|---|---|
| `IItemWritable.writeSymbol(player, stack, symbol)` | Page, Agebook, Folder | Desk can write a symbol into it. |
| `IItemRenameable.get/setDisplayName` | Page(no-op), Agebook, Linkbook, Folder, Portfolio | Desk/Link Modifier rename. |
| `IItemPageProvider.getPageList` | Page, Agebook, Linkbook, Folder | Read pages. |
| `IItemPageAcceptor.addPage` | Folder, Portfolio | Add a page. |
| `IItemOrderablePageProvider.setPage/removePage(index)` | Folder | Ordered editing. |
| `IItemPageCollection.remove(page)/getItems` | Portfolio | Unordered editing. |
| `IItemPortalActivator.onPortalCollision/getPortalColor` | Agebook, Linkbook | Receptacle portal. |
| `IItemOnLoadable.onLoad` | Page, Agebook, Folder, Portfolio | Migration on load. |
---

## 3. Blocks & Block Entities

### 3.1 Block summary

| Block | Registry name | Unlocalized | Material / sound | Hardness / resistance | Light | Tile entity | Harvest |
|---|---|---|---|---|---|---|---|
| Ink Mixer | `blockinkmixer` | `tile.myst.inkmixer` | WOOD / wood | 2 / 2 | 0 | `TileEntityInkMixer` (`mystcraft:inkmixer`) | any |
| Book Binder | `blockbookbinder` | `tile.myst.bookbinder` | WOOD / wood | 2 / 2 | 0 | `TileEntityBookBinder` (`mystcraft:bookbinder`) | any |
| Book Receptacle | `blockbookreceptacle` | `tile.myst.receptacle` | GLASS / glass | 1 / default | 0 | `TileEntityBookReceptacle` (`mystcraft:crystal_receptacle`) | any |
| Bookstand | `blockbookstand` | `tile.myst.bookstand` | WOOD / wood | 2 / 2 | 0 (opacity 0) | `TileEntityBookstand` (`mystcraft:linkbook_stand`) | any |
| Lectern | `blocklectern` | `tile.myst.lectern` | WOOD / wood | 2 / 2 | 0 (opacity 255) | `TileEntityLectern` (`mystcraft:linkbook_lectern`) | any |
| Decay | `blockdecay` | `tile.myst.unstable` | SAND / sand | per type | 0 | – | pickaxe 0 (blue, purple), shovel 0 (red, black), pickaxe 2 (white) |
| Link Modifier | `blocklinkmodifier` | `tile.myst.linkmodifier` | IRON / metal | 2 / 2 | 0 | `TileEntityLinkModifier` (`mystcraft:linkmodifier`) | any |
| Crystal | `blockcrystal` | `tile.myst.crystal` | GLASS / glass | 1 / default | 0.5 (=light 7–8) | – | pickaxe 0 |
| Link Portal | `linkportal` | `tile.myst.linkportal` | PORTAL / glass | unbreakable | 0.75 (=11) | – | drops nothing |
| Writing Desk | `writingdesk` | `tile.myst.writing_desk` | WOOD / wood | 2.5 | 0 | `TileEntityDesk` (`mystcraft:writingdesk`) on head block only | any |
| Star Fissure | `blockstarfissure` | `tile.myst.starfissure` | PORTAL | unbreakable | 0.4 (=6) | `TileEntityStarFissure` (`mystcraft:starfissure`, render only) | drops nothing |
| Black Ink (fluid block) | `fluidblockblackink` | `tile.myst.ink.black` | LIQUID (MapColor WATER) | – | – | – | – |

Fluid: `myst.ink.black` (`fluid.myst.ink.black` = "Black Ink"), still texture `blocks/fluid`, flowing `blocks/fluid_flow`, colour `0xFF191919`, bucket registered. `Mystcraft.validInks` = {`myst.ink.black`} (set of accepted ink fluid names; extensible).

### 3.2 Ink Mixer (`BlockInkMixer` / `TileEntityInkMixer`)

> **Reborn revision (deterministic ink, one ingredient per effect):** the basin holds a *set* of link effects instead of probabilities. Clicking the basin with an ingredient consumes exactly one item and switches on the one effect the ingredient stands for (an effect already present costs nothing); the clearing ingredient (black dye) empties the set; filling the basin with fresh ink clears it too. The Link Panel page gets exactly the effects in the set - no roll. The table is config (`inkmixer.ingredients`, one `effect=item` entry per effect, `inkmixer.clearIngredient`), defaults priced by what the effect gives: Generate Platform = clay ball, Maintain Momentum = feather, Disarm = gunpowder, Intra-Linking Only = compass, Intra-Linking = ender pearl, Relative = amethyst shard, Following = eye of ender. The original's probability table in §7.2 (mushroom stew, bottle o' enchanting, fire charge, metal dusts) is reference only and no longer implemented. `[ink]` log lines record the resolved table and every mix.

* State: `facing` (horizontal, from placer). Non-opaque cube, model `inkwell_model` (+ TESR `ModelInkMixer` for the pages on it).
* Right-click → GUI `INK_MIXER`.
* Break → drops inventory contents.
* Tile inventory (`IOInventory`, 3 slots, accessible from all sides): slot 0 `ink_in` (input; any fluid container whose fluid name ∈ validInks), slot 1 `paper` (input; Paper only), slot 2 `ink_out` (output; empty containers).
* Tile NBT: `inventory`, `ink` (bool hasInk), `probabilities` (compound String→float).
* Tick (server): if `ink_in` non-empty and `!hasInk`: drain exactly 1000 mB (`Fluid.BUCKET_VOLUME`) from the container; container's empty form is merged into `ink_out` (only if it fits); `hasInk=true`.
* **Adding modifiers** (`addItems(stack, amount)` from GUI message `Consume`): requires `hasInk`. Looks up `InkEffects.getItemEffects(stack)` (§19.1). Let `total` = sum of allowed property probabilities of the item; for each consumed item: every existing probability is multiplied by `(1 − total)`, then each item property probability is added. Properties disabled by config (`crafting.linkeffects.<prop>.enabled=false`) are ignored. Properties with key `""` (black dye) count as "dilution" only.
* **Crafting** (`getCraftedItem` shown in output slot when `canBuildItem` = paper present && hasInk): result = Link Panel page. On take (`buildItem`): with `Random(next_seed)`, for each property p: `if rand.nextInt(100) < prob*100` add property to the page; `next_seed = rand.nextLong()`; `hasInk=false`; probabilities cleared; paper shrinks 1. (The seed is synced to the client so the client can predict.)

### 3.3 Book Binder (`BlockBookBinder` / `TileEntityBookBinder`)

* State `facing` (horizontal). Right-click → GUI `BOOK_BINDER`. Break drops inventory and page list.
* Tile: `IOInventory` slot 0 = cover slot (input; Leather or an *empty* Collation Folder), plus a virtual ordered page list `pages` and `pendingtitle` (NBT `items`, `pages` list, `title`).
* `insertPage(stack, index)`: Paper is converted page by page into blank pages; `mystcraft:page` items are inserted one-by-one at index; other items rejected. `insertFromFolder(folder, index)`: if folder has no pages (largest slot −1) the binder's pages are moved *into* the folder; else each folder page is inserted in order.
* `canBuildItem`: cover valid, ≥1 page, page 0 is a Link Panel, title non-empty, no other page is a Link Panel.
* Craft result: Descriptive Book (`ItemAgebook.create(book, player, pages, title)`); on take, pages cleared, title cleared, cover shrinks by 1.

### 3.4 Book displays (`BlockBookDisplay` base: Bookstand, Lectern)

> **Reborn revision:** linking from a stand / lectern / receptacle (right-click, or walking through the portal) binds an unbound Descriptive Book through the normal first link and **keeps the bound copy in the block** (`BookDisplayBlockEntity.link`, `LinkPortalBlock.entityInside`), so the book it holds carries the discovered pages. Debug / command books (`/myst-scene`, `/myst-create`, `/myst-visit`, QA shelf) are made with `DescriptiveBookItem.createBound`, i.e. through the blueprint, never as empty Ages.

> **Reborn revision:** a Descriptive or Linking Book on a stand is rendered as an open book (vanilla `BookModel`, legacy `agebook.png` / `linkbook.png` covers) - flat on the bookstand, along the lectern's slope with the spine running away from the reader; non-book items still lie flat as their icon.

Common right-click logic (server):
* If tile has no book: if held item acceptable → move 1 into the tile; else open GUI `BOOK_DISPLAY`.
* If tile has a book: sneaking with empty hand → take the book; else open GUI `BOOK_DISPLAY`.
* Break → drop the book. Comparator output = `calcRedstoneFromInventory()` of the 1-slot inventory (i.e. 15 when filled).
* Tile `TileEntityBook`: 1-slot `IOInventory` (in/out slot 0, stack limit 1, filter). `TileEntityBookRotateable` adds `Yaw`/`Pitch` shorts; `link(entity)` activates the book.

| Block | State | Accepts | Bounding box | Yaw quantum | Render |
|---|---|---|---|---|---|
| Bookstand | `rotindex` 0–7 (from placer yaw: `floor(yaw*8/360+0.5)&7`) | `ItemLinking` only | (0.125,0,0.125)-(0.875,0.75,0.875); raytrace uses 3 sub-boxes {0.35,0,0.35,0.65,0.2,0.65},{0.45,0.1,0.45,0.55,0.5,0.45},{0.15,0.4,0.15,0.85,0.7,0.85} | 45° (`rotation - rotation%45`; legacy NBT `Rotation`+270) | OBJ model `obj/bookstand.obj`; TESR draws open book (ModelBook open=1.05, scale 0.8) at y+0.55, rotated 90+45·rotindex, tilted 120° |
| Lectern | `facing` horizontal | `ItemLinking`, `mystcraft:page`, `minecraft:filled_map` | (0,0,0)-(1,0.4375,1) | 90° | OBJ `obj/lectern.obj`; TESR draws open book (1.22) at y+0.255 tilted 110°, or a map (rendered via map renderer) or a generic item. Ticks: if displaying a filled map, sends map data packets to all players each tick. |

Name labels: when `renderlabels` (client config) AND server allows (`serverLabels`), book title is drawn above stands/lecterns/receptacles/book entities within 25 blocks.

### 3.5 Book Receptacle (`BlockBookReceptacle` / `TileEntityBookReceptacle`)

* State `rotation` (all 6 facings; = placement face). Can only be placed on a **Crystal** block (the block behind the placement face), never on the bottom face. Neighbour change: if the crystal behind it is gone → drops itself.
* Bounding box: 0.375 thick slab against the attached face.
* Right-click: empty → accepts an `IItemPortalActivator` item (books) from hand (whole stack moved, hand emptied); filled → empty hand takes the book.
* Break → drop book. Comparator like other displays.
* On item change (server): `PortalUtils.shutdownPortal(pos)` then if a book is present `PortalUtils.firePortal(pos)`.
* `getPortalColor()` → from the book (`IItemPortalActivator.getPortalColor`) else 0xFFFFFF.
* Renders a closed book (ModelBook 0.005) rotated per face.

### 3.6 Crystal (`BlockCrystal`)

* States: `source` (EnumFacing, default DOWN), `active` (bool). Legacy meta: 0 inactive; else `source.ordinal()+1`.
* Light 0.5, glass sound, hardness 1, pickaxe level 0.
* Neighbour change (server): if active and the receptacle reachable by following `source` links is missing or has no book → reset to default state and `PortalUtils.shutdownPortal`.
* Comparator output 15 when active.
* Used as block-modifier symbol (`ModMat_blockcrystal_0`, word Chain, card rank 3; categories SOLID 4, STRUCTURE 4, CRYSTAL 4) and instability factors (20, 4). Crystal Formation feature default block.

### 3.7 Link Portal (`BlockLinkPortal`)

* Material PORTAL, unbreakable, light 0.75, ticks randomly, translucent render layer, no collision box, drops nothing.
* States: `source` (6 facings), `active` (bool), plus render-only `hasface` and `renderface` (axis) computed in `getActualState`: for each axis, if all 4 orthogonal neighbours around that axis are portal/crystal blocks, the axis is "valid"; `hasface = exactly one valid axis`, `renderface = that axis`. Models: `linkportal_ew` (x), `linkportal_ud` (y), `linkportal_ns` (z), `linkportal_full`.
* Visual bounding box grows from the central 0.25–0.75 cube to touch any adjacent crystal/portal on each side.
* Side culling: full-cube portals hide faces against other full portals and crystals.
* Block colour: `TileEntityBookReceptacle.getPortalColor()` of the receptacle found by following `source` chain; else white. Item colour 0x3333FF.
* Neighbour change / random tick (server): `PortalUtils.validatePortal(pos)`.
* Entity collision (server): find the receptacle via the chain; if none or no book → set to air; else `IItemPortalActivator.onPortalCollision(book, world, entity, pos)` (link).
* Portal mechanics: see §7.7.

### 3.8 Decay (`BlockDecay`)

* State `decay` = DecayType {BLACK(0,"black"), RED(1), GREEN(2), BLUE(3), PURPLE(4), YELLOW(5), WHITE(6)}. Material SAND, sand sounds, `tickRandomly=false` (ticked by the `EffectExtraTicks` instability effect and normal block ticks), no drops, pick-block gives the sub-item. Green and Yellow have models/items but **no handler** (fallback to BLACK behaviour).
* Delegates `updateTick`, `onBlockAdded`, `onBlockDestroyedByPlayer`, `getExplosionResistance`, `getBlockHardness`, `onEntityWalk/onEntityCollidedWithBlock` to a `DecayHandler` per type.
* Base handler: `onBlockAdded` outside a Mystcraft Age → block removed (air). Default explosion resistance 2.5, hardness 0.5.

| Type | Pulse (random tick) | Conversion difficulty (`rand.nextInt(d)==0` converts neighbour) | Hardness | Expl. resist | Contact |
|---|---|---|---|---|---|
| **Black** | 1/10: `decay`: corrupt 4 horizontal neighbours, set block below to air, and drop *this* block as an `EntityFallingBlock`; else 1/5: corrupt 4 horizontal neighbours. `corrupt(pos)`: liquid → air; non-air → black decay. `onBlockAdded`: if instability enabled in age: if block below is black decay → remove self and drop; if block above is black decay → remove self and drop the one above. | – | 0.5 | 2.5 | – |
| **Red** (spreading) | each pulse tries all 6 neighbours | air → 20; else `max(1, explosionResistance)` (clamped to 1000; negative→1000) | 1.0 | 10 | – |
| **Blue** (spreading) | 6 neighbours | air → 20; else `max(1, (int)hardness*2)` (hardness clamp 0..1000) | 5.0 | 2.0 | – |
| **Purple** (spreading) | 6 neighbours | air → 8; decay → 5; liquid → 3; else `max(1,(int)(2*hardness + explosionResist)) * 10` | 50 | 100 | – |
| **White** (spreading) | 6 neighbours | air → 50; decay → 1; anything else → 1 (converts everything) | 50 | 100 | Entity contact: 1 magic damage |

Spreading handlers never convert a neighbour that already is the same decay state.

### 3.9 Link Modifier (`BlockLinkModifier` / `TileEntityLinkModifier`)

* State `facing` horizontal. Material IRON, metal sound. Right-click → GUI `LINK_MODIFIER`. Break drops contents. Tile = 1-slot book holder (yaw quantum 90°).
* Tile operations (via GUI): rename book, get/set link flags on the book NBT, get/set `Props.Seed` (Descriptive Books only), read target dimension id, detect dead link (`isDimensionDead || !checkDimensionUUID`), and **recycle dimension** (`DimensionUtils.markDimensionDead(dimId)`).
* No recipe (creative/admin tool).

### 3.10 Writing Desk (`BlockWritingDesk` / `TileEntityDesk`)

> **Reborn revision (world-building plan §4):** the desk has **no notebook tabs**. Its target slot takes only a **Collation Folder**; paper, an ink container and the inkwell stay. The writing surface lists the symbols the *player* knows (§2.2), grouped by category under tabs (All, Terrain, Biomes, Sky, Weather, Features, Materials, Effects, Modifiers) with a search box; clicking a primary symbol writes a copy onto a fresh page in the folder (one paper, 50 mB ink; the page is a draft until the folder leaves the desk); clicking a modifier **attaches** it to the page selected in the folder strip when that page's symbol takes it (`AgeSymbol.takes`; 50 mB ink, no paper); right-click on a strip page removes its last modifier. `WritingDeskBlockEntity.writeSymbol` / `attachModifier` / `detachLastModifier` refuse unknown symbols, modifiers as standalone pages and non-matching slots. A **Scholar's Writing Desk** (`mystcraft:scholars_writing_desk`, creative only, no recipe; `scholar` flag on the block entity, full shelves in the renderer) offers every registered symbol. Books are bound at the Book Binder from the folder's pages and are no longer edited at the desk.

* States: `facing` horizontal (default NORTH), `istop`, `isfoot`. Legacy meta = `top<<3 | foot<<2 | horizontalIndex`. Render type ENTITYBLOCK_ANIMATED (TESR `ModelWritingDesk`, texture `entity/desk.png`, shows backboard when `hasTop()`, and paper count). Only the head block (not top, not foot) has the tile entity.
* Bounding box: full cube for base blocks; top blocks are 0.75 high and half-width on the side facing the desk (`dirInt 0: xmin=0.5; 1: zmin=0.5; 2: xmax=0.5; 3: zmax=0.5`).
* Neighbour change: top (non-foot) removed (dropping the backboard item) if the block below is not a desk; foot removed if head missing; head removed if foot missing (`headFootMap = {{0,1},{-1,0},{0,-1},{1,0}}` indexed by horizontal index).
* Creative harvest of a base block also removes the two top blocks.
* Right-click any desk block → GUI `WRITING_DESK` (tile resolved through top/foot to head).
* Drops: base → `writingdesk` meta 0, top → meta 1. Pick block likewise.
* **Tile inventories**:
  * Main (`IOInventory`, 4 slots; slot 1 is the only automation input): 0 `slot_wrt` target (an `IItemWritable`/`IItemRenameable`/`IItemPageAcceptor`, count 1), 1 `slot_pap` paper, 2 `slot_ctn` fluid container in (must contain a permitted ink), 3 `slot_out` empty container out.
  * Tabs (`IOInventory`, 25 misc slots): notebooks — items that are `IItemPageCollection` or `IItemWritable` (folders, portfolios, books, pages).
  * Inkwell: `FluidTankFiltered` capacity 1000 mB, permitted fluids = `validInks`. Fluid capability exposed.
  * NBT: `fluid`, `items`, `notebooks`.
* Tick (server): if a container is in slot 2: try to fill the tank from it (whole container amount must fit; empty container goes to slot 3 if mergeable) else try to fill the container from the tank (1000 mB).
* `writeSymbol(player, symbol)` (server): requires ink ≥ `inkcost` (50 mB). If target slot empty and paper present, move one paper as a blank page into the target slot. If target is `IItemWritable` and `writeSymbol` succeeds → drain 50 mB, trigger advancement `writing_desk_write`. Else if paper present and target is `IItemPageAcceptor`: create a symbol page from one paper and add it to the target; on success drain 50 mB, shrink paper, trigger advancement.
* `link(entity)`: activates the book in the target slot.
* `getTargetString/setBookTitle`: rename via `IItemRenameable`.
* Page moving helpers: `removePageFromSurface(tab, index|page)`, `addPageToTab`, `placePageOnSurface(tab, page, index)` — used by the container messages.
* Render bounding box (−1,0,−1)-(2,2,2).
* Villages: the Archivist house places a desk when `generation.villageDeskGen` is true.

### 3.11 Star Fissure (`BlockStarFissure` / `TileEntityStarFissure`)

* Material PORTAL, light 0.4, unbreakable, non-collidable, does not suffocate, no drops, bounding box (0,0,0)-(1,0.1,1). No block updates react to neighbours.
* Entity collision: builds a link info clone of the default: `Dimension = homeDimension` (config, default 0), flags `Natural=true`, `External=true`, property `Sound = mystcraft:linking.link-fissure`, `SpawnYaw = entity.rotationYaw`; posts `StarFissureLinkEvent`; `LinkController.travelEntity`. Since no spawn is set the destination world's spawn point is used.
* Rendering: TESR draws 8 layered end-portal-style planes (`end_sky` then `end_portal` textures, tex-gen animated) on the top and bottom faces; render distance 256 blocks (65536 sq).

### 3.12 Black Ink fluid block (`BlockFluidInk`)

* Forge classic fluid block, light opacity 3, treated as water for `isEntityInsideMaterial` (so players "swim"). Fog colour handled like water. The original **cancelled** filling a bucket or glass bottle from the ink block (`FillBucketEvent`, `RightClickItem`). **Reborn revision:** ink is collectable — an empty bucket picks up a source block as a Black Ink Bucket (vanilla `BucketPickup`), and a glass bottle scoops a source block into an Ink Vial (`CommonEvents.scoopIntoVial`); flowing ink yields nothing.
* Instability factors for the ink fluid: (1, 0). Fluid symbol default card rank 4.
---

## 4. Symbols

### 4.1 Symbol system fundamentals

* A symbol is an `IAgeSymbol` registry entry (Forge registry `mystcraft:symbol_registry`, modifiable, not saved). Required members: `registerLogic(AgeDirector, long seed)`, `instabilityModifier(int countOfThisSymbolInAge)`, `generatesConfigOption()`, `getLocalizedName()`, `getPoem()` (4 words).
* Registration (`SymbolManager.tryAddSymbol`):
  1. If `generatesConfigOption()`, read `symbols.cfg` category `symbol.<domain>` key `<path lowercased, spaces→_>.enabled` (default true); if false, do not register.
  2. Reject if the id has a remapping (§19.4) or is blacklisted (IMC).
  3. **Profile** the symbol by calling `registerLogic` with a `SymbolProfiler` director (records which interfaces the symbol registers, which modifiers it sets/pops, etc.). Exceptions blacklist the symbol and record it in "errored symbols" (shown in a startup warning GUI). Profiling data is used to find fallback symbols (`findAgeSymbolsImplementing(IBiomeController.class)` etc.).
* Localized name default: `myst.symbol.<path lowercase>.name`. Some modifier symbols compute names in code (see table).
* Words: `WordData` defines ~65 fictional words each mapped to component indices into `textures/symbolcomponents.png` (512×512, 8×8 grid of 64×64 glyphs). Words: Balance, Believe, Change, Chaos, Civilization, Constraint, Contradict, Control, Convey, Creativity, Cycle, Dependence, Discover, Dynamic, Elevate, Encourage, Energy, Entropy, Ethereal, Exist, Explore, Flow, Force, Form, Future, Growth, Harmony, Honor, Infinite, Inhibit, Intelligence, Love, Machine, Merge, Momentum, Motion, Mutual, Nature, Nurture, Possibility, Power, Question, Rebirth, Remember, Resilience, Resurrect, Sacrifice, Society, Spur, Static, Stimulate, Survival, Sustain, System, Time, Tradition, Transform, Weave, Wisdom, Void, Chain, Celestial, Image, Terrain, Order; aliases Modifier=Transform, Environment=Survival, Structure=Static, Ore=Machine, Sea=Flow. Numbers "0".."25" are also registered as words (used on notebook tabs). Component lists per word are in `WordData.init` (e.g. Nature = {5,6,8,10,11,12,15,16,17,22}). Unknown words get a deterministic random glyph (3–12 components from indices 4..23, seeded by `hashCode`). Each registered word also registers a banner pattern `mystcraft_<word>`.
* **Card rank** (`setCardRank`): integer rarity tier (0 = very common … 5 = rarest; `null` = never generated/traded). Item weight per rank is computed once at post-init (`buildCardRanks`): iterate ranks from highest to lowest, `weight=1` for the highest rank present, then for each lower rank `weight = max(weight+1, lastTotal/count + 1)` where `lastTotal = count(prevRank)*weight(prevRank)`. Result: each rank has at least the total weight of the rank above it. Default max stack in treasure per rank: rank0→16, 1→8, 2→4, 3→2, else 1 (overridable per symbol). Symbol tradeable iff weight>0 (overridable). Default trade price = `max(1, 12*rank)` emeralds (overridable via `setSymbolTradeItems`); biome symbols are set to 1 emerald.
* **Instability contribution**: `instabilityModifier(count)` summed by the AgeController each time the symbol is added (count = occurrences so far).

### 4.2 The modifier stack (AgeDirector)

Symbols communicate through named **modifiers** held by the director while an Age is being built (in page order):

| Modifier id | Set by | Consumed by | Value type |
|---|---|---|---|
| `angle` | Direction symbols | Sun, Moon, Stars, Rainbow | float degrees |
| `phase` | Phase symbols | Sun, Moon | float degrees |
| `wavelength` (FACTOR) | Length symbols | Sun, Moon, Stars, Gradient | float multiplier |
| `color` | Colour symbols | Gradient, Grass/Foliage/Water colour, any `popGradient` fallback | `Color` |
| `gradient` | Gradient symbol | Sky/Fog/Cloud colour, Stars, Lightning, Horizon | `ColorGradient` |
| `sunset` | Sunset Color (ColorHorizon) | Sun, Moon; global fallback for horizon | `ColorGradient` |
| `blocklist` | Block symbols, No Seas | Terrain generators, Spheres, Tendrils, Spikes, Obelisks, Lakes, Crystal Formation, Floating Islands | list of `BlockDescriptor` (usable categories) |
| `biomelist` | Biome symbols | Biome controllers, Floating Islands | list of `Biome` |

* `popModifier(id)` removes and returns the modifier (returns an empty modifier if absent); `setModifier(id, value)` replaces (if a modifier already occupied that id, its **dangling** value is added to symbol instability).
* Every modifier carries a `dangling` cost: default 100; blocklist adds 50 per block pushed; biomelist 100 per biome; sunset gradient 0. After all symbols are processed, remaining un-consumed modifiers add their dangling cost to instability. `ModClear` clears all modifiers adding `dangling × 0.20` each.
* Blocks are pushed to the *front* of the block list; `popBlockMatching(categories…)` scans from the front and removes the first block usable for any of the categories. Biomes are pushed to the end and popped from the end (LIFO).
* Colour combination rules: a second Colour symbol averages with the pending colour (component-wise mean). Angle/Phase symbols average with the pending value on the circle (`averageAngles`); Length symbols average linearly. Gradient pushes the pending colour with interval = pending `wavelength` (default 1.0) onto the pending gradient (or a new one). `popGradient(director, r,g,b)` = pop gradient; if none, new gradient with the pending `color`; if still empty push the default colour.
* `ColorGradient.getColor(t)`: `t = t mod totalLength`; walk intervals; linear interpolate between the colour whose interval contains t and the next (wrapping). Callers pass `worldTime / 12000` so an interval of 1.0 = half a day.

### 4.3 Complete symbol list

Column key: **Rank** = card rank (`–` = null, never generated randomly / traded); **Words** = 4-word poem; **Grammar** = rule(s) `PARENT → [tokens…] (rank r)`; the last token of every rule is the symbol itself and the earlier tokens are the modifiers the grammar generates before it. `Inst.` = `instabilityModifier(count)`.

#### 4.3.1 Visual / colour symbols (category `Visual`)

| Id (`mystcraft:`) | Display name | Rank | Words | Grammar | Logic |
|---|---|---|---|---|---|
| ColorCloud | Cloud Color | 1 | Image, Entropy, Believe, Weave | `Visual → Gradient, ColorCloud` (3) | Registers dynamic CLOUD colour = gradient(time/12000); default colour white (1,1,1). |
| ColorCloudNat | Natural Cloud Color | 1 | Image, Entropy, Believe, Nature | `Visual → ColorCloudNat` (2) | Cloud colour constant white. |
| ColorFog | Fog Color | 1 | Image, Entropy, Explore, Weave | `Visual → Gradient, ColorFog` (3) | Dynamic FOG colour = gradient(time/12000); default (0.7529,0.8471,1.0). Pure black is replaced by (0.0001,0.0001,0.0001). |
| ColorFogNat | Natural Fog Color | 1 | Image, Entropy, Explore, Nature | `Visual → ColorFogNat` (2) | Vanilla fog: `f=clamp(cos(angle·2π)·2+0.5,0,1)`; rgb = (0.7529·(f·0.94+0.06), 0.8471·(f·0.94+0.06), 1.0·(f·0.91+0.09)). |
| ColorFoliage | Foliage Color | 1 | Image, Growth, Elevate, Weave | `Visual → Color, ColorFoliage` (3) | Static FOLIAGE colour = pending `color` (null if none). |
| ColorFoliageNat | Natural Foliage Color | 1 | Image, Growth, Elevate, Nature | `Visual → ColorFoliageNat` (2) | Vanilla foliage colour from biome temperature/rainfall (biome default Plains). |
| ColorGrass | Grass Color | 1 | Image, Growth, Resilience, Weave | `Visual → Color, ColorGrass` (3) | Static GRASS colour. |
| ColorGrassNat | Natural Grass Color | 1 | Image, Growth, Resilience, Nature | `Visual → ColorGrassNat` (2) | Vanilla grass colour. |
| ColorSky | Sky Color | 1 | Image, Celestial, Harmony, Weave | `Visual → Gradient, ColorSky` (3) | Dynamic SKY colour = gradient(time/12000) × `clamp(cos(angle·2π)·2+0.5, 0..1)`; default white. |
| ColorSkyNat | Natural Sky Color | 1 | Image, Celestial, Harmony, Nature | `Visual → ColorSkyNat` (2) | Vanilla: HSB(0.6222−t·0.05, 0.5+t·0.1, 1.0) with t=clamp(temp/3,−1,1), scaled by the same daylight factor. |
| ColorSkyNight | Night Sky Color | 1 | Image, Celestial, Contradict, Weave | `Visual → Gradient, ColorSkyNight` (3) | As ColorSky but multiplied by `1 − daylightFactor` (visible at night). |
| ColorWater | Water Color | 1 | Image, Flow, Constraint, Weave | `Visual → Color, ColorWater` (3) | Static WATER colour. |
| ColorWaterNat | Natural Water Color | 1 | Image, Flow, Constraint, Nature | `Visual → ColorWaterNat` (2) | Biome water colour multiplier. |
| NoHorizon | Boundless Sky | 1 | Celestial, Inhibit, Image, Void | `Visual → NoHorizon` (2) | `setHorizon(0)`, `setDrawHorizon(false)`, `setDrawVoid(false)`. |
| Rainbow | Rainbow | 1 | Celestial, Image, Harmony, Balance | `Visual → Angle, Rainbow` (4) | Celestial doodad: renders a rainbow arc (`RenderRainbow.renderRainbow(0, 50)`) rotated by −angle (random 0–360 if none). Display list cached when `fast_rainbows`. Does not provide light. |

Multiple colour providers of the same kind are averaged. When no provider exists the world provider falls back to the vanilla formula.

#### 4.3.2 Celestial symbols (`Sun`, `Moon`, `Starfield`, `Doodad`)

| Id | Display | Rank | Words | Grammar | Logic |
|---|---|---|---|---|---|
| SunNormal | Normal Sun | 2 | Celestial, Image, Stimulate, Energy | `Sun → Sunset, Period, Angle, Phase, SunNormal` (1) | Pops `wavelength`, `angle`, `phase`, `sunset`. **Provides light.** period = (wavelength or `0.4·rand+0.8`) × 24000 ticks; angle = −(angle or random 360); offset = phase/360 (or random; if period==0 offset = rand/2+0.25) − 0.5. Altitude angle `f = ((time mod period)+partial)/period + offset` wrapped to 0..1, then eased: `f1=f; f = 1−(cos(f·π)+1)/2; f = f1 + (f−f1)/3`. Renders 30-unit sun quad (vanilla sun texture) rotated by angle then `f·360` about X; then horizon (sunset) colours. `getTimeToDawn = period·|0.75−offset| − current (mod period)`. |
| SunDark | Dark Sun | 1 | Celestial, Void, Inhibit, Energy | `Sun → SunDark` (3) | Dummy (no logic). |
| MoonNormal | Normal Moon | 1 | Celestial, Image, Cycle, Wisdom | `Moon → SunsetUncommon, Period, Angle, Phase, MoonNormal` (1) | Like the sun but period default `1.8·rand+0.2` days, size 20, moon phase = `(time/period) mod 8` (vanilla moon_phases texture), no light, horizon rendered at alpha 0.3 only if a sunset gradient was given. |
| MoonDark | Dark Moon | 1 | Celestial, Void, Inhibit, Wisdom | `Moon → MoonDark` (3) | Dummy. |
| StarsNormal | Normal Stars | 1 | Celestial, Harmony, Ethereal, Order | `Starfield → Gradient, Period, Angle, StarsNormal` (1) | Pops wavelength, angle, gradient (default white). period = (wavelength or `1.8·rand+0.2`) × 240000. 1500 vanilla-style stars (seeded 10842) in a display list; colour = gradient(time/12000), alpha = world star brightness × (1−rain); rotates with the same eased period formula. |
| StarsTwinkle | Twinkling Stars | 1 | Celestial, Harmony, Ethereal, Entropy | `Starfield → Gradient, Period, Angle, StarsTwinkle` (2) | 10 layers × 100 stars each with random time offsets; layer brightness `clamp(1 − (cos(((t+offset) mod 100)/100·2π)·2 + 0.25), 0, 1)`. |
| StarsEndSky | Ender Starfield | 1 | Celestial, Image, Chaos, Weave | `Starfield → Gradient, StarsEndSky` (4) | Renders the End sky box (6 faces, `end_sky.png`, tinted by gradient(time/12000), default 0x282828 = (0.156,0.156,0.156)); also `setHorizon(0)`, no horizon, no void. |
| StarsDark | Dark Stars | 1 | Celestial, Void, Inhibit, Order | `Starfield → StarsDark` (3) | Dummy. |

`calculateCelestialAngle(time)`: over all light-providing celestials, take the lowest and highest altitude (wrapped 0..1), starting both at 0.5; if `(1−highest) < lowest` return highest else lowest. With no suns → constant 0.5 (permanent night lighting). `getTimeToSunrise` = minimum of suns' `getTimeToDawn`; default `24000 − time mod 24000` (used when all players sleep).

#### 4.3.3 Lighting (`Lighting`)

| Id | Display | Rank | Words | Grammar | Inst. | Logic (`generateLightBrightnessTable`, `scaleLighting`) |
|---|---|---|---|---|---|---|
| LightingNormal | Normal Lighting | 2 | Ethereal, Dynamic, Cycle, Balance | `Lighting → LightingNormal` (1) | 0 | Vanilla: `f1=1−i/15; t[i]=(1−f1)/(f1·3+1)`; scale = identity. |
| LightingBright | Bright Lighting | 3 | Ethereal, Power, Infinite, Spur | (2) | +500 | `t[i] = ((1−f1)/(f1·3+1))·0.75 + 0.25`; `scale(v) = v + (15−v)/2`. |
| LightingDark | Dark Lighting | 3 | Ethereal, Void, Constraint, Inhibit | (2) | 0 | `t[i] = vanilla/2`; `scale(v)=v/2`. |

#### 4.3.4 Weather (`Weather`)

| Id | Display | Rank | Words | Grammar rank | Logic |
|---|---|---|---|---|---|
| WeatherNorm | Normal Weather | 2 | Sustain, Dynamic, Tradition, Balance | 1 | Cycling controller: rain duration base 12000 + rand(12000); rain cooldown base 12000 + rand(168000); thunder duration base 3600 + rand(12000); thunder cooldown base 12000 + rand(168000). |
| WeatherFast | Fast Weather | 3 | Sustain, Dynamic, Tradition, Spur | 2 | Half of normal: rain 6000+rand(6000) / cooldown 6000+rand(84000); thunder 1800+rand(6000) / 6000+rand(84000). |
| WeatherSlow | Slow Weather | 3 | Sustain, Dynamic, Tradition, Inhibit | 2 | Double: rain 24000+rand(24000) / 24000+rand(336000); thunder 7200+rand(24000) / 24000+rand(336000). |
| WeatherOff | No Weather | 3 | Sustain, Static, Stimulate, Energy | 2 | Toggleable: enabled ⇒ rain 0; disabled (toggle) ⇒ rain 1. |
| WeatherOn | Eternal Weather | 3 | Sustain, Static, Tradition, Stimulate | 2 | Toggleable: enabled ⇒ rain strength 1 (biome-native precipitation). |
| WeatherCloudy | Overcast | 3 | Sustain, Static, Believe, Motion | 2 | Rain strength 1 but rain & snow disabled in all biomes (dark sky, no precipitation). |
| WeatherRain | Eternal Rain | 3 | Sustain, Static, Rebirth, Growth | 2 | Rain 1, rain enabled, snow disabled; temperature floor 0.20. |
| WeatherSnow | Eternal Snow | 3 | Sustain, Static, Inhibit, Energy | 2 | Rain 1, rain+snow enabled; temperature ceiling 0.10. |
| WeatherStorm | Eternal Storm | 3 | Sustain, Static, Nature, Power | 2 | Rain 1, thunder 1, snow disabled, temp floor 0.20; lightning: per chunk tick 1/100000 chance at a random column when raining+thundering and sky visible. |

Weather controller storage (`AgeData` compound `weather`): cycling controllers store `raining`, `thundering`, `rain_counter`, `thunder_counter`; toggleable store `disabled`, `reset_counter` (12000 ticks after a toggle the controller re-enables). `updateRaining` each tick moves rain/thunder strength by ±0.01 toward target. `togglePrecipitation` (command `/toggledownfall`) sets `rain_counter=1` for cycling controllers or flips `disabled` for toggleables. `tick(chunk)` for cycling/toggleable base: when raining & thundering, 1/100000 per chunk tick spawn a vanilla lightning bolt at a random rain-exposed column.

#### 4.3.5 Biome distribution (`BiomeController`)

| Id | Display | Rank | Words | Grammar | Logic |
|---|---|---|---|---|---|
| BioConNative | Native Biome Distribution | 3 | Constraint, Nature, Tradition, Sustain | `BiomeController → BioConNative` (1) | Wraps a vanilla `BiomeProvider` (WorldType DEFAULT) with the Age seed. |
| BioConSingle | Single | 3 | Constraint, Nature, Infinite, Static | `→ Biome, BioConSingle` (1) | Pops one biome (random biome if none); whole world is that biome. |
| BioConTiled | Tiled | 3 | Constraint, Nature, Chain, Contradict | `→ Biomes, Biome, BioConTiled` (2) | Pops all biomes (fill to ≥2 randomly). Biome at (x,z) = list[((x>>4)+(z>>4)) mod n] (16-block diagonal stripes/checkerboard). |
| BioConGrid | Grid-form | 3 | Constraint, Nature, Chain, Mutual | `→ Biomes, Biome, BioConGrid` (2) | Same indexing as Tiled but `getBiomesForGeneration` samples at coordinates ×4 (generation-scale grid). |
| BioConTiny | Tiny | 3 | Constraint, Nature, Weave, "Tiny" | `→ Biomes, Biome, Biome, BioConTiny` (2) | GenLayer controller, zoom scale 0. |
| BioConSmall | Small | 3 | …, "Small" | (2) | zoom 1. |
| BioConMedium | Medium | 3 | …, "Medium" | (1) | zoom 2. |
| BioConLarge | Large | 3 | …, "Large" | (2) | zoom 3. |
| BioConHuge | Huge | 3 | …, "Huge" | (2) | zoom 4. |

GenLayer controller (`SymbolBiomeControllerLarge.BiomeController(zoom, biomes)`): pops all biomes and pads to ≥3 random ones. Layer stack: `Island(1) → FuzzyZoom(2000) → [magnify ×0] → GenLayerBiomeMyst(200, allowedBiomes: picks uniformly from the allowed list per cell) → magnify ×2 (1000) → zoom ×zoomscale (1000+i) → Smooth(1000)`; index layer = `VoronoiZoom(10)`. Spawn biomes list = Forest, Plains, Taiga, TaigaHills, ForestHills, Jungle, JungleHills. Uses a `BiomeCache`.

#### 4.3.6 Terrain generators (`TerrainGen`)

All pop `blocklist` for a SEA block then a TERRAIN block (defaults water/stone). All set 128-height noise fields (world 256 tall; base generator fills y<128 only via 4×8×4 interpolation; flat fills 256).

| Id | Display | Rank | Words | Grammar | Logic |
|---|---|---|---|---|---|
| TerrainNormal | Standard World | 2 | Terrain, Form, Tradition, Flow | `TerrainGen → BlockTerrain, BlockSea, TerrainNormal` (1) | Vanilla-1.6-style overworld noise (octaves 16,16,8,10,16; scale 684.412; biome height/variation weighting with parabolic 5×5 field). Bedrock y ≤ rand(5). Sea level from director (63). |
| TerrainAmplified | Amplified Normal World | 3 | Terrain, Form, Tradition, Spur | (3) | Same with amplified biome heights (`h = 1+2h`, `v = 1+4v` when h>0). |
| TerrainFlat | Flat World | 3 | Terrain, Form, Inhibit, Motion | (2) | y=0 bedrock; y<groundLevel (64 default; averaged from biome base heights ×64+64 pushed by biome symbols) fill; y≤sealevel sea. |
| TerrainNether | Cave World | 4 | Terrain, Form, Constraint, Entropy | (3) | Nether-style noise (scale 684.412 / 2053.236, cos-shaped vertical density, closed top and bottom). Sets cloud height 200, horizon 128, sea level 32. |
| TerrainEnd | Island World | 4 | Terrain, Form, Ethereal, Flow | (3) | End-style noise; central island `distFactor = clamp(100 − dist·4, −100, 80)`; density crushed above mid-height and below y=8; no bedrock; sea block defaults to AIR; horizon 0, sea level 49, no horizon/void rendering. |
| TerrainVoid | Void World | 4 | Terrain, Form, Infinite, Void | `TerrainGen → TerrainVoid` (3) | Generates nothing. Cloud height 0, horizon 0, no horizon/void rendering. |

#### 4.3.7 Large features (`FeatureLarge`)

| Id | Display | Rank | Words | Grammar | Inst. | Logic |
|---|---|---|---|---|---|---|
| Caves | Caves | 2 | Terrain, Transform, Void, Flow | `FeatureLarge → Caves` (1) | 0 | `MapGenCavesMyst(seed, rate 15, size 40, AIR)`: per chunk in range 8, `nodes = rand(rand(rand(40)+1)+1)`, only if `rand(15)==0`; 1/4 chance of a large cave node; tunnels carve AIR (never bedrock; won't replace liquid with non-solid). |
| Tendrils | Tendrils | 3 | Terrain, Transform, Growth, Flow | `→ BlockStructure, Tendrils` (4) | 0 | Same cave algorithm but rate 15, size 18, placing the STRUCTURE block (default oak log) instead of air. |
| Skylands | Skylands | 3 | Terrain, Transform, Void, Elevate | `→ Skylands` (5) | 0 | Primer filter: every block at `y ≤ 76 + noise(x,z)` (7-octave noise) is removed; liquids above the cut are also removed. Cloud height 42.5, horizon 0. |
| FloatIslands | Floating Islands | 3 | Terrain, Transform, Form, Celestial | `→ Biome, BlockStructure, FloatIslands` (4) | 0 | Pops a biome (random if none) and STRUCTURE block (default stone). `MapGenFloatingIslands` rate 1/192 per chunk (range 5): one blob at y=150+rand(rand(50)+50) with scalar 12, squash 0.2, plus 40–51 sub-blobs (scale 1–4, squash 0.4) within ±20/±10/±20; surface replaced with the biome's top/filler blocks; those columns' biome ids are replaced with the island biome at chunk finalization. |
| HugeTrees | Huge Trees | 2 | Nature, Stimulate, Spur, Elevate | `→ HugeTrees` (2) | 0 | `WorldGenMystBigTree`: per chunk (range 8) 50% chance; trunk 2×2 logs from y=4..11 root up to blob at y∈[128,180], blob height 40–69; leaf nodes 4 per layer; roots count = height/4. |
| DenseOres | Dense Ores | 5 | Environment, Stimulate, Machine, Chaos | `→ DenseOres` (–) | 0 | Populator: coal ×20 (size 16, y 0–128), iron ×20 (8, 0–64), gold ×2 (8, 0–32), redstone ×8 (7, 0–16), diamond ×1 (7, 0–16), lapis ×1 (6, 0–16), emerald ×6 (1, 4–32), nether quartz ×10 (13 in netherrack, 10–256); posts `DenseOresEvent`. |
| FeatureLargeDummy | Lacking Large Features | 4 | Contradict, Chaos, Exist, Terrain | `FeatureLarges0 → FeatureLargeExt, FeatureLargeDummy` (5); `FeatureLargeExt → FeatureLargeDummy` (–) | 0 | No logic. |

#### 4.3.8 Medium features (`FeatureMedium`)

| Id | Display | Rank | Words | Grammar | Inst. | Logic |
|---|---|---|---|---|---|---|
| Villages | Villages | 3 | Civilization, Society, Harmony, Nurture | (1) | count>3 → +100 | Vanilla `MapGenVillage` (terrain pass + populate). |
| Strongholds | Strongholds | 3 | Civilization, Wisdom, Future, Honor | (1) | count>3 → +100 | Vanilla `MapGenStronghold`; locator "Stronghold". |
| Mineshafts | Mineshafts | 3 | Civilization, Machine, Motion, Tradition | (1) | count>3 → +100 | Vanilla `MapGenMineshaft`. |
| NetherFort | Nether Fortress | 3 | Civilization, Machine, Power, Entropy | (2) | count>3 → +100 | Vanilla `MapGenNetherBridge`; locator "Fortress". |
| Ravines | Ravines | 2 | Terrain, Transform, Void, Weave | (1) | 0 | `MapGenRavineMyst` (AIR): 1/50 per chunk, y = rand(rand(40)+8)+20, vanilla ravine shape (avoids water). |
| Dungeons | Dungeons | 2 | Civilization, Constraint, Chain, Resurrect | (2) | 0 | 8 vanilla `WorldGenDungeons` attempts per chunk at random y 0–255. |
| TerModSpheres | Spheres | 2 | Terrain, Transform, Form, Cycle | `→ BlockStructure, TerModSpheres` (3) | 0 | `MapGenSpheresMyst`: 5% per chunk (range 8), one node at y=32+rand(rand(192)+1), radius scalar 1–5, made of STRUCTURE block (default cobblestone). |
| GenSpikes | Spikes | 3 | Nature, Encourage, Entropy, Structure | `→ BlockStructure, GenSpikes` (3) | 0 | Populator 1/18 per chunk: at surface, if the whole base circle (width 1–4, r²≤w²+1) is supported: columns of height `rand(rand(6..37)+1)+1` of STRUCTURE block (default stone). |
| FeatureMediumDummy | Lacking Medium Features | 4 | Contradict, Chaos, Exist, Balance | `FeatureMediums0 → FeatureMediumExt, FeatureMediumDummy` (5); `FeatureMediumExt → FeatureMediumDummy` (–) | +1000 | – |

#### 4.3.9 Small features (`FeatureSmall`)

| Id | Display | Rank | Words | Grammar | Inst. | Logic |
|---|---|---|---|---|---|---|
| LakesSurface | Surface Lakes | 3 | Nature, Flow, Static, Elevate | `→ BlockFluid, LakesSurface` (1) | 0 | 1/4 per chunk (only if no earlier populator returned true): `WorldGenLakesAdv(FLUID block, default water)` at random y 0–255 (vanilla lake shape; fails near liquids). |
| LakesDeep | Deep Lakes | 3 | Nature, Flow, Static, Explore | `→ BLOCK_NONSOLID, LakesDeep` (1) | 0 | 1/8 per chunk: y = rand(rand(248)+8); only generated if y < sea level or 1/10; block = FLUID or GAS category (default lava). |
| Obelisks | Obelisks | 3 | Civilization, Resilience, Static, Form | `→ BlockStructure, Obelisks` (3) | 0 | 1/128 per chunk: 4×4 base layers dug down up to 5 until supported, then 2×2×12 pillar of STRUCTURE block (default obsidian). |
| CryForm | Crystalline Formations | 3 | Nature, Encourage, Growth, Structure | `→ BlockCrystal, CryForm` (3) | 0 | 1/15 per chunk: 1–3 lines from surface−2 at angle 15–155°, length 6–12, each step drawing a 7-block "plus" of the CRYSTAL block (default `blockcrystal`). |
| StarFissure | Star Fissure | 3 | Nature, Harmony, Mutual, Void | `→ StarFissure` (3) | 0 | Only in the spawn chunk: repeatedly generates a fissure (10–17 rows, widths random-walked) of `blockstarfissure` at y=0, clearing everything above each fissure block up to world height. |
| FeatureSmallDummy | Lacking Small Features | 5 | Contradict, Chaos, Exist, Form | `FeatureSmalls0 → FeatureSmallExt, FeatureSmallDummy` (–); `FeatureSmallExt → FeatureSmallDummy` (–) | +2000 | – |

#### 4.3.10 Environmental effects (`Effect`) — grammar rank null (never random)

| Id | Display | Rank | Words | Grammar | Inst. | Logic |
|---|---|---|---|---|---|---|
| EnvAccel | Accelerated | 3 | Environment, Dynamic, Change, Spur | `Effect → EnvAccel` | +1000 | `EffectExtraTicks()`: per chunk tick, for each block-storage section that needs random ticks, 3 extra random block ticks. |
| EnvExplosions | Spontaneous Explosions | 3 | Environment, Sacrifice, Power, Force | `Effect → EnvExplosions` | −500 | 1/1000 per chunk tick: explosion power 3, flaming, smoking at random column, y random 0–255 (+1). |
| EnvLightning | Lightning | 3 | Environment, Sacrifice, Power, Energy | `Effect → Gradient, EnvLightning` | −500 (count≤1), else 0 | Pops gradient (coloured bolts if present): when raining+thundering 1/5000 per chunk tick, else 1/100000; spawns `EntityLightningBoltAdv` at precipitation height and sends `MPacketSpawnLightningBolt` within 512 blocks. Colour = gradient(totalTime/12000). |
| EnvMeteor | Meteors | 3 | Environment, Sacrifice, Power, Momentum | `Effect → EnvMeteor` | −1000 | 1/50000 per chunk tick: `EntityMeteor(scale 1, penetration 0)` at y=500 over a random column with motion (gauss·0.25, −2−rand·2, gauss·0.25). |
| EnvScorch | Scorched Surface | 3 | Environment, Sacrifice, Power, Chaos | `Effect → EnvScorch` | −500 (count≤1) | `EffectScorched(1)`: 1/10 per chunk tick pick a random entity in the chunk; if it can see the sky set it on fire for `4·level` seconds. |

#### 4.3.11 Misc

| Id | Display | Rank | Words | Grammar | Logic |
|---|---|---|---|---|---|
| PvPOff | Anti-PvP | – | Chain, Chaos, Encourage, Harmony | none | `setPvPEnabled(false)`: player-vs-player `LivingAttackEvent` cancelled in the Age. |
| NoSea | No Seas | 2 | Modifier, Constraint, Flow, Inhibit | `BlockSea → NoSea` (2) | Pushes an AIR block usable as SEA. |
| ModClear | Clear Modifiers | 0 | Contradict, Transform, Change, Void | none | `clearModifiers()` (adds 20% of dangling costs). |

#### 4.3.12 Modifier symbols

| Id | Display (computed) | Rank | Words | Grammar | Logic |
|---|---|---|---|---|---|
| ModNorth / ModEast / ModSouth / ModWest | "North Direction" etc. | 0 | Modifier, Flow, Motion, {Control, Tradition, Chaos, Change} | `AngleBasic → ModX` (1) | angle 0 / 90 / 180 / 270, averaged with pending angle on the circle. |
| ModEnd / ModRising / ModNoon / ModSetting | "Nadir Phase", "Rising Phase", "Zenith Phase", "Setting Phase" | 0 | Modifier, Cycle, System, {Rebirth, Growth, Harmony, Future} | `PhaseBasic → ModX` (1) | phase 0 / 90 / 180 / 270 (averaged). |
| ModZero / ModHalf / ModFull / ModDouble | "Zero Length", "Half Length", "Full Length", "Double Length" | 0 | Modifier, Time, System, {Inhibit, Stimulate, Balance, Sacrifice} | `PeriodBasic → ModX` (ModZero rank 2, others 1) | wavelength 0 / 0.5 / 1 / 2 (linear average with pending). |
| ModGradient | Gradient | 1 | Modifier, Image, Merge, Weave | `GradientBasic → Color, Period, ModGradient` (1) | Push pending colour with interval = pending wavelength (default 1) onto pending gradient. |
| ColorHorizon | Sunset Color | 0 | Modifier, Image, Celestial, Change | `Sunset → Sunset_Ext, Gradient, ColorHorizon` (2) | Appends popped gradient (or colour) onto the `sunset` gradient modifier (dangling 0). |
| ModColor* (16) | "<Colour> Color" | 0 | Modifier, Image, Weave, <id> | `ColorBasic → ModColorX` (1) | Sets/averages `color`. Values: Maroon (0.5,0,0), Red (1,0,0), Olive (0.5,0.5,0), Yellow (1,1,0), DarkGreen (0,0.5,0), Green (0,1,0), Teal (0,0.5,0.5), Cyan (0,1,1), Navy (0,0,0.5), Blue (0,0,1), Purple (0.5,0,0.5), Magenta (1,0,1), Black (0,0,0), Grey (0.5,0.5,0.5), Silver (0.75,0.75,0.75), White (1,1,1). |
| Biome<numericId> | "<Biome name> Biome" (`myst.symbol.biome.wrapper`) | 2 (Sky/End biome: null) | Nature, Nurture, Encourage, `<BiomeClassName><id>` | `Biome → BiomeN` (1; End biome null) | Generated for every registered biome at biome-registry time; pushes the biome onto `biomelist` and `setAverageGroundLevel(baseHeight·64+64)`. Trade item: 1 emerald. Excluded from random selection only if rule rank is null. |
| ModMat_<block>_<meta> | "<Block name> Block" (`myst.symbol.block.wrapper`; " Block" suffix stripped) | per table | Modifier, Constraint, <word>, `<id path>` | one rule per usable category: `Block<Cat> → ModMat_x` (rank) | Pushes `BlockDescriptor` with the usable categories. Id domain = block's mod id (minecraft→mystcraft). |

#### 4.3.13 Built-in block symbols (`ModSymbolsModifiers`)

Categories: TERRAIN, STRUCTURE, SOLID, ORGANIC, CRYSTAL, SEA, FLUID, GAS, ANY. Word aliases: Terrain, Structure(=Static), Ore(=Machine), Chain, Sea(=Flow).

| Block(meta) | Word | Card rank | Category ranks |
|---|---|---|---|
| dirt 0 | Terrain | 2 | TERRAIN 4, STRUCTURE 2, SOLID 1 |
| stone 0 | Terrain | 2 | TERRAIN 1, STRUCTURE 2, SOLID 1 |
| stone 1,3,5 (granite, diorite, andesite) | Terrain | 2 | TERRAIN 2, STRUCTURE 2, SOLID 1 |
| stone 2,4,6 (polished) | Structure | 2 | TERRAIN 5, STRUCTURE 1, SOLID 1 |
| sandstone 0 | Terrain | 2 | TERRAIN 2, STRUCTURE 1, SOLID 1 |
| netherrack 0 | Terrain | 2 | TERRAIN 3, STRUCTURE 2, SOLID 2 |
| end_stone 0 | Terrain | 3 | TERRAIN 4, STRUCTURE 3, SOLID 3 |
| nether_brick 0 | Structure | 2 | SOLID 2, STRUCTURE 2 |
| log 0–3, log2 0–1 | Structure | 2 | SOLID 1, ORGANIC 1, STRUCTURE 1 |
| diamond_ore | Ore | 5 | SOLID 6, STRUCTURE 6 |
| gold_ore | Ore | 4 | SOLID 5, STRUCTURE 5 |
| iron_ore | Ore | 3 | SOLID 4, STRUCTURE 4 |
| coal_ore | Ore | 3 | SOLID 4, STRUCTURE 4 |
| redstone_ore | Ore | 4 | SOLID 5, STRUCTURE 5 |
| lapis_ore | Ore | 3 | SOLID 4, STRUCTURE 4 |
| emerald_ore | Ore | 4 | SOLID 5, STRUCTURE 5 |
| ice | Chain | 2 | SOLID 3, FLUID 3, SEA 2, STRUCTURE 3, CRYSTAL 3 |
| packed_ice | Chain | 2 | SOLID 3, FLUID 3, TERRAIN 3, SEA 3, STRUCTURE 3, CRYSTAL 3 |
| glass | Chain | 2 | SOLID 3, STRUCTURE 3, CRYSTAL 3 |
| snow (block) | Chain | 2 | SOLID 3, STRUCTURE 3, CRYSTAL 3 |
| obsidian | Chain | 3 | SOLID 4, TERRAIN 4, STRUCTURE 3, CRYSTAL 3 |
| glowstone | Chain | 3 | SOLID 4, STRUCTURE 4, CRYSTAL 4 |
| quartz_ore | Chain | 3 | SOLID 4, STRUCTURE 4, CRYSTAL 4 |
| mystcraft:blockcrystal | Chain | 3 | SOLID 4, STRUCTURE 4, CRYSTAL 4 |
| water 0 | Sea | 2 | FLUID 1, SEA 1 |
| lava 0 | Sea | 3 | FLUID 2, SEA 2 |

#### 4.3.14 Fluid symbols (auto-generated at biome-registry time for every registered Forge fluid)

Skipped: gaseous or negative-density fluids, blacklisted (IMC `blacklistfluid`), fluids with no block, and vanilla water/lava. Block state = `getMaxRenderHeightMeta` state. Symbol word = Sea; card rank & grammar rank & sea-ban & instability factors come from `balance.cfg` category `fluids` keys `<fluid.unlocalizedName lowercase>.cardrank`, `.grammar`, `.seabanned`, `.instability.factor_accessibility`, `.instability.factor_flat` with defaults (card 4, grammar 4, seabanned false, factor1 1.0, factor2 0.25) and a built-in table of known modded fluids (e.g. `fluid.mobessence` 98/7, `fluid.ender` banned 72/6, `fluid.redstone` banned 72/6, molten metals banned 8/2, `fluid.cryotheum` −2/−1, `fluid.mana` −6/−2, `fluid.fluxgoo` banned −12/−3, `fluid.myst.ink.black` 1/0 …). Categories: FLUID always, SEA unless sea-banned (GAS instead if gaseous). Instability factors are registered for the fluid block.

### 4.4 Grammar (random Age generation)

> **Reborn revision (the grammar is gone):** random Age completion is done by the **Age blueprint** (`AgeBlueprint`, `docs/impl/WORLD_BUILDING_PLAN.md`). Every symbol belongs to one `SymbolCategory` - Terrain, Biomes, Biome layout, Lighting, Celestials, Sky colours, World colours, Weather, Structures, Features, Effects, plus the attached-only Materials and Modifiers. At the first link the blueprint (1) leaves every category the author wrote anything in alone, (2) fills the required categories (terrain, biome layout, biomes unless the layout is Native, lighting, celestials with exactly one sun) and (3) gives optional categories their configured defaults and, by chance, random extras, all from `RandomSource.create(seed ^ FILL_SALT)` with one stream per category. Hard gates: nether biomes / nether fortress need nether terrain, end biomes need end terrain, Single takes one biome, other layouts at least two, void terrain is never picked, a dark sun is only kept together with bright lighting. The discovered symbols' instability stays within `fill.instabilityBudget` (default 500). Every number is in `mystcraft-worldbuilding.toml` (`WorldBuildingConfig`). The pages are then **organised** (link panel, categories in build order, player pages before discovered ones, blanks last) and **flattened** (each page: its modifiers then its symbol) into `AgeData.symbols`; the book carries the organised list, so it is a complete description of the Age. The dummies ("Lacking ... Features") and Clear Modifiers no longer exist. `[blueprint]` log lines record every fill and every pick dropped for the budget. §4.4.1-4.4.3 below describe the original and are kept for reference only.

#### 4.4.1 Rule model

A `Rule(parent, values[], rank)` is a CFG production. Rank ⇒ weight: per parent token, weights are built like card ranks (`buildRankWeights`: highest rank weight 1, each lower rank ≥ total weight of the rank above +1). Rank `null` ⇒ weight 0 (rule is never chosen when expanding randomly but is available for connecting written symbols).

#### 4.4.2 Core rules (`GrammarRules`)

```
Age(root) → TerrainGen BiomeController Weather Lighting Spawning0 Suns0 Moons0 Starfields0 Doodads0 Visuals0 FeatureSmalls0 FeatureMediums0 FeatureLarges0 Effects0   (rank 0)
Spawning0 → ε (10)
Biomes → BiomesAdv (1); BiomesAdv → BiomesAdv Biome (2) | Biome (3); Biomes → BiomesExt Biome (null); BiomesExt → BiomesExt Biomes (null) | ε (1)
Suns0 → SunsAdv (1); SunsAdv → SunsAdv Sun (4) | Sun (2); Suns0 → SunsExt Sun (null); SunsExt → SunsExt Sun (null) | ε (1)
Moons0 → MoonsAdv (1); MoonsAdv → MoonsAdv Moon (2) | Moon (2); Moons0 → MoonsExt Moon (null); MoonsExt → MoonsExt Moon (null) | ε (1)
Starfields0 → StarfieldsAdv (1); StarfieldsAdv → StarfieldsAdv Starfield (3) | Starfield (2); Starfields0 → StarfieldsExt Starfield (null); StarfieldsExt → StarfieldsExt Starfield (null) | ε (1); Starfield → ε (1)
Doodads0 → DoodadsAdv (1); DoodadsAdv → DoodadsAdv Doodad (5) | Doodad (2); Doodads0 → DoodadsExt Doodad (null); DoodadsExt → DoodadsExt Doodad (null) | ε (1); Doodad → ε (0)
Visuals0 → VisualsAdv (1); VisualsAdv → VisualsAdv Visual (3) | Visual (2); Visuals0 → VisualsExt Visual (null); VisualsExt → VisualsExt Visual (null) | ε (1); Visual → ε (1)
FeatureLarges0 → FeatureLargeAdv (1); FeatureLargeAdv → FeatureLargeAdv FeatureLarge (2) | FeatureLarge (2); FeatureLarges0 → FeatureLargeExt FeatureLarge (null); FeatureLargeExt → FeatureLargeExt FeatureLarge (null) | ε (1); FeatureLarge → ε (4)
FeatureMediums0 → FeatureMediumAdv (1); FeatureMediumAdv → FeatureMediumAdv FeatureMedium (2) | FeatureMedium (3); … Ext as above; FeatureMedium → ε (4)
FeatureSmalls0 → FeatureSmallAdv (1); FeatureSmallAdv → FeatureSmallAdv FeatureSmall (2) | FeatureSmall (4); … Ext as above; FeatureSmall → ε (4)
Effects0 → EffectsAdv (1); EffectsAdv → EffectsAdv Effect (3) | Effect (2); Effects0 → EffectsExt Effect (null); EffectsExt → EffectsExt Effect (null) | ε (1); Effect → ε (1)
SunsetUncommon → ε (2) | Sunset (3); Sunset → ε (1); Sunset_Ext → Sunset (null) | ε (1)
Angle → AngleAdv (1); AngleAdv → AngleAdv AngleBasic (2) | AngleBasic (3); Angle → Angle_Ext AngleBasic (null); Angle_Ext → Angle (null) | ε (1)
Period → PeriodAdv (1); PeriodAdv → PeriodAdv PeriodBasic (2) | PeriodBasic (3); Period → Period_Ext PeriodBasic (null); Period_Ext → Period (null) | ε (1)
Phase → PhaseAdv (1); PhaseAdv → PhaseAdv PhaseBasic (2) | PhaseBasic (3); Phase → Phase_Ext PhaseBasic (null); Phase_Ext → Phase (null) | ε (1)
Color → ColorAdv (1); ColorAdv → ColorAdv ColorBasic (2) | ColorBasic (3); Color → Color_Ext ColorBasic (null); Color_Ext → Color (null) | ε (1)
Gradient → GradientAdv (1); GradientAdv → GradientAdv GradientBasic (2) | GradientBasic (2); Gradient → Gradient_Ext GradientBasic (null); Gradient_Ext → Gradient (null) | ε (1)
BlockTerrain|BlockSolid|BlockStructure|BlockOrganic|BlockCrystal|BlockSea|BlockFluid|BlockGas|BlockAny → ε (0)
BLOCK_NONSOLID → BlockFluid (1) | BlockGas (2)
```

Symbol rules (`SymbolRules` + `createRules`) are listed in §4.3 per symbol. Additional: `FeatureLargeDummy` etc. (dummy features) rank 5 on the `Features*0` token so a random Age has a 5-vs-1 chance of lacking a feature tier — with the associated instability (0 / 1000 / 2000).

#### 4.4.3 Expansion algorithm (`GrammarTree`)

Used both to generate an Age from written pages and to build creative/random ages (`GrammarAPI.generateFromToken`).

1. `parseTerminals(symbols, rand)`: for each written symbol (processed from last to first) build a subtree: try to attach it to any currently *unexplored* node via the shortest rule path (`getShortestPaths(symbolToken, nodeToken)`, precomputed BFS over reverse rules at post-init; ties chosen by weighted random); otherwise reverse-expand upward while the token has exactly one parent rule (stop before the root), and keep it as a detached sub-root. New non-terminal children become unexplored nodes.
2. `getExpanded(rand)`:
   * Start output = written symbol list. If the root is unexpanded, add it to unexplored. Expand every unexplored node that has exactly one rule.
   * Connect every remaining sub-root to the main tree (`connectSubtreeShortest`): same token match, or a direct parent rule from an unexplored node (weighted), or shortest path. Failures are kept aside (unconnected written symbols still remain in the output list).
   * Compute insertion points: each unexpanded non-terminal leaf gets inserted relative to the nearest written terminal positions (left/right) and expanded via `explore(token)` = pick a weighted random rule recursively until terminals. Unplaced leaves go to the end.
   * Result: the full ordered symbol list of the Age (written symbols preserved in order, random fill-ins inserted at grammatically correct places).
3. The expanded list is stored in `AgeData.symbols` on the first link (`getSymbols(false)` when not visited); the original pages remain in `AgeData.pages`.

Note: because the root is always expanded, every random/first-link Age gets exactly one TerrainGen, BiomeController, Weather, Lighting, and grammar-selected suns/moons/etc.

#### 4.4.4 Fallback when required logic is missing

After processing all symbols (`AgeController.reconstruct`), if no biome controller / terrain generator / lighting / weather controller was registered, a random symbol providing that interface is picked (seeded by the Age seed) and appended to `AgeData.symbols` with instability `InstabilityData.missing.controller` = **0** (lighting: 0). Registering a *second* controller of the same kind adds `InstabilityData.extra.controller` = **500** each. **Reborn:** the blueprint fills the required categories before the first build, so this fallback only fires for Ages whose symbols fail to register (logged as `[blueprint] ... falling back`).
---

## 5. Age Generation Pipeline

### 5.1 Dimension registration & persistence

* One `DimensionType` "Mystcraft" (suffix `_myst`, provider id from config `ids.dim_provider`, default 1210950779, `keepLoaded=false`).
* On server start: scan the overworld's map-data directory for files `agedata_<id>.dat`; register each id as a Mystcraft dimension; those with `Dead=true` are added to `deadDims`. On server stop: unregister all.
* Clients receive `MPacketDimensions` (all registered ids on join; single id on creation) and register them locally with a client-side `SaveDataMemoryStorage` for `AgeData`.
* `DimensionUtils.createAge()`: reuse the first dead dim whose world is not loaded (delete its `DIM_MYST<id>` folder; `AgeData.recreate(id)` resets name "Age <id>", new seed = old seed + `Random(id).nextLong()`, new UUID, time 0, spawn null, instability 0, visited/dead false, pages/symbols/authors cleared); else `DimensionManager.getNextFreeDimId()`, register, broadcast `MPacketDimensions`, create `AgeData`.
* New `AgeData` defaults: name `Age <id>`, seed = `levelSeed + Random(id).nextLong()`, random UUID, time 0, instability 0, instabilityEnabled true.
* Every Age has its own `WorldInfoMyst` (derived world info): world time and spawn point come from `AgeData`; total-time counter is local on the client; raining/thundering delegate to the weather controller; `setRaining` toggles precipitation; map features enabled.
* Home dimension for Star Fissures and ejections: config `teleportation.homedim` (0).

### 5.2 `AgeData` (WorldSavedData `agedata_<id>`, version "4.3")

| NBT key | Type | Notes |
|---|---|---|
| `Version` | String | "4.3" |
| `AgeName` | String | |
| `Seed` | Long | |
| `UUID` | String | random UUID; link items store `TargetUUID` and are refused if mismatched |
| `BaseIns` | Short | base instability (sum of `addSymbol` penalties, e.g. missing controllers) |
| `InstabilityEnabled` | Bool | toggled by `/myst-twi` |
| `Visited` | Bool | set when the Age is first built; freezes seed/pages |
| `Dead` | Bool | recyclable |
| `DataCompound` | Compound | per-subsystem storage objects (`weather`, …) |
| `WorldTime` | Long | |
| `SpawnX/Y/Z` | Int | optional |
| `Pages` | List<Compound> | page item NBT tags |
| `Symbols` | List<String> | expanded symbol ids |
| `Authors` | List<String> | |
| `Cruft` | Compound | leftovers from older versions (e.g. `instabilityeffects` list consumed by `StorageInstabilityData`) |

Client sync: `MPacketAgeData` carries the full NBT; sent on login (if in an Age), on dimension change into an Age, and every 200 ticks while `needsResend` (set by weather toggles/resets).

### 5.3 World provider behaviour (`WorldProviderMyst`)

| Aspect | Behaviour |
|---|---|
| Save folder | `DIM_MYST<id>` |
| Dimension name | age name |
| Sky light | yes; not nether; `isSkyColored=false`; void fog factor 0.03125; `shouldClientCheckLighting=false` |
| Respawn allowed | config `respawning.respawnInAges` (true) |
| Cloud height / horizon / ground level / sea level | from AgeController (defaults 128 / 63 / 64 / 63; multiple setters average) |
| Light table | from lighting controller |
| Celestial angle | from suns (§4.3.2) |
| Fog / sky / cloud colours | from colour providers, else vanilla formulas; rain/thunder/lightning darkening as vanilla (sky: rain factor 0.75, cloud: 0.95) |
| Freezing/snow | temperature via `getTemperatureAtHeight` (identity); freeze requires temp ≤ 0.15 and block light < 10 (vanilla) |
| `canDoLightning(chunk)` | hijacked: calls `AgeController.tickBlocksAndAmbiance(chunk)` (weather tick, env effects, instability effects) and returns false |
| `updateWeather` (server & client tick) | bonus manager tick; weather controller `updateRaining`; copies rain/thunder strength into the world; if no players for 10 ticks → queue unload all chunks; if all players asleep → advance time to next sunrise; resend age data every 200 ticks when flagged |
| PvP | `isPvPEnabled` from controller (Anti-PvP symbol) |
| Spawn point | `verifySpawn`: if unset, try `findBiomePosition(0,0,256, spawnBiomes)`; then up to 1000 tries of random (±64, sea level, ±64) needing a non-bedrock solid top block; finally raise until air; store in AgeData. |
| Biomes | `getBiomeForCoords` returns a cached `BiomeWrapperMyst` per biome: mirrors base biome (same registry name) but temperature/rainfall/snow/rain come from the weather controller and grass/foliage/water colours from static colour providers. Spawn lists delegate to base biome. |
| Debug | registers debug nodes under `ages.<agedata_id>` |

### 5.4 Chunk generation (`ChunkProviderMyst`)

1. `rand.setSeed(chunkX·0x4f9939f508 + chunkZ·0x1ef1565bd5)`; new `ChunkPrimerMyst` (rejects bedrock at y≤5 during biome decoration; applies primer filters e.g. Skylands).
2. Add primer filters from terrain alterations.
3. `controller.generateTerrain` (the single terrain generator).
4. Biome array for the chunk; `replaceBlocksForBiome` (Forge `ReplaceBiomeBlocks` event, then each biome's `genTerrainBlocks` with 4-octave Perlin stone noise ×0.0625).
5. `controller.modifyTerrain` (all terrain alterations in symbol order: caves, ravines, spheres, floating islands, huge trees, tendrils, structure map-gens).
6. Scattered feature generator (Mystcraft Library, §11).
7. Build chunk, generate skylight, zero block light for y<128, `controller.finalizeChunk` (Floating Islands biome replacement).

Population: `chunk.setTerrainPopulated(false)`; seed like vanilla; scattered features; `biome.decorate`; animals (if event allows); `controller.populate(x,z)` (all `IPopulate` in order; a populator returning true sets `flag` so later ones may skip); 16 nether-quartz veins (size 13 in netherrack) at y 10–117 when the QUARTZ ore event allows; ice/snow pass; `setTerrainPopulated(true)`.

`MystWorldGenerator` (Forge world generator, weight `Integer.MAX_VALUE`): in the spawn chunk places a 5×5 cobblestone platform at spawn−1 and clears 4 blocks above; queues completed 3×3-populated chunks to the `ChunkProfilerManager` thread for instability profiling.

Creature spawns: chunk provider returns the biome's spawn list (spawn modifiers are a no-op hook).

> **Reborn revision (Creatures category, world-building plan §10):** the biome's spawn list is rescaled per creature group by the Age's `CreatureController`s (`AgeChunkGenerator.getMobsAt` → `CreatureRules.scaleSpawns`). Groups: passive (animals, ambient, water), neutral (`#mystcraft:neutral_creatures` entity-type tag) and hostile (monsters); the pages `creatures_passive / neutral / hostile` take rate (`mod_rate_none|sparse|dense|swarm` ×0/0.25/2/4), cap (`mod_cap_few|many|horde` ×0.25/2/4 of the vanilla per-category cap over the loaded spawn area; enforced in `MobSpawnEvent.PositionCheck` for smaller caps and by extra `NaturalSpawner` passes from the Age ticker for larger ones) and, hostiles only, difficulty (`mod_difficulty_easy|hard|brutal`: health/damage attribute modifiers on `FinalizeSpawnEvent`); `creatures_none` ("Lifeless") silences every group. An unwritten group spawns as vanilla. None of these pages carries symbol instability; the harsh-deck **Frenzy** card (cost 1000) instead makes hostiles spawn ×1.5 and one difficulty step harder while dealt. `[creatures]` log lines (DEBUG) record cap refusals and extra spawns.

### 5.5 AgeController construction

* `reconstruct()` runs on creation and whenever `AgeData.isUpdated()` (after an NBT reload) — guarded by a semaphore; other threads wait.
* Reads `agedata.getSymbols(isRemote)`; for each id, `SymbolManager.getAgeSymbol(id)`; unknown ids log an error and are skipped. `addSymbol`: `registerLogic(this, symbolSeedRand.nextLong())` where `symbolSeedRand = Random(ageSeed)`; count occurrences; add `instabilityModifier(count)`.
* Fallbacks (§4.4.4); then weather storage object bound; remaining modifiers' dangling costs added; light table generated; `agedata.markVisited()`.

---

## 6. Instability System

### 6.1 Score

```
score = debugInstability + symbolInstability + blockInstability + agedata.BaseIns + bonusManager.total
score *= {difficulty 0: 0.25, 1: 0.5, 2: 1.0, 3: 1.75}      // balance.cfg instability.global.difficulty (default 2)
```

* `symbolInstability`: sum of symbol `instabilityModifier`, extra-controller penalties (+500 each), replaced-modifier dangling, leftover dangling, `ModClear` 20% dangling, `addInstability()` calls.
* `blockInstability`: from the chunk profiler once ≥ 400 chunks have been profiled (`MINCHUNKS`); recomputed every 100 further profiled chunks. Until then the score is **0** (`blockinstability == null` ⇒ `getInstabilityScore` returns 0). If fewer than 400 chunks exist the controller force-generates chunks in a spiral from spawn until the profiling queue is large enough.
* Instability is globally enabled by `balance.cfg instability.global.enabled` and per-Age by `AgeData.InstabilityEnabled`.

### 6.2 Chunk profiling (`ChunkProfiler`, per-world saved data `MystChunkProfile`)

* For each completed chunk: a 256×256-cell "solid map" (index `y<<8|z<<4|x`, 256 layers) accumulates accessibility per block: non-air 2, watched block (has instability factors) 1, passable 1, air 0; `count += 2`. Each watched block state has its own count map (+1 per occurrence; map count +1 per chunk).
* `calculateSplitInstability()`: per layer average solidity; `filtered = avg − min`; `rounded = round(100·filtered)/100`; `ground = mean of positive rounded`; `solid[y] = rounded>ground`; `accessibility[y] = solid ? 1−rounded : 1`. For each watched block (only if its map count ≥ 100) and each cell: `val = density · accessibility[y] · factor1 + density · factor2`, summed per block key.
* `calculateInstability()` = `Σ max(0, split[block] − baseline[block])` (negatives allowed through), rounded. Returns 0 if no baseline exists.
* Watched blocks & factors (`InstabilityData.initialize` + fluids): coal_ore (5,1), lapis_ore (5,1), iron_ore (60,1), emerald_ore (200,2), redstone_ore (250,2), gold_ore (750,4), diamond_ore (4000,20), blockcrystal (20,4), glowstone (50,4), quartz_ore (20,4); fluids per §4.3.14; IMC `blockinstability` can add more.
* Command `/myst-reprofile [dim]` clears a dimension's profile.

### 6.3 Baseline profiling (`InstabilityDataCalculator`)

* Purpose: compute `freevals[block]` = what a "normal" world yields, so only *excess* ores count.
* Mode `baselining.useconfigs=true`: read `balance.cfg` category `baselining` keys `<blockkey>` with defaults coal 300, diamond 1000, emerald 100, glowstone 0, gold 500, iron 500, lapis 100, quartz 0, redstone 600, crystal 0; skip generation.
* Otherwise: a hidden dimension type `Mystcraft_ProfilerDummy` (suffix `_mystprof`, ids from `Integer.MIN_VALUE` upward) is registered and a control Age generated: grid biome controller with *every* selectable biome, stone/water blocks, TerrainNormal, surface lakes (water), deep lakes (lava), caves, ravines, villages, mineshafts. Chunks are generated one per `baselining.tickrate.minimum` ticks (default 5; 1 when not per-save) along a strip (`setBounds(count−1, minimumchunks+2, −1, 2)`), profiled, until `minimumchunks = biomeCount · max(10, ceil(500/biomeCount))` chunks. Then `freevals = ceil(split·1.05/100)·100` and the profiling dimension is unloaded/unregistered and its folder deleted.
* `client.persave=true` (default): runs in the background on the server for each save (`myst_baseline` saved data in the overworld); players who log into the profiling dim are ejected; links into it are cancelled; `MPacketProfilingState` shows toast notifications ("Expect Lag (Profiling)"/"Ding!"). `persave=false`: runs once client-side at the main menu in a temporary integrated-server world `mystcraft_profiling` with a progress GUI (`GuiMystcraftProfiling`), stores results in `<mcDataDir>/mystcraft/` via `ExternalSaveHandler`, deletes the world afterwards. `server.disconnectclients=true` refuses connections to a dedicated server while profiling.

### 6.4 Decks and providers (`InstabilityManager`, `InstabilityController`)

* Providers: `registerInstability(id, provider, activationCost)`; disabled per id by `instabilities.cfg instability.<id>.enabled`. `smallestcost` = min positive cost (initially 500) — the score is quantised down to a multiple of it before reconstruction.
* Decks with costs: `basic` 0, `harsh` 2500, `destructive` 10000, `eating` 15000, `death` 20000. Cards (provider ids) with copies per deck:

| Provider id | Effect | Activation cost | basic | harsh | destructive | eating | death |
|---|---|---|---|---|---|---|---|
| blindness | Blindness 60t (sky-visible entity) | 1000 | | | | 1 | |
| blindness,g | Blindness 60t global | 1500 | | | | | 1 |
| enemyregen,g | Regeneration 200t on non-players (global) | 1000 | 5 | 2 | | | |
| enemyresist,g | Resistance 200t on non-players | 1000 | 2 | 1 | | | |
| fatigue | Mining Fatigue 80t | 500 | 5 | | | | |
| fatigue,g | global | 1000 | | 5 | | | |
| hunger | Hunger 80t | 500 | 8 | 2 | | | |
| hunger,g | | 1000 | | 5 | | | |
| nausea | Nausea 60t | 1000 | | | | 1 | |
| nausea,g | | 1500 | | | | | 1 |
| poison | Poison 80t | 500 | 9 | 3 | | | |
| poison,g | | 1000 | | 5 | | | |
| slow | Slowness 80t | 500 | 6 | 1 | | | |
| slow,g | | 1000 | | 5 | | | |
| weakness | Weakness 80t | 500 | 8 | 2 | | | |
| weakness,g | | 1000 | | 5 | | | |
| wither | Wither 30t | 1000 | | 1 | 1 | 2 | |
| wither,g | | 2000 | | | 1 | 1 | 1 |
| burning | `EffectScorched(level)` | 500 | | 1 | | | |
| crumble | `EffectCrumble` | 2000 | | | 6 | | |
| decayblue | blue decay | 2000 | | | | 2 | 1 |
| decaypurple | purple decay | 2000 | | | | 2 | 1 |
| decayred | red decay | 2000 | | | | 2 | 1 |
| decaywhite | white decay | 5000 | | | | 1 | 3 |
| explosions | `EffectExplosions` | 1000 | | | 8 | | |
| lightning | `EffectLightning` | 1000 | | 4 | 4 | | |
| meteors | `EffectMeteor` | 1000 | | | 4 | | |
| frenzy *(Reborn)* | hostiles spawn ×1.5, one difficulty step harder (`CreatureRules`) | 1000 | | 3 | | | |

(Registered but unused/commented: `burning,g` 1000, `crumblebedrock` 5000, `decayblack` 5000, `erosion` 2000.)

* Potion providers: level-based (`uselevel=true`): one effect instance with amplifier = level−1 (level = number of drawn copies). Non-global effects only target entities that can see the sky. Each chunk tick: pick a random entity list section, random entity, apply if `EntityLivingBase`.
* Deck order per Age is persisted in per-world saved data `MystInstabilityData` (`Decks[{Name, Cards[]}]`); on build, cards already in the stored order are kept, missing ones appended shuffled with `Random(ageSeed)`.
* `reconstruct()` (whenever the quantised score changes or enable state flips): for each deck: `budget = score − deckCost`; if <0 skip; walk cards in order subtracting each card's cost while budget ≥ 0; every drawn card increments that provider's level; then each provider `addEffects(controller, level)`.
* `tick(chunk)` runs all active effects for the chunk each chunk tick.

### 6.5 Effect implementations

| Effect | Behaviour per chunk tick |
|---|---|
| `EffectDecayBasic(type, minY, maxY)` | If `rand(1000000) < score`: choose LCG random (x,z,y); `maxY` null ⇒ surface height (or average ground level if ≤ min); `y = (y mod (max−min)) + min`; skip banned materials moving down; place decay of `type` (flag 2). Black: min 0 max 12 (bans air/water/lava); Blue 25..surface; Purple 25..54; Red 25..surface; White 20..surface. Each decay provider also registers `EffectExtraTicks(thatDecayState)` (3 random ticks per section restricted to that state) — white registers one extra globally. |
| `EffectCrumble` | One random block per chunk tick (y 0–255) replaced by its mapping: ores→stone (diamond→coal ore), ice→water, glowstone→glass, crystal→glass, nether brick→netherrack, quartz ore→netherrack, netherrack→soul sand, soul sand→gravel, stone brick→stone, stone→gravel, cobble→gravel, grass/mycelium/mushroom blocks/clay→dirt, gravel/dirt/glass/sandstone→sand, logs→planks, planks→dirt, wool→white wool→web, sapling/web/leaves/tallgrass/mushrooms/flowers→air; any ore-dictionary `ore*`/`gem*`/`dust*` item block → stone. |
| `EffectErosion` (unused) | 1/100: replace a random block (y<128) with an adjacent fluid. |
| `EffectExplosions` | 1/1000: explosion 3.0 flaming at random column. |
| `EffectLightning` | see §4.3.10 |
| `EffectMeteor` | 1/50000: meteor. |
| `EffectScorched(level)` | see §4.3.10 |
| `EffectExtraTicks(state?)` | 3 extra random ticks per 16-block section per chunk tick (optionally only for one block state). |

### 6.6 Instability bonuses (`InstabilityBonusManager`)

Providers are registered globally (`registerBonusProvider`); base mod registers none by default but ships two bonus types usable via API/other mods:
* `PlayerKilledBonus(dim, playerName, max, decayRate)`: value = −current; set to `max` when that player is killed by a player in the dim (`instability.bonus.death`), `max/2` on other deaths; decays by `decayRate` per tick; announces on entry.
* `PlayerTrollPenalty`: value = +current, grows by rate while the named player is in the dim (up to max), decays when absent, resets on death.
Messages use `instability.bonus.*` lang keys, sent to all players in the dim.

---

## 7. Linking System

### 7.1 Link info (`ILinkInfo` / `LinkOptions` NBT)

| Key | Type | Accessor | Default |
|---|---|---|---|
| `DisplayName` (legacy `agename`) | String | display name | "???" |
| `Dimension` (legacy `AgeUID`) | Int | target dim | null |
| `TargetUUID` | String | age UUID check | null |
| `SpawnX/Y/Z` | Int | target position | null ⇒ world spawn |
| `SpawnYaw` | Float | | 180 |
| `Flags.<flag>` | Bool | link property flags | false |
| `Props.<name>` | String | properties (`Seed`, `Sound`) | null |

Flag constants: `Intra Linking`, `Intra Linking Only`, `Relative`, `Disarm`, `Maintain Momentum`, `Generate Platform`, `Natural`, `External`, `Offensive`, `Op-TP`, `Following`. Property `Sound`.

### 7.2 Link properties (ink effects) and their meaning

> **Reborn revision:** Following is inkable (gold) so the Link Modifier lists it and the mixer can set it (eye of ender). Every inkable property has exactly one ingredient (§3.2).

| Property | Colour (ink gradient) | Gameplay |
|---|---|---|
| Intra Linking | (0,1,0) green | Allows linking within the same dimension. |
| Intra Linking Only | (1,1,1) white | Link only works within the same dimension. |
| Generate Platform | (0.5,0.5,0.5) grey | On arrival, if the two blocks below spawn are air, place a stone block under the player. (Descriptive Books always have it.) |
| Maintain Momentum | (0,0,1) blue | Keep velocity, re-oriented to the destination yaw; otherwise motion and fall distance are zeroed. Both cases add +0.2 upward motion. |
| Disarm | (1,0,0) red | On link start: players/`IInventory` entities/horses drop their whole inventory; living mobs drop equipment; items and book entities cannot use disarm links. |
| Relative | (0.6,0,0.6) purple | Destination = destination world spawn + (entity pos − origin world spawn). Not craftable (excluded from creative/trades). |
| Following | – | Book is not left behind; enchant glint; sound `link-following`. |
| Natural / External / Offensive / Op-TP | – | Informational flags: Natural+External set by Star Fissure; External by portals; Op-TP by `/tpx` (bypasses permissions). |

Ink modifier items (probability contributed per item consumed; totals per item may not exceed 1.0):

| Item | Property : probability |
|---|---|
| Gunpowder | Disarm 0.20 |
| Mushroom Stew | Disarm 0.05 |
| Clay Ball | Generate Platform 0.25 |
| Bottle o' Enchanting | Intra Linking 0.15 |
| Black dye (ore dict `dyeBlack`) | "" (dilution) 0.50 |
| Ender Pearl | Intra Linking 0.15, Disarm 0.15 |
| Feather | Maintain Momentum 0.15 |
| Fire Charge | Disarm 0.25 |
| `dustBrass`, `dustBronze` | Disarm 0.15 |
| `dustTin` | Generate Platform 0.10, Intra Linking 0.10 |
| `dustIron` | Generate Platform 0.15, Intra Linking 0.15 |
| `dustLead` | Disarm 0.20, Intra Linking 0.20 |
| `dustSilver` | Generate Platform 0.20, Intra Linking 0.20 |
| `dustDiamond` | Intra Linking 0.25, Maintain Momentum 0.10, Generate Platform 0.10 |
| `dustGold` | Intra Linking 0.25, Generate Platform 0.10, Disarm 0.10 |
| Gold Nugget (Reborn addition, vanilla stand-in for `dustGold`) | Intra Linking 0.12, Generate Platform 0.05, Disarm 0.05 |
| Iron Nugget (Reborn addition, vanilla stand-in for `dustIron`) | Generate Platform 0.08, Intra Linking 0.08 |

The Ink Mixer basin tooltip lists every ingredient available in the current game (tag bindings with no items are hidden) with its effects, so the recipe list is discoverable in-game.

Ink gradient for GUI display (`getPropertiesGradient`): for each property with p ≥ 0.001, push its colour with interval p (split as (p−0.3)+0.3 when p>0.3); remaining (1−Σ) pushed as black the same way.

### 7.3 Link flow (`LinkController.travelEntity`)

1. Server only; clone info; need `Dimension`; `LinkListenerManager.isLinkPermitted` (posts `LinkEventAllow`; cancelled ⇒ refuse). Use the lowest riding entity. Require server & (dim==0 or allow-nether).
2. Get/load target `WorldServer`; if `Spawn` is null use the target world's spawn point.
3. Post `LinkEventAlter` (listeners may override `spawn`/`rotationYaw`; the Relative flag handler does).
4. `teleportEntity`: recursively dismount and teleport passengers first; re-check permission; post `LinkEventStart`; un-sneak; if different world `entity.changeDimension(dim, LinkTeleporter)` (places at spawn with yaw), else `setLocationAndAngles(spawn+0.5, yaw)`; load destination chunk; while the entity bounding box collides with world geometry move spawn up by 1; players: `setPlayerLocation`, close open container/screen; post `LinkEventEnd`; re-mount passengers.
5. Base listeners: `LinkListenerBasic` (permission rules, Relative alter, Disarm start, momentum + Generate Platform + minecart stop on end, advancement triggers), `LinkListenerPermissions`, `LinkListenerEffects` (particles + sounds at start and end), `LinkListenerForgeServer` (fires Forge `PlayerChangedDimensionEvent`).

Permission rules (`LinkEventAllow` cancelled when): server world; `Dimension` null; entity dead / in another world / being ridden; same dimension without Intra Linking (or Intra Linking Only); different dimension with Intra Linking Only; target dead; `TargetUUID` mismatch (`requireUUIDTest` config strictness applies to login checks); Disarm links for `EntityItem`/`EntityLinkbook`; per-player permission lists (§12 `/myst-permissions`), except Op-TP links; the baseline profiling dim.

Sounds on start and end: items/book entities → `linking.pop`; Disarm → `linking.link-disarm`; property `Sound` → that sound event; Following → `linking.link-following`; Intra Linking → `linking.link-intra`; else `linking.link`. Volume 0.8, pitch 0.9–1.1. 50 "link" particles (dark grey smoke-like, shrinking) at the position (`MPacketParticles` to the dimension).

Advancements: on entering a Mystcraft dim, players carrying a Linking Book trigger `enter_myst_dimension_safe` ("The Way Back"), otherwise `enter_myst_dimension_quinn` ("Call Me Quinn", hidden).

### 7.4 Login/dimension checks (`MystcraftConnectionHandler`)

* On player login: if (no stored dim UUID and `requireUUIDTest`) or the current dim is dead or the stored UUID (`ForgeData.PlayerPersisted.myst.dimUUID`) mismatches → schedule ejection to the home dimension (an Op-TP link). If in an Age, send age data.
* On dimension change: dead target ⇒ eject; store the new dim's UUID on the player; send age data for Ages.
* On client connect: create client `SaveDataMemoryStorage`; on disconnect clear and unregister dims (if no local server).
* Server sends `MPacketDimensions(all)` and `MPacketConfigs` on connection open.

### 7.5 Dead dimensions

`markDimensionDead(id)`: not allowed for the home dim or non-Mystcraft dims; sets `Dead` and queues for recycling. Links into dead dims are refused; players found in them are ejected. The Link Modifier GUI exposes "mark book as dead" (arm + confirm).

### 7.6 Portal colour

`DimensionUtils.getLinkColor(info)`: `Random(displayName.hashCode())` → r,g,b bytes (each `nextInt(256)`), packed `b<<16 | g<<8 | r` order as coded (`color += r; color += g<<8; color += b<<16`).

### 7.7 Crystal portals (`PortalUtils`)

* Valid portal blocks: `blockcrystal` and `linkportal`. Receptacle attaches to a crystal (its `rotation` faces away from the crystal; the crystal is `pos.offset(rotation.opposite)`).
* `firePortal(receptaclePos)` when a book is inserted: `onpulse(crystalPos)`: flood-fill from the 18 positions around the base crystal; an air block becomes a portal block if ≥2 of its 6 neighbours are portal/crystal blocks (`expandPortal`); newly created blocks expand further; afterwards each created block is validated by **tension**: count axes (X, Y, Z) whose both opposite neighbours are portal/crystal; require ≥2 axes ("score==2 yields forcefield walls") else remove.
* `pathto(receptaclePos)`: BFS from the receptacle assigning each inactive crystal/portal block `active=true` and `source` = direction back toward the receptacle (neighbour offsets east=5,up=1,south=3,west=6,down=2,north=4 as `EnumFacing.values()[meta-1]`); portal blocks are processed after crystals; afterwards each portal block must be stable (tension ≥2 and a receptacle reachable through the `source` chain); unstable portals are removed, neighbours re-pathed (`repathNeighbors` tries alternative source directions that still reach the same receptacle). Changed blocks get render updates.
* `shutdownPortal`: `unpath` — BFS clearing `active`/`source` on all connected crystal/portal blocks, removing portal blocks that become unstable.
* `validatePortal(pos)` (on neighbour change/random tick of a portal block): remove unstable portal blocks and cascade to neighbours.
* `getTileEntity(pos)`: follow `source` links from a crystal/portal through active blocks until reaching a receptacle (cycle-safe); null if the chain breaks. Portal colour and collision use this.

### 7.8 Star Fissure

Feature generation in §4.3.9; block behaviour in §3.11. Home dimension default 0.

### 7.9 Book entities (dropped books)

`EntityLinkbook` (§9) is created by `ItemLinking.createEntity` when a book stack is dropped or left behind. Right-click opens its GUI (`BOOK_ENTITY`); sneak + empty hand picks it up.
---

## 8. GUIs & Containers

All GUIs are opened via a GUI handler with ids (`ModGUIs` ordinal order): `BOOK_BINDER`, `BOOK_DISPLAY`, `INK_MIXER`, `LINK_MODIFIER`, `WRITING_DESK`, `BOOK`, `FOLDER`, `PORTFOLIO`, `BOOK_ENTITY`, `VILLAGER`. Client↔server GUI communication uses a single generic packet `MPacketGuiMessage(windowId, NBT)` dispatched to the open container if it implements `IGuiMessageHandler.processMessage(player, nbt)`; the server validates the window id and `getCanCraft`. Clients typically send the message and also apply it locally (prediction).

Shift-click routing uses `SlotCollection` chains: internal slots → main inventory → hotbar, and main/hotbar → internal slots → page receiver (desk tab / binder page list).

### 8.1 Writing Desk (`ContainerWritingDesk` / `GuiWritingDesk`)

> **Reborn revisions:** (1) the left panel is the **symbol surface** (`SymbolSurface`): search box, nine category tabs, then the known symbols grouped under category headers; the tooltip of a symbol shows its category, what it takes ("Takes: direction, phase, length, sunset colour") or what it attaches to, and what a click does. (2) **Drafts:** pages written at the desk are recorded as drafts (`WritingDeskBlockEntity.Draft` = page index + paper used) and drawn washed out in the strip; **right-clicking a draft erases it** and refunds its ink and paper (right-clicking a permanent page hands it to the cursor); drafts become permanent when the folder leaves the slot (checked every tick), when pages are moved by hand in the strip, or when the desk is broken. There is no undo button. (3) The folder strip selects a page on click (gold frame); modifiers that fit the selected page glow on the surface, the others dim; shift-click takes a page out, shift + right-click removes its last modifier. (4) Messages: `SetTitle`, `WriteSymbol(Symbol)`, `AttachModifier(Symbol, Index)`, `DetachModifier(Index)`, `RemovePage(Index)`, `TakeFromSlider`, `InsertHeldAt`, `SetFluid`. (5) The desk block renders an inkwell on the desk top showing the tank level; a Scholar's desk shows full shelves. No notebook slots, no `AddToSurface` / `RemoveFrom*` / `AddToTab` messages, no AZ/ALL buttons.

Layout constants: left panel width 228, main window 176×166 shifted by (233, 20); button row 18 px.

Slots (indices): 0–3 notebook tab slots (tab inventory, indices `firstslot..firstslot+3`, stack limit 1, at x=37, y=14+i·37+20); 4 target slot (main 0, limit 1) at (241, 80); 5 paper (main 1) at (241, 28); 6 ink container in (main 2) at (385, 28); 7 container out (main 3) at (385, 80); 8–34 player inventory; 35–43 hotbar.

Elements:
* **Notebook tabs** (left column, 4 visible of 25; scroll with up/down arrows, keyboard up/down or W/S): each shows the numeral word glyph and the item's name. Click a tab: if holding an item → `AddToCollection` (add held page/stack to that notebook; right-click adds a single); else select as active (`SetActiveNotebook`). `SetFirstNotebook` scrolls.
* **Page surface** (left, below a search text field and two toggle buttons "AZ" sort alphabetically and "ALL" show all symbols): displays the active notebook's pages as 30×40 tiles in rows (with counts for collections; ghost pages with count 0 for "ALL"); scrollbar on the right (20 px). Click with an item in hand → `AddToSurface` at hovered index (right-click = single). Left-click a page → pickup (`RemoveFromCollection` with the page NBT, shift = 64; or `RemoveFromOrderedCollection` index for folders). Right-click-and-release on a page → **copy** (`WriteSymbol` with that symbol). Middle click reserved. Search filters by localized symbol name. Buttons are enabled only for collection items (portfolios).
* **Target area**: `GuiElementBook` (link screen, §8.5) when the target is a linking item; `GuiElementScrollablePages` (horizontal page strip with arrows; click a page to take it → `TakeFromSlider`; click with item → `InsertHeldAt` index, right = single) when the target is a writable non-book (folder); `GuiElementPage` (renders the target page/item, click passes through to the slot) otherwise.
* **Name text field** ("ItemName", max 21 chars) below: renames the target (`SetTitle`); read-only if the target is empty or a page.
* **Ink tank** (16×70 at right): shows fluid level and tooltip `<Fluid>: amount/1000`.
* Server→client sync messages: `SetFluid`, `LinkPermitted`, `SetTitle`, `SetCurrentPage`.

### 8.2 Book Binder (`GuiBookBinder`, 176×181)

* Slot 0 cover (8,27); 1–27 inventory (y 99+); 28–36 hotbar (y 157); 37 craft output (152,27) — `SlotCraftCustom` (not insertable; on take calls `buildItem`).
* Title text field (7,9, width 116, max 21; red border while empty). Page strip (7,45, width 162, height 40) showing the pending page list (`InsertHeldAt`/`TakeFromSlider`; holding a folder inserts from the folder). A pulsing red "Missing Link Panel" icon (27,26) appears while page 0 is not a Link Panel with tooltip "Add a link panel as the first page of the book."
* Shift-clicking pages from the inventory appends them to the page list.

### 8.3 Ink Mixer (`GuiInkMixer`, 176×181, plain container)

* Slots: 0 ink in (8,27), 1 paper (8,48), 2 empty out (152,27), inventory/hotbar as binder, craft output (152,48).
* Basin: circle centre (88,49) radius² 900 drawn at (54,16) 66×65: shows black ink texture when `hasInk`, tinted by the animated property gradient (frame/300) plus a D'ni colour "eye" (`DniColorRenderer`, radius 20). Clicking inside the basin with an item → `Consume` (right = single).
* Sync: `SetInk`, `SetSeed`, `SetProperties`.

### 8.4 Link Modifier (`GuiLinkModifier`, single-slot background 176×166)

* Slot 0 book at (80,35); inventory standard.
* Toggle buttons (18 px) in columns from (5,10), one per registered link property (tooltip = property name; toggles the book flag via `SetFlag`).
* Text fields: "Seed" (80,15) visible only for Descriptive Books (`SetSeed`, empty clears); "ItemName" (80,56) (`SetTitle`). Target dim id drawn at (100,40).
* "kill" (120,32) arms; "confirmkill" (140,32, red) → `RecycleDim` (mark dead). Shown only for Descriptive Books; `LinkDead` state synced.

### 8.5 Book GUI (`GuiBook` / `GuiElementBook` / `ContainerBook`) — used for held books, stands, lecterns, receptacles, book entities

> **Reborn revisions:** symbol pages show the symbol's category (small, above the name), name and a one-line description (`symbol.<ns>.<id>.desc`, `AgeSymbol.description()`) on the left page followed by the attached modifiers ("With: + North Direction"), the glyph with its modifier overlays on the right; discovered pages are drawn in the discovered ink with a "discovered at the first link" note, drafts in grey. The pages of a bound book are in category order (§4.4). The same description is in page item tooltips. A bound Descriptive Book has a **summary** after its last page, spread over both pages (`AgeSummary`, synced by `BookMenu` once a second): left - seed, base + symbol instability, the live instability score while the Age is loaded, discovered-page count, authors; right - the active instability effects. The link panel shows four level photographs taken from the arrival point (north, east, south, west, in that order) as a slideshow; a new arrival replaces the set.

* Book element is 327×199 (scaled): cover textures `bookui_cover.png`; Descriptive Books get gold borders. Page 0 (index 0) shows: optional book slot at (40,20) (only for tile/entity containers), title at (40,40), authors at half scale from (50,50) stepping 5, and the **link panel** at (173,20) 132×83: gradient dark-blue→teal (`0xFF000044→0xFF006666`) if the target dimension is registered/visited, else black; then registered `ILinkPanelEffect`s (Disarm: red lightning flashes every 3–8 s; LookingGlass: live world view with shaders when installed); then a grey overlay (`0xBB888888`) if the link is not permitted. Clicking the panel on page 0 sends `Link`. Pages > 0 show a symbol page (glyph at (171,25) size 140, tooltip name) or the link-panel page. Click left half / right half or arrow keys (or A/D binds) to page. Footer `current/total` at (165,185).
* When the container has an "other inventory" and no book, the GUI shows a plain single-slot 176×166 inventory screen (`single_slot.png`) so a book can be inserted; when a book is present only the book slot (41,21) exists (player inventory hidden).
* `ContainerBook` sources: player hotbar slot, `TileEntityBookRotateable`, or `EntityLinkbook` (1-slot wrapper; extracting the book kills the entity). Messages: `LinkPermitted` (server→client, computed via `LinkListenerManager` or true for new Descriptive Books), `SetCurrentPage`, `Link` (client→server; calls the tile/entity/item `activate`).

### 8.6 Folder / Portfolio GUI (`GuiInventoryFolder`, `ContainerFolder`)

> **Reborn revision:** folders only (the portfolio is gone): search box over the page grid, no AZ/ALL buttons, messages `AddToSurface` and `RemoveFromOrderedCollection`.

* 176 wide; page surface 132 high with search field and AZ/ALL buttons on top, then the player inventory (from `writingdesk.png` region y=82, 80 high). The held folder's hotbar slot is banned (cannot be taken). Messages `AddToSurface` (index, Single), `RemoveFromOrderedCollection`, `RemoveFromCollection`. Shift-click from inventory adds pages into the collection.

### 8.7 Creative collections

`mystcraft.common` tab adds "Spawned (<name>)" portfolios: All Symbols; Biome Distributions (`BiomeController`); Celestials (Sun, Moon, Starfield, Doodad); Effects; Lighting; Modifiers, Basic (AngleBasic, PeriodBasic, PhaseBasic); Modifiers, Biomes (Biome); Modifiers, Block (all Block* categories); Modifiers, Colors (ColorBasic, Color, GradientBasic, Gradient, Sunset); World Features (FeatureSmall/Medium/Large); World Landscapes (TerrainGen); Visuals; Weather. Contents = every symbol appearing as a value of a rule whose parent is one of the tokens, sorted by name.

> **Reborn revision:** one "Spawned (<category>)" portfolio per `SymbolCategory` (§4.4), pages sorted by id.

### 8.8 Villager shop (`GuiVillagerShop`, `tradeshop.png` 176×181)

Three shop item panels (rank 1, 2, ≥3 symbol pages, price text), booster icon with count and "Buy" button (cost 20 emeralds), player emerald count label (emeralds + 9×emerald blocks). Messages `PB` (purchase booster), `PI <index>` (purchase item), `UVC` (server→client villager inventory NBT). Container closes when the villager is dead or >8 blocks away.

### 8.9 Notifications & startup checks

* `GuiNotification`: achievement-toast-style popup (160×32, `toasts.png`) shown for 3 s; used for profiling state.
* `MystcraftStartupChecker` (client, main menu): shows `GuiNonCriticalError` listing errored symbols, or an error if no symbol provides biome distribution / terrain / lighting / weather; enables first-run profiling when not per-save.

---

## 9. Entities

| Entity | Registry | Tracking | Behaviour |
|---|---|---|---|
| Book (`EntityLinkbook`, `myst.book`, id 0) | `mystcraft:myst.book` | 64, 1, velocity | `EntityLiving` 0.25×0.2; data params `BOOK` (ItemStack), `AGE_NAME`. Spawned at the dropper's eye, offset −0.16 along facing, inherits motion. Health mirrors the book item health (max 10). Damage: ignores in-wall; fire damage doubled and sets the entity on fire; no knockback. Every 10000 ticks 1 starvation damage; 1 drown damage per tick while wet; air decreases by 2. Never despawns; no AI; cannot attack. Collides with hopper minecarts → inserted (entity dies). Right-click: sneaking with empty hand takes the book; else opens `BOOK_ENTITY` GUI. NBT `DecayTimer`, `Item`. Render: open `ModelBook` (1.2) with agebook/linkbook texture, red tint while hurt; label with age name when enabled. |
| Falling block (`EntityFallingBlock`, `myst.block`, id 1) | `mystcraft:myst.block` | 16, 10 | Used by black decay: carries a block state + drops list + tile NBT; gravity 0.04, drag 0.98; on ground places the block (restoring the tile entity) or drops items if placement fails; dies below y=−10. On first tick, cascades adjacent leaves (drops them too). Renders the block model. |
| Meteor (`EntityMeteor`, `myst.meteor`, id 2) | `mystcraft:myst.meteor` | 192, 2 | Size = scale; posts `MeteorEvent.MetorSpawn`. Each tick: on fire; first tick plays thunder sound (vol 10000) and a client moving sound `entity.meteor.roar`; ray-trace along motion; on hit: `inGroundTime++`, motionY ×0.9, breaks all non-air blocks in its AABB (+5 up), and once `inGroundTime ≥ penetration` performs 8 `ExplosionAdvanced`s: (5.0 at pos, ores), (scale at y−scale/10, ores), (2·scale at y−scale/5, ores), (scale at y−2scale/5, ores), and four flaming (scale) at ±4scale/5 on X/Z; posts `MetorImpact`; dies. Smoke particles each tick; water bubbles when submerged. Not saved (dies on load). Renders `ModelMeteor` with end-portal texture at scale/10. |
| Lightning (`EntityLightningBoltAdv`) | vanilla lightning subclass | – | Adds a colour (default (0.45,0.45,0.5)); rendered by `RenderLightningBoltAdv` with the colour; spawned via `MPacketSpawnLightningBolt`. |
| `EntityDummy` | not registered | – | Position holder used to validate books in the Link Modifier; dies on update. |

`ExplosionAdvanced`: 16³ ray cube like vanilla; power `size·(0.7..1.3)`; blocks collected; entity damage `((f²+f)/2·8·size+1)` with knockback; effects list executed per block: `Basic` (client particles), `BreakBlocks` (noDrop / dropItems 30%), `Fire` (1/3 on opaque support), `PlaceOres` (1/20 on air above opaque: coal 0.5 / iron 0.3 / gold 0.2 weighted; extensible via IMC `meteorblock`). Synced with `MPacketExplosion` within 64 blocks.

---

## 10. Villagers & Trading

* Profession `mystcraft:archivist` (texture `textures/villager/archivist.png`, zombie texture vanilla), single career `archivist`; enabled by config `ids.villager.archivist` (default true).
* Vanilla trade list: booster for 25 emeralds (registered post-init), plus any `IMerchantRecipeProvider`s (symbol trades: buy items from `SymbolValuesAPI` → symbol page; link panel trades: 16 emeralds per property page, 4 emeralds for a plain panel; all with max uses reduced by 6, i.e. 1 use before restock). Non-Mystcraft recipes are wrapped into `MerchantRecipeMyst` (7 max uses base).
* **Custom shop** (right-click an Archivist without sneaking cancels the vanilla GUI and opens `VILLAGER` GUI): `InventoryVillager` stored in the villager's entity data `Mystcraft.Trade` (`Inventory` 3 stacks, `boostercount`, `lastrestock`): 3 page slots holding 3 pages of a random symbol of rank ≥1, ≥2, ≥3 respectively (weighted by item weight); price = `4·(1+rank)` emeralds (100 if rank unknown); boosters start at 5, cost 20 emeralds. Restock simulation every 12000 ticks since last restock: roll `rand(1+3+3)`: 1/7 nothing, 3/7 +1 booster (max 8), 3/7 +1 page to that slot (max 5). Simulation runs on open and every 1000 server ticks for cached inventories. Payment accepts emeralds and emerald blocks (auto-changes a block into 9 emeralds when needed).
* Village house: `ComponentVillageArchivistHouse` (piece weight 20, limit `rand(i, i+1)` per village), 9×12×7 bounding box, cobblestone/planks house with bookshelves, two lecterns with loot books, a Writing Desk (if `villageDeskGen`), and one Archivist villager. Structure component id `ViMystAH`.

---

## 11. Structures & Loot (Ages and Overworld)

Note: the Mystcraft Library generates only inside Mystcraft Ages (it is invoked by the Age chunk provider). The Archivist House (§10) generates in vanilla villages in any dimension that runs vanilla village generation (overworld) and in Ages with the Villages symbol.

### 11.1 Mystcraft Library (`MapGenScatteredFeatureMyst`, structure id `MystLibrary`, component `TeMystSL`)

* Placed in **Mystcraft Ages** (the chunk provider runs it; not in the overworld). Spacing: one candidate per 32×32 chunk region, offset `rand(32−8)` within, seed salt 14357617; every candidate spawns (biome check disabled). Spawn list: Witch (weight 1, 1–1).
* `ComponentScatteredFeatureSmallLibrary`: 11×10×11, random horizontal orientation, placed at average ground level (bounding box shifted so `maxY = ground + 10`); foundation of cobblestone downward under the whole footprint; a loot chest at local (4,1,2) with `mystcraft:mystcraft_treasure`; five lecterns at local (6,2,2),(8,2,4),(8,2,5),(8,2,6),(6,2,8) facing (6,3),(7,4),(7,5),(7,6),(6,7) each holding a random rank≥3 symbol page (or other treasure item acceptable to a lectern) from the treasure table. Full block layout in Appendix B.

### 11.2 Loot

* Loot table `mystcraft:mystcraft_treasure` (pool `mystcraft:mystcraft_treasure`, rolls 4–8, bonus rolls 1–2): Ink Vial (50), Sealed Notebook (1000), Leather 1–3 (50), Paper 1–6 (50), plus — injected at load — one entry per registered symbol page with weight = symbol item weight and count `1 + rand(max(1, maxStack−1))`.
* Vanilla tables `chests/desert_pyramid`, `chests/jungle_temple`, `chests/stronghold_library`, `chests/simple_dungeon` get an extra entry `myst_treasure_hook` (weight 10) in pool `main` — a hook (`TreasureGenWrapper`) that adds nothing by default (extension point).
* `generateLecternItem`: roll the treasure table up to 100 times until an item accepted by the lectern filter is found; symbol pages must be rank ≥3.

---

## 12. Commands

| Command | Permission | Syntax | Behaviour |
|---|---|---|---|
| `/tpx` | 2 | `/tpx [subject] <targetPlayer | dim [x y z]>` (`~`/`?` relative/random numbers) | Builds a link (to a player's position or a dimension/coords), sets `Intra Linking` and `Op-TP`, travels the subject. |
| `/myst-create` | default 4 | `/myst-create <dimId>` | Registers a new Age with that id and a single Link Panel page. |
| `/myst-agebook` | | `/myst-agebook [dim]` | Gives the player a Descriptive Book for the given (Mystcraft) dimension. |
| `/myst-toggleworldinstability` (`/myst-twi`) | 2 | `/myst-twi [dim] [true|false]` | Toggles/sets `InstabilityEnabled` of an Age. |
| `/myst-spawnmeteor` | 2 | `/myst-spawnmeteor [scale] [penetration] [x z]` | Only if config `commands.spawnmeteor.enabled`. Spawns a meteor at y=500 falling straight down (−3). |
| `/toggledownfall` | 2 | `/toggledownfall [dim]` | Overrides vanilla: toggles raining in the dim (and sets thundering true). |
| `/time` | 2 | `/time <set|add> <value|day|night> [dim|all]` | Overrides vanilla: per-dimension; `day`/`night` computed from the next celestial angle 0.78 / 0.30 (search stepping). |
| `/myst-permissions` | | `/myst-permissions <player> <restrict|permit> <entry|depart> <all|dimId>` | Per-player link entry/departure lists (saved data `mystcraft-linking-permissions`). |
| `/myst-regenchunk` | 4 | `/myst-regenchunk [range=3] [dim] [chunkX] [chunkZ]` | Unloads and regenerates chunks in range, moving watching players away, resends chunks. |
| `/myst-reprofile` | 4 | `/myst-reprofile [dim]` | Clears the chunk profile of an Age. |
| `/myst-dbg` | 2 | `/myst-dbg read <a.b.c> | set <a.b.c> <value> | run <a.b.c> [arg]` | Debug hierarchy access (ages.<id>.instability.bonus/blocks, data.instability_calc, global.profiler.file_output, experimental.mark_dead). |

---

## 13. Configuration

Files under `config/mystcraft/`: `core.cfg`, `balance.cfg` (and optional `balance_template.cfg`), `symbols.cfg`, `instabilities.cfg`. Old `config/Mystcraft.txt` is migrated to `core.cfg`.

| File / category | Key | Default | Meaning |
|---|---|---|---|
| core / general | `configs.generate_template.balance` | false | Write `balance_template.cfg` with every fluid default. |
| core / general | `commands.spawnmeteor.enabled` | false | Register `/myst-spawnmeteor`. |
| core / general | `respawning.respawnInAges` | true | Players respawn inside Ages. |
| core / general | `generation.villageDeskGen` | true | Archivist houses contain a Writing Desk. |
| core / general | `teleportation.requireUUIDTest` | false | Strict dimension UUID check on login (new players sent home). |
| core / general | `teleportation.homedim` | 0 | Home dimension (Star Fissure target, ejection target). |
| core / general | `ids.villager.archivist` | true | Enable the Archivist profession. |
| core / general | `ids.dim_provider` | 1210950779 | Dimension type id. |
| core / general | `crafting.linkbook.enabled` | true | Enable the Unlinked Link Book recipe. |
| core / general | `crafting.linkeffects.<property>.enabled` | true | Allow each link property in the Ink Mixer (`intra_linking`, `intra_linking_only`, `generate_platform`, `maintain_momentum`, `disarm`, `relative`). Reborn: `crafting.linkeffects.disabled` list. |
| core / general | `inkmixer.ingredients`, `inkmixer.clearIngredient` | Reborn table (§3.2) | Reborn: one `effect=item` entry per effect; the clearing item. |
| core / render | `renderlabels` | false | Draw book names above stands/lecterns/receptacles/entities (server value overrides clients). |
| core / render | `fast_rainbows` | true | Cache rainbow in a display list. |
| core / baselining | `client.persave` | true | Baseline profiling per save in background (false: once at startup with a loading GUI). |
| core / baselining | `useconfigs` | false | Skip profiling; use baseline values from balance.cfg. |
| core / baselining | `server.disconnectclients` | false | Refuse client connections during profiling (dedicated). |
| core / baselining | `tickrate.minimum` | 5 | Ticks between profiling chunk generations. |
| balance / instability | `global.difficulty` | 2 (optional) | 0–3 multiplier 0.25/0.5/1/1.75. |
| balance / instability | `global.enabled` | true | Master instability switch. |
| balance / fluids | `<fluid>.seabanned`, `.cardrank`, `.grammar`, `.instability.factor_accessibility`, `.instability.factor_flat` | per fluid (optional) | Fluid symbol tuning. |
| balance / baselining | `<blockkey>` | see §6.3 | Baseline free values when `useconfigs`. |
| symbols / symbol.<modid> | `<symbolpath>.enabled` | true | Disable individual symbols. |
| instabilities / instability | `<providerid>.enabled` | true | Disable instability providers. |

`MystConfig.getOptional` reads a string; empty ⇒ default (so optional keys stay blank in the file).

---

## 14. Network Packets (channel "Mystcraft")

| # | Packet | Direction | Payload | Purpose |
|---|---|---|---|---|
| 0 | `MPacketAgeData` | S→C | dim id, AgeData NBT (compressed) | Sync age data. |
| 1 | `MPacketConfigs` | S→C | serverLabels bool | Server label permission. |
| 2 | `MPacketDimensions` | S→C | int count + ids | Register Mystcraft dims on the client. |
| 3 | `MPacketProfilingState` | S→C | running bool | Profiling toast. |
| 4 | `MPacketExplosion` | S→C | x,y,z, size, effect ids, relative block positions | Client-side explosion effects. |
| 5 | `MPacketSpawnLightningBolt` | S→C | entity id, pos, optional rgb | Coloured lightning. |
| 6 | `MPacketParticles` | S→C | x,y,z, particle name | 50 link particles. |
| 7/8 | `MPacketGuiMessage` | both | window id + NBT | Container messages (§8). |

Helpers: `sendAgeData(player, dim)`, `sendMessageToPlayersInWorld`, `sendMessageToAdmins`.

---

## 15. Public API (`com.xcompwiz.mystcraft.api`)

* Entry: `MystObjects.entryPoint.getProviderInstance()` → `APIInstanceProvider.getAPIInstance("<name>-<version>")` (per calling mod). Names (all version 1): `dimension`, `grammar`, `instability`, `instabilityfact`, `itemfact`, `linking`, `linkingprop`, `page`, `render`, `symbol`, `symbolfact`, `symbolvals`, `word`. Exceptions: `APIUndefined`, `APIVersionUndefined`, `APIVersionRemoved`.
* `DimensionAPI`: `getAllAges()`, `isMystcraftAge(id)`, `createAge()`.
* `GrammarAPI`: `registerGrammarRule(parent, rank, tokens…)`, `getSymbolsExpandingToken`, `getTokensProducingToken`, `generateFromToken(root, rand[, written])`.
* `InstabilityAPI`: `registerInstability(id, provider, cost)`, `addCards(deck, id, count)`, `getAllInstabilityProviders`, `getInstabilityProvider`. `InstabilityFactory.createProviderForEffect(effectClass, useLevel, ctorArgs…)`.
* `ItemFactory`: `buildPage`, `buildSymbolPage`, `buildLinkPage(props…)`, `buildCollectionItem(name, tokens…| pages…)`.
* `LinkingAPI`: `isLinkAllowed`, `linkEntity`, `createLinkInfoFromPosition`, `createLinkInfo(nbt)`. `LinkPropertyAPI`: register properties/colours, add item property probabilities, get gradient.
* `PageAPI`: `hasLinkPanel`, `getPageLinkProperties`, `isPageWritable`, `get/setPageSymbol`.
* `RenderAPI`: `registerRenderEffect(ILinkPanelEffect)`, `drawWord`, `drawSymbol`, `drawColorEye`.
* `SymbolAPI`: `blacklistIdentifier`, `getAllRegisteredSymbols`, `getSymbol`, `getSymbolOwner`. `SymbolFactory.createSymbol(blockstate, thirdWord, rank, CategoryPair…)` (block symbols). `SymbolValuesAPI`: card rank, purchasable, item weight, tradable, trade items. `WordAPI.registerWord(name, components|DrawableWord)`.
* Symbols themselves: implement `IAgeSymbol` and register into the `IAgeSymbol` Forge registry (or `SymbolAPI`). `AgeDirector` (§4.2) is the construction interface; logic interfaces: `IBiomeController`, `ITerrainGenerator`, `ILightingController`, `IWeatherController`, `ICelestial`, `ITerrainAlteration` (+`IPrimerFilter`), `IChunkProviderFinalization`, `IPopulate`, `ITerrainFeatureLocator`, `ISpawnModifier` (empty), `IDynamicColorProvider` (cloud/fog/sky), `IStaticColorProvider` (foliage/grass/water), `IEnvironmentalEffect`. Helpers: `ModifierUtils`, `BlockCategory` (registerable), `BlockDescriptor`, `Color`, `ColorGradient`, `Modifier`, `StorageObject`.
* Events (Forge bus): `LinkEvent.{LinkEventAllow (cancelable), LinkEventAlter, LinkEventStart, LinkEventFailed, LinkEventEnd, LinkEventExitWorld, LinkEventEnterWorld}`, `PortalLinkEvent`, `StarFissureLinkEvent`, `MeteorEvent.{MetorSpawn, MetorImpact, MetorExplosion}`, `DenseOresEvent`, `ContainedItemTooltipEvent` (tooltip of a page inside a container item).
* Item interfaces: §2.10.
* IMC messages (key → payload): `blockinstability` (NBT: `ItemStack` or `BlockName`, `Metadata`, `Accessibility`, `Flat`), `blacklistfluid` (string fluid name), `blacklist` (string symbol id), `fluidsymboldata` (NBT `fluidname`, `seabanned`, `cardrank`, `grammarrank`, `factor1`, `factor2`), `meteorblock` (NBT block + `Weight`).
* Outgoing IMC: to `lookingglass` ("API" → `LookingGlassIntegration.register`) and `reccomplex` (`registerDimensionType MYSTCRAFT_PROFILING`, `registerDimension` for the profiling dim id).
* CraftTweaker: `mods.mystcraft.CTAgeSymbol`, `mods.mystcraft.symbol.CTBlockSymbol` (ZenScript wrappers for symbol values/creation).
---

## 16. Localization

Full `en_US.lang` dump is in **Appendix A** (1657 keys; 1456 of them are banner pattern names of the form `item.banner.mystcraft_<word>.<color>=<Word> in <Color>` for the 65 words + numerals 0–25 × 16 dye colours). Other locales shipped: cs_CZ, de_DE, en_GB, en_PT, es_ES, fi_FI, fr_FR, it_IT, la_LA, nl_NL, pt_BR, ru_RU, zh_CN, zh_TW (translate from the en_US keys).

Key families: `myst.profiling.*`, `commands.myst.*`, `entity.Mystcraft.myst.*`, `tile.myst.*`, `fluid.myst.ink.black`, `item.myst.*`, `itemGroup.mystcraft.*`, `myst.creative.notebook.*`, `advancements.myst.root.*`, `achievement.myst.*`, `linkeffect.*.name`, `instability.bonus.*`, `myst.symbol.*.name` (+ `biome.wrapper`, `block.wrapper`), `item.banner.mystcraft_*`.

Names produced in code rather than lang: direction symbols ("North Direction" …), phase symbols ("Nadir Phase", "Rising Phase", "Zenith Phase", "Setting Phase"), length symbols ("Zero Length", "Half Length", "Full Length", "Double Length"), biome symbols (`%s Biome` with a de-camel-cased biome class name), block symbols (`%s Block` from the item name), GUI button tooltips ("Sort Alphabetically", "Show all Symbols", "Missing Link Panel", "Add a link panel as the first page of the book.", "Mark book as dead. (NO UNDO!)", "Confirm mark book as dead. (NO UNDO!)"), profiling GUI text, startup error texts, and admin command feedback strings.

---

## 17. Assets Inventory

```
assets/mystcraft/
  advancements/ root, symbol, write, agebook, linkbook, dimension, quinn (json)
  blockstates/  blockbookbinder, blockbookreceptacle, blockbookstand (OBJ), blockcrystal, blockdecay,
                blockinkmixer, blocklectern (OBJ), blocklinkmodifier, blockstarfissure, fluids, linkportal, writingdesk
  gui/          bookui_cover.png, bookui_pagel.png, bookui_pager.png, bookui_rpage_full.png, inkmixer.png,
                notebook.png, pagebinder.png, scrollable.png, scrollbar.png, single_slot.png, tradeshop.png, writingdesk.png
  lang/         15 .lang files
  loot_tables/  mystcraft_treasure.json
  models/block/ blockcrystal, blockdecay_{black,red,green,blue,purple,yellow,white}, blockmodel_nothing,
                blockmodel_unrendered_desk, blockstarfissure_placeholder, bookbinder_model, bookreceptacle_model,
                inkwell_model, linkmodifier_model, linkportal_{ew,full,ns,ud}
  models/item/  agebook, blockbookbinder, blockbookreceptacle, blockcrystal, blockdecay, blockinkmixer, blocklinkmodifier,
                blockstarfissure, booster, decay_{7 colours}, desk_bottom, desk_top, folder, glasses, linkbook, linkportal,
                page, portfolio, unlinkedbook, vial; obj/bookstand.obj+.mtl, obj/lectern.obj+.mtl
  recipes/      bookbinder, bookstand, folder, inkmixer, inkvial, inkvial_bucket, lectern, portfolio, receptacle,
                writingdesk, writingdesk_back
  shaders/      linkeffect.frag/.vert, linkingpanel.frag/.vert (LookingGlass link-panel shader)
  sounds/       linking/{pop,link,link-disarm,link-following,link-intra,link-fissure,link-portal}.ogg, entity/meteor/roar.ogg
  sounds.json
  textures/blocks/   book_receptacle, bookbinder_{bottom,side,top}, crystal, decay_{7}, fluid(+mcmeta), fluid_flow(+mcmeta),
                     inkmixer_{bottom,side,top}, linkmodifier_{bottom,side1,side2,top}, portal(+mcmeta animated)
  textures/entity/   agebook.png, linkbook.png (book model skins), bookstand.png, desk.png, lectern.png,
                     bookbinder/{bookbinder,press_part}.png, inkwell/{ink,ink_glass,inkwell_base,inkwell_cup}.png, linkmodifier/
  textures/items/    agebook, booster, deskext, folder, folder_filled, glasses, ink_vial(+mcmeta), inkvial, inkvialmask,
                     linkingbook, page_background, portfolio, unlinked, wrapper, writingdesk
  textures/models/   bookstand.png, glasses.png, lectern_texture.png
  textures/villager/ archivist.png
  textures/symbolcomponents.png (512×512, 8×8 grid of 64px glyph components)
  textures/eastercomponents.png (Easter-egg glyphs)
mystcraft_logo.png, mcmod.info
```

Notable custom rendering:

| Feature | Requirement |
|---|---|
| Page textures | Generated at texture-stitch time per symbol (§2.2). Symbol glyphs drawn from word components tinted (blend by source alpha) onto the page background. |
| Symbol drawing in GUIs (`GuiUtils.drawSymbol`) | 4 words drawn in a diamond: word0 top-centre, word1 right, word2 bottom, word3 left, each at half scale; a page with no symbol draws component 0 centred. Easter (date-based, Easter Sunday) replaces glyphs with coloured egg components. |
| Sky | Custom `IRenderHandler`: sky dome coloured by sky colour, celestials rendered by the AgeController (suns/moons/stars/rainbow with sunset horizon bands), optional void (below horizon) and horizon planes per `shouldRenderVoid/Horizon`. |
| Clouds | Custom cloud renderer using cloud colour and age cloud height (fast and fancy paths mirror vanilla). |
| Weather | Custom rain/snow renderer honouring per-biome wrapper temperature. |
| Block colours | Grass/tallgrass/leaves/vines/lily pad/melon stem/water use age static colour providers with vanilla fallback. Portal tinted by receptacle book colour; ink fluid tinted 0x191919. |
| TESRs | Writing Desk (model with optional backboard and paper stack; open book or item on the desk), Book Receptacle (closed book), Bookstand (open book 1.05), Lectern (open book 1.22 / map / item), Ink Mixer (page decoration), Star Fissure (end-portal style layers). |
| Entities | Book entity (open book model, hurt tint), meteor (`ModelMeteor`, end-portal texture), falling block, coloured lightning. |
| Link panel effects | Disarm lightning overlay; LookingGlass world view (shaders `linkingpanel.*` with uniforms texture, damage, resolution, time, waveScale, colorScale, linkColor). |
| Ink mixer eye | `DniColorRenderer` draws a D'ni-style colour glyph for the property gradient. |
| Rainbow | `RenderRainbow.renderRainbow(angle, width 50)` HSV arc. |
| Labels | Floating name labels within 25 blocks when enabled. |
| Banners | Each word registers a banner pattern `mystcraft_<word>` (hash `mystcraft.<word>`) with a generated 64×64 layer image (word glyph inverted onto banner base) for banners and shields; creative tab lists them. |

---

## 18. Sound Events

| Id | File | Category | Use |
|---|---|---|---|
| `mystcraft:linking.pop` | linking/pop.ogg | player | Item/book entity links |
| `mystcraft:linking.link` | linking/link.ogg | player | Default link |
| `mystcraft:linking.link-disarm` | linking/link-disarm.ogg | player | Disarm links |
| `mystcraft:linking.link-following` | linking/link-following.ogg | player | Following links |
| `mystcraft:linking.link-intra` | linking/link-intra.ogg | player | Intra-linking |
| `mystcraft:linking.link-fissure` | linking/link-fissure.ogg | player | Star Fissure |
| `mystcraft:linking.link-portal` | linking/link-portal.ogg | player | Crystal portal |
| `mystcraft:entity.meteor.roar` | entity/meteor/roar.ogg | hostile | Meteor moving sound (loop, volume 2, no attenuation) |

Also uses vanilla `entity.lightning.thunder` (meteor spawn, volume 10000) and `entity.generic.explode` (advanced explosions, volume 4).

---

## 19. Other Notable Systems

### 19.1 Ink effects registry (`InkEffects`)
Properties registered with colours (§7.2). Item bindings by exact ItemStack (count normalised to 1), by ore-dictionary name, or by Item; lookup order: itemstack → oredict → item. Sum of an item's probabilities may not exceed 1 (runtime exception).

### 19.2 Page/book durability
Linking items have float health (max 10 stored as `MaxHealth`, damage stored as `damage`); shown as durability bar. Book entities take damage (fire ×2, starvation 1/10000 ticks, drowning); when the entity health hits 0 the entity dies and the book is lost. Item `isDamaged` = health ≠ max.

### 19.3 Age profiling / symbol discovery
No in-game "discovery" mechanic exists beyond loot/trade; however, `SymbolProfiler` classifies symbols by provided interfaces for fallback selection and startup validation, and `ChunkProfiler` (§6.2) profiles Age chunks for instability.

### 19.4 Symbol remappings (`SymbolRemappings`)
Applied to pages/books/age data on load. A remapping maps an old id to a list of new ids (one page can become several). Rules: ids in domain `modmat_*` → domain stripped, path prefixed `modmat_`; domain `minecraft` → `mystcraft`. Table (old → new…): `ModMat_tile.stone→ModMat_minecraft:stone_0`, `ModMat_tile.lava→ModMat_minecraft:flowing_lava_0`, `ModMat_tile.water→ModMat_minecraft:flowing_water_0`, `modmat_flowing_water_0→modmat_water_0`, `Mod<Colour>→ModColor<Colour>` (16 colours; "ModDark Green"→ModColorDarkGreen), `LavaLakes→ModMat_tile.lava, LakesDeep`, `Lakes→ModMat_tile.water, LakesSurface`, `CryFormCry→ModMat_tile.myst.crystal, CryForm`, `CryFormGlow→ModMat_tile.myst.lightgem, CryForm`, `CryFormQuartz→ModMat_tile.myst.netherquartz, CryForm`, `Standard Terrain→TerrainNormal`, `Star Fissure→StarFissure`, `Rain→WeatherRain`, `Snow→WeatherSnow`, `Huge Trees→HugeTrees`, `NormalStars→StarsNormal`, `Single Biome→BioConSingle`, `Checkerboard Biomes→BioConTiled`, `BiomeControllerNative→BioConNative`, `Lava Lakes→LavaLakes`, `WeatherSun→WeatherOff`, `Standard Lighting→LightingNormal`, `Storm→WeatherStorm`, `Fog→ColorFog`, `ModFluid_tile.lava/ModFluidtile.lava/ModLavaSea→ModMat_tile.lava`, `ModFluid_tile.water/ModFluidtile.water→ModMat_tile.water`, `ModNetherTerrain/ModMattile.hellrock→ModMat_tile.hellrock`, `ModMattile.whiteStone→ModMat_tile.whiteStone`, `ModMattile.oreDiamond→ModMat_tile.oreDiamond`, `TendrilsIce→ModMat_tile.ice, Tendrils`, `WoodCaves→Tendrils`, `SkyDropDark→StarsDark`, `FTime→ModHalf,SunNormal,ModHalf,MoonNormal`, `STime→ModDouble,SunNormal,ModDouble,MoonNormal`, `NTime→ModFull,SunNormal,ModFull,MoonNormal`, `Dusk→ModZero,ModSetting,SunNormal,ModZero,MoonNormal`, `Night→SunDark,ModZero,MoonNormal`, `Day→MoonDark,ModZero,ModNoon,SunNormal`, `Heavy Resources→DenseOres`, `SunsetNormal/Normal Sunset Colors→SunsetRed`, `CloudNormal→CloudWhite`, `NativeBiomeController→BioConLarge`, `Flat Sea→TerrainFlat`, `Sky Islands→Skylands`, `Tree Age→Huge Trees,TerrainFlat,Swampland,BioConSingle`, `DefaultBiome→BioConSingle`, `DefaultLighting→Standard Lighting`, `DefaultSunrise→Normal Sunset Colors`, `DefaultTerrain→Standard Terrain`, `Flat→TerrainFlat`, `Void→TerrainVoid`; colour-family remaps: `Fog{Chromatic,Red,Green,Blue,Black,White,Normal}`, `Cloud{…}`, `Sky{…,Normal=Blue}`, `Sunset{…}` → `Mod<Colour>, Color{Fog|Cloud|Sky|Horizon}` where Chromatic = `ModBlack,ModRed,ModRed,ModGradient,ModBlack,ModGreen,ModGreen,ModGradient,ModBlack,ModBlue,ModBlue,ModGradient` then the colour symbol; `ModGradient_HERE→ModGradient,ColorSky`. Note remaps are not recursive at lookup (the `Mod<Colour>` targets of the family remaps are themselves old names; the original relies on repeated `onUpdate` remapping passes).

### 19.5 Advancements
`mystcraft:root` (icon agebook, stone background, tick trigger, hidden toast), `symbol` (page in inventory), `write` (custom trigger `mystcraft:writing_desk_write`), `agebook` (agebook in inventory), `linkbook` (unlinkedbook in inventory), `dimension` (`mystcraft:enter_myst_dimension_safe`), `quinn` (hidden, `mystcraft:enter_myst_dimension_quinn`).

### 19.6 Words & banners
`DrawableWord` = list of component indices (+ optional colours, optional custom image source). Numbers 0–25 map to glyph components: 0→{1}, ≥25→{2}, tens digit component 60–63 for 5/10/15/20, units component 55+n. Each registered word adds a banner pattern (enum-extended in 1.12); textures generated at runtime.

### 19.7 Seasonal
On Easter Sunday (computed Gregorian algorithm) symbols are drawn as coloured eggs (`eastercomponents.png`, 4 components per word, deterministic colours from word hash).

### 19.8 Debug hierarchy
`/myst-dbg` exposes: `global.profiler.file_output` (dump profile maps to `logs/profiling/*.txt`), `ages.<agedata_id>.instability.{bonus.*, blocks.<blockkey>}`, `ages.<id>.experimental.mark_dead`, `data.instability_calc.{profiled_chunks, freevals.*, profiled.*}`.

### 19.9 Saved data summary

| Storage | Where | Contents |
|---|---|---|
| `agedata_<id>` | overworld map storage | AgeData (§5.2) |
| `MystChunkProfile` | per-Age world storage | chunk profile maps |
| `MystInstabilityData` | per-Age world storage | deck orders |
| `myst_baseline` | overworld map storage (or `<mcdir>/mystcraft/` when not per-save) | baseline chunk profile |
| `mystcraft-linking-permissions` | overworld map storage | `PermitDepart`, `PermitEntry`, `RestrictDepart`, `RestrictEntry` (player → int list) |
| Player persisted NBT | `myst.dimUUID` | last dimension UUID |
| Villager entity data | `Mystcraft.Trade` | shop inventory |

### 19.10 Startup order (for reference)
Pre-init: API, packets, registries, event handlers, configs, structure ids, remappings, grammar core rules, dimension type, world generator, creative tabs, link listeners, fluids/items/blocks, ink effects, recipes, fluid symbol defaults, advancement triggers, sounds, client proxy pre-init, `ModSymbols.initialize` (+modifiers), words, symbol rules, instability data. Registry events: symbols/blocks/items/recipes/sounds; on biome registration (lowest priority) biome symbols and fluid symbols are generated. Init: IMC to LookingGlass/RecComplex, GUI handler, tile entities, entities, loot tables, archivist, village handler, client init. Post-init: client post-init, card ranks, symbol rules → grammar, `buildGrammar` (shortest paths + rank weights), baseline balance data, archivist booster trade. Server start: commands, profiler thread, baseline profiling, dimension registration, permissions load.

---

## Appendix A — `en_US.lang` (complete)

```properties
myst.profiling.running=Expect Lag (Profiling)
myst.profiling.running.message=Mystcraft is baking a cake.
myst.profiling.complete=Ding!
myst.profiling.complete.message=Mystcraft Profiling Complete

commands.myst.generic.player.notfound=Could not get Player by name: %s

commands.myst.tpx.usage=/tpx [subject:player] <target:player | target:dim [x y z]>
commands.myst.tpx.fail.nosubject=Teleport subject invalid. No tp.
commands.myst.tpx.fail.noworld=Could not get world for dimension %d. No tp.

commands.myst.time.usage=/time <set|add> <value> [dim]
commands.myst.time.fail.noworld=Target Dimension is not Loaded
commands.myst.time.set=Set the time to %d in dimension %d
commands.myst.time.set.all=Set the time to %d in all dimensions
commands.myst.time.added=Added %d to the time in dimension %d
commands.myst.time.added.all=Added %d to the time in all dimensions

commands.myst.toggledownfall.usage=/toggledownfall [dim]
commands.myst.downfall.success=Toggled downfall in dimension %d
commands.myst.downfall.fail.nodim=No dimension set.

commands.myst.agebook.usage=/myst-agebook [dim]
commands.myst.twi.usage=/myst-twi [dim] [true|false]
commands.myst.meteor.usage=/myst-spawnmeteor
commands.myst.permissions.usage=/myst-permissions <player> <restrict|permit> <entry|depart> <all|dimid>
commands.myst.chunkregen.usage=/myst-regenchunk [range] [dimId] [chunkX] [chunkZ]

entity.Mystcraft.myst.book.name=Book
entity.Mystcraft.myst.block.name=Falling Block
entity.Mystcraft.myst.meteor.name=Meteor

tile.myst.receptacle.name=Book Receptacle
tile.myst.unstable.name=Decay Effect
tile.myst.unstable.blue.name=Blue Decay
tile.myst.unstable.red.name=Red Decay
tile.myst.unstable.purple.name=Purple Decay
tile.myst.unstable.white.name=White Decay
tile.myst.unstable.black.name=Black Decay
tile.myst.crystal.name=Crystal
tile.myst.lectern.name=Lectern
tile.myst.bookstand.name=Bookstand
tile.myst.linkmodifier.name=Link Modifier
tile.myst.bookbinder.name=Book Binder
tile.myst.inkmixer.name=Ink Mixer
tile.myst.linkportal.name=Link Portal
tile.myst.starfissure.name=Star Fissure
tile.myst.writing_desk.name=Writing Desk

tile.myst.ink.black.name=Black Ink

fluid.myst.ink.black=Black Ink

item.myst.agebook.name=Descriptive Book
item.myst.linkbook.name=Linking Book
item.myst.folder.name=Collation Folder
item.myst.portfolio.name=Symbol Portfolio
item.myst.booster.name=Sealed Notebook
item.myst.page.blank.name=Blank Page
item.myst.page.panel.name=Link Panel Page
item.myst.page.symbol.name=Page
item.myst.unlinkedbook.name=Unlinked Link Book
item.myst.writingdesk.name=Writing Desk
item.myst.writingdesk.top.name=Writing Desk Backboard
item.myst.vial.name=Ink Vial
item.myst.glasses.name=XComp's Glasses

itemGroup.mystcraft.common=Mystcraft
itemGroup.mystcraft.pages=Mystcraft Pages

myst.creative.notebook.wrapper=Spawned (%s)
myst.creative.notebook.all=All Symbols
myst.creative.notebook.biomedist=Biome Distributions
myst.creative.notebook.celestials=Celestials
myst.creative.notebook.effects=Effects
myst.creative.notebook.lighting=Lighting
myst.creative.notebook.modbasic=Modifiers, Basic
myst.creative.notebook.modbiome=Modifiers, Biomes
myst.creative.notebook.modblock=Modifiers, Block
myst.creative.notebook.modcolor=Modifiers, Colors
myst.creative.notebook.features=World Features
myst.creative.notebook.terrain=World Landscapes
myst.creative.notebook.visuals=Visuals
myst.creative.notebook.weather=Weather

advancements.myst.root.title=Mystcraft
advancements.myst.root.description=

achievement.myst.symbol=Small Step of the Journey
achievement.myst.symbol.desc=Find a Symbol
achievement.myst.write=The Art: A Primer
achievement.myst.write.desc=Copy A Symbol
achievement.myst.agebook=One Way Ticket
achievement.myst.agebook.desc=Craft a Descriptive Book. Don't forget it's a one way trip!
achievement.myst.linkbook=Tie a String
achievement.myst.linkbook.desc=Craft a Linking Book. Good, now you can get back!
achievement.myst.quinn=Call Me Quinn
achievement.myst.quinn.desc=Travel to a dimension without a linking book. Better hope there's a way out!
achievement.myst.safe=The Way Back
achievement.myst.safe.desc=Travel to a dimension while carrying a linking book.

linkeffect.disarm.name=Disarm
linkeffect.generateplatform.name=Generate Platform
linkeffect.intralinking.name=Intra-Linking
linkeffect.intralinkingonly.name=Intra-Linking Only
linkeffect.maintainmomentum.name=Maintain Momentum
linkeffect.relative.name=Relative Link

instability.bonus.death=%s has been killed by %s, activating bonus stability!
instability.bonus.death.partial=%s has died.  Unlocking part of the bonus stability.  Kill them personally to unlock all of it!
instability.bonus.death.alert=%s has entered the age.  Kill them in order to unlock a temporary stability bonus in this age!

instability.bonus.troll.death=%s has been killed, resetting the instability penalty!
instability.bonus.troll.alert=%s has entered the age.  The age will slowly become less stable!  Kill them to reset this!
instability.bonus.troll.left=%s has left the age.  The stability penalty will slowly dissipate.

myst.symbol.biome.wrapper=%s Biome
myst.symbol.block.wrapper=%s Block

myst.symbol.biocongrid.name=Grid-form Biome Distribution
myst.symbol.bioconhuge.name=Huge Biome Distribution
myst.symbol.bioconlarge.name=Large Biome Distribution
myst.symbol.bioconmedium.name=Medium Biome Distribution
myst.symbol.bioconnative.name=Native Biome Distribution
myst.symbol.bioconsingle.name=Single Biome Distribution
myst.symbol.bioconsmall.name=Small Biome Distribution
myst.symbol.biocontiled.name=Tiled Biome Distribution
myst.symbol.biocontiny.name=Tiny Biome Distribution
myst.symbol.caves.name=Caves
myst.symbol.colorcloud.name=Cloud Color
myst.symbol.colorcloudnat.name=Natural Cloud Color
myst.symbol.colorfog.name=Fog Color
myst.symbol.colorfognat.name=Natural Fog Color
myst.symbol.colorfoliage.name=Foliage Color
myst.symbol.colorfoliagenat.name=Natural Foliage Color
myst.symbol.colorgrass.name=Grass Color
myst.symbol.colorgrassnat.name=Natural Grass Color
myst.symbol.colorhorizon.name=Sunset Color
myst.symbol.colorsky.name=Sky Color
myst.symbol.colorskynat.name=Natural Sky Color
myst.symbol.colorskynight.name=Night Sky Color
myst.symbol.colorwater.name=Water Color
myst.symbol.colorwaternat.name=Natural Water Color
myst.symbol.cryform.name=Crystalline Formations
myst.symbol.denseores.name=Dense Ores
myst.symbol.dungeons.name=Dungeons
myst.symbol.envaccel.name=Accelerated
myst.symbol.envexplosions.name=Spontaneous Explosions
myst.symbol.envlightning.name=Lightning
myst.symbol.envmeteor.name=Meteors
myst.symbol.envscorch.name=Scorched Surface
myst.symbol.featurelargedummy.name=Lacking Large Features
myst.symbol.featuremediumdummy.name=Lacking Medium Features
myst.symbol.featuresmalldummy.name=Lacking Small Features
myst.symbol.flat.name=Flat World
myst.symbol.floatislands.name=Floating Islands
myst.symbol.genspikes.name=Spikes
myst.symbol.hugetrees.name=Huge Trees
myst.symbol.lakesdeep.name=Deep Lakes
myst.symbol.lakessurface.name=Surface Lakes
myst.symbol.lightingbright.name=Bright Lighting
myst.symbol.lightingdark.name=Dark Lighting
myst.symbol.lightingnormal.name=Normal Lighting
myst.symbol.mineshafts.name=Mineshafts
myst.symbol.modclear.name=Clear Modifiers
myst.symbol.modcolormaroon.name=Maroon Color
myst.symbol.modcolorred.name=Red Color
myst.symbol.modcolorolive.name=Olive Color
myst.symbol.modcoloryellow.name=Yellow Color
myst.symbol.modcolordarkgreen.name=Dark Green Color
myst.symbol.modcolorgreen.name=Green Color
myst.symbol.modcolorteal.name=Teal Color
myst.symbol.modcolorcyan.name=Cyan Color
myst.symbol.modcolornavy.name=Navy Color
myst.symbol.modcolorblue.name=Blue Color
myst.symbol.modcolorpurple.name=Purple Color
myst.symbol.modcolormagenta.name=Magenta Color
myst.symbol.modcolorblack.name=Black Color
myst.symbol.modcolorgrey.name=Grey Color
myst.symbol.modcolorsilver.name=Silver Color
myst.symbol.modcolorwhite.name=White Color
myst.symbol.modgradient.name=Gradient
myst.symbol.moondark.name=Dark Moon
myst.symbol.moonnormal.name=Normal Moon
myst.symbol.netherfort.name=Nether Fortress
myst.symbol.nohorizon.name=Boundless Sky
myst.symbol.nosea.name=No Seas
myst.symbol.obelisks.name=Obelisks
myst.symbol.pvpoff.name=Anti-PvP
myst.symbol.rainbow.name=Rainbow
myst.symbol.ravines.name=Ravines
myst.symbol.skylands.name=Skylands
myst.symbol.starfissure.name=Star Fissure
myst.symbol.starsdark.name=Dark Stars
myst.symbol.starsendsky.name=Ender Starfield
myst.symbol.starsnormal.name=Normal Stars
myst.symbol.starstwinkle.name=Twinkling Stars
myst.symbol.strongholds.name=Strongholds
myst.symbol.sundark.name=Dark Sun
myst.symbol.sunnormal.name=Normal Sun
myst.symbol.tendrils.name=Tendrils
myst.symbol.termodspheres.name=Spheres
myst.symbol.terrainamplified.name=Amplified Normal World
myst.symbol.terrainend.name=Island World
myst.symbol.terrainnether.name=Cave World
myst.symbol.terrainnormal.name=Standard World
myst.symbol.terrainflat.name=Flat World
myst.symbol.terrainvoid.name=Void World
myst.symbol.weathercloudy.name=Overcast
myst.symbol.weatherfast.name=Fast Weather
myst.symbol.weathernorm.name=Normal Weather
myst.symbol.weatheroff.name=No Weather
myst.symbol.weatheron.name=Eternal Weather
myst.symbol.weatherrain.name=Eternal Rain
myst.symbol.weatherslow.name=Slow Weather
myst.symbol.weathersnow.name=Eternal Snow
myst.symbol.weatherstorm.name=Eternal Storm
myst.symbol.villages.name=Villages
myst.symbol.void.name=Void World

itemGroup.mystcraft.banners=Mystcraft Banners
item.banner.mystcraft_balance.white=Balance in White
item.banner.mystcraft_balance.orange=Balance in Orange
item.banner.mystcraft_balance.magenta=Balance in Magenta
item.banner.mystcraft_balance.lightBlue=Balance in Light Blue
item.banner.mystcraft_balance.yellow=Balance in Yellow
item.banner.mystcraft_balance.lime=Balance in Lime
item.banner.mystcraft_balance.pink=Balance in Pink
item.banner.mystcraft_balance.gray=Balance in Gray
item.banner.mystcraft_balance.silver=Balance in Silver
item.banner.mystcraft_balance.cyan=Balance in Cyan
item.banner.mystcraft_balance.purple=Balance in Purple
item.banner.mystcraft_balance.blue=Balance in Blue
item.banner.mystcraft_balance.brown=Balance in Brown
item.banner.mystcraft_balance.green=Balance in Green
item.banner.mystcraft_balance.red=Balance in Red
item.banner.mystcraft_balance.black=Balance in Black
item.banner.mystcraft_believe.white=Believe in White
item.banner.mystcraft_believe.orange=Believe in Orange
item.banner.mystcraft_believe.magenta=Believe in Magenta
item.banner.mystcraft_believe.lightBlue=Believe in Light Blue
item.banner.mystcraft_believe.yellow=Believe in Yellow
item.banner.mystcraft_believe.lime=Believe in Lime
item.banner.mystcraft_believe.pink=Believe in Pink
item.banner.mystcraft_believe.gray=Believe in Gray
item.banner.mystcraft_believe.silver=Believe in Silver
item.banner.mystcraft_believe.cyan=Believe in Cyan
item.banner.mystcraft_believe.purple=Believe in Purple
item.banner.mystcraft_believe.blue=Believe in Blue
item.banner.mystcraft_believe.brown=Believe in Brown
item.banner.mystcraft_believe.green=Believe in Green
item.banner.mystcraft_believe.red=Believe in Red
item.banner.mystcraft_believe.black=Believe in Black
item.banner.mystcraft_change.white=Change in White
item.banner.mystcraft_change.orange=Change in Orange
item.banner.mystcraft_change.magenta=Change in Magenta
item.banner.mystcraft_change.lightBlue=Change in Light Blue
item.banner.mystcraft_change.yellow=Change in Yellow
item.banner.mystcraft_change.lime=Change in Lime
item.banner.mystcraft_change.pink=Change in Pink
item.banner.mystcraft_change.gray=Change in Gray
item.banner.mystcraft_change.silver=Change in Silver
item.banner.mystcraft_change.cyan=Change in Cyan
item.banner.mystcraft_change.purple=Change in Purple
item.banner.mystcraft_change.blue=Change in Blue
item.banner.mystcraft_change.brown=Change in Brown
item.banner.mystcraft_change.green=Change in Green
item.banner.mystcraft_change.red=Change in Red
item.banner.mystcraft_change.black=Change in Black
item.banner.mystcraft_chaos.white=Chaos in White
item.banner.mystcraft_chaos.orange=Chaos in Orange
item.banner.mystcraft_chaos.magenta=Chaos in Magenta
item.banner.mystcraft_chaos.lightBlue=Chaos in Light Blue
item.banner.mystcraft_chaos.yellow=Chaos in Yellow
item.banner.mystcraft_chaos.lime=Chaos in Lime
item.banner.mystcraft_chaos.pink=Chaos in Pink
item.banner.mystcraft_chaos.gray=Chaos in Gray
item.banner.mystcraft_chaos.silver=Chaos in Silver
item.banner.mystcraft_chaos.cyan=Chaos in Cyan
item.banner.mystcraft_chaos.purple=Chaos in Purple
item.banner.mystcraft_chaos.blue=Chaos in Blue
item.banner.mystcraft_chaos.brown=Chaos in Brown
item.banner.mystcraft_chaos.green=Chaos in Green
item.banner.mystcraft_chaos.red=Chaos in Red
item.banner.mystcraft_chaos.black=Chaos in Black
item.banner.mystcraft_civilization.white=Civilization in White
item.banner.mystcraft_civilization.orange=Civilization in Orange
item.banner.mystcraft_civilization.magenta=Civilization in Magenta
item.banner.mystcraft_civilization.lightBlue=Civilization in Light Blue
item.banner.mystcraft_civilization.yellow=Civilization in Yellow
item.banner.mystcraft_civilization.lime=Civilization in Lime
item.banner.mystcraft_civilization.pink=Civilization in Pink
item.banner.mystcraft_civilization.gray=Civilization in Gray
item.banner.mystcraft_civilization.silver=Civilization in Silver
item.banner.mystcraft_civilization.cyan=Civilization in Cyan
item.banner.mystcraft_civilization.purple=Civilization in Purple
item.banner.mystcraft_civilization.blue=Civilization in Blue
item.banner.mystcraft_civilization.brown=Civilization in Brown
item.banner.mystcraft_civilization.green=Civilization in Green
item.banner.mystcraft_civilization.red=Civilization in Red
item.banner.mystcraft_civilization.black=Civilization in Black
item.banner.mystcraft_constraint.white=Constraint in White
item.banner.mystcraft_constraint.orange=Constraint in Orange
item.banner.mystcraft_constraint.magenta=Constraint in Magenta
item.banner.mystcraft_constraint.lightBlue=Constraint in Light Blue
item.banner.mystcraft_constraint.yellow=Constraint in Yellow
item.banner.mystcraft_constraint.lime=Constraint in Lime
item.banner.mystcraft_constraint.pink=Constraint in Pink
item.banner.mystcraft_constraint.gray=Constraint in Gray
item.banner.mystcraft_constraint.silver=Constraint in Silver
item.banner.mystcraft_constraint.cyan=Constraint in Cyan
item.banner.mystcraft_constraint.purple=Constraint in Purple
item.banner.mystcraft_constraint.blue=Constraint in Blue
item.banner.mystcraft_constraint.brown=Constraint in Brown
item.banner.mystcraft_constraint.green=Constraint in Green
item.banner.mystcraft_constraint.red=Constraint in Red
item.banner.mystcraft_constraint.black=Constraint in Black
item.banner.mystcraft_contradict.white=Contradict in White
item.banner.mystcraft_contradict.orange=Contradict in Orange
item.banner.mystcraft_contradict.magenta=Contradict in Magenta
item.banner.mystcraft_contradict.lightBlue=Contradict in Light Blue
item.banner.mystcraft_contradict.yellow=Contradict in Yellow
item.banner.mystcraft_contradict.lime=Contradict in Lime
item.banner.mystcraft_contradict.pink=Contradict in Pink
item.banner.mystcraft_contradict.gray=Contradict in Gray
item.banner.mystcraft_contradict.silver=Contradict in Silver
item.banner.mystcraft_contradict.cyan=Contradict in Cyan
item.banner.mystcraft_contradict.purple=Contradict in Purple
item.banner.mystcraft_contradict.blue=Contradict in Blue
item.banner.mystcraft_contradict.brown=Contradict in Brown
item.banner.mystcraft_contradict.green=Contradict in Green
item.banner.mystcraft_contradict.red=Contradict in Red
item.banner.mystcraft_contradict.black=Contradict in Black
item.banner.mystcraft_control.white=Control in White
item.banner.mystcraft_control.orange=Control in Orange
item.banner.mystcraft_control.magenta=Control in Magenta
item.banner.mystcraft_control.lightBlue=Control in Light Blue
item.banner.mystcraft_control.yellow=Control in Yellow
item.banner.mystcraft_control.lime=Control in Lime
item.banner.mystcraft_control.pink=Control in Pink
item.banner.mystcraft_control.gray=Control in Gray
item.banner.mystcraft_control.silver=Control in Silver
item.banner.mystcraft_control.cyan=Control in Cyan
item.banner.mystcraft_control.purple=Control in Purple
item.banner.mystcraft_control.blue=Control in Blue
item.banner.mystcraft_control.brown=Control in Brown
item.banner.mystcraft_control.green=Control in Green
item.banner.mystcraft_control.red=Control in Red
item.banner.mystcraft_control.black=Control in Black
item.banner.mystcraft_convey.white=Convey in White
item.banner.mystcraft_convey.orange=Convey in Orange
item.banner.mystcraft_convey.magenta=Convey in Magenta
item.banner.mystcraft_convey.lightBlue=Convey in Light Blue
item.banner.mystcraft_convey.yellow=Convey in Yellow
item.banner.mystcraft_convey.lime=Convey in Lime
item.banner.mystcraft_convey.pink=Convey in Pink
item.banner.mystcraft_convey.gray=Convey in Gray
item.banner.mystcraft_convey.silver=Convey in Silver
item.banner.mystcraft_convey.cyan=Convey in Cyan
item.banner.mystcraft_convey.purple=Convey in Purple
item.banner.mystcraft_convey.blue=Convey in Blue
item.banner.mystcraft_convey.brown=Convey in Brown
item.banner.mystcraft_convey.green=Convey in Green
item.banner.mystcraft_convey.red=Convey in Red
item.banner.mystcraft_convey.black=Convey in Black
item.banner.mystcraft_creativity.white=Creativity in White
item.banner.mystcraft_creativity.orange=Creativity in Orange
item.banner.mystcraft_creativity.magenta=Creativity in Magenta
item.banner.mystcraft_creativity.lightBlue=Creativity in Light Blue
item.banner.mystcraft_creativity.yellow=Creativity in Yellow
item.banner.mystcraft_creativity.lime=Creativity in Lime
item.banner.mystcraft_creativity.pink=Creativity in Pink
item.banner.mystcraft_creativity.gray=Creativity in Gray
item.banner.mystcraft_creativity.silver=Creativity in Silver
item.banner.mystcraft_creativity.cyan=Creativity in Cyan
item.banner.mystcraft_creativity.purple=Creativity in Purple
item.banner.mystcraft_creativity.blue=Creativity in Blue
item.banner.mystcraft_creativity.brown=Creativity in Brown
item.banner.mystcraft_creativity.green=Creativity in Green
item.banner.mystcraft_creativity.red=Creativity in Red
item.banner.mystcraft_creativity.black=Creativity in Black
item.banner.mystcraft_cycle.white=Cycle in White
item.banner.mystcraft_cycle.orange=Cycle in Orange
item.banner.mystcraft_cycle.magenta=Cycle in Magenta
item.banner.mystcraft_cycle.lightBlue=Cycle in Light Blue
item.banner.mystcraft_cycle.yellow=Cycle in Yellow
item.banner.mystcraft_cycle.lime=Cycle in Lime
item.banner.mystcraft_cycle.pink=Cycle in Pink
item.banner.mystcraft_cycle.gray=Cycle in Gray
item.banner.mystcraft_cycle.silver=Cycle in Silver
item.banner.mystcraft_cycle.cyan=Cycle in Cyan
item.banner.mystcraft_cycle.purple=Cycle in Purple
item.banner.mystcraft_cycle.blue=Cycle in Blue
item.banner.mystcraft_cycle.brown=Cycle in Brown
item.banner.mystcraft_cycle.green=Cycle in Green
item.banner.mystcraft_cycle.red=Cycle in Red
item.banner.mystcraft_cycle.black=Cycle in Black
item.banner.mystcraft_dependence.white=Dependence in White
item.banner.mystcraft_dependence.orange=Dependence in Orange
item.banner.mystcraft_dependence.magenta=Dependence in Magenta
item.banner.mystcraft_dependence.lightBlue=Dependence in Light Blue
item.banner.mystcraft_dependence.yellow=Dependence in Yellow
item.banner.mystcraft_dependence.lime=Dependence in Lime
item.banner.mystcraft_dependence.pink=Dependence in Pink
item.banner.mystcraft_dependence.gray=Dependence in Gray
item.banner.mystcraft_dependence.silver=Dependence in Silver
item.banner.mystcraft_dependence.cyan=Dependence in Cyan
item.banner.mystcraft_dependence.purple=Dependence in Purple
item.banner.mystcraft_dependence.blue=Dependence in Blue
item.banner.mystcraft_dependence.brown=Dependence in Brown
item.banner.mystcraft_dependence.green=Dependence in Green
item.banner.mystcraft_dependence.red=Dependence in Red
item.banner.mystcraft_dependence.black=Dependence in Black
item.banner.mystcraft_discover.white=Discover in White
item.banner.mystcraft_discover.orange=Discover in Orange
item.banner.mystcraft_discover.magenta=Discover in Magenta
item.banner.mystcraft_discover.lightBlue=Discover in Light Blue
item.banner.mystcraft_discover.yellow=Discover in Yellow
item.banner.mystcraft_discover.lime=Discover in Lime
item.banner.mystcraft_discover.pink=Discover in Pink
item.banner.mystcraft_discover.gray=Discover in Gray
item.banner.mystcraft_discover.silver=Discover in Silver
item.banner.mystcraft_discover.cyan=Discover in Cyan
item.banner.mystcraft_discover.purple=Discover in Purple
item.banner.mystcraft_discover.blue=Discover in Blue
item.banner.mystcraft_discover.brown=Discover in Brown
item.banner.mystcraft_discover.green=Discover in Green
item.banner.mystcraft_discover.red=Discover in Red
item.banner.mystcraft_discover.black=Discover in Black
item.banner.mystcraft_dynamic.white=Dynamic in White
item.banner.mystcraft_dynamic.orange=Dynamic in Orange
item.banner.mystcraft_dynamic.magenta=Dynamic in Magenta
item.banner.mystcraft_dynamic.lightBlue=Dynamic in Light Blue
item.banner.mystcraft_dynamic.yellow=Dynamic in Yellow
item.banner.mystcraft_dynamic.lime=Dynamic in Lime
item.banner.mystcraft_dynamic.pink=Dynamic in Pink
item.banner.mystcraft_dynamic.gray=Dynamic in Gray
item.banner.mystcraft_dynamic.silver=Dynamic in Silver
item.banner.mystcraft_dynamic.cyan=Dynamic in Cyan
item.banner.mystcraft_dynamic.purple=Dynamic in Purple
item.banner.mystcraft_dynamic.blue=Dynamic in Blue
item.banner.mystcraft_dynamic.brown=Dynamic in Brown
item.banner.mystcraft_dynamic.green=Dynamic in Green
item.banner.mystcraft_dynamic.red=Dynamic in Red
item.banner.mystcraft_dynamic.black=Dynamic in Black
item.banner.mystcraft_elevate.white=Elevate in White
item.banner.mystcraft_elevate.orange=Elevate in Orange
item.banner.mystcraft_elevate.magenta=Elevate in Magenta
item.banner.mystcraft_elevate.lightBlue=Elevate in Light Blue
item.banner.mystcraft_elevate.yellow=Elevate in Yellow
item.banner.mystcraft_elevate.lime=Elevate in Lime
item.banner.mystcraft_elevate.pink=Elevate in Pink
item.banner.mystcraft_elevate.gray=Elevate in Gray
item.banner.mystcraft_elevate.silver=Elevate in Silver
item.banner.mystcraft_elevate.cyan=Elevate in Cyan
item.banner.mystcraft_elevate.purple=Elevate in Purple
item.banner.mystcraft_elevate.blue=Elevate in Blue
item.banner.mystcraft_elevate.brown=Elevate in Brown
item.banner.mystcraft_elevate.green=Elevate in Green
item.banner.mystcraft_elevate.red=Elevate in Red
item.banner.mystcraft_elevate.black=Elevate in Black
item.banner.mystcraft_encourage.white=Encourage in White
item.banner.mystcraft_encourage.orange=Encourage in Orange
item.banner.mystcraft_encourage.magenta=Encourage in Magenta
item.banner.mystcraft_encourage.lightBlue=Encourage in Light Blue
item.banner.mystcraft_encourage.yellow=Encourage in Yellow
item.banner.mystcraft_encourage.lime=Encourage in Lime
item.banner.mystcraft_encourage.pink=Encourage in Pink
item.banner.mystcraft_encourage.gray=Encourage in Gray
item.banner.mystcraft_encourage.silver=Encourage in Silver
item.banner.mystcraft_encourage.cyan=Encourage in Cyan
item.banner.mystcraft_encourage.purple=Encourage in Purple
item.banner.mystcraft_encourage.blue=Encourage in Blue
item.banner.mystcraft_encourage.brown=Encourage in Brown
item.banner.mystcraft_encourage.green=Encourage in Green
item.banner.mystcraft_encourage.red=Encourage in Red
item.banner.mystcraft_encourage.black=Encourage in Black
item.banner.mystcraft_energy.white=Energy in White
item.banner.mystcraft_energy.orange=Energy in Orange
item.banner.mystcraft_energy.magenta=Energy in Magenta
item.banner.mystcraft_energy.lightBlue=Energy in Light Blue
item.banner.mystcraft_energy.yellow=Energy in Yellow
item.banner.mystcraft_energy.lime=Energy in Lime
item.banner.mystcraft_energy.pink=Energy in Pink
item.banner.mystcraft_energy.gray=Energy in Gray
item.banner.mystcraft_energy.silver=Energy in Silver
item.banner.mystcraft_energy.cyan=Energy in Cyan
item.banner.mystcraft_energy.purple=Energy in Purple
item.banner.mystcraft_energy.blue=Energy in Blue
item.banner.mystcraft_energy.brown=Energy in Brown
item.banner.mystcraft_energy.green=Energy in Green
item.banner.mystcraft_energy.red=Energy in Red
item.banner.mystcraft_energy.black=Energy in Black
item.banner.mystcraft_entropy.white=Entropy in White
item.banner.mystcraft_entropy.orange=Entropy in Orange
item.banner.mystcraft_entropy.magenta=Entropy in Magenta
item.banner.mystcraft_entropy.lightBlue=Entropy in Light Blue
item.banner.mystcraft_entropy.yellow=Entropy in Yellow
item.banner.mystcraft_entropy.lime=Entropy in Lime
item.banner.mystcraft_entropy.pink=Entropy in Pink
item.banner.mystcraft_entropy.gray=Entropy in Gray
item.banner.mystcraft_entropy.silver=Entropy in Silver
item.banner.mystcraft_entropy.cyan=Entropy in Cyan
item.banner.mystcraft_entropy.purple=Entropy in Purple
item.banner.mystcraft_entropy.blue=Entropy in Blue
item.banner.mystcraft_entropy.brown=Entropy in Brown
item.banner.mystcraft_entropy.green=Entropy in Green
item.banner.mystcraft_entropy.red=Entropy in Red
item.banner.mystcraft_entropy.black=Entropy in Black
item.banner.mystcraft_ethereal.white=Ethereal in White
item.banner.mystcraft_ethereal.orange=Ethereal in Orange
item.banner.mystcraft_ethereal.magenta=Ethereal in Magenta
item.banner.mystcraft_ethereal.lightBlue=Ethereal in Light Blue
item.banner.mystcraft_ethereal.yellow=Ethereal in Yellow
item.banner.mystcraft_ethereal.lime=Ethereal in Lime
item.banner.mystcraft_ethereal.pink=Ethereal in Pink
item.banner.mystcraft_ethereal.gray=Ethereal in Gray
item.banner.mystcraft_ethereal.silver=Ethereal in Silver
item.banner.mystcraft_ethereal.cyan=Ethereal in Cyan
item.banner.mystcraft_ethereal.purple=Ethereal in Purple
item.banner.mystcraft_ethereal.blue=Ethereal in Blue
item.banner.mystcraft_ethereal.brown=Ethereal in Brown
item.banner.mystcraft_ethereal.green=Ethereal in Green
item.banner.mystcraft_ethereal.red=Ethereal in Red
item.banner.mystcraft_ethereal.black=Ethereal in Black
item.banner.mystcraft_exist.white=Exist in White
item.banner.mystcraft_exist.orange=Exist in Orange
item.banner.mystcraft_exist.magenta=Exist in Magenta
item.banner.mystcraft_exist.lightBlue=Exist in Light Blue
item.banner.mystcraft_exist.yellow=Exist in Yellow
item.banner.mystcraft_exist.lime=Exist in Lime
item.banner.mystcraft_exist.pink=Exist in Pink
item.banner.mystcraft_exist.gray=Exist in Gray
item.banner.mystcraft_exist.silver=Exist in Silver
item.banner.mystcraft_exist.cyan=Exist in Cyan
item.banner.mystcraft_exist.purple=Exist in Purple
item.banner.mystcraft_exist.blue=Exist in Blue
item.banner.mystcraft_exist.brown=Exist in Brown
item.banner.mystcraft_exist.green=Exist in Green
item.banner.mystcraft_exist.red=Exist in Red
item.banner.mystcraft_exist.black=Exist in Black
item.banner.mystcraft_explore.white=Explore in White
item.banner.mystcraft_explore.orange=Explore in Orange
item.banner.mystcraft_explore.magenta=Explore in Magenta
item.banner.mystcraft_explore.lightBlue=Explore in Light Blue
item.banner.mystcraft_explore.yellow=Explore in Yellow
item.banner.mystcraft_explore.lime=Explore in Lime
item.banner.mystcraft_explore.pink=Explore in Pink
item.banner.mystcraft_explore.gray=Explore in Gray
item.banner.mystcraft_explore.silver=Explore in Silver
item.banner.mystcraft_explore.cyan=Explore in Cyan
item.banner.mystcraft_explore.purple=Explore in Purple
item.banner.mystcraft_explore.blue=Explore in Blue
item.banner.mystcraft_explore.brown=Explore in Brown
item.banner.mystcraft_explore.green=Explore in Green
item.banner.mystcraft_explore.red=Explore in Red
item.banner.mystcraft_explore.black=Explore in Black
item.banner.mystcraft_flow.white=Flow in White
item.banner.mystcraft_flow.orange=Flow in Orange
item.banner.mystcraft_flow.magenta=Flow in Magenta
item.banner.mystcraft_flow.lightBlue=Flow in Light Blue
item.banner.mystcraft_flow.yellow=Flow in Yellow
item.banner.mystcraft_flow.lime=Flow in Lime
item.banner.mystcraft_flow.pink=Flow in Pink
item.banner.mystcraft_flow.gray=Flow in Gray
item.banner.mystcraft_flow.silver=Flow in Silver
item.banner.mystcraft_flow.cyan=Flow in Cyan
item.banner.mystcraft_flow.purple=Flow in Purple
item.banner.mystcraft_flow.blue=Flow in Blue
item.banner.mystcraft_flow.brown=Flow in Brown
item.banner.mystcraft_flow.green=Flow in Green
item.banner.mystcraft_flow.red=Flow in Red
item.banner.mystcraft_flow.black=Flow in Black
item.banner.mystcraft_force.white=Force in White
item.banner.mystcraft_force.orange=Force in Orange
item.banner.mystcraft_force.magenta=Force in Magenta
item.banner.mystcraft_force.lightBlue=Force in Light Blue
item.banner.mystcraft_force.yellow=Force in Yellow
item.banner.mystcraft_force.lime=Force in Lime
item.banner.mystcraft_force.pink=Force in Pink
item.banner.mystcraft_force.gray=Force in Gray
item.banner.mystcraft_force.silver=Force in Silver
item.banner.mystcraft_force.cyan=Force in Cyan
item.banner.mystcraft_force.purple=Force in Purple
item.banner.mystcraft_force.blue=Force in Blue
item.banner.mystcraft_force.brown=Force in Brown
item.banner.mystcraft_force.green=Force in Green
item.banner.mystcraft_force.red=Force in Red
item.banner.mystcraft_force.black=Force in Black
item.banner.mystcraft_form.white=Form in White
item.banner.mystcraft_form.orange=Form in Orange
item.banner.mystcraft_form.magenta=Form in Magenta
item.banner.mystcraft_form.lightBlue=Form in Light Blue
item.banner.mystcraft_form.yellow=Form in Yellow
item.banner.mystcraft_form.lime=Form in Lime
item.banner.mystcraft_form.pink=Form in Pink
item.banner.mystcraft_form.gray=Form in Gray
item.banner.mystcraft_form.silver=Form in Silver
item.banner.mystcraft_form.cyan=Form in Cyan
item.banner.mystcraft_form.purple=Form in Purple
item.banner.mystcraft_form.blue=Form in Blue
item.banner.mystcraft_form.brown=Form in Brown
item.banner.mystcraft_form.green=Form in Green
item.banner.mystcraft_form.red=Form in Red
item.banner.mystcraft_form.black=Form in Black
item.banner.mystcraft_future.white=Future in White
item.banner.mystcraft_future.orange=Future in Orange
item.banner.mystcraft_future.magenta=Future in Magenta
item.banner.mystcraft_future.lightBlue=Future in Light Blue
item.banner.mystcraft_future.yellow=Future in Yellow
item.banner.mystcraft_future.lime=Future in Lime
item.banner.mystcraft_future.pink=Future in Pink
item.banner.mystcraft_future.gray=Future in Gray
item.banner.mystcraft_future.silver=Future in Silver
item.banner.mystcraft_future.cyan=Future in Cyan
item.banner.mystcraft_future.purple=Future in Purple
item.banner.mystcraft_future.blue=Future in Blue
item.banner.mystcraft_future.brown=Future in Brown
item.banner.mystcraft_future.green=Future in Green
item.banner.mystcraft_future.red=Future in Red
item.banner.mystcraft_future.black=Future in Black
item.banner.mystcraft_growth.white=Growth in White
item.banner.mystcraft_growth.orange=Growth in Orange
item.banner.mystcraft_growth.magenta=Growth in Magenta
item.banner.mystcraft_growth.lightBlue=Growth in Light Blue
item.banner.mystcraft_growth.yellow=Growth in Yellow
item.banner.mystcraft_growth.lime=Growth in Lime
item.banner.mystcraft_growth.pink=Growth in Pink
item.banner.mystcraft_growth.gray=Growth in Gray
item.banner.mystcraft_growth.silver=Growth in Silver
item.banner.mystcraft_growth.cyan=Growth in Cyan
item.banner.mystcraft_growth.purple=Growth in Purple
item.banner.mystcraft_growth.blue=Growth in Blue
item.banner.mystcraft_growth.brown=Growth in Brown
item.banner.mystcraft_growth.green=Growth in Green
item.banner.mystcraft_growth.red=Growth in Red
item.banner.mystcraft_growth.black=Growth in Black
item.banner.mystcraft_harmony.white=Harmony in White
item.banner.mystcraft_harmony.orange=Harmony in Orange
item.banner.mystcraft_harmony.magenta=Harmony in Magenta
item.banner.mystcraft_harmony.lightBlue=Harmony in Light Blue
item.banner.mystcraft_harmony.yellow=Harmony in Yellow
item.banner.mystcraft_harmony.lime=Harmony in Lime
item.banner.mystcraft_harmony.pink=Harmony in Pink
item.banner.mystcraft_harmony.gray=Harmony in Gray
item.banner.mystcraft_harmony.silver=Harmony in Silver
item.banner.mystcraft_harmony.cyan=Harmony in Cyan
item.banner.mystcraft_harmony.purple=Harmony in Purple
item.banner.mystcraft_harmony.blue=Harmony in Blue
item.banner.mystcraft_harmony.brown=Harmony in Brown
item.banner.mystcraft_harmony.green=Harmony in Green
item.banner.mystcraft_harmony.red=Harmony in Red
item.banner.mystcraft_harmony.black=Harmony in Black
item.banner.mystcraft_honor.white=Honor in White
item.banner.mystcraft_honor.orange=Honor in Orange
item.banner.mystcraft_honor.magenta=Honor in Magenta
item.banner.mystcraft_honor.lightBlue=Honor in Light Blue
item.banner.mystcraft_honor.yellow=Honor in Yellow
item.banner.mystcraft_honor.lime=Honor in Lime
item.banner.mystcraft_honor.pink=Honor in Pink
item.banner.mystcraft_honor.gray=Honor in Gray
item.banner.mystcraft_honor.silver=Honor in Silver
item.banner.mystcraft_honor.cyan=Honor in Cyan
item.banner.mystcraft_honor.purple=Honor in Purple
item.banner.mystcraft_honor.blue=Honor in Blue
item.banner.mystcraft_honor.brown=Honor in Brown
item.banner.mystcraft_honor.green=Honor in Green
item.banner.mystcraft_honor.red=Honor in Red
item.banner.mystcraft_honor.black=Honor in Black
item.banner.mystcraft_infinite.white=Infinite in White
item.banner.mystcraft_infinite.orange=Infinite in Orange
item.banner.mystcraft_infinite.magenta=Infinite in Magenta
item.banner.mystcraft_infinite.lightBlue=Infinite in Light Blue
item.banner.mystcraft_infinite.yellow=Infinite in Yellow
item.banner.mystcraft_infinite.lime=Infinite in Lime
item.banner.mystcraft_infinite.pink=Infinite in Pink
item.banner.mystcraft_infinite.gray=Infinite in Gray
item.banner.mystcraft_infinite.silver=Infinite in Silver
item.banner.mystcraft_infinite.cyan=Infinite in Cyan
item.banner.mystcraft_infinite.purple=Infinite in Purple
item.banner.mystcraft_infinite.blue=Infinite in Blue
item.banner.mystcraft_infinite.brown=Infinite in Brown
item.banner.mystcraft_infinite.green=Infinite in Green
item.banner.mystcraft_infinite.red=Infinite in Red
item.banner.mystcraft_infinite.black=Infinite in Black
item.banner.mystcraft_inhibit.white=Inhibit in White
item.banner.mystcraft_inhibit.orange=Inhibit in Orange
item.banner.mystcraft_inhibit.magenta=Inhibit in Magenta
item.banner.mystcraft_inhibit.lightBlue=Inhibit in Light Blue
item.banner.mystcraft_inhibit.yellow=Inhibit in Yellow
item.banner.mystcraft_inhibit.lime=Inhibit in Lime
item.banner.mystcraft_inhibit.pink=Inhibit in Pink
item.banner.mystcraft_inhibit.gray=Inhibit in Gray
item.banner.mystcraft_inhibit.silver=Inhibit in Silver
item.banner.mystcraft_inhibit.cyan=Inhibit in Cyan
item.banner.mystcraft_inhibit.purple=Inhibit in Purple
item.banner.mystcraft_inhibit.blue=Inhibit in Blue
item.banner.mystcraft_inhibit.brown=Inhibit in Brown
item.banner.mystcraft_inhibit.green=Inhibit in Green
item.banner.mystcraft_inhibit.red=Inhibit in Red
item.banner.mystcraft_inhibit.black=Inhibit in Black
item.banner.mystcraft_intelligence.white=Intelligence in White
item.banner.mystcraft_intelligence.orange=Intelligence in Orange
item.banner.mystcraft_intelligence.magenta=Intelligence in Magenta
item.banner.mystcraft_intelligence.lightBlue=Intelligence in Light Blue
item.banner.mystcraft_intelligence.yellow=Intelligence in Yellow
item.banner.mystcraft_intelligence.lime=Intelligence in Lime
item.banner.mystcraft_intelligence.pink=Intelligence in Pink
item.banner.mystcraft_intelligence.gray=Intelligence in Gray
item.banner.mystcraft_intelligence.silver=Intelligence in Silver
item.banner.mystcraft_intelligence.cyan=Intelligence in Cyan
item.banner.mystcraft_intelligence.purple=Intelligence in Purple
item.banner.mystcraft_intelligence.blue=Intelligence in Blue
item.banner.mystcraft_intelligence.brown=Intelligence in Brown
item.banner.mystcraft_intelligence.green=Intelligence in Green
item.banner.mystcraft_intelligence.red=Intelligence in Red
item.banner.mystcraft_intelligence.black=Intelligence in Black
item.banner.mystcraft_love.white=Love in White
item.banner.mystcraft_love.orange=Love in Orange
item.banner.mystcraft_love.magenta=Love in Magenta
item.banner.mystcraft_love.lightBlue=Love in Light Blue
item.banner.mystcraft_love.yellow=Love in Yellow
item.banner.mystcraft_love.lime=Love in Lime
item.banner.mystcraft_love.pink=Love in Pink
item.banner.mystcraft_love.gray=Love in Gray
item.banner.mystcraft_love.silver=Love in Silver
item.banner.mystcraft_love.cyan=Love in Cyan
item.banner.mystcraft_love.purple=Love in Purple
item.banner.mystcraft_love.blue=Love in Blue
item.banner.mystcraft_love.brown=Love in Brown
item.banner.mystcraft_love.green=Love in Green
item.banner.mystcraft_love.red=Love in Red
item.banner.mystcraft_love.black=Love in Black
item.banner.mystcraft_machine.white=Machine in White
item.banner.mystcraft_machine.orange=Machine in Orange
item.banner.mystcraft_machine.magenta=Machine in Magenta
item.banner.mystcraft_machine.lightBlue=Machine in Light Blue
item.banner.mystcraft_machine.yellow=Machine in Yellow
item.banner.mystcraft_machine.lime=Machine in Lime
item.banner.mystcraft_machine.pink=Machine in Pink
item.banner.mystcraft_machine.gray=Machine in Gray
item.banner.mystcraft_machine.silver=Machine in Silver
item.banner.mystcraft_machine.cyan=Machine in Cyan
item.banner.mystcraft_machine.purple=Machine in Purple
item.banner.mystcraft_machine.blue=Machine in Blue
item.banner.mystcraft_machine.brown=Machine in Brown
item.banner.mystcraft_machine.green=Machine in Green
item.banner.mystcraft_machine.red=Machine in Red
item.banner.mystcraft_machine.black=Machine in Black
item.banner.mystcraft_merge.white=Merge in White
item.banner.mystcraft_merge.orange=Merge in Orange
item.banner.mystcraft_merge.magenta=Merge in Magenta
item.banner.mystcraft_merge.lightBlue=Merge in Light Blue
item.banner.mystcraft_merge.yellow=Merge in Yellow
item.banner.mystcraft_merge.lime=Merge in Lime
item.banner.mystcraft_merge.pink=Merge in Pink
item.banner.mystcraft_merge.gray=Merge in Gray
item.banner.mystcraft_merge.silver=Merge in Silver
item.banner.mystcraft_merge.cyan=Merge in Cyan
item.banner.mystcraft_merge.purple=Merge in Purple
item.banner.mystcraft_merge.blue=Merge in Blue
item.banner.mystcraft_merge.brown=Merge in Brown
item.banner.mystcraft_merge.green=Merge in Green
item.banner.mystcraft_merge.red=Merge in Red
item.banner.mystcraft_merge.black=Merge in Black
item.banner.mystcraft_momentum.white=Momentum in White
item.banner.mystcraft_momentum.orange=Momentum in Orange
item.banner.mystcraft_momentum.magenta=Momentum in Magenta
item.banner.mystcraft_momentum.lightBlue=Momentum in Light Blue
item.banner.mystcraft_momentum.yellow=Momentum in Yellow
item.banner.mystcraft_momentum.lime=Momentum in Lime
item.banner.mystcraft_momentum.pink=Momentum in Pink
item.banner.mystcraft_momentum.gray=Momentum in Gray
item.banner.mystcraft_momentum.silver=Momentum in Silver
item.banner.mystcraft_momentum.cyan=Momentum in Cyan
item.banner.mystcraft_momentum.purple=Momentum in Purple
item.banner.mystcraft_momentum.blue=Momentum in Blue
item.banner.mystcraft_momentum.brown=Momentum in Brown
item.banner.mystcraft_momentum.green=Momentum in Green
item.banner.mystcraft_momentum.red=Momentum in Red
item.banner.mystcraft_momentum.black=Momentum in Black
item.banner.mystcraft_motion.white=Motion in White
item.banner.mystcraft_motion.orange=Motion in Orange
item.banner.mystcraft_motion.magenta=Motion in Magenta
item.banner.mystcraft_motion.lightBlue=Motion in Light Blue
item.banner.mystcraft_motion.yellow=Motion in Yellow
item.banner.mystcraft_motion.lime=Motion in Lime
item.banner.mystcraft_motion.pink=Motion in Pink
item.banner.mystcraft_motion.gray=Motion in Gray
item.banner.mystcraft_motion.silver=Motion in Silver
item.banner.mystcraft_motion.cyan=Motion in Cyan
item.banner.mystcraft_motion.purple=Motion in Purple
item.banner.mystcraft_motion.blue=Motion in Blue
item.banner.mystcraft_motion.brown=Motion in Brown
item.banner.mystcraft_motion.green=Motion in Green
item.banner.mystcraft_motion.red=Motion in Red
item.banner.mystcraft_motion.black=Motion in Black
item.banner.mystcraft_mutual.white=Mutual in White
item.banner.mystcraft_mutual.orange=Mutual in Orange
item.banner.mystcraft_mutual.magenta=Mutual in Magenta
item.banner.mystcraft_mutual.lightBlue=Mutual in Light Blue
item.banner.mystcraft_mutual.yellow=Mutual in Yellow
item.banner.mystcraft_mutual.lime=Mutual in Lime
item.banner.mystcraft_mutual.pink=Mutual in Pink
item.banner.mystcraft_mutual.gray=Mutual in Gray
item.banner.mystcraft_mutual.silver=Mutual in Silver
item.banner.mystcraft_mutual.cyan=Mutual in Cyan
item.banner.mystcraft_mutual.purple=Mutual in Purple
item.banner.mystcraft_mutual.blue=Mutual in Blue
item.banner.mystcraft_mutual.brown=Mutual in Brown
item.banner.mystcraft_mutual.green=Mutual in Green
item.banner.mystcraft_mutual.red=Mutual in Red
item.banner.mystcraft_mutual.black=Mutual in Black
item.banner.mystcraft_nature.white=Nature in White
item.banner.mystcraft_nature.orange=Nature in Orange
item.banner.mystcraft_nature.magenta=Nature in Magenta
item.banner.mystcraft_nature.lightBlue=Nature in Light Blue
item.banner.mystcraft_nature.yellow=Nature in Yellow
item.banner.mystcraft_nature.lime=Nature in Lime
item.banner.mystcraft_nature.pink=Nature in Pink
item.banner.mystcraft_nature.gray=Nature in Gray
item.banner.mystcraft_nature.silver=Nature in Silver
item.banner.mystcraft_nature.cyan=Nature in Cyan
item.banner.mystcraft_nature.purple=Nature in Purple
item.banner.mystcraft_nature.blue=Nature in Blue
item.banner.mystcraft_nature.brown=Nature in Brown
item.banner.mystcraft_nature.green=Nature in Green
item.banner.mystcraft_nature.red=Nature in Red
item.banner.mystcraft_nature.black=Nature in Black
item.banner.mystcraft_nurture.white=Nurture in White
item.banner.mystcraft_nurture.orange=Nurture in Orange
item.banner.mystcraft_nurture.magenta=Nurture in Magenta
item.banner.mystcraft_nurture.lightBlue=Nurture in Light Blue
item.banner.mystcraft_nurture.yellow=Nurture in Yellow
item.banner.mystcraft_nurture.lime=Nurture in Lime
item.banner.mystcraft_nurture.pink=Nurture in Pink
item.banner.mystcraft_nurture.gray=Nurture in Gray
item.banner.mystcraft_nurture.silver=Nurture in Silver
item.banner.mystcraft_nurture.cyan=Nurture in Cyan
item.banner.mystcraft_nurture.purple=Nurture in Purple
item.banner.mystcraft_nurture.blue=Nurture in Blue
item.banner.mystcraft_nurture.brown=Nurture in Brown
item.banner.mystcraft_nurture.green=Nurture in Green
item.banner.mystcraft_nurture.red=Nurture in Red
item.banner.mystcraft_nurture.black=Nurture in Black
item.banner.mystcraft_possibility.white=Possibility in White
item.banner.mystcraft_possibility.orange=Possibility in Orange
item.banner.mystcraft_possibility.magenta=Possibility in Magenta
item.banner.mystcraft_possibility.lightBlue=Possibility in Light Blue
item.banner.mystcraft_possibility.yellow=Possibility in Yellow
item.banner.mystcraft_possibility.lime=Possibility in Lime
item.banner.mystcraft_possibility.pink=Possibility in Pink
item.banner.mystcraft_possibility.gray=Possibility in Gray
item.banner.mystcraft_possibility.silver=Possibility in Silver
item.banner.mystcraft_possibility.cyan=Possibility in Cyan
item.banner.mystcraft_possibility.purple=Possibility in Purple
item.banner.mystcraft_possibility.blue=Possibility in Blue
item.banner.mystcraft_possibility.brown=Possibility in Brown
item.banner.mystcraft_possibility.green=Possibility in Green
item.banner.mystcraft_possibility.red=Possibility in Red
item.banner.mystcraft_possibility.black=Possibility in Black
item.banner.mystcraft_power.white=Power in White
item.banner.mystcraft_power.orange=Power in Orange
item.banner.mystcraft_power.magenta=Power in Magenta
item.banner.mystcraft_power.lightBlue=Power in Light Blue
item.banner.mystcraft_power.yellow=Power in Yellow
item.banner.mystcraft_power.lime=Power in Lime
item.banner.mystcraft_power.pink=Power in Pink
item.banner.mystcraft_power.gray=Power in Gray
item.banner.mystcraft_power.silver=Power in Silver
item.banner.mystcraft_power.cyan=Power in Cyan
item.banner.mystcraft_power.purple=Power in Purple
item.banner.mystcraft_power.blue=Power in Blue
item.banner.mystcraft_power.brown=Power in Brown
item.banner.mystcraft_power.green=Power in Green
item.banner.mystcraft_power.red=Power in Red
item.banner.mystcraft_power.black=Power in Black
item.banner.mystcraft_question.white=Question in White
item.banner.mystcraft_question.orange=Question in Orange
item.banner.mystcraft_question.magenta=Question in Magenta
item.banner.mystcraft_question.lightBlue=Question in Light Blue
item.banner.mystcraft_question.yellow=Question in Yellow
item.banner.mystcraft_question.lime=Question in Lime
item.banner.mystcraft_question.pink=Question in Pink
item.banner.mystcraft_question.gray=Question in Gray
item.banner.mystcraft_question.silver=Question in Silver
item.banner.mystcraft_question.cyan=Question in Cyan
item.banner.mystcraft_question.purple=Question in Purple
item.banner.mystcraft_question.blue=Question in Blue
item.banner.mystcraft_question.brown=Question in Brown
item.banner.mystcraft_question.green=Question in Green
item.banner.mystcraft_question.red=Question in Red
item.banner.mystcraft_question.black=Question in Black
item.banner.mystcraft_rebirth.white=Rebirth in White
item.banner.mystcraft_rebirth.orange=Rebirth in Orange
item.banner.mystcraft_rebirth.magenta=Rebirth in Magenta
item.banner.mystcraft_rebirth.lightBlue=Rebirth in Light Blue
item.banner.mystcraft_rebirth.yellow=Rebirth in Yellow
item.banner.mystcraft_rebirth.lime=Rebirth in Lime
item.banner.mystcraft_rebirth.pink=Rebirth in Pink
item.banner.mystcraft_rebirth.gray=Rebirth in Gray
item.banner.mystcraft_rebirth.silver=Rebirth in Silver
item.banner.mystcraft_rebirth.cyan=Rebirth in Cyan
item.banner.mystcraft_rebirth.purple=Rebirth in Purple
item.banner.mystcraft_rebirth.blue=Rebirth in Blue
item.banner.mystcraft_rebirth.brown=Rebirth in Brown
item.banner.mystcraft_rebirth.green=Rebirth in Green
item.banner.mystcraft_rebirth.red=Rebirth in Red
item.banner.mystcraft_rebirth.black=Rebirth in Black
item.banner.mystcraft_remember.white=Remember in White
item.banner.mystcraft_remember.orange=Remember in Orange
item.banner.mystcraft_remember.magenta=Remember in Magenta
item.banner.mystcraft_remember.lightBlue=Remember in Light Blue
item.banner.mystcraft_remember.yellow=Remember in Yellow
item.banner.mystcraft_remember.lime=Remember in Lime
item.banner.mystcraft_remember.pink=Remember in Pink
item.banner.mystcraft_remember.gray=Remember in Gray
item.banner.mystcraft_remember.silver=Remember in Silver
item.banner.mystcraft_remember.cyan=Remember in Cyan
item.banner.mystcraft_remember.purple=Remember in Purple
item.banner.mystcraft_remember.blue=Remember in Blue
item.banner.mystcraft_remember.brown=Remember in Brown
item.banner.mystcraft_remember.green=Remember in Green
item.banner.mystcraft_remember.red=Remember in Red
item.banner.mystcraft_remember.black=Remember in Black
item.banner.mystcraft_resilience.white=Resilience in White
item.banner.mystcraft_resilience.orange=Resilience in Orange
item.banner.mystcraft_resilience.magenta=Resilience in Magenta
item.banner.mystcraft_resilience.lightBlue=Resilience in Light Blue
item.banner.mystcraft_resilience.yellow=Resilience in Yellow
item.banner.mystcraft_resilience.lime=Resilience in Lime
item.banner.mystcraft_resilience.pink=Resilience in Pink
item.banner.mystcraft_resilience.gray=Resilience in Gray
item.banner.mystcraft_resilience.silver=Resilience in Silver
item.banner.mystcraft_resilience.cyan=Resilience in Cyan
item.banner.mystcraft_resilience.purple=Resilience in Purple
item.banner.mystcraft_resilience.blue=Resilience in Blue
item.banner.mystcraft_resilience.brown=Resilience in Brown
item.banner.mystcraft_resilience.green=Resilience in Green
item.banner.mystcraft_resilience.red=Resilience in Red
item.banner.mystcraft_resilience.black=Resilience in Black
item.banner.mystcraft_resurrect.white=Resurrect in White
item.banner.mystcraft_resurrect.orange=Resurrect in Orange
item.banner.mystcraft_resurrect.magenta=Resurrect in Magenta
item.banner.mystcraft_resurrect.lightBlue=Resurrect in Light Blue
item.banner.mystcraft_resurrect.yellow=Resurrect in Yellow
item.banner.mystcraft_resurrect.lime=Resurrect in Lime
item.banner.mystcraft_resurrect.pink=Resurrect in Pink
item.banner.mystcraft_resurrect.gray=Resurrect in Gray
item.banner.mystcraft_resurrect.silver=Resurrect in Silver
item.banner.mystcraft_resurrect.cyan=Resurrect in Cyan
item.banner.mystcraft_resurrect.purple=Resurrect in Purple
item.banner.mystcraft_resurrect.blue=Resurrect in Blue
item.banner.mystcraft_resurrect.brown=Resurrect in Brown
item.banner.mystcraft_resurrect.green=Resurrect in Green
item.banner.mystcraft_resurrect.red=Resurrect in Red
item.banner.mystcraft_resurrect.black=Resurrect in Black
item.banner.mystcraft_sacrifice.white=Sacrifice in White
item.banner.mystcraft_sacrifice.orange=Sacrifice in Orange
item.banner.mystcraft_sacrifice.magenta=Sacrifice in Magenta
item.banner.mystcraft_sacrifice.lightBlue=Sacrifice in Light Blue
item.banner.mystcraft_sacrifice.yellow=Sacrifice in Yellow
item.banner.mystcraft_sacrifice.lime=Sacrifice in Lime
item.banner.mystcraft_sacrifice.pink=Sacrifice in Pink
item.banner.mystcraft_sacrifice.gray=Sacrifice in Gray
item.banner.mystcraft_sacrifice.silver=Sacrifice in Silver
item.banner.mystcraft_sacrifice.cyan=Sacrifice in Cyan
item.banner.mystcraft_sacrifice.purple=Sacrifice in Purple
item.banner.mystcraft_sacrifice.blue=Sacrifice in Blue
item.banner.mystcraft_sacrifice.brown=Sacrifice in Brown
item.banner.mystcraft_sacrifice.green=Sacrifice in Green
item.banner.mystcraft_sacrifice.red=Sacrifice in Red
item.banner.mystcraft_sacrifice.black=Sacrifice in Black
item.banner.mystcraft_society.white=Society in White
item.banner.mystcraft_society.orange=Society in Orange
item.banner.mystcraft_society.magenta=Society in Magenta
item.banner.mystcraft_society.lightBlue=Society in Light Blue
item.banner.mystcraft_society.yellow=Society in Yellow
item.banner.mystcraft_society.lime=Society in Lime
item.banner.mystcraft_society.pink=Society in Pink
item.banner.mystcraft_society.gray=Society in Gray
item.banner.mystcraft_society.silver=Society in Silver
item.banner.mystcraft_society.cyan=Society in Cyan
item.banner.mystcraft_society.purple=Society in Purple
item.banner.mystcraft_society.blue=Society in Blue
item.banner.mystcraft_society.brown=Society in Brown
item.banner.mystcraft_society.green=Society in Green
item.banner.mystcraft_society.red=Society in Red
item.banner.mystcraft_society.black=Society in Black
item.banner.mystcraft_spur.white=Spur in White
item.banner.mystcraft_spur.orange=Spur in Orange
item.banner.mystcraft_spur.magenta=Spur in Magenta
item.banner.mystcraft_spur.lightBlue=Spur in Light Blue
item.banner.mystcraft_spur.yellow=Spur in Yellow
item.banner.mystcraft_spur.lime=Spur in Lime
item.banner.mystcraft_spur.pink=Spur in Pink
item.banner.mystcraft_spur.gray=Spur in Gray
item.banner.mystcraft_spur.silver=Spur in Silver
item.banner.mystcraft_spur.cyan=Spur in Cyan
item.banner.mystcraft_spur.purple=Spur in Purple
item.banner.mystcraft_spur.blue=Spur in Blue
item.banner.mystcraft_spur.brown=Spur in Brown
item.banner.mystcraft_spur.green=Spur in Green
item.banner.mystcraft_spur.red=Spur in Red
item.banner.mystcraft_spur.black=Spur in Black
item.banner.mystcraft_static.white=Static in White
item.banner.mystcraft_static.orange=Static in Orange
item.banner.mystcraft_static.magenta=Static in Magenta
item.banner.mystcraft_static.lightBlue=Static in Light Blue
item.banner.mystcraft_static.yellow=Static in Yellow
item.banner.mystcraft_static.lime=Static in Lime
item.banner.mystcraft_static.pink=Static in Pink
item.banner.mystcraft_static.gray=Static in Gray
item.banner.mystcraft_static.silver=Static in Silver
item.banner.mystcraft_static.cyan=Static in Cyan
item.banner.mystcraft_static.purple=Static in Purple
item.banner.mystcraft_static.blue=Static in Blue
item.banner.mystcraft_static.brown=Static in Brown
item.banner.mystcraft_static.green=Static in Green
item.banner.mystcraft_static.red=Static in Red
item.banner.mystcraft_static.black=Static in Black
item.banner.mystcraft_stimulate.white=Stimulate in White
item.banner.mystcraft_stimulate.orange=Stimulate in Orange
item.banner.mystcraft_stimulate.magenta=Stimulate in Magenta
item.banner.mystcraft_stimulate.lightBlue=Stimulate in Light Blue
item.banner.mystcraft_stimulate.yellow=Stimulate in Yellow
item.banner.mystcraft_stimulate.lime=Stimulate in Lime
item.banner.mystcraft_stimulate.pink=Stimulate in Pink
item.banner.mystcraft_stimulate.gray=Stimulate in Gray
item.banner.mystcraft_stimulate.silver=Stimulate in Silver
item.banner.mystcraft_stimulate.cyan=Stimulate in Cyan
item.banner.mystcraft_stimulate.purple=Stimulate in Purple
item.banner.mystcraft_stimulate.blue=Stimulate in Blue
item.banner.mystcraft_stimulate.brown=Stimulate in Brown
item.banner.mystcraft_stimulate.green=Stimulate in Green
item.banner.mystcraft_stimulate.red=Stimulate in Red
item.banner.mystcraft_stimulate.black=Stimulate in Black
item.banner.mystcraft_survival.white=Survival in White
item.banner.mystcraft_survival.orange=Survival in Orange
item.banner.mystcraft_survival.magenta=Survival in Magenta
item.banner.mystcraft_survival.lightBlue=Survival in Light Blue
item.banner.mystcraft_survival.yellow=Survival in Yellow
item.banner.mystcraft_survival.lime=Survival in Lime
item.banner.mystcraft_survival.pink=Survival in Pink
item.banner.mystcraft_survival.gray=Survival in Gray
item.banner.mystcraft_survival.silver=Survival in Silver
item.banner.mystcraft_survival.cyan=Survival in Cyan
item.banner.mystcraft_survival.purple=Survival in Purple
item.banner.mystcraft_survival.blue=Survival in Blue
item.banner.mystcraft_survival.brown=Survival in Brown
item.banner.mystcraft_survival.green=Survival in Green
item.banner.mystcraft_survival.red=Survival in Red
item.banner.mystcraft_survival.black=Survival in Black
item.banner.mystcraft_sustain.white=Sustain in White
item.banner.mystcraft_sustain.orange=Sustain in Orange
item.banner.mystcraft_sustain.magenta=Sustain in Magenta
item.banner.mystcraft_sustain.lightBlue=Sustain in Light Blue
item.banner.mystcraft_sustain.yellow=Sustain in Yellow
item.banner.mystcraft_sustain.lime=Sustain in Lime
item.banner.mystcraft_sustain.pink=Sustain in Pink
item.banner.mystcraft_sustain.gray=Sustain in Gray
item.banner.mystcraft_sustain.silver=Sustain in Silver
item.banner.mystcraft_sustain.cyan=Sustain in Cyan
item.banner.mystcraft_sustain.purple=Sustain in Purple
item.banner.mystcraft_sustain.blue=Sustain in Blue
item.banner.mystcraft_sustain.brown=Sustain in Brown
item.banner.mystcraft_sustain.green=Sustain in Green
item.banner.mystcraft_sustain.red=Sustain in Red
item.banner.mystcraft_sustain.black=Sustain in Black
item.banner.mystcraft_system.white=System in White
item.banner.mystcraft_system.orange=System in Orange
item.banner.mystcraft_system.magenta=System in Magenta
item.banner.mystcraft_system.lightBlue=System in Light Blue
item.banner.mystcraft_system.yellow=System in Yellow
item.banner.mystcraft_system.lime=System in Lime
item.banner.mystcraft_system.pink=System in Pink
item.banner.mystcraft_system.gray=System in Gray
item.banner.mystcraft_system.silver=System in Silver
item.banner.mystcraft_system.cyan=System in Cyan
item.banner.mystcraft_system.purple=System in Purple
item.banner.mystcraft_system.blue=System in Blue
item.banner.mystcraft_system.brown=System in Brown
item.banner.mystcraft_system.green=System in Green
item.banner.mystcraft_system.red=System in Red
item.banner.mystcraft_system.black=System in Black
item.banner.mystcraft_time.white=Time in White
item.banner.mystcraft_time.orange=Time in Orange
item.banner.mystcraft_time.magenta=Time in Magenta
item.banner.mystcraft_time.lightBlue=Time in Light Blue
item.banner.mystcraft_time.yellow=Time in Yellow
item.banner.mystcraft_time.lime=Time in Lime
item.banner.mystcraft_time.pink=Time in Pink
item.banner.mystcraft_time.gray=Time in Gray
item.banner.mystcraft_time.silver=Time in Silver
item.banner.mystcraft_time.cyan=Time in Cyan
item.banner.mystcraft_time.purple=Time in Purple
item.banner.mystcraft_time.blue=Time in Blue
item.banner.mystcraft_time.brown=Time in Brown
item.banner.mystcraft_time.green=Time in Green
item.banner.mystcraft_time.red=Time in Red
item.banner.mystcraft_time.black=Time in Black
item.banner.mystcraft_tradition.white=Tradition in White
item.banner.mystcraft_tradition.orange=Tradition in Orange
item.banner.mystcraft_tradition.magenta=Tradition in Magenta
item.banner.mystcraft_tradition.lightBlue=Tradition in Light Blue
item.banner.mystcraft_tradition.yellow=Tradition in Yellow
item.banner.mystcraft_tradition.lime=Tradition in Lime
item.banner.mystcraft_tradition.pink=Tradition in Pink
item.banner.mystcraft_tradition.gray=Tradition in Gray
item.banner.mystcraft_tradition.silver=Tradition in Silver
item.banner.mystcraft_tradition.cyan=Tradition in Cyan
item.banner.mystcraft_tradition.purple=Tradition in Purple
item.banner.mystcraft_tradition.blue=Tradition in Blue
item.banner.mystcraft_tradition.brown=Tradition in Brown
item.banner.mystcraft_tradition.green=Tradition in Green
item.banner.mystcraft_tradition.red=Tradition in Red
item.banner.mystcraft_tradition.black=Tradition in Black
item.banner.mystcraft_transform.white=Transform in White
item.banner.mystcraft_transform.orange=Transform in Orange
item.banner.mystcraft_transform.magenta=Transform in Magenta
item.banner.mystcraft_transform.lightBlue=Transform in Light Blue
item.banner.mystcraft_transform.yellow=Transform in Yellow
item.banner.mystcraft_transform.lime=Transform in Lime
item.banner.mystcraft_transform.pink=Transform in Pink
item.banner.mystcraft_transform.gray=Transform in Gray
item.banner.mystcraft_transform.silver=Transform in Silver
item.banner.mystcraft_transform.cyan=Transform in Cyan
item.banner.mystcraft_transform.purple=Transform in Purple
item.banner.mystcraft_transform.blue=Transform in Blue
item.banner.mystcraft_transform.brown=Transform in Brown
item.banner.mystcraft_transform.green=Transform in Green
item.banner.mystcraft_transform.red=Transform in Red
item.banner.mystcraft_transform.black=Transform in Black
item.banner.mystcraft_weave.white=Weave in White
item.banner.mystcraft_weave.orange=Weave in Orange
item.banner.mystcraft_weave.magenta=Weave in Magenta
item.banner.mystcraft_weave.lightBlue=Weave in Light Blue
item.banner.mystcraft_weave.yellow=Weave in Yellow
item.banner.mystcraft_weave.lime=Weave in Lime
item.banner.mystcraft_weave.pink=Weave in Pink
item.banner.mystcraft_weave.gray=Weave in Gray
item.banner.mystcraft_weave.silver=Weave in Silver
item.banner.mystcraft_weave.cyan=Weave in Cyan
item.banner.mystcraft_weave.purple=Weave in Purple
item.banner.mystcraft_weave.blue=Weave in Blue
item.banner.mystcraft_weave.brown=Weave in Brown
item.banner.mystcraft_weave.green=Weave in Green
item.banner.mystcraft_weave.red=Weave in Red
item.banner.mystcraft_weave.black=Weave in Black
item.banner.mystcraft_wisdom.white=Wisdom in White
item.banner.mystcraft_wisdom.orange=Wisdom in Orange
item.banner.mystcraft_wisdom.magenta=Wisdom in Magenta
item.banner.mystcraft_wisdom.lightBlue=Wisdom in Light Blue
item.banner.mystcraft_wisdom.yellow=Wisdom in Yellow
item.banner.mystcraft_wisdom.lime=Wisdom in Lime
item.banner.mystcraft_wisdom.pink=Wisdom in Pink
item.banner.mystcraft_wisdom.gray=Wisdom in Gray
item.banner.mystcraft_wisdom.silver=Wisdom in Silver
item.banner.mystcraft_wisdom.cyan=Wisdom in Cyan
item.banner.mystcraft_wisdom.purple=Wisdom in Purple
item.banner.mystcraft_wisdom.blue=Wisdom in Blue
item.banner.mystcraft_wisdom.brown=Wisdom in Brown
item.banner.mystcraft_wisdom.green=Wisdom in Green
item.banner.mystcraft_wisdom.red=Wisdom in Red
item.banner.mystcraft_wisdom.black=Wisdom in Black
item.banner.mystcraft_void.white=Void in White
item.banner.mystcraft_void.orange=Void in Orange
item.banner.mystcraft_void.magenta=Void in Magenta
item.banner.mystcraft_void.lightBlue=Void in Light Blue
item.banner.mystcraft_void.yellow=Void in Yellow
item.banner.mystcraft_void.lime=Void in Lime
item.banner.mystcraft_void.pink=Void in Pink
item.banner.mystcraft_void.gray=Void in Gray
item.banner.mystcraft_void.silver=Void in Silver
item.banner.mystcraft_void.cyan=Void in Cyan
item.banner.mystcraft_void.purple=Void in Purple
item.banner.mystcraft_void.blue=Void in Blue
item.banner.mystcraft_void.brown=Void in Brown
item.banner.mystcraft_void.green=Void in Green
item.banner.mystcraft_void.red=Void in Red
item.banner.mystcraft_void.black=Void in Black
item.banner.mystcraft_chain.white=Chain in White
item.banner.mystcraft_chain.orange=Chain in Orange
item.banner.mystcraft_chain.magenta=Chain in Magenta
item.banner.mystcraft_chain.lightBlue=Chain in Light Blue
item.banner.mystcraft_chain.yellow=Chain in Yellow
item.banner.mystcraft_chain.lime=Chain in Lime
item.banner.mystcraft_chain.pink=Chain in Pink
item.banner.mystcraft_chain.gray=Chain in Gray
item.banner.mystcraft_chain.silver=Chain in Silver
item.banner.mystcraft_chain.cyan=Chain in Cyan
item.banner.mystcraft_chain.purple=Chain in Purple
item.banner.mystcraft_chain.blue=Chain in Blue
item.banner.mystcraft_chain.brown=Chain in Brown
item.banner.mystcraft_chain.green=Chain in Green
item.banner.mystcraft_chain.red=Chain in Red
item.banner.mystcraft_chain.black=Chain in Black
item.banner.mystcraft_celestial.white=Celestial in White
item.banner.mystcraft_celestial.orange=Celestial in Orange
item.banner.mystcraft_celestial.magenta=Celestial in Magenta
item.banner.mystcraft_celestial.lightBlue=Celestial in Light Blue
item.banner.mystcraft_celestial.yellow=Celestial in Yellow
item.banner.mystcraft_celestial.lime=Celestial in Lime
item.banner.mystcraft_celestial.pink=Celestial in Pink
item.banner.mystcraft_celestial.gray=Celestial in Gray
item.banner.mystcraft_celestial.silver=Celestial in Silver
item.banner.mystcraft_celestial.cyan=Celestial in Cyan
item.banner.mystcraft_celestial.purple=Celestial in Purple
item.banner.mystcraft_celestial.blue=Celestial in Blue
item.banner.mystcraft_celestial.brown=Celestial in Brown
item.banner.mystcraft_celestial.green=Celestial in Green
item.banner.mystcraft_celestial.red=Celestial in Red
item.banner.mystcraft_celestial.black=Celestial in Black
item.banner.mystcraft_image.white=Image in White
item.banner.mystcraft_image.orange=Image in Orange
item.banner.mystcraft_image.magenta=Image in Magenta
item.banner.mystcraft_image.lightBlue=Image in Light Blue
item.banner.mystcraft_image.yellow=Image in Yellow
item.banner.mystcraft_image.lime=Image in Lime
item.banner.mystcraft_image.pink=Image in Pink
item.banner.mystcraft_image.gray=Image in Gray
item.banner.mystcraft_image.silver=Image in Silver
item.banner.mystcraft_image.cyan=Image in Cyan
item.banner.mystcraft_image.purple=Image in Purple
item.banner.mystcraft_image.blue=Image in Blue
item.banner.mystcraft_image.brown=Image in Brown
item.banner.mystcraft_image.green=Image in Green
item.banner.mystcraft_image.red=Image in Red
item.banner.mystcraft_image.black=Image in Black
item.banner.mystcraft_terrain.white=Terrain in White
item.banner.mystcraft_terrain.orange=Terrain in Orange
item.banner.mystcraft_terrain.magenta=Terrain in Magenta
item.banner.mystcraft_terrain.lightBlue=Terrain in Light Blue
item.banner.mystcraft_terrain.yellow=Terrain in Yellow
item.banner.mystcraft_terrain.lime=Terrain in Lime
item.banner.mystcraft_terrain.pink=Terrain in Pink
item.banner.mystcraft_terrain.gray=Terrain in Gray
item.banner.mystcraft_terrain.silver=Terrain in Silver
item.banner.mystcraft_terrain.cyan=Terrain in Cyan
item.banner.mystcraft_terrain.purple=Terrain in Purple
item.banner.mystcraft_terrain.blue=Terrain in Blue
item.banner.mystcraft_terrain.brown=Terrain in Brown
item.banner.mystcraft_terrain.green=Terrain in Green
item.banner.mystcraft_terrain.red=Terrain in Red
item.banner.mystcraft_terrain.black=Terrain in Black
item.banner.mystcraft_order.white=Order in White
item.banner.mystcraft_order.orange=Order in Orange
item.banner.mystcraft_order.magenta=Order in Magenta
item.banner.mystcraft_order.lightBlue=Order in Light Blue
item.banner.mystcraft_order.yellow=Order in Yellow
item.banner.mystcraft_order.lime=Order in Lime
item.banner.mystcraft_order.pink=Order in Pink
item.banner.mystcraft_order.gray=Order in Gray
item.banner.mystcraft_order.silver=Order in Silver
item.banner.mystcraft_order.cyan=Order in Cyan
item.banner.mystcraft_order.purple=Order in Purple
item.banner.mystcraft_order.blue=Order in Blue
item.banner.mystcraft_order.brown=Order in Brown
item.banner.mystcraft_order.green=Order in Green
item.banner.mystcraft_order.red=Order in Red
item.banner.mystcraft_order.black=Order in Black
item.banner.mystcraft_0.white=0 in White
item.banner.mystcraft_0.orange=0 in Orange
item.banner.mystcraft_0.magenta=0 in Magenta
item.banner.mystcraft_0.lightBlue=0 in Light Blue
item.banner.mystcraft_0.yellow=0 in Yellow
item.banner.mystcraft_0.lime=0 in Lime
item.banner.mystcraft_0.pink=0 in Pink
item.banner.mystcraft_0.gray=0 in Gray
item.banner.mystcraft_0.silver=0 in Silver
item.banner.mystcraft_0.cyan=0 in Cyan
item.banner.mystcraft_0.purple=0 in Purple
item.banner.mystcraft_0.blue=0 in Blue
item.banner.mystcraft_0.brown=0 in Brown
item.banner.mystcraft_0.green=0 in Green
item.banner.mystcraft_0.red=0 in Red
item.banner.mystcraft_0.black=0 in Black
item.banner.mystcraft_1.white=1 in White
item.banner.mystcraft_1.orange=1 in Orange
item.banner.mystcraft_1.magenta=1 in Magenta
item.banner.mystcraft_1.lightBlue=1 in Light Blue
item.banner.mystcraft_1.yellow=1 in Yellow
item.banner.mystcraft_1.lime=1 in Lime
item.banner.mystcraft_1.pink=1 in Pink
item.banner.mystcraft_1.gray=1 in Gray
item.banner.mystcraft_1.silver=1 in Silver
item.banner.mystcraft_1.cyan=1 in Cyan
item.banner.mystcraft_1.purple=1 in Purple
item.banner.mystcraft_1.blue=1 in Blue
item.banner.mystcraft_1.brown=1 in Brown
item.banner.mystcraft_1.green=1 in Green
item.banner.mystcraft_1.red=1 in Red
item.banner.mystcraft_1.black=1 in Black
item.banner.mystcraft_2.white=2 in White
item.banner.mystcraft_2.orange=2 in Orange
item.banner.mystcraft_2.magenta=2 in Magenta
item.banner.mystcraft_2.lightBlue=2 in Light Blue
item.banner.mystcraft_2.yellow=2 in Yellow
item.banner.mystcraft_2.lime=2 in Lime
item.banner.mystcraft_2.pink=2 in Pink
item.banner.mystcraft_2.gray=2 in Gray
item.banner.mystcraft_2.silver=2 in Silver
item.banner.mystcraft_2.cyan=2 in Cyan
item.banner.mystcraft_2.purple=2 in Purple
item.banner.mystcraft_2.blue=2 in Blue
item.banner.mystcraft_2.brown=2 in Brown
item.banner.mystcraft_2.green=2 in Green
item.banner.mystcraft_2.red=2 in Red
item.banner.mystcraft_2.black=2 in Black
item.banner.mystcraft_3.white=3 in White
item.banner.mystcraft_3.orange=3 in Orange
item.banner.mystcraft_3.magenta=3 in Magenta
item.banner.mystcraft_3.lightBlue=3 in Light Blue
item.banner.mystcraft_3.yellow=3 in Yellow
item.banner.mystcraft_3.lime=3 in Lime
item.banner.mystcraft_3.pink=3 in Pink
item.banner.mystcraft_3.gray=3 in Gray
item.banner.mystcraft_3.silver=3 in Silver
item.banner.mystcraft_3.cyan=3 in Cyan
item.banner.mystcraft_3.purple=3 in Purple
item.banner.mystcraft_3.blue=3 in Blue
item.banner.mystcraft_3.brown=3 in Brown
item.banner.mystcraft_3.green=3 in Green
item.banner.mystcraft_3.red=3 in Red
item.banner.mystcraft_3.black=3 in Black
item.banner.mystcraft_4.white=4 in White
item.banner.mystcraft_4.orange=4 in Orange
item.banner.mystcraft_4.magenta=4 in Magenta
item.banner.mystcraft_4.lightBlue=4 in Light Blue
item.banner.mystcraft_4.yellow=4 in Yellow
item.banner.mystcraft_4.lime=4 in Lime
item.banner.mystcraft_4.pink=4 in Pink
item.banner.mystcraft_4.gray=4 in Gray
item.banner.mystcraft_4.silver=4 in Silver
item.banner.mystcraft_4.cyan=4 in Cyan
item.banner.mystcraft_4.purple=4 in Purple
item.banner.mystcraft_4.blue=4 in Blue
item.banner.mystcraft_4.brown=4 in Brown
item.banner.mystcraft_4.green=4 in Green
item.banner.mystcraft_4.red=4 in Red
item.banner.mystcraft_4.black=4 in Black
item.banner.mystcraft_5.white=5 in White
item.banner.mystcraft_5.orange=5 in Orange
item.banner.mystcraft_5.magenta=5 in Magenta
item.banner.mystcraft_5.lightBlue=5 in Light Blue
item.banner.mystcraft_5.yellow=5 in Yellow
item.banner.mystcraft_5.lime=5 in Lime
item.banner.mystcraft_5.pink=5 in Pink
item.banner.mystcraft_5.gray=5 in Gray
item.banner.mystcraft_5.silver=5 in Silver
item.banner.mystcraft_5.cyan=5 in Cyan
item.banner.mystcraft_5.purple=5 in Purple
item.banner.mystcraft_5.blue=5 in Blue
item.banner.mystcraft_5.brown=5 in Brown
item.banner.mystcraft_5.green=5 in Green
item.banner.mystcraft_5.red=5 in Red
item.banner.mystcraft_5.black=5 in Black
item.banner.mystcraft_6.white=6 in White
item.banner.mystcraft_6.orange=6 in Orange
item.banner.mystcraft_6.magenta=6 in Magenta
item.banner.mystcraft_6.lightBlue=6 in Light Blue
item.banner.mystcraft_6.yellow=6 in Yellow
item.banner.mystcraft_6.lime=6 in Lime
item.banner.mystcraft_6.pink=6 in Pink
item.banner.mystcraft_6.gray=6 in Gray
item.banner.mystcraft_6.silver=6 in Silver
item.banner.mystcraft_6.cyan=6 in Cyan
item.banner.mystcraft_6.purple=6 in Purple
item.banner.mystcraft_6.blue=6 in Blue
item.banner.mystcraft_6.brown=6 in Brown
item.banner.mystcraft_6.green=6 in Green
item.banner.mystcraft_6.red=6 in Red
item.banner.mystcraft_6.black=6 in Black
item.banner.mystcraft_7.white=7 in White
item.banner.mystcraft_7.orange=7 in Orange
item.banner.mystcraft_7.magenta=7 in Magenta
item.banner.mystcraft_7.lightBlue=7 in Light Blue
item.banner.mystcraft_7.yellow=7 in Yellow
item.banner.mystcraft_7.lime=7 in Lime
item.banner.mystcraft_7.pink=7 in Pink
item.banner.mystcraft_7.gray=7 in Gray
item.banner.mystcraft_7.silver=7 in Silver
item.banner.mystcraft_7.cyan=7 in Cyan
item.banner.mystcraft_7.purple=7 in Purple
item.banner.mystcraft_7.blue=7 in Blue
item.banner.mystcraft_7.brown=7 in Brown
item.banner.mystcraft_7.green=7 in Green
item.banner.mystcraft_7.red=7 in Red
item.banner.mystcraft_7.black=7 in Black
item.banner.mystcraft_8.white=8 in White
item.banner.mystcraft_8.orange=8 in Orange
item.banner.mystcraft_8.magenta=8 in Magenta
item.banner.mystcraft_8.lightBlue=8 in Light Blue
item.banner.mystcraft_8.yellow=8 in Yellow
item.banner.mystcraft_8.lime=8 in Lime
item.banner.mystcraft_8.pink=8 in Pink
item.banner.mystcraft_8.gray=8 in Gray
item.banner.mystcraft_8.silver=8 in Silver
item.banner.mystcraft_8.cyan=8 in Cyan
item.banner.mystcraft_8.purple=8 in Purple
item.banner.mystcraft_8.blue=8 in Blue
item.banner.mystcraft_8.brown=8 in Brown
item.banner.mystcraft_8.green=8 in Green
item.banner.mystcraft_8.red=8 in Red
item.banner.mystcraft_8.black=8 in Black
item.banner.mystcraft_9.white=9 in White
item.banner.mystcraft_9.orange=9 in Orange
item.banner.mystcraft_9.magenta=9 in Magenta
item.banner.mystcraft_9.lightBlue=9 in Light Blue
item.banner.mystcraft_9.yellow=9 in Yellow
item.banner.mystcraft_9.lime=9 in Lime
item.banner.mystcraft_9.pink=9 in Pink
item.banner.mystcraft_9.gray=9 in Gray
item.banner.mystcraft_9.silver=9 in Silver
item.banner.mystcraft_9.cyan=9 in Cyan
item.banner.mystcraft_9.purple=9 in Purple
item.banner.mystcraft_9.blue=9 in Blue
item.banner.mystcraft_9.brown=9 in Brown
item.banner.mystcraft_9.green=9 in Green
item.banner.mystcraft_9.red=9 in Red
item.banner.mystcraft_9.black=9 in Black
item.banner.mystcraft_10.white=10 in White
item.banner.mystcraft_10.orange=10 in Orange
item.banner.mystcraft_10.magenta=10 in Magenta
item.banner.mystcraft_10.lightBlue=10 in Light Blue
item.banner.mystcraft_10.yellow=10 in Yellow
item.banner.mystcraft_10.lime=10 in Lime
item.banner.mystcraft_10.pink=10 in Pink
item.banner.mystcraft_10.gray=10 in Gray
item.banner.mystcraft_10.silver=10 in Silver
item.banner.mystcraft_10.cyan=10 in Cyan
item.banner.mystcraft_10.purple=10 in Purple
item.banner.mystcraft_10.blue=10 in Blue
item.banner.mystcraft_10.brown=10 in Brown
item.banner.mystcraft_10.green=10 in Green
item.banner.mystcraft_10.red=10 in Red
item.banner.mystcraft_10.black=10 in Black
item.banner.mystcraft_11.white=11 in White
item.banner.mystcraft_11.orange=11 in Orange
item.banner.mystcraft_11.magenta=11 in Magenta
item.banner.mystcraft_11.lightBlue=11 in Light Blue
item.banner.mystcraft_11.yellow=11 in Yellow
item.banner.mystcraft_11.lime=11 in Lime
item.banner.mystcraft_11.pink=11 in Pink
item.banner.mystcraft_11.gray=11 in Gray
item.banner.mystcraft_11.silver=11 in Silver
item.banner.mystcraft_11.cyan=11 in Cyan
item.banner.mystcraft_11.purple=11 in Purple
item.banner.mystcraft_11.blue=11 in Blue
item.banner.mystcraft_11.brown=11 in Brown
item.banner.mystcraft_11.green=11 in Green
item.banner.mystcraft_11.red=11 in Red
item.banner.mystcraft_11.black=11 in Black
item.banner.mystcraft_12.white=12 in White
item.banner.mystcraft_12.orange=12 in Orange
item.banner.mystcraft_12.magenta=12 in Magenta
item.banner.mystcraft_12.lightBlue=12 in Light Blue
item.banner.mystcraft_12.yellow=12 in Yellow
item.banner.mystcraft_12.lime=12 in Lime
item.banner.mystcraft_12.pink=12 in Pink
item.banner.mystcraft_12.gray=12 in Gray
item.banner.mystcraft_12.silver=12 in Silver
item.banner.mystcraft_12.cyan=12 in Cyan
item.banner.mystcraft_12.purple=12 in Purple
item.banner.mystcraft_12.blue=12 in Blue
item.banner.mystcraft_12.brown=12 in Brown
item.banner.mystcraft_12.green=12 in Green
item.banner.mystcraft_12.red=12 in Red
item.banner.mystcraft_12.black=12 in Black
item.banner.mystcraft_13.white=13 in White
item.banner.mystcraft_13.orange=13 in Orange
item.banner.mystcraft_13.magenta=13 in Magenta
item.banner.mystcraft_13.lightBlue=13 in Light Blue
item.banner.mystcraft_13.yellow=13 in Yellow
item.banner.mystcraft_13.lime=13 in Lime
item.banner.mystcraft_13.pink=13 in Pink
item.banner.mystcraft_13.gray=13 in Gray
item.banner.mystcraft_13.silver=13 in Silver
item.banner.mystcraft_13.cyan=13 in Cyan
item.banner.mystcraft_13.purple=13 in Purple
item.banner.mystcraft_13.blue=13 in Blue
item.banner.mystcraft_13.brown=13 in Brown
item.banner.mystcraft_13.green=13 in Green
item.banner.mystcraft_13.red=13 in Red
item.banner.mystcraft_13.black=13 in Black
item.banner.mystcraft_14.white=14 in White
item.banner.mystcraft_14.orange=14 in Orange
item.banner.mystcraft_14.magenta=14 in Magenta
item.banner.mystcraft_14.lightBlue=14 in Light Blue
item.banner.mystcraft_14.yellow=14 in Yellow
item.banner.mystcraft_14.lime=14 in Lime
item.banner.mystcraft_14.pink=14 in Pink
item.banner.mystcraft_14.gray=14 in Gray
item.banner.mystcraft_14.silver=14 in Silver
item.banner.mystcraft_14.cyan=14 in Cyan
item.banner.mystcraft_14.purple=14 in Purple
item.banner.mystcraft_14.blue=14 in Blue
item.banner.mystcraft_14.brown=14 in Brown
item.banner.mystcraft_14.green=14 in Green
item.banner.mystcraft_14.red=14 in Red
item.banner.mystcraft_14.black=14 in Black
item.banner.mystcraft_15.white=15 in White
item.banner.mystcraft_15.orange=15 in Orange
item.banner.mystcraft_15.magenta=15 in Magenta
item.banner.mystcraft_15.lightBlue=15 in Light Blue
item.banner.mystcraft_15.yellow=15 in Yellow
item.banner.mystcraft_15.lime=15 in Lime
item.banner.mystcraft_15.pink=15 in Pink
item.banner.mystcraft_15.gray=15 in Gray
item.banner.mystcraft_15.silver=15 in Silver
item.banner.mystcraft_15.cyan=15 in Cyan
item.banner.mystcraft_15.purple=15 in Purple
item.banner.mystcraft_15.blue=15 in Blue
item.banner.mystcraft_15.brown=15 in Brown
item.banner.mystcraft_15.green=15 in Green
item.banner.mystcraft_15.red=15 in Red
item.banner.mystcraft_15.black=15 in Black
item.banner.mystcraft_16.white=16 in White
item.banner.mystcraft_16.orange=16 in Orange
item.banner.mystcraft_16.magenta=16 in Magenta
item.banner.mystcraft_16.lightBlue=16 in Light Blue
item.banner.mystcraft_16.yellow=16 in Yellow
item.banner.mystcraft_16.lime=16 in Lime
item.banner.mystcraft_16.pink=16 in Pink
item.banner.mystcraft_16.gray=16 in Gray
item.banner.mystcraft_16.silver=16 in Silver
item.banner.mystcraft_16.cyan=16 in Cyan
item.banner.mystcraft_16.purple=16 in Purple
item.banner.mystcraft_16.blue=16 in Blue
item.banner.mystcraft_16.brown=16 in Brown
item.banner.mystcraft_16.green=16 in Green
item.banner.mystcraft_16.red=16 in Red
item.banner.mystcraft_16.black=16 in Black
item.banner.mystcraft_17.white=17 in White
item.banner.mystcraft_17.orange=17 in Orange
item.banner.mystcraft_17.magenta=17 in Magenta
item.banner.mystcraft_17.lightBlue=17 in Light Blue
item.banner.mystcraft_17.yellow=17 in Yellow
item.banner.mystcraft_17.lime=17 in Lime
item.banner.mystcraft_17.pink=17 in Pink
item.banner.mystcraft_17.gray=17 in Gray
item.banner.mystcraft_17.silver=17 in Silver
item.banner.mystcraft_17.cyan=17 in Cyan
item.banner.mystcraft_17.purple=17 in Purple
item.banner.mystcraft_17.blue=17 in Blue
item.banner.mystcraft_17.brown=17 in Brown
item.banner.mystcraft_17.green=17 in Green
item.banner.mystcraft_17.red=17 in Red
item.banner.mystcraft_17.black=17 in Black
item.banner.mystcraft_18.white=18 in White
item.banner.mystcraft_18.orange=18 in Orange
item.banner.mystcraft_18.magenta=18 in Magenta
item.banner.mystcraft_18.lightBlue=18 in Light Blue
item.banner.mystcraft_18.yellow=18 in Yellow
item.banner.mystcraft_18.lime=18 in Lime
item.banner.mystcraft_18.pink=18 in Pink
item.banner.mystcraft_18.gray=18 in Gray
item.banner.mystcraft_18.silver=18 in Silver
item.banner.mystcraft_18.cyan=18 in Cyan
item.banner.mystcraft_18.purple=18 in Purple
item.banner.mystcraft_18.blue=18 in Blue
item.banner.mystcraft_18.brown=18 in Brown
item.banner.mystcraft_18.green=18 in Green
item.banner.mystcraft_18.red=18 in Red
item.banner.mystcraft_18.black=18 in Black
item.banner.mystcraft_19.white=19 in White
item.banner.mystcraft_19.orange=19 in Orange
item.banner.mystcraft_19.magenta=19 in Magenta
item.banner.mystcraft_19.lightBlue=19 in Light Blue
item.banner.mystcraft_19.yellow=19 in Yellow
item.banner.mystcraft_19.lime=19 in Lime
item.banner.mystcraft_19.pink=19 in Pink
item.banner.mystcraft_19.gray=19 in Gray
item.banner.mystcraft_19.silver=19 in Silver
item.banner.mystcraft_19.cyan=19 in Cyan
item.banner.mystcraft_19.purple=19 in Purple
item.banner.mystcraft_19.blue=19 in Blue
item.banner.mystcraft_19.brown=19 in Brown
item.banner.mystcraft_19.green=19 in Green
item.banner.mystcraft_19.red=19 in Red
item.banner.mystcraft_19.black=19 in Black
item.banner.mystcraft_20.white=20 in White
item.banner.mystcraft_20.orange=20 in Orange
item.banner.mystcraft_20.magenta=20 in Magenta
item.banner.mystcraft_20.lightBlue=20 in Light Blue
item.banner.mystcraft_20.yellow=20 in Yellow
item.banner.mystcraft_20.lime=20 in Lime
item.banner.mystcraft_20.pink=20 in Pink
item.banner.mystcraft_20.gray=20 in Gray
item.banner.mystcraft_20.silver=20 in Silver
item.banner.mystcraft_20.cyan=20 in Cyan
item.banner.mystcraft_20.purple=20 in Purple
item.banner.mystcraft_20.blue=20 in Blue
item.banner.mystcraft_20.brown=20 in Brown
item.banner.mystcraft_20.green=20 in Green
item.banner.mystcraft_20.red=20 in Red
item.banner.mystcraft_20.black=20 in Black
item.banner.mystcraft_21.white=21 in White
item.banner.mystcraft_21.orange=21 in Orange
item.banner.mystcraft_21.magenta=21 in Magenta
item.banner.mystcraft_21.lightBlue=21 in Light Blue
item.banner.mystcraft_21.yellow=21 in Yellow
item.banner.mystcraft_21.lime=21 in Lime
item.banner.mystcraft_21.pink=21 in Pink
item.banner.mystcraft_21.gray=21 in Gray
item.banner.mystcraft_21.silver=21 in Silver
item.banner.mystcraft_21.cyan=21 in Cyan
item.banner.mystcraft_21.purple=21 in Purple
item.banner.mystcraft_21.blue=21 in Blue
item.banner.mystcraft_21.brown=21 in Brown
item.banner.mystcraft_21.green=21 in Green
item.banner.mystcraft_21.red=21 in Red
item.banner.mystcraft_21.black=21 in Black
item.banner.mystcraft_22.white=22 in White
item.banner.mystcraft_22.orange=22 in Orange
item.banner.mystcraft_22.magenta=22 in Magenta
item.banner.mystcraft_22.lightBlue=22 in Light Blue
item.banner.mystcraft_22.yellow=22 in Yellow
item.banner.mystcraft_22.lime=22 in Lime
item.banner.mystcraft_22.pink=22 in Pink
item.banner.mystcraft_22.gray=22 in Gray
item.banner.mystcraft_22.silver=22 in Silver
item.banner.mystcraft_22.cyan=22 in Cyan
item.banner.mystcraft_22.purple=22 in Purple
item.banner.mystcraft_22.blue=22 in Blue
item.banner.mystcraft_22.brown=22 in Brown
item.banner.mystcraft_22.green=22 in Green
item.banner.mystcraft_22.red=22 in Red
item.banner.mystcraft_22.black=22 in Black
item.banner.mystcraft_23.white=23 in White
item.banner.mystcraft_23.orange=23 in Orange
item.banner.mystcraft_23.magenta=23 in Magenta
item.banner.mystcraft_23.lightBlue=23 in Light Blue
item.banner.mystcraft_23.yellow=23 in Yellow
item.banner.mystcraft_23.lime=23 in Lime
item.banner.mystcraft_23.pink=23 in Pink
item.banner.mystcraft_23.gray=23 in Gray
item.banner.mystcraft_23.silver=23 in Silver
item.banner.mystcraft_23.cyan=23 in Cyan
item.banner.mystcraft_23.purple=23 in Purple
item.banner.mystcraft_23.blue=23 in Blue
item.banner.mystcraft_23.brown=23 in Brown
item.banner.mystcraft_23.green=23 in Green
item.banner.mystcraft_23.red=23 in Red
item.banner.mystcraft_23.black=23 in Black
item.banner.mystcraft_24.white=24 in White
item.banner.mystcraft_24.orange=24 in Orange
item.banner.mystcraft_24.magenta=24 in Magenta
item.banner.mystcraft_24.lightBlue=24 in Light Blue
item.banner.mystcraft_24.yellow=24 in Yellow
item.banner.mystcraft_24.lime=24 in Lime
item.banner.mystcraft_24.pink=24 in Pink
item.banner.mystcraft_24.gray=24 in Gray
item.banner.mystcraft_24.silver=24 in Silver
item.banner.mystcraft_24.cyan=24 in Cyan
item.banner.mystcraft_24.purple=24 in Purple
item.banner.mystcraft_24.blue=24 in Blue
item.banner.mystcraft_24.brown=24 in Brown
item.banner.mystcraft_24.green=24 in Green
item.banner.mystcraft_24.red=24 in Red
item.banner.mystcraft_24.black=24 in Black
item.banner.mystcraft_25.white=25 in White
item.banner.mystcraft_25.orange=25 in Orange
item.banner.mystcraft_25.magenta=25 in Magenta
item.banner.mystcraft_25.lightBlue=25 in Light Blue
item.banner.mystcraft_25.yellow=25 in Yellow
item.banner.mystcraft_25.lime=25 in Lime
item.banner.mystcraft_25.pink=25 in Pink
item.banner.mystcraft_25.gray=25 in Gray
item.banner.mystcraft_25.silver=25 in Silver
item.banner.mystcraft_25.cyan=25 in Cyan
item.banner.mystcraft_25.purple=25 in Purple
item.banner.mystcraft_25.blue=25 in Blue
item.banner.mystcraft_25.brown=25 in Brown
item.banner.mystcraft_25.green=25 in Green
item.banner.mystcraft_25.red=25 in Red
item.banner.mystcraft_25.black=25 in Black
```

## Appendix B — Mystcraft Library structure layout (`ComponentScatteredFeatureSmallLibrary`)

Local coordinates before rotation (component is 11×11×11, `super(rand, x, 64, z, 11, 10, 11)`; random horizontal `coordBaseMode`). Blocks are placed in source order; the maps below show the final state of each explicitly set cell (694 `setBlockState` calls). Blank = not set (existing terrain remains). After these, the foundation is filled downward with cobblestone under every column (`replaceAirAndLiquidDownwards` at y=-1 for x,z in 0..11), a chest with `mystcraft:mystcraft_treasure` is placed at (4,1,2), and lecterns at (6,2,2),(8,2,4),(8,2,5),(8,2,6),(6,2,8).

Legend: `.` air, `C` cobblestone, `B` bookshelf, `P` oak planks, `X` cobweb, `#` cobblestone wall, `s` cobblestone slab (bottom, meta 3), `S` cobblestone slab (top, meta 11), stone stairs facing `n` north / `u` south / `e` east / `w` west (uppercase = upside-down/top half).

```
y=0   x = 0..10 →  (each line is one z row, z = 0 at top)
    ennnnnnnn
    eCCCCCCCw
    eCCCCCCCw
  nneCCCCCCCw
  eCCCCCCCCCw
  eCCCCCCCCCw
  eCCCCCCCCCw
  uuuCCCCCCCw
    eCCCCCCCw
    eCCCCCCCw
    uuuuuuuuw
y=1   x = 0..10 →  (each line is one z row, z = 0 at top)
             
     CCCCCCC 
     C BBBPC 
  ...CB...BC 
  .#.C....BC 
  ........BC 
  .#.C....BC 
  ...CB...BC 
     CPBBBPC 
     CCCCCCC 
             
y=2   x = 0..10 →  (each line is one z row, z = 0 at top)
             
     CCCCCCC 
     CPB BPC 
  ...CB...BC 
  .#.C.... C 
  ........ C 
  .#.C.... C 
  ...CB...BC 
     CPB BPC 
     CCCCCCC 
             
y=3   x = 0..10 →  (each line is one z row, z = 0 at top)
             
     CCCCCCC 
     CPBBBPC 
  ...CB...BC 
  .#.C....BC 
  ...S....BC 
  .#.C....BC 
  ...CB...BC 
     CPBBBPC 
     CCCCCCC 
             
y=4   x = 0..10 →  (each line is one z row, z = 0 at top)
             
     CCCCCCC 
     CPBBBPC 
   nnCB...BC 
   sCC....BC 
   sCC....BC 
   sCC....BC 
   uuCB...BC 
     CPBBBPC 
     CCCCCCC 
             
y=5   x = 0..10 →  (each line is one z row, z = 0 at top)
             
     CCCCCCC 
     CXX..XC 
     C....XC 
   nnC....XC 
   SCC....XC 
   uuC....XC 
     C.....C 
     C.XXXXC 
     CCCCCCC 
             
y=6   x = 0..10 →  (each line is one z row, z = 0 at top)
             
     CCCCCCC 
     CXXXX.C 
     CXXX.XC 
     CXX...C 
     CX....C 
     CX...XC 
     C..X..C 
     C..XX.C 
     CCCCCCC 
             
y=7   x = 0..10 →  (each line is one z row, z = 0 at top)
    NNNNNNNNW
    ECCCCCCCW
    ECCCCCCCW
    ECCCCCCCW
    ECCCCCCCW
    ECCCCCCCW
    ECCCCCCCW
    ECCCCCCCW
    ECCCCCCCW
    ECCCCCCCW
    EUUUUUUUU
y=8   x = 0..10 →  (each line is one z row, z = 0 at top)
             
             
             
     nnnnnnn 
     eCCCCCw 
     eCCCCCw 
     eCCCCCw 
     uuuuuuw 
             
             
             
y=9   x = 0..10 →  (each line is one z row, z = 0 at top)
             
             
             
             
      ennnw  
      eCCCw  
      euuuu  
             
             
             
             
y=10   x = 0..10 →  (each line is one z row, z = 0 at top)
             
             
             
             
             
       sss   
             
             
             
             
             
```
