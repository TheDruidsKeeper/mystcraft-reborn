package com.techbucketdivision.mystcraft.instability;

import com.techbucketdivision.mystcraft.api.instability.InstabilityBonus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Instability penalty that grows while a named player is inside the Age and decays while absent; resets on death
 * (original spec §6.6).
 */
public final class PlayerTrollPenalty implements InstabilityBonus, InstabilityBonusManager.Listener {
    private final ResourceKey<Level> levelKey;
    private final String playerName;
    private final int max;
    private final float rate;
    private float current;
    private boolean playerInLevel;

    public PlayerTrollPenalty(InstabilityBonusManager manager, ServerLevel level, String playerName, int max, float rate) {
        this.levelKey = level.dimension();
        this.playerName = playerName;
        this.max = max;
        this.rate = rate;
        for (var p : level.players()) {
            if (p.getName().getString().equals(playerName)) playerInLevel = true;
        }
        manager.register(this);
    }

    @Override
    public String name() {
        return "Player: " + playerName;
    }

    @Override
    public int value() {
        return (int) current;
    }

    @Override
    public void tick(ServerLevel level) {
        current = playerInLevel ? Math.min(max, current + rate) : Math.max(0, current - rate);
    }

    @Override
    public void onDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().dimension().equals(levelKey) && entity.getName().getString().equals(playerName)) {
            current = 0;
            PlayerKilledBonus.Announcements.toLevel(levelKey, "instability.mystcraft.bonus.troll.death", playerName);
        }
    }

    @Override
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player p = event.getEntity();
        if (p.level().dimension().equals(levelKey) && p.getName().getString().equals(playerName)) {
            playerInLevel = true;
            PlayerKilledBonus.Announcements.toLevel(levelKey, "instability.mystcraft.bonus.troll.alert", playerName);
        }
    }

    @Override
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player p = event.getEntity();
        if (p.level().dimension().equals(levelKey) && p.getName().getString().equals(playerName)) {
            playerInLevel = false;
            PlayerKilledBonus.Announcements.toLevel(levelKey, "instability.mystcraft.bonus.troll.left", playerName);
        }
    }

    @Override
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player p = event.getEntity();
        if (!p.getName().getString().equals(playerName)) return;
        if (event.getFrom().equals(levelKey)) {
            playerInLevel = false;
            PlayerKilledBonus.Announcements.toLevel(levelKey, "instability.mystcraft.bonus.troll.left", playerName);
        }
        if (event.getTo().equals(levelKey)) {
            playerInLevel = true;
            PlayerKilledBonus.Announcements.toLevel(levelKey, "instability.mystcraft.bonus.troll.alert", playerName);
        }
    }
}
