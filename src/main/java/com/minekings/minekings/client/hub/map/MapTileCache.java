package com.minekings.minekings.client.hub.map;

import com.minekings.minekings.politics.MapRegionPayload;
import com.minekings.minekings.politics.RequestMapRegionPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side LRU cache of map chunk tiles served by
 * {@link MapRegionPayload}. The hub map renderer reads from here; misses
 * trigger a region request via {@link #requestRegion} subject to a
 * single-flight throttle so panning bursts don't flood the server.
 *
 * <p>Also stores the most recent set of village markers, replaced whole
 * on each payload (markers don't have a persistent per-chunk identity in
 * the current payload, so caching them per-tile is overkill).
 */
public final class MapTileCache {
    public static final MapTileCache INSTANCE = new MapTileCache();

    /** Soft cap on cached tiles. ~131k * ~80 bytes ≈ 10 MB — lets you zoom out
     *  across a reasonable explored area without eviction. */
    private static final int MAX_TILES = 131_072;
    /** Min ms between successive region requests. */
    private static final long REQUEST_INTERVAL_MS = 100L;

    /**
     * @param biome pre-parsed biome ResourceLocation (null if unknown).
     *              Parsing is done once at apply() time so the renderer's
     *              hot path doesn't re-parse on every neighbor peek.
     */
    public record ChunkTile(byte terrain, @Nullable ResourceLocation biome, long lastSeenMs) {}

    /** access-order LRU; iteration evicts oldest. */
    private final LinkedHashMap<Long, ChunkTile> tiles =
            new LinkedHashMap<>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, ChunkTile> eldest) {
                    return size() > MAX_TILES;
                }
            };

    /** Visible-village snapshot; refreshed whole on each payload. */
    private final Map<Long, MapRegionPayload.VillageMarker> villagesByPos = new HashMap<>();

    private boolean inFlight = false;
    private long lastRequestMs = 0L;

    private MapTileCache() {}

    /** Drops everything. Call when the hub closes or world changes. */
    public void clear() {
        tiles.clear();
        villagesByPos.clear();
        inFlight = false;
    }

    public @Nullable ChunkTile get(int chunkX, int chunkZ) {
        return tiles.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    /**
     * Direct tile write — called from {@link ClientMapCapture} as the
     * client naturally loads chunks in the world. Idempotent and cheap,
     * but fires on every chunk load so stays out of the render path.
     */
    public void putTile(int chunkX, int chunkZ, byte terrain, @Nullable ResourceLocation biome) {
        if (terrain == MapRegionPayload.TERRAIN_UNLOADED) return;
        tiles.put(ChunkPos.asLong(chunkX, chunkZ),
                new ChunkTile(terrain, biome, System.currentTimeMillis()));
    }

    /**
     * Returns the biome {@link ResourceLocation} for this chunk, or null if
     * we haven't received data for it yet. Used by {@link TileRenderIterator}
     * as the chunk "tile ID" for autotile stitching. Called in the render
     * hot path — keep cheap.
     */
    public @Nullable ResourceLocation biomeAt(int chunkX, int chunkZ) {
        ChunkTile t = tiles.get(ChunkPos.asLong(chunkX, chunkZ));
        return t == null ? null : t.biome();
    }

    public List<MapRegionPayload.VillageMarker> villages() {
        return new ArrayList<>(villagesByPos.values());
    }

    /**
     * Apply a payload to the cache. In the current model the payload
     * carries only village markers — terrain/biome cache entries are
     * written by {@link ClientMapCapture} as chunks load naturally.
     */
    public void apply(MapRegionPayload p) {
        // Whole-dimension marker refresh: replace the entire marker set
        // with what came back, so stale polities (merged, destroyed) drop.
        villagesByPos.clear();
        for (MapRegionPayload.VillageMarker m : p.markers()) {
            long key = ChunkPos.asLong(m.blockX() >> 4, m.blockZ() >> 4);
            villagesByPos.put(key, m);
        }
        inFlight = false;
    }

    /**
     * Request a chunk rectangle from the server. Single-flight: if a
     * request is already in flight or we requested too recently, this is
     * a no-op (the renderer will re-attempt next frame).
     *
     * @return true if a request was actually sent
     */
    public boolean requestRegion(int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ) {
        long now = System.currentTimeMillis();
        if (inFlight) return false;
        if (now - lastRequestMs < REQUEST_INTERVAL_MS) return false;
        inFlight = true;
        lastRequestMs = now;
        PacketDistributor.sendToServer(new RequestMapRegionPayload(chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ));
        return true;
    }

}
