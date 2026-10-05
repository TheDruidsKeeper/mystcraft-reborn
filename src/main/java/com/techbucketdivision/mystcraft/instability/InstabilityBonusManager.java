package com.techbucketdivision.mystcraft.instability;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.api.instability.InstabilityBonus;
import com.techbucketdivision.mystcraft.api.instability.InstabilityBonusProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-Age collection of {@link InstabilityBonus}es (original spec §6.6). Providers are registered globally; the base
 * mod registers none. Bonuses that implement {@link Listener} receive the relevant game events.
 */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class InstabilityBonusManager {

    /** Optional event hooks for bonuses. */
    public interface Listener {
        default void onDeath(LivingDeathEvent event) {}

        default void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {}

        default void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {}

        default void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {}
    }

    private static final Set<InstabilityBonusProvider> PROVIDERS = new LinkedHashSet<>();
    private static final Map<ResourceKey<Level>, InstabilityBonusManager> MANAGERS = new ConcurrentHashMap<>();

    public static synchronized void registerProvider(InstabilityBonusProvider provider) {
        PROVIDERS.add(provider);
    }

    /** The manager for an Age level (created on first use). */
    public static InstabilityBonusManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level.dimension(), k -> {
            InstabilityBonusManager m = new InstabilityBonusManager(level.dimension());
            synchronized (InstabilityBonusManager.class) {
                for (InstabilityBonusProvider p : PROVIDERS) {
                    try {
                        p.register(m, level);
                    } catch (Exception e) {
                        Mystcraft.LOGGER.error("Instability bonus provider {} failed for {}", p, level.dimension().identifier(), e);
                    }
                }
            }
            return m;
        });
    }

    private final ResourceKey<Level> levelKey;
    private final List<InstabilityBonus> bonuses = new ArrayList<>();
    private int total;

    private InstabilityBonusManager(ResourceKey<Level> levelKey) {
        this.levelKey = levelKey;
    }

    public ResourceKey<Level> levelKey() {
        return levelKey;
    }

    public synchronized void register(InstabilityBonus bonus) {
        bonuses.add(bonus);
    }

    public synchronized List<InstabilityBonus> bonuses() {
        return Collections.unmodifiableList(new ArrayList<>(bonuses));
    }

    /** Sum of all bonus values as of the last tick. */
    public int total() {
        return total;
    }

    public synchronized void tick(ServerLevel level) {
        int sum = 0;
        for (InstabilityBonus b : bonuses) {
            b.tick(level);
            sum += b.value();
        }
        total = sum;
    }

    // --- event dispatch ----------------------------------------------------------------------------------------

    private static void forEachListener(java.util.function.Consumer<Listener> action) {
        for (InstabilityBonusManager m : MANAGERS.values()) {
            for (InstabilityBonus b : m.bonuses()) {
                if (b instanceof Listener l) action.accept(l);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        forEachListener(l -> l.onDeath(event));
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        forEachListener(l -> l.onPlayerLoggedIn(event));
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forEachListener(l -> l.onPlayerLoggedOut(event));
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        forEachListener(l -> l.onPlayerChangedDimension(event));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MANAGERS.clear();
    }
}
