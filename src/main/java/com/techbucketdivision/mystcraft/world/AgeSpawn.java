package com.techbucketdivision.mystcraft.world;

import com.mojang.datafixers.util.Pair;
import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.api.symbol.logic.BiomeController;
import com.techbucketdivision.mystcraft.world.feature.StarFissurePopulator;
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
        // A Star Fissure always generates in chunk (0,0); keep the spawn within reach of it instead of biome-hunting.
        boolean nearOrigin = controller.populators().stream().anyMatch(p -> p instanceof StarFissurePopulator);
        BlockPos biomePos = nearOrigin ? null : findSpawnBiome(level, controller, rand, seaLevel);
        if (biomePos != null) {
            cx = biomePos.getX();
            cz = biomePos.getZ();
        }

        BlockPos result = null;
        int tries = 0;
        // MOTION_BLOCKING_NO_LEAVES counts water as blocking, so an ocean surface is not "ground": require a block
        // with a collision shape under the feet. The spread widens after the first half of the tries so ocean or
        // void Ages still find a shore / island.
        for (int i = 0; i < RANDOM_TRIES; i++) {
            tries++;
            int spread = i < RANDOM_TRIES / 2 ? RANDOM_SPREAD : RANDOM_SPREAD * 3;
            int x = cx + rand.nextInt(spread * 2 + 1) - spread;
            int z = cz + rand.nextInt(spread * 2 + 1) - spread;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (top < level.getMinY()) continue;
            BlockPos ground = new BlockPos(x, top, z);
            BlockState state = level.getBlockState(ground);
            if (state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty() || state.getCollisionShape(level, ground).isEmpty()) continue;
            result = ground.above();
            break;
        }
        if (result == null) {
            result = new BlockPos(cx, Math.max(seaLevel, level.getMinY() + 1), cz);
            Mystcraft.LOGGER.warn("[spawn] no ground found for Age '{}' after {} tries (water / void everywhere?); using {}", data.name(), tries, result.toShortString());
        }
        int maxY = level.getMaxY();
        while (result.getY() < maxY && !level.getBlockState(result).isAir()) {
            result = result.above();
        }
        data.setSpawn(result);
        placePlatform(level, result);
        Mystcraft.LOGGER.info("[spawn] Age '{}' spawn determined at {} after {} tries ({}, platform placed)", data.name(), result.toShortString(), tries,
                nearOrigin ? "kept near the star fissure at chunk 0,0" : biomePos == null ? "no preferred biome found" : "preferred biome at " + biomePos.toShortString());
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

    /** Blocks scanned downwards when snapping an arrival onto the ground; beyond this the Age is treated as sky. */
    public static final int SNAP_SCAN = 32;

    /**
     * Moves {@code pos} down onto the first block with collision below it (max {@link #SNAP_SCAN} blocks), so an
     * arrival determined from a stale heightmap or an overworld-sized y does not float above the terrain. Positions
     * with nothing below (skylands, void) are returned unchanged.
     */
    public static BlockPos snapToGround(ServerLevel level, BlockPos pos) {
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // force generation so the scan sees real terrain
        BlockPos.MutableBlockPos at = pos.mutable();
        if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
            // Buried: rise until there is air for the feet (teleportEntity also pushes the entity out of geometry).
            int top = level.getMaxY();
            while (at.getY() < top && !level.getBlockState(at).getCollisionShape(level, at).isEmpty()) at.move(0, 1, 0);
            return at.immutable();
        }
        for (int i = 0; i < SNAP_SCAN && at.getY() - 1 > level.getMinY(); i++) {
            BlockPos below = at.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                if (at.getY() != pos.getY()) {
                    Mystcraft.LOGGER.info("[spawn] snapped arrival {} down to {} in {}", pos.toShortString(), at.toShortString(),
                            level.dimension().identifier());
                }
                return at.immutable();
            }
            at.move(0, -1, 0);
        }
        Mystcraft.LOGGER.info("[spawn] no ground within {} blocks below {} in {}; arriving airborne on a platform", SNAP_SCAN,
                pos.toShortString(), level.dimension().identifier());
        return pos;
    }

    /**
     * Guarantees a 3x3 cobblestone pad under {@code spawn} and two blocks of head room above it. Only blocks without
     * collision (air, water, lava, plants) are replaced below; above, everything but air is cleared so a tree or cliff
     * cannot trap the traveller. Idempotent, cheap, called on every Age arrival.
     */
    public static void ensureArrivalPlatform(ServerLevel level, BlockPos spawn) {
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int placed = 0, cleared = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                at.set(spawn.getX() + dx, spawn.getY() - 1, spawn.getZ() + dz);
                if (at.getY() >= level.getMinY() && level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                    level.setBlock(at, cobble, 3);
                    placed++;
                }
                for (int dy = 0; dy < 2; dy++) {
                    at.set(spawn.getX() + dx, spawn.getY() + dy, spawn.getZ() + dz);
                    if (at.getY() <= level.getMaxY() && !level.getBlockState(at).isAir()) {
                        level.setBlock(at, air, 3);
                        cleared++;
                    }
                }
            }
        }
        if (placed > 0 || cleared > 0) {
            Mystcraft.LOGGER.info("[spawn] arrival platform at {} in {}: placed {} cobblestone, cleared {} blocks",
                    spawn.toShortString(), level.dimension().identifier(), placed, cleared);
        }
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
