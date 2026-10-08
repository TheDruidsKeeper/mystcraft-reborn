# Mystcraft Reborn

A from-scratch, open-source recreation of [Mystcraft](https://github.com/Mystcraft/Mystcraft-Legacy) for
**Minecraft 26.1 / NeoForge 26.1**: learn symbols, write Descriptive Books, link into procedurally generated Ages,
survive their instability — and, with the Vault symbol, solve a Facility for the way home.

| | |
|---|---|
| Minecraft | 26.1.x |
| Loader | NeoForge 26.1.2.104+ |
| Java | 25 |
| License | LGPL-3.0-or-later ([`LICENSE`](LICENSE), third-party notices in [`NOTICE.md`](NOTICE.md)) |

## Install

[![Install instance pack](https://img.shields.io/badge/Install-Prism%20%2F%20MultiMC-2ea44f?logo=minecraft&logoColor=white)](https://github.com/TheDruidsKeeper/mystcraft-reborn/releases/latest/download/mystcraft-reborn.mrpack)
[![One-click Prism](https://img.shields.io/badge/One--click-Prism%20Launcher-1b2838)](https://cdn.jsdelivr.net/gh/TheDruidsKeeper/mystcraft-reborn@main/docs/install.html)

**Easiest (client):** download the [instance pack](https://github.com/TheDruidsKeeper/mystcraft-reborn/releases/latest/download/mystcraft-reborn.mrpack)
(`.mrpack`) and import it in [Prism Launcher](https://prismlauncher.org/) (Add Instance → Import), or open the
[one-click install page](https://cdn.jsdelivr.net/gh/TheDruidsKeeper/mystcraft-reborn@main/docs/install.html)
if Prism is installed. MultiMC can import
[`mystcraft-reborn-multimc.zip`](https://github.com/TheDruidsKeeper/mystcraft-reborn/releases/latest/download/mystcraft-reborn-multimc.zip)
the same way. Requires **Java 25** (Prism usually installs the matching runtime).

**Jar only (client or server):** drop `mystcraft-neoforge-26.1-<version>.jar` into `mods/`. Config files appear in
`config/` (`mystcraft-common.toml`, `-balance.toml`, `-worldbuilding.toml`, `-client.toml`); the Facility rooms are a
built-in data pack that can be disabled in the world's data pack screen.

## Play
1. Find symbol pages (loot, Archivist villagers, Sealed Notebooks) and **use** them to learn their symbols.
2. At a **Writing Desk** write known symbols into a **Collation Folder** and attach modifiers; mix a **Link Panel** at
   the **Ink Mixer**; bind a **Descriptive Book** at the **Book Binder**.
3. Link. The first link completes your book: everything you left unwritten is discovered and recorded.
4. Get back with a **Linking Book**, a **Star Fissure**, or the reward of a **Facility** (Vault symbol).
5. Unstable Ages decay. Write better ones.

[`docs/GAMEPLAY.md`](docs/GAMEPLAY.md) explains every mechanic.

## Build and test
Requires Docker with BuildKit (`docker buildx`). From the repo root:

```bash
# scripts/build.sh — jar → out/
docker buildx build --progress=plain --build-arg GRADLE_TASKS=build --target export --output type=local,dest=out .

# scripts/smoke.sh — server SelfCheck; pass when out/smoke-status.txt is PASSED
docker buildx build --progress=plain --build-arg SMOKE_SECONDS=420 --target smoke-export --output type=local,dest=out .

# scripts/gametest.sh — pass when out/gametest-status.txt is PASSED
docker buildx build --progress=plain --target gametest-export --output type=local,dest=out .

# scripts/client-smoke.sh — pass when status is PASSED or VISUAL_DRIFT (drift is a warning)
docker buildx build --progress=plain --build-arg CLIENT_SMOKE_SECONDS=1200 --target client-smoke-export --output type=local,dest=out .
```

See [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) for what each layer catches; the documentation index is in [`CLAUDE.md`](CLAUDE.md).

## Credits
Original Mystcraft by XCompWiz (LGPL-3.0). Dynamic dimensions after Commoble's Infiniverse (MIT). Facility rooms
imported from Stonevaults by TheGrimsey (MIT) and others listed in [`NOTICE.md`](NOTICE.md) and the pack's `CREDITS.md`.

## Contributing
Issues and pull requests are welcome. Verify vanilla/NeoForge calls against the 26.1 sources ([`docs/API_NOTES.md`](docs/API_NOTES.md)),
keep tests green, and document behaviour by pointing at the code rather than restating it.
