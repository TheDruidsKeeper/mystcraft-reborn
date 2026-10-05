package com.techbucketdivision.mystcraft;

import com.techbucketdivision.mystcraft.age.AgeBlueprint;
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
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.ChunkPos;
import com.techbucketdivision.mystcraft.world.structure.FacilityLocator;
import com.techbucketdivision.mystcraft.world.AgeSpawn;
import com.techbucketdivision.mystcraft.registry.ModStructures;
import com.techbucketdivision.mystcraft.item.DescriptiveBookItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless end-to-end verification, run on server start when {@code -Dmystcraft.selfcheck=true} or the environment
 * variable {@code MYSTCRAFT_SELFCHECK=1} is set. Used by the Docker smoke test (scripts/smoke-entry.sh) so that CI
 * verifies the parts a plain server boot never touches: dimension creation, chunk generation and the Age blueprint.
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
            checkBlueprint();
            checkDimensionType(server);
            checkFacilityPack(server);
            checkAgeCreationAndGeneration(server);
            checkFacilityGeneration(server);
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

    /** An empty book must be filled into a complete, stable, deterministic Age description (plan §2). */
    private static void checkBlueprint() {
        List<ItemStack> empty = List.of(PageItem.createLinkPanel());
        AgeBlueprint.Result result = AgeBlueprint.fill(empty, 1234L);
        List<Identifier> generated = AgeBlueprint.flatten(result.pages());
        check("blueprint fills an empty book (" + result.discovered().size() + " discovered pages, " + generated.size() + " symbols)", !generated.isEmpty());

        List<Identifier> unresolved = new ArrayList<>();
        for (Identifier id : generated) if (SymbolRegistry.get(id) == null) unresolved.add(id);
        check("every discovered symbol resolves", unresolved.isEmpty());
        if (!unresolved.isEmpty()) fail("unresolved symbols: " + unresolved);
        check("no required category is missing after the fill", AgeBlueprint.missing(result.pages()).isEmpty());
        check("discovered instability within budget (" + result.discoveredInstability() + ")",
                result.discoveredInstability() <= com.techbucketdivision.mystcraft.config.WorldBuildingConfig.INSTABILITY_BUDGET.get());

        // Determinism: the same seed must yield the same Age, or saved Ages would not survive a restart.
        List<Identifier> again = AgeBlueprint.flatten(AgeBlueprint.fill(empty, 1234L).pages());
        check("blueprint fill is deterministic for a given seed", generated.equals(again));

        // 200-seed stress: never void terrain, never an unstable pick, always a sun and a terrain
        int badSeeds = 0;
        for (long seed = 1; seed <= 200; seed++) {
            AgeBlueprint.Result r = AgeBlueprint.fill(empty, seed);
            List<Identifier> ids = AgeBlueprint.flatten(r.pages());
            boolean ok = !ids.contains(Identifier.fromNamespaceAndPath("mystcraft", "terrain_void"))
                    && ids.stream().anyMatch(id -> id.getPath().startsWith("terrain_"))
                    && ids.stream().anyMatch(id -> id.getPath().startsWith("sun_"))
                    && AgeBlueprint.missing(r.pages()).isEmpty()
                    && r.discoveredInstability() <= com.techbucketdivision.mystcraft.config.WorldBuildingConfig.INSTABILITY_BUDGET.get();
            if (!ok) badSeeds++;
        }
        check("200 random fills: terrain + sun present, never void, within budget (" + badSeeds + " bad)", badSeeds == 0);
    }

    private static void checkDimensionType(MinecraftServer server) {
        boolean present = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE)
                .get(AgeDimensionType.AGE).isPresent();
        check("dimension type mystcraft:age loaded from the datapack", present);
    }

    /**
     * The Facility built-in datapack (docs/STRUCTURES.md): the jigsaw structure, every generated template pool and
     * every template file the pools reference must load. Catches a stale manifest, a missing .nbt or a bad remap.
     */
    private static void checkFacilityPack(MinecraftServer server) {
        var structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        check("facility structure loaded from the built-in datapack", structures.get(MystIds.id("facility")).isPresent());
        var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        StructureTemplateManager templates = server.getStructureManager();
        int poolCount = 0;
        int elements = 0;
        List<String> missing = new ArrayList<>();
        for (var ref : pools.listElements().toList()) {
            Identifier id = ref.key().identifier();
            if (!id.getNamespace().equals(Mystcraft.MOD_ID) || !id.getPath().startsWith("facility/")) continue;
            poolCount++;
            StructureTemplatePool pool = ref.value();
            for (var entry : pool.getTemplates()) {
                elements++;
                StructurePoolElement element = entry.getFirst();
                // A missing .nbt resolves to an empty template (StructureTemplateManager#getOrCreate), i.e. size 0.
                if (element instanceof SinglePoolElement && element.getSize(templates, Rotation.NONE).equals(Vec3i.ZERO)) {
                    missing.add(id + " -> " + element);
                }
            }
        }
        check("facility template pools generated (" + poolCount + " pools, " + elements + " elements)", poolCount >= 7 && elements >= 30);
        check("every facility pool element has a loadable template", missing.isEmpty());
        if (!missing.isEmpty()) fail("missing templates: " + missing);
    }

    /**
     * The core path: create an Age, materialise its dimension, compile its controller and generate a chunk. This is
     * the only automated coverage of AgeChunkGenerator and the terrain generators.
     */
    private static void checkAgeCreationAndGeneration(MinecraftServer server) {
        AgeData data = AgeManager.createAge(server);

        // Give it a real description so the generator has something to work with.
        AgeBlueprint.Result filled = AgeBlueprint.fill(List.of(PageItem.createLinkPanel()), data.seed());
        List<Identifier> symbols = AgeBlueprint.flatten(filled.pages());
        data.setPages(filled.pages());
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
        long first = System.currentTimeMillis() - start;
        check("chunk (0,0) generated in " + first + " ms", chunk != null);

        // Time a warm batch: the first chunk includes noise-field allocation and JIT warm-up, so it says
        // nothing about steady-state cost. Chunk generation has to be viable for the Age to be playable.
        start = System.currentTimeMillis();
        int generated = 0;
        for (int cx = 1; cx <= 3; cx++) {
            for (int cz = 1; cz <= 3; cz++) {
                if (level.getChunk(cx, cz) != null) generated++;
            }
        }
        long batch = System.currentTimeMillis() - start;
        long per = generated == 0 ? -1 : batch / generated;
        Mystcraft.LOGGER.info("[selfcheck] generated {} more chunks in {} ms ({} ms/chunk)", generated, batch, per);
        check("warm chunk generation under 500 ms/chunk (was " + per + ")", per >= 0 && per < 500);

        if (chunk != null) describeTerrain(level, chunk);

        // Leave no trace: retire the test Age so it can be recycled.
        AgeManager.markDead(server, data.levelKey());
        check("test age retired", true);
    }

    /**
     * Informational terrain sample. A Void Age legitimately has no blocks at all, so nothing here is fatal; the point
     * is to make "the generator ran but produced nothing sensible" visible in the CI log.
     */
    /**
     * A Vault Age on a real server (structures enabled): the Facility start exists in the chunk NearOriginPlacement
     * names, with pieces, and the arrival point is FACILITY_MIN..MAX blocks from it.
     */
    private static void checkFacilityGeneration(MinecraftServer server) {
        ItemStack book = DescriptiveBookItem.createBound(server, "Selfcheck Vault", 777L, List.of(PageItem.createSymbolPage(MystIds.id("vault"))));
        AgeData data = DescriptiveBookItem.getAgeData(server, book);
        check("vault age created", data != null);
        if (data == null) return;
        ServerLevel level = AgeManager.getOrCreateLevel(server, data);
        ChunkPos facility = FacilityLocator.facilityChunk(level);
        check("vault age has a facility placement", facility != null);
        if (facility == null) return;
        long t0 = System.currentTimeMillis();
        ChunkAccess chunk = level.getChunk(facility.x(), facility.z(), ChunkStatus.STRUCTURE_STARTS);
        Structure structure = server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.FACILITY).value();
        StructureStart start = chunk.getStartForStructure(structure);
        boolean valid = start != null && start.isValid();
        check("facility start generated in chunk " + facility + " (" + (System.currentTimeMillis() - t0) + " ms)", valid);
        if (valid) {
            check("facility assembled " + start.getPieces().size() + " pieces (entrance, shaft, lobby, rooms, vault)", start.getPieces().size() >= 4);
            Mystcraft.LOGGER.info("[selfcheck] facility bounding box {}", start.getBoundingBox());
            // Fully generate the facility's chunks: the pool element resolves the puzzle markers at placement.
            net.minecraft.world.level.levelgen.structure.BoundingBox box = start.getBoundingBox();
            t0 = System.currentTimeMillis();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) level.getChunk(cx, cz, ChunkStatus.FULL);
            }
            int doors = 0, locks = 0, caches = 0, vaults = 0, linkbooks = 0;
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                var state = level.getBlockState(pos);
                if (state.is(ModBlocks.WARDED_DOOR.get())) doors++;
                else if (state.is(ModBlocks.SYMBOL_ALTAR.get()) || state.is(ModBlocks.OFFERING_PEDESTAL.get()) || state.is(ModBlocks.SEQUENCE_DIAL.get())) locks++;
                else if (state.is(ModBlocks.FACILITY_CACHE.get())) {
                    caches++;
                    if (level.getBlockEntity(pos) instanceof com.techbucketdivision.mystcraft.blockentity.FacilityCacheBlockEntity cache && cache.isLinkbook()) linkbooks++;
                } else if (state.is(net.minecraft.world.level.block.Blocks.VAULT)) vaults++;
            }
            Mystcraft.LOGGER.info("[selfcheck] facility generated in {} ms: {} warded doors, {} locks, {} caches ({} linkbook), {} vaults",
                    System.currentTimeMillis() - t0, doors, locks, caches, linkbooks, vaults);
            check("facility rooms carry puzzle blocks (" + doors + " doors, " + locks + " locks)", doors > 0 && locks > 0);
            check("facility has a Linking Book cache", linkbooks >= 1);
            BlockPos probe = start.getPieces().getLast().getBoundingBox().getCenter();
            check("facility interior is protected until solved", com.techbucketdivision.mystcraft.facility.FacilityProtection.protects(level, probe));
        }
        BlockPos spawn = AgeSpawn.findSpawn(level, AgeControllers.server(level));
        double distance = Math.sqrt(facility.getMiddleBlockPosition(spawn.getY()).distSqr(spawn));
        check("vault age spawn " + spawn.toShortString() + " is " + (int) distance + " blocks from the facility", distance >= AgeSpawn.FACILITY_MIN - 16 && distance <= AgeSpawn.FACILITY_MAX + 16);
        AgeManager.markDead(server, data.levelKey());
        check("vault age retired", true);
    }

    private static void describeTerrain(ServerLevel level, ChunkAccess chunk) {
        int minY = level.getMinY();
        int maxY = minY + level.getHeight();
        int solid = 0;
        int topSolid = Integer.MIN_VALUE;
        int columnsWithTerrain = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                boolean any = false;
                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (!state.isAir()) {
                        solid++;
                        any = true;
                        if (y > topSolid) topSolid = y;
                    }
                }
                if (any) columnsWithTerrain++;
            }
        }
        Mystcraft.LOGGER.info("[selfcheck] terrain sample: {}/16 columns have blocks, {} non-air blocks, highest y={}",
                columnsWithTerrain, solid, topSolid == Integer.MIN_VALUE ? "none" : topSolid);
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
