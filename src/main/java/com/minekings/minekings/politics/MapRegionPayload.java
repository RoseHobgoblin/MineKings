package com.minekings.minekings.politics;

import com.minekings.minekings.MineKings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server→client: a rectangular slab of map data.
 *
 * <p>Two parallel grids of length {@code width * height}, row-major over
 * Z then X (i.e. index = {@code dz * width + dx}):
 * <ul>
 *   <li>{@link #terrain} — one byte per chunk: {@link #TERRAIN_UNLOADED},
 *       {@link #TERRAIN_LAND}, or {@link #TERRAIN_OCEAN}.</li>
 *   <li>{@link #biomeIds} — one short per chunk: 0 = unknown / unloaded,
 *       otherwise an index into {@link #biomePalette} + 1.</li>
 * </ul>
 *
 * <p>{@link #biomePalette} de-duplicates biome resource-location strings
 * across the slab so a 4096-chunk region carries a few dozen short
 * strings rather than 4096 copies of "minecraft:plains".
 *
 * <p>Village markers carry block-space coords (not remapped to the slab),
 * so the client can place them sub-chunk-accurately at any zoom.
 */
public record MapRegionPayload(
        int chunkMinX,
        int chunkMinZ,
        int width,
        int height,
        byte[] terrain,
        short[] biomeIds,
        List<String> biomePalette,
        List<VillageMarker> markers,
        List<ChunkTileOverride> tileOverrides,
        List<VillageChunks> villageChunks
) implements CustomPacketPayload {

    /**
     * Per-chunk tile replacement used by the hub map to render village
     * buildings (house/farm/path/...) in place of the underlying biome tile.
     * Tile id is a full ResourceLocation string like {@code minekings:house}.
     */
    public record ChunkTileOverride(int chunkX, int chunkZ, String tileId) {}

    /**
     * The set of chunks a single village occupies, plus its culture color,
     * used by the client to draw a 1-px outline around the village cluster
     * and to hit-test hover anywhere inside that region.
     */
    public record VillageChunks(int villageId, int colorRGB, long[] chunkPosLongs) {}

    public static final byte TERRAIN_UNLOADED = 0;
    public static final byte TERRAIN_LAND = 1;
    public static final byte TERRAIN_OCEAN = 2;

    /**
     * @param name      village name (e.g. "Millbrook")
     * @param polityName polity display name (e.g. "Kingdom of Millbrook"), or empty
     * @param leaderLine leader title + name, "(vacant)", or "(unclaimed)"
     * @param tier      subtree height; 0 = barony, 1 = county, etc.
     * @param population current village population
     * @param markerTypeId byte encoding of {@code MarkerType}:
     *        0 = TOWN (held but not the polity seat),
     *        1 = SEAT_OF_VASSAL (seat of a polity that has a liege),
     *        2 = CAPITAL (seat of a sovereign polity).
     */
    public record VillageMarker(
            int blockX,
            int blockZ,
            int colorRGB,
            int tier,
            int population,
            int markerTypeId,
            String name,
            String polityName,
            String leaderLine
    ) {}

    public static final Type<MapRegionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineKings.MODID, "map_region"));

    public static final StreamCodec<FriendlyByteBuf, MapRegionPayload> STREAM_CODEC =
            StreamCodec.of(MapRegionPayload::encode, MapRegionPayload::decode);

    private static void encode(FriendlyByteBuf buf, MapRegionPayload p) {
        buf.writeVarInt(p.chunkMinX);
        buf.writeVarInt(p.chunkMinZ);
        buf.writeVarInt(p.width);
        buf.writeVarInt(p.height);
        buf.writeByteArray(p.terrain);
        buf.writeVarInt(p.biomeIds.length);
        for (short s : p.biomeIds) buf.writeShort(s);
        buf.writeVarInt(p.biomePalette.size());
        for (String s : p.biomePalette) buf.writeUtf(s, 96);
        buf.writeVarInt(p.markers.size());
        for (VillageMarker m : p.markers) {
            buf.writeVarInt(m.blockX);
            buf.writeVarInt(m.blockZ);
            buf.writeVarInt(m.colorRGB);
            buf.writeVarInt(m.tier);
            buf.writeVarInt(m.population);
            buf.writeByte(m.markerTypeId);
            buf.writeUtf(m.name, 64);
            buf.writeUtf(m.polityName, 128);
            buf.writeUtf(m.leaderLine, 96);
        }
        buf.writeVarInt(p.tileOverrides.size());
        for (ChunkTileOverride o : p.tileOverrides) {
            buf.writeVarInt(o.chunkX);
            buf.writeVarInt(o.chunkZ);
            buf.writeUtf(o.tileId, 96);
        }
        buf.writeVarInt(p.villageChunks.size());
        for (VillageChunks vc : p.villageChunks) {
            buf.writeVarInt(vc.villageId);
            buf.writeVarInt(vc.colorRGB);
            buf.writeVarInt(vc.chunkPosLongs.length);
            for (long cp : vc.chunkPosLongs) buf.writeLong(cp);
        }
    }

    private static MapRegionPayload decode(FriendlyByteBuf buf) {
        int cx = buf.readVarInt();
        int cz = buf.readVarInt();
        int w = buf.readVarInt();
        int h = buf.readVarInt();
        byte[] terrain = buf.readByteArray();
        int bnLen = buf.readVarInt();
        short[] biomeIds = new short[bnLen];
        for (int i = 0; i < bnLen; i++) biomeIds[i] = buf.readShort();
        int paletteLen = buf.readVarInt();
        List<String> palette = new ArrayList<>(paletteLen);
        for (int i = 0; i < paletteLen; i++) palette.add(buf.readUtf(96));
        int mn = buf.readVarInt();
        List<VillageMarker> ms = new ArrayList<>(mn);
        for (int i = 0; i < mn; i++) {
            ms.add(new VillageMarker(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readByte() & 0xFF,
                    buf.readUtf(64),
                    buf.readUtf(128),
                    buf.readUtf(96)
            ));
        }
        int on = buf.readVarInt();
        List<ChunkTileOverride> overrides = new ArrayList<>(on);
        for (int i = 0; i < on; i++) {
            overrides.add(new ChunkTileOverride(buf.readVarInt(), buf.readVarInt(), buf.readUtf(96)));
        }
        int vcn = buf.readVarInt();
        List<VillageChunks> vcs = new ArrayList<>(vcn);
        for (int i = 0; i < vcn; i++) {
            int vid = buf.readVarInt();
            int col = buf.readVarInt();
            int cpn = buf.readVarInt();
            long[] cps = new long[cpn];
            for (int j = 0; j < cpn; j++) cps[j] = buf.readLong();
            vcs.add(new VillageChunks(vid, col, cps));
        }
        return new MapRegionPayload(cx, cz, w, h, terrain, biomeIds, palette, ms, overrides, vcs);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
