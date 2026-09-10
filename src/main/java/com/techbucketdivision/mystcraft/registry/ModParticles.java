package com.techbucketdivision.mystcraft.registry;

import com.techbucketdivision.mystcraft.Mystcraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Particles: the dark "link" puff used on link start/end. */
public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Mystcraft.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LINK = PARTICLES.register("link", () -> new SimpleParticleType(false));
}
