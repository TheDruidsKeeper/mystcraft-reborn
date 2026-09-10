package com.techbucketdivision.mystcraft.network;

import com.techbucketdivision.mystcraft.menu.MenuMessageHandler;
import com.techbucketdivision.mystcraft.util.MystIds;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Generic GUI message (mirrors the original {@code MPacketGuiMessage}): routed to the player's open menu if it
 * implements {@link MenuMessageHandler} and the container id matches.
 */
public record MenuMessagePayload(int containerId, CompoundTag data) implements CustomPacketPayload {
    public static final Type<MenuMessagePayload> TYPE = new Type<>(MystIds.id("menu_message"));
    public static final StreamCodec<ByteBuf, MenuMessagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MenuMessagePayload::containerId,
            ByteBufCodecs.COMPOUND_TAG, MenuMessagePayload::data,
            MenuMessagePayload::new);

    /** Key used for the message name inside {@link #data}. */
    public static final String KEY_MESSAGE = "msg";

    public static MenuMessagePayload of(int containerId, String message, CompoundTag payload) {
        CompoundTag tag = payload.copy();
        tag.putString(KEY_MESSAGE, message);
        return new MenuMessagePayload(containerId, tag);
    }

    public static MenuMessagePayload of(int containerId, String message) {
        return of(containerId, message, new CompoundTag());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(MenuMessagePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> dispatch(ctx.player(), payload));
    }

    /** Shared dispatch (also used by the client handler). */
    public static void dispatch(Player player, MenuMessagePayload payload) {
        if (player.containerMenu != null && player.containerMenu.containerId == payload.containerId()
                && player.containerMenu instanceof MenuMessageHandler handler) {
            handler.processMessage(player, payload.data());
        }
    }
}
