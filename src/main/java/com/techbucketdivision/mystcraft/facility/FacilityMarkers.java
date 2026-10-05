package com.techbucketdivision.mystcraft.facility;

import com.techbucketdivision.mystcraft.Mystcraft;
import com.techbucketdivision.mystcraft.age.AgeData;
import com.techbucketdivision.mystcraft.age.AgeManager;
import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.api.symbol.SymbolCategory;
import com.techbucketdivision.mystcraft.block.SequenceDialBlock;
import com.techbucketdivision.mystcraft.blockentity.FacilityCacheBlockEntity;
import com.techbucketdivision.mystcraft.blockentity.FacilityLockBlockEntity;
import com.techbucketdivision.mystcraft.blockentity.WardedDoorBlockEntity;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the DATA markers of a Facility piece (docs/plans/FACILITY_PLAN.md §2.2; docs/STRUCTURES.md lists the
 * grammar). Markers are vanilla structure blocks in DATA mode; the metadata string is parsed here:
 *
 * <pre>
 * door                       Warded Door bound to this piece's lock
 * lock:symbol[:N]            Symbol Altar wanting N pages of the Age's own symbols (default config)
 * lock:sequence              one Sequence Dial of this piece's bank (order = template order)
 * lock:offering[:item_id]    Offering Pedestal wanting an item (default: random from config)
 * lock:trial                 Offering Pedestal wanting a Trial Key
 * trial_spawner[:kind]       vanilla Trial Spawner using a vanilla trial-chamber config (melee/ranged/small_melee/breeze)
 * vault[:loot_table]         vanilla Vault keyed by Trial Keys (default mystcraft:chests/facility_vault)
 * loot:loot_table            chest with that loot table
 * reward:linkbook            Facility Cache with the Linking Book home (per player; marks the facility solved)
 * reward:loot_table          Facility Cache rolling that table once per player
 * clue:N                     glyph block showing digit N of the Age's sequence code
 * spawn                      reserved (ignored)
 * </pre>
 *
 * Every marker of the piece is passed in so banks and doors can be wired, but only markers inside {@code chunkBB}
 * are placed (pieces are placed chunk by chunk). A {@code door} whose outward neighbour lies in an earlier piece of
 * the same start is the doorway the player arrives through and is left open (rooms are sealed behind the player,
 * never in front of the way in); see {@code scripts/structures/markers.py} for how doorways get their markers.
 */
public final class FacilityMarkers {
    private FacilityMarkers() {}

    public static final Identifier VAULT_LOOT = Identifier.fromNamespaceAndPath(Mystcraft.MOD_ID, "chests/facility_vault");
    public static final Identifier TRIAL_KEY = Identifier.withDefaultNamespace("trial_key");

    /** The eight glyph faces, in {@link SequenceDialBlock#GLYPH} order; clue markers place the same blocks. */
    public static final List<Block> GLYPH_BLOCKS = List.of(Blocks.WHITE_GLAZED_TERRACOTTA, Blocks.ORANGE_GLAZED_TERRACOTTA,
            Blocks.MAGENTA_GLAZED_TERRACOTTA, Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, Blocks.YELLOW_GLAZED_TERRACOTTA,
            Blocks.LIME_GLAZED_TERRACOTTA, Blocks.PINK_GLAZED_TERRACOTTA, Blocks.GRAY_GLAZED_TERRACOTTA);

    private static final List<String> TRIAL_KINDS = List.of("melee/zombie", "melee/husk", "melee/spider", "ranged/skeleton",
            "ranged/stray", "small_melee/baby_zombie", "small_melee/cave_spider", "small_melee/slime", "breeze");

    /** A marker: absolute position and its metadata string. */
    public record Marker(BlockPos pos, String data) {
        public static Marker of(StructureTemplate.StructureBlockInfo info) {
            String data = info.nbt() == null ? "" : info.nbt().getStringOr("metadata", "");
            return new Marker(info.pos(), data.trim().toLowerCase(Locale.ROOT));
        }

        /** The word before the first colon. */
        String head() {
            int i = data.indexOf(':');
            return i < 0 ? data : data.substring(0, i);
        }

        /** Everything after the first colon (may itself be a namespaced id), or {@code ""}. */
        String rest() {
            int i = data.indexOf(':');
            return i < 0 ? "" : data.substring(i + 1);
        }

        /** Splits {@code s} at its first colon into a word and a remainder. */
        static String[] split(String s) {
            int i = s.indexOf(':');
            return i < 0 ? new String[] {s, ""} : new String[] {s.substring(0, i), s.substring(i + 1)};
        }
    }

    /**
     * Places the markers of one piece.
     *
     * @param level     the world (a {@code WorldGenRegion} during generation)
     * @param markers   every DATA marker of the piece, in template order
     * @param pieceId   the piece's lock id (its placement origin)
     * @param pieceBox  the piece's bounding box
     * @param earlier   bounding boxes of the pieces generated before this one (doorways into them stay open)
     * @param chunkBB   the chunk being generated; markers outside it are skipped
     * @param random    the piece's random
     */
    public static void place(ServerLevelAccessor level, List<Marker> markers, long pieceId, BoundingBox pieceBox, List<BoundingBox> earlier,
                             BoundingBox chunkBB, RandomSource random) {
        ServerLevel serverLevel = level.getLevel();
        List<BlockPos> dials = new ArrayList<>();
        for (Marker m : markers) if (m.data().equals("lock:sequence")) dials.add(m.pos());
        int dialIndex = 0;
        for (Marker m : markers) {
            int myDial = m.data().equals("lock:sequence") ? dialIndex++ : -1;
            if (!chunkBB.isInside(m.pos())) continue;
            if (m.head().equals("door") && isEntry(m.pos(), pieceBox, earlier)) {
                set(level, m.pos(), Blocks.AIR.defaultBlockState());
                continue;
            }
            try {
                resolve(level, serverLevel, m, pieceId, pieceBox, random, dials, myDial);
            } catch (RuntimeException e) {
                Mystcraft.LOGGER.warn("Facility marker '{}' at {} failed: {}", m.data(), m.pos(), e.toString());
                level.setBlock(m.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Whether a doorway cell opens onto a piece generated earlier (the way in). */
    public static boolean isEntry(BlockPos pos, BoundingBox pieceBox, List<BoundingBox> earlier) {
        if (earlier.isEmpty()) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos outside = pos.relative(d);
            if (pieceBox.isInside(outside)) continue;
            for (BoundingBox box : earlier) if (box.isInside(outside)) return true;
        }
        return false;
    }

    private static void resolve(ServerLevelAccessor level, ServerLevel serverLevel, Marker m, long pieceId, BoundingBox pieceBox,
                                RandomSource random, List<BlockPos> dials, int dialIndex) {
        String rest = m.rest();
        switch (m.head()) {
            case "door" -> {
                set(level, m.pos(), ModBlocks.WARDED_DOOR.get().defaultBlockState());
                if (level.getBlockEntity(m.pos()) instanceof WardedDoorBlockEntity door) door.setLockId(pieceId);
            }
            case "lock" -> placeLock(level, serverLevel, m, rest, pieceId, pieceBox, random, dials, dialIndex);
            case "trial_spawner" -> {
                String kind = rest.isEmpty() ? TRIAL_KINDS.get(random.nextInt(TRIAL_KINDS.size())) : rest;
                CompoundTag tag = new CompoundTag();
                tag.putString("normal_config", "minecraft:trial_chamber/" + kind + "/normal");
                tag.putString("ominous_config", "minecraft:trial_chamber/" + kind + "/ominous");
                set(level, m.pos(), Blocks.TRIAL_SPAWNER.defaultBlockState());
                load(level, m.pos(), tag);
            }
            case "vault" -> {
                CompoundTag config = new CompoundTag();
                config.putString("loot_table", rest.isEmpty() ? VAULT_LOOT.toString() : rest);
                CompoundTag key = new CompoundTag();
                key.putString("id", TRIAL_KEY.toString());
                key.putInt("count", 1);
                config.put("key_item", key);
                CompoundTag tag = new CompoundTag();
                tag.put("config", config);
                set(level, m.pos(), Blocks.VAULT.defaultBlockState());
                load(level, m.pos(), tag);
            }
            case "loot" -> {
                if (rest.isEmpty()) throw new IllegalArgumentException("loot marker without a table");
                Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                set(level, m.pos(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
                if (level.getBlockEntity(m.pos()) instanceof RandomizableContainer chest) {
                    chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(rest)), random.nextLong());
                }
            }
            case "reward" -> {
                set(level, m.pos(), ModBlocks.FACILITY_CACHE.get().defaultBlockState());
                if (level.getBlockEntity(m.pos()) instanceof FacilityCacheBlockEntity cache) {
                    cache.setLootTable(rest.isEmpty() || rest.equals("linkbook") ? null : Identifier.parse(rest));
                }
            }
            case "clue" -> {
                int digit = rest.isEmpty() ? 0 : Integer.parseInt(rest);
                int[] code = FacilityState.code(serverLevel, Math.max(digit + 1, MystcraftConfig.FACILITY_SEQUENCE_LENGTH.get()));
                Block block = GLYPH_BLOCKS.get(code[digit]);
                BlockState state = block.defaultBlockState();
                if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random));
                }
                set(level, m.pos(), state);
            }
            case "spawn", "" -> set(level, m.pos(), Blocks.AIR.defaultBlockState());
            default -> throw new IllegalArgumentException("unknown marker");
        }
    }

    private static void placeLock(ServerLevelAccessor level, ServerLevel serverLevel, Marker m, String spec, long pieceId,
                                  BoundingBox pieceBox, RandomSource random, List<BlockPos> dials, int dialIndex) {
        String[] parts = Marker.split(spec);
        String type = parts[0].isEmpty() ? "offering" : parts[0];
        String arg = parts[1];
        switch (type) {
            case "symbol" -> {
                int count = arg.isEmpty() ? FacilityLockBlockEntity.symbolPageCount() : Integer.parseInt(arg);
                set(level, m.pos(), ModBlocks.SYMBOL_ALTAR.get().defaultBlockState());
                if (level.getBlockEntity(m.pos()) instanceof FacilityLockBlockEntity lock) {
                    lock.configure(FacilityLockBlockEntity.Kind.SYMBOL, pieceId, pieceBox);
                    lock.setRequiredSymbols(pickSymbols(serverLevel, random, count));
                }
            }
            case "sequence" -> {
                set(level, m.pos(), ModBlocks.SEQUENCE_DIAL.get().defaultBlockState()
                        .setValue(SequenceDialBlock.GLYPH, random.nextInt(FacilityState.glyphCount())));
                if (level.getBlockEntity(m.pos()) instanceof FacilityLockBlockEntity lock) {
                    lock.configure(FacilityLockBlockEntity.Kind.SEQUENCE, pieceId, pieceBox);
                    lock.setDials(dials, Math.max(dialIndex, 0));
                }
            }
            case "offering", "trial" -> {
                Identifier item = type.equals("trial") ? TRIAL_KEY : arg.isEmpty() ? randomOffering(random) : Identifier.parse(arg);
                set(level, m.pos(), ModBlocks.OFFERING_PEDESTAL.get().defaultBlockState());
                if (level.getBlockEntity(m.pos()) instanceof FacilityLockBlockEntity lock) {
                    lock.configure(FacilityLockBlockEntity.Kind.OFFERING, pieceId, pieceBox);
                    lock.setWanted(item);
                }
            }
            default -> throw new IllegalArgumentException("unknown lock type " + type);
        }
    }

    /** {@code count} distinct primary symbols of the Age, which arriving players learn (terrain symbols outside Ages). */
    public static List<Identifier> pickSymbols(ServerLevel level, RandomSource random, int count) {
        AgeData age = AgeManager.get(level.getServer(), level.dimension());
        List<Identifier> pool = new ArrayList<>();
        if (age != null) {
            for (Identifier id : age.symbols()) {
                AgeSymbol s = SymbolRegistry.get(id);
                if (s != null && s.hasCategory() && !s.category().isModifier() && s.category() != SymbolCategory.BIOMES && !pool.contains(id)) pool.add(id);
            }
        }
        if (pool.isEmpty()) {
            for (AgeSymbol s : SymbolRegistry.inCategory(SymbolCategory.TERRAIN)) pool.add(s.id());
        }
        List<Identifier> out = new ArrayList<>();
        while (out.size() < count && !pool.isEmpty()) out.add(pool.remove(random.nextInt(pool.size())));
        return out;
    }

    private static Identifier randomOffering(RandomSource random) {
        List<? extends String> offerings = MystcraftConfig.FACILITY_OFFERINGS.get();
        if (offerings.isEmpty()) return Identifier.fromNamespaceAndPath(Mystcraft.MOD_ID, "crystal");
        return Identifier.parse(offerings.get(random.nextInt(offerings.size())));
    }

    private static void set(ServerLevelAccessor level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void load(ServerLevelAccessor level, BlockPos pos, CompoundTag tag) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
    }

    /** Debug/QA: resolves the marker grammar for a loot table key check. */
    public static ResourceKey<LootTable> vaultLoot() {
        return ResourceKey.create(Registries.LOOT_TABLE, VAULT_LOOT);
    }
}
