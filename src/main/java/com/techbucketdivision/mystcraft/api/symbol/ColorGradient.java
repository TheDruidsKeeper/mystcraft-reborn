package com.techbucketdivision.mystcraft.api.symbol;

import com.techbucketdivision.mystcraft.util.Colors;

import java.util.ArrayList;
import java.util.List;

/**
 * A cyclic gradient of colours with per-entry interval lengths. {@link #getColor(float)} wraps around the total length
 * and linearly interpolates between neighbouring entries.
 */
public final class ColorGradient {
    private final List<Colors.RGB> colors = new ArrayList<>();
    private final List<Float> intervals = new ArrayList<>();
    private float totalLength;

    public ColorGradient() {}

    public ColorGradient(Colors.RGB single) {
        pushColor(single, 1.0f);
    }

    public void pushColor(Colors.RGB color, float interval) {
        colors.add(color);
        intervals.add(interval);
        totalLength += interval;
    }

    public void pushColor(Colors.RGB color) {
        pushColor(color, 1.0f);
    }

    /** Appends every entry of another gradient. */
    public void append(ColorGradient other) {
        for (int i = 0; i < other.colors.size(); i++) {
            pushColor(other.colors.get(i), other.intervals.get(i));
        }
    }

    public int size() {
        return colors.size();
    }

    public boolean isEmpty() {
        return colors.isEmpty();
    }

    public float totalLength() {
        return totalLength;
    }

    public List<Colors.RGB> colors() {
        return colors;
    }

    public List<Float> intervals() {
        return intervals;
    }

    /** Colour at position {@code t} (wraps around {@link #totalLength()}). */
    public Colors.RGB getColor(float t) {
        if (colors.isEmpty()) return Colors.RGB.WHITE;
        if (colors.size() == 1 || totalLength <= 0) return colors.getFirst();
        t = t % totalLength;
        if (t < 0) t += totalLength;
        int i = 0;
        while (i < intervals.size() && t >= intervals.get(i)) {
            t -= intervals.get(i);
            i++;
        }
        if (i >= colors.size()) i = 0;
        float interval = intervals.get(i);
        float f = interval <= 0 ? 0 : t / interval;
        Colors.RGB a = colors.get(i);
        Colors.RGB b = colors.get((i + 1) % colors.size());
        return a.lerp(b, f);
    }
}
