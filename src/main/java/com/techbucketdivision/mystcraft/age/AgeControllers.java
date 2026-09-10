package com.techbucketdivision.mystcraft.age;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-side cache of {@link AgeController}s keyed by Age UUID. The server side reads {@link AgeDataStorage}; the client
 * side reads {@code ClientAgeData}. Both are cleared on server stop / client disconnect.
 */
public final class AgeControllers {
    private AgeControllers() {}

    private static final Map<UUID, AgeController> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, AgeController> CLIENT = new ConcurrentHashMap<>();

    /** Server-side controller for an Age level, or {@code null} if the level is not an Age. */
    public static @Nullable AgeController server(MinecraftServer server, ResourceKey<Level> levelKey) {
        UUID uuid = AgeData.uuidFromLevelKey(levelKey);
        if (uuid == null) return null;
        return server(server, uuid);
    }

    public static @Nullable AgeController server(MinecraftServer server, UUID uuid) {
        AgeData data = AgeDataStorage.get(server).get(uuid);
        if (data == null) return null;
        AgeController c = SERVER.computeIfAbsent(uuid, u -> new AgeController(data, server.registryAccess(), false));
        c.ensureCurrent();
        return c;
    }

    public static @Nullable AgeController server(Level level) {
        MinecraftServer server = level.getServer();
        return server == null ? null : server(server, level.dimension());
    }

    /** Client-side controller; {@code data} comes from the synced cache. */
    public static AgeController client(AgeData data, HolderLookup.Provider registries) {
        AgeController c = CLIENT.computeIfAbsent(data.uuid(), u -> new AgeController(data, registries, true));
        c.ensureCurrent();
        return c;
    }

    public static @Nullable AgeController clientIfPresent(UUID uuid) {
        return CLIENT.get(uuid);
    }

    /** Side-agnostic lookup: server for server levels, client cache for client levels. */
    public static @Nullable AgeController of(Level level) {
        if (!level.isClientSide()) return server(level);
        UUID uuid = AgeData.uuidFromLevelKey(level.dimension());
        return uuid == null ? null : CLIENT.get(uuid);
    }

    public static void invalidateServer(UUID uuid) {
        SERVER.remove(uuid);
    }

    public static void clearServer() {
        SERVER.clear();
    }

    public static void clearClient() {
        CLIENT.clear();
    }
}
