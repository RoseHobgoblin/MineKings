package com.minekings.minekings.client.hub.map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minekings.minekings.MineKings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datapack reload listener for {@code data/minekings/atlas/texture_sets/*.json}.
 *
 * <p>JSON shape (from AntiqueAtlas, kept verbatim):
 * <pre>
 * {
 *   "version": 1,
 *   "data": {
 *     "textures": {
 *       "minekings:forest": 1,
 *       "minekings:forest2": 1,
 *       "minekings:forest3": 1
 *     }
 *   }
 * }
 * </pre>
 *
 * <p>The key is a texture reference; the value is its weight. We rewrite
 * {@code "minekings:foo"} into {@code ResourceLocation(minekings,
 * textures/gui/map/tiles/foo.png)} at load time.
 */
public class TileTextureSetManager extends SimpleJsonResourceReloadListener {
    protected static final ResourceLocation ID = MineKings.locate("atlas/texture_sets");
    private static TileTextureSetManager INSTANCE = new TileTextureSetManager();

    private final Map<ResourceLocation, TileTextureSet> sets = new HashMap<>();

    public TileTextureSetManager() {
        super(new Gson(), "atlas/texture_sets");
        INSTANCE = this;
    }

    public static TileTextureSetManager getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        sets.clear();
        for (Map.Entry<ResourceLocation, JsonElement> e : prepared.entrySet()) {
            try {
                TileTextureSet set = parse(e.getKey(), e.getValue().getAsJsonObject());
                sets.put(e.getKey(), set);
            } catch (Exception ex) {
                MineKings.LOGGER.error("Failed to load texture set {}: {}", e.getKey(), ex.getMessage());
            }
        }
        MineKings.LOGGER.info("Loaded {} map texture sets.", sets.size());
    }

    private static TileTextureSet parse(ResourceLocation id, JsonObject root) {
        JsonObject data = root.getAsJsonObject("data");
        JsonObject textures = data.getAsJsonObject("textures");
        List<TileTextureSet.Variant> variants = new ArrayList<>();
        for (Map.Entry<String, JsonElement> t : textures.entrySet()) {
            ResourceLocation texId = resolveTexture(t.getKey());
            int weight = t.getValue().isJsonPrimitive()
                    ? Math.max(1, t.getValue().getAsInt())
                    : 1;
            variants.add(new TileTextureSet.Variant(texId, weight));
        }
        return TileTextureSet.of(id, variants);
    }

    /**
     * Rewrites short identifiers like {@code minekings:forest} into the
     * full texture path {@code minekings:textures/gui/map/tiles/forest.png}.
     */
    private static ResourceLocation resolveTexture(String key) {
        ResourceLocation ref = ResourceLocation.parse(key);
        String path = ref.getPath();
        if (!path.endsWith(".png")) path = "textures/gui/map/tiles/" + path + ".png";
        return ResourceLocation.fromNamespaceAndPath(ref.getNamespace(), path);
    }

    public Optional<TileTextureSet> get(ResourceLocation id) {
        return Optional.ofNullable(sets.get(id));
    }

    public Optional<TileTextureSet> get(String namespaceAndPath) {
        try {
            return get(ResourceLocation.parse(namespaceAndPath));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
