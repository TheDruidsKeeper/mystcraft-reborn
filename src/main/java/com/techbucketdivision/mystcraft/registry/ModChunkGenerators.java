package com.techbucketdivision.mystcraft.registry;

import com.mojang.serialization.MapCodec;
import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.world.AgeChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModChunkGenerators {
    private ModChunkGenerators() {}

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Mystcraft.MOD_ID);

    static {
        CHUNK_GENERATORS.register("age", () -> AgeChunkGenerator.CODEC);
    }
}
