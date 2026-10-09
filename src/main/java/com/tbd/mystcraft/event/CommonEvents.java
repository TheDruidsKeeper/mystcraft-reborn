package com.tbd.mystcraft.event;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.age.AgeController;
import com.tbd.mystcraft.age.AgeControllers;
import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.age.AgeManager;
import com.tbd.mystcraft.api.linking.LinkInfo;
import com.tbd.mystcraft.api.linking.LinkProperty;
import com.tbd.mystcraft.config.MystcraftConfig;
import com.tbd.mystcraft.dimension.AgeEnvironment;
import com.tbd.mystcraft.entity.LinkbookEntity;
import com.tbd.mystcraft.creature.CreatureEvents;
import com.tbd.mystcraft.instability.BaselineProfiler;
import com.tbd.mystcraft.instability.InstabilityController;
import com.tbd.mystcraft.item.LinkingBookItem;
import com.tbd.mystcraft.item.LinkingItem;
import com.tbd.mystcraft.linking.LinkController;
import com.tbd.mystcraft.knowledge.SymbolKnowledge;
import com.tbd.mystcraft.network.ServerConfigPayload;
import com.tbd.mystcraft.registry.ModAttachments;
import com.tbd.mystcraft.registry.ModCriteria;
import com.tbd.mystcraft.symbol.BiomeSymbols;
import com.tbd.mystcraft.symbol.FluidSymbols;
import com.tbd.mystcraft.world.AgeSpawn;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.tbd.mystcraft.network.Network;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Game-bus listeners: server lifecycle, login / dimension-change checks, respawn, PvP, ink, dropped books. */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class CommonEvents {
    private CommonEvents() {}

    /** Players scheduled to be sent home on the next server tick (original spec §7.4). */
    private static final List<UUID> PENDING_EJECTIONS = new ArrayList<>();

    // --- server lifecycle ----------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        try {
            BiomeSymbols.registerAll(server.registryAccess());
            FluidSymbols.registerAll();
        } catch (Exception e) {
            Mystcraft.LOGGER.error("Failed to register biome/fluid symbols", e);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        BaselineProfiler.initialize(server);
        AgeManager.restoreAll(server);
        // Headless end-to-end verification for the Docker smoke test / CI. Never runs in normal play.
        if (com.tbd.mystcraft.SelfCheck.enabled()) {
            com.tbd.mystcraft.SelfCheck.run(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AgeControllers.clearServer();
        InstabilityController.clearAll();
        CreatureEvents.forgetAll();
        synchronized (PENDING_EJECTIONS) {
            PENDING_EJECTIONS.clear();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        List<UUID> pending;
        synchronized (PENDING_EJECTIONS) {
            if (PENDING_EJECTIONS.isEmpty()) return;
            pending = new ArrayList<>(PENDING_EJECTIONS);
            PENDING_EJECTIONS.clear();
        }
        MinecraftServer server = event.getServer();
        for (UUID uuid : pending) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) ejectToHome(player);
        }
    }

    /** Schedules a player to be linked to the home dimension (Op-TP + intra-linking link). */
    public static void scheduleEjection(ServerPlayer player) {
        synchronized (PENDING_EJECTIONS) {
            if (!PENDING_EJECTIONS.contains(player.getUUID())) PENDING_EJECTIONS.add(player.getUUID());
        }
    }

    public static void ejectToHome(ServerPlayer player) {
        ResourceKey<Level> home = MystcraftConfig.homeDimension();
        if (player.level().dimension().equals(home)) return;
        LinkInfo info = new LinkInfo(Optional.of(home), Optional.empty(), Optional.empty(), LinkInfo.DEFAULT_YAW,
                home.identifier().toString(), Set.of(LinkProperty.INTRA_LINKING, LinkProperty.OP_TP), Map.of());
        if (!LinkController.travelEntity(player, info)) {
            Mystcraft.LOGGER.warn("Could not eject {} to {}", player.getPlainTextName(), home.identifier());
        }
    }

    // --- players -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        Network.sendToPlayer(player, new ServerConfigPayload(MystcraftConfig.SERVER_LABELS.get()));
        SymbolKnowledge.sync(player);

        ResourceKey<Level> current = player.level().dimension();
        UUID currentAge = AgeData.uuidFromLevelKey(current);
        Optional<UUID> stored = player.getData(ModAttachments.LAST_AGE.get());
        boolean eject = false;
        if (currentAge != null) {
            if (AgeManager.isDead(server, current)) eject = true;
            else if (stored.isEmpty() && MystcraftConfig.REQUIRE_UUID_TEST.get()) eject = true;
            else if (stored.isPresent() && !stored.get().equals(currentAge)) eject = true;
        }
        if (eject) {
            scheduleEjection(player);
            return;
        }
        syncAgeData(player, server, current);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        ResourceKey<Level> to = event.getTo();
        if (AgeManager.isDead(server, to)) {
            scheduleEjection(player);
            return;
        }
        player.setData(ModAttachments.LAST_AGE.get(), Optional.ofNullable(AgeData.uuidFromLevelKey(to)));
        SymbolKnowledge.sync(player); // the client gets a fresh player entity on a dimension change
        if (AgeManager.isAge(to)) {
            syncAgeData(player, server, to);
            awardEntryAdvancement(player);
            // arriving in an Age teaches every symbol it is made of (world-building plan §7.1)
            AgeData age = AgeManager.get(server, to);
            if (age != null) SymbolKnowledge.learnAge(player, age);
        }
    }

    private static void syncAgeData(ServerPlayer player, MinecraftServer server, ResourceKey<Level> levelKey) {
        AgeData data = AgeManager.get(server, levelKey);
        if (data != null) AgeManager.sendAgeData(player, data);
    }

    /** "The Way Back" when carrying a Linking Book, else "Call Me Quinn" (original spec §7.3). */
    private static void awardEntryAdvancement(ServerPlayer player) {
        boolean hasLinkingBook = false;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof LinkingBookItem) {
                hasLinkingBook = true;
                break;
            }
        }
        if (hasLinkingBook) ModCriteria.ENTER_AGE_SAFE.get().trigger(player);
        else ModCriteria.ENTER_AGE_QUINN.get().trigger(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SymbolKnowledge.sync(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel deathLevel = player.level();
        if (!AgeManager.isAge(deathLevel.dimension())) return;
        if (!MystcraftConfig.RESPAWN_IN_AGES.get()) return;
        TeleportTransition original = event.getOriginalTeleportTransition();
        // Only intervene when vanilla would fall back to the overworld spawn (no valid bed / anchor in this Age).
        if (original.newLevel().dimension().equals(deathLevel.dimension())) return;
        ServerPlayer.RespawnConfig respawnConfig = player.getRespawnConfig();
        boolean respawnBlockElsewhere = respawnConfig != null
                && !ServerPlayer.RespawnConfig.getDimensionOrDefault(respawnConfig).equals(deathLevel.dimension());
        if (respawnBlockElsewhere && !original.missingRespawnBlock()) return;
        MinecraftServer server = deathLevel.getServer();
        if (server == null || AgeManager.isDead(server, deathLevel.dimension())) return;
        AgeController controller = AgeControllers.server(deathLevel);
        if (controller == null) return;
        BlockPos spawn = AgeSpawn.findSpawn(deathLevel, controller);
        event.setTeleportTransition(new TeleportTransition(deathLevel, Vec3.atBottomCenterOf(spawn), Vec3.ZERO,
                original.yRot(), original.xRot(), TeleportTransition.DO_NOTHING));
        event.setCopyOriginalSpawnPosition(false);
    }

    // --- combat / interaction ------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player)) return;
        Level level = victim.level();
        if (level.isClientSide() || !AgeManager.isAge(level.dimension())) return;
        AgeController controller = AgeControllers.server(level);
        if (controller != null && !controller.isPvPEnabled()) {
            event.setCanceled(true);
        }
    }

    // --- entities / levels ---------------------------------------------------------------------------------------

    /** Dropped linking books become {@link LinkbookEntity}s (original spec §7.9). */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ItemEntity item)) return;
        ItemStack stack = item.getItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof LinkingItem)) return;
        LinkbookEntity book = LinkbookEntity.spawnAt(level, item.position(), item.getDeltaMovement(), item.getYRot(), stack);
        if (book != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!AgeManager.isAge(level.dimension())) return;
        AgeController controller = AgeControllers.server(level);
        if (controller == null) {
            Mystcraft.LOGGER.warn("Age level {} loaded without AgeData", level.dimension().identifier());
            return;
        }
        AgeEnvironment.apply(level, controller);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            InstabilityController.invalidate(level.dimension());
            CreatureEvents.forget(level.dimension());
        }
    }

    /** Utility for commands: the Age level the entity stands in, or {@code null}. */
    public static @Nullable ServerLevel ageLevelOf(Entity entity) {
        return entity.level() instanceof ServerLevel level && AgeManager.isAge(level.dimension()) ? level : null;
    }
}
