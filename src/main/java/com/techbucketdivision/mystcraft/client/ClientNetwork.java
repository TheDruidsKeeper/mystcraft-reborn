package com.tbd.mystcraft.client;

import com.tbd.mystcraft.menu.ClientSender;
import com.tbd.mystcraft.network.MenuMessagePayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client → server transport; installed into {@code AbstractMystcraftMenu} as the {@link ClientSender}. */
public final class ClientNetwork implements ClientSender {
    public static final ClientNetwork INSTANCE = new ClientNetwork();

    private ClientNetwork() {}

    @Override
    public void send(MenuMessagePayload payload) {
        sendToServer(payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
