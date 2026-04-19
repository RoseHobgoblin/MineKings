package com.minekings.minekings.politics;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * Server-side handler for {@link RequestHubDataPayload}. For the Clan
 * tab, looks up the player's polity (player-backed leader) and sends
 * back a {@link PolityViewPayload}. If the player rules no polity,
 * sends back an empty marker payload — TBD in v0.8.
 */
public final class RequestHubDataHandler {
    private RequestHubDataHandler() {}

    public static void handle(RequestHubDataPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> dispatch(player, payload.tabId()));
    }

    private static void dispatch(ServerPlayer player, int tabId) {
        ServerLevel level = player.serverLevel();
        PoliticsManager mgr = PoliticsManager.get(level);

        if (tabId == RequestHubDataPayload.TAB_CLAN) {
            Optional<Polity> polity = mgr.getPolityRuledByPlayer(player.getUUID());
            if (polity.isEmpty()) return; // TODO v0.8: signal "no polity ruled"
            PolityViewPayload payload = PoliticsEntityEvents.buildPayload(mgr, polity.get(), level);
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
