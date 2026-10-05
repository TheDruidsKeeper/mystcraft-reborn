package com.tbd.mystcraft.facility;

import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.age.AgeManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-Age puzzle state of the Facility (docs/plans/FACILITY_PLAN.md §2.2–2.3), kept in {@code AgeData.data("facility")}:
 * which locks are solved (doors stay open, decision §8.5) and whether the facility as a whole is solved (the first
 * Linking Book was claimed from the reward pedestal), which lifts the protection rule.
 *
 * <p>A lock id is the placement origin of the structure piece the lock sits in, so every lock, door and dial of a
 * piece share it. The sequence code is not stored: it is a pure function of the Age seed.
 *
 * <p>Levels that are not Ages (the GameTest level) use a transient in-memory store so the mechanics stay testable.
 */
public final class FacilityState {
    private FacilityState() {}

    private static final String KEY = "facility";
    private static final String SOLVED = "solved";
    private static final String LOCKS = "locks";
    private static final int GLYPHS = 8;
    private static final Map<ResourceKey<Level>, CompoundTag> TRANSIENT = new HashMap<>();

    private static @Nullable AgeData age(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return AgeManager.get(server, level.dimension());
    }

    private static CompoundTag tag(ServerLevel level) {
        AgeData data = age(level);
        if (data != null) return data.data(KEY);
        synchronized (TRANSIENT) {
            return TRANSIENT.computeIfAbsent(level.dimension(), k -> new CompoundTag());
        }
    }

    private static void dirty(ServerLevel level) {
        AgeData data = age(level);
        if (data != null) data.markDirty();
    }

    public static boolean isSolved(ServerLevel level) {
        return tag(level).getBooleanOr(SOLVED, false);
    }

    public static void markSolved(ServerLevel level) {
        tag(level).putBoolean(SOLVED, true);
        dirty(level);
    }

    public static boolean isLockSolved(ServerLevel level, long lockId) {
        for (long l : tag(level).getLongArray(LOCKS).orElse(new long[0])) if (l == lockId) return true;
        return false;
    }

    public static void solveLock(ServerLevel level, long lockId) {
        if (isLockSolved(level, lockId)) return;
        long[] old = tag(level).getLongArray(LOCKS).orElse(new long[0]);
        long[] out = new long[old.length + 1];
        System.arraycopy(old, 0, out, 0, old.length);
        out[old.length] = lockId;
        tag(level).putLongArray(LOCKS, out);
        dirty(level);
    }

    /** Seed every Facility choice derives from: the Age seed, or the level seed outside Ages. */
    public static long seed(ServerLevel level) {
        AgeData data = age(level);
        return data != null ? data.seed() : level.getSeed();
    }

    /** The Age's sequence code: {@code length} glyphs in {@code [0, GLYPHS)}, deterministic from the seed. */
    public static int[] code(ServerLevel level, int length) {
        RandomSource random = RandomSource.create(seed(level) ^ 0x5F4C17L);
        int[] code = new int[length];
        for (int i = 0; i < length; i++) code[i] = random.nextInt(GLYPHS);
        return code;
    }

    public static int glyphCount() {
        return GLYPHS;
    }
}
