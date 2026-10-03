package com.techbucketdivision.mystcraft.instability.effects;

import com.techbucketdivision.mystcraft.api.instability.InstabilityDirector;
import com.techbucketdivision.mystcraft.api.instability.InstabilityProvider;
import com.techbucketdivision.mystcraft.api.symbol.logic.EnvironmentalEffect;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Level-based potion card (REQUIREMENTS §6.4): one effect instance whose amplifier is {@code level - 1}. Each chunk
 * tick a random entity in the chunk receives the effect; non-global variants require the entity to see the sky;
 * "enemy" variants never target players.
 */
public final class PotionEffectProvider implements InstabilityProvider {
    private final boolean global;
    private final Holder<MobEffect> effect;
    private final int duration;
    private final boolean enemiesOnly;

    public PotionEffectProvider(boolean global, Holder<MobEffect> effect, int duration, boolean enemiesOnly) {
        this.global = global;
        this.effect = effect;
        this.duration = duration;
        this.enemiesOnly = enemiesOnly;
    }

    @Override
    public void addEffects(InstabilityDirector director, int level) {
        director.registerEffect(new PotionEffect(level));
    }

    /**
     * Only (re)apply when the effect is absent, weaker, or past half its duration: re-adding every chunk tick resets the
     * timer each tick, which spams effect-update packets and makes the HUD timer flicker.
     */
    public static boolean shouldApply(LivingEntity living, Holder<MobEffect> effect, int duration, int amplifier) {
        MobEffectInstance existing = living.getEffect(effect);
        return existing == null || existing.getAmplifier() < amplifier || existing.getDuration() <= duration / 2;
    }

    /** The environmental effect instance. */
    public final class PotionEffect implements EnvironmentalEffect {
        private final int amplifier;

        PotionEffect(int level) {
            this.amplifier = Math.max(0, level - 1);
        }

        @Override
        public void tick(ServerLevel world, LevelChunk chunk) {
            Entity entity = ChunkEntities.random(world, chunk);
            if (!(entity instanceof LivingEntity living)) return;
            if (enemiesOnly && entity instanceof Player) return;
            if (!global && !world.canSeeSky(entity.blockPosition())) return;
            if (!shouldApply(living, effect, duration, amplifier)) return;
            living.addEffect(new MobEffectInstance(effect, duration, amplifier));
        }
    }
}
