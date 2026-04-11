/*
 * Derived from Minecraft Comes Alive Reborn (https://github.com/Luke100000/minecraft-comes-alive)
 * Copyright (c) 2016-2024 The MCA Project contributors
 *
 * Licensed under the GNU General Public License v3.0.
 * See LICENSE in this project's root for full text.
 */
package com.minekings.minekings.village;

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

public class BuildingTypes extends SimpleJsonResourceReloadListener implements Iterable<BuildingType> {
    protected static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MineKings.MODID, "building_types");
    private static BuildingTypes INSTANCE = new BuildingTypes();
    private final Map<String, BuildingType> buildingTypes = new HashMap<>();
    private final Map<String, BuildingType> buildingTypesClient = new HashMap<>();

    public BuildingTypes() {
        super(new Gson(), ID.getPath());
        INSTANCE = this;
    }

    public static BuildingTypes getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        for (Map.Entry<ResourceLocation, JsonElement> pair : prepared.entrySet()) {
            String name = pair.getKey().getPath();
            buildingTypes.put(name, new BuildingType(name, pair.getValue().getAsJsonObject()));
        }
        setBuildingTypes(buildingTypes);
        MineKings.LOGGER.info("Loaded {} building types", buildingTypes.size());
    }

    public Map<String, BuildingType> getServerBuildingTypes() {
        return buildingTypes;
    }

    public Map<String, BuildingType> getBuildingTypes() {
        return buildingTypesClient;
    }

    // Provide the client with building types
    public void setBuildingTypes(Map<String, BuildingType> buildingTypes) {
        buildingTypesClient.clear();
        buildingTypesClient.putAll(buildingTypes);
    }

    public BuildingType getBuildingType(String type) {
        return buildingTypesClient.containsKey(type) ? buildingTypesClient.get(type) : new BuildingType();
    }

    @Override
    public Iterator<BuildingType> iterator() {
        return buildingTypesClient.values().iterator();
    }
}
