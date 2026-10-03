package com.techbucketdivision.mystcraft.config;

import com.techbucketdivision.mystcraft.linking.InkEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Common config (REQUIREMENTS §13 core.cfg). */
public final class MystcraftConfig {
    private MystcraftConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SPAWN_METEOR_COMMAND = B.comment("Register the /myst-spawnmeteor command")
            .define("commands.spawnmeteor.enabled", false);
    public static final ModConfigSpec.BooleanValue RESPAWN_IN_AGES = B.comment("Players respawn inside Ages instead of being sent home")
            .define("respawning.respawnInAges", true);
    public static final ModConfigSpec.BooleanValue VILLAGE_DESK_GEN = B.comment("Archivist houses contain a Writing Desk")
            .define("generation.villageDeskGen", true);
    public static final ModConfigSpec.BooleanValue REQUIRE_UUID_TEST = B.comment("Strict dimension UUID check on login (players without a stored UUID are sent home)")
            .define("teleportation.requireUUIDTest", false);
    public static final ModConfigSpec.ConfigValue<String> HOME_DIMENSION = B.comment("Home dimension (Star Fissure target, ejection target)")
            .define("teleportation.homedim", "minecraft:overworld");
    public static final ModConfigSpec.BooleanValue ARCHIVIST_ENABLED = B.comment("Enable the Archivist villager profession")
            .define("ids.villager.archivist", true);
    public static final ModConfigSpec.BooleanValue LINKBOOK_RECIPE = B.comment("Enable the Unlinked Link Book recipe")
            .define("crafting.linkbook.enabled", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_LINK_EFFECTS = B.comment("Link properties the Ink Mixer may NOT produce")
            .defineListAllowEmpty("crafting.linkeffects.disabled", List.of(), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> INK_INGREDIENTS = B.comment(
                    "Ink Mixer ingredients, one entry per effect as \"effect=item\" (one ingredient per effect, one effect per ingredient).",
                    "Effects: intra_linking, intra_linking_only, relative, disarm, maintain_momentum, generate_platform, following.")
            .defineListAllowEmpty("inkmixer.ingredients", InkEffects.DEFAULT_INGREDIENTS, () -> "", o -> o instanceof String);
    public static final ModConfigSpec.ConfigValue<String> INK_CLEAR_INGREDIENT = B.comment("Item that clears every effect from the basin")
            .define("inkmixer.clearIngredient", InkEffects.DEFAULT_CLEAR_INGREDIENT);
    public static final ModConfigSpec.BooleanValue SERVER_LABELS = B.comment("Allow clients to render book name labels above stands/lecterns/receptacles/book entities")
            .define("render.serverLabels", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_SYMBOLS = B.comment("Symbol ids to disable, e.g. \"mystcraft:env_meteor\"")
            .defineListAllowEmpty("symbols.disabled", List.of(), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_INSTABILITIES = B.comment("Instability provider ids to disable, e.g. \"meteors\"")
            .defineListAllowEmpty("instability.disabled", List.of(), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.BooleanValue BASELINE_USE_CONFIGS = B.comment("Skip baseline profiling and use the values from the balance config")
            .define("baselining.useconfigs", true);
    public static final ModConfigSpec.IntValue BASELINE_TICKRATE = B.comment("Ticks between baseline profiling chunk generations")
            .defineInRange("baselining.tickrate.minimum", 5, 1, 200);

    public static final ModConfigSpec SPEC = B.build();

    public static ResourceKey<Level> homeDimension() {
        Identifier id = Identifier.tryParse(HOME_DIMENSION.get());
        return ResourceKey.create(Registries.DIMENSION, id == null ? Identifier.withDefaultNamespace("overworld") : id);
    }

    /** Symbol toggles are read before config load in some paths; treat unloaded config as "enabled". */
    public static boolean isSymbolEnabled(Identifier id) {
        try {
            return !DISABLED_SYMBOLS.get().contains(id.toString());
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean isInstabilityEnabled(String providerId) {
        try {
            return !DISABLED_INSTABILITIES.get().contains(providerId);
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean isLinkEffectEnabled(String property) {
        try {
            return !DISABLED_LINK_EFFECTS.get().contains(property);
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
