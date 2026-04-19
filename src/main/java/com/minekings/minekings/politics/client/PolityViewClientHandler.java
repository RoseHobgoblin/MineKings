package com.minekings.minekings.politics.client;

import com.minekings.minekings.client.hub.HubScreen;
import com.minekings.minekings.politics.PolityViewPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for {@link PolityViewPayload}. If a {@link HubScreen}
 * is currently open, the payload feeds its Clan panel. Otherwise, opens
 * the legacy stand-alone {@link PolityScreen} (used by the right-click-
 * bound-villager flow).
 */
public final class PolityViewClientHandler {
    private PolityViewClientHandler() {}

    public static void handle(PolityViewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            HubScreen hub = HubScreen.current();
            if (hub != null) {
                hub.getClanPanel().receive(payload);
            } else {
                Minecraft.getInstance().setScreen(new PolityScreen(payload));
            }
        });
    }
}
