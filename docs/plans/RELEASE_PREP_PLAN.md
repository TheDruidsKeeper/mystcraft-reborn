# Release preparation: QA strategy, QA shelf, commands, documentation — plan

Status: **approved 2026-10-04** — all §6 decisions taken as recommended (replace REQUIREMENTS with GAMEPLAY.md, shrink the cheatsheet to API_NOTES.md, consolidate commands under `/myst` + `/myst-dev`, build the screenshot regression, 21 worlds for now, `CLAUDE.md` entry point). R1–R3 and R5 done; R4 (screenshot tour) running — baselines pending. Delete this file once the baselines are committed.

Goal: ship-ready mod. Manual QA limited to what a human must judge; everything else automated. Debug/QA commands
reduced to a coherent, documented set. Documentation rewritten so an LLM (or a contributor) gets the right context
fast, with code as the single source of truth.

---

## 1. What the automated layers already cover (and what they cannot)

| Layer | Runs | Covers | Cannot cover |
|---|---|---|---|
| GameTests (`src/gametest`, 48 tests) | `scripts/gametest.sh` | Logic with observable state: blueprint fill, symbol schema, knowledge, workstations (desk, mixer, binder), linking/portals/spawn, creatures, instability tick, facility placement | Anything perceptual; anything needing a render |
| Smoke SelfCheck (`SelfCheck.java`) | `scripts/smoke.sh` | Registries bound, datapacks load, Age creation, chunk generation speed, facility assembly on a real server | Terrain *shape*, feature *placement quality* |
| Client smoke (`ClientSelfCheck.java`) | `scripts/client-smoke.sh` | Screens open, renderers run without crashing, screenshots exist | Nobody checks the pixels unless a human (or a pixel check) does |

**Where manual testing is still spending effort on things a machine can check:**

| Currently manual (TESTING.md checklist) | Automatable as | Layer |
|---|---|---|
| "flat obsidian terrain, no sea", "obelisks of glowstone", "spikes of obsidian", dense ores, lakes of lava | block census of generated chunks (count block X in region ≥ N; sea level column is air) | GameTest |
| nether / end / void / skylands / amplified terrain type | heightmap statistics + block palette of chunk (0,0) | GameTest |
| tiny / large / single biome layouts | biome sample grid: number of distinct biomes within radius, patch sizes | GameTest |
| villages / mineshafts / strongholds / fortress / vault generate | structure starts near origin (`createStructures` as in FacilityTests) | GameTest |
| star fissure present, arrival platform, spawn grounded | already covered | GameTest |
| sky / fog / grass / water colour applied | client: sample `AgeClientEnvironment` colour outputs for the Age at a given time (no pixels needed) | Client smoke (new asserts) |
| celestial modifiers (direction, phase, length, dark sun) | client: celestial angle / position maths from `AgeClientEnvironment` + screenshot pixel sampling (sky mean hue at fixed ticks) | Client smoke |
| lighting bright / dark | client: `level.getBrightness`/sky light at arrival | Client smoke |
| weather state in the Age | server: `level.isRaining()/isThundering()` after N ticks | GameTest |
| creature spawn rates / caps / difficulty | already covered | GameTest |
| instability effects appear | server: run the controller N ticks, assert effect active / decay blocks placed | GameTest |

**What stays manual (needs a human):** how things *look* (sky gradient smoothness, sunset tint, fog, horizon band,
celestial rendering, world-colour blending at biome edges, weather visuals), how things *feel* (terrain shapes,
feature density, structure integration with terrain, instability pacing, creature pressure), GUI usability and
layout, audio, performance stutter, multiplayer sync, and first-time review of any new feature.

**Principle:** every bullet in the manual checklist must answer "why can't a test assert this?". If the answer is
"it can", it moves to a test and leaves the checklist.

### 1.1 Making the remaining manual checks cheap: screenshot regression
Add a *shelf tour* to the client smoke: visit each QA world, teleport to the arrival point, screenshot at two fixed
times of day. A Python step (`scripts/qa/compare.py`, Pillow — already used for crops) compares against committed
baseline images (mean colour of sky band / horizon band / ground band, plus a perceptual hash) and fails on drift
above a threshold. A human looks only when a baseline is first created or a diff fires. This turns the visual matrix
from "re-verify everything each release" into "review the diffs".

---

## 2. Manual QA matrix (what the shelf holds)

Design rules: (a) one world per *conflict group* — attributes that visually interfere (storm vs sky colours,
dark lighting vs world colours) never share a world; (b) orthogonal attributes are *stacked* so each world verifies
3–6 things; (c) every world has a fixed seed and a "look for" line; (d) a world exists only for perceptual checks —
block-level facts are asserted by tests on the same seeds (§1).

| Section (floor colour) | World | Stacked attributes | Look for |
|---|---|---|---|
| **A Baseline** (white) | A1 Empty book | blueprint defaults | plain stable Age; nothing odd |
| **B Sky & celestials** (light blue) | B1 Sky colours | sky red→blue gradient, fog yellow, night sky purple, horizon colour | gradient over the day, smooth sunrise/sunset, no flicker |
| | B2 Celestial modifiers | sun east/half/green sunset, moon noon phase, twinkling double-speed stars, rainbow | sun path + speed, moon phase, star twinkle, rainbow arc |
| | B3 Dark sun, bright light | sun_dark, lighting_bright, stars | no sun disc, fully lit world, stars by day |
| | B4 Dark light, end sky | lighting_dark, stars_end_sky, cloudy | darkness level, end sky texture, cloud cover |
| **C World colours & weather** (lime) | C1 World colours | magenta grass, cyan foliage, red water | colours apply, blend at biome borders, water tint |
| | C2 Rain & snow | weather_rain, weather_snow (two Ages) | precipitation visuals, snow layering |
| | C3 Storm | weather_storm, env_lightning | thunder, lightning effect, storm sky |
| **D Terrain & features** (orange) | D1 Flat + materials | terrain_flat/obsidian, no_sea, obelisks/glowstone | terrain *looks* flat, obelisk silhouettes |
| | D2 Skylands | skylands, floating_islands/ice, huge_trees, crystal_formations | island shapes, trees, crystal clusters |
| | D3 Amplified + lakes | terrain_amplified, lakes_deep/lava, tendrils, no_horizon | cliffs, lava lakes, tendril shapes, missing horizon band |
| | D4 Nether | terrain_nether/lava, nether biomes, nether_fortress | cave roof, lava sea, fortress integration |
| | D5 End | terrain_end, end biome, spikes/obsidian | island edge, spikes |
| | D6 Void + fissure | terrain_void, star_fissure | platform only, fissure visible |
| **E Biomes & structures** (yellow) | E1 Tiny biomes | biome_tiny, desert/jungle/ice spikes, villages, ravines | patchwork look, village placement |
| | E2 Large biomes + Facility | biome_large, vault | biome scale, Facility entrance visible from spawn, structure vs terrain |
| **F Creatures** (red) | F1 Brutal swarm | hostile swarm/horde/brutal, passive sparse | pressure at night |
| | F2 Peaceful meadow | hostile none, passive dense/many | animal density, no hostiles |
| | F3 Lifeless | creatures_none | nothing spawns (also a test, but the *feel* is the point) |
| **G Instability** (purple) | G1 Unstable | meteors, accelerated, explosions, dense_ores | effect pacing, decay spread, meteor visuals |

21 worlds (was 17), but each attribute appears once and tests cover the block-level facts. Section F3 and E2 are
candidates to drop once their tests exist.

### 2.1 Shelf layout (`/myst-dev qa-shelf`)
* Sections are rows, 3 blocks apart (row = lecterns on a 1-high coloured platform, lecterns 2 apart); a 1-block
  walkway on all sides of every lectern; stone-brick path between rows.
* Floor under a section = that section's coloured concrete; a standing sign at the row start with the section name,
  a wall sign on each lectern pedestal with the world id + 2-line "look for".
* The command rebuilds in place (clears its own footprint only) and logs `[qa]` with seed, dimension and "look for".
* The *same* `QaShelf.cases()` list feeds the gametests (block census per seed) and the client smoke shelf tour.

---

## 3. Commands

Today: 15 roots (`/tpx`, `/myst-create`, `/myst-visit`, `/myst-agebook`, `/myst-locate`, `/myst-twi` +
`/myst-toggleworldinstability`, `/myst-spawnmeteor`, `/myst-permissions`, `/myst-regenchunk` (stub, throws),
`/myst-reprofile`, `/myst-dbg` (address strings), `/myst-time`, `/myst-toggledownfall`, `/myst-scene`,
`/myst-qa-shelf`).

Proposed: two roots.

| `/myst …` (ops, level 2) | Does | Replaces |
|---|---|---|
| `visit [name]` | create an Age (or find by name) and link in through the normal path | `/myst-visit` |
| `create [name]` | create an Age without travelling | `/myst-create` |
| `book [dim]` | give the Descriptive Book of an Age | `/myst-agebook` |
| `locate facility` | Facility entrance of this Age | `/myst-locate` |
| `time set/add …` | Age clock | `/myst-time` |
| `weather toggle/clear/rain/storm` | Age weather | `/myst-toggledownfall` |
| `instability status` | one readable summary (score, symbol/block/bonus parts, active effects, profiled chunks) | `/myst-dbg read …` |
| `instability toggle/reprofile/meteor` | | `/myst-twi`, `/myst-reprofile`, `/myst-spawnmeteor` |
| `permissions …` | link permissions | `/myst-permissions` |

| `/myst-dev …` (level 4) | Does |
|---|---|
| `scene [closeup|open|use …]` | debug showcase (used by the client smoke) |
| `qa-shelf` | the QA matrix shelf |

Removed: `/tpx` (vanilla `/execute in <dim> run tp` covers it), `/myst-regenchunk` (never implemented),
`/myst-dbg set/run` (nothing a test or `instability status` does not do better). `ClientSelfCheck` and the scripts
are updated to the new names; lang keys renamed.

---

## 4. Documentation

### 4.1 Diagnosis
| File | Lines | Verdict |
|---|---|---|
| `REQUIREMENTS.md` | 3086 | Original-mod (1.12) spec with 17 "Reborn revision" call-outs. The design is no longer bound to the original; the file mixes dead reference (grammar, 1.12 packets, GUI pixel sizes) with current truth in call-outs. **Replace** with a current-behaviour doc; the spec stays in git history. |
| `API_CHEATSHEET.md` | 2788 | Verified 26.1 signatures. Valuable *because* LLMs otherwise guess 1.21 APIs, but 90 % is method listings that the decompiled sources on disk answer better. **Shrink** to renames/gotchas + "how to look up the real source". |
| `TOOLCHAIN.md` | 1691 | Research snapshot incl. full MDK file dumps. **Delete**; pinned versions already live in `gradle.properties`. |
| `IMPLEMENTATION_CONTRACTS.md`, `impl/{blocks_menus,client,instability_entities_events,items_linking,resources,symbols,world,integration_review,integration_review_2,grammar_audit}.md` | ~1200 | Wave-1 port planning and reviews. Obsolete. **Delete**. |
| `impl/WORLD_BUILDING_PLAN.md` | 242 | Implemented. **Fold** its decisions into the behaviour doc; delete. |
| `impl/FACILITY_PLAN.md` | 195 | Active (Phase 3+). **Move** to `docs/plans/`; fold into the behaviour doc when done. |
| `ARCHITECTURE.md` | 104 | Mostly right; references classes. **Rewrite** tighter, drop the milestone list. |
| `TESTING.md` | 225 | Good bones; the manual checklist mixes automatable items. **Rewrite** per §1/§2. |
| `STRUCTURES.md` | 59 | Current. Keep. |
| `README.md` | 83 | Build section duplicates TESTING; has a `.ps1` section. **Trim** to user-facing + pointers. |

### 4.2 Target set (≈ 900 lines total instead of 9 800)

```
CLAUDE.md                 entry point for LLM sessions: build/test commands, where each doc is, conventions, the
                          dev cycle (fix → test → docs → pipeline green → commit → jar to instance). ~60 lines.
README.md                 users: what it is, install, gameplay loop, config files, license, credits. ~70 lines.
docs/ARCHITECTURE.md      packages → responsibilities → key classes (links, no signatures); data flow of an Age
                          (book → blueprint → AgeData → controller → generator → client env); conventions. ~120.
docs/GAMEPLAY.md          current behaviour by mechanic (pages/knowledge, desk, mixer, binder, linking, Ages:
                          categories & fill, instability, creatures, structures/Facility, config keys), each item
                          naming the class/resource that implements it instead of restating numbers. ~300.
docs/DEVELOPMENT.md       pipeline (Docker targets, scripts, outputs), log markers, commands (/myst, /myst-dev),
                          adding a gametest / client-smoke step / QA world, API lookup (decompiled sources path,
                          cheatsheet), release checklist. ~200.
docs/QA.md                §1 principle, the matrix (§2) with per-world "look for", manual checklist (perceptual
                          only), screenshot regression workflow. ~120.
docs/STRUCTURES.md        unchanged.
docs/API_NOTES.md         26.1 renames/gotchas only (from API_CHEATSHEET §0 + the verified surprises). ~150.
docs/plans/*.md           active plans only (FACILITY_PLAN, this file); deleted when folded in.
```

Rules for every doc: state *what* and *why*, point to *where* (`ClassName#method`, resource path) for *how*; no
numbers that live in config or code; no history ("playtest bug: …" moves to the test descriptions); one topic per
file; headings an LLM can grep.

---

## 5. Work breakdown

| Phase | Work | Exit |
|---|---|---|
| R1 Tests | GameTests for the automatable rows of §1 (block census, terrain type, biome layout, structure starts, weather, instability effects) on the QA seeds; client-smoke colour/lighting asserts | pipeline green; TESTING checklist items with a test are deleted |
| R2 Shelf | `QaShelf` sections + layout + signs (§2.1); cases list = §2 | screenshot of the shelf; `[qa]` log |
| R3 Commands | `/myst` + `/myst-dev` trees, removals, lang, ClientSelfCheck/scripts updated | gametest for command registration; client smoke green |
| R4 Shelf tour | client smoke visits each shelf world, screenshots, `scripts/qa/compare.py` + baselines | `out/qa/` diff report; baselines committed |
| R5 Docs | §4.2 set written; old files deleted; links checked | every class/resource reference resolves (script) |

R1–R3 are independent; R4 after R2; R5 last (so it documents the final state).

---

## 6. Decisions needed
1. **`REQUIREMENTS.md`**: replace with `GAMEPLAY.md` and delete (git history keeps the spec) — *recommended* — or keep
   as `docs/reference/` (excluded from normal context)?
2. **`API_CHEATSHEET.md`**: shrink to gotchas + lookup instructions (*recommended*) or keep whole?
3. **Commands**: consolidate under `/myst` + `/myst-dev` with the removals above (*recommended*), or keep names and
   only delete the dead ones?
4. **Screenshot regression (R4)**: build it (*recommended*; it is what makes the visual matrix cheap) or keep the
   shelf manual-only?
5. **Matrix size**: 21 worlds as in §2, or drop F3/E2 once their tests exist?
6. **`CLAUDE.md`** as the LLM entry point: yes (*recommended*) / fold into README.
