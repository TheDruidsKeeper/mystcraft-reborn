package com.techbucketdivision.mystcraft.dimension;

import com.mojang.serialization.Lifecycle;
import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.network.UpdateDimensionsPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.techbucketdivision.mystcraft.network.Network;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Runtime creation and removal of dimensions. Adapted from Commoble's Infiniverse (MIT, 26.1 branch) — see
 * NOTICE.md. Requires the access transformer on {@code WorldGenSettings#dimensions}.
 */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class DynamicDimensions {
    private DynamicDimensions() {}

    private static final RegistrationInfo DIMENSION_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.stable());
    private static final Set<ResourceKey<Level>> VANILLA_LEVELS = Set.of(Level.OVERWORLD, Level.NETHER, Level.END);
    private static Set<ResourceKey<Level>> pendingUnregistration = new HashSet<>();

    private static final Field SERVER_EXECUTOR = ObfuscationReflectionHelper.findField(MinecraftServer.class, "executor");
    private static final Field SERVER_STORAGE = ObfuscationReflectionHelper.findField(MinecraftServer.class, "storageSource");
    private static final Field SERVER_REGISTRIES = ObfuscationReflectionHelper.findField(MinecraftServer.class, "registries");
    private static final Field LAYERED_VALUES = ObfuscationReflectionHelper.findField(LayeredRegistryAccess.class, "values");
    private static final Field LAYERED_COMPOSITE = ObfuscationReflectionHelper.findField(LayeredRegistryAccess.class, "composite");
    private static final Field IMMUTABLE_REGISTRIES = ObfuscationReflectionHelper.findField(RegistryAccess.ImmutableRegistryAccess.class, "registries");

    /** Gets a level, dynamically creating and registering it (and persisting it to world_gen_settings.dat) if needed. */
    @SuppressWarnings("deprecation")
    public static ServerLevel getOrCreateLevel(MinecraftServer server, ResourceKey<Level> levelKey, Supplier<LevelStem> dimensionFactory) {
        Map<ResourceKey<Level>, ServerLevel> map = server.forgeGetWorldMap();
        ServerLevel existing = map.get(levelKey);
        return existing != null ? existing : createAndRegisterLevel(server, map, levelKey, dimensionFactory);
    }

    public static void markForUnregistration(MinecraftServer server, ResourceKey<Level> levelKey) {
        if (!VANILLA_LEVELS.contains(levelKey) && server.getLevel(levelKey) != null) {
            pendingUnregistration.add(levelKey);
        }
    }

    public static Set<ResourceKey<Level>> pendingUnregistration() {
        return Set.copyOf(pendingUnregistration);
    }

    @SuppressWarnings("deprecation")
    private static ServerLevel createAndRegisterLevel(MinecraftServer server, Map<ResourceKey<Level>, ServerLevel> map,
                                                      ResourceKey<Level> levelKey, Supplier<LevelStem> dimensionFactory) {
        ResourceKey<LevelStem> dimensionKey = ResourceKey.create(Registries.LEVEL_STEM, levelKey.identifier());
        LevelStem dimension = dimensionFactory.get();

        Executor executor = get(SERVER_EXECUTOR, server);
        LevelStorageSource.LevelStorageAccess storage = get(SERVER_STORAGE, server);
        WorldData worldData = server.getWorldData();
        DerivedLevelData derivedLevelData = new DerivedLevelData(worldData, worldData.overworldData());
        WorldGenSettings worldGenSettings = server.getWorldGenSettings();
        long serverSeed = worldGenSettings.options().seed();

        Registry<LevelStem> dimensionRegistry = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        if (dimensionRegistry instanceof MappedRegistry<LevelStem> writable) {
            writable.unfreeze(false);
            writable.register(dimensionKey, dimension, DIMENSION_REGISTRATION_INFO);
        } else {
            throw new IllegalStateException("Unable to register dimension " + dimensionKey.identifier() + ": registry not writable");
        }

        // Persist so vanilla reconstitutes the level on the next start.
        Map<ResourceKey<LevelStem>, LevelStem> dimensionMap = new HashMap<>(worldGenSettings.dimensions.dimensions());
        dimensionMap.put(dimensionKey, dimension);
        worldGenSettings.dimensions = new WorldDimensions(dimensionMap);
        worldGenSettings.setDirty();

        ServerLevel newLevel = new ServerLevel(server, executor, storage, derivedLevelData, levelKey, dimension,
                worldData.isDebugWorld(), BiomeManager.obfuscateSeed(dimension.seedOverride().orElse(serverSeed)),
                List.of(), false);

        newLevel.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        server.getPlayerList().addWorldborderListener(newLevel);
        map.put(levelKey, newLevel);
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(newLevel));
        sendToAll(server, new UpdateDimensionsPayload(Set.of(levelKey), true));
        Mystcraft.LOGGER.info("Registered dynamic dimension {}", levelKey.identifier());
        return newLevel;
    }

    @SuppressWarnings("deprecation")
    private static void unregisterScheduled(MinecraftServer server) {
        if (pendingUnregistration.isEmpty()) return;
        Set<ResourceKey<Level>> keysToRemove = pendingUnregistration;
        pendingUnregistration = new HashSet<>();

        WorldGenSettings worldGenSettings = server.getWorldGenSettings();
        Map<ResourceKey<LevelStem>, LevelStem> dimensionMap = new HashMap<>(worldGenSettings.dimensions.dimensions());
        for (ResourceKey<Level> key : keysToRemove) {
            dimensionMap.remove(ResourceKey.create(Registries.LEVEL_STEM, key.identifier()));
        }
        worldGenSettings.dimensions = new WorldDimensions(dimensionMap);
        worldGenSettings.setDirty();

        Registry<LevelStem> oldRegistry = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        if (!(oldRegistry instanceof MappedRegistry<LevelStem> oldMapped)) {
            Mystcraft.LOGGER.warn("Cannot unload dimensions: LEVEL_STEM registry is not a MappedRegistry");
            return;
        }
        LayeredRegistryAccess<RegistryLayer> layered = get(SERVER_REGISTRIES, server);
        RegistryAccess.Frozen composite = get(LAYERED_COMPOSITE, layered);
        if (!(composite instanceof RegistryAccess.ImmutableRegistryAccess immutable)) {
            Mystcraft.LOGGER.warn("Cannot unload dimensions: composite registry access is not ImmutableRegistryAccess");
            return;
        }

        Set<ResourceKey<Level>> removed = new HashSet<>();
        ServerLevel overworld = server.overworld();
        for (ResourceKey<Level> key : keysToRemove) {
            ServerLevel level = server.forgeGetWorldMap().remove(key);
            if (level == null) continue;
            for (ServerPlayer player : new ArrayList<>(level.players())) {
                ServerPlayer.RespawnConfig respawnConfig = player.getRespawnConfig();
                LevelData.RespawnData respawn = respawnConfig == null ? server.getRespawnData() : respawnConfig.respawnData();
                ResourceKey<Level> respawnKey = respawn.dimension();
                BlockPos pos = respawn.pos();
                if (keysToRemove.contains(respawnKey)) {
                    respawnKey = Level.OVERWORLD;
                    if (respawnConfig != null) player.setRespawnPosition(null, false);
                }
                ServerLevel destination = server.getLevel(respawnKey);
                if (destination == null) destination = overworld;
                player.teleportTo(destination, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), respawn.yaw(), respawn.pitch(), true);
            }
            level.save(null, false, level.noSave());
            NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
            removed.add(key);
        }
        if (removed.isEmpty()) return;

        MappedRegistry<LevelStem> newRegistry = new MappedRegistry<>(Registries.LEVEL_STEM, oldMapped.registryLifecycle());
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : oldRegistry.entrySet()) {
            ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, entry.getKey().identifier());
            if (!removed.contains(levelKey)) {
                newRegistry.register(entry.getKey(), entry.getValue(),
                        oldRegistry.registrationInfo(entry.getKey()).orElse(DIMENSION_REGISTRATION_INFO));
            }
        }
        List<RegistryAccess.Frozen> layers = new ArrayList<>();
        for (RegistryLayer layer : RegistryLayer.values()) {
            layers.add(layer == RegistryLayer.DIMENSIONS
                    ? new RegistryAccess.ImmutableRegistryAccess(List.of(newRegistry)).freeze()
                    : layered.getLayer(layer));
        }
        Map<ResourceKey<? extends Registry<?>>, Registry<?>> newMap = new HashMap<>();
        for (RegistryAccess.Frozen access : layers) {
            access.registries().forEach(e -> newMap.put(e.key(), e.value()));
        }
        set(LAYERED_VALUES, layered, List.copyOf(layers));
        set(IMMUTABLE_REGISTRIES, immutable, newMap);
        server.markWorldsDirty();
        sendToAll(server, new UpdateDimensionsPayload(removed, false));
        Mystcraft.LOGGER.info("Unregistered dynamic dimensions {}", removed);
    }

    private static void sendToAll(MinecraftServer server, UpdateDimensionsPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(payload)) {
                Network.sendToPlayer(player, payload);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Field field, Object instance) {
        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void set(Field field, Object instance, Object value) {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) unregisterScheduled(server);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        pendingUnregistration = new HashSet<>();
    }
}
