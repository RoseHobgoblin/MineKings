package com.minekings.minekings.client.hub.map;

import com.minekings.minekings.politics.MapRegionPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * The real map-data capture pipeline: hook client-side chunk loads and
 * sample biome + terrain from the chunk data the client already has, no
 * round-trip to the server. This is the same model AntiqueAtlas and
 * JourneyMap use.
 *
 * <p>Writes to {@link MapTileCache} directly. Village markers still come
 * from the server, but they're small and can be fetched on demand or
 * pushed on change — that channel is independent of the terrain grid.
 */
public final class ClientMapCapture {
    private ClientMapCapture() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        BlockPos sample = new BlockPos(cx * 16 + 8, level.getSeaLevel(), cz * 16 + 8);
        Holder<Biome> biome = level.getBiome(sample);

        byte terrain = (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN))
                ? MapRegionPayload.TERRAIN_OCEAN
                : MapRegionPayload.TERRAIN_LAND;

        ResourceLocation biomeId = biome.unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);

        MapTileCache.INSTANCE.putTile(cx, cz, terrain, biomeId);
    }

    /** Reset the per-session cache on disconnect so a new world starts clean. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MapTileCache.INSTANCE.clear();
    }
}
