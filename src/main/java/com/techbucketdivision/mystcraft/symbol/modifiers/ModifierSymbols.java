package com.techbucketdivision.mystcraft.symbol.modifiers;

import com.techbucketdivision.mystcraft.api.symbol.AgeDirector;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.ColorGradient;
import com.techbucketdivision.mystcraft.api.symbol.Modifier;
import com.techbucketdivision.mystcraft.api.symbol.ModifierUtils;
import com.techbucketdivision.mystcraft.symbol.symbols.BuiltinSymbols;
import com.techbucketdivision.mystcraft.symbol.symbols.SimpleSymbol;
import com.techbucketdivision.mystcraft.util.Colors;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.techbucketdivision.mystcraft.api.symbol.WordData.*;
import static com.techbucketdivision.mystcraft.symbol.grammar.GrammarRules.*;

/**
 * Modifier symbols (REQUIREMENTS §4.3.12): directions, phases, lengths, gradient, sunset colour and the 16 colours.
 * Display names are translation keys {@code symbol.mystcraft.<id>} (e.g. {@code symbol.mystcraft.mod_north} = "North
 * Direction", {@code symbol.mystcraft.mod_color_dark_green} = "Dark Green Color").
 */
public final class ModifierSymbols {
    private ModifierSymbols() {}

    /** Colour table: id suffix -> (display name, rgb). Order = original registration order. */
    public static final Map<String, ColorEntry> COLORS = new LinkedHashMap<>();

    public record ColorEntry(String display, Colors.RGB rgb) {}

    static {
        COLORS.put("maroon", new ColorEntry("Maroon", new Colors.RGB(0.50f, 0.00f, 0.00f)));
        COLORS.put("red", new ColorEntry("Red", new Colors.RGB(1.00f, 0.00f, 0.00f)));
        COLORS.put("olive", new ColorEntry("Olive", new Colors.RGB(0.50f, 0.50f, 0.00f)));
        COLORS.put("yellow", new ColorEntry("Yellow", new Colors.RGB(1.00f, 1.00f, 0.00f)));
        COLORS.put("dark_green", new ColorEntry("Dark Green", new Colors.RGB(0.00f, 0.50f, 0.00f)));
        COLORS.put("green", new ColorEntry("Green", new Colors.RGB(0.00f, 1.00f, 0.00f)));
        COLORS.put("teal", new ColorEntry("Teal", new Colors.RGB(0.00f, 0.50f, 0.50f)));
        COLORS.put("cyan", new ColorEntry("Cyan", new Colors.RGB(0.00f, 1.00f, 1.00f)));
        COLORS.put("navy", new ColorEntry("Navy", new Colors.RGB(0.00f, 0.00f, 0.50f)));
        COLORS.put("blue", new ColorEntry("Blue", new Colors.RGB(0.00f, 0.00f, 1.00f)));
        COLORS.put("purple", new ColorEntry("Purple", new Colors.RGB(0.50f, 0.00f, 0.50f)));
        COLORS.put("magenta", new ColorEntry("Magenta", new Colors.RGB(1.00f, 0.00f, 1.00f)));
        COLORS.put("black", new ColorEntry("Black", new Colors.RGB(0.00f, 0.00f, 0.00f)));
        COLORS.put("grey", new ColorEntry("Grey", new Colors.RGB(0.50f, 0.50f, 0.50f)));
        COLORS.put("silver", new ColorEntry("Silver", new Colors.RGB(0.75f, 0.75f, 0.75f)));
        COLORS.put("white", new ColorEntry("White", new Colors.RGB(1.00f, 1.00f, 1.00f)));
    }

    public static void registerAll() {
        BuiltinSymbols.rule(BuiltinSymbols.add(new AngleSymbol("mod_north", 0f, "North", CONTROL)), 1, ANGLE_BASIC);
        BuiltinSymbols.rule(BuiltinSymbols.add(new AngleSymbol("mod_east", 90f, "East", TRADITION)), 1, ANGLE_BASIC);
        BuiltinSymbols.rule(BuiltinSymbols.add(new AngleSymbol("mod_south", 180f, "South", CHAOS)), 1, ANGLE_BASIC);
        BuiltinSymbols.rule(BuiltinSymbols.add(new AngleSymbol("mod_west", 270f, "West", CHANGE)), 1, ANGLE_BASIC);

        BuiltinSymbols.rule(BuiltinSymbols.add(new PhaseSymbol("mod_end", 0f, "Nadir", REBIRTH)), 1, PHASE_BASIC);
        BuiltinSymbols.rule(BuiltinSymbols.add(new PhaseSymbol("mod_rising", 90f, "Rising", GROWTH)), 1, PHASE_BASIC);
        BuiltinSymbols.rule(BuiltinSymbols.add(new PhaseSymbol("mod_noon", 180f, "Zenith", HARMONY)), 1, PHASE_BASIC);
        BuiltinSymbols.rule(BuiltinSymbols.add(new PhaseSymbol("mod_setting", 270f, "Setting", FUTURE)), 1, PHASE_BASIC);

        BuiltinSymbols.rule(BuiltinSymbols.add(new LengthSymbol("mod_zero", 0.0f, "Zero Length", INHIBIT)), 2, PERIOD_BASIC);
        BuiltinSymbols.rule(BuiltinSymbols.add(new LengthSymbol("mod_half", 0.5f, "Half Length", STIMULATE)), 1, PERIOD_BASIC);
        BuiltinSymbols.rule(BuiltinSymbols.add(new LengthSymbol("mod_full", 1.0f, "Full Length", BALANCE)), 1, PERIOD_BASIC);
        BuiltinSymbols.rule(BuiltinSymbols.add(new LengthSymbol("mod_double", 2.0f, "Double Length", SACRIFICE)), 1, PERIOD_BASIC);

        BuiltinSymbols.rule(BuiltinSymbols.add(new GradientSymbol()), 1, GRADIENT_BASIC, COLOR, PERIOD);
        BuiltinSymbols.rule(BuiltinSymbols.add(new HorizonColorSymbol()), 2, SUNSET, SUNSET_EXT, GRADIENT);

        for (Map.Entry<String, ColorEntry> e : COLORS.entrySet()) {
            BuiltinSymbols.rule(BuiltinSymbols.add(new ColorSymbol("mod_color_" + e.getKey(), e.getValue())), 1, COLOR_BASIC);
        }
    }

    // --- angle / phase / length ----------------------------------------------------------------------------------

    /** Direction: sets or averages the {@code angle} modifier on the circle. */
    public static final class AngleSymbol extends SimpleSymbol {
        private final float degrees;
        private final String display;

        public AngleSymbol(String path, float degrees, String display, String fourthWord) {
            super(path, 0, MODIFIER, FLOW, MOTION, fourthWord);
            this.degrees = degrees;
            this.display = display;
        }

        public float degrees() { return degrees; }

        /** English fallback name ("North Direction"). */
        public String englishName() { return display + " Direction"; }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ModifierUtils.pushAngle(director, degrees);
        }
    }

    /** Phase: sets or averages the {@code phase} modifier on the circle. */
    public static final class PhaseSymbol extends SimpleSymbol {
        private final float degrees;
        private final String display;

        public PhaseSymbol(String path, float degrees, String display, String fourthWord) {
            super(path, 0, MODIFIER, CYCLE, SYSTEM, fourthWord);
            this.degrees = degrees;
            this.display = display;
        }

        public float degrees() { return degrees; }

        /** English fallback name ("Nadir Phase"). */
        public String englishName() { return display + " Phase"; }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ModifierUtils.pushPhase(director, degrees);
        }
    }

    /** Length: sets or linearly averages the {@code wavelength} factor. */
    public static final class LengthSymbol extends SimpleSymbol {
        private final float factor;
        private final String display;

        public LengthSymbol(String path, float factor, String display, String fourthWord) {
            super(path, 0, MODIFIER, TIME, SYSTEM, fourthWord);
            this.factor = factor;
            this.display = display;
        }

        public float factor() { return factor; }

        public String englishName() { return display; }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ModifierUtils.pushFactor(director, factor);
        }
    }

    // --- gradient / sunset ---------------------------------------------------------------------------------------

    /** Gradient: pushes the pending colour (interval = pending wavelength, default 1) onto the pending gradient. */
    public static final class GradientSymbol extends SimpleSymbol {
        public GradientSymbol() { super("mod_gradient", 1, MODIFIER, IMAGE, MERGE, WEAVE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ModifierUtils.pushGradient(director);
        }
    }

    /** Sunset Color: appends the popped gradient (or colour) to the {@code sunset} gradient (dangling 0). */
    public static final class HorizonColorSymbol extends SimpleSymbol {
        public HorizonColorSymbol() { super("color_horizon", 0, MODIFIER, IMAGE, CELESTIAL, CHANGE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ColorGradient sunset = ModifierUtils.popSunset(director);
            if (sunset == null) sunset = new ColorGradient();
            ColorGradient gradient = ModifierUtils.popGradientOrNull(director);
            if (gradient != null) sunset.append(gradient);
            director.setModifier(Modifier.SUNSET, new Modifier(sunset, 0));
        }
    }

    // --- colours -------------------------------------------------------------------------------------------------

    /** Colour: sets or averages the {@code color} modifier. Poem: Modifier, Image, Weave, {@code <id path>}. */
    public static final class ColorSymbol extends SimpleSymbol {
        private final ColorEntry entry;

        public ColorSymbol(String path, ColorEntry entry) {
            super(path, 0, MODIFIER, IMAGE, WEAVE, path);
            this.entry = entry;
        }

        public Colors.RGB rgb() { return entry.rgb(); }

        /** English fallback name ("Dark Green Color"). */
        public String englishName() { return entry.display() + " Color"; }

        @Override
        public Component displayName() {
            return Component.translatableWithFallback(descriptionId(), englishName());
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            ModifierUtils.pushColor(director, entry.rgb());
        }
    }

    /** Every colour symbol id path, in registration order. */
    public static java.util.List<String> colorIds() {
        return COLORS.keySet().stream().map(k -> "mod_color_" + k).toList();
    }

    /** Helper for docs/lang generation. */
    public static Map<String, String> englishNames() {
        Map<String, String> out = new LinkedHashMap<>();
        for (AgeSymbol s : java.util.List.of(
                new AngleSymbol("mod_north", 0, "North", CONTROL), new AngleSymbol("mod_east", 90, "East", TRADITION),
                new AngleSymbol("mod_south", 180, "South", CHAOS), new AngleSymbol("mod_west", 270, "West", CHANGE),
                new PhaseSymbol("mod_end", 0, "Nadir", REBIRTH), new PhaseSymbol("mod_rising", 90, "Rising", GROWTH),
                new PhaseSymbol("mod_noon", 180, "Zenith", HARMONY), new PhaseSymbol("mod_setting", 270, "Setting", FUTURE),
                new LengthSymbol("mod_zero", 0, "Zero Length", INHIBIT), new LengthSymbol("mod_half", 0.5f, "Half Length", STIMULATE),
                new LengthSymbol("mod_full", 1, "Full Length", BALANCE), new LengthSymbol("mod_double", 2, "Double Length", SACRIFICE))) {
            String name = switch (s) {
                case AngleSymbol a -> a.englishName();
                case PhaseSymbol p -> p.englishName();
                case LengthSymbol l -> l.englishName();
                default -> s.id().getPath();
            };
            out.put(s.descriptionId(), name);
        }
        out.put("symbol.mystcraft.mod_gradient", "Gradient");
        out.put("symbol.mystcraft.color_horizon", "Sunset Color");
        for (Map.Entry<String, ColorEntry> e : COLORS.entrySet()) {
            out.put("symbol.mystcraft.mod_color_" + e.getKey(), e.getValue().display() + " Color");
        }
        return out;
    }
}
