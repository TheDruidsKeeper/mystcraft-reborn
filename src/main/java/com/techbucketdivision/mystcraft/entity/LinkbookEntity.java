package com.techbucketdivision.mystcraft.entity;

import com.techbucketdivision.mystcraft.item.LinkingItem;
import com.techbucketdivision.mystcraft.item.component.BookHealth;
import com.techbucketdivision.mystcraft.menu.BookMenu;
import com.techbucketdivision.mystcraft.registry.ModDataComponents;
import com.techbucketdivision.mystcraft.registry.ModEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A dropped linking book (original spec §9, §7.9). Health mirrors the book item's {@code BookHealth}; fire damage is
 * doubled and ignites the entity; wall damage is ignored; every 10000 ticks it takes 1 starvation damage and 1 drown
 * damage per tick while wet. Right-click opens the book GUI, sneak + empty hand picks it up.
 */
public final class LinkbookEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> DATA_BOOK = SynchedEntityData.defineId(LinkbookEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<String> DATA_AGE_NAME = SynchedEntityData.defineId(LinkbookEntity.class, EntityDataSerializers.STRING);

    private int decayTimer;
    private int hurtTime;

    public LinkbookEntity(EntityType<? extends LinkbookEntity> type, Level level) {
        super(type, level);
    }

    // --- factories -----------------------------------------------------------------------------------------------

    /** Spawns a book entity at the dropper's eyes, slightly in front of it, inheriting its motion. */
    public static @Nullable LinkbookEntity spawnFor(ServerLevel level, Entity dropper, ItemStack book) {
        float yaw = dropper.getYRot();
        double x = dropper.getX() - Mth.cos(yaw / 180f * (float) Math.PI) * 0.16f;
        double y = dropper.getY() + dropper.getEyeHeight() - 0.1;
        double z = dropper.getZ() - Mth.sin(yaw / 180f * (float) Math.PI) * 0.16f;
        return spawnAt(level, new Vec3(x, y, z), dropper.getDeltaMovement(), yaw, book);
    }

    /** Spawns a book entity at an explicit position (used when replacing dropped item entities). */
    public static @Nullable LinkbookEntity spawnAt(ServerLevel level, Vec3 pos, Vec3 motion, float yaw, ItemStack book) {
        if (book.isEmpty()) return null;
        LinkbookEntity entity = ModEntities.LINKBOOK.get().create(level, EntitySpawnReason.TRIGGERED);
        if (entity == null) return null;
        entity.setBook(book.copy());
        entity.snapTo(pos.x, pos.y, pos.z, yaw, 0f);
        entity.setDeltaMovement(motion);
        entity.xo = pos.x;
        entity.yo = pos.y;
        entity.zo = pos.z;
        level.addFreshEntity(entity);
        return entity;
    }

    // --- data ----------------------------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BOOK, ItemStack.EMPTY);
        builder.define(DATA_AGE_NAME, "");
    }

    public ItemStack getBook() {
        return getEntityData().get(DATA_BOOK);
    }

    public void setBook(ItemStack stack) {
        getEntityData().set(DATA_BOOK, stack);
        getEntityData().set(DATA_AGE_NAME, stack.isEmpty() || !(stack.getItem() instanceof LinkingItem)
                ? "" : LinkingItem.getLinkInfo(stack).displayName());
    }

    public String getAgeName() {
        return getEntityData().get(DATA_AGE_NAME);
    }

    /** Ticks since last hurt (for the red tint of the renderer). */
    public int hurtTime() {
        return hurtTime;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        decayTimer = input.getIntOr("decay_timer", 0);
        ItemStack book = input.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        if (book.isEmpty()) {
            discard();
            return;
        }
        setBook(book);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("decay_timer", decayTimer);
        ItemStack book = getBook();
        if (!book.isEmpty()) output.store("item", ItemStack.CODEC, book);
    }

    // --- health ---------------------------------------------------------------------------------------------------

    private BookHealth bookHealth() {
        ItemStack book = getBook();
        BookHealth h = book.get(ModDataComponents.BOOK_HEALTH.get());
        return h == null ? BookHealth.FULL : h;
    }

    public float getHealth() {
        return bookHealth().health();
    }

    public float getMaxHealth() {
        return bookHealth().maxHealth();
    }

    public void setHealth(float health) {
        ItemStack book = getBook();
        if (book.isEmpty()) return;
        ItemStack copy = book.copy();
        copy.set(ModDataComponents.BOOK_HEALTH.get(), bookHealth().withHealth(health));
        getEntityData().set(DATA_BOOK, copy);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved()) return false;
        if (source.is(DamageTypes.IN_WALL)) return false;
        if (source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 2;
            igniteForSeconds(amount);
        }
        float health = getHealth() - amount;
        setHealth(health);
        hurtTime = 10;
        level.broadcastDamageEvent(this, source);
        if (health <= 0) {
            discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // --- interaction ----------------------------------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            player.setItemInHand(hand, getBook());
            player.getInventory().setChanged();
            discard();
            return InteractionResult.SUCCESS_SERVER;
        }
        BookMenu.openForEntity(serverPlayer, this);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Links an entity using this book (called by the book menu's "Link" message). */
    public void linkEntity(Entity entity) {
        ItemStack book = getBook();
        if (book.isEmpty() || !(book.getItem() instanceof LinkingItem linkingItem)) return;
        if (level() instanceof ServerLevel server) {
            linkingItem.activate(book, server, entity);
        }
    }

    // --- tick -----------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (hurtTime > 0) hurtTime--;
        xo = getX();
        yo = getY();
        zo = getZ();
        if (!isNoGravity()) setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
        move(MoverType.SELF, getDeltaMovement());
        float drag = onGround() ? 0.6f : 0.98f;
        setDeltaMovement(getDeltaMovement().multiply(drag, 0.98, drag));

        if (!(level() instanceof ServerLevel server)) return;
        if (getBook().isEmpty()) {
            discard();
            return;
        }
        decayTimer++;
        if (decayTimer % 10000 == 0) {
            hurtServer(server, damageSources().starve(), 1f);
        }
        if (isInWaterOrRain()) {
            hurtServer(server, damageSources().drown(), 1f);
        }
    }

    @Override
    public ItemStack getPickResult() {
        return getBook().copy();
    }
}
