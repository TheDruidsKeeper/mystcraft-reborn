# Testing

Everything that can be verified without a human runs headless in Docker (same `Dockerfile` locally and in CI, no
platform-specific scripts). The manual checklist at the end is only for what a renderer on a real GPU and a human eye
can judge. Each layer is cheap to run on its own; run them in order when something is unclear.


| Layer          | Command                                | Runs                                                      | Covers                                                                                                    | Time     |
| -------------- | -------------------------------------- | --------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- | -------- |
| 1 Build + unit | `scripts/build.sh` (`--target export`) | `gradle build`: compile, `AssetIntegrityTest`             | API compatibility; every blockstate/model/texture/atlas/sound/lang reference                              | ~40 s    |
| 2 Server smoke | `scripts/smoke.sh`                     | dedicated server + `SelfCheck` (`MYSTCRAFT_SELFCHECK=1`)  | registries, datapacks, AT, symbols, Age blueprint (200-seed stress), dimension type, Age creation + chunk generation | ~2 min   |
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
| `out/client-smoke.log`, `out/client-smoke-status.txt`, `out/screenshots/selfcheck_*.png` | client log, status, screenshots (`01_overworld`, `02_scene_overworld`, `02a`–`02g` close-ups, `02z_screen_*` every GUI, `03_age_arrival`, `04_scene_age`, `04b_age_book`, `04c_age_book_page` (first symbol page: category label, glyph), `04d_age_book_summary` (summary page), `05_age_night`) |




## Log markers

The mod logs the decisions that matter for bug reports at INFO under bracketed tags, so a report only needs the
`debug.log` (client: `<instance>/.minecraft/logs/debug.log`; server: `logs/debug.log`). `grep -E "\[(spawn|link|age|ink|blueprint|knowledge|desk|qa|panel|portal|worldgen|scene|clientcheck|selfcheck)\]"` is the first thing to run on any log.


| Tag                                                                     | Where                                     | Meaning                                                                                        |
| ----------------------------------------------------------------------- | ----------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `[spawn]`                                                               | `AgeSpawn`, `LinkController.defaultSpawn` | spawn determination (biome hit / fissure / none), ground snap, arrival platform counts         |
| `[link] refused <entity> -> <dim>: <reason>`                            | `LinkListeners.isLinkPermitted`           | every refused link with its reason, rate-limited per entity + reason (portals retry each tick) |
| `[worldgen]`                                                            | populators                                | star fissure position per Age                                                                  |
| `[ink]`                                                                 | `InkEffects`, `InkMixerBlockEntity`       | resolved ingredient table (with rejected config entries) and every mix into a basin           |
| `[knowledge]`                                                           | `SymbolKnowledge`, client handler         | every symbol a player learns (from a page or an Age) and the client's synced count                |
| `[desk]`                                                                | `WritingDeskBlockEntity`                  | pages written / modifiers attached, drafts committed or undone (DEBUG level)                     |
| `[qa]`                                                                  | `/myst-qa-shelf`                          | one line per lectern: case, seed, Age, what to look for                                          |
| `[blueprint]`                                                           | `AgeBlueprint`, `AgeController`           | every first-link fill (seed, written/discovered pages, instability), picks dropped for the budget, rejected config entries, controller fallbacks (should never fire) |
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
| `/myst-qa-shelf`                                                          | builds a row of 14 lecterns in front of you, each with a Descriptive Book bound to a fixed-seed Age that shows one thing only eyes can judge (sky gradient, world colours, celestial modifiers, storm, obsidian flatland, skylands, nether / end Ages, tiny biomes, instability, void + fissure, amplified lava lakes); `[qa]` log lines say what to look for per lectern |
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
| Ink ingredient table (one ingredient per effect)            | `inkIngredientTableIsOneToOne`, `mixerOneIngredientPerEffect`; `[ink]` log lines                                          |
| Symbol categories / modifier slots wrong                     | `everySymbolHasACategory`, `builtinSchemaIsAsDesigned`                                                                     |
| Page modifiers / discovered flag lost                        | `symbolPageRoundTripAndFlattening`, `organiserKeepsPagesWhole`; close-up `02g_closeup_pages` (overlay page, discovered page) |
| First link: missing terrain/sun, void terrain, unstable Age  | `fillIsDeterministicAndSafe` (200 seeds), `biomeGates`, `playerCategoriesAreUntouched`, `bindingWritesGeneratedSymbolsIntoBook`; `SelfCheck` blueprint section |
| Desk writes unknown symbols / books as target / wrong modifier | `deskWritesKnownSymbolsIntoFolder`, `deskAttachesModifiers`, `deskDraftsUndoAndCommit`; client smoke checks the Scholar's desk lists > 50 symbols (`02z_screen_desk`, `02y_desk_tab_*`) |
| Pages / Ages do not teach symbols                            | `studyingAPageTeachesItsSymbols`, `arrivingInAnAgeTeachesItsSymbols`; client smoke fails when no symbols are known after arriving in the Age |
| QA shelf case uses a wrong id / modifier                     | `qaShelfCasesResolve`                                                                                                     |
| Book in a lectern / receptacle stays unbound, no symbols added | `displayLinkKeepsBoundBook`, `portalBindsUnboundBook` (asserts discovered pages in the receptacle); `/myst-scene`, `/myst-create`, `/myst-visit`, the QA shelf all bind through `DescriptiveBookItem.createBound` |
| Book not organised / no summary page                         | client smoke pages the Age book to page 1 (terrain) and to the summary (`04c`, `04d`), fails without a synced summary |
| Decay blocks do nothing in Ages                             | decay blocks random-tick (`ModBlocks`); `InstabilityTests`                                                                |
| Pages without symbol icons                                  | item frames with pages in the scene, close-up `02g_closeup_pages`                                                         |
| World failed to reopen, client ERROR spam                   | client smoke creates/reopens a world and fails on any mod ERROR line                                                      |
| GUIs                                                        | client smoke opens every screen (`02z_screen_*` screenshots) and fails if one does not open                               |
| Link panel pictures                                         | client smoke checks a picture was captured on arrival and shown in the Age's book (`04b_age_book`)                        |


## Manual checklist

What a human still has to judge: look & feel on a real GPU, sound, input feel and anything that needs more than a
minute of play. Organised by game mechanic; work through it top to bottom in a **new** creative world.

Setup: build (`scripts/build.sh`), copy `out/*.jar` into the instance, start the world, run `/myst-scene` (puts every
block in front of you; it clears its volume without dropping grass, flowers or seeds), then play through the mechanics below. Report one line per failed item plus the session's
`debug.log` (zip the `logs/` folder); `[spawn]`, `[link]`, `[age]`, `[panel]`, `[scene]` lines are read first.

### 1. Pages and ink (Ink Mixer)

- [ ] Fill the mixer: ink vial or Bucket of Black Ink into the **Ink in** slot; the basin shows ink, the emptied container appears in **Out**.
- [ ] Hover the basin: tooltip lists the eight ingredients ("one item each": clay ball, feather, gunpowder, compass, ender pearl, amethyst shard, eye of ender; black dye clears) with the one effect each switches on, and the effects currently in the ink (no percentages - effects are on or off); hold an ingredient over it: tooltip says what it adds, "(already in the ink)" or that it has no effect (a lead or gold nugget has none).
- [ ] Click the basin with a stack of ingredients: exactly one item is used per click; a second click with the same effect uses nothing. Black dye clears every effect (and is not used up on plain ink). Refilling the ink after a page was made starts with no effects. The basin colour shows equal bands per effect.
- [ ] Paper in the **Paper** slot produces a Link Panel page in the output slot; the panel page carries exactly the effects that were in the ink (hover it).
- [ ] Pour a bucket of ink into the world: it forms a pool you can swim in and climb out of.
- [ ] Right-click a still (source) ink block with an empty bucket: you get a Black Ink Bucket and the block is gone; with a glass bottle: you get an Ink Vial. Flowing (non-source) ink cannot be scooped. Both containers work in the desk and mixer afterwards.

### 2. Learning and writing (pages, Writing Desk)

- [ ] A new player knows nothing: the desk surface shows the "You know no symbols yet" hint on every tab. Right-click a symbol page in hand: "Learned: …" in the action bar, a page-turn sound, the page is consumed and the symbol appears on the surface (its tab and the All tab); a page you already know says so and is kept. A page with attached modifiers teaches the modifiers too.
- [ ] Visit an Age: the action bar reports the symbols learned on arrival (`[knowledge]` in the log); afterwards every page of that Age's book is a known symbol on the desk.
- [ ] Desk target: only a Collation Folder goes into the target slot (books, pages and other items are refused); paper and ink as before. Search box on top, nine tabs below it (All, Ter, Bio, Sky, Wea, Fea, Mat, Eff, Mod - hover for the full name); symbols are grouped under category headers, sorted by name; search filters by name.
- [ ] Hover a symbol: name, category, description, "Takes: …" (primaries) or "Attaches to a page that takes: …" (modifiers), then what a click does. Click a primary symbol: a page appears in the folder strip (paper and ink consumed, washed out as a draft). Clicking a modifier with no page selected does nothing (tooltip says to select a page first).
- [ ] Click a strip page: gold frame; on the Modifiers / Materials tabs the modifiers that fit glow and the rest dim. Click a fitting modifier: it attaches (ink only; the page icon gets a corner overlay; the tooltip lists it). Shift + right-click the page: the last modifier comes off.
- [ ] Right-click a draft page in the strip: it is erased and its paper + ink come back (no undo button any more). Right-click a permanent page: it goes to your cursor. Taking the folder out makes the drafts permanent.
- [ ] Rename the folder in the name field; the title shows on the item.
- [ ] Scholar's Writing Desk (creative tab, no recipe): every symbol on the surface for any player, full shelves on the backboard, caption "Scholar's desk" under the window; breaking it drops the Scholar's desk again. A plain desk has empty shelves.
- [ ] Empty slots show faded example items and a tooltip saying what goes there; the empty surface and target area explain themselves.
- [ ] Bind the folder's pages at the Book Binder (drag them into the strip) and visit the Age: attached modifiers take effect (a sun with East direction rises in the west; a red sky colour is red).

### 3. Binding (Book Binder)

- [ ] Leather in **Cover**, a title typed, pages dragged into the strip with a Link Panel first: the finished book appears in the output slot. Without a title or link panel it does not (red outline / pulsing icon explain why).
- [ ] Pages can be re-ordered / taken back out of the strip.

### 4. Linking

- [ ] Right-click an unused Descriptive Book: you arrive in a new Age standing on a 3x3 cobblestone pad with head room, never floating or buried. Repeat for 5 Ages (`/myst-visit` is a shortcut).
- [ ] After the first visit the book carries a page for every symbol of the Age: your pages plus the discovered ones (teal ink, "discovered at the first link" under the glyph, "Discovered in the Age" in the tooltip), all sorted by category (Terrain, Biomes, Biome Layout, Lighting, Celestials, Sky Colors, World Colors, Weather, Structures, Features, Effects) with your pages first inside a category. `Bound descriptive book ... discovered` and a `[blueprint]` line are in the log.
- [ ] Pages with attached modifiers (e.g. a sun with a direction, a coloured sky): the modifier glyphs sit on the corners of the page icon (colour modifiers in their colour) in inventories, item frames and the book; the left page lists them under "With:".
- [ ] Page past the last page of a bound book: the **Age summary** over both pages - left: seed, instability base/symbols, current score while you stand in the Age, "N of M pages discovered", authors; right: the active instability effects ("unknown until the Age is loaded" outside it). Nothing is cut off at GUI scale 2-4.
- [ ] An empty book (link panel only) still gives a sane Age: terrain, a sun, biomes, lighting; never a void Age; the Age is stable (no instability effects within 10 minutes).
- [ ] Linking Book (right-click an Unlinked Book where you stand): returns you to that exact spot; the book stays behind unless it has the Following effect (Link Modifier check box, or an eye of ender in the ink mixer), in which case it comes along.
- [ ] Open a book: the link panel cycles through four level photos taken from the arrival point looking north, east, south and west (the view turns for a few ticks right after arriving - that is the camera); unvisited Ages show the plain dark panel.
- [ ] Page through a book: each symbol page shows the glyph on the right and the symbol's name plus a one-line description of what it does on the left; hovering a page item anywhere shows the same description.
- [ ] Link Modifier: insert a book; title/seed editable; each effect is a labelled check box with a tooltip; **Mark Age dead** asks for confirmation and cannot be undone.
- [ ] Linking while riding is refused with a `[link] refused ...` line; dismount and retry works.

### 5. Books in the world

- [ ] Bookstand and Lectern: place a book by right-click; the book lies **open** (descriptive = gold-brown cover, linking = green) flat on the bookstand and along the lectern's slope with the spine running away from the reader; a page item still lies flat as its icon. Title label hovers above (if server labels are on); right-click with an empty hand links you. Models look right from all sides and for all facings; the lectern's low edge faces you when placed.
- [ ] Writing desk block: ink in the tank shows as an inkwell on the desk top whose fill follows the level; a Scholar's desk has full shelves, a plain desk empty ones.
- [ ] Book receptacle + crystal frame: book in the receptacle lights the portal; walking through links you; breaking a frame crystal or removing the book collapses the portal (no crumble effect: crystal is plain glass-like block).
- [ ] Star fissure (Age with the Star Fissure symbol, or the scene): the thin starry plane at bedrock level; falling onto it sends you home; water flowing onto it does not remove it.

### 6. Ages: sky, time, weather, colours

- [ ] New Ages start at different times of day (some in daylight, some at night; the `[age] … ticking: time` log line shows the start tick). Sun/moon move at a sensible speed; `/myst-time set night` darkens sky and world lighting (stars, mobs); `/myst-time set day` restores it. Time survives leaving and re-entering the Age and reopening the world.
- [ ] Sunrise/sunset tint the sky and fog smoothly, no flicker.
- [ ] Weather symbols (rain/snow/storm): precipitation and thunder in the Age; `/myst-toggledownfall` toggles it.
- [ ] Colour symbols (grass/foliage/water/sky/fog): the Age's blocks and sky take the colour.
- [ ] Terrain/biome symbols: an Age written with specific terrain, biome, feature and block pages shows them (flat, skylands, floating islands, dense ores, obelisks, spheres…). A category you wrote is never changed by the first link (write only a dark lighting page: it stays the only lighting page; write a moon and no sun: no sun is added).
- [ ] Config `mystcraft-worldbuilding.toml`: set `fill.terrain.weights = ["terrain_flat=1"]` and bind an empty book: the Age is flat; set `fill.starFissureChance = 100`: every new Age has a star fissure.

### 6b. QA shelf (`/myst-qa-shelf`)

- [ ] Run `/myst-qa-shelf` in a creative world: 14 lecterns appear in front of you, each titled "QA NN …". Right-click each lectern's book (empty hand) and compare the Age with the `[qa]` line in the log ("look for …"): dark sun + bright light, sky gradient / fog / night sky, world colours, celestial modifiers (sun from the west, half day, green sunset), storm, obsidian flatland without sea, skylands + ice islands, nether Age with fortress and lava sea, end Age, tiny biomes, unstable Age, void + star fissure, amplified lava lakes.
- [ ] The same shelf built again (new world, same command) gives the same Ages (fixed seeds); the book of each shelf Age is organised by category and ends with the summary page.

### 7. Instability

- [ ] A very unstable Age (many conflicting pages, or `/myst-twi` on): symptoms appear over ~10 minutes: potion effects with stable HUD timers, meteors, lightning, decay spreading, crumbling blocks, scorched terrain.
- [ ] Decay blocks placed by hand inside an Age spread/decay over time; outside Ages they vanish on placement (by design).
- [ ] No "Can't keep up" spam in the server log while standing in a loaded Age.

### 8. Items and inventory

- [ ] Pages show their symbol glyph as the item icon (inventory, hand, item frame, dropped); link panel pages show the dark panel; blank pages the plain parchment.
- [ ] Books show the right cover; Bucket of Black Ink shows a dark fluid overlay; folders show their contents when opened.
- [ ] Ink Vial icon reads as a full vial everywhere: on the dark hotbar (grey glass outline + cork visible around the ink), in inventories and in hand.
- [ ] Folder screen: page grid, search, shift-click moves pages.

### 9. Persistence and multiplayer

- [ ] Quit to title and reopen: every Age reloads, Age time continues where it was, link panel pictures are still there, books still link to the same places.
- [ ] Dedicated server / LAN (optional): the same flows work for a second player; no client errors on join while inside an Age.

### 10. Performance and feel

- [ ] No stutter when a new Age generates its first chunks around the arrival point.
- [ ] GUI scale 2 and 3 at 1920x1080: nothing cut off in the writing desk, link modifier (wide side panel) and book screens.
- [ ] Sounds: link sound on departure/arrival, portal hum, ink pour.
