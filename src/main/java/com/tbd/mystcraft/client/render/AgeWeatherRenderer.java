package com.tbd.mystcraft.client.render;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.CustomWeatherEffectRenderer;

/**
 * Age precipitation. Phase 1 keeps the vanilla rain/snow renderer (driven by the rain level pushed each tick from the
 * Age's {@code WeatherController}); per-biome wrapper temperatures are a Phase 2 item.
 */
public final class AgeWeatherRenderer implements CustomWeatherEffectRenderer {
    @Override
    public boolean renderSnowAndRain(LevelRenderState levelRenderState, WeatherRenderState weatherRenderState, MultiBufferSource bufferSource, Vec3 camPos) {
        return false;
    }
}
