package com.tbd.mystcraft.client;

import com.tbd.mystcraft.api.symbol.logic.ColorKind;
import com.tbd.mystcraft.client.particle.LinkParticle;
import com.tbd.mystcraft.client.render.AgeCloudRenderer;
import com.tbd.mystcraft.client.render.AgeSkyRenderer;
import com.tbd.mystcraft.client.render.AgeWeatherRenderer;
import com.tbd.mystcraft.client.render.blockentity.BookDisplayRenderer;
import com.tbd.mystcraft.client.render.blockentity.BookReceptacleRenderer;
import com.tbd.mystcraft.client.render.blockentity.StarFissureRenderer;
import com.tbd.mystcraft.client.render.blockentity.WritingDeskRenderer;
import com.tbd.mystcraft.client.render.entity.ColoredLightningRenderer;
import com.tbd.mystcraft.client.render.entity.LinkbookRenderer;
import com.tbd.mystcraft.client.render.entity.MeteorRenderer;
import com.tbd.mystcraft.client.render.entity.MystFallingBlockRenderer;
import com.tbd.mystcraft.client.render.item.SymbolPageSpecialRenderer;
import com.tbd.mystcraft.client.render.model.LegacyModels;
import com.tbd.mystcraft.client.render.tint.AgeBiomeTintSource;
import com.tbd.mystcraft.client.render.tint.InkTintSource;
import com.tbd.mystcraft.client.render.tint.PortalTintSource;
import com.tbd.mystcraft.client.screen.ArchivistShopScreen;
import com.tbd.mystcraft.client.screen.BookBinderScreen;
import com.tbd.mystcraft.client.screen.BookScreen;
import com.tbd.mystcraft.client.screen.FolderScreen;
import com.tbd.mystcraft.client.screen.InkMixerScreen;
import com.tbd.mystcraft.client.screen.LinkModifierScreen;
import com.tbd.mystcraft.client.screen.WritingDeskScreen;
import com.tbd.mystcraft.menu.AbstractMystcraftMenu;
import com.tbd.mystcraft.registry.ModBlockEntities;
import com.tbd.mystcraft.registry.ModBlocks;
import com.tbd.mystcraft.registry.ModEntities;
import com.tbd.mystcraft.registry.ModFluids;
import com.tbd.mystcraft.registry.ModMenus;
import com.tbd.mystcraft.registry.ModParticles;
import com.tbd.mystcraft.util.MystIds;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/** All client registration. Called once from {@code MystcraftClient}. */
public final class ClientSetup {
    private ClientSetup() {}

    /** Id of the custom environment renderers referenced by {@code data/mystcraft/dimension_type/age.json}. */
    public static final Identifier AGE_ENVIRONMENT = MystIds.id("age");

    public static void register(IEventBus modBus) {
        AbstractMystcraftMenu.setClientSender(ClientNetwork.INSTANCE);

        modBus.addListener(ClientSetup::registerScreens);
        modBus.addListener(ClientSetup::registerRenderers);
        modBus.addListener(ClientSetup::registerLayers);
        modBus.addListener(ClientPayloadHandlers::register);
        modBus.addListener(ClientSetup::registerEnvironmentRenderers);
        modBus.addListener(ClientSetup::registerParticles);
        modBus.addListener(ClientSetup::registerBlockTints);
        modBus.addListener(ClientSetup::registerFluidModels);
        modBus.addListener(ClientSetup::registerSpecialModels);

        ClientGameEvents.register(NeoForge.EVENT_BUS);
        ClientSelfCheck.register(NeoForge.EVENT_BUS);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.WRITING_DESK.get(), WritingDeskScreen::new);
        event.register(ModMenus.BOOK_BINDER.get(), BookBinderScreen::new);
        event.register(ModMenus.INK_MIXER.get(), InkMixerScreen::new);
        event.register(ModMenus.LINK_MODIFIER.get(), LinkModifierScreen::new);
        event.register(ModMenus.BOOK.get(), BookScreen::new);
        event.register(ModMenus.FOLDER.get(), FolderScreen::new);
        event.register(ModMenus.ARCHIVIST_SHOP.get(), ArchivistShopScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.WRITING_DESK.get(), WritingDeskRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BOOK_DISPLAY.get(), BookDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BOOK_RECEPTACLE.get(), BookReceptacleRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STAR_FISSURE.get(), StarFissureRenderer::new);

        event.registerEntityRenderer(ModEntities.LINKBOOK.get(), LinkbookRenderer::new);
        event.registerEntityRenderer(ModEntities.METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(ModEntities.FALLING_BLOCK.get(), MystFallingBlockRenderer::new);
        event.registerEntityRenderer(ModEntities.LIGHTNING.get(), ColoredLightningRenderer::new);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LegacyModels.BOOKSTAND, LegacyModels::bookstand);
        event.registerLayerDefinition(LegacyModels.WRITING_DESK, LegacyModels::writingDesk);
    }

    private static void registerEnvironmentRenderers(RegisterCustomEnvironmentEffectRendererEvent event) {
        event.registerSkyboxRenderer(AGE_ENVIRONMENT, new AgeSkyRenderer());
        event.registerCloudRenderer(AGE_ENVIRONMENT, new AgeCloudRenderer());
        event.registerWeatherEffectRenderer(AGE_ENVIRONMENT, new AgeWeatherRenderer());
    }

    /** Page icons are drawn from the symbol glyph sheet (see {@link SymbolPageSpecialRenderer}). */
    private static void registerSpecialModels(RegisterSpecialModelRendererEvent event) {
        event.register(SymbolPageSpecialRenderer.ID, SymbolPageSpecialRenderer.Unbaked.MAP_CODEC);
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.LINK.get(), LinkParticle::provider);
    }

    private static void registerBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(new PortalTintSource()), ModBlocks.LINK_PORTAL.get());
        event.register(List.of(new InkTintSource()), ModBlocks.BLACK_INK.get());

        // Age colour symbols: wrap the vanilla biome tint sources (registered before this event fires, see
        // BlockColors.createDefault) so a static GRASS/FOLIAGE/WATER colour replaces the biome colour inside an Age.
        BlockColors colors = event.getBlockColors();
        wrapBiomeTints(event, colors, ColorKind.GRASS,
                Blocks.GRASS_BLOCK, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.POTTED_FERN, Blocks.BUSH,
                Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.PINK_PETALS, Blocks.WILDFLOWERS, Blocks.SUGAR_CANE);
        wrapBiomeTints(event, colors, ColorKind.FOLIAGE,
                Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
                Blocks.MANGROVE_LEAVES, Blocks.VINE);
        wrapBiomeTints(event, colors, ColorKind.WATER,
                Blocks.WATER, Blocks.BUBBLE_COLUMN, Blocks.WATER_CAULDRON);
    }

    private static void wrapBiomeTints(RegisterColorHandlersEvent.BlockTintSources event, BlockColors colors,
                                       ColorKind kind, Block... blocks) {
        for (Block block : blocks) {
            List<BlockTintSource> existing = colors.getTintSources(block.defaultBlockState());
            if (existing.isEmpty()) continue;
            List<BlockTintSource> wrapped = new ArrayList<>(existing.size());
            for (BlockTintSource source : existing) wrapped.add(new AgeBiomeTintSource(kind, source));
            event.register(List.copyOf(wrapped), block);
        }
    }

    /**
     * 26.1 moved fluid textures off {@code IClientFluidTypeExtensions}: without a {@link FluidModel} the placed ink
     * renders as the missing texture ("Missing FluidModel for fluid 'mystcraft:black_ink'") and the
     * {@code neoforge:fluid_container} bucket model has no fluid sprite. One model is shared by source and flowing.
     */
    private static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(new FluidModel.Unbaked(
                        new Material(MystIds.id("block/fluid")),
                        new Material(MystIds.id("block/fluid_flow")),
                        null,
                        new InkTintSource()),
                ModFluids.BLACK_INK, ModFluids.FLOWING_BLACK_INK);
    }
}
