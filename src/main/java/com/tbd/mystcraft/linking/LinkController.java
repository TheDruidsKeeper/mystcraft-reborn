package com.tbd.mystcraft.linking;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.age.AgeController;
import com.tbd.mystcraft.age.AgeControllers;
import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.age.AgeManager;
import com.tbd.mystcraft.api.linking.LinkEvent;
import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.world.AgeSpawn;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Link flow (original spec §7.3): permission → destination → alter → teleport with passengers → listeners. */
public final class LinkController {
    private LinkController() {}

    /**
     * Moves {@code entity} (and its riders) to the destination described by {@code info}. Server only.
     *
     * @return {@code true} when the entity was moved
     */
    public static boolean travelEntity(Entity entity, LinkInfo info) {
        if (!(entity.level() instanceof ServerLevel origin)) return false;
        if (info.dimension().isEmpty()) return false;
        if (!LinkListeners.isLinkPermitted(origin, entity, info)) return false;

        entity = entity.getRootVehicle();
        MinecraftServer server = origin.getServer();
        if (server == null) return false;

        ResourceKey<Level> dimension = info.dimension().get();
        ServerLevel destination = resolveLevel(server, dimension);
        if (destination == null) {
            Mystcraft.LOGGER.error("Cannot link entity to dimension {}: level not available", dimension.identifier());
            NeoForge.EVENT_BUS.post(new LinkEvent.Failed(origin, entity, info, "no_level"));
            return false;
        }

        BlockPos spawn = info.spawn().orElse(null);
        if (spawn == null) {
            spawn = defaultSpawn(destination);
            info = info.withSpawn(spawn);
        }
        float yaw = info.yaw();

        LinkEvent.Alter alter = new LinkEvent.Alter(origin, destination, entity, info, spawn, yaw);
        NeoForge.EVENT_BUS.post(alter);
        if (alter.getSpawn() != null) spawn = alter.getSpawn();
        yaw = alter.getYaw();
        info = info.withSpawn(spawn).withYaw(yaw);

        return teleportEntity(destination, entity, spawn, yaw, info) != null;
    }

    /**
     * Default arrival point of a level. For Ages this is {@link AgeSpawn#findSpawn} (determined and stored on first
     * use), snapped onto the ground by {@link AgeSpawn#snapToGround}. {@code ServerLevel#getRespawnData()} is NOT
     * usable here: in 26.1 it delegates to the server-wide (overworld) respawn data, which put Age arrivals at the
     * overworld spawn coordinates - floating above or buried in unrelated terrain.
     */
    public static BlockPos defaultSpawn(ServerLevel destination) {
        if (AgeManager.isAge(destination.dimension())) {
            AgeController controller = AgeControllers.server(destination);
            if (controller != null) {
                BlockPos spawn = AgeSpawn.findSpawn(destination, controller);
                return AgeSpawn.snapToGround(destination, spawn);
            }
            Mystcraft.LOGGER.warn("[link] no controller for Age level {}; falling back to the shared spawn", destination.dimension().identifier());
        }
        return destination.getRespawnData().pos();
    }

    /** Loads (or creates, for Ages) the destination level. */
    public static @Nullable ServerLevel resolveLevel(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level != null) return level;
        AgeData data = AgeManager.get(server, dimension);
        if (data == null || data.dead()) return null;
        try {
            return AgeManager.getOrCreateLevel(server, data);
        } catch (Exception e) {
            Mystcraft.LOGGER.error("Failed to create level for Age {}", data.uuid(), e);
            return null;
        }
    }

    private static @Nullable Entity teleportEntity(ServerLevel destination, Entity entity, BlockPos spawn, float yaw, LinkInfo info) {
        List<Entity> passengers = new ArrayList<>(entity.getPassengers());
        List<Entity> movedPassengers = new ArrayList<>();
        for (Entity passenger : passengers) {
            passenger.stopRiding();
            Entity moved = teleportEntity(destination, passenger, spawn, yaw, info);
            if (moved != null) movedPassengers.add(moved);
        }

        if (!(entity.level() instanceof ServerLevel origin)) return null;
        if (!LinkListeners.isLinkPermitted(origin, entity, info)) return null;

        NeoForge.EVENT_BUS.post(new LinkEvent.Start(origin, destination, entity, info));

        // Sneaking across a level change desyncs the pose.
        if (entity.isShiftKeyDown()) entity.setShiftKeyDown(false);

        double x = spawn.getX() + 0.5;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5;
        float pitch = entity.getXRot();

        if (origin != destination) {
            Entity result = entity.teleport(new TeleportTransition(destination, new Vec3(x, y, z), Vec3.ZERO, yaw, pitch,
                    TeleportTransition.DO_NOTHING));
            if (result == null) {
                NeoForge.EVENT_BUS.post(new LinkEvent.Failed(origin, entity, info, "teleport_cancelled"));
                return null;
            }
            entity = result;
        } else {
            entity.teleportTo(destination, x, y, z, Set.of(), yaw, pitch, false);
        }

        // Make sure the destination chunk exists, then push the entity up out of any solid geometry.
        destination.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        BlockPos pos = spawn;
        int limit = destination.getMaxY();
        while (pos.getY() < limit && !destination.noCollision(entity, entity.getBoundingBox())) {
            pos = pos.above();
            entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }
        if (!pos.equals(spawn)) {
            entity.teleportTo(destination, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), yaw, pitch, false);
            info = info.withSpawn(pos);
        }

        if (entity instanceof ServerPlayer player) {
            if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        }

        NeoForge.EVENT_BUS.post(new LinkEvent.End(origin, destination, entity, info));

        for (Entity passenger : movedPassengers) {
            passenger.startRiding(entity);
        }
        return entity;
    }
}
