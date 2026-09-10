package com.techbucketdivision.mystcraft.age.weather;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import static com.techbucketdivision.mystcraft.age.weather.WeatherStorageKeys.RAINING;
import static com.techbucketdivision.mystcraft.age.weather.WeatherStorageKeys.RAIN_COUNTER;
import static com.techbucketdivision.mystcraft.age.weather.WeatherStorageKeys.THUNDERING;
import static com.techbucketdivision.mystcraft.age.weather.WeatherStorageKeys.THUNDER_COUNTER;

/**
 * Vanilla-like rain/thunder cycle (REQUIREMENTS §4.3.4 WeatherNorm / WeatherFast / WeatherSlow). Each phase lasts
 * {@code base + rand(spread)} ticks; when the counter runs out the state flips and a new counter is rolled.
 */
public final class CyclingWeather extends AbstractWeather {
    private final int rainDurationBase, rainDuration;
    private final int rainCooldownBase, rainCooldown;
    private final int thunderDurationBase, thunderDuration;
    private final int thunderCooldownBase, thunderCooldown;

    public CyclingWeather(int rainDurationBase, int rainDuration, int rainCooldownBase, int rainCooldown,
                          int thunderDurationBase, int thunderDuration, int thunderCooldownBase, int thunderCooldown) {
        this.rainDurationBase = rainDurationBase;
        this.rainDuration = rainDuration;
        this.rainCooldownBase = rainCooldownBase;
        this.rainCooldown = rainCooldown;
        this.thunderDurationBase = thunderDurationBase;
        this.thunderDuration = thunderDuration;
        this.thunderCooldownBase = thunderCooldownBase;
        this.thunderCooldown = thunderCooldown;
    }

    /** Rain 12000+rand(12000) / cooldown 12000+rand(168000); thunder 3600+rand(12000) / 12000+rand(168000). */
    public static CyclingWeather normal() {
        return new CyclingWeather(12000, 12000, 12000, 168000, 3600, 12000, 12000, 168000);
    }

    /** Half of normal. */
    public static CyclingWeather fast() {
        return new CyclingWeather(6000, 6000, 6000, 84000, 1800, 6000, 6000, 84000);
    }

    /** Double of normal. */
    public static CyclingWeather slow() {
        return new CyclingWeather(24000, 24000, 24000, 336000, 7200, 24000, 24000, 336000);
    }

    @Override
    public void bindStorage(CompoundTag storage) {
        this.storage = storage;
        if (storage.getBooleanOr(RAINING, false)) rainStrength = 1.0;
        if (storage.getBooleanOr(THUNDERING, false)) thunderStrength = 1.0;
    }

    @Override
    public void updateRaining(Level level) {
        int thunderCounter = storage.getIntOr(THUNDER_COUNTER, 0);
        if (thunderCounter <= 0) {
            if (isThundering()) {
                thunderCounter = thunderDurationBase + (thunderDuration > 0 ? random().nextInt(thunderDuration) : 0);
            } else {
                thunderCounter = thunderCooldownBase + (thunderCooldown > 0 ? random().nextInt(thunderCooldown) : 0);
            }
            storage.putInt(THUNDER_COUNTER, thunderCounter);
        } else {
            storage.putInt(THUNDER_COUNTER, --thunderCounter);
            if (thunderCounter <= 0) {
                storage.putBoolean(THUNDERING, !isThundering());
                markDirty();
            }
        }

        int rainCounter = storage.getIntOr(RAIN_COUNTER, 0);
        if (rainCounter <= 0) {
            if (isRaining()) {
                rainCounter = rainDurationBase + (rainDuration > 0 ? random().nextInt(rainDuration) : 0);
            } else {
                rainCounter = rainCooldownBase + (rainCooldown > 0 ? random().nextInt(rainCooldown) : 0);
            }
            storage.putInt(RAIN_COUNTER, rainCounter);
        } else {
            storage.putInt(RAIN_COUNTER, --rainCounter);
            if (rainCounter <= 0) {
                storage.putBoolean(RAINING, !isRaining());
                markDirty();
            }
        }

        rainStrength = step(rainStrength, isRaining());
        thunderStrength = step(thunderStrength, isThundering());
    }

    @Override
    public void tick(ServerLevel level, LevelChunk chunk) {
        tickLightning(level, chunk, true);
    }

    @Override
    public boolean isRaining() {
        return storage.getBooleanOr(RAINING, false);
    }

    @Override
    public boolean isThundering() {
        return storage.getBooleanOr(THUNDERING, false);
    }

    /** Forces the rain phase to end (or start) next tick. */
    @Override
    public void togglePrecipitation() {
        storage.putInt(RAIN_COUNTER, 1);
        markDirty();
    }

    /** Clears all weather state. */
    public void reset() {
        storage.putInt(RAIN_COUNTER, 0);
        storage.putBoolean(RAINING, false);
        storage.putInt(THUNDER_COUNTER, 0);
        storage.putBoolean(THUNDERING, false);
        markDirty();
    }
}
