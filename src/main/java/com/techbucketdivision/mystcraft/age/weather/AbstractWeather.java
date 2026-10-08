package com.tbd.mystcraft.age.weather;

import com.tbd.mystcraft.api.symbol.logic.WeatherController;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Shared state (storage binding, strengths, lightning LCG) of the weather controllers. */
abstract class AbstractWeather implements WeatherController {
    protected static final double STRENGTH_STEP = 0.01;
    protected static final int LIGHTNING_CHANCE = 100000;

    private Random random = new Random();
    private int updateLCG = random.nextInt();

    protected CompoundTag storage = new CompoundTag();
    protected double rainStrength;
    protected double thunderStrength;
    private boolean dirty;

    @Override
    public void seedFromAge(long seed) {
        // Distinct salt so weather rolls do not share the same stream as spawn / blueprint fill.
        random = new Random(seed ^ 0xA6E5_17E5_7A7EL);
        updateLCG = random.nextInt();
    }

    protected Random random() {
        return random;
    }

    protected void markDirty() {
        dirty = true;
    }

    @Override
    public boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    @Override
    public float getRainStrength() {
        return (float) rainStrength;
    }

    @Override
    public float getThunderStrength() {
        return (float) thunderStrength;
    }

    /** Moves a strength by +-0.01 toward 1 or 0 and clamps. */
    protected static double step(double strength, boolean up) {
        strength += up ? STRENGTH_STEP : -STRENGTH_STEP;
        if (strength < 0.0) strength = 0.0;
        if (strength > 1.0) strength = 1.0;
        return strength;
    }

    /** Picks the next pseudo-random column of the chunk (vanilla's LCG walk). */
    protected BlockPos randomColumn(LevelChunk chunk) {
        int xBase = chunk.getPos().x() * 16;
        int zBase = chunk.getPos().z() * 16;
        updateLCG = updateLCG * 3 + 1013904223;
        int coords = updateLCG >> 2;
        return new BlockPos(xBase + (coords & 15), 0, zBase + (coords >> 8 & 15));
    }

    /** Base lightning behaviour: 1/100000 per chunk tick when raining and thundering, at a rain-exposed column. */
    protected void tickLightning(ServerLevel level, LevelChunk chunk, boolean requireLevelRain) {
        if (!isRaining() || !isThundering()) return;
        if (level.getRandom().nextInt(LIGHTNING_CHANCE) != 0) return;
        BlockPos column = randomColumn(chunk);
        BlockPos precip = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        boolean allowed = requireLevelRain ? level.isRainingAt(precip) : level.canSeeSky(precip);
        if (allowed) strike(level, precip);
    }

    /** Spawns a vanilla lightning bolt at the position (see API_NOTES F3). */
    public static void strike(ServerLevel level, BlockPos pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.snapTo(Vec3.atBottomCenterOf(pos));
        level.addFreshEntity(bolt);
    }
}
