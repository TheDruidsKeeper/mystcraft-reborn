package com.techbucketdivision.mystcraft.gametest;

import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.linking.LinkController;
import com.techbucketdivision.mystcraft.registry.ModStructures;
import com.techbucketdivision.mystcraft.util.MystIds;
import com.techbucketdivision.mystcraft.world.AgeSpawn;
import com.techbucketdivision.mystcraft.world.feature.VanillaStructurePopulator;
import com.techbucketdivision.mystcraft.world.structure.FacilityLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

/** The Facility structure (docs/impl/FACILITY_PLAN.md): Vault symbol, near_origin placement, spawn relation. */
@ForEachTest(groups = "facility")
public class FacilityTests {

    @GameTest(timeoutTicks = 20 * 120)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "An Age written with the Vault symbol has exactly one Facility start in the near_origin chunk and spawns 60-120 blocks from it")
    static void vaultSymbolPlacesOneFacilityNearOrigin(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("Facility", 4242L, MystIds.id("vault"));
        AgeData data = TestBooks.bind(book, server);
        helper.assertTrue(data.symbols().contains(MystIds.id("vault")), "written symbol kept after the fill");
        ServerLevel age = AgeManager.getOrCreateLevel(server, data);
        AgeController controller = AgeControllers.server(age);
        helper.assertNotNull(controller, "Age controller");
        helper.assertTrue(controller.populators().stream()
                .anyMatch(p -> p instanceof VanillaStructurePopulator v && v.structureSet() == ModStructures.FACILITY_SET),
                "vault populator enables the facility structure set");

        ChunkPos expected = FacilityLocator.facilityChunk(age);
        helper.assertNotNull(expected, "FacilityLocator knows the facility chunk");
        helper.assertTrue(expected.equals(FacilityLocator.facilityChunk(age)), "placement is deterministic");
        helper.assertTrue(!(expected.x() == 0 && expected.z() == 0), "facility never sits on the origin chunk");
        helper.assertTrue(Math.abs(expected.x()) <= 5 && Math.abs(expected.z()) <= 5, "facility within 5 chunks of the origin: " + expected);

        // The GameTest server runs with WorldOptions.generateStructures=false, so ChunkStatusTasks never calls
        // createStructures: drive it directly for the chunks around the origin. Real generation on a dedicated server
        // is covered by SelfCheck (smoke test).
        Structure structure = age.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.FACILITY).value();
        var cs = age.getChunkSource();
        int starts = 0;
        for (int cx = -6; cx <= 6; cx++) {
            for (int cz = -6; cz <= 6; cz++) {
                ChunkAccess chunk = age.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS);
                cs.getGenerator().createStructures(age.registryAccess(), cs.getGeneratorState(), age.structureManager(), chunk,
                        server.getStructureManager(), age.dimension());
                StructureStart start = chunk.getStartForStructure(structure);
                if (start != null && start.isValid()) {
                    starts++;
                    helper.assertTrue(cx == expected.x() && cz == expected.z(), "facility start at " + cx + "," + cz + " but placement says " + expected);
                    helper.assertTrue(start.getPieces().size() >= 4, "facility has entrance + shaft + lobby + rooms, got " + start.getPieces().size() + " pieces");
                }
            }
        }
        helper.assertTrue(starts == 1, "exactly one facility start within 6 chunks of the origin, found " + starts);

        BlockPos spawn = LinkController.defaultSpawn(age);
        BlockPos entrance = expected.getMiddleBlockPosition(spawn.getY());
        double distance = Math.sqrt(entrance.distSqr(spawn));
        helper.assertTrue(distance >= AgeSpawn.FACILITY_MIN - 16 && distance <= AgeSpawn.FACILITY_MAX + 16,
                "spawn " + spawn.toShortString() + " is " + (int) distance + " blocks from the facility entrance (expected "
                        + AgeSpawn.FACILITY_MIN + "-" + AgeSpawn.FACILITY_MAX + ")");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20 * 60)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "An Age without the Vault symbol has no Facility placement")
    static void noVaultSymbolNoFacility(ExtendedGameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack book = TestBooks.unboundDescriptiveBook("No facility", 4343L, MystIds.id("villages"));
        AgeData data = TestBooks.bind(book, server);
        ServerLevel age = AgeManager.getOrCreateLevel(server, data);
        helper.assertTrue(!data.symbols().contains(MystIds.id("vault")) || FacilityLocator.facilityChunk(age) != null,
                "a random fill may add the vault symbol; if it did the locator must know about it");
        if (!data.symbols().contains(MystIds.id("vault"))) {
            helper.assertTrue(FacilityLocator.facilityChunk(age) == null, "no facility placement without the symbol");
        }
        helper.succeed();
    }
}
