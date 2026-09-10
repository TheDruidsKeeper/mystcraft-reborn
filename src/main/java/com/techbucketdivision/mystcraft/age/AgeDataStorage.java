package com.techbucketdivision.mystcraft.age;

import com.mojang.serialization.Codec;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-global registry of all Ages: {@code <world>/data/mystcraft/ages.dat}. */
public final class AgeDataStorage extends SavedData {
    private static final Codec<AgeDataStorage> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
            AgeData.CODEC.listOf().fieldOf("ages").forGetter(s -> List.copyOf(s.ages.values())),
            Codec.INT.optionalFieldOf("ordinal", 0).forGetter(s -> s.ordinalCounter)
    ).apply(i, AgeDataStorage::new));

    public static final SavedDataType<AgeDataStorage> TYPE = new SavedDataType<>(
            MystIds.id("ages"), AgeDataStorage::new, CODEC);

    private final Map<UUID, AgeData> ages = new LinkedHashMap<>();
    private int ordinalCounter;

    public AgeDataStorage() {}

    private AgeDataStorage(List<AgeData> list, int ordinal) {
        for (AgeData a : list) put(a);
        ordinalCounter = Math.max(ordinal, list.size());
    }

    public static AgeDataStorage get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private void put(AgeData data) {
        ages.put(data.uuid(), data);
        data.setDirtyListener(this::setDirty);
    }

    public @Nullable AgeData get(UUID uuid) {
        return ages.get(uuid);
    }

    public Collection<AgeData> all() {
        return Collections.unmodifiableCollection(ages.values());
    }

    /** Creates a new Age, recycling the first dead one whose level is not loaded. */
    public AgeData createAge(MinecraftServer server) {
        int ordinal = ++ordinalCounter;
        for (AgeData a : ages.values()) {
            if (a.dead() && server.getLevel(a.levelKey()) == null) {
                a.recreate(ordinal);
                setDirty();
                return a;
            }
        }
        AgeData data = AgeData.create(UUID.randomUUID(), server.overworld().getSeed(), ordinal);
        put(data);
        setDirty();
        return data;
    }

    /** Number of ages created so far (used for default names). */
    public int ordinalCounter() {
        return ordinalCounter;
    }
}
