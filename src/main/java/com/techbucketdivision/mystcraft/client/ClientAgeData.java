package com.techbucketdivision.mystcraft.client;

import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.age.AgeData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of synced {@link AgeData} keyed by Age UUID. Age time advances locally between syncs so the sky
 * renders smoothly; a sync overwrites the local clock.
 */
public final class ClientAgeData {
    private ClientAgeData() {}

    private static final Map<UUID, AgeData> DATA = new ConcurrentHashMap<>();
    private static final String WEATHER_KEY = "weather";

    /** Applies a snapshot from the server. */
    public static void accept(AgeData incoming) {
        AgeData existing = DATA.get(incoming.uuid());
        if (existing == null) {
            DATA.put(incoming.uuid(), incoming);
            return;
        }
        boolean structural = existing.seed() != incoming.seed()
                || !existing.symbols().equals(incoming.symbols())
                || existing.dead() != incoming.dead()
                || existing.visited() != incoming.visited()
                || !existing.name().equals(incoming.name());
        if (structural) {
            existing.copyFrom(incoming); // bumps the revision -> controller rebuilds lazily
            return;
        }
        // Cheap update: keep the controller (and its bound weather storage) alive.
        existing.setWorldTime(incoming.worldTime());
        CompoundTag weather = existing.data(WEATHER_KEY);
        for (String key : new ArrayList<>(weather.keySet())) weather.remove(key);
        weather.merge(incoming.data(WEATHER_KEY));
    }

    public static @Nullable AgeData get(UUID uuid) {
        return DATA.get(uuid);
    }

    /** Synced data for a client level, or {@code null} when the level is not an Age (or not synced yet). */
    public static @Nullable AgeData dataFor(ClientLevel level) {
        UUID uuid = AgeData.uuidFromLevelKey(level.dimension());
        return uuid == null ? null : DATA.get(uuid);
    }

    /** Client controller for an Age level, or {@code null}. */
    public static @Nullable AgeController controllerFor(ClientLevel level) {
        AgeData data = dataFor(level);
        if (data == null) return null;
        try {
            return AgeControllers.client(data, level.registryAccess());
        } catch (RuntimeException e) {
            return null; // a broken symbol must never kill rendering
        }
    }

    /** Age time in ticks for a client level (0 when not an Age). */
    public static long ageTime(ClientLevel level) {
        AgeData data = dataFor(level);
        return data == null ? 0L : data.worldTime();
    }

    /** Called every client tick: advances the local clock of the current Age. */
    public static void tick(ClientLevel level) {
        AgeData data = dataFor(level);
        if (data != null) data.setWorldTime(data.worldTime() + 1);
    }

    public static void clear() {
        DATA.clear();
        AgeControllers.clearClient();
    }
}
