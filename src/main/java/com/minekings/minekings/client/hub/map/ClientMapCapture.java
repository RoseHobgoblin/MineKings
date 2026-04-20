package com.minekings.minekings.client.hub.map;

import com.minekings.minekings.politics.MapRegionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.nio.file.Path;

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
    private static @org.jetbrains.annotations.Nullable Path currentCacheFile = null;

    private ClientMapCapture() {}

    /**
     * Derives a client-side cache file path keyed on world identity:
     * integrated-server save name for SP, host:port hash for MP. Returns
     * null if we can't identify the world (shouldn't happen in practice).
     */
    private static @org.jetbrains.annotations.Nullable Path resolveCacheFile() {
        Minecraft mc = Minecraft.getInstance();
        String key;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            key = "sp_" + sanitize(mc.getSingleplayerServer().getWorldData().getLevelName());
        } else {
            ServerData sd = mc.getCurrentServer();
            if (sd == null) return null;
            key = "mp_" + sanitize(sd.ip);
        }
        return FMLPaths.GAMEDIR.get()
                .resolve("minekings").resolve("mapcache").resolve(key + ".dat");
    }

    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append((Character.isLetterOrDigit(c) || c == '_' || c == '-') ? c : '_');
        }
        return sb.toString();
    }

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

    /**
     * On login: identify the world and load its persisted tile cache
     * (if any). Clears first so we don't leak tiles from a previous world
     * when /reconnect jumps straight from one server to another without an
     * intervening LoggingOut.
     */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        MapTileCache.INSTANCE.clear();
        Path file = resolveCacheFile();
        currentCacheFile = file;
        if (file != null) {
            MapTileCache.INSTANCE.loadFromFile(file);
        }
    }

    /**
     * On logout: persist the tile cache to its world-keyed file, then
     * clear in-memory state so a subsequent different world starts clean.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (currentCacheFile != null) {
            MapTileCache.INSTANCE.saveToFile(currentCacheFile);
            currentCacheFile = null;
        }
        MapTileCache.INSTANCE.clear();
    }
}
