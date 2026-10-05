package com.techbucketdivision.mystcraft.symbol.symbols;

import com.techbucketdivision.mystcraft.age.lighting.BrightLighting;
import com.techbucketdivision.mystcraft.age.lighting.DarkLighting;
import com.techbucketdivision.mystcraft.age.lighting.NormalLighting;
import com.techbucketdivision.mystcraft.api.symbol.AgeDirector;

import static com.techbucketdivision.mystcraft.api.symbol.WordData.*;

/** Lighting symbols (original spec §4.3.3). */
public final class LightingSymbols {
    private LightingSymbols() {}

    public static final int BRIGHT_INSTABILITY = 500;

    public static final class LightingNormal extends SimpleSymbol {
        public LightingNormal() { super("lighting_normal", 2, ETHEREAL, DYNAMIC, CYCLE, BALANCE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new NormalLighting());
        }
    }

    public static final class LightingBright extends SimpleSymbol {
        public LightingBright() { super("lighting_bright", 3, ETHEREAL, POWER, INFINITE, SPUR); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new BrightLighting());
        }

        @Override
        public int instabilityModifier(int count) {
            return BRIGHT_INSTABILITY;
        }
    }

    public static final class LightingDark extends SimpleSymbol {
        public LightingDark() { super("lighting_dark", 3, ETHEREAL, VOID, CONSTRAINT, INHIBIT); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new DarkLighting());
        }
    }
}
