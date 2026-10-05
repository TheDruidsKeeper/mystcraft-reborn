package com.tbd.mystcraft.api.symbol.logic;

/** Sky rendering options collected by the director (mutable during construction, read by the client). */
public final class SkyOptions {
    public float cloudHeight = 192f;
    public float horizon = 63f;
    public boolean drawHorizon = true;
    public boolean drawVoid = true;
    public boolean pvpEnabled = true;
}
