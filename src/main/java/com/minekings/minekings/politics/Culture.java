package com.minekings.minekings.politics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Datapack-loaded cultural identity for a polity. Defines the tier labels,
 * leader titles, name pools, and biome affinity that determine how the
 * polity and its leaders are rendered. Immutable after load.
 *
 * <p>Culture is assigned to a polity at founding based on the biome at the
 * village's center and never changes afterwards — even if the polity is
 * subjugated by a polity of a different culture. Cultural identity persists
 * through conquest.
 *
 * <p>Authors supply one JSON file per culture under
 * {@code data/minekings/cultures/*.json}. See {@link CultureManager} for the
 * loading pipeline and {@link Culture#Culture(String, JsonObject)} for the
 * accepted schema.
 */
public final class Culture {
    private final String id;
    private final String displayName;
    private final List<ResourceLocation> biomes;
    private final List<String> tiers;
    private final List<String> leaderTitles;
    private final List<String> firstNames;
    private final List<String> lastNames;
    private final String polityNameFormat;
    /** Packed 0xRRGGBB. Used to tint this culture's villages on the map. */
    private final int color;

    /** Sentinel used when no culture at all is loaded (misconfigured datapack). */
    public static Culture empty() {
        return new Culture(
                "empty",
                "Empty",
                List.of(),
                List.of("Settlement"),
                List.of("Leader"),
                List.of("Nameless"),
                List.of("One"),
                "{tier} of {place}",
                0x888888
        );
    }

    private Culture(String id,
                    String displayName,
                    List<ResourceLocation> biomes,
                    List<String> tiers,
                    List<String> leaderTitles,
                    List<String> firstNames,
                    List<String> lastNames,
                    String polityNameFormat,
                    int color) {
        this.id = id;
        this.displayName = displayName;
        this.biomes = Collections.unmodifiableList(biomes);
        this.tiers = Collections.unmodifiableList(tiers);
        this.leaderTitles = Collections.unmodifiableList(leaderTitles);
        this.firstNames = Collections.unmodifiableList(firstNames);
        this.lastNames = Collections.unmodifiableList(lastNames);
        this.polityNameFormat = polityNameFormat;
        this.color = color;
    }

    public Culture(String id, JsonObject json) {
        this.id = id;
        this.displayName = GsonHelper.getAsString(json, "displayName", id);
        this.biomes = parseResourceLocations(json, "biomes");
        this.tiers = parseStringList(json, "tiers");
        this.leaderTitles = parseStringList(json, "leaderTitles");
        this.firstNames = parseStringList(json, "firstNames");
        this.lastNames = parseStringList(json, "lastNames");
        this.polityNameFormat = GsonHelper.getAsString(json, "polityNameFormat", "{tier} of {place}");
        this.color = parseColor(json);
    }

    private static int parseColor(JsonObject json) {
        if (!json.has("color")) return 0x888888;
        JsonElement e = json.get("color");
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString().trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
            try {
                return Integer.parseInt(s, 16) & 0xFFFFFF;
            } catch (NumberFormatException ex) {
                return 0x888888;
            }
        }
        return e.getAsInt() & 0xFFFFFF;
    }

    private static List<ResourceLocation> parseResourceLocations(JsonObject json, String key) {
        List<ResourceLocation> out = new ArrayList<>();
        if (!json.has(key)) return out;
        JsonArray arr = GsonHelper.getAsJsonArray(json, key);
        for (JsonElement e : arr) {
            out.add(ResourceLocation.parse(e.getAsString()));
        }
        return out;
    }

    private static List<String> parseStringList(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        if (!json.has(key)) return out;
        JsonArray arr = GsonHelper.getAsJsonArray(json, key);
        for (JsonElement e : arr) {
            out.add(e.getAsString());
        }
        return out;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public List<ResourceLocation> biomes() { return biomes; }
    public List<String> tiers() { return tiers; }
    public List<String> leaderTitles() { return leaderTitles; }
    public List<String> firstNames() { return firstNames; }
    public List<String> lastNames() { return lastNames; }
    public String polityNameFormat() { return polityNameFormat; }
    public int color() { return color; }

    /** Returns true if {@code biomeId} matches this culture's biome list. Empty list = never matches (wildcard is handled elsewhere). */
    public boolean matchesBiome(ResourceLocation biomeId) {
        if (biomeId == null || biomes.isEmpty()) return false;
        return biomes.contains(biomeId);
    }

    /** Returns true if this culture is a wildcard fallback (empty biome list). */
    public boolean isWildcard() {
        return biomes.isEmpty();
    }

    /** Returns the tier label for the given subtree height, clamped to the last entry. */
    public String tierAt(int height) {
        if (tiers.isEmpty()) return "Settlement";
        return tiers.get(Math.min(height, tiers.size() - 1));
    }

    /** Returns the leader title for the given subtree height, clamped to the last entry. */
    public String leaderTitleAt(int height) {
        if (leaderTitles.isEmpty()) return "Leader";
        return leaderTitles.get(Math.min(height, leaderTitles.size() - 1));
    }
}
