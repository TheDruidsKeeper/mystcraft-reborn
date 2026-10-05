# Gameplay

What the mod does, mechanic by mechanic, and where each rule is implemented. Numbers (costs, chances, weights)
live in the config files and the named classes, not here.

## Loop
1. **Learn symbols**: use a symbol page in hand (teaches its symbol and attached modifiers, consumes the page) or
   arrive in an Age (teaches everything written in it). Knowledge is per player, kept over death —
   `knowledge/SymbolKnowledge`, log `[knowledge]`.
2. **Write pages** at the Writing Desk from known symbols into a Collation Folder (paper + ink); attach modifiers
   to pages whose symbol takes them — `blockentity/WritingDeskBlockEntity`, `client/screen/WritingDeskScreen`.
3. **Mix a Link Panel** at the Ink Mixer: one ingredient switches on one link effect, deterministic —
   `blockentity/InkMixerBlockEntity`, table in config `inkmixer.ingredients`, log `[ink]`.
4. **Bind a Descriptive Book** at the Book Binder (link panel + pages, optional title) — `item/DescriptiveBookItem`.
5. **Link**: use the book (or a stand / lectern / receptacle portal). The first link completes the Age from the
   written pages, builds the dimension and lands the player on a platform — `linking/LinkController`, `world/AgeSpawn`,
   log `[spawn]`, `[link]`.
6. **Get home**: craft an Unlinked Book → Linking Book bound where it was used; a Star Fissure (chance per Age) or
   the Facility's reward book (Vault symbol) also lead back.
7. **Survive instability**: badly written Ages decay — `instability/*`.

## Symbols and pages
* Every symbol is an `api/symbol/AgeSymbol` in one `SymbolCategory` (terrain, biome layout, biomes, lighting,
  celestials, sky colours, world colours, weather, structures, features, effects, creatures; materials and modifiers
  attach to pages). Built-ins: `symbol/symbols/*`, `symbol/modifiers/*`, biome/block/fluid tables in `symbol/`.
* A page is `item/PageItem` with the `SymbolPage` component: symbol + attached modifier ids + `discovered` flag.
  Modifiers apply right before their symbol when the Age is compiled, so pages are self-contained.
* Which modifiers a symbol takes is derived from a dry run of its logic (`AgeSymbol.accepts/takes`); the desk and
  the binder refuse the rest. Symbol glyphs are composed from the four poem words (`client/render/SymbolGlyphs`).
* Pages reach players through loot, the Archivist villager (`villager/`) and Sealed Notebooks.

## The blueprint (how an incomplete book becomes an Age)
`age/AgeBlueprint.fill` runs at the first link, seeded by the Age seed:
* categories the author wrote anything in are never touched;
* required categories (terrain, biome layout, biomes unless the layout is Native, lighting, exactly one light-giving
  sun) are filled;
* optional categories get their configured defaults and, by chance, random extras; dangerous picks are rare;
  a Star Fissure is added by chance;
* gates: nether biomes / fortress need nether terrain, end biomes need end terrain, Single takes one biome.
Added pages are flagged *discovered*, rendered in a different ink, and the book is reorganised by category. The
weights and chances are `WorldBuildingConfig` (`mystcraft-worldbuilding.toml`). Log `[blueprint]`.

## Ages
* **Identity**: `age/AgeData` (name, seed, pages, symbols, time, spawn, instability state, per-subsystem data) in
  `AgeDataStorage`; dimension `mystcraft:age_<uuid>` created on demand (`dimension/DynamicDimensions`); dead Ages
  are recycled (`age/AgeManager`).
* **Compilation**: `age/AgeController` runs every page; symbol logic registers terrain generators, alterations,
  populators, biome controller, lighting, weather, celestials, colours, effects and creature rules through
  `api/symbol/AgeDirector`.
* **Terrain**: `world/gen/*` (normal, amplified, flat, nether, end, void) with materials from block pages;
  `world/feature/*` for lakes, obelisks, spikes, spheres, tendrils, crystal formations, floating islands, skylands,
  huge trees, caves, ravines, dense ores, star fissure; vanilla structure sets per structure symbol and the Facility
  (`world/structure/`).
* **Time and sky**: each Age has its own clock (`dimension/AgeTicker`, starts at a random time of day);
  celestials (`age/celestial/*`) take direction, phase, length and sunset-colour modifiers; the client renders them
  with `client/render/AgeSkyRenderer` and friends; lighting symbols drive sky light through `dimension/AgeEnvironment`.
* **Weather**: per Age (`age/weather/*`): off, on, cloudy (overcast, no precipitation), rain, snow, storm, cycling
  fast/slow.
* **Creatures**: passive / neutral / hostile groups rescaled by rate, cap and (hostiles) difficulty modifiers;
  Lifeless silences all — `creature/*`, log `[creatures]`.
* **Colours**: sky, night sky, fog, cloud, horizon/sunset, grass, foliage, water pages with colour or gradient
  modifiers — `client/render/tint`, `age/AgeController` colour providers.

## Linking
* `api/linking/LinkInfo` on books (data component): target dimension/UUID, spawn, flags (`LinkProperty`: intra-linking,
  intra-linking only, relative, disarm, maintain momentum, generate platform, following, …).
* Travel: `linking/LinkController.travelEntity` → listeners (`LinkListeners`: permissions, effects) → teleport; arrival
  snaps to ground and places a platform (`world/AgeSpawn`). Refusals are logged with a reason under `[link]`.
* Portals: crystal frames around a Book Receptacle holding a book (`linking/PortalUtils`, `block/LinkPortalBlock`);
  an unbound book binds on first contact. Stands and lecterns link on use and keep the bound copy.
* Star Fissure: a natural way home near the origin of some Ages (`world/feature/StarFissurePopulator`).
* Permissions per player and dimension: `linking/LinkPermissions`, `/myst permissions`.

## Instability
Score = symbol instability (from the pages) + block profile of the generated chunks (`instability/ChunkProfiler`
vs `BaselineProfiler`) + bonuses; quantised and walked through decks (basic, harsh, destructive, eating) that hand
out effects: potion effects, crumble, decay (spreading coloured decay blocks), explosions, lightning, meteors,
scorched, extra ticks — `instability/InstabilityController`, `effects/`, `decay/`. Master switch and difficulty in
`BalanceConfig` (`mystcraft-balance.toml`); `/myst instability status` prints every input.

## Structures
* **Library** (`world/structure/MystcraftLibrary`): small building with a loot chest and symbol-page lecterns, once per
  region of every Age.
* **Facility** (Vault symbol, `docs/plans/FACILITY_PLAN.md`): one jigsaw structure per Vault Age, 2–5 chunks from the
  origin, arrival 60–120 blocks from its entrance; rooms are imported pieces in the built-in datapack
  `mystcraft_facility` (`docs/STRUCTURES.md`). Puzzle locks, protection and the Linking-Book reward are in progress.
* Vanilla villages (with the Archivist's house), mineshafts, strongholds and nether fortresses per structure symbol.

## Content
Blocks: ink mixer, book binder, book receptacle, bookstand, lectern, link modifier, crystal, link portal, writing desk,
star fissure, black ink (fluid), decay variants — `registry/ModBlocks`. Items: page, descriptive / linking / unlinked
book, sealed notebook, collation folder, ink vial, writing desks (incl. Scholar's desk that knows everything),
black ink bucket — `registry/ModItems`. Entities: dropped linkbook, falling block, meteor, coloured lightning —
`registry/ModEntities`. Recipes, loot and trades: `src/main/resources/data/mystcraft`.

## Configuration
| File | Class | Covers |
|---|---|---|
| `mystcraft-common.toml` | `config/MystcraftConfig` | commands, respawning, village desk, Facility generation, link effects crafting, ink mixer table, labels, UUID checks |
| `mystcraft-balance.toml` | `config/BalanceConfig` | instability switch, difficulty, baselining |
| `mystcraft-worldbuilding.toml` | `config/WorldBuildingConfig` | blueprint fill: per-category chance/defaults/weights, biome counts, celestial modifiers, colours, materials, creatures, star fissure chance, instability budget |
| `mystcraft-client.toml` | `config/ClientConfig` | client rendering options |
