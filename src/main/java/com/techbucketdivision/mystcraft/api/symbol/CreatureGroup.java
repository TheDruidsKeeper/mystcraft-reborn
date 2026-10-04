package com.techbucketdivision.mystcraft.api.symbol;

import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.jspecify.annotations.Nullable;

/**
 * The three creature groups an Age author controls (plan §10). A mob's group follows its {@link MobCategory}, except
 * that entity types in {@code #mystcraft:neutral_creatures} are {@link #NEUTRAL} whatever their category.
 */
public enum CreatureGroup {
    PASSIVE("passive"),
    NEUTRAL("neutral"),
    HOSTILE("hostile");

    public static final TagKey<EntityType<?>> NEUTRAL_TAG = TagKey.create(Registries.ENTITY_TYPE, MystIds.id("neutral_creatures"));

    private final String id;

    CreatureGroup(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** The group of an entity type, or {@code null} for things that are not creatures (items, projectiles, misc). */
    public static @Nullable CreatureGroup of(EntityType<?> type) {
        if (type.builtInRegistryHolder().is(NEUTRAL_TAG)) return NEUTRAL;
        MobCategory category = type.getCategory();
        if (category == MobCategory.MISC) return null;
        return category == MobCategory.MONSTER ? HOSTILE : PASSIVE;
    }

    /** Whether natural spawns of this category can belong to the group (a category may hold several groups). */
    public boolean covers(MobCategory category) {
        if (category == MobCategory.MISC) return false;
        return switch (this) {
            case HOSTILE -> category == MobCategory.MONSTER;
            case PASSIVE -> category != MobCategory.MONSTER;
            case NEUTRAL -> true;
        };
    }
}
