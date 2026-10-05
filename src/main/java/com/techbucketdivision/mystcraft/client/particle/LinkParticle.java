package com.tbd.mystcraft.client.particle;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * The dark "link" puff. Phase 1 reuses the vanilla smoke particle behaviour (dark grey, slow rise) with the sprite set
 * bound to {@code assets/mystcraft/particles/link.json}; a dedicated particle class with the original 0.3 grey tint and
 * random drift is a Phase 2 refinement.
 */
public final class LinkParticle {
    private LinkParticle() {}

    /** {@code ParticleResources.SpriteParticleRegistration} factory. */
    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return new SmokeParticle.Provider(sprites);
    }
}
