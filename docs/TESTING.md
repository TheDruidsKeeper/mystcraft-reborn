# Testing

Everything that can be verified without a human runs headless in Docker (same `Dockerfile` locally and in CI, no
platform-specific scripts). The manual checklist at the end is only for what a renderer on a real GPU and a human eye
can judge. Each layer is cheap to run on its own; run them in order when something is unclear.

| Layer | Command | Runs | Covers | Time |
|---|---|---|---|---|
| 1 Build + unit | `scripts/build.sh` (`--target export`) | `gradle build`: compile, `AssetIntegrityTest` | API compatibility; every blockstate/model/texture/atlas/sound/lang reference | ~40 s |
| 2 Server smoke | `scripts/smoke.sh` | dedicated server + `SelfCheck` (`MYSTCRAFT_SELFCHECK=1`) | registries, datapacks, AT, symbols/grammar, dimension type, Age creation + chunk generation | ~2 min |
| 3 Game tests | `scripts/gametest.sh` | GameTest server + `mystcraft_tests` mod (`src/gametest`) | behaviour: book binding, Age arrival/platform, portals, Disarm, instability effects, fluids, star fissure | ~1.5 min |
| 4 Client smoke | `scripts/client-smoke.sh` | dev client under Xvfb + Mesa, driven by `ClientSelfCheck` | model baking, screens, BERs, Age sky/fog/tints, link through the real client path; **screenshots** | ~6 min |
| 5 Manual | checklist below | your client | look & feel, sound, UI interaction, performance | — |

CI (`.github/workflows/build.yml`) runs layers 1–4 on every push; artifacts: `mystcraft-reborn-jars`, `smoke-log`,
`gametest-log`, `client-smoke` (log + screenshots).

## Outputs

All stages export into `out/` (gitignored) and keep the Docker build output in `*-docker.log`:

| File | Content |
|---|---|
| `out/mystcraft-neoforge-26.1-<version>.jar` | the mod |
| `out/smoke.log`, `out/smoke-status.txt` | dedicated server log, `PASSED` / `SELFCHECK_FAILED` / `SERVER_DID_NOT_LOAD` |
| `out/gametest.log`, `out/gametest-status.txt`, `out/gametest-results/*.xml` | game-test server log, `PASSED` / `FAILED`, JUnit summary |
| `out/client-smoke.log`, `out/client-smoke-status.txt`, `out/screenshots/selfcheck_*.png` | client log, status, screenshots (`01_overworld`, `02_scene_overworld`, `03_age_arrival`, `04_scene_age`, `05_age_night`) |

## Log markers

The mod logs the decisions that matter for bug reports at INFO under bracketed tags, so a report only needs the
`debug.log` (client: `<instance>/.minecraft/logs/debug.log`; server: `logs/debug.log`). `grep -E "\[(spawn|link|portal|worldgen|scene|clientcheck|selfcheck)\]"` is the first thing to run on any log.

| Tag | Where | Meaning |
|---|---|---|
| `[spawn]` | `AgeSpawn`, `LinkController.defaultSpawn` | spawn determination (biome hit / fissure / none), ground snap, arrival platform counts |
| `[link] refused <entity> -> <dim>: <reason>` | `LinkListeners.isLinkPermitted` | every refused link with its reason, rate-limited per entity + reason (portals retry each tick) |
| `[worldgen]` | populators | star fissure position per Age |
| `[scene]` | `/myst-scene` | where the debug scene was built and where the viewer stands |
| `[clientcheck]` / `CLIENT SELFCHECK PASSED|FAILED` | `ClientSelfCheck` | headless client script progress |
| `[selfcheck]` / `SELFCHECK PASSED|FAILED` | `SelfCheck` | server self-check |
| `Created Age`, `Bound descriptive book`, `Registered dynamic dimension` | `AgeManager`, `DescriptiveBookItem` | Age lifecycle |
| `Symbol <id> failed to register logic` | `AgeController` | a symbol threw while compiling an Age (ERROR) |

Debug commands (OP, also used by the client smoke):

| Command | Effect |
|---|---|
| `/myst-scene` | builds the showcase in front of you: writing desk, bookstand + book, lectern + book, ink mixer, book binder, link modifier, ink pool, every decay block, a crystal column, a powered crystal portal, star fissure blocks; teleports you to the viewpoint |
| `/myst-visit [name]` | creates a new Age and links you into it through the normal link path (spawn search, ground snap, platform); gives you the bound Descriptive Book |
| `/myst-create [name]`, `/myst-agebook [dim]`, `/tpx`, `/myst-time set day|night`, `/myst-twi`, `/myst-spawnmeteor`, `/myst-dbg` | existing admin / debug commands |

## Writing game tests

`src/gametest/java/.../gametest/*Tests.java`, NeoForge test framework (`net.neoforged:testframework`, dev runs only).
A test is a static method taking `ExtendedGameTestHelper`, annotated `@GameTest`, `@EmptyTemplate("WxHxD", floor = true)`
and `@TestHolder(description = ...)`. Coordinates are template-relative; the floor is y = 0. Use `Check.run(helper, ...)`
around bodies that call into the mod so unexpected exceptions reach the log with a stack trace. Age tests are slow the
first time (chunk generation) — give them `timeoutTicks = 20 * 60`. Entities teleported into another level only become
visible to `level.getEntity(uuid)` on the next tick: poll with `startSequence().thenWaitUntil(...)`.

Run a single test locally without Docker (JDK 25): `./gradlew runGameTestServer` runs all; in the dev client
`/tests` lists and runs them interactively.

## What the headless layers already verify (reported bugs)

| Report | Root cause | Headless check |
|---|---|---|
| Sun/moon racing across the sky | `SkyRenderState` angles are radians; degrees were written | client smoke screenshots `03`/`05` (visual); no automated angle check possible without a GPU oracle |
| Portal does nothing | receptacle inventory hands out copies; a Descriptive Book bound on contact was never stored → "book is not bound" | `portalBindsUnboundBook`, `portalLinksEntity` |
| Portal renders as separate cubes | faces shared between portal blocks not culled | `LinkPortalBlock.skipRendering`; screenshot `02`/`04` |
| Spawn floating / no platform | Age arrival used the overworld respawn point | `arrivalLandsOnPlatform`, `ageDefaultSpawnIsGrounded`, `[spawn]` log lines |
| Effect timers flicker | potion re-applied every chunk tick | `potionEffectNotReappliedEveryTick` |
| Stuck in ink | custom fluid had no movement logic (`isWaterLike`) | `inkIsSwimmable` |
| No star fissures | generated in chunk (0,0) while arrivals landed elsewhere | `starFissureGeneratesAtOrigin` (fissure present, spawn kept near it), `[worldgen]` log |
| World failed to reopen (`AgeBiomeSource used without a server`) | level.dat validated on the client before the server exists | client smoke creates and reopens worlds; smoke/gametest exercise the codec |
| Client ERROR spam `biome_native failed to register logic` | client built server-only biome controller | no ERROR lines in `client-smoke.log` (checked by the stage) |

## Manual checklist

Build (`scripts/build.sh`), copy `out/*.jar` into the instance, start a **new** creative world, run `/myst-scene`
first — it puts every renderable block in front of you. Then `/myst-visit`.

Report format: one line per failed item + the `debug.log` of that session (zip the `logs/` folder); screenshots help for
anything visual. Lines tagged `[spawn]`, `[link]`, `[scene]` are what I will read first.

### A. Rendering (overworld, `/myst-scene`)
- [ ] Writing desk: full desk visible (table top, legs, backboard) with the desk texture, both halves, from all sides. *If parts are missing, screenshot from two angles — this is the open "missing most of the image" report.*
- [ ] Bookstand and Lectern: textured (no purple/black checker), book shown on top, title readable when looking at it.
- [ ] Ink mixer, book binder, link modifier: textured on all sides.
- [ ] Ink pool: dark liquid, animated surface; **walk into it: you can swim, move and jump out**.
- [ ] Bucket of Black Ink in hand/inventory shows a dark fluid overlay on the bucket.
- [ ] Decay blocks: five distinct textures.
- [ ] Crystal portal: visible, tinted, renders as **one** translucent volume (no inner cube faces). Walking through links you into the scene's Age.
- [ ] Star fissure block: renders (sky/void look), no missing texture.
- [ ] Falling blocks (hit a crystal column or wait for instability crumble): real block model, lit.

### B. Linking
- [ ] Right-click an untitled Descriptive Book: Age named "Age N" (not "???"); book title follows.
- [ ] Arrival: standing on a 3×3 cobblestone pad at ground level with 2 blocks of head room, never floating, never buried. Repeat for 5 Ages (`/myst-visit` each time).
- [ ] Linking Book back to the overworld returns you to the exact spot.
- [ ] Portal with an **unused** Descriptive Book: first contact creates the Age; the book in the receptacle is now bound (take it out: it has the Age name).
- [ ] Linking while riding (horse/boat): refused with a `[link] refused ...` line in the log; dismount and retry works.

### C. Sky / time (inside an Age)
- [ ] Sun and moon move at vanilla speed (full day ≈ 20 min at period 1.0); `/myst-time set night` darkens the sky; stars appear.
- [ ] Sky and fog colours change at sunrise/sunset; no flicker.
- [ ] Ages with colour symbols (write a book with a grass/foliage colour page): grass blocks and leaves take the colour; **water does not yet** (documented gap).
- [ ] Weather: rain/thunder in an Age with the matching symbols; no client errors.

### D. Instability
- [ ] Unstable Age (many pages / `/myst-twi`): potion effects appear with **stable** HUD timers (no per-tick reset); meteors, lightning, decay spread, crumble each visible at least once over ~10 minutes.
- [ ] Performance: no "Can't keep up" spam in the server log while standing in a loaded Age.

### E. Persistence
- [ ] Quit to title and reopen the world: no "Failed to load level data" error; every Age dimension reloads; `[link]` into a previously visited Age lands you on the same platform.
- [ ] Dedicated server (optional): same as above over LAN.

### F. UI
- [ ] Writing desk screen: notebook tabs, page surface, search, name field, ink tank all render inside the window texture; nothing is cut off at 1920×1080 and GUI scale 2/3.
- [ ] Book binder, ink mixer, link modifier, folder, book screens open and close without log errors.
