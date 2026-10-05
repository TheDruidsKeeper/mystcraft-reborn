# Mystcraft Reborn — working notes for agents

NeoForge 26.1 / Minecraft 26.1 / Java 25 mod, id `mystcraft`, package `com.techbucketdivision.mystcraft`.
The code is the source of truth; the docs say what and why and point at where.

## Read first
| Need | Read |
|---|---|
| What the mod does, by mechanic | `docs/GAMEPLAY.md` |
| Where things live, how an Age is built | `docs/ARCHITECTURE.md` |
| Build, test, debug, commands, release | `docs/DEVELOPMENT.md` |
| What a human still has to check, and how | `docs/QA.md` |
| 26.1 API renames and gotchas, where the real sources are | `docs/API_NOTES.md` |
| Adding Facility rooms | `docs/STRUCTURES.md` |
| Work in progress | `docs/plans/*.md` |

## Development cycle
1. Fix / implement. New behaviour gets a GameTest (`src/gametest`), a SelfCheck line (`SelfCheck`, dedicated
   server) or a ClientSelfCheck step (`client/ClientSelfCheck`) — whichever layer can observe it (`docs/QA.md`).
2. Keep the `[tag]` log markers useful for bug reports (`docs/DEVELOPMENT.md` "Log markers").
3. Run the pipeline until green: `scripts/build.sh`, `scripts/smoke.sh`, `scripts/gametest.sh`,
   `scripts/client-smoke.sh` (Docker only; never platform-specific scripts). Run long ones in the background and
   poll `out/*-status.txt`.
4. Update the docs that describe the changed behaviour (never duplicate code or numbers into them).
5. Commit in logical commits. Copy `out/mystcraft-neoforge-26.1-<version>.jar` into the test instance when asked.

## Rules
* No backwards compatibility with old worlds: replace, do not layer.
* Verify every vanilla/NeoForge call against the decompiled 26.1 sources (`docs/API_NOTES.md`) — training-data
  APIs (1.20/1.21) are often wrong here.
* Client-only code stays under `client/**` and is referenced only from client entry points.
* Ask before large or irreversible design decisions; plans go to `docs/plans/` and are deleted when folded in.
