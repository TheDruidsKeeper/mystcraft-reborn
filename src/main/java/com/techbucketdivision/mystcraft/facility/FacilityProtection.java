package com.techbucketdivision.mystcraft.facility;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import com.techbucketdivision.mystcraft.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * Facility protection (FACILITY_PLAN.md §2.3, decision §8.3): until the facility is solved, blocks inside its
 * structure bounds cannot be broken by survival players and are not destroyed by explosions. Creative players and
 * operators are exempt. {@code facility.protection} = {@code full} (default) or {@code none}.
 */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class FacilityProtection {
    private FacilityProtection() {}

    public static boolean enabled() {
        return !"none".equalsIgnoreCase(MystcraftConfig.FACILITY_PROTECTION.get());
    }

    public static boolean exempt(@Nullable Player player) {
        return player != null && (player.isCreative() || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    /** Whether {@code pos} lies inside an unsolved Facility of an Age. */
    public static boolean protects(ServerLevel level, BlockPos pos) {
        if (!enabled() || !AgeData.isAgeLevel(level.dimension()) || FacilityState.isSolved(level)) return false;
        return insideFacility(level, pos);
    }

    public static boolean insideFacility(ServerLevel level, BlockPos pos) {
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(ModStructures.FACILITY);
        return structure != null && level.structureManager().getStructureAt(pos, structure).isValid();
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || exempt(event.getPlayer())) return;
        if (protects(level, event.getPos())) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.mystcraft.facility.protected"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !enabled()) return;
        event.getAffectedBlocks().removeIf(pos -> protects(level, pos));
    }
}
