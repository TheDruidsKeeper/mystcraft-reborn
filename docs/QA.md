# QA

Rule: a manual check exists only when no test can assert the fact. Every item below answers "why can't a test see
this?" — if it could, write the test ([`docs/DEVELOPMENT.md`](DEVELOPMENT.md#writing-tests) "Writing tests") and delete the item.

## What each layer verifies

| Layer | Asserts | Cannot see |
|---|---|---|
| GameTests (`src/gametest`) | blueprint fill, symbol schema (incl. the block support matrix), knowledge, workstations, linking/portals/spawn, creatures, instability tick, facility placement and puzzle mechanics, and for the QA worlds (`QaWorldTests`): terrain type, feature materials, biome layouts, structure starts, weather state, effects | anything rendered |
| Server smoke (`SelfCheck`) | registries, datapacks, Age creation, generation speed, Facility assembly on a real server | terrain shape, placement quality |
| Client smoke (`ClientSelfCheck` + [`scripts/qa/compare.py`](../scripts/qa/compare.py)) | screens open, renderers run, sky light follows the Age's celestial angle; QA screenshots are compared to baselines (`VISUAL_DRIFT` warns, does not fail) | whether it looks *right* the first time |

## The QA shelf (`/myst-dev qa-shelf`)
One lectern per QA world, grouped in coloured sections with a sign each; every book is bound to a fixed-seed Age
(`command/QaShelf.sections()` is the single source of the matrix — tests and the client tour read the same list).
Row A starts with the home Linking Book on a bookstand (the way back from any world). The floor is fenced with a
walkway (open towards the player) and lit by a glowstone grid, so it reads at night.
`/myst-dev qa-visit <id>` links straight into one world. The `[qa]` log line of each lectern repeats the "look for".

| Section | World | Look for (human) | Asserted by tests |
|---|---|---|---|
| A Baseline (white) | A1 Empty book | nothing odd | stable (no symbol instability) |
| B Sky & celestials (light blue) | B1 Sky colours | gradient over the day, fog, night sky, no flicker | — (screenshot drift) |
| | B2 Celestial modifiers | sun path/speed, moon phase, star twinkle, rainbow | — (screenshot drift) |
| | B3 Dark sun, bright light | no sun disc, fully lit, stars by day | sky light vs celestial angle |
| | B4 Dark light, end sky | darkness, end sky texture, cloud cover | cloudy = overcast without precipitation |
| C World colours & weather (lime) | C1 World colours | colours apply, blend at biome borders | — (screenshot drift) |
| | C2 Rain, C3 Snow, C4 Storm | precipitation visuals, snow layers, lightning effect | raining / thundering state |
| D Terrain & features (orange) | D1 Flat, no sea, ravines | looks flat, ravine cuts, obelisk silhouettes | stone plane, no water, ravines carved, glowstone obelisks |
| | D2 Skylands | island shapes, trees, crystal clusters | air at sea level, land above |
| | D3 Amplified deep lakes | cliffs, lava lakes, tendril shapes, no horizon band | relief ≥ 24, lava lakes placed |
| | D4 Nether | cave roof, lava lakes, fortress integration | lava, netherrack, caves |
| | D5 End | island edge, spike shapes | end stone, single biome, obsidian spikes |
| | D6 Void + fissure | platform only, fissure visible | empty chunks |
| E Biomes & structures (yellow) | E1 Tiny biomes | patchwork look, village placement | ≥3 biomes, frequent transitions |
| | E2 Large biomes + Facility | biome scale; entrance in view, on the terrain; `qa_E2_facility_entrance` / `_lobby` shots | few transitions, one facility start, puzzle blocks generated (smoke) |
| F Creatures (red) | F1 Brutal swarm, F2 Peaceful meadow, F3 Lifeless | pressure / density / silence at night | spawn scaling (`CreatureTests`) |
| G Instability (purple) | G1 Unstable | effect pacing, decay spread, meteor visuals | effects registered |

### Screenshot regression
The client smoke tours every world (`ClientSelfCheck` steps `TOUR_*`) and writes `selfcheck_qa_<id>_<day|night>.png`.
[`scripts/qa/compare.py`](../scripts/qa/compare.py) reduces each to band colours + a thumbnail and compares with [`scripts/qa/baselines.json`](../scripts/qa/baselines.json);
drift writes status `VISUAL_DRIFT` and `out/qa-report.txt` but does **not** fail the layer (selfcheck + resource warnings still do). Defaults: band Euclidean distance **40**, thumbnail mean abs delta **28**.

**Stability contract:** after `--update`, an immediate second client-smoke with unchanged code must report `0 to review`. If it drifts, fix the capture environment — do not keep re-baselining.

To keep runs signature-stable, Docker [`client-smoke-entry.sh`](../docker/client-smoke-entry.sh) pins `renderClouds:false`, `particles:2` (minimal), vsync off, fixed FOV/gamma/AO, and `graphicsPreset:custom`. Non-weather shelf books pin `weather_off` so blueprint fill cannot inject cycling rain; B4 / C2–C4 keep their authored weather. Before each tour frame (`RenderFrameEvent.Pre`) the client hides the HUD, disables view bobbing, empties the hand, and locks yaw/pitch −30° (including `*RotO`); the PNG is grabbed on `RenderFrameEvent.Post` so the framebuffer matches. G1 disables instability (and clears meteors) before shooting so decay does not rewrite the ground between runs. The tour waits for nearby chunks, then settles ~100 ticks after each `myst time set`. Phase 1 still uses the vanilla skybox (`AgeSkyRenderer` returns false), so B4 asserts darkness/overcast, not a custom end-sky starbox.

Workflow:
1. Drift or `NEW` reported → look at `out/screenshots/selfcheck_qa_*.png`.
2. Looks right → `python scripts/qa/compare.py out/screenshots --update`, commit [`baselines.json`](../scripts/qa/baselines.json), then **rerun** client-smoke and confirm `PASSED` / `0 to review`.
3. Looks wrong → fix, rerun.
Adding a world: add it to `QaShelf.sections()`, run the client smoke, review, update baselines, verify a clean second run.

## Manual checklist (perceptual only)
Walk this once per release with the shelf, and after changes to rendering, GUI, audio or worldgen shapes.

**Look**
- [ ] Shelf worlds: the first baselines (2026-10-04) were reviewed only as thumbnails → look at `out/screenshots/selfcheck_qa_*.png` once at full size; known questions: D1's arrival view shows forest rather than the plane, D4's arrival is in unlit cave darkness. B3 (bright lighting rendering dark) was fixed by `client/AgeClientEnvironment` lightmap attributes; terrain no longer takes block pages (D1/D4 re-written).
- [ ] Sunrise/sunset tint and fog transitions are smooth at normal game speed (screenshots are single frames).
- [ ] Celestials: multiple suns/moons overlap sanely; rainbow arc; end-sky starfield; dark sun has no disc.
- [ ] Precipitation particles, snow layering, lightning flash and thunder audio in C2–C4.
- [ ] Facility (E2): entrance sits on the terrain; interior rooms are lit and connected; no cut-off pieces.
- [ ] Facility run-through (E2, survival): each locked room seals its exits behind you (never the way in); the
      lobby's four clue blocks match the dial order in a sequence room; the Symbol Altar names pages you learned on
      arrival; the Offering Pedestal names a craftable item; trial rooms eject keys that open their pedestal and the
      vault; the Facility Cache gives one home book per player and then blocks can be broken; `/myst locate facility`.
- [ ] Book rendering on stands/lecterns; page glyphs with modifier overlays; discovered-page ink.

**Feel**
- [ ] Creature pressure in F1 is threatening but survivable; F2 has no hostiles across a night; F3 is silent.
- [ ] G1: instability symptoms arrive within minutes and escalate; decay is visible and spreading.
- [ ] Chunk generation keeps up when flying over amplified / skylands Ages (no multi-second stalls).

**Interact**
- [ ] Writing desk: tabs, search, writing, attaching modifiers, drafts and their undo feel right; tooltips read well.
- [ ] Ink mixer, book binder, link modifier, folder screens: slot behaviour, labels, no overlapping text.
- [ ] Multiplayer: a second player sees Age sky/time/weather identically; link permissions apply; per-player rewards.

**Audio**
- [ ] Link sounds, portal hum, instability effects, meteor impact.
