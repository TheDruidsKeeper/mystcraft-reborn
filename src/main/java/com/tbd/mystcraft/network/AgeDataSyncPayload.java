package com.tbd.mystcraft.network;

import com.tbd.mystcraft.age.AgeData;
import com.tbd.mystcraft.util.MystIds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Full AgeData snapshot sent to clients on login, dimension change and periodically while dirty. */
public record AgeDataSyncPayload(AgeData data) implements CustomPacketPayload {
    public static final Type<AgeDataSyncPayload> TYPE = new Type<>(MystIds.id("age_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AgeDataSyncPayload> STREAM_CODEC =
            AgeData.STREAM_CODEC.map(AgeDataSyncPayload::new, AgeDataSyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
