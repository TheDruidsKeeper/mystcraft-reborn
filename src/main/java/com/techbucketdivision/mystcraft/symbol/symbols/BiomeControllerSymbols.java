package com.techbucketdivision.mystcraft.symbol.symbols;

import com.techbucketdivision.mystcraft.api.symbol.AgeDirector;
import com.techbucketdivision.mystcraft.api.symbol.ModifierUtils;
import com.techbucketdivision.mystcraft.world.biome.LayeredBiomeController;
import com.techbucketdivision.mystcraft.world.biome.NativeBiomeController;
import com.techbucketdivision.mystcraft.world.biome.SingleBiomeController;
import com.techbucketdivision.mystcraft.world.biome.TiledBiomeController;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.techbucketdivision.mystcraft.api.symbol.WordData.*;

/** Biome distribution symbols (original spec §4.3.5). */
public final class BiomeControllerSymbols {
    private BiomeControllerSymbols() {}

    /** Pops every pending biome (LIFO order as the director returns them). */
    static List<Holder<Biome>> popAllBiomes(AgeDirector director) {
        List<Holder<Biome>> biomes = new ArrayList<>();
        Holder<Biome> biome = director.popBiome();
        while (biome != null) {
            biomes.add(biome);
            biome = director.popBiome();
        }
        return biomes;
    }

    /** Pads the list with random selectable biomes (seeded by the Age seed) until it has {@code min} entries. */
    static void pad(AgeDirector director, List<Holder<Biome>> biomes, int min) {
        Random rand = new Random(director.getSeed());
        while (biomes.size() < min) {
            biomes.add(ModifierUtils.randomBiome(director, rand.nextLong()));
        }
    }

    /** Native Biome Distribution: vanilla overworld multi-noise with the Age seed. */
    public static final class Native extends SimpleSymbol {
        public Native() { super("biome_native", 3, CONSTRAINT, NATURE, TRADITION, SUSTAIN); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            if (director.isProfiling() || director.isClientSide()) {
                director.registerInterface(Markers.BIOME_CONTROLLER); // client has no multi-noise parameter lists
                return;
            }
            director.registerInterface(new NativeBiomeController(director.getSeed(), director.registries()));
        }
    }

    /** Single: one biome everywhere (random if none written). */
    public static final class Single extends SimpleSymbol {
        public Single() { super("biome_single", 3, CONSTRAINT, NATURE, INFINITE, STATIC); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Holder<Biome> biome = director.popBiome();
            if (director.isProfiling()) {
                director.registerInterface(Markers.BIOME_CONTROLLER);
                return;
            }
            if (biome == null) biome = ModifierUtils.randomBiome(director, director.getSeed());
            director.registerInterface(new SingleBiomeController(biome));
        }
    }

    /** Tiled / Grid-form: biome = list[((x>>4)+(z>>4)) mod n]; grid samples at generation scale (x4). */
    public static final class Tiled extends SimpleSymbol {
        private final boolean grid;

        public Tiled(boolean grid) {
            super(grid ? "biome_grid" : "biome_tiled", 3, CONSTRAINT, NATURE, CHAIN, grid ? MUTUAL : CONTRADICT);
            this.grid = grid;
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            List<Holder<Biome>> biomes = popAllBiomes(director);
            if (director.isProfiling()) {
                director.registerInterface(Markers.BIOME_CONTROLLER);
                return;
            }
            pad(director, biomes, 2);
            director.registerInterface(new TiledBiomeController(biomes, grid));
        }
    }

    /** GenLayer-like distribution with a zoom scale (Tiny 0 .. Huge 4); pads to at least 3 biomes. */
    public static final class Layered extends SimpleSymbol {
        private final int zoom;

        public Layered(String path, int zoom, int rank, String fourthWord) {
            super(path, rank, CONSTRAINT, NATURE, WEAVE, fourthWord);
            this.zoom = zoom;
        }

        public int zoom() {
            return zoom;
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            List<Holder<Biome>> biomes = popAllBiomes(director);
            if (director.isProfiling()) {
                director.registerInterface(Markers.BIOME_CONTROLLER);
                return;
            }
            pad(director, biomes, 3);
            director.registerInterface(new LayeredBiomeController(director.getSeed(), zoom, biomes));
        }
    }

    public static Layered tiny() { return new Layered("biome_tiny", 0, 3, "Tiny"); }
    public static Layered small() { return new Layered("biome_small", 1, 3, "Small"); }
    public static Layered medium() { return new Layered("biome_medium", 2, 3, "Medium"); }
    public static Layered large() { return new Layered("biome_large", 3, 3, "Large"); }
    public static Layered huge() { return new Layered("biome_huge", 4, 3, "Huge"); }
}
