package com.techbucketdivision.mystcraft.blockentity;

import com.techbucketdivision.mystcraft.api.symbol.AgeSymbol;
import com.techbucketdivision.mystcraft.block.SequenceDialBlock;
import com.techbucketdivision.mystcraft.config.MystcraftConfig;
import com.techbucketdivision.mystcraft.facility.FacilityState;
import com.techbucketdivision.mystcraft.item.PageItem;
import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import com.techbucketdivision.mystcraft.symbol.SymbolRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * One Facility lock (FACILITY_PLAN.md §2.2), shared by the Symbol Altar, the Offering Pedestal and the Sequence Dial.
 * Solving it records the lock in {@link FacilityState} and removes every Warded Door of the same piece.
 *
 * <ul>
 * <li>{@link Kind#SYMBOL}: wants one page of each symbol in {@code required} (the Age's own symbols, which arriving
 *     players have learned and can write, or find in facility chests).</li>
 * <li>{@link Kind#OFFERING}: wants one {@code wanted} item (a Trial Key for {@code lock:trial}).</li>
 * <li>{@link Kind#SEQUENCE}: one dial of a bank; the bank is solved when every dial shows its glyph of the Age's
 *     code ({@link FacilityState#code}). Clue blocks elsewhere in the facility show the glyphs.</li>
 * </ul>
 */
public class FacilityLockBlockEntity extends MystBlockEntity {
    public enum Kind { SYMBOL, OFFERING, SEQUENCE }

    private Kind kind = Kind.OFFERING;
    private long lockId;
    private BoundingBox pieceBox = new BoundingBox(BlockPos.ZERO);
    private final List<Identifier> required = new ArrayList<>();
    private final List<Identifier> provided = new ArrayList<>();
    private Identifier wanted = Identifier.withDefaultNamespace("trial_key");
    private final List<BlockPos> dials = new ArrayList<>();
    private int index;

    public FacilityLockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FACILITY_LOCK.get(), pos, state);
    }

    // --- configuration (placement) ------------------------------------------------------------------------------

    public void configure(Kind kind, long lockId, BoundingBox pieceBox) {
        this.kind = kind;
        this.lockId = lockId;
        this.pieceBox = pieceBox;
        setChanged();
    }

    public void setRequiredSymbols(List<Identifier> symbols) {
        required.clear();
        required.addAll(symbols);
        provided.clear();
        setChanged();
    }

    public void setWanted(Identifier item) {
        wanted = item;
        setChanged();
    }

    public void setDials(List<BlockPos> positions, int index) {
        dials.clear();
        dials.addAll(positions);
        this.index = index;
        setChanged();
    }

    public Kind kind() { return kind; }
    public long lockId() { return lockId; }
    public BoundingBox pieceBox() { return pieceBox; }
    public List<Identifier> required() { return List.copyOf(required); }
    public List<Identifier> provided() { return List.copyOf(provided); }
    public Identifier wanted() { return wanted; }
    public List<BlockPos> dials() { return List.copyOf(dials); }
    public int index() { return index; }

    public boolean isSolved() {
        return getLevel() instanceof ServerLevel level && FacilityState.isLockSolved(level, lockId);
    }

    // --- interaction --------------------------------------------------------------------------------------------

    /** Server-side use of the lock block. */
    public InteractionResult interact(ServerPlayer player, InteractionHand hand) {
        if (!(getLevel() instanceof ServerLevel level)) return InteractionResult.PASS;
        if (FacilityState.isLockSolved(level, lockId)) {
            player.sendSystemMessage(Component.translatable("message.mystcraft.facility.lock.solved"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getItemInHand(hand);
        return switch (kind) {
            case SYMBOL -> offerPage(level, player, held);
            case OFFERING -> offerItem(level, player, held);
            case SEQUENCE -> turnDial(level, player);
        };
    }

    private InteractionResult offerPage(ServerLevel level, ServerPlayer player, ItemStack held) {
        Identifier symbol = PageItem.getSymbolId(held);
        if (symbol != null && required.contains(symbol) && !provided.contains(symbol)) {
            if (!player.hasInfiniteMaterials()) held.shrink(1);
            provided.add(symbol);
            markForUpdate();
            level.playSound(null, getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
            if (provided.size() >= required.size()) solve(level);
            else player.sendSystemMessage(Component.translatable("message.mystcraft.facility.lock.accepted", missingNames()), true);
            return InteractionResult.CONSUME;
        }
        player.sendSystemMessage(Component.translatable("message.mystcraft.facility.lock.wants_pages", missingNames()), true);
        return InteractionResult.CONSUME;
    }

    private InteractionResult offerItem(ServerLevel level, ServerPlayer player, ItemStack held) {
        Item item = BuiltInRegistries.ITEM.getValue(wanted);
        if (!held.isEmpty() && held.is(item)) {
            if (!player.hasInfiniteMaterials()) held.shrink(1);
            solve(level);
            return InteractionResult.CONSUME;
        }
        player.sendSystemMessage(Component.translatable("message.mystcraft.facility.lock.wants_item", item.getName(new ItemStack(item))), true);
        return InteractionResult.CONSUME;
    }

    private InteractionResult turnDial(ServerLevel level, ServerPlayer player) {
        BlockState state = getBlockState();
        if (!state.hasProperty(SequenceDialBlock.GLYPH)) return InteractionResult.PASS;
        int next = (state.getValue(SequenceDialBlock.GLYPH) + 1) % FacilityState.glyphCount();
        level.setBlockAndUpdate(getBlockPos(), state.setValue(SequenceDialBlock.GLYPH, next));
        level.playSound(null, getBlockPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.9f);
        if (sequenceMatches(level)) solve(level);
        return InteractionResult.CONSUME;
    }

    /** Whether every dial of this bank shows its glyph of the Age code. */
    public boolean sequenceMatches(ServerLevel level) {
        if (dials.isEmpty()) return false;
        int[] code = FacilityState.code(level, dials.size());
        for (int i = 0; i < dials.size(); i++) {
            BlockState s = level.getBlockState(dials.get(i));
            if (!s.hasProperty(SequenceDialBlock.GLYPH) || s.getValue(SequenceDialBlock.GLYPH) != code[i]) return false;
        }
        return true;
    }

    private MutableComponent missingNames() {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Identifier id : required) {
            if (provided.contains(id)) continue;
            AgeSymbol symbol = SymbolRegistry.get(id);
            if (!first) out.append(", ");
            out.append(symbol != null ? symbol.displayName() : Component.literal(id.toString()));
            first = false;
        }
        return out;
    }

    /** Records the lock as solved and opens the piece's doors. */
    public void solve(ServerLevel level) {
        FacilityState.solveLock(level, lockId);
        level.playSound(null, getBlockPos(), SoundEvents.VAULT_ACTIVATE, SoundSource.BLOCKS, 1f, 1f);
        openDoors(level, pieceBox, lockId);
        markForUpdate();
    }

    /** Removes every Warded Door with {@code lockId} inside {@code box} (loaded chunks; the rest open on tick). */
    public static void openDoors(ServerLevel level, BoundingBox box, long lockId) {
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (!level.hasChunkAt(pos)) continue;
            if (level.getBlockEntity(pos) instanceof WardedDoorBlockEntity door && door.lockId() == lockId) door.open();
        }
    }

    /** How many pages a symbol altar asks for (config). */
    public static int symbolPageCount() {
        return MystcraftConfig.FACILITY_SYMBOL_PAGES.get();
    }

    // --- persistence --------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("Kind", kind.name());
        output.putLong("LockId", lockId);
        output.putIntArray("Piece", new int[] {pieceBox.minX(), pieceBox.minY(), pieceBox.minZ(), pieceBox.maxX(), pieceBox.maxY(), pieceBox.maxZ()});
        output.store("Required", Identifier.CODEC.listOf(), required);
        output.store("Provided", Identifier.CODEC.listOf(), provided);
        output.store("Wanted", Identifier.CODEC, wanted);
        output.store("Dials", BlockPos.CODEC.listOf(), dials);
        output.putInt("Index", index);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            kind = Kind.valueOf(input.getStringOr("Kind", Kind.OFFERING.name()));
        } catch (IllegalArgumentException e) {
            kind = Kind.OFFERING;
        }
        lockId = input.getLongOr("LockId", 0L);
        int[] p = input.getIntArray("Piece").orElse(null);
        pieceBox = p != null && p.length == 6 ? new BoundingBox(p[0], p[1], p[2], p[3], p[4], p[5]) : new BoundingBox(getBlockPos());
        required.clear();
        required.addAll(input.read("Required", Identifier.CODEC.listOf()).orElse(List.of()));
        provided.clear();
        provided.addAll(input.read("Provided", Identifier.CODEC.listOf()).orElse(List.of()));
        wanted = input.read("Wanted", Identifier.CODEC).orElse(Identifier.withDefaultNamespace("trial_key"));
        dials.clear();
        dials.addAll(input.read("Dials", BlockPos.CODEC.listOf()).orElse(List.of()));
        index = input.getIntOr("Index", 0);
    }
}
