package com.techbucketdivision.mystcraft.symbol.symbols;

import com.techbucketdivision.mystcraft.api.symbol.AgeDirector;
import com.techbucketdivision.mystcraft.api.symbol.BlockCategory;
import com.techbucketdivision.mystcraft.api.symbol.ModifierUtils;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.world.feature.CavesAlteration;
import com.techbucketdivision.mystcraft.world.feature.CrystalFormationPopulator;
import com.techbucketdivision.mystcraft.world.feature.DenseOresPopulator;
import com.techbucketdivision.mystcraft.world.feature.DungeonsPopulator;
import com.techbucketdivision.mystcraft.world.feature.FloatingIslandsAlteration;
import com.techbucketdivision.mystcraft.world.feature.HugeTreesAlteration;
import com.techbucketdivision.mystcraft.world.feature.LakesPopulator;
import com.techbucketdivision.mystcraft.world.feature.ObelisksPopulator;
import com.techbucketdivision.mystcraft.world.feature.RavinesAlteration;
import com.techbucketdivision.mystcraft.world.feature.SkylandsAlteration;
import com.techbucketdivision.mystcraft.world.feature.SpheresAlteration;
import com.techbucketdivision.mystcraft.world.feature.SpikesPopulator;
import com.techbucketdivision.mystcraft.world.feature.StarFissurePopulator;
import com.techbucketdivision.mystcraft.world.feature.VanillaStructurePopulator;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.techbucketdivision.mystcraft.api.symbol.WordData.*;

/** Large / medium / small feature symbols (REQUIREMENTS §4.3.7–4.3.9). */
public final class FeatureSymbols {
    private FeatureSymbols() {}

    public static final int STRUCTURE_OVERUSE_INSTABILITY = 100;

    // --- large ---------------------------------------------------------------------------------------------------

    /** Caves: cave carver rate 15, size 40, carving air. */
    public static final class Caves extends SimpleSymbol {
        public Caves() { super("caves", 2, TERRAIN, TRANSFORM, VOID, FLOW); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            if (director.isProfiling()) {
                director.registerInterface(Markers.TERRAIN_ALTERATION);
                return;
            }
            director.registerInterface(new CavesAlteration(seed, 15, 40, Blocks.AIR.defaultBlockState()));
        }
    }

    /** Tendrils: cave algorithm (rate 15, size 18) placing the STRUCTURE block (default oak log). */
    public static final class Tendrils extends SimpleSymbol {
        public Tendrils() { super("tendrils", 3, TERRAIN, TRANSFORM, GROWTH, FLOW); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            BlockState block = ModifierUtils.popStructureBlock(director, Blocks.OAK_LOG.defaultBlockState());
            if (director.isProfiling()) {
                director.registerInterface(Markers.TERRAIN_ALTERATION);
                return;
            }
            director.registerInterface(new CavesAlteration(seed, 15, 18, block));
        }
    }

    /** Skylands: removes everything below a noise cut; cloud height 42.5, horizon 0. */
    public static final class Skylands extends SimpleSymbol {
        public Skylands() { super("skylands", 3, TERRAIN, TRANSFORM, VOID, ELEVATE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            if (director.isProfiling()) {
                director.registerInterface(Markers.TERRAIN_ALTERATION);
            } else {
                director.registerInterface(new SkylandsAlteration(director.getSeed()));
            }
            director.setCloudHeight(42.5f);
            director.setHorizon(0);
        }
    }

    /** Floating Islands: pops a biome (random if none) and a STRUCTURE block (default stone). */
    public static final class FloatingIslands extends SimpleSymbol {
        public FloatingIslands() { super("floating_islands", 3, TERRAIN, TRANSFORM, FORM, CELESTIAL); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            Holder<Biome> biome = director.popBiome();
            BlockState block = ModifierUtils.popStructureBlock(director, Blocks.STONE.defaultBlockState());
            if (director.isProfiling()) {
                director.registerInterface(Markers.ALTERATION_AND_FINALIZER);
                return;
            }
            if (biome == null) biome = ModifierUtils.randomBiome(director, seed);
            director.registerInterface(new FloatingIslandsAlteration(seed, biome, block));
        }
    }

    /** Huge Trees. */
    public static final class HugeTrees extends SimpleSymbol {
        public HugeTrees() { super("huge_trees", 2, NATURE, STIMULATE, SPUR, ELEVATE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            if (director.isProfiling()) {
                director.registerInterface(Markers.TERRAIN_ALTERATION);
                return;
            }
            director.registerInterface(new HugeTreesAlteration(seed));
        }
    }

    /** Dense Ores. */
    public static final class DenseOres extends SimpleSymbol {
        public DenseOres() { super("dense_ores", 5, ENVIRONMENT, STIMULATE, MACHINE, CHAOS); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new DenseOresPopulator());
        }
    }

    // --- medium --------------------------------------------------------------------------------------------------

    /** Vanilla structure symbols (villages, strongholds, mineshafts, nether fortress): +100 when written > 3 times. */
    public static final class VanillaStructure extends SimpleSymbol {
        private final String kind;

        public VanillaStructure(String path, String kind, String... words) {
            super(path, 3, words);
            this.kind = kind;
        }

        public String kind() {
            return kind;
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            if (director.isProfiling()) {
                director.registerInterface(Markers.POPULATOR);
                return;
            }
            director.registerInterface(new VanillaStructurePopulator(kind));
        }

        @Override
        public int instabilityModifier(int count) {
            return count > 3 ? STRUCTURE_OVERUSE_INSTABILITY : 0;
        }
    }

    public static VanillaStructure villages() {
        return new VanillaStructure("villages", "villages", CIVILIZATION, SOCIETY, HARMONY, NURTURE);
    }

    public static VanillaStructure strongholds() {
        return new VanillaStructure("strongholds", "strongholds", CIVILIZATION, WISDOM, FUTURE, HONOR);
    }

    public static VanillaStructure mineshafts() {
        return new VanillaStructure("mineshafts", "mineshafts", CIVILIZATION, MACHINE, MOTION, TRADITION);
    }

    public static VanillaStructure netherFortress() {
        return new VanillaStructure("nether_fortress", "nether_fortress", CIVILIZATION, MACHINE, POWER, ENTROPY);
    }

    /** Ravines. */
    public static final class Ravines extends SimpleSymbol {
        public Ravines() { super("ravines", 2, TERRAIN, TRANSFORM, VOID, WEAVE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            if (director.isProfiling()) {
                director.registerInterface(Markers.TERRAIN_ALTERATION);
                return;
            }
            director.registerInterface(new RavinesAlteration(seed));
        }
    }

    /** Dungeons: 8 vanilla dungeon attempts per chunk. */
    public static final class Dungeons extends SimpleSymbol {
        public Dungeons() { super("dungeons", 2, CIVILIZATION, CONSTRAINT, CHAIN, RESURRECT); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(new DungeonsPopulator());
        }
    }

    /** Spheres of the STRUCTURE block (default cobblestone). */
    public static final class Spheres extends SimpleSymbol {
        public Spheres() { super("spheres", 2, TERRAIN, TRANSFORM, FORM, CYCLE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            BlockState block = ModifierUtils.popStructureBlock(director, Blocks.COBBLESTONE.defaultBlockState());
            if (director.isProfiling()) {
                director.registerInterface(Markers.TERRAIN_ALTERATION);
                return;
            }
            director.registerInterface(new SpheresAlteration(seed, block));
        }
    }

    /** Spikes of the STRUCTURE block (default stone). */
    public static final class Spikes extends SimpleSymbol {
        public Spikes() { super("spikes", 3, NATURE, ENCOURAGE, ENTROPY, STRUCTURE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            BlockState block = ModifierUtils.popStructureBlock(director, Blocks.STONE.defaultBlockState());
            if (director.isProfiling()) {
                director.registerInterface(Markers.POPULATOR);
                return;
            }
            director.registerInterface(new SpikesPopulator(block));
        }
    }

    // --- small ---------------------------------------------------------------------------------------------------

    /** Surface Lakes of the FLUID block (default water). */
    public static final class LakesSurface extends SimpleSymbol {
        public LakesSurface() { super("lakes_surface", 3, NATURE, FLOW, STATIC, ELEVATE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            BlockState block = ModifierUtils.popBlockState(director, Blocks.WATER.defaultBlockState(), BlockCategory.FLUID);
            if (director.isProfiling()) {
                director.registerInterface(Markers.POPULATOR);
                return;
            }
            director.registerInterface(new LakesPopulator(block, false));
        }
    }

    /** Deep Lakes of a FLUID or GAS block (default lava). */
    public static final class LakesDeep extends SimpleSymbol {
        public LakesDeep() { super("lakes_deep", 3, NATURE, FLOW, STATIC, EXPLORE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            BlockState block = ModifierUtils.popBlockState(director, Blocks.LAVA.defaultBlockState(), BlockCategory.FLUID, BlockCategory.GAS);
            if (director.isProfiling()) {
                director.registerInterface(Markers.POPULATOR);
                return;
            }
            director.registerInterface(new LakesPopulator(block, true));
        }
    }

    /** Obelisks of the STRUCTURE block (default obsidian). */
    public static final class Obelisks extends SimpleSymbol {
        public Obelisks() { super("obelisks", 3, CIVILIZATION, RESILIENCE, STATIC, FORM); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            BlockState block = ModifierUtils.popStructureBlock(director, Blocks.OBSIDIAN.defaultBlockState());
            if (director.isProfiling()) {
                director.registerInterface(Markers.POPULATOR);
                return;
            }
            director.registerInterface(new ObelisksPopulator(block));
        }
    }

    /** Crystalline Formations of the CRYSTAL block (default mystcraft crystal). */
    public static final class CrystalFormations extends SimpleSymbol {
        public CrystalFormations() { super("crystal_formations", 3, NATURE, ENCOURAGE, GROWTH, STRUCTURE); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            if (director.isProfiling()) {
                director.popBlockMatching(BlockCategory.CRYSTAL);
                director.registerInterface(Markers.POPULATOR);
                return;
            }
            BlockState block = ModifierUtils.popBlockState(director, ModBlocks.CRYSTAL.get().defaultBlockState(), BlockCategory.CRYSTAL);
            director.registerInterface(new CrystalFormationPopulator(block));
        }
    }

    /** Star Fissure in the spawn chunk. */
    public static final class StarFissure extends SimpleSymbol {
        public StarFissure() { super("star_fissure", 3, NATURE, HARMONY, MUTUAL, VOID); }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            if (director.isProfiling()) {
                director.registerInterface(Markers.POPULATOR);
                return;
            }
            director.registerInterface(new StarFissurePopulator());
        }
    }
}
