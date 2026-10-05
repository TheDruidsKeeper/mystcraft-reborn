package com.techbucketdivision.mystcraft.entity;

import com.techbucketdivision.mystcraft.entity.explosion.AdvancedExplosion;
import com.techbucketdivision.mystcraft.registry.ModEntities;
import com.techbucketdivision.mystcraft.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A meteor (original spec §9): burns, roars, ray-traces along its motion; on impact it breaks the blocks in its box
 * (+5 up), and once {@code inGroundTime >= penetration} performs eight {@link AdvancedExplosion}s and dies. Not saved.
 */
public final class MeteorEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);

    private int inGroundTime;
    private int penetration;
    private boolean announced;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
    }

    /** Spawns a meteor at y=500 over (x, z) with a random slight drift (original spec §4.3.10). */
    public static @Nullable MeteorEntity spawn(ServerLevel level, double x, double z, float scale, int penetration) {
        double dx = level.getRandom().nextGaussian() * 0.25;
        double dy = level.getRandom().nextFloat() * -2 - 2;
        double dz = level.getRandom().nextGaussian() * 0.25;
        return spawn(level, new Vec3(x, 500, z), new Vec3(dx, dy, dz), scale, penetration);
    }

    public static @Nullable MeteorEntity spawn(ServerLevel level, Vec3 pos, Vec3 motion, float scale, int penetration) {
        MeteorEntity meteor = ModEntities.METEOR.get().create(level, EntitySpawnReason.TRIGGERED);
        if (meteor == null) return null;
        meteor.setScale(scale, penetration);
        meteor.snapTo(pos.x, pos.y, pos.z, 0f, 0f);
        meteor.setDeltaMovement(motion);
        level.addFreshEntity(meteor);
        return meteor;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SCALE, 1.0f);
    }

    public void setScale(float scale, int penetration) {
        getEntityData().set(DATA_SCALE, scale);
        this.penetration = penetration;
        refreshDimensions();
    }

    public float getScale() {
        return getEntityData().get(DATA_SCALE);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        // Meteors are never persisted (EntityType#noSave); anything loaded is discarded.
        discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("in_ground", inGroundTime);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float scale = Math.max(0.1f, getScale());
        return EntityDimensions.scalable(scale, scale);
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        // A meteor cannot be damaged or destroyed; it only ends on impact.
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        igniteForTicks(1);
        if (!announced) {
            announced = true;
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                    10000.0f, 0.8f + getRandom().nextFloat() * 0.2f);
            level().playSound(null, this, ModSounds.METEOR_ROAR.value(), SoundSource.WEATHER, 4.0f, 1.0f);
        }

        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            inGroundTime++;
            onImpact();
        } else {
            inGroundTime = Math.max(0, inGroundTime - 1);
        }
        if (isRemoved()) return;

        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        float horizontal = (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        setYRot((float) (Mth.atan2(motion.x, motion.z) * 180.0 / Math.PI));
        setXRot((float) (Mth.atan2(motion.y, horizontal) * 180.0 / Math.PI));

        if (level().isClientSide()) {
            if (isInWater()) {
                for (int i = 0; i < 4; i++) {
                    level().addParticle(ParticleTypes.BUBBLE, getX() - motion.x * 0.25, getY() - motion.y * 0.25,
                            getZ() - motion.z * 0.25, motion.x, motion.y, motion.z);
                }
            }
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 0, 0, 0);
        }
    }

    private void onImpact() {
        if (!(level() instanceof ServerLevel server)) return;
        setDeltaMovement(getDeltaMovement().multiply(1, 0.9, 1));
        breakBlocksInBox(server, getBoundingBox());
        if (inGroundTime >= penetration) {
            float scale = getScale();
            double x = getX(), y = getY(), z = getZ();
            explode(server, x, y, z, 5.0f, false, true);
            explode(server, x, y - scale / 10, z, scale, false, true);
            explode(server, x, y - scale / 5, z, scale * 2, false, true);
            explode(server, x, y - scale * 2 / 5, z, scale, false, true);
            explode(server, x + scale * 4 / 5, y, z, scale, true, false);
            explode(server, x - scale * 4 / 5, y, z, scale, true, false);
            explode(server, x, y, z + scale * 4 / 5, scale, true, false);
            explode(server, x, y, z - scale * 4 / 5, scale, true, false);
            discard();
        }
    }

    private void explode(ServerLevel level, double x, double y, double z, float power, boolean flaming, boolean ores) {
        AdvancedExplosion explosion = new AdvancedExplosion(level, this, x, y, z, power)
                .addEffect(AdvancedExplosion.BASIC)
                .addEffect(AdvancedExplosion.BREAK_NO_DROP);
        if (flaming) explosion.addEffect(AdvancedExplosion.FIRE);
        if (ores) explosion.addEffect(AdvancedExplosion.PLACE_ORES);
        explosion.explode();
    }

    private void breakBlocksInBox(ServerLevel level, AABB box) {
        Vec3 motion = getDeltaMovement();
        int minX = Mth.floor(box.minX + motion.x);
        int minY = Mth.floor(box.minY + motion.y);
        int minZ = Mth.floor(box.minZ + motion.z);
        int maxX = Mth.floor(box.maxX + motion.x);
        int maxY = Mth.floor(box.maxY + 5 + motion.y);
        int maxZ = Mth.floor(box.maxZ + motion.z);
        boolean broke = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (level.isOutsideBuildHeight(pos)) continue;
                    if (!level.getBlockState(pos).isAir()) {
                        broke = true;
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        if (broke) {
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        }
    }
}
