package com.techbucketdivision.mystcraft.instability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-Age accumulation of block accessibility and watched-block density (REQUIREMENTS §6.2). Stored as per-level
 * SavedData {@code mystcraft:chunk_profile}. Every newly generated chunk of an Age level is fed in from
 * {@link ChunkEvent.Load}.
 */
@EventBusSubscriber(modid = Mystcraft.MOD_ID)
public final class ChunkProfiler extends SavedData {
    private static final Codec<ChunkProfiler> CODEC = RecordCodecBuilder.create(i -> i.group(
            ChunkProfileData.CODEC.fieldOf("solid").forGetter(p -> p.solidMap),
            Codec.unboundedMap(Codec.STRING, ChunkProfileData.CODEC).optionalFieldOf("blocks", Map.of()).forGetter(p -> p.blockMaps)
    ).apply(i, ChunkProfiler::new));

    public static final SavedDataType<ChunkProfiler> TYPE = new SavedDataType<>(MystIds.id("chunk_profile"), ChunkProfiler::new, CODEC);

    private ChunkProfileData solidMap;
    private Map<String, ChunkProfileData> blockMaps;
    private int count;
    private @Nullable Map<String, Float> lastSplit;

    public ChunkProfiler() {
        this.solidMap = new ChunkProfileData();
        this.blockMaps = new LinkedHashMap<>();
        ensureWatchedMaps();
    }

    private ChunkProfiler(ChunkProfileData solid, Map<String, ChunkProfileData> blocks) {
        this.solidMap = solid;
        this.blockMaps = new LinkedHashMap<>(blocks);
        ensureWatchedMaps();
        this.count = solid.count / 2;
        for (ChunkProfileData map : blockMaps.values()) {
            if (map.count < count) count = map.count;
        }
    }

    public static ChunkProfiler get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private void ensureWatchedMaps() {
        for (String key : InstabilityBlocks.watchedKeys()) {
            blockMaps.computeIfAbsent(key, k -> new ChunkProfileData());
        }
    }

    /** Number of profiled chunks. */
    public synchronized int count() {
        return count;
    }

    public synchronized void clear() {
        count = 0;
        solidMap = new ChunkProfileData();
        blockMaps = new LinkedHashMap<>();
        ensureWatchedMaps();
        lastSplit = null;
        setDirty();
    }

    public @Nullable Map<String, Float> lastSplit() {
        return lastSplit;
    }

    // --- profiling -------------------------------------------------------------------------------------------

    public synchronized void profile(LevelChunk chunk) {
        ensureWatchedMaps();
        int[] solid = solidMap.data;
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int ly = 0; ly < 16; ly++) {
                int y = baseY + ly;
                if (y < ChunkProfileData.MIN_Y || y >= ChunkProfileData.MIN_Y + ChunkProfileData.LAYERS) continue;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int idx = ChunkProfileData.index(x, y, z);
                        if (section.hasOnlyAir()) continue; // air contributes 0
                        BlockState state = section.getBlockState(x, ly, z);
                        int accessibility = state.isAir() ? 0 : 2;
                        String key = InstabilityBlocks.keyFor(state);
                        if (key != null) {
                            ChunkProfileData map = blockMaps.get(key);
                            if (map != null) {
                                map.data[idx]++;
                                accessibility = 1;
                            }
                        }
                        if (!state.isAir() && !state.blocksMotion()) accessibility = 1;
                        solid[idx] += accessibility;
                    }
                }
            }
        }
        solidMap.count += 2;
        for (ChunkProfileData map : blockMaps.values()) map.count++;
        count++;
        setDirty();
    }

    // --- maths -------------------------------------------------------------------------------------------------

    /** Total block instability: {@code sum(max(0, split - baseline))} (negatives pass through). 0 without baseline. */
    public synchronized int calculateInstability() {
        if (!BaselineProfiler.isConstructed()) return 0;
        Map<String, Float> split = calculateSplitInstability();
        float instability = 0;
        for (Map.Entry<String, Float> e : split.entrySet()) {
            float val = e.getValue();
            if (val > 0) val = Math.max(0, val - BaselineProfiler.baseline(e.getKey()));
            instability += val;
        }
        return Math.round(instability);
    }

    /** Per watched-block instability contribution (REQUIREMENTS §6.2). */
    public synchronized Map<String, Float> calculateSplitInstability() {
        int layers = ChunkProfileData.LAYERS;
        float[] averages = new float[layers];
        float[] rounded = new float[layers];
        float[] accessibility = new float[layers];
        float minimum = 1;
        float solidCount = Math.max(1, solidMap.count);
        for (int y = 0; y < layers; y++) {
            float sum = 0;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    sum += solidMap.data[y << 8 | z << 4 | x] / solidCount;
                }
            }
            averages[y] = sum / 256f;
            if (averages[y] < minimum) minimum = averages[y];
        }
        float groundSum = 0;
        int groundCount = 0;
        for (int y = 0; y < layers; y++) {
            float filtered = Math.max(0, averages[y] - minimum);
            rounded[y] = Math.round(100 * filtered) / 100f;
            if (rounded[y] > 0) {
                groundSum += rounded[y];
                groundCount++;
            }
        }
        float ground = groundCount == 0 ? 0 : groundSum / groundCount;
        for (int y = 0; y < layers; y++) {
            boolean solid = rounded[y] > ground;
            accessibility[y] = solid ? 1 - rounded[y] : 1;
        }
        Map<String, Float> split = new HashMap<>();
        for (String key : InstabilityBlocks.watchedKeys()) {
            ChunkProfileData map = blockMaps.get(key);
            if (map == null || map.count < 100) continue;
            float factor1 = InstabilityBlocks.factor1(key);
            float factor2 = InstabilityBlocks.factor2(key);
            float total = 0;
            for (int y = 0; y < layers; y++) {
                float avail = accessibility[y];
                int base = y << 8;
                for (int c = 0; c < 256; c++) {
                    float density = map.data[base | c] / (float) map.count;
                    if (density == 0) continue;
                    total += density * avail * factor1 + density * factor2;
                }
            }
            split.put(key, total);
        }
        lastSplit = split;
        return split;
    }

    // --- hook ----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!AgeData.isAgeLevel(level.dimension())) return;
        try {
            get(level).profile(chunk);
        } catch (Exception e) {
            Mystcraft.LOGGER.error("Failed to profile chunk {} of {}", chunk.getPos(), level.dimension().identifier(), e);
        }
    }
}
