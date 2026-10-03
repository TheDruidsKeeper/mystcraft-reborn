package com.techbucketdivision.mystcraft.gametest;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.testframework.conf.Feature;
import net.neoforged.testframework.conf.FrameworkConfiguration;
import net.neoforged.testframework.conf.MissingDescriptionAction;
import net.neoforged.testframework.impl.MutableTestFramework;
import net.neoforged.testframework.summary.JUnitSummaryDumper;

import java.nio.file.Path;

/**
 * Dev-only companion mod ({@code mystcraft_tests}) hosting the in-game tests. Loaded by the {@code gameTestServer}
 * run only (see build.gradle); never part of the shipped jar. Tests live in {@link LinkingTests},
 * {@link WorldTests} and {@link InstabilityTests}; {@code /tests} is available in the dev client for manual runs.
 */
@Mod("mystcraft_tests")
public class MystcraftTestsMod {
    public MystcraftTestsMod(IEventBus modBus, ModContainer container) {
        MutableTestFramework framework = FrameworkConfiguration.builder(Identifier.fromNamespaceAndPath("mystcraft_tests", "tests"))
                .enable(Feature.TEST_STORE)
                .dumpers(new JUnitSummaryDumper(Path.of("gametest-results/")))
                .onMissingDescription(MissingDescriptionAction.ERROR)
                .build().create();
        framework.init(modBus, container);

        NeoForge.EVENT_BUS.addListener((final RegisterCommandsEvent event) -> {
            LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("tests");
            framework.registerCommands(node);
            event.getDispatcher().register(node);
        });
    }
}
