package com.tbd.mystcraft.symbol.symbols;

import com.tbd.mystcraft.age.celestial.EndSkyCelestial;
import com.tbd.mystcraft.age.celestial.MoonCelestial;
import com.tbd.mystcraft.age.celestial.StarfieldCelestial;
import com.tbd.mystcraft.age.celestial.SunCelestial;
import com.tbd.mystcraft.age.celestial.TwinkleStarfieldCelestial;
import com.tbd.mystcraft.api.symbol.AgeDirector;
import com.tbd.mystcraft.api.symbol.ColorGradient;
import com.tbd.mystcraft.api.symbol.ModifierUtils;
import com.tbd.mystcraft.util.Colors;

import static com.tbd.mystcraft.api.symbol.WordData.*;

/** Sun / moon / starfield symbols (original spec §4.3.2). Dark variants are {@link DummySymbol}s. */
public final class CelestialSymbols {
    private CelestialSymbols() {}

    /** Normal Sun: pops wavelength, angle, phase, sunset. Provides light. */
    public static final class SunNormal extends SimpleSymbol {
        public SunNormal() { super("sun_normal", 2, CELESTIAL, IMAGE, STIMULATE, ENERGY); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Float period = ModifierUtils.popFactor(director);
            Float angle = ModifierUtils.popAngle(director);
            Float phase = ModifierUtils.popPhase(director);
            ColorGradient sunset = ModifierUtils.popSunset(director);
            director.registerInterface(new SunCelestial(seed, period, angle, phase, sunset));
        }
    }

    /** Normal Moon: like the sun with a longer default period; no light. */
    public static final class MoonNormal extends SimpleSymbol {
        public MoonNormal() { super("moon_normal", 1, CELESTIAL, IMAGE, CYCLE, WISDOM); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Float period = ModifierUtils.popFactor(director);
            Float angle = ModifierUtils.popAngle(director);
            Float phase = ModifierUtils.popPhase(director);
            ColorGradient sunset = ModifierUtils.popSunset(director);
            director.registerInterface(new MoonCelestial(seed, period, angle, phase, sunset));
        }
    }

    /** Normal Stars: pops wavelength, angle, gradient (default white). */
    public static final class StarsNormal extends SimpleSymbol {
        public StarsNormal() { super("stars_normal", 1, CELESTIAL, HARMONY, ETHEREAL, ORDER); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Float period = ModifierUtils.popFactor(director);
            Float angle = ModifierUtils.popAngle(director);
            ColorGradient gradient = ModifierUtils.popGradient(director, Colors.RGB.WHITE);
            director.registerInterface(new StarfieldCelestial(seed, period, angle, gradient));
        }
    }

    /** Twinkling Stars. */
    public static final class StarsTwinkle extends SimpleSymbol {
        public StarsTwinkle() { super("stars_twinkle", 1, CELESTIAL, HARMONY, ETHEREAL, ENTROPY); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Float period = ModifierUtils.popFactor(director);
            Float angle = ModifierUtils.popAngle(director);
            ColorGradient gradient = ModifierUtils.popGradient(director, Colors.RGB.WHITE);
            director.registerInterface(new TwinkleStarfieldCelestial(seed, period, angle, gradient));
        }
    }

    /** Ender Starfield: End sky box tinted by the gradient (default 0x282828); horizon 0, no horizon/void. */
    public static final class StarsEndSky extends SimpleSymbol {
        public StarsEndSky() { super("stars_end_sky", 1, CELESTIAL, IMAGE, CHAOS, WEAVE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ColorGradient gradient = ModifierUtils.popGradient(director, EndSkyCelestial.DEFAULT_COLOR);
            director.registerInterface(new EndSkyCelestial(gradient));
            director.setHorizon(0);
            director.setDrawHorizon(false);
            director.setDrawVoid(false);
        }
    }

    public static DummySymbol sunDark() {
        return new DummySymbol("sun_dark", 1, CELESTIAL, VOID, INHIBIT, ENERGY);
    }

    public static DummySymbol moonDark() {
        return new DummySymbol("moon_dark", 1, CELESTIAL, VOID, INHIBIT, WISDOM);
    }

    public static DummySymbol starsDark() {
        return new DummySymbol("stars_dark", 1, CELESTIAL, VOID, INHIBIT, ORDER);
    }
}
