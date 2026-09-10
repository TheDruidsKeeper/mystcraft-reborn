package com.techbucketdivision.mystcraft.symbol.symbols;

import com.techbucketdivision.mystcraft.age.celestial.RainbowCelestial;
import com.techbucketdivision.mystcraft.api.symbol.AgeDirector;
import com.techbucketdivision.mystcraft.api.symbol.ColorGradient;
import com.techbucketdivision.mystcraft.api.symbol.ModifierUtils;
import com.techbucketdivision.mystcraft.api.symbol.logic.ColorKind;
import com.techbucketdivision.mystcraft.symbol.color.FixedStaticColor;
import com.techbucketdivision.mystcraft.symbol.color.GradientDynamicColor;
import com.techbucketdivision.mystcraft.symbol.color.NaturalDynamicColor;
import com.techbucketdivision.mystcraft.util.Colors;

import static com.techbucketdivision.mystcraft.api.symbol.WordData.*;

/** Visual / colour symbols (REQUIREMENTS §4.3.1). */
public final class ColorSymbols {
    private ColorSymbols() {}

    private static final Colors.RGB FOG_DEFAULT = new Colors.RGB(0.7529412f, 0.8470588f, 1.0f);

    /** Cloud Color: dynamic CLOUD colour = gradient(time/12000); default white. */
    public static final class ColorCloud extends SimpleSymbol {
        public ColorCloud() { super("color_cloud", 1, IMAGE, ENTROPY, BELIEVE, WEAVE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ColorGradient gradient = ModifierUtils.popGradient(director, Colors.RGB.WHITE);
            director.registerInterface(GradientDynamicColor.cloud(gradient));
        }
    }

    /** Natural Cloud Color: constant white. */
    public static final class ColorCloudNatural extends SimpleSymbol {
        public ColorCloudNatural() { super("color_cloud_natural", 1, IMAGE, ENTROPY, BELIEVE, NATURE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(NaturalDynamicColor.cloud());
        }
    }

    /** Fog Color: dynamic FOG colour = gradient(time/12000); pure black becomes (0.0001, 0.0001, 0.0001). */
    public static final class ColorFog extends SimpleSymbol {
        public ColorFog() { super("color_fog", 1, IMAGE, ENTROPY, EXPLORE, WEAVE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ColorGradient gradient = ModifierUtils.popGradient(director, FOG_DEFAULT);
            director.registerInterface(GradientDynamicColor.fog(gradient));
        }
    }

    /** Natural Fog Color: vanilla formula. */
    public static final class ColorFogNatural extends SimpleSymbol {
        public ColorFogNatural() { super("color_fog_natural", 1, IMAGE, ENTROPY, EXPLORE, NATURE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(NaturalDynamicColor.fog());
        }
    }

    /** Foliage Color: static FOLIAGE colour = pending colour (null if none). */
    public static final class ColorFoliage extends SimpleSymbol {
        public ColorFoliage() { super("color_foliage", 1, IMAGE, GROWTH, ELEVATE, WEAVE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new FixedStaticColor(ColorKind.FOLIAGE, ModifierUtils.popColor(director)));
        }
    }

    /** Natural Foliage Color: biome colour. */
    public static final class ColorFoliageNatural extends SimpleSymbol {
        public ColorFoliageNatural() { super("color_foliage_natural", 1, IMAGE, GROWTH, ELEVATE, NATURE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(FixedStaticColor.natural(ColorKind.FOLIAGE));
        }
    }

    /** Grass Color: static GRASS colour. */
    public static final class ColorGrass extends SimpleSymbol {
        public ColorGrass() { super("color_grass", 1, IMAGE, GROWTH, RESILIENCE, WEAVE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new FixedStaticColor(ColorKind.GRASS, ModifierUtils.popColor(director)));
        }
    }

    /** Natural Grass Color: biome colour. */
    public static final class ColorGrassNatural extends SimpleSymbol {
        public ColorGrassNatural() { super("color_grass_natural", 1, IMAGE, GROWTH, RESILIENCE, NATURE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(FixedStaticColor.natural(ColorKind.GRASS));
        }
    }

    /** Sky Color: dynamic SKY colour = gradient(time/12000) x daylight factor; default white. */
    public static final class ColorSky extends SimpleSymbol {
        public ColorSky() { super("color_sky", 1, IMAGE, CELESTIAL, HARMONY, WEAVE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ColorGradient gradient = ModifierUtils.popGradient(director, Colors.RGB.WHITE);
            director.registerInterface(GradientDynamicColor.sky(gradient));
        }
    }

    /** Natural Sky Color: vanilla temperature-based HSB sky. */
    public static final class ColorSkyNatural extends SimpleSymbol {
        public ColorSkyNatural() { super("color_sky_natural", 1, IMAGE, CELESTIAL, HARMONY, NATURE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(NaturalDynamicColor.sky());
        }
    }

    /** Night Sky Color: as Sky Color but scaled by (1 - daylight factor). */
    public static final class ColorSkyNight extends SimpleSymbol {
        public ColorSkyNight() { super("color_sky_night", 1, IMAGE, CELESTIAL, CONTRADICT, WEAVE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ColorGradient gradient = ModifierUtils.popGradient(director, Colors.RGB.WHITE);
            director.registerInterface(GradientDynamicColor.nightSky(gradient));
        }
    }

    /** Water Color: static WATER colour. */
    public static final class ColorWater extends SimpleSymbol {
        public ColorWater() { super("color_water", 1, IMAGE, FLOW, CONSTRAINT, WEAVE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new FixedStaticColor(ColorKind.WATER, ModifierUtils.popColor(director)));
        }
    }

    /** Natural Water Color: biome water colour. */
    public static final class ColorWaterNatural extends SimpleSymbol {
        public ColorWaterNatural() { super("color_water_natural", 1, IMAGE, FLOW, CONSTRAINT, NATURE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(FixedStaticColor.natural(ColorKind.WATER));
        }
    }

    /** Boundless Sky: horizon 0, no horizon band, no void plane. */
    public static final class NoHorizon extends SimpleSymbol {
        public NoHorizon() { super("no_horizon", 1, CELESTIAL, INHIBIT, IMAGE, VOID); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.setHorizon(0);
            director.setDrawHorizon(false);
            director.setDrawVoid(false);
        }
    }

    /** Rainbow: celestial doodad rotated by -angle (random when none). */
    public static final class Rainbow extends SimpleSymbol {
        public Rainbow() { super("rainbow", 1, CELESTIAL, IMAGE, HARMONY, BALANCE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Float angle = ModifierUtils.popAngle(director);
            director.registerInterface(new RainbowCelestial(seed, angle));
        }
    }
}
