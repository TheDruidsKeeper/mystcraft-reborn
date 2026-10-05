package com.tbd.mystcraft.util;

import com.tbd.mystcraft.Mystcraft;
import net.minecraft.resources.Identifier;

/** Identifier helpers. */
public final class MystIds {
    private MystIds() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mystcraft.MOD_ID, path);
    }

    public static Identifier mc(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    /** Sanitises arbitrary text into a valid identifier path segment. */
    public static String pathSafe(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (char c : raw.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/') {
                sb.append(c);
            } else if (c == ' ' || c == ':') {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
