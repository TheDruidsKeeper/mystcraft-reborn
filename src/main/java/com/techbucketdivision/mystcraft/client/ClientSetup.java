package com.techbucketdivision.mystcraft.client;

import com.techbucketdivision.mystcraft.client.particle.LinkParticle;
import com.techbucketdivision.mystcraft.client.render.AgeCloudRenderer;
import com.techbucketdivision.mystcraft.client.render.AgeSkyRenderer;
import com.techbucketdivision.mystcraft.client.render.AgeWeatherRenderer;
import com.techbucketdivision.mystcraft.client.render.blockentity.BookDisplayRenderer;
import com.techbucketdivision.mystcraft.client.render.blockentity.BookReceptacleRenderer;
import com.techbucketdivision.mystcraft.client.render.blockentity.StarFissureRenderer;
import com.techbucketdivision.mystcraft.client.render.blockentity.WritingDeskRenderer;
import com.techbucketdivision.mystcraft.client.render.entity.ColoredLightningRenderer;
import com.techbucketdivision.mystcraft.client.render.entity.LinkbookRenderer;
import com.techbucketdivision.mystcraft.client.render.entity.MeteorRenderer;
import com.techbucketdivision.mystcraft.client.render.entity.MystFallingBlockRenderer;
import com.techbucketdivision.mystcraft.client.render.tint.InkTintSource;
import com.techbucketdivision.mystcraft.client.render.tint.PortalTintSource;
import com.techbucketdivision.mystcraft.client.screen.ArchivistShopScreen;
import com.techbucketdivision.mystcraft.client.screen.BookBinderScreen;
import com.techbucketdivision.mystcraft.client.screen.BookScreen;
import com.techbucketdivision.mystcraft.client.screen.FolderScreen;
import com.techbucketdivision.mystcraft.client.screen.InkMixerScreen;
import com.techbucketdivision.mystcraft.client.screen.LinkModifierScreen;
import com.techbucketdivision.mystcraft.client.screen.WritingDeskScreen;
import com.techbucketdivision.mystcraft.menu.AbstractMystcraftMenu;
import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.registry.ModEntities;
import com.techbucketdivision.mystcraft.registry.ModMenus;
import com.techbucketdivision.mystcraft.registry.ModParticles;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

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
        modBus.addListener(ClientPayloadHandlers::register);
        modBus.addListener(ClientSetup::registerEnvironmentRenderers);
        modBus.addListener(ClientSetup::registerParticles);
        modBus.addListener(ClientSetup::registerBlockTints);

        ClientGameEvents.register(NeoForge.EVENT_BUS);
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

    private static void registerEnvironmentRenderers(RegisterCustomEnvironmentEffectRendererEvent event) {
        event.registerSkyboxRenderer(AGE_ENVIRONMENT, new AgeSkyRenderer());
        event.registerCloudRenderer(AGE_ENVIRONMENT, new AgeCloudRenderer());
        event.registerWeatherEffectRenderer(AGE_ENVIRONMENT, new AgeWeatherRenderer());
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.LINK.get(), LinkParticle::provider);
    }

    private static void registerBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(new PortalTintSource()), ModBlocks.LINK_PORTAL.get());
        event.register(List.of(new InkTintSource()), ModBlocks.BLACK_INK.get());
    }
}
