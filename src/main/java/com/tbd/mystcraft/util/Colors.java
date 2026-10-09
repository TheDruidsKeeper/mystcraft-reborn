package com.tbd.mystcraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** Colour helpers. All components are 0..1 floats. */
public final class Colors {
    private Colors() {}

    public record RGB(float r, float g, float b) {
        public static final Codec<RGB> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("r").forGetter(RGB::r),
                Codec.FLOAT.fieldOf("g").forGetter(RGB::g),
                Codec.FLOAT.fieldOf("b").forGetter(RGB::b)).apply(i, RGB::new));

        public static final RGB WHITE = new RGB(1, 1, 1);
        public static final RGB BLACK = new RGB(0, 0, 0);

        public RGB lerp(RGB other, float f) {
            return new RGB(Mth.lerp(f, r, other.r), Mth.lerp(f, g, other.g), Mth.lerp(f, b, other.b));
        }

        public RGB average(RGB other) {
            return lerp(other, 0.5f);
        }

        public RGB scale(float f) {
            return new RGB(r * f, g * f, b * f);
        }

        public RGB clamp() {
            return new RGB(Mth.clamp(r, 0, 1), Mth.clamp(g, 0, 1), Mth.clamp(b, 0, 1));
        }

        /** Packed 0xRRGGBB. */
        public int toRGB() {
            return ARGB.color(channel(r), channel(g), channel(b));
        }

        /** Packed 0xAARRGGBB with full alpha. */
        public int toARGB() {
            return ARGB.opaque(toRGB());
        }

        public static RGB fromRGB(int rgb) {
            return new RGB(ARGB.red(rgb) / 255f, ARGB.green(rgb) / 255f, ARGB.blue(rgb) / 255f);
        }

        private static int channel(float v) {
            return Mth.clamp(Math.round(v * 255f), 0, 255);
        }
    }

    /** HSB to RGB, all inputs 0..1. */
    public static RGB hsb(float h, float s, float v) {
        return RGB.fromRGB(Mth.hsvToRgb(h - Mth.floor(h), Mth.clamp(s, 0, 1), Mth.clamp(v, 0, 1)));
    }
}
