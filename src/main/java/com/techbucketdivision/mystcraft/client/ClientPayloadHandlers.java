package com.techbucketdivision.mystcraft.client;

import com.techbucketdivision.mystcraft.entity.ColoredLightningBolt;
import com.techbucketdivision.mystcraft.network.AgeDataSyncPayload;
import com.techbucketdivision.mystcraft.network.ExplosionEffectsPayload;
import com.techbucketdivision.mystcraft.network.LightningPayload;
import com.techbucketdivision.mystcraft.network.LinkParticlesPayload;
import com.techbucketdivision.mystcraft.network.MenuMessagePayload;
import com.techbucketdivision.mystcraft.network.ProfilingStatePayload;
import com.techbucketdivision.mystcraft.network.ServerConfigPayload;
import com.techbucketdivision.mystcraft.network.UpdateDimensionsPayload;
import com.techbucketdivision.mystcraft.registry.ModEntities;
import com.techbucketdivision.mystcraft.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Client handlers for every server → client payload (registered from {@link ClientSetup}). */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    /** True while the server allows floating book labels (synced on login). */
    public static volatile boolean serverLabelsAllowed = true;

    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(AgeDataSyncPayload.TYPE, ClientPayloadHandlers::handleAgeData);
        event.register(UpdateDimensionsPayload.TYPE, ClientPayloadHandlers::handleUpdateDimensions);
        event.register(ServerConfigPayload.TYPE, ClientPayloadHandlers::handleServerConfig);
        event.register(LinkParticlesPayload.TYPE, ClientPayloadHandlers::handleLinkParticles);
        event.register(ExplosionEffectsPayload.TYPE, ClientPayloadHandlers::handleExplosionEffects);
        event.register(LightningPayload.TYPE, ClientPayloadHandlers::handleLightning);
        event.register(ProfilingStatePayload.TYPE, ClientPayloadHandlers::handleProfilingState);
        event.register(MenuMessagePayload.TYPE, ClientPayloadHandlers::handleMenuMessage);
    }

    private static void handleAgeData(AgeDataSyncPayload payload, IPayloadContext ctx) {
        ClientAgeData.accept(payload.data());
    }

    /** Adds / removes dimension keys from the client's known level set (TOOLCHAIN §4.3.4, Infiniverse pattern). */
    private static void handleUpdateDimensions(UpdateDimensionsPayload payload, IPayloadContext ctx) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Set<ResourceKey<Level>> levels = Objects.requireNonNullElse(player.connection.levels(), Set.of());
        Consumer<ResourceKey<Level>> op = payload.add() ? levels::add : levels::remove;
        try {
            payload.keys().forEach(op);
        } catch (UnsupportedOperationException ignored) {
            // immutable set on this connection: nothing we can do, vanilla will refuse the dimension change
        }
    }

    private static void handleServerConfig(ServerConfigPayload payload, IPayloadContext ctx) {
        serverLabelsAllowed = payload.serverLabels();
    }

    private static void handleLinkParticles(LinkParticlesPayload payload, IPayloadContext ctx) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource rand = level.getRandom();
        for (int i = 0; i < 50; i++) {
            double dx = rand.nextGaussian() * 0.15;
            double dy = rand.nextGaussian() * 0.15;
            double dz = rand.nextGaussian() * 0.15;
            level.addParticle(ModParticles.LINK.get(), payload.x() + rand.nextGaussian() * 0.3, payload.y() + rand.nextDouble() * 1.8,
                    payload.z() + rand.nextGaussian() * 0.3, dx, dy, dz);
        }
    }

    private static void handleExplosionEffects(ExplosionEffectsPayload payload, IPayloadContext ctx) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource rand = level.getRandom();
        double x = payload.x(), y = payload.y(), z = payload.z();
        if (payload.size() >= 2f) {
            level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1.0, 0.0, 0.0);
        } else {
            level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 1.0, 0.0, 0.0);
        }
        for (BlockPos pos : payload.affected()) {
            double px = pos.getX() + rand.nextFloat();
            double py = pos.getY() + rand.nextFloat();
            double pz = pos.getZ() + rand.nextFloat();
            double dx = px - x, dy = py - y, dz = pz - z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-4) continue;
            dx /= len;
            dy /= len;
            dz /= len;
            double f = 0.5 / (len / payload.size() + 0.1);
            f *= rand.nextFloat() * rand.nextFloat() + 0.3f;
            dx *= f;
            dy *= f;
            dz *= f;
            level.addParticle(ParticleTypes.POOF, (px + x) / 2.0, (py + y) / 2.0, (pz + z) / 2.0, dx, dy, dz);
            level.addParticle(ParticleTypes.SMOKE, px, py, pz, dx, dy, dz);
        }
    }

    /** Visual-only coloured bolt for players outside the entity's tracking range. */
    private static void handleLightning(LightningPayload payload, IPayloadContext ctx) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ColoredLightningBolt bolt = new ColoredLightningBolt(ModEntities.LIGHTNING.get(), level);
        bolt.snapTo(payload.x(), payload.y(), payload.z());
        bolt.setColor(payload.color());
        bolt.setVisualOnly(true);
        level.addEntity(bolt);
    }

    private static void handleProfilingState(ProfilingStatePayload payload, IPayloadContext ctx) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        // Toast manager name is unverified in 26.1 (was getToasts()); the action-bar overlay is the safe fallback.
        player.sendOverlayMessage(Component.translatable(payload.running() ? "gui.mystcraft.profiling.started" : "gui.mystcraft.profiling.finished"));
    }

    private static void handleMenuMessage(MenuMessagePayload payload, IPayloadContext ctx) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) MenuMessagePayload.dispatch(player, payload);
    }
}
