# Facility (puzzle vault) structures — plan

Status: **in progress** — Phase 1 (pipeline, built-in pack, Stonevaults room set) and Phase 2 (Vault symbol, `near_origin`
placement, spawn relation, `/myst-locate`) are implemented; Phase 3 (puzzle framework) is next. All decisions taken (§8).

Goal: one large, hard, puzzle-driven structure ("the Facility") near the arrival point of an Age. Solving it yields a
Linking Book back to overworld spawn plus worthwhile loot. Rooms are *not* hand-designed by us: they come from
permissively licensed structure packs and from vanilla, imported as structure-template `.nbt` files through a
repeatable pipeline, and assembled by the vanilla jigsaw system so new rooms can be dropped in indefinitely.

---

## 1. Research summary

### 1.1 File format → vanilla structure templates (`.nbt`)

| Format | Produced by | Verdict |
|---|---|---|
| Vanilla structure template `.nbt` | structure blocks, Axiom, most datapack/mod authors | **Use this.** Loaded natively by `StructureTemplateManager` from `data/<ns>/structure/`, zero dependencies, DataFixer-upgraded on load (older-version files still work), rotation/mirror/processors/jigsaw built in. |
| Sponge `.schem` (v2/v3) | WorldEdit, Axiom | Convertible (block palette + block entities map 1:1). Needs a converter (§3). |
| Litematica `.litematic` | Litematica | Convertible, slightly more work (bit-packed regions). |
| MCEdit `.schematic` (legacy numeric ids) | pre-1.13 tools | Avoid: needs a 1.12 → flattening id map. |

Sizes: the 48³ limit only applies to in-game structure *blocks*; templates loaded from files have no limit. The
practical cap is the structure's `max_distance_from_center` (≤ 128 blocks from the start piece) — a whole facility
up to ~250×250 is fine.

### 1.2 Where rooms come from (license-compatible with our LGPL-3.0-or-later)

| Source | License | MC version | What it gives us |
|---|---|---|---|
| **Vanilla trial chambers** (`minecraft:trial_chambers/*` template pools, in the game jar) | referenced by id, not redistributed | 26.1 | Dozens of corridor / chamber / trap rooms with Trial Spawners and Vaults, already jigsaw-connected. Zero import work — our structure JSON can pull these pools directly. |
| **Stonevaults** (TheGrimsey) | MIT ("free to port, modify, use it as you wish") | 26.1 / 26.2 | Jigsaw dungeons, towers and igloos with simple puzzles; pools + processors reusable. |
| **Moog's Voyager Structures** (MVS) | MIT | 26.3 | 130+ vanilla-style structures incl. dungeons; a deep pool of large builds to carve rooms from. |
| **Moog's End / Nether Structures** | LGPL-3.0 | 26.3 | Same author, exotic palettes (useful for nether/end-material Ages). |
| **Repurposed Structures** (TelepathicGrunt) | LGPL-3.0 | 26.3 | Many vanilla-style variants; also the reference jigsaw tutorial code. |
| Masik's Puzzle Dungeon / Ancient Puzzles | LGPL-3.0 | 1.20.1 / 1.18.2 | Puzzle-room layouts (DataFixer upgrades the files; their Java puzzle logic is not reused). |
| YUNG's Better Dungeons/Strongholds | LGPL-3.0 | 1.21.4 | Large multi-room pieces. |

Not usable: Dungeons and Taverns, When Dungeons Arise, IDAS, Dungeons Enhanced, Additional Structures (all
All-Rights-Reserved); Dungeon Crawl / Roguelike Dungeons (GPL-3.0 — would force relicensing); Towns and Towers /
DeCubed (CC-BY-NC-SA — share-alike and non-commercial clauses). Community schematic sites (Planet Minecraft,
minecraft-schematics, GrabCraft) have per-upload, mostly unstated terms — not worth the risk for shipped content.

Attribution: every imported file gets a line in `NOTICE.md` (pack, author, license, original path) and the pipeline
manifest (§3) carries the same metadata so it cannot be lost.

### 1.3 Online converters (for one-offs only)
createmod.com and bloxelizer.com convert `.schem ⇄ .nbt ⇄ .litematic` in the browser; fine for a single file, not
for a reproducible pipeline — hence the script in §3.

---

## 2. Design

### 2.1 Shape of a Facility
Jigsaw structure, four pool tiers, each a directory of `.nbt` pieces:

```
entrance   → 1 of N surface entrances (the only part visible from spawn)
corridors  → connectors / stairs / traps (vanilla trial-chamber corridors mixed with imported ones)
puzzle     → rooms with a puzzle marker set (door + lock), 3–6 per facility, depth-limited
vault      → terminal room: reward chest(s) + the Linking Book pedestal, sealed by the last door
```
Variety comes from the pools: every Age picks a different combination; adding a file to a directory adds it to the
pool after the next build (§3).

### 2.2 Puzzles are mod mechanics placed into generic rooms (no room design needed)
Imported rooms are retrofitted with **data markers** only (vanilla structure block in DATA mode with a string),
which a custom `StructureProcessor` resolves at placement. The puzzle logic lives in reusable mod blocks:

| Marker | Resolves to | Puzzle |
|---|---|---|
| `door` | **Warded Door** (indestructible in survival; opens when its lock is satisfied) | gate between tiers |
| `lock:symbol` | **Symbol Altar** — wants N specific symbol pages hidden elsewhere in the facility (pages chosen from the Age's own book; clue = page lecterns in earlier rooms) | Mystcraft-flavoured key hunt |
| `lock:sequence` | lever / button bank with a per-Age random code; clue rendered on item frames / signs via `clue` markers | logic |
| `lock:offering` | pedestal wanting an item craftable from facility loot (e.g. ink + paper → written page) | crafting gate |
| `lock:trial` | vanilla **Trial Spawner** wave + **Vault** keyed by trial keys (vanilla blocks, our loot tables) | combat |
| `loot:<table>` | chest with the named loot table | rewards |
| `reward:linkbook` | pedestal/bookstand holding a Linking Book bound to overworld spawn | the exit |
| `clue:<n>` | sign / item frame showing part of the sequence code | — |
| `spawn` | optional: where the player arrives (if spawn is inside the entrance, decision §8.2) | — |

Vanilla redstone contraptions inside rooms keep working unmodified; parkour/maze rooms need no markers at all.

### 2.3 Protection (decision §8.3)
Default proposal: block-break and explosion events are cancelled inside the structure's bounding box (looked up via
`StructureManager` by chunk) until the facility's vault door has been opened (flag in `AgeData`); creative mode and
ops are exempt. This seals *any* imported room without per-block "warded" variants. Alternative: only the vault room
and warded doors are protected (classic Minecraft "dig around it" allowed).

### 2.4 Placement and spawn
* New `StructurePlacement` type `mystcraft:near_origin`: exactly one candidate chunk per Age, deterministic from the
  Age seed, within `radius` chunks of (0,0) (default 2–6 chunks). Registered in `BuiltInRegistries.STRUCTURE_PLACEMENT`.
* The facility structure set is added to `AgeChunkGenerator#createState` (always, or symbol-gated — §8.1). Biome
  predicate: a `#mystcraft:has_structure/facility` tag that contains every biome (Ages mix arbitrary biomes).
* `terrain_adaptation: beard_box` for the entrance; interior tiers generate underground (`project_start_to_heightmap`
  on the entrance only), so arbitrary terrain symbols do not cut the rooms.
* `AgeSpawn.findSpawn`: when the Age has a facility, resolve its start position (`findNearestMapStructure` on the
  facility tag with the origin chunk pre-generated) and place the spawn relative to it (§8.2). Star-Fissure Ages keep
  their rule; facility wins ties.
* `/myst-locate facility` for QA.

### 2.5 Rewards
* Linking Book bound to overworld spawn (new loot function `mystcraft:set_link_target` building the book's
  components; reuses `LinkingBookItem`).
* `mystcraft:chests/facility_vault` loot table: rank-4/5 symbol pages, notebooks, enchanted gear, a Link Modifier,
  plus `mystcraft:chests/facility_room` for mid-run chests. Numbers in §8.5.

---

## 3. Pipeline (how we keep adding rooms)

```
scripts/structures/
  import.py        .schem / .litematic / .nbt → data/mystcraft/structure/facility/<tier>/<name>.nbt
                   (nbtlib; normalises DataVersion, strips entities unless --keep-entities, records provenance)
  manifest.json    per file: source pack, author, license, url, tier, weight, tags (e.g. "needs-markers", "trial")
  gen_pools.py     manifest + directory listing → src/generated/resources/data/mystcraft/worldgen/template_pool/*.json
                   (runs from Gradle `generateStructurePools`, wired before processResources; CI fails if the
                   generated JSON is stale)
docs/STRUCTURES.md how to add a room: import → place markers in a dev world → re-save → add manifest entry → build
```
Marker retrofit happens once per room in a creative dev world (`/myst-scene` already exists for layouts): load the
template with a structure block, drop DATA markers, save, export. That is the only manual step and it is minutes per
room, not design work.

Mixed vanilla content needs no files at all: pool JSON can reference `minecraft:trial_chambers/corridors/...`.

---

## 4. Implementation phases

| Phase | Work | Exit criterion |
|---|---|---|
| **1 Pipeline** ✅ | `scripts/structures/*`, Gradle task, built-in datapack registration, `docs/STRUCTURES.md`, NOTICE entries, 1 imported test room | `./gradlew generateStructurePools` reproducible in Docker; smoke test loads the pools |
| **2 Worldgen** ✅ | `near_origin` placement, facility `Structure` + set + biome tag, `createState` wiring, `AgeSpawn` alignment, `/myst-locate facility` | gametest: every new Age has exactly one facility within R of origin; spawn relation holds; smoke green |
| **3 Puzzle framework** | marker processor, Warded Door, Symbol Altar, sequence lock + clues, offering pedestal, trial/vault loot, protection rule, `AgeData` flags, reward linkbook loot function | gametests per lock type (placed room → lock satisfied → door opens; break cancelled inside bounds) |
| **4 Content v1** | import 2–3 entrances, 6–10 puzzle rooms, 2 vaults from §1.2 sources + vanilla trial-chamber corridors; marker retrofit; weights | client-smoke screenshot of an entrance + a puzzle room; manual run-through solvable in TESTING.md |
| **5 Ship** | TESTING.md checklist (by mechanic), REQUIREMENTS "Reborn revision" §11.x, config keys (radius, enable, protection mode), jar to MultiMC | full pipeline green, playtest report |

Order: 1 → 2 → 3 can be developed in parallel with 4 once markers are specified (§2.2 is the contract).

---

## 5. Config keys (proposal)
`facility.enabled`, `facility.radiusChunks` (4), `facility.protection` (`full` / `vault_only` / `none`),
`facility.puzzleRooms.min/max` (3/6), `facility.rewardLinkbook` (true).

## 6. Risks
* **26.1 jigsaw/structure JSON schema**: verify field names against the 26.1 vanilla datapack (`trial_chambers`
  structure JSON in the client jar) before writing ours; add to API_CHEATSHEET.
* **Custom chunk generator + structures**: verified — the jigsaw assembles 36 pieces on the dedicated server (bbox
  ~143×57×169). `terrain_adaptation: beard_thin` is declared but our legacy terrain gens ignore the Beardifier, so the
  entrance may float / be buried on rough terrain: Phase 4 adds a flat-fill foundation processor or marker if playtests
  show it.
* **GameTest server has `generateStructures=false`**: tests call `ChunkGenerator#createStructures` directly
  (FacilityTests); the smoke SelfCheck covers real generation.
* **`Level#getHeight` never generates**: it answers `minY` for unloaded chunks, so any spawn search outside the
  pre-generated area must `getChunk` first (AgeSpawn.groundAt(load=true)).
* **Old-version `.nbt` (1.18/1.20) imports**: DataFixer handles blocks; block entities with removed fields may log
  warnings — the importer normalises and the smoke test catches hard failures.
* **Spawn-inside-structure cold start**: the origin chunks must be generated before the first link so the structure
  start exists when the spawn is resolved — `AgeSpawn` already forces chunks via `getHeight`; add an explicit
  `getChunk(..., STRUCTURE_STARTS)` for the placement chunk.

## 7. Testing
* Gametest: template placement with every marker type; lock state machines; protection rule; placement determinism
  (same seed → same chunk); spawn/facility distance.
* Smoke: datapack registries (structure, structure_set, template_pool, processor_list) load.
* Client smoke: `/myst-scene facility` builds an entrance + one room and screenshots it.
* Manual (TESTING.md): full solve path; bypass attempts; multiplayer re-entry.

## 8. Decisions needed
1. **Which Ages get a Facility** — DECIDED: a new **"Vault" symbol** (Structures category in WORLD_BUILDING_PLAN §2
   row 9; random fill ~15 % of unwritten Ages; writable by players). Facility set is only enabled in `createState`
   when the symbol's populator is present.
2. **Spawn relation** — DECIDED: arrive **60–120 blocks** from the entrance, entrance visible (spawn search
   constrained to that annulus around the facility start).
3. **Protection** — DECIDED: **full bounding-box protection until solved**, `facility.protection` config to relax.
4. **Content sources for v1** — DECIDED: vanilla trial-chamber pools + MIT packs (Stonevaults, MVS) + LGPL packs
   (Repurposed Structures, YUNG's, Moog's End/Nether, Masik's). GPL / ARR / NC packs excluded.
5. **Reward tuning** — DECIDED: **everything per player**. The vault uses per-player containers (vanilla `Vault`
   block semantics — each player who opens it rolls the loot table once, tracked by UUID); the linkbook pedestal is a
   mod block with the same per-player rule. Lock state is per-Age (doors stay open once solved), rewards are not.
6. **Puzzle set v1** — DECIDED: **all five lock types** (`symbol`, `sequence`, `offering`, `trial`, plus the
   `clue` system) in Phase 3.
7. **Difficulty scaling** — DECIDED: **static**; room count 3–6 and code length are config keys only.
8. **Shipping form** — DECIDED: **separate bundled datapack**. Rooms, pools, processor lists and the structure JSON
   live in `src/main/resources/datapacks/mystcraft_facility/` registered as a built-in pack via
   `AddPackFindersEvent` (enabled by default, `facility.enabled` toggles it). Players add rooms by dropping their own
   datapack with extra `template_pool` entries (pools are tag-like: our pool JSON references a
   `#mystcraft:facility/puzzle` structure tag where possible, or players override the pool file). `docs/STRUCTURES.md`
   documents the player path as well as ours. Only the Java mechanics (blocks, placement type, processor) stay in the
   mod proper.
