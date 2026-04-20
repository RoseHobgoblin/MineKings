package com.minekings.minekings.politics.client;

import com.minekings.minekings.client.hub.map.MapTileCache;
import com.minekings.minekings.politics.MapRegionPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-side handler for {@link MapRegionPayload}. */
public final class MapRegionClientHandler {
    private MapRegionClientHandler() {}

    public static void handle(MapRegionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MapTileCache.INSTANCE.apply(payload));
    }
}
