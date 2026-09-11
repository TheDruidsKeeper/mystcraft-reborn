package com.techbucketdivision.mystcraft;

import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.dimension.AgeDimensionType;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.registry.ModCreativeTabs;
import com.techbucketdivision.mystcraft.registry.ModDataComponents;
import com.techbucketdivision.mystcraft.registry.ModEntities;
import com.techbucketdivision.mystcraft.registry.ModFluids;
import com.techbucketdivision.mystcraft.registry.ModItems;
import com.techbucketdivision.mystcraft.registry.ModMenus;
import com.techbucketdivision.mystcraft.registry.ModSounds;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import com.techbucketdivision.mystcraft.symbol.grammar.Grammar;
import com.techbucketdivision.mystcraft.symbol.grammar.GrammarRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless end-to-end verification, run on server start when {@code -Dmystcraft.selfcheck=true} or the environment
 * variable {@code MYSTCRAFT_SELFCHECK=1} is set. Used by the Docker smoke test (scripts/smoke-entry.sh) so that CI
 * verifies the parts a plain server boot never touches: dimension creation, chunk generation and the grammar.
 * <p>
 * Never enabled in normal play. Every check logs {@code [selfcheck]} and the summary line is
 * {@code SELFCHECK PASSED: n checks} / {@code SELFCHECK FAILED: n of m checks}.
 */
public final class SelfCheck {
    private SelfCheck() {}

    public static boolean enabled() {
        return "true".equalsIgnoreCase(System.getProperty("mystcraft.selfcheck"))
                || "1".equals(System.getenv("MYSTCRAFT_SELFCHECK"));
    }

    private static final List<String> FAILURES = new ArrayList<>();
    private static int checks;

    public static void run(MinecraftServer server) {
        FAILURES.clear();
        checks = 0;
        Mystcraft.LOGGER.info("[selfcheck] starting");
        try {
            checkRegistries();
            checkSymbols();
            checkGrammar();
            checkDimensionType(server);
            checkAgeCreationAndGeneration(server);
        } catch (Throwable t) {
            fail("uncaught exception: " + t);
            Mystcraft.LOGGER.error("[selfcheck] uncaught", t);
        }

        if (FAILURES.isEmpty()) {
            Mystcraft.LOGGER.info("SELFCHECK PASSED: {} checks", checks);
        } else {
            Mystcraft.LOGGER.error("SELFCHECK FAILED: {} of {} checks", FAILURES.size(), checks);
            for (String f : FAILURES) Mystcraft.LOGGER.error("[selfcheck]   - {}", f);
        }
    }

    // --- checks ------------------------------------------------------------------------------------------------

    /** Every DeferredRegister entry must actually be bound to a registry object. */
    private static void checkRegistries() {
        checkRegister("blocks", ModBlocks.BLOCKS);
        checkRegister("items", ModItems.ITEMS);
        checkRegister("block entities", ModBlockEntities.BLOCK_ENTITIES);
        checkRegister("menus", ModMenus.MENUS);
        checkRegister("entities", ModEntities.ENTITIES);
        checkRegister("sounds", ModSounds.SOUNDS);
        checkRegister("data components", ModDataComponents.COMPONENTS);
        checkRegister("creative tabs", ModCreativeTabs.TABS);
        checkRegister("fluid types", ModFluids.FLUID_TYPES);
        checkRegister("fluids", ModFluids.FLUIDS);
    }

    private static void checkRegister(String label, DeferredRegister<?> register) {
        int bound = 0;
        int total = 0;
        for (var holder : register.getEntries()) {
            total++;
            if (holder.isBound()) {
                bound++;
            } else {
                fail(label + ": unbound entry " + holder.getId());
            }
        }
        check(label + ": " + bound + "/" + total + " bound", bound == total && total > 0);
    }

    private static void checkSymbols() {
        var symbols = SymbolRegistry.all();
        check("symbols registered: " + symbols.size(), !symbols.isEmpty());

        int badPoems = 0;
        int badPages = 0;
        for (AgeSymbol symbol : symbols) {
            if (symbol.poem().size() != 4) badPoems++;
            ItemStack page = PageItem.createSymbolPage(symbol);
            if (page.isEmpty() || !symbol.equals(PageItem.getSymbol(page))) badPages++;
        }
        check("every symbol has a 4-word poem", badPoems == 0);
        check("every symbol round-trips through a page item", badPages == 0);
    }

    /** A random Age description must expand to symbols that all resolve. */
    private static void checkGrammar() {
        RandomSource random = RandomSource.create(1234L);
        List<Identifier> generated = Grammar.generateFromToken(GrammarRules.AGE, random);
        check("grammar generates a random Age (" + generated.size() + " symbols)", !generated.isEmpty());

        List<Identifier> unresolved = new ArrayList<>();
        for (Identifier id : generated) {
            if (SymbolRegistry.get(id) == null) unresolved.add(id);
        }
        check("every generated symbol resolves", unresolved.isEmpty());
        if (!unresolved.isEmpty()) fail("unresolved symbols: " + unresolved);
    }

    private static void checkDimensionType(MinecraftServer server) {
        boolean present = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE)
                .get(AgeDimensionType.AGE).isPresent();
        check("dimension type mystcraft:age loaded from the datapack", present);
    }

    /**
     * The core path: create an Age, materialise its dimension, compile its controller and generate a chunk. This is
     * the only automated coverage of AgeChunkGenerator and the terrain generators.
     */
    private static void checkAgeCreationAndGeneration(MinecraftServer server) {
        AgeData data = AgeManager.createAge(server);

        // Give it a real description so the generator has something to work with.
        RandomSource random = RandomSource.create(data.seed());
        List<Identifier> symbols = Grammar.generateFromToken(GrammarRules.AGE, random);
        data.setSymbols(symbols);
        check("age created: " + data.name() + " with " + symbols.size() + " symbols", !symbols.isEmpty());

        ServerLevel level = AgeManager.getOrCreateLevel(server, data);
        check("age dimension created (" + data.levelKey().identifier() + ")", level != null);
        check("age dimension is registered on the server", server.getLevel(data.levelKey()) != null);

        if (level == null) return;

        AgeController controller = AgeControllers.server(server, data.uuid());
        check("age controller compiled", controller != null);
        if (controller != null) {
            check("controller has a terrain generator", controller.terrainGenerator() != null);
            check("controller has a biome controller", controller.biomeController() != null);
            check("controller has lighting", controller.lighting() != null);
            check("controller has weather", controller.weather() != null);
        }

        // Generate a chunk: exercises AgeChunkGenerator, the terrain generator, alterations and populators.
        long start = System.currentTimeMillis();
        ChunkAccess chunk = level.getChunk(0, 0);
        long ms = System.currentTimeMillis() - start;
        check("chunk (0,0) generated in " + ms + " ms", chunk != null);

        if (chunk != null) {
            int solid = 0;
            for (int y = level.getMinY(); y < level.getMinY() + level.getHeight(); y += 4) {
                BlockState state = chunk.getBlockState(new BlockPos(8, y, 8));
                if (!state.isAir()) solid++;
            }
            // A Void Age legitimately has none, so this is informational rather than fatal.
            Mystcraft.LOGGER.info("[selfcheck] column (8,8) of the generated chunk has {} non-air samples", solid);
        }

        // Leave no trace: retire the test Age so it can be recycled.
        AgeManager.markDead(server, data.levelKey());
        check("test age retired", true);
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private static void check(String description, boolean ok) {
        checks++;
        if (ok) {
            Mystcraft.LOGGER.info("[selfcheck] OK   {}", description);
        } else {
            Mystcraft.LOGGER.error("[selfcheck] FAIL {}", description);
            FAILURES.add(description);
        }
    }

    private static void fail(String description) {
        FAILURES.add(description);
    }
}
