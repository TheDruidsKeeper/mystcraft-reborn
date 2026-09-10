package com.techbucketdivision.mystcraft.api.symbol.logic;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;

/** Per-Age weather. Exactly one per Age. State is persisted in {@code AgeData.dataCompound("weather")}. */
public interface WeatherController {
    /** Binds persistent storage; called once when the controller is constructed. */
    void bindStorage(CompoundTag storage);

    /** Advance rain/thunder strengths by one tick toward their targets (both sides). */
    void updateRaining(Level level);

    /** Server chunk tick: lightning etc. */
    void tick(ServerLevel level, LevelChunk chunk);

    boolean isRaining();

    boolean isThundering();

    float getRainStrength();

    float getThunderStrength();

    /** Command / sleep toggle. */
    void togglePrecipitation();

    /** Base temperature adjustment for a biome under this weather (e.g. eternal snow forces <= 0.10). */
    default float getTemperature(Holder<Biome> biome, float baseTemperature) {
        return baseTemperature;
    }

    default boolean getRainEnabled(Holder<Biome> biome, boolean biomeDefault) {
        return biomeDefault;
    }

    default boolean getSnowEnabled(Holder<Biome> biome, boolean biomeDefault) {
        return biomeDefault;
    }

    /** True if the storage changed and clients need a resend. */
    boolean consumeDirty();
}
