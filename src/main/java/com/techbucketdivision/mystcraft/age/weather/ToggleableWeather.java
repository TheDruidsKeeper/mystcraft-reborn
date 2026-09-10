package com.techbucketdivision.mystcraft.age.weather;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import static com.techbucketdivision.mystcraft.age.weather.WeatherStorageKeys.DISABLED;
import static com.techbucketdivision.mystcraft.age.weather.WeatherStorageKeys.RESET_COOLDOWN;
import static com.techbucketdivision.mystcraft.age.weather.WeatherStorageKeys.RESET_COUNTER;

/**
 * Fixed weather that can be toggled off for {@link WeatherStorageKeys#RESET_COOLDOWN} ticks (REQUIREMENTS §4.3.4
 * WeatherOff / WeatherOn / WeatherCloudy / WeatherRain / WeatherSnow / WeatherStorm). Storage: {@code disabled},
 * {@code reset_counter}.
 */
public final class ToggleableWeather extends AbstractWeather {

    /**
     * @param enabledRain     rain strength while enabled
     * @param enabledThunder  thunder strength while enabled
     * @param disabledRain    rain strength while toggled off
     * @param rainEnabled     override of biome rain (null = biome default) while enabled
     * @param snowEnabled     override of biome snow (null = biome default) while enabled
     * @param minTemperature  temperature floor while enabled (null = none)
     * @param maxTemperature  temperature ceiling while enabled (null = none)
     * @param lightningIgnoresBiomeRain  storm variant: strike wherever the sky is visible instead of only where it rains
     */
    public record Settings(double enabledRain, double enabledThunder, double disabledRain,
                           @Nullable Boolean rainEnabled, @Nullable Boolean snowEnabled,
                           @Nullable Float minTemperature, @Nullable Float maxTemperature,
                           boolean lightningIgnoresBiomeRain) {}

    /** No Weather: enabled = rain 0; toggled off = rain 1. */
    public static final Settings OFF = new Settings(0, 0, 1, null, null, null, null, false);
    /** Eternal Weather: rain 1 with the biome's native precipitation. */
    public static final Settings ON = new Settings(1, 0, 0, null, null, null, null, false);
    /** Overcast: rain strength 1 but no rain or snow anywhere. */
    public static final Settings CLOUDY = new Settings(1, 0, 0, false, false, null, null, false);
    /** Eternal Rain: rain enabled, snow disabled, temperature floor 0.20. */
    public static final Settings RAIN = new Settings(1, 0, 0, true, false, 0.20f, null, false);
    /** Eternal Snow: rain and snow enabled, temperature ceiling 0.10. */
    public static final Settings SNOW = new Settings(1, 0, 0, true, true, null, 0.10f, false);
    /** Eternal Storm: rain 1, thunder 1, snow disabled, temperature floor 0.20, lightning wherever the sky is visible. */
    public static final Settings STORM = new Settings(1, 1, 0, true, false, 0.20f, null, true);

    private final Settings settings;
    private boolean enabled = true;

    public ToggleableWeather(Settings settings) {
        this.settings = settings;
    }

    public static ToggleableWeather off() { return new ToggleableWeather(OFF); }
    public static ToggleableWeather on() { return new ToggleableWeather(ON); }
    public static ToggleableWeather cloudy() { return new ToggleableWeather(CLOUDY); }
    public static ToggleableWeather rain() { return new ToggleableWeather(RAIN); }
    public static ToggleableWeather snow() { return new ToggleableWeather(SNOW); }
    public static ToggleableWeather storm() { return new ToggleableWeather(STORM); }

    public Settings settings() {
        return settings;
    }

    @Override
    public void bindStorage(CompoundTag storage) {
        this.storage = storage;
        rainStrength = 0.0;
        thunderStrength = 0.0;
        if (storage.getBooleanOr(DISABLED, false)) applyDisabled(false); else applyEnabled(false);
    }

    @Override
    public void updateRaining(Level level) {
        int resetCounter = storage.getIntOr(RESET_COUNTER, 0);
        storage.putInt(RESET_COUNTER, --resetCounter);
        if (resetCounter == 0) enable();
    }

    @Override
    public void tick(ServerLevel level, LevelChunk chunk) {
        tickLightning(level, chunk, !settings.lightningIgnoresBiomeRain());
    }

    @Override
    public boolean isRaining() {
        return rainStrength > 0.2;
    }

    @Override
    public boolean isThundering() {
        return thunderStrength > 0.2;
    }

    public boolean isDisabled() {
        return !enabled;
    }

    @Override
    public void togglePrecipitation() {
        if (isDisabled()) enable(); else disable();
    }

    public void reset() {
        enable();
    }

    private void enable() {
        storage.putInt(RESET_COUNTER, 0);
        storage.putBoolean(DISABLED, false);
        applyEnabled(true);
    }

    private void disable() {
        storage.putInt(RESET_COUNTER, RESET_COOLDOWN);
        storage.putBoolean(DISABLED, true);
        applyDisabled(true);
    }

    private void applyEnabled(boolean dirty) {
        enabled = true;
        rainStrength = settings.enabledRain();
        thunderStrength = settings.enabledThunder();
        if (dirty) markDirty();
    }

    private void applyDisabled(boolean dirty) {
        enabled = false;
        rainStrength = settings.disabledRain();
        thunderStrength = 0.0;
        if (dirty) markDirty();
    }

    @Override
    public float getTemperature(Holder<Biome> biome, float baseTemperature) {
        if (!enabled) return baseTemperature;
        float t = baseTemperature;
        if (settings.minTemperature() != null && t < settings.minTemperature()) t = settings.minTemperature();
        if (settings.maxTemperature() != null && t > settings.maxTemperature()) t = settings.maxTemperature();
        return t;
    }

    @Override
    public boolean getRainEnabled(Holder<Biome> biome, boolean biomeDefault) {
        if (!enabled || settings.rainEnabled() == null) return biomeDefault;
        return settings.rainEnabled();
    }

    @Override
    public boolean getSnowEnabled(Holder<Biome> biome, boolean biomeDefault) {
        if (!enabled || settings.snowEnabled() == null) return biomeDefault;
        return settings.snowEnabled();
    }
}
