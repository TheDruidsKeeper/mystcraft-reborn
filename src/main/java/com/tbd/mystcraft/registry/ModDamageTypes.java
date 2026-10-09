package com.tbd.mystcraft.registry;

import com.tbd.mystcraft.util.MystIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The mod's damage types ({@code data/mystcraft/damage_type}): what instability kills with. Death messages are
 * {@code death.attack.mystcraft.<id>}; the {@link #INSTABILITY} tag groups them for the "Death by Typo" advancement
 * ({@code instability/InstabilityDeaths}).
 */
public final class ModDamageTypes {
    private ModDamageTypes() {}

    public static final ResourceKey<DamageType> DECAY = key("decay");
    public static final ResourceKey<DamageType> METEOR = key("meteor");
    public static final ResourceKey<DamageType> INSTABILITY_EXPLOSION = key("instability_explosion");
    public static final TagKey<DamageType> INSTABILITY = TagKey.create(Registries.DAMAGE_TYPE, MystIds.id("instability"));

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, MystIds.id(path));
    }

    /** A damage source of {@code type} caused by {@code cause} (both direct and causing entity; may be null). */
    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity cause) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), cause, cause);
    }
}
