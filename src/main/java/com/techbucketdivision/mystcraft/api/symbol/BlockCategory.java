package com.techbucketdivision.mystcraft.api.symbol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A usage category for block modifier symbols (what a block may be used as). Registerable by add-ons. */
public final class BlockCategory {
    private static final Map<String, BlockCategory> REGISTRY = new LinkedHashMap<>();

    public static final BlockCategory TERRAIN = register("terrain");
    public static final BlockCategory STRUCTURE = register("structure");
    public static final BlockCategory SOLID = register("solid");
    public static final BlockCategory ORGANIC = register("organic");
    public static final BlockCategory CRYSTAL = register("crystal");
    public static final BlockCategory SEA = register("sea");
    public static final BlockCategory FLUID = register("fluid");
    public static final BlockCategory GAS = register("gas");
    public static final BlockCategory ANY = register("any");

    private final String name;

    private BlockCategory(String name) {
        this.name = name;
    }

    public static synchronized BlockCategory register(String name) {
        return REGISTRY.computeIfAbsent(name, BlockCategory::new);
    }

    public static BlockCategory get(String name) {
        BlockCategory c = REGISTRY.get(name);
        if (c == null) throw new IllegalArgumentException("Unknown block category " + name);
        return c;
    }

    public static Map<String, BlockCategory> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
