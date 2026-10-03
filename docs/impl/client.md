# Package D — client (`client/**`)

Root package `com.techbucketdivision.mystcraft.client`. Entry point: `ClientSetup.register(IEventBus modBus)` (called by
`MystcraftClient`). Everything here may import `net.minecraft.client.*`; nothing outside `client/` references these
classes.

## Wiring

| Class | Purpose |
|---|---|
| `ClientSetup` | `register(modBus)`: installs `ClientNetwork.INSTANCE` as the menu `ClientSender`; mod-bus listeners `RegisterMenuScreensEvent` (7 screens), `EntityRenderersEvent.RegisterRenderers` (4 BERs, 4 entity renderers), `RegisterClientPayloadHandlersEvent` (→ `ClientPayloadHandlers.register`), `RegisterCustomEnvironmentEffectRendererEvent` (`mystcraft:age` skybox / clouds / weather), `RegisterParticleProvidersEvent` (`mystcraft:link` sprite set), `RegisterColorHandlersEvent.BlockTintSources` (portal tint, ink tint, Age biome-tint wrappers), `RegisterFluidModelsEvent` (`FluidModel.Unbaked(block/fluid, block/fluid_flow, null, InkTintSource)` for both ink fluids); then `ClientGameEvents.register(NeoForge.EVENT_BUS)`. Constant `AGE_ENVIRONMENT = mystcraft:age`. |
| `AgeClientEnvironment` | `LevelEvent.Load` for Age client levels: rebuilds `ClientLevel.environmentAttributes` (opened by the AT) = default layers + `AgeDayCurves` time layers for `SKY_LIGHT_LEVEL`, `SKY_LIGHT_FACTOR`, `SKY_COLOR`, `FOG_COLOR`, `CLOUD_COLOR` from the Age's celestial angle. Needed because 26.1 lighting is timeline/world-clock driven (global clocks cannot express per-Age periods) and server attribute layers are never synced. |
| `ClientGameEvents` | Game bus: `ExtractLevelRenderStateEvent` (Phase 1 sky: `skyRenderState.sunAngle/moonAngle/starAngle/starBrightness/rainBrightness/moonPhase/sunriseAndSunsetColor/skyColor`, `levelRenderState.cloudColor/cloudHeight` from the client `AgeController`), `ViewportEvent.ComputeFogColor` (fog colour), `ClientTickEvent.Post` (local age clock + `WeatherController.updateRaining` → `Level#setRainLevel/setThunderLevel`), `ClientPlayerNetworkEvent.LoggingOut` (`ClientAgeData.clear()`). |
| `ClientAgeData` | Cache of synced `AgeData` by UUID. `accept(AgeData)` (structural change → `copyFrom`, else only time + weather storage so the controller survives), `get(UUID)`, `dataFor(ClientLevel)`, `controllerFor(ClientLevel)` (→ `AgeControllers.client`), `ageTime(ClientLevel)`, `tick(ClientLevel)`, `clear()` (also `AgeControllers.clearClient()`). |
| `ClientNetwork` | `implements menu.ClientSender`; `INSTANCE`, `send(MenuMessagePayload)`, `static sendToServer(CustomPacketPayload)` via `ClientPacketDistributor`. |
| `ClientPayloadHandlers` | `register(RegisterClientPayloadHandlersEvent)`: `AgeDataSyncPayload` → `ClientAgeData.accept`; `UpdateDimensionsPayload` → add/remove keys in `player.connection.levels()`; `ServerConfigPayload` → `serverLabelsAllowed`; `LinkParticlesPayload` → 50 `mystcraft:link` particles; `ExplosionEffectsPayload` → explosion/poof/smoke particles; `LightningPayload` → client-only `ColoredLightningBolt` (visual only); `ProfilingStatePayload` → action-bar overlay message; `MenuMessagePayload` → `MenuMessagePayload.dispatch`. |
| `particle.LinkParticle` | `provider(SpriteSet)` → vanilla `SmokeParticle.Provider` (dark puff). |

## Rendering (`client.render`)

| Class | Notes |
|---|---|
| `SymbolGlyphs` | `drawComponent(g, index, x, y, size, argb)`, `drawWord(g, word, x, y, size[, argb])`, `drawSymbol(g, List<String> poem | AgeSymbol, x, y, size[, argb])` (diamond: top/right/bottom/left at half scale), `drawPage(g, ItemStack, x, y, w, h)` (parchment from `bookui_pagel.png` 156,0 30×40 + glyph / black link-panel rectangle), `drawPageBackground`. Glyphs are tinted black (`DEFAULT`) like the original. Sheet: `textures/misc/symbolcomponents.png`, 512×512, 8×8 cells of 64 px. |
| `AgeSkyMath` | `first(controller, Kind)`, `firstStars`, `angleDegrees(celestial, time, partial, fallback)`, `starBrightness(angle, rain)`, `sunriseColor(sun, angle)` (uses the sun's `horizonGradient()` when present), `argb`, `rgb`, `color(controller, ColorKind, ...)` (dynamic → static → null). |
| `AgeSkyRenderer` / `AgeCloudRenderer` / `AgeWeatherRenderer` | `CustomSkyboxRenderer` / `CustomCloudsRenderer` / `CustomWeatherEffectRenderer` returning `false` (vanilla draws with the extracted values). `AgeSkyRenderer` carries the `// TODO Phase 2` plan. |
| `ItemRenderHelper` | `extract(ItemStackRenderState, ItemStack, ItemDisplayContext, Level, seed)` via `Minecraft#getItemModelResolver().updateForTopItem`, `submit(state, poseStack, collector, light)`. |
| `BoxRenderer` | Stretched block-item cubes (`extractBlock`, `box(...)`) used for the placeholder desk geometry. |
| `LabelRenderer` | Floating labels (`enabled()` = `ClientConfig.RENDER_LABELS` && server allows; 25-block limit; `submit` via `submitNameTag`). |
| `render.model.LegacyModels` | `LayerDefinition`s ported mechanically from the Techne exports (`ModelBookstand`, `ModelLectern`, `ModelWritingDesk`); registered in `ClientSetup.registerLayers` (`EntityRenderersEvent.RegisterLayerDefinitions`); textures are `Sheets.BLOCK_ENTITIES_MAPPER` sprites, i.e. `textures/entity/*` stitched into the block atlas via `atlases/blocks.json`. `bookstand.png` was re-authored as 64x32 (the committed 64x64 was a vertically doubled + flipped copy). |
| `tint.PortalTintSource` | `BlockTintSource` for `link_portal`: `PortalUtils.getReceptacle(level, pos).getPortalColor() \| 0xFF000000`. Alpha must be opaque: 26.1 `QuadInstance.multiplyColor` uses `ARGB.multiply` on all four channels, so a bare `0xRRGGBB` tint renders the block invisible. |
| `tint.InkTintSource` | `FluidTintSource` (NeoForge, extends `BlockTintSource`) for `black_ink`: `0xFF191919`; shared by the block tint registration, the `FluidModel` (fluid renderer uses `ARGB.scaleRGB`, alpha kept) and the `neoforge:fluid_container` bucket (`FluidContentsTint` → `colorAsStack`). |
| `tint.AgeBiomeTints` / `tint.AgeBiomeTintSource` | Static `ColorKind.GRASS/FOLIAGE/WATER` overrides of the current Age (`AgeController.staticColor`), refreshed each client tick into volatile ints and read by chunk-mesh workers. `ClientSetup.wrapBiomeTints` wraps the vanilla `BlockColors` entries (grass block/plants, leaves/vine, water cauldron/bubble column/water block) at `RegisterColorHandlersEvent.BlockTintSources` time, which fires after `BlockColors.createDefault` and overrides by `register`. The water *fluid* itself still uses NeoForge's `FluidTintSources.water()` — `RegisterFluidModelsEvent` rejects duplicate registrations, so a per-Age water colour needs a mixin into `ClientLevel#getBlockTint`/`BiomeColors` (not done). |
| `blockentity.BookDisplayRenderer<T extends BookDisplayBlockEntity>` | bookstand (surface 12/16, scale 0.525) / lectern (7/16, 0.61): displayed item lying flat, rotated by yaw, tilted by pitch; optional label. Render state `State` (item, yaw, pitch, surfaceHeight, scale, label, distanceSq). |
| `blockentity.BookReceptacleRenderer` | subclass: book on the slab face of `BookReceptacleBlock.ROTATION`. |
| `blockentity.WritingDeskRenderer` | Draws the original `ModelWritingDesk` (`render.model.LegacyModels#writingDesk`, 256x128 UVs on `textures/entity/desk.png`) through `SubmitNodeCollector#submitModel` with the original GL chain (`translate(.5,1.5,.5)`, X90, Y90, Z90, Y90*`Direction#get2DDataValue`); backboard parts toggled by `hasBackboard()`, paper parts by `getPaperCount()` thresholds 0/1/2/27/47; target item lies open on the head half. |
| `blockentity.StarFissureRenderer` | `submitCustomGeometry(RenderTypes.endPortal())` quads on the top (1.6 px) and bottom faces. |
| `entity.LinkbookRenderer` | `EntityRenderer<LinkbookEntity, State>`: book item flat on the ground, hurt bounce, name tag = `getAgeName()` when labels are enabled. |
| `entity.MeteorRenderer` | tumbling end-portal cube scaled by `getScale()`. |
| `entity.MystFallingBlockRenderer` | Mirrors vanilla `FallingBlockRenderer`: `MovingBlockRenderState` (blockState, blockPos at bbox top, biome, `ClientLevel#cardinalLighting()`, light engine) → `SubmitNodeCollector#submitMovingBlock` after `translate(-0.5, 0, -0.5)`; `shadowRadius = 0.5` on the renderer. |
| `entity.ColoredLightningRenderer` | port of the vanilla bolt geometry via `RenderTypes.lightning()`, tinted with `getColor()`. |

## Screens (`client.screen`, all extend `AbstractMystcraftScreen<M>` → `AbstractContainerScreen<M>`)

`AbstractMystcraftScreen`: owns `GuiElement`s (rendered in `extractBackground` after `drawBackgroundTexture`, tooltips via
`setTooltipForNextFrame`), routes `mouseClicked/mouseReleased/mouseScrolled/keyPressed/charTyped` to elements and
tracked `EditBox`es (anvil pattern), `send(msg, tag)` = local `menu.processMessage` + `menu.sendToServer`,
`sendOnly(msg[, tag])` for server-only actions, `ops()`, `carried()`, `player()`.

| Screen | Size / texture | Elements & messages |
|---|---|---|
| `WritingDeskScreen` | 409×185; `writingdesk.png` at (233,20) | `NotebookTabs` (`SetActiveNotebook`, `SetFirstNotebook`, `AddToCollection`), search `EditBox` + `AZ`/`ALL` `ToggleButton`s, `PageSurface` (`AddToSurface`, `RemoveFromCollection`(Page, ×64 with shift), `RemoveFromOrderedCollection`, right-click-release → `WriteSymbol`), `BookElement` (`SetCurrentPage`, `Link`), `ScrollablePages` for folders (`InsertHeldAt`, `TakeFromSlider`), page preview for page targets, name `EditBox` (`SetTitle`, read-only for pages/empty, synced from `getTargetName()`), `InkTank`. |
| `BookBinderScreen` | 176×181; `pagebinder.png` | title `EditBox` (7,9; red outline while empty; `SetTitle`), `ScrollablePages` (7,45,162,40; `InsertHeldAt`/`TakeFromSlider`), pulsing missing-link-panel icon (27,26) with tooltip. |
| `InkMixerScreen` | 176×181; `inkmixer.png` | basin (54,16 66×65): ink fill + animated property-gradient tint + colour rings; click inside r²=900 with an item → `Consume(Single)`. |
| `LinkModifierScreen` | 176×166; `single_slot.png` | one `ToggleButton` per `InkEffects.getProperties()` from (5,10) (`SetFlag`), seed `EditBox` (80,15; Descriptive Books only; `SetSeed`), name `EditBox` (80,56; `SetTitle`), dimension id text at (100,40), kill (120,32) arms → confirm (140,32, red) → `RecycleDim`. |
| `BookScreen` | 327×199 frame; `bookui_*.png`, `single_slot.png` when no book | `BookElement` (cover, gold borders for Descriptive Books, title/authors, link panel gradient `0xFF000044→0xFF006666` when visited else black, grey overlay when not permitted, click → `Link`; pages with symbol glyph size 140 + tooltip; left/right halves, ←/→/A/D → `SetCurrentPage`; footer `current/total`). |
| `FolderScreen` | 176×231; desk texture region (0,82) 176×80 for the inventory | search + `AZ`/`ALL`, `PageSurface` (`AddToSurface`, `RemoveFromCollection`, `RemoveFromOrderedCollection`). |
| `ArchivistShopScreen` | 176×181; `tradeshop.png` | 3 `ShopPanel`s (page tile, name, price + emerald) with `Buy` buttons → `PI(Index)`; booster icon + count + `Buy` → `PB`; emerald balance label. |

`client.screen.gui`: `GuiElement` (base), `ToggleButton`, `PageSurface` (grid of 30×40 tiles, grouping/counts for
`PageCollection` items, ordered slots for `OrderablePageProvider`s, `ALL` ghost pages from `SymbolRegistry.all()`,
search filter, scrollbar), `ScrollablePages` (horizontal strip with arrows), `BookElement` (+ `Container` interface),
`NotebookTabs`, `InkTank`.

## Resources package F must add

* `assets/mystcraft/particles/link.json`: `{"textures":["minecraft:generic_0","minecraft:generic_1","minecraft:generic_2","minecraft:generic_3","minecraft:generic_4","minecraft:generic_5","minecraft:generic_6","minecraft:generic_7"]}`
* `data/mystcraft/dimension_type/age.json` attributes: `"neoforge:custom_skybox": "mystcraft:age"`, `"neoforge:custom_clouds": "mystcraft:age"`, `"neoforge:custom_weather_effects": "mystcraft:age"`.
* Blockstate/model for `link_portal` must use `"tintindex": 0` on its faces; `black_ink` fluid block model tint index 0.
* Textures already present and used: `textures/gui/{bookui_cover,bookui_pagel,bookui_pager,bookui_rpage_full,inkmixer,pagebinder,single_slot,tradeshop,writingdesk}.png`, `textures/misc/symbolcomponents.png`.

## Lang keys used

```
gui.mystcraft.profiling.started=Baseline profiling started
gui.mystcraft.profiling.finished=Baseline profiling finished
gui.mystcraft.tank.empty=Empty
gui.mystcraft.surface.sort=Sort Alphabetically
gui.mystcraft.surface.show_all=Show all Symbols
gui.mystcraft.surface.search=Search
gui.mystcraft.item_name=Name
gui.mystcraft.seed=Seed
gui.mystcraft.binder.missing_link_panel=Add a link panel as the first page of the book.
gui.mystcraft.link_modifier.kill=Mark book as dead. (NO UNDO!)
gui.mystcraft.link_modifier.confirm_kill=Confirm mark book as dead. (NO UNDO!)
gui.mystcraft.shop.buy_page=Buy this page
gui.mystcraft.shop.buy_booster=Buy a Sealed Notebook (%s emeralds)
```
(`container.inventory` is vanilla.)

## UNVERIFIED lines (grep `UNVERIFIED`)

`ClientLevel#addEntity`, `SkyRenderState.rainBrightness` semantics, `MoonPhase.values()` order,
`ViewportEvent.ComputeFogColor#setRed/setGreen/setBlue`, `SmokeParticle.Provider(SpriteSet)`,
`submitNameTag` with a null attachment, `EditBox#canConsumeInput`. (`Biome#getBaseTemperature`, `Direction#getRotation`,
`CameraRenderState#pos` and the falling-block shadow radius were resolved in `integration_review_2.md`.)

## Phase 2 TODOs

Custom sky (`AgeSkyRenderer`), real desk / book models, per-biome weather temperatures, link-panel effects (Disarm
lightning, LookingGlass), D'ni colour eye, fluid sprite in `InkTank`, water-*fluid* tint from `ColorKind.WATER` (needs a
mixin, see `tint.AgeBiomeTints`). Done since: `MovingBlockRenderState` falling blocks, grass/foliage block tints, ink
`FluidModel`, dynamic bucket model.
