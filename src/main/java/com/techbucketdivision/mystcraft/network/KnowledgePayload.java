package com.techbucketdivision.mystcraft.network;

import com.techbucketdivision.mystcraft.util.MystIds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Server → client: the full set of symbols the player knows how to write. */
public record KnowledgePayload(List<Identifier> symbols) implements CustomPacketPayload {
    public static final Type<KnowledgePayload> TYPE = new Type<>(MystIds.id("knowledge"));
    public static final StreamCodec<ByteBuf, KnowledgePayload> STREAM_CODEC =
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()).map(KnowledgePayload::new, KnowledgePayload::symbols);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
