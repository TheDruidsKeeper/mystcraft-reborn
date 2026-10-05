package com.techbucketdivision.mystcraft.instability;

import com.techbucketdivision.mystcraft.api.instability.InstabilityBonus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

/**
 * Stability bonus unlocked by killing a named player inside the Age (original spec §6.6). Value is {@code -current};
 * set to {@code max} when the player is killed by another player, {@code max/2} on other deaths; decays per tick.
 */
public final class PlayerKilledBonus implements InstabilityBonus, InstabilityBonusManager.Listener {
    private final InstabilityBonusManager manager;
    private final ResourceKey<Level> levelKey;
    private final String playerName;
    private final int max;
    private final float decayRate;
    private float current;

    public PlayerKilledBonus(InstabilityBonusManager manager, ServerLevel level, String playerName, int max, float decayRate) {
        this.manager = manager;
        this.levelKey = level.dimension();
        this.playerName = playerName;
        this.max = max;
        this.decayRate = decayRate;
        manager.register(this);
    }

    @Override
    public String name() {
        return "Player Killed: " + playerName;
    }

    @Override
    public int value() {
        return -(int) current;
    }

    @Override
    public void tick(ServerLevel level) {
        current = Math.max(0, current - decayRate);
    }

    @Override
    public void onDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!entity.level().dimension().equals(levelKey) || !entity.getName().getString().equals(playerName)) return;
        Entity killer = event.getSource().getEntity();
        if (killer instanceof Player) {
            current = max;
            announce("instability.mystcraft.bonus.death", playerName, killer.getName().getString());
        } else {
            current = Math.max(max / 2f, current);
            announce("instability.mystcraft.bonus.death.partial", playerName);
        }
    }

    @Override
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player p = event.getEntity();
        if (p.level().dimension().equals(levelKey) && p.getName().getString().equals(playerName)) {
            announce("instability.mystcraft.bonus.death.alert", playerName);
        }
    }

    @Override
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player p = event.getEntity();
        if (event.getTo().equals(levelKey) && p.getName().getString().equals(playerName)) {
            announce("instability.mystcraft.bonus.death.alert", playerName);
        }
    }

    private void announce(String key, Object... args) {
        Announcements.toLevel(levelKey, key, args);
    }

    /** Shared helper: send a translatable chat message to every player in a level (if instability is on). */
    static final class Announcements {
        private Announcements() {}

        static void toLevel(ResourceKey<Level> key, String translationKey, Object... args) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            @Nullable ServerLevel level = server.getLevel(key);
            if (level == null) return;
            InstabilityController controller = InstabilityController.get(level);
            if (controller != null && !controller.isEnabled()) return;
            Component msg = Component.translatable(translationKey, args);
            for (ServerPlayer player : level.players()) player.sendSystemMessage(msg);
        }
    }
}
