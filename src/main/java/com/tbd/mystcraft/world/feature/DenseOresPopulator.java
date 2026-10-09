package com.tbd.mystcraft.world.feature;

import com.tbd.mystcraft.api.symbol.logic.Populator;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

/**
 * "Dense Ores" (original spec §4.3.7): coal ×20 (size 16, y 0–128), iron ×20 (8, 0–64), gold ×2 (8, 0–32),
 * redstone ×8 (7, 0–16), diamond ×1 (7, 0–16), lapis ×1 (6, 0–16), emerald ×6 (1, 4–32), nether quartz ×10
 * (13 in netherrack, 10–256). Uses vanilla {@link Feature#ORE}.
 */
public final class DenseOresPopulator implements Populator {

    private record Vein(ConfiguredFeature<OreConfiguration, ?> feature, int times, int minY, int maxY) {}

    private final Vein[] veins;

    public DenseOresPopulator() {
        RuleTest stone = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
        RuleTest netherrack = new BlockMatchTest(Blocks.NETHERRACK);
        veins = new Vein[] {
                vein(stone, Blocks.COAL_ORE, 16, 20, 0, 128),
                vein(stone, Blocks.IRON_ORE, 8, 20, 0, 64),
                vein(stone, Blocks.GOLD_ORE, 8, 2, 0, 32),
                vein(stone, Blocks.REDSTONE_ORE, 7, 8, 0, 16),
                vein(stone, Blocks.DIAMOND_ORE, 7, 1, 0, 16),
                vein(stone, Blocks.LAPIS_ORE, 6, 1, 0, 16),
                vein(stone, Blocks.EMERALD_ORE, 1, 6, 4, 32),
                vein(netherrack, Blocks.NETHER_QUARTZ_ORE, 13, 10, 10, 256),
        };
    }

    private static Vein vein(RuleTest target, Block ore, int size, int times, int minY, int maxY) {
        ConfiguredFeature<OreConfiguration, ?> cf = new ConfiguredFeature<>(Feature.ORE,
                new OreConfiguration(target, ore.defaultBlockState(), size));
        return new Vein(cf, times, minY, maxY);
    }

    @Override
    public boolean populate(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, boolean flag) {
        ChunkGenerator generator = level.getLevel().getChunkSource().getGenerator();
        int x = chunkX << 4;
        int z = chunkZ << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Vein vein : veins) {
            for (int i = 0; i < vein.times(); ++i) {
                int px = x + random.nextInt(16);
                int py = random.nextInt(vein.maxY() - vein.minY()) + vein.minY();
                int pz = z + random.nextInt(16);
                pos.set(px, py, pz);
                vein.feature().place(level, generator, random, pos.immutable());
            }
        }
        return false;
    }
}
