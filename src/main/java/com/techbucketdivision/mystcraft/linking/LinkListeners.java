package com.techbucketdivision.mystcraft.linking;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.api.linking.LinkEvent;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
import com.techbucketdivision.mystcraft.world.AgeSpawn;
import com.techbucketdivision.mystcraft.entity.LinkbookEntity;
import com.techbucketdivision.mystcraft.item.LinkingBookItem;
import com.techbucketdivision.mystcraft.network.LinkParticlesPayload;
import com.techbucketdivision.mystcraft.registry.ModCriteria;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import com.techbucketdivision.mystcraft.network.Network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Default link listeners (REQUIREMENTS §7.3): permission rules, per-player permissions, the Relative alteration,
 * Disarm, momentum / platform / minecart handling, advancements, particles and sounds.
 */
public final class LinkListeners {
    private LinkListeners() {}

    private static boolean registered;

    /** Registers every default listener on the game bus. Idempotent. */
    public static synchronized void registerDefaults() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(LinkListeners::onAllowBasic);
        NeoForge.EVENT_BUS.addListener(LinkListeners::onAllowPermissions);
        NeoForge.EVENT_BUS.addListener(LinkListeners::onAlterRelative);
        NeoForge.EVENT_BUS.addListener(LinkListeners::onStartDisarm);
        NeoForge.EVENT_BUS.addListener(LinkListeners::onStartEffects);
        NeoForge.EVENT_BUS.addListener(LinkListeners::onEndMomentum);
        NeoForge.EVENT_BUS.addListener(LinkListeners::onEndPlatform);
        NeoForge.EVENT_BUS.addListener(LinkListeners::onEndAdvancements);
        NeoForge.EVENT_BUS.addListener(LinkListeners::onEndEffects);
    }

    /**
     * Posts {@link LinkEvent.Allow}; {@code false} when any listener cancelled it. Refusals are logged once per entity
     * and reason (portal collisions re-try every tick), so a "walked through the portal, nothing happened" report can
     * be diagnosed from the log: {@code [link] refused <entity> -> <dimension>: <reason>}.
     */
    public static boolean isLinkPermitted(ServerLevel origin, Entity entity, LinkInfo info) {
        LinkEvent.Allow event = new LinkEvent.Allow(origin, entity, info);
        NeoForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) return true;
        String reason = event.getReason() == null ? "cancelled by a listener" : event.getReason();
        String key = entity.getUUID() + "|" + reason;
        long now = origin.getGameTime();
        Long last = REFUSAL_LOG.get(key);
        if (last == null || now - last > REFUSAL_LOG_INTERVAL) {
            REFUSAL_LOG.put(key, now);
            if (REFUSAL_LOG.size() > 256) REFUSAL_LOG.clear();
            Mystcraft.LOGGER.info("[link] refused {} -> {}: {}", entity.getName().getString(),
                    info.dimension().map(k -> k.identifier().toString()).orElse("<unbound>"), reason);
        }
        return false;
    }

    private static final Map<String, Long> REFUSAL_LOG = new HashMap<>();
    private static final long REFUSAL_LOG_INTERVAL = 100; // ticks

    // --- Allow -------------------------------------------------------------------------------------------------

    private static void onAllowBasic(LinkEvent.Allow event) {
        Entity entity = event.getEntity();
        ServerLevel origin = event.getOrigin();
        LinkInfo info = event.getInfo();
        MinecraftServer server = origin.getServer();

        ResourceKey<Level> target = info.dimension().orElse(null);
        if (target == null || server == null) {
            event.cancel("book is not bound to a dimension");
            return;
        }
        if (!entity.isAlive()) {
            event.cancel("entity is dead");
            return;
        }
        if (entity.level() != origin) {
            event.cancel("entity is in another level");
            return;
        }
        if (entity.isVehicle()) {
            event.cancel("entity is being ridden (passengers link with their vehicle)");
            return;
        }
        boolean sameDimension = origin.dimension().equals(target);
        if (sameDimension && !info.hasFlag(LinkProperty.INTRA_LINKING) && !info.hasFlag(LinkProperty.INTRA_LINKING_ONLY)) {
            event.cancel("already in the target dimension and the book has no Intra-Linking");
            return;
        }
        if (!sameDimension && info.hasFlag(LinkProperty.INTRA_LINKING_ONLY)) {
            event.cancel("book is intra-linking only");
            return;
        }
        if (AgeManager.isAge(target)) {
            AgeData data = AgeManager.get(server, target);
            if (data == null) {
                event.cancel("target Age has no data (deleted world data?)");
                return;
            }
            if (data.dead()) {
                event.cancel("target Age is dead");
                return;
            }
            UUID expected = info.targetUuid().orElse(null);
            if (expected != null && !expected.equals(data.uuid())) {
                event.cancel("book Age uuid " + expected + " does not match dimension Age " + data.uuid());
                return;
            }
        }
        if (info.hasFlag(LinkProperty.DISARM) && (entity instanceof ItemEntity || entity instanceof LinkbookEntity)) {
            event.cancel("Disarm books do not link items");
        }
    }

    private static void onAllowPermissions(LinkEvent.Allow event) {
        if (event.isCanceled()) return;
        LinkInfo info = event.getInfo();
        if (info.hasFlag(LinkProperty.OP_TP)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        MinecraftServer server = event.getOrigin().getServer();
        ResourceKey<Level> target = info.dimension().orElse(null);
        if (server == null || target == null) return;
        LinkPermissions permissions = LinkPermissions.get(server);
        if (!permissions.canLeave(player, event.getOrigin().dimension())) {
            event.cancel("player may not leave " + event.getOrigin().dimension().identifier());
        } else if (!permissions.canEnter(player, target)) {
            event.cancel("player may not enter " + target.identifier());
        }
    }

    // --- Alter -------------------------------------------------------------------------------------------------

    /** Relative: destination = destination spawn + (entity position − origin spawn). */
    private static void onAlterRelative(LinkEvent.Alter event) {
        if (!event.getInfo().hasFlag(LinkProperty.RELATIVE)) return;
        Entity entity = event.getEntity();
        BlockPos originSpawn = event.getOrigin().getRespawnData().pos();
        int dx = (int) (entity.getX() - originSpawn.getX());
        int dy = (int) (entity.getY() - originSpawn.getY());
        int dz = (int) (entity.getZ() - originSpawn.getZ());
        BlockPos destinationSpawn = event.getDestination().getRespawnData().pos();
        event.setSpawn(destinationSpawn.offset(dx, dy, dz));
    }

    // --- Start -------------------------------------------------------------------------------------------------

    private static void onStartDisarm(LinkEvent.Start event) {
        if (!event.getInfo().hasFlag(LinkProperty.DISARM)) return;
        Entity entity = event.getEntity();
        ServerLevel level = event.getOrigin();
        if (entity instanceof Player player) {
            player.getInventory().dropAll();
        }
        if (entity instanceof Container container) {
            ejectContainer(level, entity, container);
        }
        if (entity instanceof AbstractHorse horse) {
            ejectHorseInventory(level, horse);
        }
        if (entity instanceof Mob mob) {
            dropEquipment(level, mob);
        }
    }

    /**
     * Horse / donkey / llama saddle + chest contents. {@code AbstractHorse.inventory} is protected in 26.1, but the
     * vanilla {@link Entity#getSlot(int)} exposes it at {@code 500 + index} (used by /item), so no AT is needed.
     */
    private static void ejectHorseInventory(ServerLevel level, AbstractHorse horse) {
        for (int i = 0; i < horse.getInventorySize(); i++) {
            SlotAccess slot = horse.getSlot(500 + i);
            if (slot == null) continue;
            ItemStack stack = slot.get();
            if (stack.isEmpty()) continue;
            slot.set(ItemStack.EMPTY);
            ItemEntity item = new ItemEntity(level, horse.getX(), horse.getY() + 0.5, horse.getZ(), stack);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }

    private static void ejectContainer(ServerLevel level, Entity entity, Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            container.setItem(i, ItemStack.EMPTY);
            if (stack.isEmpty()) continue;
            while (!stack.isEmpty()) {
                int amount = Math.min(stack.getCount(), level.getRandom().nextInt(21) + 10);
                ItemStack split = stack.split(amount);
                ItemEntity item = new ItemEntity(level,
                        entity.getX() + level.getRandom().nextFloat() * 0.8f + 0.1f,
                        entity.getY() + level.getRandom().nextFloat() * 0.8f + 0.1f,
                        entity.getZ() + level.getRandom().nextFloat() * 0.8f + 0.1f, split);
                item.setDeltaMovement(level.getRandom().nextGaussian() * 0.05, level.getRandom().nextGaussian() * 0.05 + 0.2,
                        level.getRandom().nextGaussian() * 0.05);
                level.addFreshEntity(item);
            }
        }
    }

    private static void dropEquipment(ServerLevel level, LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            entity.setItemSlot(slot, ItemStack.EMPTY);
            entity.spawnAtLocation(level, stack);
        }
    }

    private static void onStartEffects(LinkEvent.Start event) {
        spawnParticles(event.getEntity());
        LinkSounds.play(event.getEntity(), event.getInfo());
    }

    // --- End ---------------------------------------------------------------------------------------------------

    /** Momentum: zero (and reset fall) unless Maintain Momentum, which re-orients velocity to the new yaw; +0.2 up. */
    private static void onEndMomentum(LinkEvent.End event) {
        Entity entity = event.getEntity();
        LinkInfo info = event.getInfo();
        Vec3 motion = entity.getDeltaMovement();
        if (!info.hasFlag(LinkProperty.MAINTAIN_MOMENTUM)) {
            motion = Vec3.ZERO;
            entity.resetFallDistance();
        } else {
            float yaw = info.yaw();
            float motionYaw = (float) (Math.atan2(motion.x, motion.z) * 180D / Math.PI);
            // to local space
            double cos = Math.cos(Math.toRadians(-motionYaw));
            double sin = Math.sin(Math.toRadians(-motionYaw));
            double lx = cos * motion.x - sin * motion.z;
            double lz = sin * motion.x + cos * motion.z;
            // to global space with the new yaw
            cos = Math.cos(Math.toRadians(yaw));
            sin = Math.sin(Math.toRadians(yaw));
            motion = new Vec3(cos * lx - sin * lz, motion.y, sin * lx + cos * lz);
        }
        entity.setDeltaMovement(motion.add(0, 0.2, 0));
        if (entity instanceof AbstractMinecart) {
            entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0);
        }
    }

    /** Generate Platform: a stone block under the spawn when the two blocks below are air. */
    /**
     * Arrival platform. Linking into an Age (any book) always leaves the traveller on a 3x3 cobblestone platform:
     * {@link AgeSpawn#ensureArrivalPlatform} fills only non-solid blocks under the feet and clears head room, so a
     * natural landing is untouched apart from the pad, and a sky / lava / cave arrival is survivable. Non-Age
     * destinations keep the original one-block stone platform when {@code GENERATE_PLATFORM} is set.
     */
    private static void onEndPlatform(LinkEvent.End event) {
        LinkInfo info = event.getInfo();
        BlockPos spawn = info.spawn().orElse(null);
        if (spawn == null) return;
        ServerLevel level = event.getDestination();
        if (AgeManager.isAge(level.dimension())) {
            AgeSpawn.ensureArrivalPlatform(level, spawn);
            return;
        }
        if (!info.hasFlag(LinkProperty.GENERATE_PLATFORM)) return;
        if (level.isEmptyBlock(spawn.below()) && level.isEmptyBlock(spawn.below(2))) {
            level.setBlockAndUpdate(spawn.below(), Blocks.STONE.defaultBlockState());
        }
    }

    /** "The Way Back" when carrying a Linking Book into an Age, otherwise "Call Me Quinn". */
    private static void onEndAdvancements(LinkEvent.End event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!AgeManager.isAge(event.getDestination().dimension())) return;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.getItem() instanceof LinkingBookItem) {
                ModCriteria.ENTER_AGE_SAFE.get().trigger(player);
                return;
            }
        }
        if (player.getOffhandItem().getItem() instanceof LinkingBookItem) {
            ModCriteria.ENTER_AGE_SAFE.get().trigger(player);
            return;
        }
        ModCriteria.ENTER_AGE_QUINN.get().trigger(player);
    }

    private static void onEndEffects(LinkEvent.End event) {
        spawnParticles(event.getEntity());
        LinkSounds.play(event.getEntity(), event.getInfo());
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private static void spawnParticles(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            Network.sendToPlayersInDimension(level, new LinkParticlesPayload(entity.getX(), entity.getY(), entity.getZ()));
        }
    }
}
