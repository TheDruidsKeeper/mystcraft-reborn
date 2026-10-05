package com.tbd.mystcraft.instability.decay;

import com.tbd.mystcraft.block.DecayType;

import java.util.EnumMap;
import java.util.Map;

/** Registry of {@link DecayHandler}s per {@link DecayType}. Green and yellow fall back to the black handler. */
public final class DecayHandlers {
    private DecayHandlers() {}

    private static final Map<DecayType, DecayHandler> HANDLERS = new EnumMap<>(DecayType.class);

    static {
        register(new BlackDecay());
        register(new RedDecay());
        register(new BlueDecay());
        register(new PurpleDecay());
        register(new WhiteDecay());
    }

    public static synchronized void register(DecayHandler handler) {
        HANDLERS.put(handler.type(), handler);
    }

    public static DecayHandler get(DecayType type) {
        DecayHandler h = HANDLERS.get(type);
        return h != null ? h : HANDLERS.get(DecayType.BLACK);
    }
}
