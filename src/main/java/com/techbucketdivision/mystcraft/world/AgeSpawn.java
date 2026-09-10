package com.techbucketdivision.mystcraft.world;

import com.mojang.datafixers.util.Pair;
import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.api.symbol.logic.BiomeController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spawn search for Ages (REQUIREMENTS §5.3 {@code verifySpawn}) and the 5×5 cobblestone spawn platform
 * ({@code MystWorldGenerator}). Server thread only.
 */
public final class AgeSpawn {
    private AgeSpawn() {}

    private static final int SEARCH_RADIUS = 256;
    private static final int RANDOM_TRIES = 1000;
    private static final int RANDOM_SPREAD = 64;
    private static final Set<String> SPAWN_BIOME_PATHS = Set.of("forest", "plains", "taiga", "jungle", "birch_forest",
            "flower_forest", "sunflower_plains", "sparse_jungle", "meadow", "cherry_grove");

    /**
     * Returns the Age's spawn, determining (and storing) it on first use: biome search around the origin, then up to
     * 1000 random positions within ±64 needing a non-bedrock solid top block, then raise until air. The platform is
     * placed when the spawn is newly determined.
     */
    public static BlockPos findSpawn(ServerLevel level, AgeController controller) {
        AgeData data = controller.ageData();
        BlockPos existing = data.spawn();
        if (existing != null) return existing;

        RandomSource rand = RandomSource.create(data.seed());
        int seaLevel = controller.seaLevel();
        int cx = 0;
        int cz = 0;
        BlockPos biomePos = findSpawnBiome(level, controller, rand, seaLevel);
        if (biomePos != null) {
            cx = biomePos.getX();
            cz = biomePos.getZ();
        }

        BlockPos result = null;
        for (int i = 0; i < RANDOM_TRIES; i++) {
            int x = cx + rand.nextInt(RANDOM_SPREAD * 2 + 1) - RANDOM_SPREAD;
            int z = cz + rand.nextInt(RANDOM_SPREAD * 2 + 1) - RANDOM_SPREAD;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (top < level.getMinY()) continue;
            BlockState state = level.getBlockState(new BlockPos(x, top, z));
            if (state.is(Blocks.BEDROCK) || !state.isSolid()) continue;
            result = new BlockPos(x, top + 1, z);
            break;
        }
        if (result == null) {
            result = new BlockPos(cx, Math.max(seaLevel, level.getMinY() + 1), cz);
            Mystcraft.LOGGER.warn("No solid spawn found for Age {}; using {}", data.name(), result);
        }
        int maxY = level.getMaxY();
        while (result.getY() < maxY && !level.getBlockState(result).isAir()) {
            result = result.above();
        }
        data.setSpawn(result);
        placePlatform(level, result);
        return result;
    }

    private static @Nullable BlockPos findSpawnBiome(ServerLevel level, AgeController controller, RandomSource rand, int y) {
        BiomeController bc = controller.biomeController();
        if (bc == null) return null;
        List<Holder<Biome>> possible = bc.possibleBiomes();
        Set<Holder<Biome>> preferred = new HashSet<>();
        for (Holder<Biome> b : possible) {
            if (isSpawnBiome(b)) preferred.add(b);
        }
        if (preferred.isEmpty()) return null;
        try {
            BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
            Pair<BlockPos, Holder<Biome>> found = source.findBiomeHorizontal(0, y, 0, SEARCH_RADIUS, preferred::contains, rand,
                    level.getChunkSource().randomState().sampler());
            return found == null ? null : found.getFirst();
        } catch (RuntimeException e) {
            Mystcraft.LOGGER.debug("Spawn biome search failed for Age {}", controller.ageData().name(), e);
            return null;
        }
    }

    /** Forest / plains / taiga / jungle family (the original's {@code biomesToSpawnIn}). */
    public static boolean isSpawnBiome(Holder<Biome> biome) {
        return biome.unwrapKey().map(ResourceKey::identifier)
                .map(id -> id.getNamespace().equals("minecraft") && SPAWN_BIOME_PATHS.contains(id.getPath()))
                .orElse(false);
    }

    /** 5×5 cobblestone platform one block below {@code spawn}, with the 4 blocks above cleared. */
    public static void placePlatform(LevelAccessor level, BlockPos spawn) {
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                at.set(spawn.getX() + dx, spawn.getY() - 1, spawn.getZ() + dz);
                if (at.getY() >= level.getMinY()) level.setBlock(at, cobble, 3);
                for (int dy = 0; dy < 4; dy++) {
                    at.set(spawn.getX() + dx, spawn.getY() + dy, spawn.getZ() + dz);
                    if (at.getY() <= level.getMaxY() && !level.getBlockState(at).isAir()) level.setBlock(at, air, 3);
                }
            }
        }
    }
}
