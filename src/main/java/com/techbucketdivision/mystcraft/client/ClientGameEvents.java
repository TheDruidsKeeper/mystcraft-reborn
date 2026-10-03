package com.techbucketdivision.mystcraft.client;

import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.celestial.AgeDayCurves;
import com.techbucketdivision.mystcraft.api.symbol.logic.Celestial;
import com.techbucketdivision.mystcraft.api.symbol.logic.ColorKind;
import com.techbucketdivision.mystcraft.api.symbol.logic.WeatherController;
import com.techbucketdivision.mystcraft.client.render.AgeSkyMath;
import com.techbucketdivision.mystcraft.client.render.tint.AgeBiomeTints;
import com.techbucketdivision.mystcraft.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Game-bus listeners: Phase-1 sky (vanilla renderer fed from the Age controller), fog colour, local Age clock and
 * weather strengths, cache clearing on logout.
 */
public final class ClientGameEvents {
    private ClientGameEvents() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(ClientGameEvents::onExtractLevelRenderState);
        gameBus.addListener(ClientGameEvents::onComputeFogColor);
        gameBus.addListener(ClientGameEvents::onClientTickPost);
        gameBus.addListener(ClientGameEvents::onLoggingOut);
        gameBus.addListener(AgeClientEnvironment::onLevelLoad);
    }

    // --- sky (Phase 1) -------------------------------------------------------------------------------------------

    private static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        AgeController controller = ClientAgeData.controllerFor(level);
        if (controller == null) return;
        float partial = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long time = ClientAgeData.ageTime(level);
        float angle = controller.celestialAngle(time, partial);
        float rain = level.getRainLevel(partial);
        float biomeTemp = biomeTemperature(level, event.getCamera().position());

        LevelRenderState state = event.getRenderState();
        SkyRenderState sky = state.skyRenderState;

        Celestial sun = AgeSkyMath.first(controller, Celestial.Kind.SUN);
        Celestial moon = AgeSkyMath.first(controller, Celestial.Kind.MOON);
        Celestial stars = AgeSkyMath.firstStars(controller);

        // SkyRenderState angles are RADIANS: vanilla SkyRenderer.extractRenderState multiplies the degree-valued
        // SUN_ANGLE/MOON_ANGLE/STAR_ANGLE attributes by PI/180 before storing them. Writing degrees here made the sun
        // and moon orbit ~57x too fast.
        sky.sunAngle = AgeSkyMath.angleDegrees(sun, time, partial, angle) * Mth.DEG_TO_RAD;
        sky.moonAngle = AgeSkyMath.angleDegrees(moon, time, partial, angle + 0.5f) * Mth.DEG_TO_RAD;
        sky.starAngle = AgeSkyMath.angleDegrees(stars, time, partial, angle) * Mth.DEG_TO_RAD;
        sky.starBrightness = stars == null ? 0f : AgeSkyMath.starBrightness(angle, rain);
        sky.rainBrightness = 1.0f - rain; // same formula as vanilla SkyRenderer.extractRenderState
        sky.moonPhase = moonPhase(moon == null ? 0 : moon.phase(time));
        sky.sunriseAndSunsetColor = AgeSkyMath.sunriseColor(sun, angle);

        Colors.RGB skyColor = AgeSkyMath.color(controller, ColorKind.SKY, time, partial, angle, biomeTemp);
        if (skyColor != null) sky.skyColor = AgeDayCurves.skyColor(AgeSkyMath.rgb(skyColor), angle);
        Colors.RGB cloudColor = AgeSkyMath.color(controller, ColorKind.CLOUD, time, partial, angle, biomeTemp);
        if (cloudColor != null) state.cloudColor = AgeDayCurves.cloudColor(AgeSkyMath.rgb(cloudColor), angle);
        state.cloudHeight = controller.sky().cloudHeight;
        sky.shouldRenderDarkDisc = controller.sky().drawVoid && sky.shouldRenderDarkDisc;
    }

    /** Vanilla phase index 0 (full) .. 7 → {@link MoonPhase}. */
    private static MoonPhase moonPhase(int phase) {
        MoonPhase[] values = MoonPhase.values();
        if (values.length == 0) return MoonPhase.values()[0];
        return values[Math.floorMod(phase, values.length)];
    }

    private static float biomeTemperature(ClientLevel level, Vec3 pos) {
        try {
            BlockPos bp = BlockPos.containing(pos);
            return level.getBiome(bp).value().getBaseTemperature(); // API_CHEATSHEET I2: Biome#getBaseTemperature() verified
        } catch (RuntimeException e) {
            return 0.5f;
        }
    }

    // --- fog -----------------------------------------------------------------------------------------------------

    private static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        AgeController controller = ClientAgeData.controllerFor(level);
        if (controller == null) return;
        float partial = (float) event.getPartialTick();
        long time = ClientAgeData.ageTime(level);
        float angle = controller.celestialAngle(time, partial);
        Colors.RGB fog = AgeSkyMath.color(controller, ColorKind.FOG, time, partial, angle, biomeTemperature(level, event.getCamera().position()));
        if (fog == null) return;
        int darkened = AgeDayCurves.fogColor(AgeSkyMath.rgb(fog), angle);
        fog = new Colors.RGB(net.minecraft.util.ARGB.redFloat(darkened), net.minecraft.util.ARGB.greenFloat(darkened), net.minecraft.util.ARGB.blueFloat(darkened));
        event.setRed(fog.r());
        event.setGreen(fog.g());
        event.setBlue(fog.b());
    }

    // --- ticking ---------------------------------------------------------------------------------------------------

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        PanelImages.tick(mc);
        ClientAgeData.tick(level);
        AgeBiomeTints.update(level);
        AgeController controller = ClientAgeData.controllerFor(level);
        if (controller == null) return;
        WeatherController weather = controller.weather();
        if (weather == null) return;
        try {
            weather.updateRaining(level);
            level.setRainLevel(weather.getRainStrength());
            level.setThunderLevel(weather.getThunderStrength());
        } catch (RuntimeException ignored) {
            // never let a weather bug kill the client tick
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAgeData.clear();
        AgeBiomeTints.clear();
        PanelImages.clear();
    }
}
