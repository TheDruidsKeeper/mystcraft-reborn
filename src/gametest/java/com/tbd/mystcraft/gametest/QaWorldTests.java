package com.tbd.mystcraft.gametest;

import com.tbd.mystcraft.age.AgeController;
import com.tbd.mystcraft.age.AgeControllers;
import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.age.AgeManager;
import com.tbd.mystcraft.api.symbol.logic.Populator;
import com.tbd.mystcraft.command.QaShelf;
import com.tbd.mystcraft.instability.InstabilityController;
import com.tbd.mystcraft.item.DescriptiveBookItem;
import com.tbd.mystcraft.registry.ModBlocks;
import com.tbd.mystcraft.registry.ModStructures;
import com.tbd.mystcraft.world.feature.CrystalFormationPopulator;
import com.tbd.mystcraft.world.feature.LakesPopulator;
import com.tbd.mystcraft.world.feature.ObelisksPopulator;
import com.tbd.mystcraft.world.feature.RavinesAlteration;
import com.tbd.mystcraft.world.feature.SpikesPopulator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Block-level facts about the QA shelf worlds (docs/QA.md): the same seeds and pages as {@code /myst-dev qa-shelf}, so
 * the shelf is only needed for what these tests cannot see (how it looks). Each test binds a shelf case, generates a
 * few chunks and asserts terrain type, materials, biome layout, structure starts, weather or instability inputs.
 */
@ForEachTest(groups = "qa")
public class QaWorldTests {

    // --- helpers -------------------------------------------------------------------------------------------------

    private static QaShelf.Case qa(String id) {
        return QaShelf.cases().stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no QA case " + id));
    }

    private record World(ServerLevel level, AgeController controller, AgeData data) {}

    private static World world(ExtendedGameTestHelper helper, String caseId) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = QaShelf.bind(server, qa(caseId));
        AgeData data = DescriptiveBookItem.getAgeData(server, book);
        helper.assertNotNull(data, caseId + " bound");
        ServerLevel level = AgeManager.getOrCreateLevel(server, data);
        AgeController controller = AgeControllers.server(level);
        helper.assertNotNull(controller, caseId + " controller");
        return new World(level, controller, data);
    }

    private static void generate(ServerLevel level, int radius) {
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) level.getChunk(cx, cz);
        }
    }

    /** Number of blocks matching {@code test} in the given chunk, between {@code minY} and {@code maxY}. */
    private static int census(ServerLevel level, int chunkX, int chunkZ, int minY, int maxY, Predicate<BlockState> test) {
        int n = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    at.set((chunkX << 4) + x, y, (chunkZ << 4) + z);
                    if (test.test(level.getBlockState(at))) n++;
                }
            }
        }
        return n;
    }

    private static int census(ServerLevel level, int chunkX, int chunkZ, Block block) {
        return census(level, chunkX, chunkZ, level.getMinY(), level.getMaxY(), s -> s.is(block));
    }

    /** Heightmap (MOTION_BLOCKING) of every column of a chunk as min/max. */
    private static int[] heightRange(ServerLevel level, int chunkX, int chunkZ) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (chunkX << 4) + x, (chunkZ << 4) + z);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        return new int[]{min, max};
    }

    /**
     * A random whose first {@code nextInt(bound)} answers 0: populators roll their per-chunk chance first, so this
     * makes the feature fire on this chunk whatever its rate (1/128 obelisks, 1/15 crystals ...), deterministically.
     */
    private static final class FirstRollZero implements RandomSource {
        private final RandomSource delegate;
        private boolean first = true;

        FirstRollZero(long seed) { this.delegate = RandomSource.create(seed); }

        @Override public RandomSource fork() { return delegate.fork(); }
        @Override public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() { return delegate.forkPositional(); }
        @Override public void setSeed(long seed) { delegate.setSeed(seed); }
        @Override public int nextInt() { return delegate.nextInt(); }
        @Override public int nextInt(int bound) {
            if (first) {
                first = false;
                return 0;
            }
            return delegate.nextInt(bound);
        }
        @Override public long nextLong() { return delegate.nextLong(); }
        @Override public boolean nextBoolean() { return delegate.nextBoolean(); }
        @Override public float nextFloat() { return delegate.nextFloat(); }
        @Override public double nextDouble() { return delegate.nextDouble(); }
        @Override public double nextGaussian() { return delegate.nextGaussian(); }
    }

    /** Runs {@code populator} on a generated chunk with the chance roll forced; returns the populator's own result. */
    private static boolean force(ServerLevel level, Populator populator, int chunkX, int chunkZ) {
        return populator.populate(level, new FirstRollZero(42L), chunkX, chunkZ, false);
    }

    private static <T extends Populator> T populator(World w, Class<T> type) {
        return w.controller().populators().stream().filter(type::isInstance).map(type::cast).findFirst().orElse(null);
    }

    private static int structureStarts(ServerLevel level, ResourceKey<Structure> key, int radiusChunks) {
        var cs = level.getChunkSource();
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key).value();
        int starts = 0;
        for (int cx = -radiusChunks; cx <= radiusChunks; cx++) {
            for (int cz = -radiusChunks; cz <= radiusChunks; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS);
                // The GameTest server has generateStructures=false: drive the generator directly (see FacilityTests).
                cs.getGenerator().createStructures(level.registryAccess(), cs.getGeneratorState(), level.structureManager(), chunk,
                        level.getServer().getStructureManager(), level.dimension());
                StructureStart start = chunk.getStartForStructure(structure);
                if (start != null && start.isValid()) starts++;
            }
        }
        return starts;
    }

    // --- D: terrain types ------------------------------------------------------------------------------------------

    @GameTest(timeoutTicks = 20 * 60)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "D1: flat stone terrain with no sea, carved by ravines - a stone plane below the biome surface, no water, ravine cuts somewhere nearby")
    static void d1FlatNoSeaRavines(ExtendedGameTestHelper helper) {
        World w = world(helper, "D1");
        generate(w.level(), 1);
        helper.assertTrue(w.controller().terrainBlock().is(Blocks.STONE), "terrain is stone: " + w.controller().terrainBlock());
        helper.assertTrue(w.controller().seaBlock().isAir(), "no_sea makes the sea air: " + w.controller().seaBlock());
        // TerrainFlatGen: a plane of stone up to the ground level; the biome surface (grass/dirt) sits on it.
        int plane = -1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(8, 0, 8);
        for (int y = w.level().getMaxY(); y >= w.level().getMinY(); y--) {
            at.setY(y);
            if (w.level().getBlockState(at).is(Blocks.STONE)) {
                plane = y;
                break;
            }
        }
        helper.assertTrue(plane >= 0, "stone plane in column (8,8)");
        int onPlane = census(w.level(), 0, 0, plane, plane, s -> s.is(Blocks.STONE) || s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK));
        helper.assertTrue(onPlane >= 160, "flat plane at y=" + plane + ": " + onPlane + "/256 columns solid (caves/ravines carve the rest)");
        int water = census(w.level(), 0, 0, Blocks.WATER) + census(w.level(), 1, 1, Blocks.WATER);
        helper.assertTrue(water == 0, "no_sea leaves no water, found " + water);
        // Ravines (a Features symbol) register a terrain alteration; a 6x6-chunk sweep hits at least one cut.
        boolean ravines = w.controller().alterations().stream().anyMatch(a -> a instanceof RavinesAlteration);
        helper.assertTrue(ravines, "ravines registered as a terrain alteration");
        int carved = 0;
        for (int cx = -3; cx < 3 && carved == 0; cx++) for (int cz = -3; cz < 3 && carved == 0; cz++) {
            w.level().getChunk(cx, cz);
            carved = census(w.level(), cx, cz, Math.max(w.level().getMinY() + 8, plane - 40), plane - 12, BlockState::isAir);
        }
        helper.assertTrue(carved > 0, "a ravine or cave cuts below the plane within 3 chunks of the origin");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 60)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "D6: void terrain generates nothing (chunk (1,1) has no blocks)")
    static void d6VoidIsEmpty(ExtendedGameTestHelper helper) {
        World w = world(helper, "D6");
        w.level().getChunk(1, 1);
        int solid = census(w.level(), 1, 1, w.level().getMinY(), w.level().getMaxY(), s -> !s.isAir());
        helper.assertTrue(solid == 0, "void chunk has " + solid + " blocks");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 90)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "D4: nether terrain is cave-like with netherrack from the nether biomes; the terrain itself is stone with a water sea at 32")
    static void d4NetherCavesAndNetherrack(ExtendedGameTestHelper helper) {
        World w = world(helper, "D4");
        generate(w.level(), 1);
        int netherrack = census(w.level(), 0, 0, Blocks.NETHERRACK);
        int sea = w.controller().seaLevel();
        int airAboveSea = census(w.level(), 0, 0, sea + 1, sea + 40, BlockState::isAir);
        int solidAboveSea = census(w.level(), 0, 0, sea + 1, sea + 40, s -> !s.isAir() && s.getFluidState().isEmpty());
        helper.assertValueEqual(sea, 32, "cave world sea level");
        helper.assertTrue(w.controller().terrainBlock().is(Blocks.STONE) && w.controller().seaBlock().is(Blocks.WATER), "terrain stone / sea water");
        helper.assertTrue(netherrack > 0, "netherrack present (" + netherrack + ")");
        helper.assertTrue(airAboveSea > 0 && solidAboveSea > 0, "cave terrain above the sea: air " + airAboveSea + ", solid " + solidAboveSea);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 90)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "D5: end terrain is an end-stone island at the origin")
    static void d5EndIsland(ExtendedGameTestHelper helper) {
        World w = world(helper, "D5");
        generate(w.level(), 1);
        int endStone = 0;
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) endStone += census(w.level(), cx, cz, Blocks.END_STONE);
        helper.assertTrue(endStone > 0, "end stone around the origin (" + endStone + ")");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 90)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "D3: amplified terrain has large height differences inside 3x3 chunks")
    static void d3AmplifiedRelief(ExtendedGameTestHelper helper) {
        World w = world(helper, "D3");
        generate(w.level(), 1);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                int[] r = heightRange(w.level(), cx, cz);
                min = Math.min(min, r[0]);
                max = Math.max(max, r[1]);
            }
        }
        helper.assertTrue(max - min >= 24, "amplified relief " + min + ".." + max);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 90)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "D2: skylands leave the sea level empty under floating land")
    static void d2SkylandsFloat(ExtendedGameTestHelper helper) {
        World w = world(helper, "D2");
        generate(w.level(), 1);
        int sea = w.controller().seaLevel();
        int airAtSea = 0;
        int solidAbove = 0;
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                airAtSea += census(w.level(), cx, cz, sea - 2, sea + 2, BlockState::isAir);
                solidAbove += census(w.level(), cx, cz, sea + 20, w.level().getMaxY(), s -> !s.isAir());
            }
        }
        helper.assertTrue(airAtSea > 9 * 16 * 16 * 5 / 2, "most of the sea level band is air (" + airAtSea + ")");
        helper.assertTrue(solidAbove > 0, "land above the sea level (" + solidAbove + ")");
        helper.succeed();
    }

    // --- D: feature materials -----------------------------------------------------------------------------------------

    @GameTest(timeoutTicks = 20 * 90)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "D1: obelisks are built from the material page (glowstone) when the populator fires")
    static void d1ObelisksOfGlowstone(ExtendedGameTestHelper helper) {
        World w = world(helper, "D1");
        generate(w.level(), 1);
        ObelisksPopulator obelisks = populator(w, ObelisksPopulator.class);
        helper.assertNotNull(obelisks, "obelisk populator registered");
        helper.assertTrue(obelisks.state().is(Blocks.GLOWSTONE), "obelisk material " + obelisks.state());
        int before = 0;
        for (int cx = 0; cx <= 1; cx++) for (int cz = 0; cz <= 1; cz++) before += census(w.level(), cx, cz, Blocks.GLOWSTONE);
        force(w.level(), obelisks, 0, 0);
        int after = 0;
        for (int cx = 0; cx <= 1; cx++) for (int cz = 0; cz <= 1; cz++) after += census(w.level(), cx, cz, Blocks.GLOWSTONE);
        helper.assertTrue(after > before, "glowstone placed (" + before + " -> " + after + ")");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 90)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "D5: spikes are built from the material page (obsidian)")
    static void d5SpikesOfObsidian(ExtendedGameTestHelper helper) {
        World w = world(helper, "D5");
        generate(w.level(), 1);
        SpikesPopulator spikes = populator(w, SpikesPopulator.class);
        helper.assertNotNull(spikes, "spike populator registered");
        helper.assertTrue(spikes.state().is(Blocks.OBSIDIAN), "spike material " + spikes.state());
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 90)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "D3: deep lakes are filled with the material page (lava) and the populator places one")
    static void d3DeepLakesOfLava(ExtendedGameTestHelper helper) {
        World w = world(helper, "D3");
        generate(w.level(), 1);
        LakesPopulator lakes = populator(w, LakesPopulator.class);
        helper.assertNotNull(lakes, "lake populator registered");
        helper.assertTrue(lakes.isDeep() && lakes.fluid().is(Blocks.LAVA), "deep lava lakes: deep=" + lakes.isDeep() + " fluid=" + lakes.fluid());
        int before = 0;
        for (int cx = 0; cx <= 1; cx++) for (int cz = 0; cz <= 1; cz++) before += census(w.level(), cx, cz, Blocks.LAVA);
        for (int seed = 0; seed < 64; seed++) lakes.populate(w.level(), new FirstRollZero(seed), 0, 0, false);
        int after = 0;
        for (int cx = 0; cx <= 1; cx++) for (int cz = 0; cz <= 1; cz++) after += census(w.level(), cx, cz, Blocks.LAVA);
        helper.assertTrue(after > before, "a lava lake was placed (" + before + " -> " + after + ")");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 90)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Crystal formations place crystal blocks when the populator fires (normal terrain; skylands have no ground at y=0 for the start search)")
    static void crystalFormations(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        QaShelf.Case plains = QaShelf.Case.of("T2", "Crystals", 2102L, "", "terrain_normal", "crystal_formations");
        AgeData data = DescriptiveBookItem.getAgeData(server, QaShelf.bind(server, plains));
        ServerLevel level = AgeManager.getOrCreateLevel(server, data);
        World w = new World(level, AgeControllers.server(level), data);
        generate(w.level(), 1);
        CrystalFormationPopulator crystals = populator(w, CrystalFormationPopulator.class);
        helper.assertNotNull(crystals, "crystal populator registered");
        int before = 0;
        int after = 0;
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) before += census(w.level(), cx, cz, ModBlocks.CRYSTAL.get());
        for (int seed = 0; seed < 32; seed++) crystals.populate(w.level(), new FirstRollZero(seed), 0, 0, false);
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) after += census(w.level(), cx, cz, ModBlocks.CRYSTAL.get());
        helper.assertTrue(after > before, "crystal blocks placed (" + before + " -> " + after + ")");
        helper.succeed();
    }

    // --- E: biome layouts and structures ----------------------------------------------------------------------------

    private static int distinctBiomes(AgeController controller, int radius, int step, Set<Holder<Biome>> out) {
        for (int x = -radius; x <= radius; x += step) {
            for (int z = -radius; z <= radius; z += step) out.add(controller.biomeAt(x, z));
        }
        return out.size();
    }

    /** Biome changes along the x axis through the origin: a proxy for patch size (tiny = many, large = few). */
    private static int transitions(AgeController controller, int radius, int step) {
        int changes = 0;
        Holder<Biome> last = null;
        for (int x = -radius; x <= radius; x += step) {
            Holder<Biome> b = controller.biomeAt(x, 0);
            if (last != null && !b.equals(last)) changes++;
            last = b;
        }
        return changes;
    }

    @GameTest(timeoutTicks = 20 * 30)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "E1: tiny biome layout - all three written biomes within 256 blocks and frequent transitions")
    static void e1TinyBiomesPatchwork(ExtendedGameTestHelper helper) {
        World w = world(helper, "E1");
        Set<Holder<Biome>> seen = new HashSet<>();
        int distinct = distinctBiomes(w.controller(), 256, 8, seen);
        helper.assertTrue(distinct >= 3, "tiny layout shows the written biomes: " + distinct);
        int changes = transitions(w.controller(), 512, 4);
        helper.assertTrue(changes >= 8, "tiny biomes change often along 1024 blocks: " + changes);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 30)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "D5: single biome layout - one biome everywhere")
    static void d5SingleBiome(ExtendedGameTestHelper helper) {
        World w = world(helper, "D5");
        Set<Holder<Biome>> seen = new HashSet<>();
        int distinct = distinctBiomes(w.controller(), 512, 32, seen);
        helper.assertTrue(distinct == 1, "single layout has one biome, found " + distinct);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 30)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "E2: large biome layout - far fewer transitions than the tiny layout")
    static void e2LargeBiomesAreLarge(ExtendedGameTestHelper helper) {
        World large = world(helper, "E2");
        World tiny = world(helper, "E1");
        int largeChanges = transitions(large.controller(), 512, 4);
        int tinyChanges = transitions(tiny.controller(), 512, 4);
        helper.assertTrue(largeChanges * 3 <= tinyChanges, "large " + largeChanges + " vs tiny " + tinyChanges + " transitions");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 120)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Villages: the symbol enables village starts (plains-only Age so the biome check always passes)")
    static void villagesGenerate(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        QaShelf.Case plains = QaShelf.Case.of("T1", "Plains villages", 2101L, "", "biome_single", "biome_minecraft_plains", "villages");
        AgeData data = DescriptiveBookItem.getAgeData(server, QaShelf.bind(server, plains));
        ServerLevel level = AgeManager.getOrCreateLevel(server, data);
        int starts = structureStarts(level, BuiltinStructures.VILLAGE_PLAINS, 20);
        helper.assertTrue(starts > 0, "plains village starts within 20 chunks: " + starts);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 60)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "E2: the Vault symbol places the Facility near the origin")
    static void e2FacilityGenerates(ExtendedGameTestHelper helper) {
        World w = world(helper, "E2");
        helper.assertTrue(structureStarts(w.level(), ModStructures.FACILITY, 6) == 1, "one facility start");
        helper.succeed();
    }

    // --- C: weather ---------------------------------------------------------------------------------------------

    @GameTest(timeoutTicks = 20 * 30)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "C2/C3/C4: rain and snow Ages rain, the storm Age thunders, cloudy B4 does not rain")
    static void weatherStates(ExtendedGameTestHelper helper) {
        for (String id : List.of("C2", "C3", "C4")) {
            World w = world(helper, id);
            helper.assertNotNull(w.controller().weather(), id + " weather controller");
            w.controller().weather().updateRaining(w.level());
            helper.assertTrue(w.controller().weather().isRaining(), id + " is raining");
            if (id.equals("C4")) helper.assertTrue(w.controller().weather().isThundering(), "C4 thunders");
        }
        // Cloudy = overcast (raining state) with precipitation visuals disabled for every biome.
        World cloudy = world(helper, "B4");
        cloudy.controller().weather().updateRaining(cloudy.level());
        Holder<Biome> biome = cloudy.controller().biomeAt(0, 0);
        helper.assertTrue(cloudy.controller().weather().isRaining(), "B4 (cloudy) is overcast");
        helper.assertTrue(!cloudy.controller().weather().getRainEnabled(biome, true) && !cloudy.controller().weather().getSnowEnabled(biome, true),
                "B4 (cloudy) shows no rain or snow");
        helper.succeed();
    }

    // --- G: instability ----------------------------------------------------------------------------------------

    @GameTest(timeoutTicks = 20 * 30)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "G1: the unstable Age registers its environmental effects and has an instability controller")
    static void g1UnstableAgeHasEffects(ExtendedGameTestHelper helper) {
        World w = world(helper, "G1");
        helper.assertTrue(w.controller().effects().size() >= 3, "meteors, accelerated and explosions registered as effects: " + w.controller().effects().size());
        InstabilityController inst = InstabilityController.get(w.level());
        helper.assertNotNull(inst, "instability controller");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 30)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "A1: the baseline Age is stable - no symbol instability")
    static void a1BaselineIsStable(ExtendedGameTestHelper helper) {
        World w = world(helper, "A1");
        helper.assertTrue(w.controller().symbolInstability() == 0, "baseline symbol instability " + w.controller().symbolInstability());
        helper.succeed();
    }
}
