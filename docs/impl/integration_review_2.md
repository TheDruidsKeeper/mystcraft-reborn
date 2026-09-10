# Integration review 2 (wave 2: package D `client/**`, package F `src/main/resources`, build glue)

Scope: every file under `src/main/java/com/techbucketdivision/mystcraft/client/**`, `MystcraftClient.java`, the menu /
network / block-entity / entity surfaces the client consumes, and everything under `src/main/resources` +
`src/main/templates`. Method: static read of every client call site against the callee (menu getters, BE/entity
accessors, payload record components), every vanilla/NeoForge client call against `docs/API_CHEATSHEET.md` (G3, G4, H,
K, L2a-2) and `docs/TOOLCHAIN.md` §4.5–4.8, plus scripted passes over the resources (lang keys ↔ Java literals,
blockstates ↔ block state definitions, models/textures/items ↔ registries, sounds/particles/data ↔ registries). No
compiler was available.

## 1. Client ↔ common

All consistent — no code changes needed:

* Every `menu.xxx(...)` used by the seven screens exists on the menu class (or `AbstractMystcraftMenu`) with the used
  arity: `WritingDeskMenu` (16 getters + `X_SHIFT/Y_SHIFT/MAX_TITLE/MSG_*`), `BookBinderMenu` (`getPendingTitle`,
  `getPageList`, `isMissingLinkPanel`, `MAX_TITLE`), `InkMixerMenu` (`hasInk`, `getPropertyGradient`,
  `updateCraftResult`, `MSG_CONSUME`), `LinkModifierMenu` (`getLinkFlag(LinkProperty)`, `getItemSeed`, `hasItemSeed`,
  `isLinkDead`, `getLinkDimensionId`, `MSG_*`), `BookMenu` (10 getters), `FolderMenu` (`getPageCollection`,
  `getTabItemName`), `ArchivistShopMenu` (`getShopItem(int)`, `getShopItemPrice(int)`, `getBoosterCount/Cost`,
  `getPlayerEmeralds`, `SHOP_SLOTS`). Message tag keys written by the screens (`Tab`, `Index`, `Single`, `Title`,
  `Page` (ItemStack.OPTIONAL_CODEC), `Symbol`, `Seed`, `Flag`, `Value`) match the keys read in each
  `processMessage`.
* `AbstractMystcraftMenu.setClientSender(ClientSender)` ← `ClientSetup.register` installs `ClientNetwork.INSTANCE`;
  `ClientSender.send(MenuMessagePayload)`; `menu.sendToServer(String, CompoundTag)` and `processMessage(Player,
  CompoundTag)` are what `AbstractMystcraftScreen.send/sendOnly` call.
* Renderers: `BookDisplayBlockEntity#getDisplayItem/getYaw/getPitch/getBookTitle`, `BookReceptacleBlock.ROTATION`
  (`EnumProperty<Direction>`), `WritingDeskBlockEntity#hasBackboard/getPaperCount/getDisplayItem`,
  `BookUtil.isLinkingItem`, `LinkbookEntity#getBook/getAgeName/hurtTime`, `MeteorEntity#getScale`,
  `MystFallingBlockEntity#getBlockState`, `ColoredLightningBolt#getColor/setColor/setVisualOnly`,
  `PortalUtils.getReceptacle(BlockGetter, BlockPos)` (the tint source passes a `BlockAndTintGetter`; the `BlockGetter`
  overload exists), `BookReceptacleBlockEntity#getPortalColor`, `ClientConfig.RENDER_LABELS`.
* Payload handlers: `AgeDataSyncPayload.data()`, `UpdateDimensionsPayload.keys()/add()`, `ServerConfigPayload
  .serverLabels()`, `LinkParticlesPayload.x/y/z()`, `ExplosionEffectsPayload.x/y/z/size/affected()`,
  `LightningPayload.x/y/z/color()`, `ProfilingStatePayload.running()`, `MenuMessagePayload.dispatch(Player, payload)`
  — all match the records in `network/*`; `Payloads.register` registers every client-bound type without a handler and
  `ClientPayloadHandlers.register` supplies all eight (incl. the bidirectional `MenuMessagePayload`).
* `MystcraftClient` (`@Mod(dist = CLIENT)`) → `ClientSetup.register(IEventBus)` exists.
* Age model used by the sky code: `AgeData#uuid/seed/symbols/dead/visited/name/worldTime/setWorldTime/data/copyFrom/
  uuidFromLevelKey`, `AgeControllers.client(AgeData, HolderLookup.Provider)/clearClient()`, `AgeController#celestials/
  sky/weather/celestialAngle/dynamicColor/staticColor`, `Celestial.Kind`, `Celestial#getAltitudeAngle/phase/
  horizonGradient`, `WeatherController#updateRaining/getRainStrength/getThunderStrength`, `SkyOptions.cloudHeight/
  drawVoid`, `ColorGradient#isEmpty/totalLength/getColor`, `Colors.RGB#clamp/toRGB`, `WordData.components`,
  `SymbolRegistry.all/get`, `PageItem.getSymbol/getSymbolId/isLinkPanel/createSymbolPage`, `ItemBehaviours.*`.
* Isolation: no file outside `client/` imports `net.minecraft.client.*` or `…mystcraft.client.*` (only the
  `Dist.CLIENT` entry point `MystcraftClient`).

## 2. Client ↔ cheat sheet

Verified as matching: `GuiGraphicsExtractor` calls (`blit` 10- and 11-arg forms, `fill`, `fillGradient`, `outline`,
`text(Font, String|Component, int, int, int, boolean)`, `item`, `enableScissor/disableScissor`, `pose()` as
`Matrix3x2fStack`, `setTooltipForNextFrame(Font, List<Component>, int, int)`), `Screen.getTooltipFromItem(Minecraft,
ItemStack)`, `MouseButtonEvent.x()/y()/button()`, `KeyEvent.key()`, `CharacterEvent`, public `extractBackground`,
`extractLabels`, `containerTick`, `EditBox` ctor/setters, BER interface (`createRenderState`, 5-arg
`extractRenderState`, `submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)`, `BlockEntityRenderState.extractBase`,
NeoForge `getRenderBoundingBox`), `EntityRenderer<T, S>` with `extractRenderState(T, S, float)`,
`ExtractLevelRenderStateEvent#getRenderState()/getLevel()/getCamera()/getDeltaTracker()`, `SkyRenderState` fields
(`sunAngle, moonAngle, starAngle, starBrightness, rainBrightness, moonPhase, sunriseAndSunsetColor, skyColor,
shouldRenderDarkDisc`), `LevelRenderState.cloudColor/cloudHeight/skyRenderState`,
`RegisterCustomEnvironmentEffectRendererEvent#registerSkyboxRenderer/registerCloudRenderer/registerWeatherEffectRenderer`,
`CustomSkyboxRenderer/CustomCloudsRenderer/CustomWeatherEffectRenderer` method shapes, `RegisterColorHandlersEvent
.BlockTintSources#register(List<BlockTintSource>, Block...)`, `RegisterMenuScreensEvent#register`,
`RegisterClientPayloadHandlersEvent#register(Type, IPayloadHandler)`, `RegisterParticleProvidersEvent#registerSpriteSet`,
`ClientPacketDistributor.sendToServer`, `Minecraft#player/level/font/getItemModelResolver()`, `ItemModelResolver
#updateForTopItem`, `ItemStackRenderState#clear/isEmpty/submit`, `SubmitNodeCollector#submitCustomGeometry/submitNameTag`,
`RenderTypes.endPortal()/lightning()`, `Level#getRainLevel/setRainLevel/setThunderLevel`, `sendOverlayMessage`.

`// UNVERIFIED` markers resolved (cheat sheet answers them):

| File | Was | Now |
|---|---|---|
| `ClientGameEvents#biomeTemperature` | `Biome#getBaseTemperature` | verified in I2 — marker removed |
| `render/blockentity/BookReceptacleRenderer` | `Direction#getRotation()` | verified in L1b (`Quaternionf getRotation()`) — marker removed |
| `render/LabelRenderer#distanceSq` | `CameraRenderState#pos` | listed in K (`LevelRenderState.cameraRenderState.pos` Vec3) — marker removed |
| `render/entity/MystFallingBlockRenderer` | `this.shadowRadius = 0.5f` in the ctor (protected `EntityRenderer` field, unverified) | replaced by `state.shadowRadius = 0.5f` in `extractRenderState` (`EntityRenderState#shadowRadius` is a verified public field) |

Markers left in place (not answerable from the docs): `SkyRenderState.rainBrightness` semantics, `MoonPhase.values()`
order, `ViewportEvent.ComputeFogColor#setRed/Green/Blue`, `ClientLevel#addEntity`, `SmokeParticle.Provider(SpriteSet)`,
`submitNameTag` with a null attachment, `EditBox#canConsumeInput`. See risks below.

## 3. Resources ↔ Java

* **Lang** — scripted over every `Component.translatable("…")` / `translatableWithFallback` / `descriptionId` literal and
  the dynamically built prefixes (`symbol.mystcraft.<id>` for all ids in IMPLEMENTATION_CONTRACTS incl. the 16
  `mod_color_*`, `link_property.mystcraft.<name>` for all 11 properties, `commands.mystcraft.*`,
  `instability.mystcraft.*`, `container.mystcraft.*`, `item/block/entity.mystcraft.<registry name>`, `fluid_type…`,
  `subtitles.mystcraft.<sound id>`). **Missing (added to `en_us.json`)**: the 11 keys package D actually uses —
  `gui.mystcraft.profiling.started/finished`, `gui.mystcraft.tank.empty`, `gui.mystcraft.surface.sort/show_all/search`,
  `gui.mystcraft.item_name`, `gui.mystcraft.seed`, `gui.mystcraft.binder.missing_link_panel`,
  `gui.mystcraft.shop.buy_page`, `gui.mystcraft.shop.buy_booster` (`%s`). `gui.mystcraft.link_modifier.kill/
  confirm_kill` were reworded to the tooltip sentences the screen expects. The provisional
  `gui.mystcraft.writing_desk.*`/`book_binder.*`/`ink_mixer.*`/`book.*`/`folder.*`/`shop.*`/`profiling.running|complete`
  keys are unused by code but harmless. (`container.inventory` is vanilla.)
* **Blockstates** — property names/values match the Java definitions: `facing` (N/E/S/W) for `ink_mixer`, `book_binder`,
  `link_modifier`, `lectern`; `rotation` = 6 `Direction`s for `book_receptacle`; `rotation` 0–7 for `bookstand`;
  `facing×top×foot` (16 combos) for `writing_desk`; `""` catch-all for `crystal` and `link_portal` (both have
  `source`/`active`), `black_ink` (`level`), `star_fissure`, the 7 `decay_*` (no properties). Every referenced model
  exists under `models/block`, every model parent/texture resolves (mod textures present, vanilla ones assumed).
* **Items** — all 28 registered items (`ModItems`, incl. 7 decay items) have `items/<name>.json` and `models/item/<name>.json`;
  all 19 blocks have `blockstates/<name>.json`. `black_ink_bucket` still uses the vanilla bucket texture (placeholder).
* **sounds.json** keys = `ModSounds` ids (`linking.pop`, `linking.link*`, `entity.meteor.roar`); all 8 `.ogg` files
  exist; subtitles keys exist in lang.
* **particles/link.json** exists (vanilla `generic_0..7`). `textures/misc/symbolcomponents.png` and every
  `textures/gui/*.png` used by `SymbolGlyphs`/screens exist.
* **dimension_type/age.json** carries `neoforge:custom_skybox/custom_clouds/custom_weather_effects = "mystcraft:age"` =
  `ClientSetup.AGE_ENVIRONMENT`.
* **Recipes** reference only registered mod items (`book_binder`, `book_receptacle`, `bookstand`, `collation_folder`,
  `crystal`, `ink_mixer`, `ink_vial`, `lectern`, `symbol_portfolio`, `writing_desk`, `writing_desk_backboard`) and
  vanilla ids; `recipe/linking_book.json` type `mystcraft:linking_book` = `ModRecipes` serializer name.
* **Loot** — `MystcraftLibrary.LOOT_TABLE = mystcraft:chests/library` ↔ `data/mystcraft/loot_table/chests/library.json`.
* **Advancements** — triggers `mystcraft:writing_desk_write`, `enter_myst_dimension_safe`, `enter_myst_dimension_quinn`
  = `ModCriteria` names.
* **Trades** — `ModVillagers` keys `mystcraft:archivist/level_1..5` ↔ `trade_set/archivist/level_n.json`, each pointing at
  `#mystcraft:archivist/level_n` ↔ `tags/villager_trade/archivist/level_n.json`, whose values exist under
  `villager_trade/archivist/`.

## 4. Build glue

* `build.gradle` adds `src/generated/resources` as a resource dir → created `src/generated/resources/.gitkeep` (the
  directory did not exist; `.gitignore` only ignores `src/generated/**/.cache/`). No `data/` datagen package exists and
  nothing references one.
* `src/main/templates/META-INF/neoforge.mods.toml`: `[[accessTransformers]] file="META-INF/accesstransformer.cfg"` ↔
  `src/main/resources/META-INF/accesstransformer.cfg` (present, one `public-f WorldGenSettings.dimensions` line);
  `logoFile="mystcraft_logo.png"` ↔ `src/main/resources/mystcraft_logo.png` (present).

## Remaining risks (client / resources), most likely compile problems first

1. `ViewportEvent.ComputeFogColor#setRed/setGreen/setBlue` and `getPartialTick()` (double) / `getCamera()` — 1.21
   names; the cheat sheet only confirms the event still exists.
2. `ClientLevel#addEntity(Entity)` in `ClientPayloadHandlers#handleLightning` — public in 1.21.x. Fallback: drop the
   visual-only bolt for out-of-range players (the entity itself is still tracked normally).
3. `SmokeParticle.Provider(SpriteSet)` (`LinkParticle`) and `ParticleProvider` generic shape — the cheat sheet warns
   26.1 may have added a `RandomSource` parameter to `createParticle`; reusing the vanilla provider sidesteps that,
   but the ctor is unverified.
4. `EditBox#canConsumeInput()` (`AbstractMystcraftScreen#keyPressed`) — if absent, replace the condition with
   `box.isFocused() && box.keyPressed(event)`.
5. `Screen.hasShiftDown()` (static, `WritingDeskScreen`/`FolderScreen` pickup) — not in the cheat sheet's `Screen`
   list; 26.1 also offers `InputWithModifiers#hasShiftDown()` on the event, but the pickup path only has the
   `GuiElement` mouse args. `Minecraft#isPaused()` (`ClientGameEvents#onClientTickPost`) likewise unlisted.
6. `net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay` package (cheat sheet gives the
   nested name only) and `net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState`.
7. `MoonPhase.values()` ordering / `SkyRenderState.rainBrightness` semantics — visual only.
8. `submitNameTag(poseStack, null, …)` anchoring for BE labels — visual only.
9. **Functional gap (not a compile error): the black ink fluid has no client fluid model.** 26.1 moved fluid textures
   off `IClientFluidTypeExtensions` onto `RegisterFluidModelsEvent` (TOOLCHAIN §4.12); nothing registers one, so the
   placed `black_ink` block will render with the missing texture even though `textures/block/fluid.png` /
   `fluid_flow.png` (+ `.mcmeta`) exist. The `FluidModel.Unbaked`/sprite-id constructor shapes are unverified, so no
   speculative code was added — add a `RegisterFluidModelsEvent` listener in `ClientSetup` once the signatures are
   confirmed (and pass `InkTintSource` there instead of / in addition to the block tint registration).
10. `advancement/root.json` background sprite id (`minecraft:gui/advancements/backgrounds/stone`) — data format,
    unverified for 26.1 (already flagged in `resources.md`).
11. `black_ink_bucket` item texture placeholder (vanilla bucket, untinted).
