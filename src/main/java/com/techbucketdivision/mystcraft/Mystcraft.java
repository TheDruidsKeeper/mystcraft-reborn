package com.techbucketdivision.mystcraft;

import com.mojang.logging.LogUtils;
import com.techbucketdivision.mystcraft.config.BalanceConfig;
import com.techbucketdivision.mystcraft.config.WorldBuildingConfig;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import com.techbucketdivision.mystcraft.instability.InstabilityManager;
import com.techbucketdivision.mystcraft.linking.LinkListeners;
import com.techbucketdivision.mystcraft.network.Payloads;
import com.techbucketdivision.mystcraft.registry.ModAttachments;
import com.techbucketdivision.mystcraft.registry.ModBiomeSources;
import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.registry.ModChunkGenerators;
import com.techbucketdivision.mystcraft.registry.ModCreativeTabs;
import com.techbucketdivision.mystcraft.registry.ModCriteria;
import com.techbucketdivision.mystcraft.registry.ModDataComponents;
import com.techbucketdivision.mystcraft.registry.ModEntities;
import com.techbucketdivision.mystcraft.registry.ModFluids;
import com.techbucketdivision.mystcraft.registry.ModItems;
import com.techbucketdivision.mystcraft.registry.ModMenus;
import com.techbucketdivision.mystcraft.registry.ModParticles;
import com.techbucketdivision.mystcraft.registry.ModRecipes;
import com.techbucketdivision.mystcraft.registry.ModSounds;
import com.techbucketdivision.mystcraft.registry.ModStructures;
import com.techbucketdivision.mystcraft.registry.ModVillagers;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;

/**
 * Mod entry point. Only wiring lives here; behaviour is in the packages listed in docs/ARCHITECTURE.md.
 */
@Mod(Mystcraft.MOD_ID)
public final class Mystcraft {
    public static final String MOD_ID = "mystcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mystcraft(IEventBus modBus, ModContainer container) {
        // Registries (order matters only for blocks before items).
        ModBlocks.BLOCKS.register(modBus);
        ModFluids.FLUID_TYPES.register(modBus);
        ModFluids.FLUIDS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModDataComponents.COMPONENTS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModChunkGenerators.CHUNK_GENERATORS.register(modBus);
        ModBiomeSources.BIOME_SOURCES.register(modBus);
        ModCriteria.TRIGGERS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModVillagers.POI_TYPES.register(modBus);
        ModVillagers.PROFESSIONS.register(modBus);
        ModStructures.PLACEMENT_TYPES.register(modBus);
        ModStructures.POOL_ELEMENT_TYPES.register(modBus);

        // Config
        container.registerConfig(ModConfig.Type.COMMON, MystcraftConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, BalanceConfig.SPEC, MOD_ID + "-balance.toml");
        container.registerConfig(ModConfig.Type.COMMON, WorldBuildingConfig.SPEC, MOD_ID + "-worldbuilding.toml");

        modBus.addListener(Payloads::register);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addPackFinders);
    }

    /**
     * The Facility rooms ship as a built-in data pack (src/main/resources/datapacks/mystcraft_facility; pools generated
     * by the {@code generateStructurePools} Gradle task). Enabled by default, but a separate pack so players can
     * disable it or layer their own room packs on top (docs/STRUCTURES.md).
     */
    private void addPackFinders(AddPackFindersEvent event) {
        event.addPackFinders(MystIds.id("datapacks/mystcraft_facility"), PackType.SERVER_DATA,
                Component.literal("Mystcraft Reborn: Facility rooms"), PackSource.BUILT_IN, false, Pack.Position.TOP);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LinkListeners.registerDefaults();
            InstabilityManager.registerDefaults();
            SymbolRegistry.bootstrapBuiltins();
            SymbolRegistry.freeze();
            LOGGER.info("Mystcraft Reborn: {} symbols registered", SymbolRegistry.all().size());
        });
    }
}
