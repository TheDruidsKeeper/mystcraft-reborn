# Mystcraft Reborn

A from-scratch, open-source recreation of [Mystcraft](https://github.com/Mystcraft/Mystcraft-Legacy) for
**Minecraft 26.1 / NeoForge 26.1.2**. Write Descriptive Books from symbol pages, link into procedurally generated
Ages, and survive their instability.

| | |
|---|---|
| Minecraft | 26.1 (26.1.x) |
| Loader | NeoForge 26.1.2.104+ |
| Java | 25 |
| License | LGPL-3.0-or-later (see `LICENSE`, `NOTICE.md`) |

## Building

Builds are reproducible inside Docker; CI uses the same `Dockerfile`.

```powershell
# Windows
.\scripts\build.ps1            # -> out\mystcraft-neoforge-26.1-<version>.jar
.\scripts\build-log.ps1        # same, plus build-docker.log for sharing
```

```bash
# Linux / macOS
scripts/build.sh
```

Without Docker (JDK 25 required): `./gradlew build`, `./gradlew runClient`, `./gradlew runServer`,
`./gradlew runData` (regenerates `src/generated/resources`), `./gradlew runGameTestServer`.

### Smoke test

Boots the NeoForge dedicated server with the mod installed and fails if it does not finish loading —
this covers mod construction, every registry, the access transformer, datapack parsing and the
server-start hooks. CI runs the same target on every push.

```powershell
.\scripts\smoke.ps1          # -> out\smoke.log, smoke-docker.log
```

```bash
scripts/smoke.sh
```

### In-game tests and client smoke

```bash
scripts/gametest.sh        # GameTest server + src/gametest (behaviour: linking, portals, spawn, fluids, instability)
scripts/client-smoke.sh    # dev client under Xvfb + Mesa, scripted by ClientSelfCheck, exports screenshots to out/screenshots
```

See `docs/TESTING.md` for the layers, log markers (`[spawn]`, `[link]`, ...) and the manual checklist.

Client rendering on a real GPU (look & feel) is covered by the manual checklist; run `./gradlew runClient`
locally for that.

## Repository layout

```
docs/                 ARCHITECTURE.md (design), REQUIREMENTS.md (original behaviour, with numbers),
                      TOOLCHAIN.md (versions), API_CHEATSHEET.md (verified 26.1 signatures)
src/main/java         mod sources (com.techbucketdivision.mystcraft)
src/main/resources    assets (textures/sounds reused from the original under LGPL), data packs
src/main/templates    neoforge.mods.toml (expanded by Gradle)
src/generated         datagen output (committed)
scripts/              docker build helpers
```

## Gameplay summary

1. Find symbol pages (loot, Archivist villagers, Sealed Notebooks).
2. Copy symbols in a **Writing Desk** (ink + paper), mix a **Link Panel** in the **Ink Mixer**, bind a book in the
   **Book Binder**.
3. Use the **Descriptive Book** to link into a new Age; craft an **Unlinked Book** into a **Linking Book** to get back.
4. Manage **instability**; **Crystal** portals with **Book Receptacles** and **Star Fissures** connect Ages.

See `docs/REQUIREMENTS.md` for the complete mechanics reference and `docs/ARCHITECTURE.md` for the milestone plan.

## Contributing

Issues and pull requests are welcome. Please keep new vanilla/NeoForge API usage consistent with
`docs/API_CHEATSHEET.md` (26.1 renamed or removed a large part of the 1.21 API).
