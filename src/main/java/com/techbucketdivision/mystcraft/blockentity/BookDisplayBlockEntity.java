package com.techbucketdivision.mystcraft.blockentity;

import com.techbucketdivision.mystcraft.api.linking.LinkInfo;
import com.techbucketdivision.mystcraft.registry.ModBlockEntities;
import com.techbucketdivision.mystcraft.registry.ModBlocks;
import com.techbucketdivision.mystcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * One-slot book holder used by the Bookstand and Lectern (REQUIREMENTS §3.4) and as base for the Link Modifier. Keeps
 * a yaw/pitch for rendering; yaw is quantised per block (45° stands, 90° lecterns / modifiers).
 */
public class BookDisplayBlockEntity extends MystBlockEntity {
    public final FilteredItemHandler inventory = new FilteredItemHandler(1,
            (slot, res) -> accepts(res.toStack()), slot -> 1, this::onBookChanged);

    private short yaw;
    private short pitch;

    public BookDisplayBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.BOOK_DISPLAY.get(), pos, state);
    }

    protected BookDisplayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Filter for the book slot. Lecterns also accept pages and filled maps. */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (BookUtil.isLinkingItem(stack)) return true;
        if (getBlockState().is(ModBlocks.LECTERN.get())) {
            return stack.is(ModItems.PAGE.get()) || stack.is(Items.FILLED_MAP);
        }
        return false;
    }

    protected int yawQuantum() {
        return getBlockState().is(ModBlocks.BOOKSTAND.get()) ? 45 : 90;
    }

    protected void onBookChanged() {
        markForUpdate();
    }

    // --- book ---------------------------------------------------------------------------------------------------------

    public ItemStack getBook() {
        return inventory.getStack(0);
    }

    /** Alias for renderers ({@code getDisplayItem} in the original). */
    public ItemStack getDisplayItem() {
        return getBook();
    }

    /** Replaces the held book; a previously held stack is dropped in the world (server). */
    public void setBook(ItemStack stack) {
        ItemStack previous = getBook();
        Level level = getLevel();
        if (!stack.isEmpty() && !previous.isEmpty() && level != null) {
            BookUtil.drop(level, getBlockPos(), previous);
        }
        inventory.set(0, ItemResource.of(stack), stack.isEmpty() ? 0 : stack.getCount());
    }

    /**
     * Writes back a modified copy of the held book (e.g. a Descriptive Book that just bound to its Age). The resource
     * based inventory hands out copies from {@link #getBook()}, so mutations must be stored explicitly; unlike
     * {@link #setBook} nothing is dropped.
     */
    public void updateBook(ItemStack stack) {
        inventory.set(0, ItemResource.of(stack), stack.isEmpty() ? 0 : stack.getCount());
    }

    public @Nullable String getBookTitle() {
        ItemStack book = getBook();
        LinkInfo info = BookUtil.linkInfo(book);
        return info == null ? null : info.displayName();
    }

    /** Activates the held book for the entity (server only). */
    public void link(Entity entity) {
        Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        ItemStack book = getBook();
        if (book.isEmpty() || !BookUtil.isLinkingItem(book)) return;
        BookUtil.activate(book, serverLevel, entity);
        // a Descriptive Book binds to its Age (and gets its discovered pages) on the first link: keep that copy
        if (!ItemStack.matches(book, getBook())) {
            updateBook(book);
            setChanged();
            serverLevel.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    // --- rotation ------------------------------------------------------------------------------------------------------

    public short getYaw() {
        return yaw;
    }

    public void setYaw(int yaw) {
        int q = yawQuantum();
        yaw = ((yaw % 360) + 360) % 360;
        this.yaw = (short) (yaw - (yaw % q));
        markForUpdate();
    }

    public short getPitch() {
        return pitch;
    }

    public void setPitch(int pitch) {
        this.pitch = (short) (pitch % 360);
        markForUpdate();
    }

    // --- persistence -----------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putChild("inventory", inventory);
        output.putShort("Yaw", yaw);
        output.putShort("Pitch", pitch);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.readChild("inventory", inventory);
        yaw = (short) input.getShortOr("Yaw", (short) 0);
        pitch = (short) input.getShortOr("Pitch", (short) 0);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        dropContents(inventory);
        super.preRemoveSideEffects(pos, state);
    }
}
