package com.minekings.minekings.politics;

import com.minekings.minekings.MineKings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client→server: "send me the data for this hub tab." Server replies
 * with the appropriate payload (e.g., {@link PolityViewPayload} for the
 * Clan tab).
 */
public record RequestHubDataPayload(int tabId) implements CustomPacketPayload {
    public static final int TAB_CLAN = 1;

    public static final Type<RequestHubDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineKings.MODID, "request_hub_data"));

    public static final StreamCodec<FriendlyByteBuf, RequestHubDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarInt(p.tabId),
                    buf -> new RequestHubDataPayload(buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
