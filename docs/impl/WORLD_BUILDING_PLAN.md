# World-building system and writing desk rework — plan

Status: **proposal, awaiting decisions** (see §7). Nothing in this document is implemented yet.

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

Import slot: a symbol page (loot, trade, Sealed Notebook) or a **bound Descriptive Book** placed here unlocks its
symbol(s) — pages are consumed, books are not. Unlock scope: **decision §7.1**.

Creative: a separate uncraftable **"Scholar's Writing Desk"** item places the same block with `allUnlocked=true` (every
registered symbol available). Link Modifier already has no recipe (stays that way).

Removed: `PortfolioItem` (existing portfolios convert into folders on load), notebook tabs, `MSG_ADD_TO_SURFACE` /
`REMOVE_FROM_*` / `ADD_TO_TAB` messages, AZ/ALL buttons (replaced by category tabs + search).

Guidance in the UI: category header text + tooltip ("Required: exactly one. Default: Standard World."), symbol tooltip
gains "Takes: colour modifier" / "Needs: cave terrain" lines, discovered pages show "discovered" in the book and strip.

## 5. Config (`mystcraft-common.toml`, section `worldbuilding`)

```
fill.instabilityBudget = 0
fill.<category>.chance = <percent>          # optional categories
fill.<category>.min / .max                   # counts
fill.<category>.weights = ["id=weight", ...] # per-symbol weights, "0" removes a symbol from random fill
fill.celestials.modifierChance.direction/phase/sunset = 30/30/20
fill.materials.exoticChance = 5
desk.unlockScope = DESK | PLAYER | WORLD
```

## 6. Work breakdown (phases, each ends green on the Docker pipeline)

| Phase | Work | Tests |
|---|---|---|
| A | `SymbolCategory`, `SymbolPage` component (modifiers + discovered), flattening in `AgeController`, page rendering of modifier grids + discovered styling | gametests: component round-trip, flattening equals old order |
| B | `AgeBlueprint` filler + organiser + summary page; config; hook into `checkFirstLink`; remove grammar | gametests: determinism (same seed → same book), required categories always present, player categories untouched, gates (nether biome ⇒ cave terrain), stability budget, 200-seed stress: no void terrain / no unstable Age |
| C | Desk BE (unlocked set, import slot, folder-only target, creative flag), menu/screens with category tabs, Scholar's desk item, portfolio removal + migration, Archivist/loot pages feed the import slot | gametests: import unlocks, folder-only, write-from-unlocked, creative desk has all; client smoke screenshots of each tab |
| D | Book view grouping, summary page, TESTING.md + REQUIREMENTS rewrite of §4.4 / §8.1 | selfcheck pages |

Order: A → B → D → C (C is the largest UI piece and depends on A/B's data model).

## 7. Decisions needed

1. **Unlock scope**: per desk (base building, shareable in multiplayer), per player (knowledge follows you), or per
   world (everyone shares)? Proposal: **per desk**, with a desk-to-desk copy by placing a bound book in the import slot.
2. **Empty Structures category**: "normal Minecraft" = villages/mineshafts/dungeons/ravines on in every Age, or none
   (original Mystcraft)? Proposal: **vanilla set on** (matches "defaults apply"), off via config.
3. **Modifiers on the page** (data-model change, §3) — confirm. Alternative keeps the sequential model and only
   *displays* modifiers grouped, which is fragile once pages are reordered.
4. **Drop the original grammar** entirely (category system replaces it; card ranks stay for trades)? Proposal: yes.
5. **Dangerous picks in random fill**: never, or low chance as in §2 (void 0 %, dark sun 4 %, meteors ~1 % overall,
   dense ores 2 %)? Proposal: low chance, all configurable, budget keeps the Age stable.
6. **Star Fissure** in random fill: never (original), or a small chance (gives a way home)? Proposal: 10 % of Ages.
7. **Reorganising player pages on bind** into category order (needed for the grouped layout) — confirm.
8. **Page sources stay**: Archivist trades, library lecterns, Sealed Notebook → all feed the import slot. Confirm; or
   should the Archivist sell "unlock" directly?
9. **Biome count** 1–4 and celestial ranges above — adjust?

## 8. Ink Mixer: one ingredient per effect (proposal, awaiting confirmation)

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

Next session: read this document, ask the decisions in §7 and confirm §8 (one message, numbered, with the proposals
as defaults), then implement in the phase order of §6 following the usual cycle (Docker pipeline green per phase,
gametests + selfcheck coverage, TESTING.md / REQUIREMENTS.md updated, logical commits, jar copied to the instance).
