package com.minekings.minekings.politics;

import com.minekings.minekings.village.Building;
import com.minekings.minekings.village.Village;
import com.minekings.minekings.village.VillageManager;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side handler for {@link RequestMapRegionPayload}. Delivers the
 * village markers whose center falls inside the requested chunk rect.
 *
 * <p>Terrain and biome data are <b>not</b> sampled here — that's the job
 * of the client-side chunk-load capture ({@code ClientMapCapture}), the
 * same model AntiqueAtlas / JourneyMap use.
 */
public final class RequestMapRegionHandler {

    private RequestMapRegionHandler() {}

    public static void handle(RequestMapRegionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> dispatch(player, payload));
    }

    private static void dispatch(ServerPlayer player, RequestMapRegionPayload req) {
        ServerLevel level = player.serverLevel();

        int minX = Math.min(req.chunkMinX(), req.chunkMaxX());
        int minZ = Math.min(req.chunkMinZ(), req.chunkMaxZ());
        int maxX = Math.max(req.chunkMinX(), req.chunkMaxX());
        int maxZ = Math.max(req.chunkMinZ(), req.chunkMaxZ());

        // Terrain + biomes are now captured client-side from chunks the
        // player has naturally loaded (see ClientMapCapture). This handler
        // exists only to deliver village markers for the requested rect.
        byte[] terrain = new byte[0];
        short[] biomeIds = new short[0];
        List<String> palette = new ArrayList<>();

        PoliticsManager pol = PoliticsManager.get(level);
        VillageManager vm = VillageManager.get(level);
        List<MapRegionPayload.VillageMarker> markers = new ArrayList<>();
        List<MapRegionPayload.ChunkTileOverride> overrides = new ArrayList<>();
        List<MapRegionPayload.VillageChunks> villageChunks = new ArrayList<>();

        int chunkMaxXIncl = maxX;
        int chunkMaxZIncl = maxZ;
        for (Village v : vm) {
            Vec3i c = v.getCenter();
            int vcx = c.getX() >> 4;
            int vcz = c.getZ() >> 4;
            if (vcx < minX || vcx > chunkMaxXIncl || vcz < minZ || vcz > chunkMaxZIncl) continue;

            int color = 0x888888;
            int tier = 0;
            String polityName = "";
            String leaderLine = "(unclaimed)";
            int markerTypeId = 0; // TOWN by default
            var holdingPolity = pol.findPolityHoldingVillage(v.getId());
            if (holdingPolity.isPresent()) {
                Polity p = holdingPolity.get();
                color = pol.getCultureOf(p).color();
                tier = pol.subtreeHeight(p.getId());
                polityName = pol.getPolityDisplayName(p);
                Character leader = pol.getCharacter(p.getLeaderCharacterId()).orElse(null);
                leaderLine = leader != null
                        ? (pol.getLeaderTitle(p) + " " + leader.getName())
                        : "(vacant)";
                // Seat = first village in heldVillageIds (the founding village).
                var held = p.getHeldVillageIds();
                boolean isSeat = !held.isEmpty() && held.iterator().next() == v.getId();
                if (isSeat) {
                    boolean sovereign = pol.getAllegianceFor(p.getId()).isEmpty();
                    markerTypeId = sovereign ? 2 /* CAPITAL */ : 1 /* SEAT_OF_VASSAL */;
                } else {
                    markerTypeId = 0; // TOWN
                }
            }
            markers.add(new MapRegionPayload.VillageMarker(
                    c.getX(), c.getZ(), color, tier, v.getPopulation(), markerTypeId,
                    v.getName(), polityName, leaderLine));

            // Per-building tile overrides + chunk union for this village.
            long[] chunks = new long[v.getBuildings().size()];
            int i = 0;
            for (Building b : v.getBuildings().values()) {
                overrides.add(new MapRegionPayload.ChunkTileOverride(
                        b.getChunkX(), b.getChunkZ(), "minekings:" + b.getType()));
                chunks[i++] = ChunkPos.asLong(b.getChunkX(), b.getChunkZ());
            }
            if (chunks.length > 0) {
                villageChunks.add(new MapRegionPayload.VillageChunks(v.getId(), color, chunks));
            }
        }

        // width/height left as 0: client reads only the markers field.
        PacketDistributor.sendToPlayer(player,
                new MapRegionPayload(minX, minZ, 0, 0, terrain, biomeIds, palette, markers,
                        overrides, villageChunks));
    }
}
