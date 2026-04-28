package com.minekings.minekings.client.hub.map;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.politics.MapRegionPayload;
import com.minekings.minekings.politics.RequestMapRegionPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** Per-chunk tile override (e.g. {@code minekings:house}) from village scan. */
    private final Map<Long, ResourceLocation> tileOverrides = new HashMap<>();

    /** Chunk → village region lookup for outline render + hover hit-test. */
    private final Map<Long, VillageRegion> regionByChunk = new HashMap<>();
    private final List<VillageRegion> regions = new ArrayList<>();

    /** A village's chunk footprint + culture color + its marker data. */
    public record VillageRegion(
            int villageId,
            int colorRGB,
            java.util.Set<Long> chunks,
            MapRegionPayload.VillageMarker marker
    ) {}

    private boolean inFlight = false;
    private long lastRequestMs = 0L;
    /** Monotonic version bumped on every tile mutation. Viewport-level
     *  memos in the renderer watch this to know when to rebuild. */
    private volatile int version = 0;

    private MapTileCache() {}

    public int version() { return version; }

    /** Drops everything. Call when the hub closes or world changes. */
    public void clear() {
        tiles.clear();
        villagesByPos.clear();
        tileOverrides.clear();
        regionByChunk.clear();
        regions.clear();
        inFlight = false;
        version++;
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
        version++;
    }

    /**
     * Returns the biome {@link ResourceLocation} for this chunk, or null if
     * we haven't received data for it yet. Used by {@link TileRenderIterator}
     * as the chunk "tile ID" for autotile stitching. Called in the render
     * hot path — keep cheap.
     */
    public @Nullable ResourceLocation biomeAt(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        ResourceLocation override = tileOverrides.get(key);
        if (override != null) return override;
        ChunkTile t = tiles.get(key);
        return t == null ? null : t.biome();
    }

    public List<MapRegionPayload.VillageMarker> villages() {
        return new ArrayList<>(villagesByPos.values());
    }

    /** All known village regions (for outline rendering). */
    public List<VillageRegion> villageRegions() {
        return regions;
    }

    /** Village whose chunk set contains {@code (cx, cz)}, or {@code null}. */
    public @Nullable VillageRegion villageAtChunk(int chunkX, int chunkZ) {
        return regionByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
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

        // Replace tile overrides wholesale.
        tileOverrides.clear();
        for (MapRegionPayload.ChunkTileOverride o : p.tileOverrides()) {
            ResourceLocation rl = ResourceLocation.tryParse(o.tileId());
            if (rl == null) continue;
            tileOverrides.put(ChunkPos.asLong(o.chunkX(), o.chunkZ()), rl);
        }

        // Rebuild village regions and the reverse chunk → region index.
        regionByChunk.clear();
        regions.clear();
        for (MapRegionPayload.VillageChunks vc : p.villageChunks()) {
            java.util.Set<Long> chunkSet = new java.util.HashSet<>(vc.chunkPosLongs().length * 2);
            for (long cp : vc.chunkPosLongs()) chunkSet.add(cp);
            // Pair the region with its marker so hover can surface the tooltip
            // without a second lookup.
            MapRegionPayload.VillageMarker marker = null;
            for (MapRegionPayload.VillageMarker m : p.markers()) {
                long mk = ChunkPos.asLong(m.blockX() >> 4, m.blockZ() >> 4);
                if (chunkSet.contains(mk)) { marker = m; break; }
            }
            VillageRegion region = new VillageRegion(vc.villageId(), vc.colorRGB(), chunkSet, marker);
            regions.add(region);
            for (long cp : vc.chunkPosLongs()) regionByChunk.put(cp, region);
        }

        inFlight = false;
        version++;
    }

    // ---- Disk persistence (client-side, per-world) ----

    /**
     * Save the tile map + markers to a binary NBT file. Cheap: one compound
     * with parallel int/long/string arrays.
     */
    public void saveToFile(Path path) {
        try {
            Files.createDirectories(path.getParent());
            CompoundTag root = new CompoundTag();
            root.putInt("version", 1);
            int n = tiles.size();
            long[] keys = new long[n];
            byte[] terrains = new byte[n];
            ListTag biomeList = new ListTag();
            int i = 0;
            for (Map.Entry<Long, ChunkTile> e : tiles.entrySet()) {
                keys[i] = e.getKey();
                terrains[i] = e.getValue().terrain();
                ResourceLocation b = e.getValue().biome();
                biomeList.add(net.minecraft.nbt.StringTag.valueOf(b == null ? "" : b.toString()));
                i++;
            }
            root.putLongArray("keys", keys);
            root.putByteArray("terrains", terrains);
            root.put("biomes", biomeList);
            NbtIo.writeCompressed(root, path);
            MineKings.LOGGER.info("[MK] Saved {} map tiles to {}", n, path.getFileName());
        } catch (IOException ex) {
            MineKings.LOGGER.warn("[MK] Failed to save map cache: {}", ex.getMessage());
        }
    }

    /**
     * Replace the current cache with tiles loaded from disk. Silently no-ops
     * if the file doesn't exist (first visit to this world).
     */
    public void loadFromFile(Path path) {
        if (!Files.exists(path)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            tiles.clear();
            villagesByPos.clear();
            long[] keys = root.getLongArray("keys");
            byte[] terrains = root.getByteArray("terrains");
            ListTag biomeList = root.getList("biomes", Tag.TAG_STRING);
            long now = System.currentTimeMillis();
            int n = Math.min(keys.length, Math.min(terrains.length, biomeList.size()));
            for (int i = 0; i < n; i++) {
                String s = biomeList.getString(i);
                ResourceLocation biome = s.isEmpty() ? null : ResourceLocation.tryParse(s);
                tiles.put(keys[i], new ChunkTile(terrains[i], biome, now));
            }
            version++;
            MineKings.LOGGER.info("[MK] Loaded {} map tiles from {}", n, path.getFileName());
        } catch (IOException ex) {
            MineKings.LOGGER.warn("[MK] Failed to load map cache: {}", ex.getMessage());
        }
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
