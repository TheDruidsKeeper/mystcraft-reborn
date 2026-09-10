package com.techbucketdivision.mystcraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Balance config (REQUIREMENTS §13 balance.cfg): instability difficulty and baseline "free ore" values. */
public final class BalanceConfig {
    private BalanceConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue INSTABILITY_ENABLED = B.comment("Master instability switch")
            .define("instability.global.enabled", true);
    public static final ModConfigSpec.IntValue INSTABILITY_DIFFICULTY = B.comment("0..3 -> score multiplier 0.25 / 0.5 / 1.0 / 1.75")
            .defineInRange("instability.global.difficulty", 2, 0, 3);

    public static final ModConfigSpec.IntValue BASE_COAL = B.defineInRange("baselining.coal_ore", 300, 0, 1_000_000);
    public static final ModConfigSpec.IntValue BASE_DIAMOND = B.defineInRange("baselining.diamond_ore", 1000, 0, 1_000_000);
    public static final ModConfigSpec.IntValue BASE_EMERALD = B.defineInRange("baselining.emerald_ore", 100, 0, 1_000_000);
    public static final ModConfigSpec.IntValue BASE_GLOWSTONE = B.defineInRange("baselining.glowstone", 0, 0, 1_000_000);
    public static final ModConfigSpec.IntValue BASE_GOLD = B.defineInRange("baselining.gold_ore", 500, 0, 1_000_000);
    public static final ModConfigSpec.IntValue BASE_IRON = B.defineInRange("baselining.iron_ore", 500, 0, 1_000_000);
    public static final ModConfigSpec.IntValue BASE_LAPIS = B.defineInRange("baselining.lapis_ore", 100, 0, 1_000_000);
    public static final ModConfigSpec.IntValue BASE_QUARTZ = B.defineInRange("baselining.nether_quartz_ore", 0, 0, 1_000_000);
    public static final ModConfigSpec.IntValue BASE_REDSTONE = B.defineInRange("baselining.redstone_ore", 600, 0, 1_000_000);
    public static final ModConfigSpec.IntValue BASE_CRYSTAL = B.defineInRange("baselining.crystal", 0, 0, 1_000_000);

    public static final ModConfigSpec SPEC = B.build();

    public static float difficultyMultiplier() {
        return switch (INSTABILITY_DIFFICULTY.get()) {
            case 0 -> 0.25f;
            case 1 -> 0.5f;
            case 3 -> 1.75f;
            default -> 1.0f;
        };
    }
}
