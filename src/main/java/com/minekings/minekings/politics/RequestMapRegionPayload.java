package com.minekings.minekings.politics;

import com.minekings.minekings.MineKings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client→server: "send me the map data for this chunk rectangle." Used by
 * the hub map's tile-streaming pipeline so the client can request
 * arbitrary regions as the user pans/zooms, instead of only a fixed
 * window around the player. Server clamps oversized regions to bound cost.
 *
 * <p>Coordinates are inclusive on both ends. {@code chunkMaxX >= chunkMinX}
 * and {@code chunkMaxZ >= chunkMinZ}.
 */
public record RequestMapRegionPayload(int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ) implements CustomPacketPayload {
    public static final Type<RequestMapRegionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineKings.MODID, "request_map_region"));

    public static final StreamCodec<FriendlyByteBuf, RequestMapRegionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.chunkMinX);
                        buf.writeVarInt(p.chunkMinZ);
                        buf.writeVarInt(p.chunkMaxX);
                        buf.writeVarInt(p.chunkMaxZ);
                    },
                    buf -> new RequestMapRegionPayload(
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
