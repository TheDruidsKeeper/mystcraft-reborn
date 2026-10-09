package com.tbd.mystcraft.registry;

import com.mojang.serialization.MapCodec;
import com.tbd.mystcraft.Mystcraft;
import com.tbd.mystcraft.world.AgeBiomeSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBiomeSources {
    private ModBiomeSources() {}

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, Mystcraft.MOD_ID);

    static {
        BIOME_SOURCES.register("age", () -> AgeBiomeSource.CODEC);
    }
}
