package com.techbucketdivision.mystcraft.api.symbol.logic;

/** Which colour a colour provider supplies. */
public enum ColorKind {
    SKY, FOG, CLOUD, GRASS, FOLIAGE, WATER;

    public boolean isDynamic() {
        return this == SKY || this == FOG || this == CLOUD;
    }
}
