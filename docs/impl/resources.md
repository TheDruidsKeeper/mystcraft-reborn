# Package F — resources & data (hand-authored JSON under `src/main/resources`)

No Java datagen; everything below is committed JSON in NeoForge 26.1 layout (`recipe/`, `loot_table/`, `advancement/`,
`tags/block|item|timeline|villager_trade/`, client item definitions in `assets/mystcraft/items/`).

## assets/mystcraft
| Path | Contents |
|---|---|
| `lang/en_us.json` | ~350 keys: `item.mystcraft.*` (incl. `page.blank/link_panel/symbol`), `block.mystcraft.*`, `fluid_type.mystcraft.black_ink`, `itemGroup.mystcraft.main/pages`, `entity.mystcraft.*`, `entity.minecraft.villager.mystcraft.archivist`, `container.mystcraft.*`, `link_property.mystcraft.*` (all 12), `symbol.mystcraft.<id>` for every builtin/modifier symbol (names from `docs/impl/symbols.md`) + `symbol.mystcraft.block.wrapper` / `biome.wrapper`, `instability.mystcraft.bonus.*`, `commands.mystcraft.*` (every key in `instability_entities_events.md` + legacy extras), `gui.mystcraft.*` (writing_desk/book_binder/ink_mixer/link_modifier/book/folder/shop/profiling/startup/creative.notebook.* — provisional names for package D), `tooltip.mystcraft.*`, `subtitles.mystcraft.*`, `advancements.mystcraft.<name>.title/description`, `key.mystcraft.*`, `death.attack.mystcraft.*`, `mystcraft.configuration.<path>` for every config path. The 1456 banner keys were skipped. Legacy `*.lang` files are left in place (ignored by the game). |
| `blockstates/*.json` | `ink_mixer`, `book_binder`, `link_modifier`, `lectern` (facing ×4), `book_receptacle` (rotation ×6: south=0°, west=y90, north=y180, east=y270, up=x90, down=x270 — matches the VoxelShapes in `BookReceptacleBlock`), `bookstand` (rotation 0..7: even → `bookstand`, odd → `bookstand_45` (elements rotated 45°), y = 90·(r/2)), `writing_desk` (facing×top×foot → `writing_desk` / `writing_desk_top`; block is `RenderShape.INVISIBLE`, the BER draws it), `crystal`, `link_portal` (single `link_portal_full` for every `source`/`active` state), `star_fissure` (invisible), `black_ink`, `decay_<7>`. |
| `models/block/*.json` | `ink_mixer`, `book_binder` (`cube_bottom_top` with the `inkmixer_*`/`bookbinder_*` textures), `link_modifier` (`cube`, side1 N/S, side2 E/W), `crystal` / `decay_*` (`cube_all`), `book_receptacle` (ported slab element, crystal + `book_receptacle` face), `bookstand` / `bookstand_45` / `lectern` (simple cuboids textured with `entity/bookstand.png` / `entity/lectern.png` — UVs are arbitrary), `link_portal_full` (ported, tintindex 0), `star_fissure` (particle only), `writing_desk` / `writing_desk_top` (oak-plank placeholder cuboids, particle only), `black_ink` (`particle` = `block/fluid`). |
| `models/item/*.json` + `items/*.json` | Flat `item/generated` models: page (`page_background`), descriptive_book (`agebook`), linking_book (`linkingbook`), unlinked_book (`unlinked`), sealed_notebook (`booster`), collation_folder (`folder`), symbol_portfolio (`portfolio`), ink_vial (`ink_vial`, animated), writing_desk (`writingdesk`), writing_desk_backboard (`deskext`), star_fissure (`block/portal`), **black_ink_bucket = PLACEHOLDER `minecraft:item/bucket`** (needs a real `item/black_ink_bucket.png` or a tinted overlay). Block items point at the block models (`link_portal` → `link_portal_full`, `decay_*` → `block/decay_*`). Every item has a `items/<name>.json` `{"model":{"type":"minecraft:model",...}}` definition. |
| `sounds.json` | `linking.pop`, `linking.link`, `linking.link_disarm`, `linking.link_following`, `linking.link_intra`, `linking.link_fissure`, `linking.link_portal` (category player), `entity.meteor.roar` (hostile, stream, attenuation 192) — file names match `sounds/**`, subtitles `subtitles.mystcraft.<id>`. |
| `particles/link.json` | textures `minecraft:generic_0..7`. |

## data/mystcraft
| Path | Contents |
|---|---|
| `dimension_type/age.json` | overworld copy (min_y −64, height/logical_height 384, skylight, no ceiling, `skybox: overworld`, overworld attributes) + `neoforge:custom_skybox/custom_clouds/custom_weather_effects = mystcraft:age`, `default_clock: mystcraft:age`, `timelines: #mystcraft:in_age`. |
| `world_clock/age.json` | `{}`. |
| `tags/timeline/in_age.json` | empty values (no timeline → the mod drives sun/moon/sky itself). Tag folder assumed `tags/timeline/` (registry `minecraft:timeline`). |
| `recipe/*.json` | §2.9: book_binder, bookstand, collation_folder, ink_mixer, ink_vial (2× black dye + potion), ink_vial_bucket (2× black dye + glass bottle + water bucket), lectern ×2, symbol_portfolio, book_receptacle, writing_desk (`v f`/`ppp`/`p p` as in the original JSON), writing_desk_backboard; `linking_book.json` = `{"type":"mystcraft:linking_book"}`. Planks use `#minecraft:planks`; ingredients are plain id strings (1.21.2+ format). |
| `loot_table/blocks/*.json` | drop-self (survives_explosion) for ink_mixer, book_binder, book_receptacle, bookstand, lectern, link_modifier, crystal. |
| `loot_table/chests/library.json` | §11.2: rolls 4–8, bonus 1–2; ink vial 50, sealed notebook 1000, leather 1–3 (50), paper 1–6 (50). Symbol-page entries are injected by Java (`MystcraftLibrary`), not JSON. |
| `tags/block/mineable/{pickaxe,axe,shovel}.json`, `tags/block/decay.json`, `tags/item/{pages,books}.json` | tool tags + helper tags. |
| `villager_trade/archivist/*.json` | `booster` (25 emeralds → sealed notebook, 1 use), `link_panel` (4 emeralds → page with `mystcraft:link_panel: []`, 1 use), `blank_page` (1 emerald → page, placeholder for the Java symbol shop), `paper_for_emerald` (24 paper → emerald), `leather_for_emerald` (4 leather → emerald). |
| `tags/villager_trade/archivist/level_1..5.json` + `trade_set/archivist/level_1..5.json` | sets referenced by `ModVillagers` (`#mystcraft:archivist/level_n`, amount 1–2, no duplicates, random_sequence `mystcraft:archivist/level_n`). Real symbol trades come from `villager.ArchivistShop`. |
| `advancement/*.json` | §19.5: root (icon descriptive_book, background `minecraft:gui/advancements/backgrounds/stone` — 1.21.4+ sprite id, UNVERIFIED for 26.1), symbol (page in inventory), write (`mystcraft:writing_desk_write`), agebook, linkbook (unlinked book), dimension (`enter_myst_dimension_safe`, goal), quinn (`enter_myst_dimension_quinn`, hidden challenge). Icons use `{"id": ...}`; inventory_changed uses `"items": [{"items": "<id>"}]`. |

## Placeholders / follow-ups
* `black_ink_bucket` item texture (vanilla bucket).
* Bookstand / lectern / writing desk block models are simple cuboids (the original used OBJ models + BERs); the BERs in package D render the real geometry.
* `gui.mystcraft.*` / `tooltip.mystcraft.*` / `key.mystcraft.*` keys are proposals for package D — rename there if needed.
* Timeline tag is empty; a `data/mystcraft/timeline/*.json` on clock `mystcraft:age` can be added later if vanilla-driven sun/moon tracks are wanted.
* No `pack.mcmeta` (mod jars do not need one).
