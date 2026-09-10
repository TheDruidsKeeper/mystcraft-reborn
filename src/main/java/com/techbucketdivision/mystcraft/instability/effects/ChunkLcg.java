package com.techbucketdivision.mystcraft.instability.effects;

import java.util.Random;

/** The vanilla-style linear congruential generator the original effects used to pick chunk-local coordinates. */
final class ChunkLcg {
    private int state = new Random().nextInt();

    /** Advances and returns {@code updateLCG >> 2}: bits 0-3 = x, 8-11 = z, 16-23 = y (0..255), 16-19 = section y. */
    int next() {
        state = state * 3 + 1013904223;
        return state >> 2;
    }

    static int localX(int coords) {
        return coords & 15;
    }

    static int localZ(int coords) {
        return coords >> 8 & 15;
    }

    static int y255(int coords) {
        return coords >> 16 & 255;
    }

    static int localY(int coords) {
        return coords >> 16 & 15;
    }
}
