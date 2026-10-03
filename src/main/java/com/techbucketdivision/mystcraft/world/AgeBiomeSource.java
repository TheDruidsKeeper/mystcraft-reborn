package com.techbucketdivision.mystcraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.techbucketdivision.mystcraft.age.AgeController;
import com.techbucketdivision.mystcraft.age.AgeControllers;
import com.techbucketdivision.mystcraft.api.symbol.logic.BiomeController;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Biome source of an Age: delegates to the Age's {@link BiomeController}. The codec stores only the Age id; the
 * controller is resolved lazily from the current server (fallback: plains everywhere when no server / controller).
 */
public final class AgeBiomeSource extends BiomeSource {
    public static final Codec<UUID> UUID_STRING_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    /**
     * The plains fallback is captured from the decoding {@code RegistryOps}, so a level stem deserialised without a
     * running server (WorldOpenFlows validating level.dat on the client before the integrated server exists) still has
     * a biome to report; previously this threw "AgeBiomeSource used without a server" and aborted the world load.
     */
    public static final MapCodec<AgeBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUID_STRING_CODEC.fieldOf("age_id").forGetter(s -> s.ageId),
            RegistryOps.retrieveElement(Biomes.PLAINS)
    ).apply(i, AgeBiomeSource::new));

    private final UUID ageId;
    private volatile @Nullable AgeController controller;
    private volatile @Nullable Holder<Biome> fallback;

    public AgeBiomeSource(UUID ageId, @Nullable Holder<Biome> fallback) {
        super();
        this.ageId = ageId;
        this.fallback = fallback;
    }

    public UUID ageId() {
        return ageId;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    /** The Age controller, resolved from the running server on first use (and re-resolved when it was unavailable). */
    public @Nullable AgeController controller() {
        AgeController c = controller;
        if (c != null) {
            c.ensureCurrent();
            return c;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        c = AgeControllers.server(server, ageId);
        if (c != null) controller = c;
        return c;
    }

    private Holder<Biome> fallbackBiome() {
        Holder<Biome> f = fallback;
        if (f != null) return f;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) throw new IllegalStateException("AgeBiomeSource " + ageId + " used without a server, a controller or a fallback biome");
        f = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        fallback = f;
        return f;
    }

    private @Nullable BiomeController biomeController() {
        AgeController c = controller();
        return c == null ? null : c.biomeController();
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        BiomeController bc = biomeController();
        List<Holder<Biome>> list = bc == null ? List.of(fallbackBiome()) : bc.possibleBiomes();
        return list.isEmpty() ? Stream.of(fallbackBiome()) : list.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        BiomeController bc = biomeController();
        return bc == null ? fallbackBiome() : bc.getNoiseBiome(quartX, quartY, quartZ);
    }
}
