package com.minekings.minekings.politics;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.minekings.minekings.MineKings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Datapack reload listener for {@link Culture} entries. Loads JSON files
 * from {@code data/minekings/cultures/*.json} and exposes lookup by biome
 * (for founding) and by string name (for rehydrating stored polities after
 * world reload).
 *
 * <p>Mirrors the pattern used by {@code BuildingTypes} in the village layer.
 */
public class CultureManager extends SimpleJsonResourceReloadListener implements Iterable<Culture> {
    protected static final ResourceLocation ID = MineKings.locate("cultures");
    private static CultureManager INSTANCE = new CultureManager();
    private final Map<String, Culture> cultures = new HashMap<>();

    public CultureManager() {
        super(new Gson(), ID.getPath());
        INSTANCE = this;
    }

    public static CultureManager getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        cultures.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
            String name = entry.getKey().getPath();
            try {
                Culture culture = new Culture(name, entry.getValue().getAsJsonObject());
                cultures.put(name, culture);
            } catch (Exception e) {
                MineKings.LOGGER.error("Failed to load culture '{}': {}", name, e.getMessage());
            }
        }
        MineKings.LOGGER.info("Loaded {} cultures", cultures.size());
    }

    public Map<String, Culture> getCultures() {
        return cultures;
    }

    public Optional<Culture> findByName(String name) {
        return Optional.ofNullable(cultures.get(name));
    }

    /**
     * Assigns a culture for a given biome using the two-pass match rule:
     * <ol>
     *     <li>First pass: return the first non-wildcard culture whose
     *         biome list contains {@code biomeId}.</li>
     *     <li>Second pass: if none matched, return the first wildcard
     *         culture (empty biome list).</li>
     *     <li>Fallback: if no culture loaded at all, return
     *         {@link Culture#empty()} and log an error.</li>
     * </ol>
     */
    public Culture assignCultureForBiome(ResourceLocation biomeId) {
        // Pass 1: explicit biome match
        for (Culture c : cultures.values()) {
            if (!c.isWildcard() && c.matchesBiome(biomeId)) {
                return c;
            }
        }
        // Pass 2: wildcard fallback
        for (Culture c : cultures.values()) {
            if (c.isWildcard()) {
                return c;
            }
        }
        // Pass 3: no cultures at all
        MineKings.LOGGER.error("No cultures loaded (not even a wildcard fallback); returning empty sentinel for biome {}", biomeId);
        return Culture.empty();
    }

    @Override
    public Iterator<Culture> iterator() {
        return cultures.values().iterator();
    }
}
