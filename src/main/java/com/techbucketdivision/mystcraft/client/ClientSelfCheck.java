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
 * <p>Script: create a fresh flat creative world → screenshot → {@code /myst-scene} (every renderable block) →
 * screenshot → {@code /myst-visit} (link into a brand-new Age through the real link path) → screenshots at day and
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

    private enum Step { TITLE, WORLD_LOADING, OVERWORLD_SETTLE, SCENE_OVERWORLD, VISIT_AGE, AGE_SETTLE, SCENE_AGE, NIGHT, DONE }

    private static final int OVERALL_BUDGET_TICKS = 20 * 60 * 6; // 6 minutes
    private static Step step = Step.TITLE;
    private static int ticks;          // total ticks since start
    private static int stepTicks;      // ticks in the current step
    private static int screenshots;
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
                        command(mc, "myst-scene");
                        next(Step.SCENE_OVERWORLD);
                    }
                }
                case SCENE_OVERWORLD -> {
                    if (stepTicks == 60) screenshot(mc, "02_scene_overworld");
                    if (stepTicks > 70) {
                        command(mc, "myst-visit Selfcheck Age");
                        next(Step.VISIT_AGE);
                    }
                }
                case VISIT_AGE -> {
                    ClientLevel level = mc.level;
                    if (level != null && AgeManager.isAge(level.dimension())) {
                        Mystcraft.LOGGER.info("[clientcheck] arrived in {} after {} ticks", level.dimension().identifier(), stepTicks);
                        next(Step.AGE_SETTLE);
                    } else if (stepTicks > 20 * 90) {
                        fail("never arrived in an Age (/myst-visit) - check [link] lines");
                    }
                }
                case AGE_SETTLE -> {
                    if (stepTicks == 100) {
                        checkAgeClientState(mc);
                        screenshot(mc, "03_age_arrival");
                    }
                    if (stepTicks > 110) {
                        command(mc, "myst-scene");
                        next(Step.SCENE_AGE);
                    }
                }
                case SCENE_AGE -> {
                    if (stepTicks == 60) screenshot(mc, "04_scene_age");
                    if (stepTicks > 70) {
                        command(mc, "myst-time set night");
                        next(Step.NIGHT);
                    }
                }
                case NIGHT -> {
                    if (stepTicks == 40) screenshot(mc, "05_age_night");
                    if (stepTicks > 60) finish(mc);
                }
                case DONE -> { }
            }
        } catch (RuntimeException e) {
            Mystcraft.LOGGER.error("[clientcheck] step {} threw", step, e);
            fail("exception in step " + step + ": " + e);
        }
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
        Mystcraft.LOGGER.info("[clientcheck] client Age controller ok: {} celestials, age time {}, celestial angle {}",
                controller.celestials().size(), time, angle);
        if (angle < 0f || angle > 1f) failures.add("celestial angle out of range: " + angle);
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
