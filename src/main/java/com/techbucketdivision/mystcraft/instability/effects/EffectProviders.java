package com.techbucketdivision.mystcraft.instability.effects;

import com.techbucketdivision.mystcraft.api.instability.InstabilityProvider;
import com.techbucketdivision.mystcraft.block.DecayType;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Factory methods for the built-in non-potion instability providers (REQUIREMENTS §6.4 / §6.5). */
public final class EffectProviders {
    private EffectProviders() {}

    private static BlockState decayState(DecayType type) {
        return ModBlocks.decay(type).get().defaultBlockState();
    }

    /** {@code EffectScorched(level)} — one instance, level = copies drawn. */
    public static InstabilityProvider scorched() {
        return (director, level) -> director.registerEffect(new ScorchedEffect(level));
    }

    public static InstabilityProvider scorchedGlobal() {
        return (director, level) -> director.registerEffect(new ScorchedEffect(level, true));
    }

    /** One {@link CrumbleEffect} per copy drawn. */
    public static InstabilityProvider crumble() {
        return (director, level) -> {
            for (int i = 0; i < level; i++) director.registerEffect(new CrumbleEffect());
        };
    }

    public static InstabilityProvider explosions() {
        return (director, level) -> {
            for (int i = 0; i < level; i++) director.registerEffect(new ExplosionsEffect());
        };
    }

    public static InstabilityProvider lightning() {
        return (director, level) -> {
            for (int i = 0; i < level; i++) director.registerEffect(new LightningEffect(null));
        };
    }

    /**
     * Frenzy (plan §10): no chunk effect of its own - while the card is dealt the creature rules make hostiles one
     * difficulty step harder and spawn 1.5× as often ({@code CreatureRules.frenzy}).
     */
    public static InstabilityProvider frenzy() {
        return (director, level) -> {};
    }

    public static InstabilityProvider meteors() {
        return (director, level) -> {
            for (int i = 0; i < level; i++) director.registerEffect(new MeteorEffect());
        };
    }

    /** Spreading decay (blue/red/purple): per copy a {@link DecayEffect} plus extra ticks for that decay state. */
    public static InstabilityProvider decay(DecayType type, int minY, @Nullable Integer maxY) {
        return (director, level) -> {
            for (int i = 0; i < level; i++) {
                director.registerEffect(new DecayEffect(type, minY, maxY));
                director.registerEffect(new ExtraTicksEffect(decayState(type)));
            }
        };
    }

    /** White decay: one extra-ticks effect globally, plus per copy a decay effect (20..surface) and extra ticks. */
    public static InstabilityProvider whiteDecay() {
        return (director, level) -> {
            director.registerEffect(new ExtraTicksEffect(decayState(DecayType.WHITE)));
            for (int i = 0; i < level; i++) {
                director.registerEffect(new DecayEffect(DecayType.WHITE, 20, null));
                director.registerEffect(new ExtraTicksEffect(decayState(DecayType.WHITE)));
            }
        };
    }

    /** Black decay: 0..12 (mapped from the old y range), bans air and fluids. Registered but not dealt. */
    public static InstabilityProvider blackDecay() {
        return (director, level) -> {
            for (int i = 0; i < level; i++) {
                director.registerEffect(new DecayEffect(DecayType.BLACK, 0, 12).banAirAndFluids());
                director.registerEffect(new ExtraTicksEffect(decayState(DecayType.BLACK)));
            }
        };
    }
}
