package com.minekings.minekings.politics.client;

import com.minekings.minekings.politics.PolityViewPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for the {@link PolityViewPayload} packet. Opens
 * the {@link PolityScreen} with the received data on the main client thread.
 */
public final class PolityViewClientHandler {
    private PolityViewClientHandler() {}

    public static void handle(PolityViewPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                Minecraft.getInstance().setScreen(new PolityScreen(payload))
        );
    }
}
