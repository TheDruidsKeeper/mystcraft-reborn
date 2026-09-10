package com.techbucketdivision.mystcraft.api.linking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable description of a link target (replaces the original {@code LinkOptions} NBT). Stored on books as a data
 * component and passed to {@code LinkController}.
 *
 * @param dimension   target dimension (empty = unbound)
 * @param targetUuid  the Age UUID the link was bound to (refused when it no longer matches)
 * @param spawn       destination block position (empty = world spawn)
 * @param yaw         arrival yaw
 * @param displayName display name (age name / dimension name)
 * @param flags       link properties
 * @param props       string properties ({@link LinkProperty#PROP_SOUND}, {@link LinkProperty#PROP_SEED}, ...)
 */
public record LinkInfo(Optional<ResourceKey<Level>> dimension,
                       Optional<UUID> targetUuid,
                       Optional<BlockPos> spawn,
                       float yaw,
                       String displayName,
                       Set<LinkProperty> flags,
                       Map<String, String> props) {

    public static final String DEFAULT_NAME = "???";
    public static final float DEFAULT_YAW = 180f;

    public static final LinkInfo EMPTY = new LinkInfo(Optional.empty(), Optional.empty(), Optional.empty(), DEFAULT_YAW,
            DEFAULT_NAME, Set.of(), Map.of());

    public static final Codec<LinkInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension").forGetter(LinkInfo::dimension),
            UUIDUtil.CODEC.optionalFieldOf("target_uuid").forGetter(LinkInfo::targetUuid),
            BlockPos.CODEC.optionalFieldOf("spawn").forGetter(LinkInfo::spawn),
            Codec.FLOAT.optionalFieldOf("yaw", DEFAULT_YAW).forGetter(LinkInfo::yaw),
            Codec.STRING.optionalFieldOf("display_name", DEFAULT_NAME).forGetter(LinkInfo::displayName),
            LinkProperty.CODEC.listOf().optionalFieldOf("flags", List.of()).xmap(l -> (Set<LinkProperty>) new LinkedHashSet<>(l), List::copyOf).forGetter(LinkInfo::flags),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("props", Map.of()).forGetter(LinkInfo::props)
    ).apply(i, LinkInfo::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkInfo> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public LinkInfo {
        flags = Set.copyOf(flags);
        props = Map.copyOf(props);
    }

    public boolean hasFlag(LinkProperty p) {
        return flags.contains(p);
    }

    public @Nullable String prop(String key) {
        return props.get(key);
    }

    public boolean isBound() {
        return dimension.isPresent();
    }

    // --- withers -----------------------------------------------------------------------------------------------

    public LinkInfo withDimension(@Nullable ResourceKey<Level> dim) {
        return new LinkInfo(Optional.ofNullable(dim), targetUuid, spawn, yaw, displayName, flags, props);
    }

    public LinkInfo withTargetUuid(@Nullable UUID uuid) {
        return new LinkInfo(dimension, Optional.ofNullable(uuid), spawn, yaw, displayName, flags, props);
    }

    public LinkInfo withSpawn(@Nullable BlockPos pos) {
        return new LinkInfo(dimension, targetUuid, Optional.ofNullable(pos), yaw, displayName, flags, props);
    }

    public LinkInfo withYaw(float yaw) {
        return new LinkInfo(dimension, targetUuid, spawn, yaw, displayName, flags, props);
    }

    public LinkInfo withDisplayName(String name) {
        return new LinkInfo(dimension, targetUuid, spawn, yaw, name, flags, props);
    }

    public LinkInfo withFlag(LinkProperty p, boolean value) {
        Set<LinkProperty> f = new LinkedHashSet<>(flags);
        if (value) f.add(p); else f.remove(p);
        return new LinkInfo(dimension, targetUuid, spawn, yaw, displayName, f, props);
    }

    public LinkInfo withFlags(Set<LinkProperty> add) {
        Set<LinkProperty> f = new LinkedHashSet<>(flags);
        f.addAll(add);
        return new LinkInfo(dimension, targetUuid, spawn, yaw, displayName, f, props);
    }

    public LinkInfo withProp(String key, @Nullable String value) {
        Map<String, String> p = new HashMap<>(props);
        if (value == null) p.remove(key); else p.put(key, value);
        return new LinkInfo(dimension, targetUuid, spawn, yaw, displayName, flags, p);
    }

    /** Link info pointing at the entity's current position (used by Linking Books). */
    public static LinkInfo fromPosition(Entity entity, String displayName, @Nullable UUID dimensionUuid) {
        return new LinkInfo(Optional.of(entity.level().dimension()), Optional.ofNullable(dimensionUuid),
                Optional.of(entity.blockPosition()), entity.getYRot(), displayName, Set.of(), Map.of());
    }
}
