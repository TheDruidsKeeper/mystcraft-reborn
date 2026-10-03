package com.techbucketdivision.mystcraft.age;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.techbucketdivision.mystcraft.util.MystIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Persistent description of one Age (REQUIREMENTS §5.2). Mutable; every mutation goes through a setter that marks the
 * owning storage dirty via {@link #markDirty()}. Synced to clients wholesale by {@code AgeDataSyncPayload}.
 */
public final class AgeData {
    public static final Codec<AgeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(a -> a.uuid),
            Codec.STRING.fieldOf("name").forGetter(a -> a.name),
            Codec.LONG.fieldOf("seed").forGetter(a -> a.seed),
            Codec.INT.optionalFieldOf("base_instability", 0).forGetter(a -> a.baseInstability),
            Codec.BOOL.optionalFieldOf("instability_enabled", true).forGetter(a -> a.instabilityEnabled),
            Codec.BOOL.optionalFieldOf("visited", false).forGetter(a -> a.visited),
            Codec.BOOL.optionalFieldOf("dead", false).forGetter(a -> a.dead),
            Codec.LONG.optionalFieldOf("world_time", 0L).forGetter(a -> a.worldTime),
            BlockPos.CODEC.optionalFieldOf("spawn").forGetter(a -> Optional.ofNullable(a.spawn)),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("pages", List.of()).forGetter(a -> a.pages),
            Identifier.CODEC.listOf().optionalFieldOf("symbols", List.of()).forGetter(a -> a.symbols),
            Codec.STRING.listOf().optionalFieldOf("authors", List.of()).forGetter(a -> a.authors),
            CompoundTag.CODEC.optionalFieldOf("data", new CompoundTag()).forGetter(a -> a.dataCompound)
    ).apply(i, AgeData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AgeData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    private final UUID uuid;
    private String name;
    private long seed;
    private int baseInstability;
    private boolean instabilityEnabled;
    private boolean visited;
    private boolean dead;
    private long worldTime;
    private @Nullable BlockPos spawn;
    private List<ItemStack> pages;
    private List<Identifier> symbols;
    private List<String> authors;
    private CompoundTag dataCompound;

    /** Incremented on every change; controllers compare it to know when to rebuild. */
    private transient int revision;
    private transient @Nullable Runnable dirtyListener;

    private AgeData(UUID uuid, String name, long seed, int baseInstability, boolean instabilityEnabled, boolean visited,
                    boolean dead, long worldTime, Optional<BlockPos> spawn, List<ItemStack> pages,
                    List<Identifier> symbols, List<String> authors, CompoundTag dataCompound) {
        this.uuid = uuid;
        this.name = name;
        this.seed = seed;
        this.baseInstability = baseInstability;
        this.instabilityEnabled = instabilityEnabled;
        this.visited = visited;
        this.dead = dead;
        this.worldTime = worldTime;
        this.spawn = spawn.orElse(null);
        this.pages = new ArrayList<>(pages);
        this.symbols = new ArrayList<>(symbols);
        this.authors = new ArrayList<>(authors);
        this.dataCompound = dataCompound.copy();
    }

    /** New Age with default values (REQUIREMENTS §5.1). */
    public static AgeData create(UUID uuid, long levelSeed, int ordinal) {
        long seed = levelSeed + new Random(ordinal).nextLong();
        return new AgeData(uuid, "Age " + ordinal, seed, 0, true, false, false, 0L, Optional.empty(),
                List.of(), List.of(), List.of(), new CompoundTag());
    }

    // --- identity ----------------------------------------------------------------------------------------------

    public UUID uuid() {
        return uuid;
    }

    /** {@code mystcraft:age_<uuid with underscores>} */
    public static ResourceKey<Level> levelKey(UUID uuid) {
        return ResourceKey.create(Registries.DIMENSION, MystIds.id("age_" + uuid.toString().replace('-', '_')));
    }

    public ResourceKey<Level> levelKey() {
        return levelKey(uuid);
    }

    public static @Nullable UUID uuidFromLevelKey(ResourceKey<Level> key) {
        Identifier id = key.identifier();
        if (!id.getNamespace().equals(com.techbucketdivision.mystcraft.Mystcraft.MOD_ID) || !id.getPath().startsWith("age_")) return null;
        try {
            return UUID.fromString(id.getPath().substring(4).replace('_', '-'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isAgeLevel(ResourceKey<Level> key) {
        return uuidFromLevelKey(key) != null;
    }

    // --- accessors ---------------------------------------------------------------------------------------------

    public String name() {
        return name;
    }

    public long seed() {
        return seed;
    }

    public int baseInstability() {
        return baseInstability;
    }

    public boolean instabilityEnabled() {
        return instabilityEnabled;
    }

    public boolean visited() {
        return visited;
    }

    public boolean dead() {
        return dead;
    }

    public long worldTime() {
        return worldTime;
    }

    public @Nullable BlockPos spawn() {
        return spawn;
    }

    public List<ItemStack> pages() {
        return List.copyOf(pages);
    }

    public List<Identifier> symbols() {
        return List.copyOf(symbols);
    }

    public List<String> authors() {
        return List.copyOf(authors);
    }

    /** Per-subsystem storage (e.g. {@code "weather"}). The returned tag is live; call {@link #markDirty()} after edits. */
    public CompoundTag data(String key) {
        CompoundTag tag = dataCompound.getCompound(key).orElse(null);
        if (tag == null) {
            tag = new CompoundTag();
            dataCompound.put(key, tag);
        }
        return tag;
    }

    public int revision() {
        return revision;
    }

    // --- mutators ----------------------------------------------------------------------------------------------

    public void setName(String name) {
        this.name = name;
        markDirty();
    }

    /** Only allowed before the Age was visited. */
    public boolean setSeed(long seed) {
        if (visited) return false;
        this.seed = seed;
        markDirty();
        return true;
    }

    public void addBaseInstability(int amount) {
        this.baseInstability += amount;
        markDirty();
    }

    public void setInstabilityEnabled(boolean enabled) {
        this.instabilityEnabled = enabled;
        markDirty();
    }

    public void markVisited() {
        if (!visited) {
            visited = true;
            markDirty();
        }
    }

    public void setDead(boolean dead) {
        this.dead = dead;
        markDirty();
    }

    /** Advances age time (no dirty marking: synced on the periodic resend, saved via {@link #markSaveNeeded()}). */
    public void setWorldTime(long time) {
        this.worldTime = time;
    }

    /**
     * Asks the owning storage to save without bumping the revision (no controller rebuild, no forced resync). Used
     * for the age clock, which changes every tick but must survive a restart (playtest: Age time reset on reload).
     */
    public void markSaveNeeded() {
        if (dirtyListener != null) dirtyListener.run();
    }

    public void setSpawn(@Nullable BlockPos spawn) {
        this.spawn = spawn == null ? null : spawn.immutable();
        markDirty();
    }

    public void setPages(List<ItemStack> pages) {
        this.pages = new ArrayList<>(pages.stream().map(ItemStack::copy).toList());
        markDirty();
    }

    public void setSymbols(List<Identifier> symbols) {
        this.symbols = new ArrayList<>(symbols);
        markDirty();
    }

    public void addSymbol(Identifier symbol) {
        this.symbols.add(symbol);
        markDirty();
    }

    public void setAuthors(List<String> authors) {
        this.authors = new ArrayList<>(authors);
        markDirty();
    }

    public void addAuthor(String author) {
        if (!authors.contains(author)) {
            authors.add(author);
            markDirty();
        }
    }

    /** Resets the Age for reuse as a fresh Age (recycling a dead one). */
    public void recreate(int ordinal) {
        this.name = "Age " + ordinal;
        this.seed = this.seed + new Random(ordinal).nextLong();
        this.baseInstability = 0;
        this.instabilityEnabled = true;
        this.visited = false;
        this.dead = false;
        this.worldTime = 0;
        this.spawn = null;
        this.pages = new ArrayList<>();
        this.symbols = new ArrayList<>();
        this.authors = new ArrayList<>();
        this.dataCompound = new CompoundTag();
        markDirty();
    }

    public void markDirty() {
        revision++;
        if (dirtyListener != null) dirtyListener.run();
    }

    void setDirtyListener(@Nullable Runnable listener) {
        this.dirtyListener = listener;
    }

    /** Deep copy (used for client sync snapshots). */
    public AgeData copy() {
        return new AgeData(uuid, name, seed, baseInstability, instabilityEnabled, visited, dead, worldTime,
                Optional.ofNullable(spawn), pages, symbols, authors, dataCompound);
    }

    /** Overwrites all state from another instance (client-side sync). */
    public void copyFrom(AgeData other) {
        this.name = other.name;
        this.seed = other.seed;
        this.baseInstability = other.baseInstability;
        this.instabilityEnabled = other.instabilityEnabled;
        this.visited = other.visited;
        this.dead = other.dead;
        this.worldTime = other.worldTime;
        this.spawn = other.spawn;
        this.pages = new ArrayList<>(other.pages);
        this.symbols = new ArrayList<>(other.symbols);
        this.authors = new ArrayList<>(other.authors);
        this.dataCompound = other.dataCompound.copy();
        revision++;
    }
}
