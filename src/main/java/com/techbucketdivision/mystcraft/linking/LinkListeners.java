package com.techbucketdivision.mystcraft.linking;

import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.api.linking.LinkEvent;
import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.api.linking.LinkProperty;
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
import net.neoforged.neoforge.network.PacketDistributor;

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

    /** Posts {@link LinkEvent.Allow}; {@code false} when any listener cancelled it. */
    public static boolean isLinkPermitted(ServerLevel origin, Entity entity, LinkInfo info) {
        LinkEvent.Allow event = new LinkEvent.Allow(origin, entity, info);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    // --- Allow -------------------------------------------------------------------------------------------------

    private static void onAllowBasic(LinkEvent.Allow event) {
        Entity entity = event.getEntity();
        ServerLevel origin = event.getOrigin();
        LinkInfo info = event.getInfo();
        MinecraftServer server = origin.getServer();

        ResourceKey<Level> target = info.dimension().orElse(null);
        if (target == null || server == null) {
            event.setCanceled(true);
            return;
        }
        if (!entity.isAlive() || entity.level() != origin || entity.isVehicle()) {
            event.setCanceled(true);
            return;
        }
        boolean sameDimension = origin.dimension().equals(target);
        if (sameDimension && !info.hasFlag(LinkProperty.INTRA_LINKING) && !info.hasFlag(LinkProperty.INTRA_LINKING_ONLY)) {
            event.setCanceled(true);
            return;
        }
        if (!sameDimension && info.hasFlag(LinkProperty.INTRA_LINKING_ONLY)) {
            event.setCanceled(true);
            return;
        }
        if (AgeManager.isAge(target)) {
            AgeData data = AgeManager.get(server, target);
            if (data == null || data.dead()) {
                event.setCanceled(true);
                return;
            }
            UUID expected = info.targetUuid().orElse(null);
            if (expected != null && !expected.equals(data.uuid())) {
                event.setCanceled(true);
                return;
            }
        }
        if (info.hasFlag(LinkProperty.DISARM) && (entity instanceof ItemEntity || entity instanceof LinkbookEntity)) {
            event.setCanceled(true);
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
        if (!permissions.canLeave(player, event.getOrigin().dimension()) || !permissions.canEnter(player, target)) {
            event.setCanceled(true);
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
    private static void onEndPlatform(LinkEvent.End event) {
        LinkInfo info = event.getInfo();
        BlockPos spawn = info.spawn().orElse(null);
        if (spawn == null || !info.hasFlag(LinkProperty.GENERATE_PLATFORM)) return;
        ServerLevel level = event.getDestination();
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
            PacketDistributor.sendToPlayersInDimension(level, new LinkParticlesPayload(entity.getX(), entity.getY(), entity.getZ()));
        }
    }
}
