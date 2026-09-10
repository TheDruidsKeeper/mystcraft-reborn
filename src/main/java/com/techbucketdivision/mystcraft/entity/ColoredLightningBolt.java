package com.techbucketdivision.mystcraft.entity;

import com.techbucketdivision.mystcraft.network.LightningPayload;
import com.techbucketdivision.mystcraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * A lightning bolt with a colour (REQUIREMENTS §9). Default colour (0.45, 0.45, 0.5). The entity is tracked to
 * clients like vanilla lightning; the client renderer reads {@link #getColor()}. A {@link LightningPayload} is also
 * sent within 512 blocks for the client-side flash tint.
 */
public class ColoredLightningBolt extends LightningBolt {
    /** (0.45, 0.45, 0.5) as 0xRRGGBB. */
    public static final int DEFAULT_COLOR = 0x737380;
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ColoredLightningBolt.class, EntityDataSerializers.INT);

    public ColoredLightningBolt(EntityType<? extends LightningBolt> type, Level level) {
        super(type, level);
    }

    /** Strikes at the given position (server side). */
    public static @Nullable ColoredLightningBolt strike(ServerLevel level, BlockPos pos, int color) {
        ColoredLightningBolt bolt = ModEntities.LIGHTNING.get().create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return null;
        Vec3 at = Vec3.atBottomCenterOf(pos);
        bolt.snapTo(at);
        bolt.setColor(color);
        bolt.setVisualOnly(false);
        level.addFreshEntity(bolt);
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 512.0, new LightningPayload(at.x, at.y, at.z, color));
        return bolt;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COLOR, DEFAULT_COLOR);
    }

    /** 0xRRGGBB. */
    public int getColor() {
        return getEntityData().get(DATA_COLOR);
    }

    public void setColor(int rgb) {
        getEntityData().set(DATA_COLOR, rgb & 0xFFFFFF);
    }
}
