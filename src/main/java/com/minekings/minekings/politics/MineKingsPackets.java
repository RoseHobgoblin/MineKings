package com.minekings.minekings.politics;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.politics.client.PolityViewClientHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all MineKings network payloads. Called once from the mod
 * constructor via {@code modEventBus.addListener(MineKingsPackets::register)}.
 */
public final class MineKingsPackets {
    private MineKingsPackets() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MineKings.MODID).versioned("1");

        registrar.playToClient(
                PolityViewPayload.TYPE,
                PolityViewPayload.STREAM_CODEC,
                PolityViewClientHandler::handle
        );
    }
}
