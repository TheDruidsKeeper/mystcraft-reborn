# Testing

Everything that can be verified without a human runs headless in Docker (same `Dockerfile` locally and in CI, no
platform-specific scripts). The manual checklist at the end is only for what a renderer on a real GPU and a human eye
can judge. Each layer is cheap to run on its own; run them in order when something is unclear.


| Layer          | Command                                | Runs                                                      | Covers                                                                                                    | Time     |
| -------------- | -------------------------------------- | --------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- | -------- |
| 1 Build + unit | `scripts/build.sh` (`--target export`) | `gradle build`: compile, `AssetIntegrityTest`             | API compatibility; every blockstate/model/texture/atlas/sound/lang reference                              | ~40 s    |
| 2 Server smoke | `scripts/smoke.sh`                     | dedicated server + `SelfCheck` (`MYSTCRAFT_SELFCHECK=1`)  | registries, datapacks, AT, symbols/grammar, dimension type, Age creation + chunk generation               | ~2 min   |
| 3 Game tests   | `scripts/gametest.sh`                  | GameTest server + `mystcraft_tests` mod (`src/gametest`)  | behaviour: book binding, Age arrival/platform, portals, Disarm, instability effects, fluids, star fissure | ~1.5 min |
| 4 Client smoke | `scripts/client-smoke.sh`              | dev client under Xvfb + Mesa, driven by `ClientSelfCheck` | model baking, screens, BERs, Age sky/fog/tints, link through the real client path; **screenshots**        | ~6 min   |
| 5 Manual       | checklist below                        | your client                                               | look & feel, sound, UI interaction, performance                                                           | —        |


CI (`.github/workflows/build.yml`) runs layers 1–4 on every push; artifacts: `mystcraft-reborn-jars`, `smoke-log`,
`gametest-log`, `client-smoke` (log + screenshots).

## Outputs

All stages export into `out/` (gitignored) and keep the Docker build output in `*-docker.log`:


| File                                                                                     | Content                                                                                                                  |
| ---------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `out/mystcraft-neoforge-26.1-<version>.jar`                                              | the mod                                                                                                                  |
| `out/smoke.log`, `out/smoke-status.txt`                                                  | dedicated server log, `PASSED` / `SELFCHECK_FAILED` / `SERVER_DID_NOT_LOAD`                                              |
| `out/gametest.log`, `out/gametest-status.txt`, `out/gametest-results/*.xml`              | game-test server log, `PASSED` / `FAILED`, JUnit summary                                                                 |
| `out/client-smoke.log`, `out/client-smoke-status.txt`, `out/screenshots/selfcheck_*.png` | client log, status, screenshots (`01_overworld`, `02_scene_overworld`, `02a`–`02g` close-ups, `02z_screen_*` every GUI, `03_age_arrival`, `04_scene_age`, `04b_age_book`, `05_age_night`) |




## Log markers

The mod logs the decisions that matter for bug reports at INFO under bracketed tags, so a report only needs the
`debug.log` (client: `<instance>/.minecraft/logs/debug.log`; server: `logs/debug.log`). `grep -E "\[(spawn|link|age|panel|portal|worldgen|scene|clientcheck|selfcheck)\]"` is the first thing to run on any log.


| Tag                                                                     | Where                                     | Meaning                                                                                        |
| ----------------------------------------------------------------------- | ----------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `[spawn]`                                                               | `AgeSpawn`, `LinkController.defaultSpawn` | spawn determination (biome hit / fissure / none), ground snap, arrival platform counts         |
| `[link] refused <entity> -> <dim>: <reason>`                            | `LinkListeners.isLinkPermitted`           | every refused link with its reason, rate-limited per entity + reason (portals retry each tick) |
| `[worldgen]`                                                            | populators                                | star fissure position per Age                                                                  |
| `[age] <name> ticking: time, celestials, celestial angle (day/night)`    | `AgeTicker`                               | first tick of an Age level after load; new Ages are moved to morning here                      |
| `[panel]`                                                               | `PanelImageStorage`, `PanelImages`        | link panel photo requests, uploads and storage                                                 |
| `[scene]`                                                               | `/myst-scene`                             | where the debug scene was built and where the viewer stands                                    |
| `[clientcheck]` / `CLIENT SELFCHECK PASSED                              | FAILED`                                   | `ClientSelfCheck`                                                                              |
| `[selfcheck]` / `SELFCHECK PASSED                                       | FAILED`                                   | `SelfCheck`                                                                                    |
| `Created Age`, `Bound descriptive book`, `Registered dynamic dimension` | `AgeManager`, `DescriptiveBookItem`       | Age lifecycle                                                                                  |
| `Symbol <id> failed to register logic`                                  | `AgeController`                           | a symbol threw while compiling an Age (ERROR)                                                  |


Debug commands (OP, also used by the client smoke):


| Command                                                                   | Effect                                                                                                                                                                                                                                                   |
| ------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/myst-scene`                                                             | builds the showcase in front of you: writing desk, bookstand + book, lectern + book, ink mixer, book binder, link modifier, every decay block, a crystal column, a powered crystal portal (receptacle on the front at eye height), an ink pool between the portal and the star fissure, item frames; puts you on the ground south of the pad (nothing floats) |
| `/myst-scene closeup <element>`, `/myst-scene open <element>`, `/myst-scene use <item>` | teleport to one scene element, right-click it (opens its screen), or put an item in hand and use it (`linking_book`, `descriptive_book`, `folder`, `notebook`, `current_age_book`) |
| `/myst-visit [name]`                                                      | creates a new Age and links you into it through the normal link path (spawn search, ground snap, platform); gives you the bound Descriptive Book                                                                                                         |
| `/myst-create [name]`, `/myst-agebook [dim]`, `/tpx`, `/myst-time set day | night`,` /myst-twi`,` /myst-spawnmeteor`,` /myst-dbg`                                                                                                                                                                                                    |




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

Every bug from a playtest gets a headless check before it is fixed, so it stays fixed. The table maps reports to the
check that would catch a regression; the manual checklist below no longer repeats these.


| Report                                                      | Headless check                                                                                                            |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Sun/moon racing, Ages never dark, every Age dark on arrival | `randomAgesStartAtVaryingTimes`, `ageTimeAdvances`; client smoke asserts the client sky light follows the Age's celestial angle and logs `[age]` + `[clientcheck] client Age controller ok` |
| Desk / bookstand / lectern models and textures              | ported original models; `AssetIntegrityTest` atlas checks; client smoke close-ups `02a`–`02c` (fails on `Missing textures`) |
| Portal does nothing / renders as cubes / too small to walk   | `portalBindsUnboundBook`, `portalLinksEntity`, `portalCollapsesWhenFrameBroken`; scene portal is a 2x3 walkable field (`02d`) |
| Spawn floating / buried / no platform                       | `arrivalLandsOnPlatform`, `ageDefaultSpawnIsGrounded`, `spawnSearchFindsGround`, `[spawn]` lines                          |
| Effect timers flicker                                       | `potionEffectNotReappliedEveryTick`                                                                                       |
| Stuck in ink                                                | `inkIsSwimmable`                                                                                                          |
| No star fissures / fissure washed away by water             | `starFissureGeneratesAtOrigin`, `fissureSurvivesWater`                                                                    |
| Ink vial / bucket not accepted by desk or mixer             | `deskAcceptsInkBucket`, `deskAcceptsInkVial`, `mixerAcceptsInkBucket`, `mixerAcceptsInkVial`, `inkContainersReportContents` |
| Decay blocks do nothing in Ages                             | decay blocks random-tick (`ModBlocks`); `InstabilityTests`                                                                |
| Pages without symbol icons                                  | item frames with pages in the scene, close-up `02g_closeup_pages`                                                         |
| World failed to reopen, client ERROR spam                   | client smoke creates/reopens a world and fails on any mod ERROR line                                                      |
| GUIs                                                        | client smoke opens every screen (`02z_screen_*` screenshots) and fails if one does not open                               |
| Link panel pictures                                         | client smoke checks a picture was captured on arrival and shown in the Age's book (`04b_age_book`)                        |


## Manual checklist

What a human still has to judge: look & feel on a real GPU, sound, input feel and anything that needs more than a
minute of play. Organised by game mechanic; work through it top to bottom in a **new** creative world.

Setup: build (`scripts/build.sh`), copy `out/*.jar` into the instance, start the world, run `/myst-scene` (puts every
block in front of you), then play through the mechanics below. Report one line per failed item plus the session's
`debug.log` (zip the `logs/` folder); `[spawn]`, `[link]`, `[age]`, `[panel]`, `[scene]` lines are read first.

### 1. Pages and ink (Ink Mixer)

- [ ] Fill the mixer: ink vial or Bucket of Black Ink into the **Ink in** slot; the basin shows ink, the emptied container appears in **Out**.
- [ ] Hover the basin: tooltip lists the usable ingredients ("one item each") with the effect each switches on, and the effects currently in the ink (no percentages - effects are on or off); hold an ingredient (e.g. feather, gunpowder, gold nugget, lead) over it: tooltip says what it adds, "(already in the ink)" or that it has no effect.
- [ ] Click the basin with a stack of ingredients: exactly one item is used per click; a second click with the same effect uses nothing. Black dye clears every effect. Refilling the ink after a page was made starts with no effects.
- [ ] Paper in the **Paper** slot produces a Link Panel page in the output slot; the panel page carries exactly the effects that were in the ink (hover it).
- [ ] Pour a bucket of ink into the world: it forms a pool you can swim in and climb out of.
- [ ] Right-click a still (source) ink block with an empty bucket: you get a Black Ink Bucket and the block is gone; with a glass bottle: you get an Ink Vial. Flowing (non-source) ink cannot be scooped. Both containers work in the desk and mixer afterwards.

### 2. Writing (Writing Desk)

- [ ] Put a notebook / symbol portfolio / folder in a tab: its symbols appear on the writing surface with readable glyphs; search and AZ/ALL filters work.
- [ ] Put paper, an ink container and a blank Descriptive Book (from the binder) in the slots; click symbols: pages are written into the book (paper and ink are consumed; ink well level drops).
- [ ] Hover a symbol on the surface: the tooltip ends with what a click does ("Click: write a copy…" when ink + target are present, otherwise "Click: take this page" plus what is missing).
- [ ] Shift-click a symbol takes one page out of the notebook; Ctrl+Shift-click takes the whole stack; right-click also writes a copy. With no ink or target, a plain click takes the page.
- [ ] Drafts: a page written here shows in grey ("draft - not yet permanent" in the book view; washed-out tile in a folder's strip) and **Undo last page** appears under the name field. Undo removes the page (books: back to a blank page) and refunds the paper and ink. Taking the book/folder out of the desk makes the drafts permanent (Undo disappears).
- [ ] Put a bound Descriptive Book in a notebook tab: its symbol pages appear on the surface and can be copied onto new pages (into another book / folder as target).
- [ ] Rename the book in the name field; the title shows on the item and later on lecterns.
- [ ] Empty slots show faded example items and a tooltip saying what goes there; the empty surface and target area explain themselves.

### 3. Binding (Book Binder)

- [ ] Leather in **Cover**, a title typed, pages dragged into the strip with a Link Panel first: the finished book appears in the output slot. Without a title or link panel it does not (red outline / pulsing icon explain why).
- [ ] Pages can be re-ordered / taken back out of the strip.

### 4. Linking

- [ ] Right-click an unused Descriptive Book: you arrive in a new Age standing on a 3x3 cobblestone pad with head room, never floating or buried. Repeat for 5 Ages (`/myst-visit` is a shortcut).
- [ ] After the first visit the book carries a page for every symbol of the Age (the ones you wrote first, then everything the grammar added - terrain, biomes, celestials, dangerous ones too); the page count in the book footer grew, `Bound descriptive book ... pages added` is in the log.
- [ ] Linking Book (right-click an Unlinked Book where you stand): returns you to that exact spot; the book stays behind unless it has the Following effect (Link Modifier check box, or a lead in the ink mixer), in which case it comes along.
- [ ] Open a book: the link panel cycles through four level photos taken from the arrival point looking north, east, south and west (the view turns for a few ticks right after arriving - that is the camera); unvisited Ages show the plain dark panel.
- [ ] Page through a book: each symbol page shows the glyph on the right and the symbol's name plus a one-line description of what it does on the left; hovering a page item anywhere shows the same description.
- [ ] Link Modifier: insert a book; title/seed editable; each effect is a labelled check box with a tooltip; **Mark Age dead** asks for confirmation and cannot be undone.
- [ ] Linking while riding is refused with a `[link] refused ...` line; dismount and retry works.

### 5. Books in the world

- [ ] Bookstand and Lectern: place a book by right-click; the book lies **open** (descriptive = gold-brown cover, linking = green) flat on the bookstand and along the lectern's slope with the spine running away from the reader; a page item still lies flat as its icon. Title label hovers above (if server labels are on); right-click with an empty hand links you. Models look right from all sides and for all facings; the lectern's low edge faces you when placed.
- [ ] Writing desk block: notebooks in the tabs appear as book spines standing in the backboard shelf (one per notebook, up to 7; blue = portfolio, tan = folder, brown = notebook); ink in the tank shows as an inkwell on the desk top whose fill follows the level.
- [ ] Book receptacle + crystal frame: book in the receptacle lights the portal; walking through links you; breaking a frame crystal or removing the book collapses the portal (no crumble effect: crystal is plain glass-like block).
- [ ] Star fissure (Age with the Star Fissure symbol, or the scene): the thin starry plane at bedrock level; falling onto it sends you home; water flowing onto it does not remove it.

### 6. Ages: sky, time, weather, colours

- [ ] New Ages start at different times of day (some in daylight, some at night; the `[age] … ticking: time` log line shows the start tick). Sun/moon move at a sensible speed; `/myst-time set night` darkens sky and world lighting (stars, mobs); `/myst-time set day` restores it. Time survives leaving and re-entering the Age and reopening the world.
- [ ] Sunrise/sunset tint the sky and fog smoothly, no flicker.
- [ ] Weather symbols (rain/snow/storm): precipitation and thunder in the Age; `/myst-toggledownfall` toggles it.
- [ ] Colour symbols (grass/foliage/water/sky/fog): the Age's blocks and sky take the colour.
- [ ] Terrain/biome symbols: an Age written with specific terrain, biome, feature and block pages shows them (flat, skylands, floating islands, dense ores, obelisks, spheres…).

### 7. Instability

- [ ] A very unstable Age (many conflicting pages, or `/myst-twi` on): symptoms appear over ~10 minutes: potion effects with stable HUD timers, meteors, lightning, decay spreading, crumbling blocks, scorched terrain.
- [ ] Decay blocks placed by hand inside an Age spread/decay over time; outside Ages they vanish on placement (by design).
- [ ] No "Can't keep up" spam in the server log while standing in a loaded Age.

### 8. Items and inventory

- [ ] Pages show their symbol glyph as the item icon (inventory, hand, item frame, dropped); link panel pages show the dark panel; blank pages the plain parchment.
- [ ] Books show the right cover; Bucket of Black Ink shows a dark fluid overlay; folders/portfolios show their contents when opened.
- [ ] Ink Vial icon reads as a full vial everywhere: on the dark hotbar (grey glass outline + cork visible around the ink), in inventories and in hand.
- [ ] Notebook / folder screens: page grid, search, shift-click moves pages.

### 9. Persistence and multiplayer

- [ ] Quit to title and reopen: every Age reloads, Age time continues where it was, link panel pictures are still there, books still link to the same places.
- [ ] Dedicated server / LAN (optional): the same flows work for a second player; no client errors on join while inside an Age.

### 10. Performance and feel

- [ ] No stutter when a new Age generates its first chunks around the arrival point.
- [ ] GUI scale 2 and 3 at 1920x1080: nothing cut off in the writing desk, link modifier (wide side panel) and book screens.
- [ ] Sounds: link sound on departure/arrival, portal hum, ink pour.
