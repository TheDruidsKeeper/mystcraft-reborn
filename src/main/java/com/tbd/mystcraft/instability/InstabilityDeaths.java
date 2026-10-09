package com.tbd.mystcraft.instability;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.entity.ColoredLightningBolt;
import com.tbd.mystcraft.registry.ModCriteria;
import com.tbd.mystcraft.registry.ModDamageTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Death by Typo": a player killed by the Age's instability. Decay, meteors and instability explosions carry the
 * {@link ModDamageTypes#INSTABILITY} damage types; the lightning effect strikes with vanilla lightning damage, so a
 * strike by a {@link ColoredLightningBolt} is remembered for a moment and matched against a lightning death.
 */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class InstabilityDeaths {
    private InstabilityDeaths() {}

    /** Ticks a remembered instability lightning strike stays valid for a death. */
    public static final long STRIKE_WINDOW = 5L;

    private static final Map<UUID, Long> STRUCK = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onStruck(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof Player player && event.getLightning() instanceof ColoredLightningBolt) {
            STRUCK.put(player.getUUID(), player.level().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isInstabilityDeath(player, event.getSource())) return;
        Mystcraft.LOGGER.info("[instability] {} was killed by instability ({})", player.getPlainTextName(), event.getSource().getMsgId());
        ModCriteria.KILLED_BY_INSTABILITY.get().trigger(player);
    }

    /** Whether {@code source} killing {@code player} now counts as the Age's instability. */
    public static boolean isInstabilityDeath(Player player, DamageSource source) {
        if (source.is(ModDamageTypes.INSTABILITY)) return true;
        if (!source.is(DamageTypes.LIGHTNING_BOLT)) return false;
        Long struck = STRUCK.remove(player.getUUID());
        return struck != null && player.level().getGameTime() - struck <= STRIKE_WINDOW;
    }

    /** Test hook: remember a strike as {@link #onStruck} would. */
    public static void rememberStrike(Player player) {
        STRUCK.put(player.getUUID(), player.level().getGameTime());
    }
}
