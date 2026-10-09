package com.tbd.mystcraft.symbol.symbols;

import com.tbd.mystcraft.api.symbol.AgeDirector;
import com.tbd.mystcraft.api.symbol.ColorGradient;
import com.tbd.mystcraft.api.symbol.ModifierUtils;
import com.tbd.mystcraft.instability.effects.ExplosionsEffect;
import com.tbd.mystcraft.instability.effects.ExtraTicksEffect;
import com.tbd.mystcraft.instability.effects.LightningEffect;
import com.tbd.mystcraft.instability.effects.MeteorEffect;
import com.tbd.mystcraft.instability.effects.ScorchedEffect;

import static com.tbd.mystcraft.api.symbol.WordData.*;

/** Environmental effect symbols (original spec §4.3.10). Grammar rank null: never generated randomly. */
public final class EffectSymbols {
    private EffectSymbols() {}

    public static final int ACCELERATED_INSTABILITY = 1000;
    public static final int EXPLOSIONS_INSTABILITY = -500;
    public static final int LIGHTNING_INSTABILITY = -500;
    public static final int METEORS_INSTABILITY = -1000;
    public static final int SCORCHED_INSTABILITY = -500;

    /** Accelerated: 3 extra random block ticks per ticking section. */
    public static final class Accelerated extends SimpleSymbol {
        public Accelerated() { super("env_accelerated", 3, ENVIRONMENT, DYNAMIC, CHANGE, SPUR); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new ExtraTicksEffect(null));
        }

        @Override
        public int instabilityModifier(int count) {
            return ACCELERATED_INSTABILITY;
        }
    }

    /** Spontaneous Explosions. */
    public static final class Explosions extends SimpleSymbol {
        public Explosions() { super("env_explosions", 3, ENVIRONMENT, SACRIFICE, POWER, FORCE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new ExplosionsEffect());
        }

        @Override
        public int instabilityModifier(int count) {
            return EXPLOSIONS_INSTABILITY;
        }
    }

    /** Lightning: coloured bolts when a gradient/colour was written. -500 for the first occurrence only. */
    public static final class Lightning extends SimpleSymbol {
        public Lightning() { super("env_lightning", 3, ENVIRONMENT, SACRIFICE, POWER, ENERGY); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ColorGradient gradient = ModifierUtils.popGradientOrNull(director);
            if (gradient != null && gradient.isEmpty()) gradient = null;
            director.registerInterface(new LightningEffect(gradient));
        }

        @Override
        public int instabilityModifier(int count) {
            return count > 1 ? 0 : LIGHTNING_INSTABILITY;
        }
    }

    /** Meteors. */
    public static final class Meteors extends SimpleSymbol {
        public Meteors() { super("env_meteors", 3, ENVIRONMENT, SACRIFICE, POWER, MOMENTUM); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new MeteorEffect());
        }

        @Override
        public int instabilityModifier(int count) {
            return METEORS_INSTABILITY;
        }
    }

    /** Scorched Surface (level 1). -500 for the first occurrence only. */
    public static final class Scorched extends SimpleSymbol {
        public Scorched() { super("env_scorched", 3, ENVIRONMENT, SACRIFICE, POWER, CHAOS); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new ScorchedEffect(1));
        }

        @Override
        public int instabilityModifier(int count) {
            return count > 1 ? 0 : SCORCHED_INSTABILITY;
        }
    }
}
