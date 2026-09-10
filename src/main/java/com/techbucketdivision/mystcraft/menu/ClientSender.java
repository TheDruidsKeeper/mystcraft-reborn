package com.techbucketdivision.mystcraft.menu;

import com.techbucketdivision.mystcraft.network.MenuMessagePayload;

/**
 * Client → server transport hook. Implemented by {@code client.ClientNetwork} and installed with
 * {@link AbstractMystcraftMenu#setClientSender(ClientSender)} so common code never touches client classes.
 */
@FunctionalInterface
public interface ClientSender {
    void send(MenuMessagePayload payload);
}
