package com.techbucketdivision.mystcraft.registry;

import com.google.common.collect.ImmutableSet;
import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.util.MystIds;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashSet;

/** The Archivist profession (original spec §10). Job site: Bookstand. Trades: datapack {@code trade_set}s. */
public final class ModVillagers {
    private ModVillagers() {}

    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Mystcraft.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, Mystcraft.MOD_ID);

    public static final ResourceKey<PoiType> ARCHIVIST_POI_KEY = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MystIds.id("archivist"));
    public static final ResourceKey<VillagerProfession> ARCHIVIST_KEY = ResourceKey.create(Registries.VILLAGER_PROFESSION, MystIds.id("archivist"));

    public static final DeferredHolder<PoiType, PoiType> ARCHIVIST_POI = POI_TYPES.register("archivist",
            () -> new PoiType(new HashSet<>(ModBlocks.BOOKSTAND.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> ARCHIVIST = PROFESSIONS.register("archivist",
            () -> new VillagerProfession(
                    Component.translatable("entity.minecraft.villager.mystcraft.archivist"),
                    holder -> holder.is(ARCHIVIST_POI_KEY),
                    holder -> holder.is(ARCHIVIST_POI_KEY),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN,
                    Int2ObjectMap.ofEntries(
                            Int2ObjectMap.entry(1, tradeSet("archivist/level_1")),
                            Int2ObjectMap.entry(2, tradeSet("archivist/level_2")),
                            Int2ObjectMap.entry(3, tradeSet("archivist/level_3")),
                            Int2ObjectMap.entry(4, tradeSet("archivist/level_4")),
                            Int2ObjectMap.entry(5, tradeSet("archivist/level_5")))));

    private static ResourceKey<TradeSet> tradeSet(String path) {
        return ResourceKey.create(Registries.TRADE_SET, MystIds.id(path));
    }
}
