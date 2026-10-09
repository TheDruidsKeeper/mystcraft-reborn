package com.tbd.mystcraft.villager;

import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.config.MystcraftConfig;
import com.tbd.mystcraft.menu.ArchivistShopMenu;
import com.tbd.mystcraft.registry.ModVillagers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Right-clicking an Archivist (not sneaking) opens the page shop instead of the vanilla trade screen. */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class ArchivistEvents {
    private ArchivistEvents() {}

    public static boolean isArchivist(Villager villager) {
        return villager.getVillagerData().profession().is(ModVillagers.ARCHIVIST_KEY);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!MystcraftConfig.ARCHIVIST_ENABLED.get()) return;
        if (!(event.getTarget() instanceof Villager villager) || !isArchivist(villager)) return;
        if (event.getEntity().isShiftKeyDown()) return;
        if (!villager.isAlive() || villager.isBaby()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            ArchivistShop shop = ArchivistShop.of(villager);
            shop.ensureStocked(player.level().getRandom());
            shop.simulate(player.level().getGameTime(), player.level().getRandom());
            shop.save(villager);
            ArchivistShopMenu.open(player, villager);
        }
    }
}
