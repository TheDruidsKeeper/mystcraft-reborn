package com.techbucketdivision.mystcraft.client;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless client verification, enabled only by {@code MYSTCRAFT_CLIENT_SELFCHECK=1} (or
 * {@code -Dmystcraft.clientselfcheck=true}); never active in normal play. Drives the running client through a fixed
 * script and then shuts it down, so the Docker {@code client-smoke} stage can exercise everything the dedicated-server
 * smoke test cannot: resource loading / model baking, screens, block-entity renderers, the Age sky, tints, portals.
 *
 * <p>Script: create a fresh flat creative world → screenshot → {@code /myst-dev scene} (every renderable block) →
 * screenshot → {@code /myst visit} (link into a brand-new Age through the real link path) → screenshots at day and
 * night, including the scene rebuilt inside the Age → {@code CLIENT SELFCHECK PASSED} and exit. Every step has a
 * timeout; any failure logs {@code CLIENT SELFCHECK FAILED: reason} and exits. Screenshots land in
 * {@code <game dir>/screenshots/selfcheck_*.png}; the Docker stage exports them for review.
 */
public final class ClientSelfCheck {
    private ClientSelfCheck() {}

    public static boolean enabled() {
        return "true".equalsIgnoreCase(System.getProperty("mystcraft.clientselfcheck"))
                || "1".equals(System.getenv("MYSTCRAFT_CLIENT_SELFCHECK"));
    }

    public static void register(IEventBus gameBus) {
        if (!enabled()) return;
        Mystcraft.LOGGER.info("[clientcheck] enabled - the client will run the self-check script and exit");
        gameBus.addListener(ClientSelfCheck::onClientTick);
    }

    private enum Step { TITLE, WORLD_LOADING, OVERWORLD_SETTLE, SCENE_OVERWORLD, CLOSEUPS, SCREENS, VISIT_AGE, AGE_SETTLE, SCENE_AGE, NIGHT, TOUR_VISIT, TOUR_DAY, TOUR_NIGHT, DONE }

    private static final int OVERALL_BUDGET_TICKS = 20 * 60 * 18; // 18 minutes (the QA tour visits every shelf world)
    private static Step step = Step.TITLE;
    private static int ticks;          // total ticks since start
    private static int stepTicks;      // ticks in the current step
    private static int screenshots;
    private static int closeupIndex;
    private static final String[] CLOSEUPS = {"desk", "bookstand", "lectern", "portal", "ink", "fissure", "pages"};
    /** Screens to open and screenshot: "open <element>" for blocks, "use <item>" for items. */
    private static final String[] SCREENS = {"open desk", "open ink_mixer", "open book_binder", "open link_modifier",
            "use linking_book", "use descriptive_book", "use folder"};
    private static int screenIndex;
    /** QA shelf tour (docs/QA.md): every case is visited and screenshotted by day and by night for scripts/qa/compare.py. */
    private static final List<com.techbucketdivision.mystcraft.command.QaShelf.Case> TOUR = com.techbucketdivision.mystcraft.command.QaShelf.cases();
    private static int tourIndex;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> tourFrom;
    private static final List<String> failures = new ArrayList<>();

    private static void onClientTick(ClientTickEvent.Post event) {
        if (step == Step.DONE) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        stepTicks++;
        if (ticks > OVERALL_BUDGET_TICKS) {
            fail("overall budget exceeded in step " + step);
            return;
        }
        try {
            switch (step) {
                case TITLE -> {
                    if (mc.screen instanceof AccessibilityOnboardingScreen) {
                        mc.setScreen(new TitleScreen()); // first-run onboarding; options.txt may not have been seeded
                    }
                    if (mc.screen instanceof TitleScreen && stepTicks > 40) {
                        Mystcraft.LOGGER.info("[clientcheck] title screen reached after {} ticks; creating world", ticks);
                        createWorld(mc);
                        next(Step.WORLD_LOADING);
                    } else if (stepTicks > 20 * 120) {
                        fail("title screen never appeared (screen=" + mc.screen + ")");
                    }
                }
                case WORLD_LOADING -> {
                    if (mc.level != null && mc.player != null && mc.getConnection() != null) {
                        Mystcraft.LOGGER.info("[clientcheck] world loaded in {} ticks", stepTicks);
                        next(Step.OVERWORLD_SETTLE);
                    } else if (stepTicks > 20 * 120) {
                        fail("world did not load (screen=" + mc.screen + ")");
                    }
                }
                case OVERWORLD_SETTLE -> {
                    if (stepTicks == 60) screenshot(mc, "01_overworld");
                    if (stepTicks > 70) {
                        command(mc, "myst-dev scene");
                        next(Step.SCENE_OVERWORLD);
                    }
                }
                case SCENE_OVERWORLD -> {
                    if (stepTicks == 60) screenshot(mc, "02_scene_overworld");
                    if (stepTicks > 70) {
                        closeupIndex = 0;
                        next(Step.CLOSEUPS);
                    }
                }
                case CLOSEUPS -> {
                    // one close-up per element: teleport, wait 30 ticks, screenshot
                    if (closeupIndex >= CLOSEUPS.length) {
                        screenIndex = 0;
                        next(Step.SCREENS);
                    } else if (stepTicks == 1) {
                        command(mc, "myst-dev scene closeup " + CLOSEUPS[closeupIndex]);
                    } else if (stepTicks == 30) {
                        screenshot(mc, String.format("02%c_closeup_%s", (char) ('a' + closeupIndex), CLOSEUPS[closeupIndex]));
                    } else if (stepTicks > 32) {
                        closeupIndex++;
                        stepTicks = 0;
                    }
                }
                case SCREENS -> {
                    // one screen each: open, wait, screenshot, close
                    if (screenIndex >= SCREENS.length) {
                        command(mc, "myst visit Selfcheck Age");
                        next(Step.VISIT_AGE);
                    } else if (stepTicks == 1) {
                        // stand next to the block first: container menus close when the player is > 8 blocks away
                        if (SCREENS[screenIndex].startsWith("open ")) command(mc, "myst-dev scene closeup " + SCREENS[screenIndex].substring(5));
                    } else if (stepTicks == 5) {
                        command(mc, "myst-dev scene " + SCREENS[screenIndex]);
                    } else if (stepTicks == 25) {
                        String name = SCREENS[screenIndex].substring(SCREENS[screenIndex].indexOf(' ') + 1);
                        if (mc.screen == null) {
                            failures.add("screen did not open for /myst-dev scene " + SCREENS[screenIndex]);
                        } else {
                            Mystcraft.LOGGER.info("[clientcheck] screen {} open: {}", name, mc.screen.getClass().getSimpleName());
                        }
                        screenshot(mc, "02z_screen_" + name);
                    } else if (stepTicks == 30 && mc.screen instanceof com.techbucketdivision.mystcraft.client.screen.WritingDeskScreen desk) {
                        // the scene desk is a Scholar's desk: the surface lists every symbol; then the Sky tab and the
                        // Modifiers tab with the folder's sun page selected (modifiers that fit it are highlighted)
                        int listed = desk.listedSymbols();
                        Mystcraft.LOGGER.info("[clientcheck] scholar's desk lists {} symbols", listed);
                        if (listed < 50) failures.add("scholar's desk surface lists only " + listed + " symbols");
                        desk.selectTab(com.techbucketdivision.mystcraft.client.screen.gui.SymbolSurface.Tab.SKY);
                    } else if (stepTicks == 34 && mc.screen instanceof com.techbucketdivision.mystcraft.client.screen.WritingDeskScreen desk) {
                        screenshot(mc, "02y_desk_tab_sky");
                        desk.selectPage(0);
                        desk.selectTab(com.techbucketdivision.mystcraft.client.screen.gui.SymbolSurface.Tab.MODIFIERS);
                    } else if (stepTicks == 38 && mc.screen instanceof com.techbucketdivision.mystcraft.client.screen.WritingDeskScreen desk) {
                        screenshot(mc, "02y_desk_tab_modifiers");
                        desk.selectTab(com.techbucketdivision.mystcraft.client.screen.gui.SymbolSurface.Tab.CREATURES);
                    } else if (stepTicks == 42 && mc.screen instanceof com.techbucketdivision.mystcraft.client.screen.WritingDeskScreen desk) {
                        int creatures = desk.listedSymbols();
                        Mystcraft.LOGGER.info("[clientcheck] creatures tab lists {} symbols", creatures);
                        if (creatures != 4) failures.add("creatures tab lists " + creatures + " symbols, expected 4");
                        screenshot(mc, "02y_desk_tab_creatures");
                    } else if (stepTicks == 44) {
                        if (mc.screen != null) mc.screen.onClose();
                    } else if (stepTicks > 48) {
                        screenIndex++;
                        stepTicks = 0;
                    }
                }
                case VISIT_AGE -> {
                    ClientLevel level = mc.level;
                    if (level != null && AgeManager.isAge(level.dimension())) {
                        Mystcraft.LOGGER.info("[clientcheck] arrived in {} after {} ticks", level.dimension().identifier(), stepTicks);
                        next(Step.AGE_SETTLE);
                    } else if (stepTicks > 20 * 90) {
                        fail("never arrived in an Age (/myst visit) - check [link] lines");
                    }
                }
                case AGE_SETTLE -> {
                    if (stepTicks == 100) {
                        checkAgeClientState(mc);
                        screenshot(mc, "03_age_arrival");
                    }
                    if (stepTicks > 110) {
                        command(mc, "myst-dev scene");
                        next(Step.SCENE_AGE);
                    }
                }
                case SCENE_AGE -> {
                    if (stepTicks == 60) {
                        screenshot(mc, "04_scene_age");
                        int known = mc.player == null ? 0 : com.techbucketdivision.mystcraft.knowledge.SymbolKnowledge.known(mc.player).size();
                        Mystcraft.LOGGER.info("[clientcheck] symbols known after arriving in the Age: {}", known);
                        if (known == 0) failures.add("arriving in an Age taught no symbols (knowledge not synced to the client)");
                    }
                    // the book of this Age: its link panel should show the photo taken on arrival
                    if (stepTicks == 70) command(mc, "myst-dev scene use current_age_book");
                    if (stepTicks == 95) {
                        if (mc.screen == null) failures.add("current Age book screen did not open");
                        var level = mc.level;
                        var info = level == null ? null : new com.techbucketdivision.mystcraft.api.linking.LinkInfo(
                                java.util.Optional.of(level.dimension()),
                                java.util.Optional.ofNullable(com.techbucketdivision.mystcraft.age.AgeData.uuidFromLevelKey(level.dimension())),
                                java.util.Optional.empty(), 0f, "", java.util.Set.of(), java.util.Map.of());
                        int frames = PanelImages.frameCount(info);
                        Mystcraft.LOGGER.info("[clientcheck] link panel pictures for this Age: {}", frames);
                        if (frames < 4) failures.add("expected four link panel pictures (N/E/S/W) for the visited Age, got " + frames + " (see [panel] lines)");
                        screenshot(mc, "04b_age_book");
                    }
                    // page to the first symbol page (category label + glyph) and to the summary page after the last page
                    if (stepTicks == 100 && mc.screen instanceof com.techbucketdivision.mystcraft.client.screen.BookScreen book) {
                        book.jumpToPage(1);
                    }
                    if (stepTicks == 110 && mc.screen instanceof com.techbucketdivision.mystcraft.client.screen.BookScreen book) {
                        if (book.getMenu().getCurrentPageIndex() != 1) failures.add("book did not turn to page 1");
                        var page = book.getMenu().getCurrentPage();
                        var symbol = com.techbucketdivision.mystcraft.item.PageItem.getSymbol(page);
                        if (symbol == null) failures.add("page 1 of the Age book is not a symbol page");
                        else if (symbol.category() != com.techbucketdivision.mystcraft.api.symbol.SymbolCategory.TERRAIN) {
                            failures.add("page 1 of the Age book is not the terrain page (organised by category); got " + symbol.id());
                        }
                        screenshot(mc, "04c_age_book_page");
                        book.jumpToPage(book.getMenu().getPageCount());
                    }
                    if (stepTicks == 125 && mc.screen instanceof com.techbucketdivision.mystcraft.client.screen.BookScreen book) {
                        var summary = book.getMenu().getSummary();
                        Mystcraft.LOGGER.info("[clientcheck] Age book summary: {}", summary);
                        if (summary == null) failures.add("no Age summary synced for the bound book");
                        else if (summary.total() == 0 || summary.discovered() == 0) failures.add("Age summary has no discovered pages: " + summary);
                        screenshot(mc, "04d_age_book_summary");
                    }
                    if (stepTicks == 130 && mc.screen != null) mc.screen.onClose();
                    if (stepTicks > 134) {
                        command(mc, "myst time set night");
                        next(Step.NIGHT);
                    }
                }
                case NIGHT -> {
                    if (stepTicks == 40) screenshot(mc, "05_age_night");
                    if (stepTicks > 60) startTourVisit(mc);
                }
                case TOUR_VISIT -> {
                    ClientLevel level = mc.level;
                    if (level != null && AgeManager.isAge(level.dimension()) && !level.dimension().equals(tourFrom)) {
                        Mystcraft.LOGGER.info("[clientcheck] tour {} arrived in {} after {} ticks", TOUR.get(tourIndex).id(), level.dimension().identifier(), stepTicks);
                        next(Step.TOUR_DAY);
                    } else if (stepTicks > 20 * 60) {
                        failures.add("tour " + TOUR.get(tourIndex).id() + " never arrived (/myst-dev qa-visit) - check [link] lines");
                        tourIndex++;
                        startTourVisit(mc);
                    }
                }
                case TOUR_DAY -> {
                    if (stepTicks == 1) {
                        // Screenshots feed scripts/qa/compare.py: no HUD/chat, and look slightly up so the sky band is sky.
                        mc.options.hideGui = true;
                        mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
                        if (mc.player != null) mc.player.setXRot(-12f);
                    }
                    if (stepTicks == 60) command(mc, "myst time set day");
                    if (stepTicks == 100) {
                        checkAgeClientState(mc);
                        screenshot(mc, "qa_" + TOUR.get(tourIndex).id() + "_day");
                        command(mc, "myst time set night");
                        next(Step.TOUR_NIGHT);
                    }
                }
                case TOUR_NIGHT -> {
                    if (stepTicks == 40) {
                        screenshot(mc, "qa_" + TOUR.get(tourIndex).id() + "_night");
                        tourIndex++;
                        startTourVisit(mc);
                    }
                }
                case DONE -> { }
            }
        } catch (RuntimeException e) {
            Mystcraft.LOGGER.error("[clientcheck] step {} threw", step, e);
            fail("exception in step " + step + ": " + e);
        }
    }

    /** Links into the next shelf world, or finishes when every case has been visited. */
    private static void startTourVisit(Minecraft mc) {
        if (tourIndex >= TOUR.size()) {
            mc.options.hideGui = false;
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
            finish(mc);
            return;
        }
        tourFrom = mc.level == null ? null : mc.level.dimension();
        command(mc, "myst-dev qa-visit " + TOUR.get(tourIndex).id());
        next(Step.TOUR_VISIT);
    }

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings("mystcraft-selfcheck", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(1337L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("mystcraft-selfcheck", settings, options,
                WorldPresets::createFlatWorldDimensions, mc.screen);
    }

    private static void command(Minecraft mc, String command) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            fail("no connection for command /" + command);
            return;
        }
        Mystcraft.LOGGER.info("[clientcheck] /{}", command);
        connection.sendCommand(command);
    }

    private static void screenshot(Minecraft mc, String name) {
        String file = "selfcheck_" + name + ".png";
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), 1,
                msg -> Mystcraft.LOGGER.info("[clientcheck] screenshot {}: {}", file, msg.getString()));
        screenshots++;
    }

    /** Client-side Age state that must exist for the sky / tint hooks to do anything. */
    private static void checkAgeClientState(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            failures.add("no client level in AGE_SETTLE");
            return;
        }
        AgeController controller = ClientAgeData.controllerFor(level);
        if (controller == null) {
            failures.add("client AgeController missing for " + level.dimension().identifier() + " (AgeData not synced?)");
            return;
        }
        long time = ClientAgeData.ageTime(level);
        float angle = controller.celestialAngle(time, 0f);
        var data = ClientAgeData.dataFor(level);
        StringBuilder celestials = new StringBuilder();
        for (var c : controller.celestials()) {
            celestials.append(c.kind()).append(c.providesLight() ? "(light)" : "").append('@')
                    .append(String.format("%.3f", c.getAltitudeAngle(time, 0f))).append(' ');
        }
        var attrs = level.environmentAttributes();
        float skyLight = attrs.getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_LIGHT_LEVEL);
        float skyFactor = attrs.getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_LIGHT_FACTOR,
                mc.player == null ? net.minecraft.world.phys.Vec3.ZERO : mc.player.position());
        Mystcraft.LOGGER.info("[clientcheck] client Age controller ok: seed {}, {} symbols, celestials [{}], age time {}, "
                        + "celestial angle {}, brightness {}, sky_light_level {}, sky_light_factor {}, skyDarken {}",
                data == null ? "?" : data.seed(), data == null ? -1 : data.symbols().size(), celestials.toString().trim(),
                time, angle, com.techbucketdivision.mystcraft.age.celestial.AgeDayCurves.brightness(angle), skyLight,
                skyFactor, level.getSkyDarken());
        if (angle < 0f || angle > 1f) failures.add("celestial angle out of range: " + angle);
        float expectedLight = 15f * com.techbucketdivision.mystcraft.age.celestial.AgeDayCurves.skyLightLevelFactor(angle);
        // vanilla weather layers darken on top of the Age curve (rain: blend towards 4 by 0.3125, thunder by 0.527)
        float thunder = level.getThunderLevel(1f), rain = level.getRainLevel(1f) - thunder;
        if (rain > 0f) expectedLight += (4f - expectedLight) * 0.3125f * rain;
        if (thunder > 0f) expectedLight += (4f - expectedLight) * 0.52734375f * thunder;
        Mystcraft.LOGGER.info("[clientcheck] Age weather: rain {}, thunder {}; expected sky light {}", rain, thunder, expectedLight);
        if (Math.abs(expectedLight - skyLight) > 1.5f) {
            failures.add("client sky light " + skyLight + " does not follow the Age's celestial angle " + angle
                    + " (expected ~" + expectedLight + ")");
        }
    }

    private static void next(Step s) {
        step = s;
        stepTicks = 0;
        Mystcraft.LOGGER.info("[clientcheck] -> {}", s);
    }

    private static void finish(Minecraft mc) {
        if (failures.isEmpty()) {
            Mystcraft.LOGGER.info("CLIENT SELFCHECK PASSED: {} screenshots, {} ticks", screenshots, ticks);
        } else {
            Mystcraft.LOGGER.error("CLIENT SELFCHECK FAILED: {} problems", failures.size());
            for (String f : failures) Mystcraft.LOGGER.error("[clientcheck]   - {}", f);
        }
        step = Step.DONE;
        mc.stop();
    }

    private static void fail(String reason) {
        failures.add(reason);
        Mystcraft.LOGGER.error("CLIENT SELFCHECK FAILED: {}", reason);
        step = Step.DONE;
        Minecraft.getInstance().stop();
    }
}
