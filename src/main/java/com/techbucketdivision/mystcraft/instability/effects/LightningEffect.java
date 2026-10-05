package com.techbucketdivision.mystcraft.instability.effects;

import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.api.symbol.ColorGradient;
import com.techbucketdivision.mystcraft.api.symbol.logic.EnvironmentalEffect;
import com.techbucketdivision.mystcraft.api.symbol.logic.WeatherController;
import com.techbucketdivision.mystcraft.entity.ColoredLightningBolt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Lightning strikes (original spec §4.3.10): 1/5000 per chunk tick while the Age is raining and thundering, else
 * 1/100000. With a gradient the bolt colour is {@code gradient(time / 12000)}.
 */
public final class LightningEffect implements EnvironmentalEffect {
    private final @Nullable ColorGradient gradient;
    private final ChunkLcg lcg = new ChunkLcg();

    public LightningEffect(@Nullable ColorGradient gradient) {
        this.gradient = gradient;
    }

    public LightningEffect() {
        this(null);
    }

    @Override
    public void tick(ServerLevel level, LevelChunk chunk) {
        AgeController age = AgeControllers.server(level);
        WeatherController weather = age == null ? null : age.weather();
        boolean storm = weather != null ? weather.isRaining() && weather.isThundering() : level.isRaining() && level.isThundering();
        int chance = storm ? 5000 : 100000;
        if (level.getRandom().nextInt(chance) != 0) return;
        int coords = lcg.next();
        int x = chunk.getPos().getMinBlockX() + ChunkLcg.localX(coords);
        int z = chunk.getPos().getMinBlockZ() + ChunkLcg.localZ(coords);
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        int color = ColoredLightningBolt.DEFAULT_COLOR;
        if (gradient != null && !gradient.isEmpty()) {
            long time = age == null ? level.getGameTime() : age.ageData().worldTime();
            color = gradient.getColor(time / 12000f).toRGB();
        }
        ColoredLightningBolt.strike(level, pos, color);
    }
}
