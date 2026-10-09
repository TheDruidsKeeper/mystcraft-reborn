# Development

Everything runs inside Docker ([`Dockerfile`](../Dockerfile), one stage per layer) so local and CI results match. Versions are pinned
in [`gradle.properties`](../gradle.properties); the toolchain is ModDevGradle ([`build.gradle`](../build.gradle)).

## Pipeline

| Layer | Script | What runs | Catches |
|---|---|---|---|
| Build | [`scripts/build.sh`](../scripts/build.sh) | `gradle build` (compile, unit tests incl. `AssetIntegrityTest`) → `out/*.jar` | compile errors, broken asset references |
| Server smoke | [`scripts/smoke.sh`](../scripts/smoke.sh) `[seconds]` | dedicated server with `MYSTCRAFT_SELFCHECK=1` → `SelfCheck` | registries, datapacks, Age creation and generation, Facility assembly, blueprint stress |
| Game tests | [`scripts/gametest.sh`](../scripts/gametest.sh) | GameTest server + `mystcraft_tests` mod (`src/gametest`) | behaviour with observable state (see [`docs/QA.md`](QA.md)) |
| Client smoke | [`scripts/client-smoke.sh`](../scripts/client-smoke.sh) `[seconds]` | dev client under Xvfb/Mesa driven by `ClientSelfCheck`, then [`scripts/qa/compare.py`](../scripts/qa/compare.py) | models, screens, renderers, Age sky/tints; QA worlds screenshot drift is reported as `VISUAL_DRIFT` (warning, not a hard fail) |

Outputs land in `out/`: `*-status.txt` (`PASSED`, `VISUAL_DRIFT` warning, or a failure kind), `logs/*.log`, `screenshots/`, `qa-report.txt`, the jar.
Each host script also tees the Docker build output to `logs/<layer>-docker.log` (git-ignored).

GitHub Actions ([`.github/workflows/build.yml`](../.github/workflows/build.yml)) skips push/PR runs that only touch
docs, markdown, LICENSE, `.cursor/`, `scripts/docs/`, or editor/git meta files. Tag a release on a build-relevant
commit, or use `workflow_dispatch`.

Without Docker (JDK 25): `./gradlew build | runServer | runClient | runGameTestServer | runData`.

### Docs tooling
* [`scripts/docs/gen_recipes.py`](../scripts/docs/gen_recipes.py) — renders the guide's recipe images from the recipe JSON (vanilla textures from
  the client jar in the Gradle cache); fails on a recipe without an image entry. Rerun after any recipe change.
* [`scripts/docs/crop_icons.py`](../scripts/docs/crop_icons.py) — cuts the guide's item icons out of the client smoke's chest screenshot
  (`selfcheck_02z_screen_chest.png` + the `[base] chest slot` log lines), so icons look as the items do in game. Run after
  a client smoke whenever an item's look or the showcase list (`QaBase.showcase`) changes, then `gen_recipes.py`.
* [`scripts/docs/check_links.py`](../scripts/docs/check_links.py) — dangling file / class references in the Markdown docs.

### Gradle tasks of note
* `generateStructurePools` — writes the Facility template pools + `CREDITS.md` from the room manifest
  ([`docs/STRUCTURES.md`](STRUCTURES.md)); runs before `processResources`.
* `runData` — datagen into [`src/generated/resources`](../src/generated/resources) (committed).

## Running on a real client
Copy `out/mystcraft-neoforge-26.1-<version>.jar` into the instance's `mods/`. Logs for bug reports:
`<instance>/.minecraft/logs/debug.log` (client) or `logs/debug.log` (server) — the `[tag]` lines below are enough
for most reports.

## Log markers
Decisions that matter for a bug report are logged at INFO under a bracketed tag:
`grep -E "\[(spawn|link|age|ink|blueprint|knowledge|desk|creatures|qa|panel|portal|worldgen|base|clientcheck|selfcheck)\]" debug.log`.

| Tag | Source | Meaning |
|---|---|---|
| `[spawn]` | `world/AgeSpawn`, `linking/LinkController` | arrival point search, Facility-relative spawn, ground snap, platform |
| `[link]` | `linking/LinkListeners` | every refused link and why (rate-limited) |
| `[age]` | `dimension/AgeTicker` | first tick of an Age level: time, celestials |
| `[blueprint]` | `age/AgeBlueprint`, `age/AgeController` | first-link fill: written/discovered pages, dropped picks, instability |
| `[knowledge]` | `knowledge/SymbolKnowledge` | symbols learned from pages or Ages |
| `[desk]` | `blockentity/WritingDeskBlockEntity` | pages written, modifiers attached, drafts (DEBUG) |
| `[ink]` | `linking/InkEffects`, `blockentity/InkMixerBlockEntity` | resolved ingredient table, every mix |
| `[creatures]` | `creature/*` | spawns refused by caps, extra spawn passes, difficulty applied |
| `[worldgen]` | populators | star fissure position |
| `[panel]` | `client/PanelImages` | link panel photographs |
| `[portal]` | `linking/PortalUtils` | crystal portal formation / collapse |
| `[qa]` | `command/QaWorlds` | one line per QA-world lectern: id, seed, Age, what to look for |
| `[base]` | `command/QaBase` | where the QA base was built, supply chest contents |
| `[archivist]` | `command/MystcraftCommands` | Archivist spawned by command |
| `[instability]` | `instability/InstabilityDeaths` | a player killed by instability (advancement) |
| `[selfcheck]`, `SELFCHECK PASSED/FAILED` | `SelfCheck` | server smoke |
| `[clientcheck]`, `CLIENT SELFCHECK PASSED/FAILED` | `client/ClientSelfCheck` | client smoke |

## Commands
Registered in `command/MystcraftCommands`; every dimension argument defaults to the Age you stand in.

`/myst …` (permission level 2):

| Subcommand | Effect |
|---|---|
| `visit [name]` | create an Age (optionally titled) and link in through the normal link path |
| `create [name]` | create an Age without travelling |
| `book [dim]` | receive the Descriptive Book of an Age |
| `locate facility` | chunk and coordinates of this Age's Facility entrance (`world/structure/FacilityLocator`) |
| `facility status` / `facility solve` | whether this Age's Facility is solved (protection lifted); `solve` marks it solved without the run-through (`facility/FacilityState`) |
| `time set <day\|night\|ticks> [dim\|all]`, `time add <ticks> [dim\|all]` | the Age's own clock (vanilla `/time` does not touch Ages) |
| `weather toggle [dim]` | toggle precipitation of the Age's weather controller |
| `instability status\|toggle [on/off]\|reprofile\|meteor [scale [penetration [pos]]]` | inspect or change instability (`meteor` needs config `commands.spawnmeteor.enabled`) |
| `permissions <player> <restrict\|permit> <entry\|depart> <all\|dim>` | per-player link permissions (`linking/LinkPermissions`) |
| `retire [dim]` | mark an Age dead (links refused, data recyclable) |

`/myst-dev …` (permission level 4):

| Subcommand | Effect |
|---|---|
| `qa-base`, `qa-base closeup <element>`, `qa-base open <element>`, `qa-base use <item>`, `qa-base view` | the QA base north of you, paved path to its gate (`command/QaBase`: every workstation in workflow order, portal, fissure, decay, a stocked supply chest, fenced and lit); `view` returns to the viewing spot; driven by the client smoke |
| `qa-worlds` | build the visual QA matrix in front of you, paved path to its gate (`command/QaWorlds`, [`docs/QA.md`](QA.md)) |
| `qa-all` | `qa-base` (left), `qa-worlds` (right) and the Archivist off one trunk path north of you |
| `qa-visit <id>` | bind one QA world and link into it (the client smoke tour uses this) |
| `archivist` | spawn an employed Archivist villager a few blocks in front of you (`villager/ArchivistShop` trades) |
| `home-book` | a Linking Book to the overworld spawn with intra-linking + following (a party's way home) |
| `facility-tp entrance|lobby|vault` | teleport into this Age's generated Facility (`world/structure/FacilityLocator.find`; the tour shoots E2's entrance and lobby) |

## Writing tests
* **GameTest** — a static method in a `@ForEachTest(groups=…)` class under `src/gametest`, annotated `@GameTest`,
  `@EmptyTemplate`, `@TestHolder(description=…)`. Bind Ages with `TestBooks` or `QaWorlds.bind`, generate chunks with
  `level.getChunk`, finish with `helper.succeed()`. The GameTest server runs with `generateStructures=false`: call
  the chunk generator's createStructures yourself (see `FacilityTests`). Level.getHeight never generates — load the
  chunk first.
* **SelfCheck** (`SelfCheck.java`) — add a `check(...)` for anything that needs a real dedicated server (structure
  generation, registries, timing).
* **ClientSelfCheck** — add a `Step`; screenshots are `selfcheck_<name>.png`; add `failures` entries for assertions.
  Visual checks of QA worlds go through the tour and [`scripts/qa/compare.py`](../scripts/qa/compare.py) ([`docs/QA.md`](QA.md)), not new steps.

## API lookup
Decompiled 26.1 sources and the NeoForge sources sit in the Gradle cache ([`docs/API_NOTES.md`](API_NOTES.md) has the paths and the
known renames). Check a signature there before using it; the smoke build is the next line of defence.

## Release checklist
1. Pipeline green, [`scripts/qa/baselines.json`](../scripts/qa/baselines.json) reviewed/updated, manual checklist in [`docs/QA.md`](QA.md) walked once.
2. `mod_version` in [`gradle.properties`](../gradle.properties); [`NOTICE.md`](../NOTICE.md) lists every imported asset pack ([`docs/STRUCTURES.md`](STRUCTURES.md)).
3. [`README.md`](../README.md) matches the shipped behaviour (jar-only install notes; launcher packs live on the
   [install page](https://thedruidskeeper.github.io/mystcraft-reborn/)); [`docs/plans/`](plans/) holds only open work.
4. Tag `v*`: [`.github/workflows/tag.yml`](../.github/workflows/tag.yml) always runs the build (not subject to
   `paths-ignore`) and [`scripts/pack-instance.sh`](../scripts/pack-instance.sh) adds
   `mystcraft-reborn.mrpack` + `mystcraft-reborn-multimc.zip` to the GitHub Release (stable names for the
   install page `/releases/latest/download/…` links). Locally: `scripts/build.sh` then `scripts/pack-instance.sh`.
5. Install page is deployed from [`docs/install.html`](install.html) to GitHub Pages
   (`https://thedruidskeeper.github.io/mystcraft-reborn/`) via [`.github/workflows/pages.yml`](../.github/workflows/pages.yml).
   First-time setup: Settings → Pages → Source = **GitHub Actions** (the workflow also sets
   `enablement: true` on `configure-pages` so a fresh repo can self-enable).
6. Once per major toolchain bump, import the `.mrpack` in Prism and confirm NeoForge + Java 25 launch.
