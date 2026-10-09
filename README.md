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

For Prism / MultiMC instance packs and one-click install, use the
[install page](https://thedruidskeeper.github.io/mystcraft-reborn/).

**Jar only (client or server):** drop `mystcraft-neoforge-26.1-<version>.jar` into `mods/`. Config files appear in
`config/` (`mystcraft-common.toml`, `-balance.toml`, `-worldbuilding.toml`, `-client.toml`); the Facility rooms are a
built-in data pack that can be disabled in the world's data pack screen.

## Play

<a href="docs/guide/README.md"><img src="docs/guide/images/age-scene.png" alt="Player Guide" width="100%"></a>

<h3 align="center"><a href="docs/guide/README.md">📖 Read the Player Guide</a></h3>
<p align="center">An Archivist’s primer: recipes, progression, linking, instability and Facilities.</p>

Learn the symbols of The Art, write Descriptive Books, link into Ages — and always keep a way home.
Inspired by the spirit of *Myst* and classic Mystcraft; not affiliated with or endorsed by Cyan Worlds.

Short version: find pages → study them → write at a Writing Desk → mix a Link Panel → bind a Descriptive Book → link
with a Linking Book ready. Unstable Ages decay; Vault Ages hide a Facility with a reward book home.

Developers: [`docs/GAMEPLAY.md`](docs/GAMEPLAY.md) explains every mechanic with code pointers.

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
