package com.tbd.mystcraft.gametest;

import com.tbd.mystcraft.command.MystcraftCommands;
import com.tbd.mystcraft.villager.ArchivistEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

/** The Archivist villager: a spawned one keeps his profession (and so his shop) without a job site. */
@ForEachTest(groups = "archivist")
public class ArchivistTests {

    @GameTest(timeoutTicks = 400)
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "A command-spawned Archivist stays an Archivist; a villager given the profession with no experience and no job site is fired by vanilla within seconds (the playtest 'refuses to interact')")
    static void spawnedArchivistKeepsProfession(ExtendedGameTestHelper helper) {
        Villager ours = MystcraftCommands.spawnArchivist(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), Direction.SOUTH);
        Villager control = EntityType.VILLAGER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        helper.assertNotNull(control, "control villager");
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        control.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        control.setVillagerData(control.getVillagerData().withProfession(helper.getLevel().registryAccess(), com.tbd.mystcraft.registry.ModVillagers.ARCHIVIST_KEY));
        helper.getLevel().addFreshEntity(control);
        helper.assertTrue(ArchivistEvents.isArchivist(ours) && ArchivistEvents.isArchivist(control), "both start as Archivists");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(control.getVillagerData().profession().is(VillagerProfession.NONE), "control villager fired"))
                .thenExecuteAfter(100, () -> {
                    helper.assertTrue(ArchivistEvents.isArchivist(ours), "spawned Archivist still employed (got " + ours.getVillagerData().profession() + ")");
                    helper.assertValueEqual(ours.getVillagerXp(), 1, "one point of experience keeps him");
                })
                .thenSucceed();
    }
}
