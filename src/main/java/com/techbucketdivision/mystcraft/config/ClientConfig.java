package com.techbucketdivision.mystcraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (rendering toggles). Registered by {@code MystcraftClient}. */
public final class ClientConfig {
    private ClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RENDER_LABELS = B.comment("Draw book names above stands, lecterns, receptacles and dropped books")
            .define("render.renderlabels", false);
    public static final ModConfigSpec.BooleanValue FAST_RAINBOWS = B.comment("Cache rainbow geometry")
            .define("render.fast_rainbows", true);

    public static final ModConfigSpec SPEC = B.build();
}
