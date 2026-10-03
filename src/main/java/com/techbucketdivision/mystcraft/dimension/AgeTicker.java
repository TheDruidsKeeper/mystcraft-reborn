package com.techbucketdivision.mystcraft.dimension;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.api.symbol.logic.EnvironmentalEffect;
import com.techbucketdivision.mystcraft.api.symbol.logic.WeatherController;
import com.techbucketdivision.mystcraft.instability.InstabilityBonusManager;
import com.techbucketdivision.mystcraft.instability.InstabilityController;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server tick of every Age level (REQUIREMENTS §5.3 updateWeather / canDoLightning): advances age time, ticks the
 * bonus manager and weather, runs weather / environmental / instability chunk ticks over the chunks around players,
 * handles sleeping and periodically resends {@code AgeData} to the level's players.
 */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class AgeTicker {
    private AgeTicker() {}

    /** Chunk-tick radius around each player is capped to this many chunks. */
    public static final int MAX_TICK_RADIUS = 8;
    public static final int RESEND_TICKS_DIRTY = 200;
    public static final int RESEND_TICKS_IDLE = 1200;
    /** How often the age clock is flushed to the saved data (it is not dirty-tracked per tick). */
    public static final int SAVE_TIME_TICKS = 600;
    /** A brand-new Age's clock starts somewhere in its first ten (vanilla-length) days. */
    public static final long START_TIME_SPAN = 24000L * 10;

    /**
     * A new Age starts at a random point of its day/night cycle (seeded by the Age, so it is reproducible): the
     * celestials already carry random phases, and the clock is offset too so a fresh Age can be met at any hour.
     * The offset is never 0 so "never ticked" stays distinguishable from "started at midnight".
     */
    private static void startAtRandomTime(AgeData data) {
        long start = 1L + new Random(data.seed() ^ 0x5A17C10CL).nextLong(START_TIME_SPAN - 1);
        data.setWorldTime(start);
        data.markSaveNeeded();
    }

    private static final class State {
        int ticksSinceSync;
        boolean weatherDirty;
        int lastRevision = -1;
    }

    private static final Map<ResourceKey<Level>, State> STATES = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!AgeData.isAgeLevel(level.dimension())) return;
        AgeController controller = AgeControllers.server(level);
        if (controller == null) return;
        AgeData data = controller.ageData();
        State state = STATES.get(level.dimension());
        if (state == null) {
            state = new State();
            STATES.put(level.dimension(), state);
            if (data.worldTime() == 0L) {
                startAtRandomTime(data);
                state.ticksSinceSync = RESEND_TICKS_IDLE; // push the new clock to anyone already inside
            }
            float angle = controller.celestialAngle(data.worldTime(), 0f);
            Mystcraft.LOGGER.info("[age] {} ({}) ticking: time {}, {} celestials ({} giving light), celestial angle {} ({})",
                    data.name(), level.dimension().identifier(), data.worldTime(), controller.celestials().size(),
                    controller.celestials().stream().filter(c -> c.providesLight()).count(), angle,
                    angle < 0.25f || angle > 0.75f ? "day" : "night");
        }

        // Age time.
        if (level.getGameRules().get(GameRules.ADVANCE_TIME)) {
            data.setWorldTime(data.worldTime() + 1);
            if (data.worldTime() % SAVE_TIME_TICKS == 0) data.markSaveNeeded();
        }

        InstabilityBonusManager.get(level).tick(level);

        // Weather: advance strengths and mirror them into the vanilla fields used by rendering/gameplay.
        WeatherController weather = controller.weather();
        if (weather != null) {
            weather.updateRaining(level);
            level.setRainLevel(weather.getRainStrength());
            level.setThunderLevel(weather.getThunderStrength());
            if (weather.consumeDirty()) state.weatherDirty = true;
        }

        List<ServerPlayer> players = level.players();
        if (!players.isEmpty()) {
            tickChunks(level, controller, weather, players);
            handleSleep(level, controller, data, players);
        }

        // Periodic / on-change resend.
        state.ticksSinceSync++;
        boolean revisionChanged = data.revision() != state.lastRevision;
        int interval = state.weatherDirty ? RESEND_TICKS_DIRTY : RESEND_TICKS_IDLE;
        if ((revisionChanged || state.ticksSinceSync >= interval) && !players.isEmpty()) {
            AgeManager.sendAgeDataToLevel(level, data);
            state.ticksSinceSync = 0;
            state.weatherDirty = false;
            state.lastRevision = data.revision();
        } else if (players.isEmpty()) {
            state.lastRevision = data.revision();
        }
    }

    private static void tickChunks(ServerLevel level, AgeController controller, @Nullable WeatherController weather, List<ServerPlayer> players) {
        MinecraftServer server = level.getServer();
        int radius = MAX_TICK_RADIUS;
        if (server != null) {
            radius = Math.min(MAX_TICK_RADIUS, server.getPlayerList().getViewDistance());
        }
        LongSet seen = new LongOpenHashSet();
        List<LevelChunk> chunks = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (player.isSpectator() && players.size() > 1) continue;
            ChunkPos center = player.chunkPosition();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int cx = center.x() + dx;
                    int cz = center.z() + dz;
                    if (!seen.add(ChunkPos.pack(cx, cz))) continue;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk != null) chunks.add(chunk);
                }
            }
        }
        InstabilityController instability = InstabilityController.get(level);
        List<EnvironmentalEffect> effects = controller.effects();
        for (LevelChunk chunk : chunks) {
            try {
                if (weather != null) weather.tick(level, chunk);
                for (EnvironmentalEffect effect : effects) effect.tick(level, chunk);
                if (instability != null) instability.tick(chunk);
            } catch (Exception e) {
                Mystcraft.LOGGER.error("Error ticking chunk {} of {}", chunk.getPos(), level.dimension().identifier(), e);
            }
        }
    }

    private static void handleSleep(ServerLevel level, AgeController controller, AgeData data, List<ServerPlayer> players) {
        boolean allSleeping = true;
        for (ServerPlayer player : players) {
            if (player.isSpectator()) continue;
            if (!player.isSleepingLongEnough()) {
                allSleeping = false;
                break;
            }
        }
        if (!allSleeping) return;
        long time = data.worldTime();
        data.setWorldTime(time + controller.timeToSunrise(time));
        data.markDirty();
        for (ServerPlayer player : players) {
            if (player.isSleeping()) player.stopSleepInBed(false, false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }
}
