package com.minekings.minekings.politics;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.politics.client.MapRegionClientHandler;
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
        PayloadRegistrar registrar = event.registrar(MineKings.MODID).versioned("2");

        registrar.playToClient(
                PolityViewPayload.TYPE,
                PolityViewPayload.STREAM_CODEC,
                PolityViewClientHandler::handle
        );

        registrar.playToServer(
                RequestHubDataPayload.TYPE,
                RequestHubDataPayload.STREAM_CODEC,
                RequestHubDataHandler::handle
        );

        registrar.playToServer(
                RequestMapRegionPayload.TYPE,
                RequestMapRegionPayload.STREAM_CODEC,
                RequestMapRegionHandler::handle
        );

        registrar.playToClient(
                MapRegionPayload.TYPE,
                MapRegionPayload.STREAM_CODEC,
                MapRegionClientHandler::handle
        );
    }
}
