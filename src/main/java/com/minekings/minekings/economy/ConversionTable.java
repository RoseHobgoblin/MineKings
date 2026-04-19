package com.minekings.minekings.economy;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minekings.minekings.MineKings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Datapack reload listener that loads item → (resource, per_item) mappings.
 * Used by the day tick to convert storehouse contents into village resource
 * stockpiles. Mod-agnostic by construction: any item registered in vanilla
 * or via another mod can be mapped via a simple JSON entry.
 *
 * <p>Loads from {@code data/minekings/economy/conversions/*.json}. Each JSON
 * file is a single object of {@code "<item_id>": {"resource": "...", "per_item": N}}
 * entries. Multiple files merge by key (later loads win on conflict).
 */
public class ConversionTable extends SimpleJsonResourceReloadListener {
    protected static final ResourceLocation ID = MineKings.locate("economy/conversions");
    private static ConversionTable INSTANCE = new ConversionTable();
    private final Map<ResourceLocation, ResourceConversion> conversions = new HashMap<>();

    public ConversionTable() {
        super(new Gson(), ID.getPath());
        INSTANCE = this;
    }

    public static ConversionTable getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        conversions.clear();
        int loaded = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject obj = entry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> itemEntry : obj.entrySet()) {
                try {
                    ResourceLocation itemId = ResourceLocation.parse(itemEntry.getKey());
                    JsonObject value = itemEntry.getValue().getAsJsonObject();
                    ResourceType res = ResourceType.parse(value.get("resource").getAsString());
                    double perItem = value.get("per_item").getAsDouble();
                    if (res == null) {
                        MineKings.LOGGER.warn("Unknown resource '{}' for item '{}' in conversion table", value.get("resource").getAsString(), itemId);
                        continue;
                    }
                    conversions.put(itemId, new ResourceConversion(res, perItem));
                    loaded++;
                } catch (Exception e) {
                    MineKings.LOGGER.error("Failed to parse conversion entry '{}': {}", itemEntry.getKey(), e.getMessage());
                }
            }
        }
        MineKings.LOGGER.info("Loaded {} resource conversions from {} file(s)", loaded, prepared.size());
    }

    /** Looks up a conversion for the given item. Returns null if no entry exists. */
    public ResourceConversion lookup(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return conversions.get(id);
    }

    public ResourceConversion lookup(ItemStack stack) {
        return lookup(stack.getItem());
    }

    public Map<ResourceLocation, ResourceConversion> all() {
        return conversions;
    }
}
