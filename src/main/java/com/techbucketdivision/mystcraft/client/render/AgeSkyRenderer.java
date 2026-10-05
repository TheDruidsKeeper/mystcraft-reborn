package com.tbd.mystcraft.client.render;

import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import org.joml.Matrix4fc;

/**
 * Age skybox. Phase 1: returns {@code false} so vanilla draws the sky using the values written into
 * {@link SkyRenderState} by {@code ClientGameEvents#onExtractLevelRenderState} (sun/moon/stars/sky colour).
 */
public final class AgeSkyRenderer implements CustomSkyboxRenderer {
    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState, Matrix4fc modelViewMatrix, Runnable setupFog) {
        // TODO Phase 2: full custom sky —
        //  * sky disc coloured by ColorKind.SKY, optional void/horizon planes per SkyOptions.drawVoid/drawHorizon;
        //  * every Celestial in AgeController#celestials(): suns/moons as textured quads rotated by angle()/altitude,
        //    starfields (STARS, STARS_TWINKLE layers, END_SKY) via RenderPipelines.STARS, RainbowCelestial as an HSV arc;
        //  * sunset bands from each celestial's horizonGradient().
        //  Requires building MeshData with Tesselator + RenderType.create(...) (RenderSetup API unverified in 26.1).
        return false;
    }
}
