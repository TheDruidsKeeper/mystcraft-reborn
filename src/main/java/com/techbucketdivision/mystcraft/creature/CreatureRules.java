package com.techbucketdivision.mystcraft.creature;

import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.api.symbol.CreatureDifficulty;
import com.techbucketdivision.mystcraft.api.symbol.CreatureGroup;
import com.techbucketdivision.mystcraft.api.symbol.logic.CreatureController;
import com.techbucketdivision.mystcraft.instability.InstabilityController;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules of the creature controllers (plan §10): how biome spawn lists, mob caps and spawned hostiles are changed
 * by an Age's {@link CreatureController}s and by the Frenzy instability card. The hooks (chunk generator, spawn
 * events, the Age ticker) call these; the game tests call them directly.
 */
public final class CreatureRules {
    private CreatureRules() {}

    /** Instability card id: while dealt, hostiles are one step harder and spawn ×1.5 (plan §10.2). */
    public static final String FRENZY_CARD = "frenzy";
    public static final float FRENZY_RATE = 1.5f;
    /** Vanilla: the spawn area of one player is 17×17 chunks. */
    public static final int SPAWN_AREA_CHUNKS = 17 * 17;

    private static final Identifier HEALTH_MODIFIER = MystIds.id("creature_difficulty_health");
    private static final Identifier DAMAGE_MODIFIER = MystIds.id("creature_difficulty_damage");

    /** Whether the Frenzy card is dealt in the level's Age. */
    public static boolean frenzy(ServerLevel level) {
        InstabilityController instability = InstabilityController.get(level);
        return instability != null && instability.providerLevels().getOrDefault(FRENZY_CARD, 0) > 0;
    }

    /** The controller for a group in an Age level, or {@code null} (not an Age, or the group is not written). */
    public static @Nullable CreatureController controller(ServerLevel level, CreatureGroup group) {
        AgeController age = AgeControllers.server(level);
        return age == null ? null : age.creatures(group);
    }

    /** Effective spawn-weight factor for a group: the controller's rate, ×{@link #FRENZY_RATE} for hostiles in frenzy. */
    public static float rate(@Nullable CreatureController controller, CreatureGroup group, boolean frenzy) {
        float rate = controller == null ? 1f : controller.rate();
        if (frenzy && group == CreatureGroup.HOSTILE) rate *= FRENZY_RATE;
        return rate;
    }

    /** Effective difficulty of hostiles: the controller's (NORMAL without one), one step harder in frenzy. */
    public static CreatureDifficulty difficulty(@Nullable CreatureController hostile, boolean frenzy) {
        CreatureDifficulty base = hostile == null ? CreatureDifficulty.NORMAL : hostile.difficulty();
        return frenzy ? base.harder() : base;
    }

    /** Rescales a biome spawn list by the groups' rates; entries whose weight rounds to 0 are dropped. */
    public static WeightedList<MobSpawnSettings.SpawnerData> scaleSpawns(WeightedList<MobSpawnSettings.SpawnerData> spawns,
                                                                       java.util.function.Function<CreatureGroup, @Nullable CreatureController> controllers,
                                                                       boolean frenzy) {
        List<Weighted<MobSpawnSettings.SpawnerData>> out = new ArrayList<>();
        boolean changed = false;
        for (Weighted<MobSpawnSettings.SpawnerData> entry : spawns.unwrap()) {
            CreatureGroup group = CreatureGroup.of(entry.value().type());
            if (group == null) {
                out.add(entry);
                continue;
            }
            float rate = rate(controllers.apply(group), group, frenzy);
            if (rate == 1f) {
                out.add(entry);
                continue;
            }
            changed = true;
            int weight = Math.round(entry.weight() * rate);
            if (weight > 0) out.add(new Weighted<>(entry.value(), weight));
        }
        return changed ? WeightedList.of(out) : spawns;
    }

    /** Mobs of the group a spawn area may hold: vanilla cap of the category × loaded spawn chunks / 289 × cap factor. */
    public static int allowed(MobCategory category, int spawnableChunks, @Nullable CreatureController controller) {
        float factor = controller == null ? 1f : controller.capFactor();
        return Math.round(category.getMaxInstancesPerChunk() * Math.max(1, spawnableChunks) / (float) SPAWN_AREA_CHUNKS * factor);
    }

    /** Applies the difficulty's health / damage factors to a freshly spawned hostile (idempotent per mob). */
    public static void applyDifficulty(Mob mob, CreatureDifficulty difficulty) {
        if (difficulty == CreatureDifficulty.NORMAL) return;
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && !health.hasModifier(HEALTH_MODIFIER)) {
            health.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER, difficulty.healthFactor - 1f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setHealth(mob.getMaxHealth());
        }
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && !damage.hasModifier(DAMAGE_MODIFIER)) {
            damage.addPermanentModifier(new AttributeModifier(DAMAGE_MODIFIER, difficulty.damageFactor - 1f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Whether the mob already carries a difficulty modifier (for logs and tests). */
    public static boolean hasDifficulty(LivingEntity mob) {
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        return health != null && health.hasModifier(HEALTH_MODIFIER);
    }
}
