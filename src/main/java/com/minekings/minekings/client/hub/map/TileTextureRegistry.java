package com.minekings.minekings.client.hub.map;

import com.minekings.minekings.MineKings;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a biome resource location → {@link TileTextureSet} for the
 * autotile renderer. Ported from AntiqueAtlas's {@code TileTextureMap}
 * but rewritten for Minecraft 1.21.1, which removed {@code Biome.Category}
 * in favor of tag-based classification.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Direct vanilla biome → set name table (exact mapping for known
 *       biomes like {@code minecraft:plains}).</li>
 *   <li>Tag fallback for unknown (modded) biomes — match by
 *       {@link BiomeTags} families.</li>
 *   <li>Plain {@code minekings:plains} default.</li>
 * </ol>
 */
public final class TileTextureRegistry {
    public static final ResourceLocation DEFAULT_SET = MineKings.locate("plains");

    private static final Map<String, String> DIRECT = new HashMap<>();
    static {
        // Vanilla 1.21.1 overworld biomes → AA-compatible texture set names.
        DIRECT.put("minecraft:plains",                  "minekings:plains");
        DIRECT.put("minecraft:sunflower_plains",        "minekings:plains");
        DIRECT.put("minecraft:snowy_plains",            "minekings:snow");
        DIRECT.put("minecraft:ice_spikes",              "minekings:ice_spikes");
        DIRECT.put("minecraft:desert",                  "minekings:desert");
        DIRECT.put("minecraft:swamp",                   "minekings:swamp");
        DIRECT.put("minecraft:mangrove_swamp",          "minekings:swamp");
        DIRECT.put("minecraft:forest",                  "minekings:forest");
        DIRECT.put("minecraft:flower_forest",           "minekings:forest_flowers");
        DIRECT.put("minecraft:birch_forest",            "minekings:birch");
        DIRECT.put("minecraft:dark_forest",             "minekings:dense_forest");
        DIRECT.put("minecraft:old_growth_birch_forest", "minekings:dense_birch");
        DIRECT.put("minecraft:old_growth_pine_taiga",   "minekings:snow_pines");
        DIRECT.put("minecraft:old_growth_spruce_taiga", "minekings:snow_pines");
        DIRECT.put("minecraft:taiga",                   "minekings:snow_pines");
        DIRECT.put("minecraft:snowy_taiga",             "minekings:snow_pines");
        DIRECT.put("minecraft:savanna",                 "minekings:savanna");
        DIRECT.put("minecraft:savanna_plateau",         "minekings:plateau_savanna");
        DIRECT.put("minecraft:windswept_hills",         "minekings:hills");
        DIRECT.put("minecraft:windswept_gravelly_hills","minekings:hills");
        DIRECT.put("minecraft:windswept_forest",        "minekings:forest_hills");
        DIRECT.put("minecraft:windswept_savanna",       "minekings:savanna");
        DIRECT.put("minecraft:jungle",                  "minekings:jungle");
        DIRECT.put("minecraft:sparse_jungle",           "minekings:jungle");
        DIRECT.put("minecraft:bamboo_jungle",           "minekings:jungle");
        DIRECT.put("minecraft:badlands",                "minekings:plateau_mesa");
        DIRECT.put("minecraft:eroded_badlands",         "minekings:plateau_mesa_low");
        DIRECT.put("minecraft:wooded_badlands",         "minekings:plateau_mesa_trees");
        DIRECT.put("minecraft:meadow",                  "minekings:plains");
        DIRECT.put("minecraft:cherry_grove",            "minekings:forest_flowers");
        DIRECT.put("minecraft:grove",                   "minekings:snow_pines");
        DIRECT.put("minecraft:snowy_slopes",            "minekings:snow");
        DIRECT.put("minecraft:frozen_peaks",            "minekings:mountains_snow_caps");
        DIRECT.put("minecraft:jagged_peaks",            "minekings:mountains_snow_caps");
        DIRECT.put("minecraft:stony_peaks",             "minekings:mountains");
        DIRECT.put("minecraft:stony_shore",             "minekings:rock_shore");
        DIRECT.put("minecraft:beach",                   "minekings:shore");
        DIRECT.put("minecraft:snowy_beach",             "minekings:shore");
        DIRECT.put("minecraft:river",                   "minekings:water");
        DIRECT.put("minecraft:frozen_river",            "minekings:ice");
        DIRECT.put("minecraft:ocean",                   "minekings:water");
        DIRECT.put("minecraft:deep_ocean",              "minekings:water");
        DIRECT.put("minecraft:cold_ocean",              "minekings:water");
        DIRECT.put("minecraft:deep_cold_ocean",         "minekings:water");
        DIRECT.put("minecraft:frozen_ocean",            "minekings:ice");
        DIRECT.put("minecraft:deep_frozen_ocean",       "minekings:ice");
        DIRECT.put("minecraft:lukewarm_ocean",          "minekings:water");
        DIRECT.put("minecraft:deep_lukewarm_ocean",     "minekings:water");
        DIRECT.put("minecraft:warm_ocean",              "minekings:water");
        DIRECT.put("minecraft:mushroom_fields",         "minekings:mushroom");
        DIRECT.put("minecraft:dripstone_caves",         "minekings:cave_walls");
        DIRECT.put("minecraft:lush_caves",              "minekings:dense_forest");
        DIRECT.put("minecraft:deep_dark",               "minekings:cave_walls");
    }

    /**
     * Memo for {@link #resolveByLocation} — the renderer calls it per subtile
     * per frame (hundreds of times) with a small set of distinct biomes, so
     * we cache the result. Invalidated whenever the texture set datapack
     * reloads via {@link #invalidateResolveCache()}.
     */
    private static final Map<ResourceLocation, TileTextureSet> RESOLVE_CACHE = new ConcurrentHashMap<>();
    /** Bumped on each cache invalidation so viewport-level memos can notice. */
    private static volatile int CACHE_VERSION = 0;

    /** Called by {@link TileTextureSetManager} after reload. */
    public static void invalidateResolveCache() {
        RESOLVE_CACHE.clear();
        CACHE_VERSION++;
    }

    public static int cacheVersion() { return CACHE_VERSION; }

    private TileTextureRegistry() {}

    /**
     * Resolve a texture set for this biome. Always returns a set; worst case,
     * the {@link #DEFAULT_SET} plains tile set or — if data isn't loaded —
     * an empty placeholder backed by the v0.7 {@code plains} tile.
     */
    /**
     * Hot-path lookup taking a pre-parsed {@link ResourceLocation} directly.
     * Avoids the {@code toString()} + re-parse that the renderer would
     * otherwise do 12× per chunk per frame.
     */
    public static TileTextureSet resolveByLocation(ResourceLocation biomeId) {
        if (biomeId == null) {
            return TileTextureSetManager.getInstance().get(DEFAULT_SET)
                    .orElseGet(() -> TileTextureSet.of(DEFAULT_SET, java.util.List.of()));
        }
        return RESOLVE_CACHE.computeIfAbsent(biomeId, id -> {
            String direct = DIRECT.get(id.toString());
            TileTextureSetManager mgr = TileTextureSetManager.getInstance();
            if (direct != null) {
                Optional<TileTextureSet> d = mgr.get(direct);
                if (d.isPresent()) return d.get();
            }
            return mgr.get(DEFAULT_SET)
                    .orElseGet(() -> TileTextureSet.of(DEFAULT_SET, java.util.List.of()));
        });
    }

    public static TileTextureSet resolve(Holder<Biome> holder) {
        Optional<ResourceLocation> biomeId = holder.unwrapKey().map(k -> k.location());
        TileTextureSetManager mgr = TileTextureSetManager.getInstance();

        if (biomeId.isPresent()) {
            String direct = DIRECT.get(biomeId.get().toString());
            if (direct != null) {
                Optional<TileTextureSet> d = mgr.get(direct);
                if (d.isPresent()) return d.get();
            }
        }

        String fallback = fallbackByTag(holder);
        Optional<TileTextureSet> f = mgr.get(fallback);
        if (f.isPresent()) return f.get();

        return mgr.get(DEFAULT_SET)
                .orElseGet(() -> TileTextureSet.of(DEFAULT_SET, java.util.List.of()));
    }

    /**
     * Resolve by the cached biome key string carried in {@code ChunkTile.biomeKey}.
     * Doesn't have the {@code Holder<Biome>} available, so tag fallback uses the
     * plain plains set.
     */
    public static TileTextureSet resolveByKey(String biomeKey) {
        if (biomeKey == null || biomeKey.isEmpty()) {
            return TileTextureSetManager.getInstance().get(DEFAULT_SET)
                    .orElseGet(() -> TileTextureSet.of(DEFAULT_SET, java.util.List.of()));
        }
        String direct = DIRECT.get(biomeKey);
        TileTextureSetManager mgr = TileTextureSetManager.getInstance();
        if (direct != null) {
            Optional<TileTextureSet> d = mgr.get(direct);
            if (d.isPresent()) return d.get();
        }
        return mgr.get(DEFAULT_SET)
                .orElseGet(() -> TileTextureSet.of(DEFAULT_SET, java.util.List.of()));
    }

    private static String fallbackByTag(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_OCEAN)) return "minekings:water";
        if (biome.is(BiomeTags.IS_RIVER))   return "minekings:water";
        if (biome.is(BiomeTags.IS_BEACH))   return "minekings:shore";
        if (biome.is(BiomeTags.IS_JUNGLE))  return "minekings:jungle";
        if (biome.is(BiomeTags.IS_SAVANNA)) return "minekings:savanna";
        if (biome.is(BiomeTags.IS_BADLANDS))return "minekings:plateau_mesa";
        if (biome.is(BiomeTags.IS_FOREST))  return "minekings:forest";
        if (biome.is(BiomeTags.IS_TAIGA))   return "minekings:snow_pines";
        if (biome.is(BiomeTags.IS_MOUNTAIN))return "minekings:mountains";
        if (biome.is(BiomeTags.IS_HILL))    return "minekings:hills";
        if (biome.is(BiomeTags.IS_NETHER))  return "minekings:soul_sand_valley";
        if (biome.is(BiomeTags.IS_END))     return "minekings:end_island";
        return "minekings:plains";
    }

    /** Used when we only have the biome key, not a full Holder. */
    public static boolean isOceanBiomeKey(String biomeKey) {
        return biomeKey != null && (
                biomeKey.contains("ocean") ||
                biomeKey.equals("minecraft:river") ||
                biomeKey.equals("minecraft:frozen_river"));
    }
}
