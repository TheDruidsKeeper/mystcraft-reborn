# World-building system and writing desk rework — plan

Status: **decided 2026-10-03, in implementation** (decisions in §7, ink table in §8 confirmed). Phase progress in §6.

Goal: replace the ported Mystcraft grammar (`Grammar.expandAge`) with a category-based "Age blueprint" that fills an
incomplete Descriptive Book deterministically and safely, organises the book, and rework the Writing Desk around
*unlocked symbols* instead of page inventories.

---

## 1. Vocabulary

| Term | Meaning |
|---|---|
| **Category** | A slot of the Age description (Terrain, Biomes, Lighting, …). Every symbol belongs to exactly one category. Categories define: required or optional, how many symbols they take, what applies when empty, the random-fill rules. |
| **Symbol page** | One page = one *primary* symbol **plus its attached modifiers** (new: modifiers live on the page, not on preceding pages). |
| **Discovered page** | A page the game added at first link (not written by a player). Rendered differently, flagged in data. |
| **Blueprint** | The resolved set of pages for an Age: player pages + discovered pages, ordered by category. Built once at binding from the Age seed. |
| **Unlocked symbol** | A symbol a Writing Desk knows and can write copies of (replaces portfolios/notebooks). |

## 2. Categories (the world-building schema)

Counts are *defaults*, every number is a config key (§5). "Default when empty" = what the Age does when no symbol of
the category is present (the "normal Minecraft" behaviour the user asked for).

Implementation note: the build order of `SymbolCategory` puts **Biomes before Biome layout** (the layout symbol consumes
the biome list), so the book and the desk strip show Terrain, Biomes, Biome layout, Lighting, Celestials, Sky colours,
World colours, Weather, Structures, Features, Effects. `no_sea` is a **material** (attached to the terrain page, 3 %),
not a feature. "Dark sun gets a bright moon" is implemented as: a dark sun is only kept when the filler may also set
`lighting_bright` (lighting not written by the player), otherwise the sun stays normal. The instability budget default
is 500 (one bright lighting / dense ores fits, Accelerated (1000) never does; effects with negative instability always fit).
Biome families: every overworld biome weight 10, nether biomes 1 (only with `terrain_nether`), end biomes 1 (only with
`terrain_end`); `fill.biomes.weights` overrides single biome symbols by id.

| # | Category | Required | Count | Default when empty | Random fill | Members (current symbol ids) |
|---|---|---|---|---|---|---|
| 1 | **Terrain** | yes | exactly 1 | `terrain_normal` | always (if missing): normal 70 / amplified 12 / flat 8 / cave 6 / island 4 / void **0** | terrain_* |
| 2 | **Biome layout** | yes | exactly 1 | `biome_native` | always: native 45 / medium 15 / large 12 / small 10 / huge 6 / tiny 4 / tiled 4 / grid 2 / single 2 | biome_* (controllers) |
| 3 | **Biomes** | yes unless layout = native | 1–4 (single: 1) | vanilla overworld set (native) | always when required: count 1–4, unique, weighted by biome family (overworld common 10, overworld rare 3, nether 1 only with cave terrain, end 1 only with island terrain, void never) | biome wrappers |
| 4 | **Lighting** | yes | exactly 1 | `lighting_normal` | always: normal 85 / bright 10 / dark 5 | lighting_* |
| 5 | **Celestials** | yes | 1–5 | sun + moon + stars (vanilla sky) | always: 1 light-giving sun guaranteed (normal 96 / dark 4 — a dark sun then gets a bright moon); moons 0–2 (0: 20, 1: 60, 2: 20); starfields 0–2 (normal 60 / twinkle 25 / end sky 5 / dark 10 as "none"); rainbow 8 %; each celestial gets modifiers: direction (basic 4 dirs, 30 % chance), phase (30 %), length (full 70 / half 10 / double 15 / zero 5), sunset colour (20 %) | sun_*, moon_*, stars_*, rainbow |
| 6 | **Sky colours** | no | 0–4 | natural | 25 % any; each of sky / night sky / fog / cloud / horizon 40 % when the category fires; colour = 1 colour modifier (gradient 25 %) | color_sky*, color_fog*, color_cloud*, color_horizon, no_horizon (5 %) |
| 7 | **World colours** | no | 0–3 | natural | 15 % any; foliage / grass / water 50 % each | color_foliage*, color_grass*, color_water* |
| 8 | **Weather** | no | 0–1 | `weather_normal` | 35 %: fast 25 / slow 25 / off 15 / cloudy 15 / rain 8 / snow 8 / on 3 / storm 1 | weather_* |
| 9 | **Structures** | no | 0–3 | **decision §7.2** (vanilla set or none) | 40 %: villages 30 / mineshafts 25 / dungeons 25 / strongholds 10 / ravines 10 / nether fortress 0 unless cave terrain | villages, strongholds, mineshafts, nether_fortress, dungeons, ravines |
| 10 | **Terrain features** | no | 0–3 | caves + surface lakes (vanilla-like) | 50 %: caves 25 / lakes_surface 20 / lakes_deep 10 / huge_trees 10 / floating_islands 8 / skylands 6 / tendrils 5 / crystal_formations 5 / obelisks 4 / spheres 3 / spikes 3 / star_fissure (§7.6) / dense_ores **2** / no_sea 3 | those ids |
| 11 | **Materials** | modifier only | – | stone / water | only added as a modifier of a feature or terrain that takes one: default material 80 % (stone / water), common block 15 %, exotic 5 % | block wrappers |
| 12 | **Effects** | no | 0–2 | none | 12 %: accelerated 40 / lightning 25 / pvp_off 20 / meteors 8 / explosions 4 / scorched 3 | env_*, pvp_off |
| – | **Modifiers** | attached | – | – | never standalone | mod_*, color modifiers, gradient, clear_modifiers (dropped: pages carry their own modifiers) |

Rules the filler obeys:

1. **Player-written categories are not touched.** If the book has any symbol of a category, the filler skips that
   category entirely (it does not "top up" celestials, biomes, …). Required categories with nothing written get filled.
2. **Compatibility gates** (hard): nether biomes need cave terrain, end biomes need island terrain, nether fortress needs
   cave terrain, `biome_single` takes exactly one biome, void terrain is never auto-picked, dark sun forces a bright moon.
3. **Stability budget**: every symbol has an instability weight (existing `instabilityModifier`); the filler keeps the
   sum of *discovered* symbols' instability ≤ `fill.instabilityBudget` (default 0 → only stable picks; abnormal picks
   that carry instability are re-rolled or dropped). Player symbols are never limited.
4. **Determinism**: `RandomSource.create(ageSeed ^ FILL_SALT)`; categories are rolled in table order so a change to
   one category's config does not reshuffle the others more than necessary.
5. **Abnormal = low-to-medium chance, defaults = normal**: visible in the weights above; all weights configurable.
6. The filler writes **discovered pages** (flag) and the book is **reorganised**: pages sorted by category order,
   player pages first within a category, each page carrying its modifiers (§3). The Age keeps the same page list.
7. **Summary page** (last page, not a symbol): seed, base instability + symbol instability score, the instability
   effects active in the Age (decay types, meteors, …), author list, "discovered N of M symbols".

## 3. Data model changes

* `ModDataComponents.SYMBOL` → `SymbolPage(symbolId, List<Identifier> modifiers, boolean discovered)` (codec keeps
  reading the old `{id}` form).
* `PageItem` keeps `createSymbolPage(id)`; adds `withModifiers`, `isDiscovered`, `modifiers()`.
* `AgeController`/grammar consume a **flattened** list: for each page emit its modifiers then the symbol — the
  existing modifier stack logic is unchanged, only the source order comes from pages.
* `SymbolCategory` enum (+ `AgeSymbol.category()`; built-in symbols assigned in their registration; third-party
  symbols default to a category by implemented interface: TerrainGenerator → Terrain, BiomeController → Biome layout,
  Celestial → Celestials, …).
* `AgeBlueprint` (new, `age.blueprint`): `fill(List<ItemStack> pages, long seed, FillConfig) -> List<ItemStack>`,
  `organise(pages)`, `summaryPage(AgeData)`. Pure, server-safe, gametested.
* `Grammar`/`GrammarRules`/`GrammarTree`/`CreativeCollections`: **removed** (card ranks stay for trade pricing).

## 4. Writing desk rework

Left panel: **category tabs** (All, Terrain, Biomes, Sky, Weather, Features, Materials, Effects, Modifiers). Surface
lists the desk's *unlocked* symbols of that category. Click = write a copy into the folder (paper + ink). No pickup,
no stacking, no page inventory in the desk.

Right panel: target slot accepts **only a Collation Folder** (books stay in the Book Binder flow). The strip shows the
folder's pages grouped by category with a header per category ("Terrain · required · 1" / "Celestials · 0–5"), and a
"missing" list for required categories (advisory only, nothing enforced). Selecting a page in the strip and clicking a
modifier symbol **attaches** it to that page (shown as a grid on the page tile and in the book view).

Learning symbols (decision §7.1): **no import slot**. A player unlocks a symbol by using a symbol page (right-click;
the page is consumed; all of the page's symbols, modifiers included) or by arriving in an Age (all of its symbols). The
desk surface shows the *player's* unlocked symbols; a desk is just a workplace.

Creative: a separate uncraftable **"Scholar's Writing Desk"** item places the same block with `allUnlocked=true` (every
registered symbol available to whoever uses it). Link Modifier already has no recipe (stays that way).

Removed: `PortfolioItem` (no migration, decision §7.10), notebook tabs, `MSG_ADD_TO_SURFACE` / `REMOVE_FROM_*` /
`ADD_TO_TAB` messages, AZ/ALL buttons (replaced by category tabs + search).

Guidance in the UI: category header text + tooltip ("Required: exactly one. Default: Standard World."), symbol tooltip
gains "Takes: colour modifier" / "Needs: cave terrain" lines, discovered pages show "discovered" in the book and strip.

## 5. Config (`mystcraft-common.toml`, section `worldbuilding`)

```
fill.instabilityBudget = 500
fill.starFissureChance = 10
fill.<category>.chance = <percent>            # optional categories (required ones are always filled)
fill.<category>.min / .max                     # counts
fill.<category>.defaults = ["id", ...]         # always added when the category is empty
fill.<category>.weights = ["id=weight", ...]   # per-symbol weights, "0" removes a symbol from random fill
fill.celestials.modifierChance.direction/phase/sunset = 30/30/20
fill.materials.commonChance / exoticChance / noSeaChance = 15 / 5 / 3
```

Config lives in its own file `mystcraft-worldbuilding.toml` (`WorldBuildingConfig`): `fill.<category>.{chance,min,max,
defaults,weights}`, `fill.biomes.{countWeights,overworldWeight,netherWeight,endWeight}`, `fill.celestials.{moonCountWeights,
starfieldCountWeights,lengthWeights,modifierChance.direction/phase/sunset}`, `fill.colors.gradientChance`,
`fill.materials.{commonChance,exoticChance,noSeaChance}`, `fill.instabilityBudget`, `fill.starFissureChance`. Unlock
scope is fixed (per player), so there is no `desk.unlockScope` key.

## 6. Work breakdown (phases, each ends green on the Docker pipeline)

| Phase | Work | Tests |
|---|---|---|
| A | `SymbolCategory`, `SymbolPage` component (modifiers + discovered), flattening in `AgeController`, page rendering of modifier grids + discovered styling | gametests: component round-trip, flattening equals old order |
| B | `AgeBlueprint` filler + organiser + summary page; config; hook into `checkFirstLink`; remove grammar | gametests: determinism (same seed → same book), required categories always present, player categories untouched, gates (nether biome ⇒ cave terrain), stability budget, 200-seed stress: no void terrain / no unstable Age |
| C | Desk BE (unlocked set, import slot, folder-only target, creative flag), menu/screens with category tabs, Scholar's desk item, portfolio removal + migration, Archivist/loot pages feed the import slot | gametests: import unlocks, folder-only, write-from-unlocked, creative desk has all; client smoke screenshots of each tab |
| D | Book view grouping, summary page, TESTING.md + REQUIREMENTS rewrite of §4.4 / §8.1 | selfcheck pages |

Order: ink mixer (independent) → A+B (one commit: the data model and the filler replace the grammar together) → D → C.

Progress: ink mixer done; A+B done (categories, `SymbolPage`, `AgeBlueprint`, `WorldBuildingConfig`, grammar removed,
modifier overlays on page icons, discovered ink); D done (category label + modifiers on the left page, summary page via
`AgeSummary`); C done (`SymbolKnowledge` player attachment, pages studied by use, Ages teach on arrival, desk rework
with `SymbolSurface` tabs, folder-only target, attach/detach modifiers, Scholar's desk, portfolio removed). Extra:
`/myst-qa-shelf` builds 14 lecterns with fixed-seed books for the visual QA matrix (`QaShelf`).

## 7. Decisions (taken 2026-10-03)

1. **Unlock scope: per player.** Knowledge follows the player (a player attachment, synced to the client). Symbols are
   learned by **using a symbol page** (right-click: the page is consumed, the symbol is unlocked) and by **arriving in
   an Age** (every symbol of that Age, modifiers included, is unlocked on arrival). There is no import slot on the desk.
2. **Empty Structures category: none** (original Mystcraft). `fill.structures.defaults = []`; the random fill may still
   add structures (40 %).
3. **Modifiers on the page: yes.** `SymbolPage(symbol, modifiers, discovered)`; the modifier glyphs are rendered as
   **overlays on the page's symbol** (corners of the icon, colour modifiers in their own colour).
4. **Grammar dropped.** `symbol/grammar` removed, the "Lacking … Features" dummies and Clear Modifiers with it. Card
   ranks stay for trade pricing.
5. **Dangerous picks: low chance, all configurable** (void 0 %, dark sun 4 %, meteors ≈1 %, dense ores 2 %), the
   instability budget keeps Ages stable.
6. **Star Fissure: 10 % of Ages** (`fill.starFissureChance`).
7. **Reorganise on bind: yes**, and the pages discovered at the first link go into the proper category order too
   (`AgeBlueprint.organise`).
8. **Page sources unchanged**: Archivist trades, library lecterns, Sealed Notebook give pages; a page is consumed to
   learn its symbol (see 1).
9. **Counts**: biomes 1–4 (weights 35/35/20/10), celestials 1–5, sky colours 0–4, world colours 0–3, structures 0–3,
   features 0–3 (defaults caves + surface lakes), effects 0–2.
10. **No backwards compatibility**: old worlds are discarded; every change replaces what was there (no migrations, no
    aliases).
11. **No playtesting between phases** unless needed to verify something.

## 8. Ink Mixer: one ingredient per effect (confirmed, implemented)

Price scales with how much the effect gives. No item grants two effects, no effect has two sources; mapping goes in
config (`inkmixer.ingredients = ["intra_linking=minecraft:ender_pearl", ...]`), `crafting.linkeffects.disabled` stays.

| Effect | Ingredient | Reasoning |
|---|---|---|
| Generate Platform | Clay Ball | safety feature, nearly free (original) |
| Maintain Momentum | Feather | niche, cheap (original) |
| Disarm | Gunpowder | a restriction used for traps / server rules, cheap (original) |
| Intra-Linking Only | Compass | a restriction ("only points home"), cheap and thematic |
| Intra-Linking | Ender Pearl | teleport anywhere in the same world: strong, mid-game, renewable |
| Relative Link | Amethyst Shard | niche advanced effect, mid-game, renewable |
| Following | Eye of Ender | strongest QoL (book never left behind): Nether-gated; alternative if that is too steep: Lead |
| *Clear all effects* | Black Dye | original dilution role |

Removed: mushroom stew, bottle o' enchanting, fire charge, `c:dusts/*` tag bindings, gold/iron nuggets, lead.

## 9. Session hand-off

Decisions are taken (§7); implementation follows the order in §6 with the usual cycle (Docker pipeline green per
phase, gametests + selfcheck coverage, TESTING.md / REQUIREMENTS.md updated, logical commits, jar copied to the
instance). A new session continues at the first phase not marked done under §6 "Progress".
