package com.techbucketdivision.mystcraft.age;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import com.techbucketdivision.mystcraft.dimension.AgeDimensionType;
import com.techbucketdivision.mystcraft.dimension.DynamicDimensions;
import com.techbucketdivision.mystcraft.network.AgeDataSyncPayload;
import com.techbucketdivision.mystcraft.world.AgeBiomeSource;
import com.techbucketdivision.mystcraft.world.AgeChunkGenerator;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import com.techbucketdivision.mystcraft.network.Network;
import org.jspecify.annotations.Nullable;

import java.util.OptionalLong;
import java.util.UUID;

/** Server-side facade for creating, looking up, syncing and retiring Ages. */
public final class AgeManager {
    private AgeManager() {}

    public static AgeData createAge(MinecraftServer server) {
        AgeData data = AgeDataStorage.get(server).createAge(server);
        AgeControllers.invalidateServer(data.uuid());
        Mystcraft.LOGGER.info("Created Age {} ({})", data.name(), data.uuid());
        return data;
    }

    public static @Nullable AgeData get(MinecraftServer server, UUID uuid) {
        return AgeDataStorage.get(server).get(uuid);
    }

    public static @Nullable AgeData get(MinecraftServer server, ResourceKey<Level> levelKey) {
        UUID uuid = AgeData.uuidFromLevelKey(levelKey);
        return uuid == null ? null : get(server, uuid);
    }

    public static boolean isAge(ResourceKey<Level> key) {
        return AgeData.isAgeLevel(key);
    }

    public static boolean isDead(MinecraftServer server, ResourceKey<Level> key) {
        AgeData data = get(server, key);
        return data != null && data.dead();
    }

    /** Loads (or creates) the level for an Age. */
    public static ServerLevel getOrCreateLevel(MinecraftServer server, AgeData data) {
        ResourceKey<Level> key = data.levelKey();
        ServerLevel existing = server.getLevel(key);
        if (existing != null) return existing;
        Holder<DimensionType> type = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(AgeDimensionType.AGE);
        return DynamicDimensions.getOrCreateLevel(server, key, () -> new LevelStem(type,
                new AgeChunkGenerator(data.uuid(), new AgeBiomeSource(data.uuid(),
                        server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS))), OptionalLong.of(data.seed())));
    }

    /** Recreates every live Age's level on server start (no-op for levels vanilla already restored). */
    public static void restoreAll(MinecraftServer server) {
        for (AgeData data : AgeDataStorage.get(server).all()) {
            if (data.dead()) continue;
            try {
                getOrCreateLevel(server, data);
            } catch (Exception e) {
                Mystcraft.LOGGER.error("Failed to restore Age {}", data.name(), e);
            }
        }
    }

    /** Marks an age dead so it can be recycled; players inside are ejected by {@code CommonEvents}. */
    public static boolean markDead(MinecraftServer server, ResourceKey<Level> key) {
        if (key.equals(MystcraftConfig.homeDimension()) || !isAge(key)) return false;
        AgeData data = get(server, key);
        if (data == null) return false;
        data.setDead(true);
        DynamicDimensions.markForUnregistration(server, key);
        AgeControllers.invalidateServer(data.uuid());
        return true;
    }

    // --- sync --------------------------------------------------------------------------------------------------

    public static void sendAgeData(ServerPlayer player, AgeData data) {
        Network.sendToPlayer(player, new AgeDataSyncPayload(data.copy()));
    }

    public static void sendAgeDataToLevel(ServerLevel level, AgeData data) {
        Network.sendToPlayersInDimension(level, new AgeDataSyncPayload(data.copy()));
    }
}
