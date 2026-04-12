/*
 * Derived from Minecraft Comes Alive Reborn (https://github.com/Luke100000/minecraft-comes-alive)
 * Copyright (c) 2016-2024 The MCA Project contributors
 *
 * Licensed under the GNU General Public License v3.0.
 * See LICENSE in this project's root for full text.
 */
package com.minekings.minekings.village;

import com.minekings.minekings.village.util.MKNbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Spatial/economic unit. A village is a set of detected buildings plus a
 * bounding box and a name. MineKings adds the political layer (polities,
 * leaders, allegiances) on top of this — villages are intentionally apolitical.
 *
 * Lifted from MCA Reborn's Village class and stripped of villager/marriage/
 * procreation/taxes/reputation/bounty-hunter code paths. MCA's reputation map
 * tracked player→villager hearts; MineKings will model character↔character
 * opinions at the polity layer instead, so the old reputation shape is not
 * preserved.
 */
public class Village implements Iterable<Building> {
    public static final int PLAYER_BORDER_MARGIN = 32;
    public static final int BORDER_MARGIN = 48;
    public static final int MERGE_MARGIN = 64;
    private static final long BED_SYNC_TIME = 200;

    private final ServerLevel world;
    private final Map<Integer, Building> buildings = new HashMap<>();
    private final int id;

    private String name;
    private int beds;
    private long lastBedSync;
    private boolean autoScan = false;
    private long stockpile = 0L;
    private BoundingBox box = new BoundingBox(0, 0, 0, 0, 0, 0);

    public Village(int id, ServerLevel world) {
        this.id = id;
        this.world = world;
        this.name = MKNames.pickVillageName(world.random);
    }

    public Village(CompoundTag v, ServerLevel world) {
        this.id = v.getInt("id");
        this.name = v.getString("name");
        this.beds = v.getInt("beds");
        this.stockpile = v.contains("stockpile") ? v.getLong("stockpile") : 0L;
        this.world = world;
        if (v.contains("autoScan")) {
            this.autoScan = v.getBoolean("autoScan");
        } else {
            this.autoScan = false;
        }

        ListTag b = v.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < b.size(); i++) {
            Building building = new Building(b.getCompound(i));
            if (world == null || BuildingTypes.getInstance().getBuildingTypes().containsKey(building.getType())) {
                buildings.put(building.getId(), building);
            }
        }

        if (!buildings.isEmpty()) {
            calculateDimensions();
        }
    }

    public static Optional<Village> findNearest(Entity entity) {
        return VillageManager.get((ServerLevel) entity.level()).findNearestVillage(entity);
    }

    public boolean isWithinBorder(Entity entity) {
        return isWithinBorder(entity.blockPosition(), entity instanceof Player ? PLAYER_BORDER_MARGIN : BORDER_MARGIN);
    }

    public boolean isWithinBorder(BlockPos pos, int margin) {
        return box.inflatedBy(margin).isInside(pos);
    }

    @Override
    public Iterator<Building> iterator() {
        return buildings.values().iterator();
    }

    public void removeBuilding(int id) {
        buildings.remove(id);
        if (!buildings.isEmpty()) {
            calculateDimensions();
        }
        markDirty();
    }

    public Stream<Building> getBuildingsOfType(String type) {
        return getBuildings().values().stream().filter(b -> b.getType().equals(type));
    }

    public Optional<Building> getBuildingAt(Vec3i pos) {
        return getBuildings().values().stream().filter(b -> b.containsPos(pos)).findAny();
    }

    public void calculateDimensions() {
        int sx = Integer.MAX_VALUE;
        int sy = Integer.MAX_VALUE;
        int sz = Integer.MAX_VALUE;
        int ex = Integer.MIN_VALUE;
        int ey = Integer.MIN_VALUE;
        int ez = Integer.MIN_VALUE;

        for (Building building : buildings.values()) {
            ex = Math.max(building.getPos1().getX(), ex);
            sx = Math.min(building.getPos0().getX(), sx);

            ey = Math.max(building.getPos1().getY(), ey);
            sy = Math.min(building.getPos0().getY(), sy);

            ez = Math.max(building.getPos1().getZ(), ez);
            sz = Math.min(building.getPos0().getZ(), sz);
        }

        box = new BoundingBox(sx, sy, sz, ex, ey, ez);
    }

    public Vec3i getCenter() {
        return box.getCenter();
    }

    public BoundingBox getBox() {
        return box;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        markDirty();
    }

    public Map<Integer, Building> getBuildings() {
        return buildings;
    }

    public Optional<Building> getBuilding(int id) {
        return Optional.ofNullable(buildings.get(id));
    }

    public int getId() {
        return id;
    }

    public boolean hasSpace() {
        return getPopulation() < getMaxPopulation();
    }

    public int getPopulation() {
        // Population tracking is deferred until the character layer lands.
        // For now treat every village as having zero residents.
        return 0;
    }

    public boolean isPositionValidBed(BlockPos pos) {
        return getBuildingAt(pos).filter(b -> b.getBuildingType().noBeds()).isEmpty();
    }

    public boolean hasBuilding(String building) {
        return buildings.values().stream().anyMatch(b -> b.getType().equals(building) && b.isComplete());
    }

    public boolean isAutoScan() {
        return autoScan;
    }

    public void setAutoScan(boolean autoScan) {
        this.autoScan = autoScan;
        markDirty();
    }

    public void updateMaxPopulation() {
        if (world != null) {
            Vec3i dimensions = box.getLength();
            int radius = (int) Math.sqrt(dimensions.getX() * dimensions.getX() + dimensions.getY() * dimensions.getY() + dimensions.getZ() * dimensions.getZ());
            beds = (int) world.getPoiManager().findAll(
                    registryEntry -> registryEntry.is(PoiTypes.HOME),
                    this::isPositionValidBed,
                    new BlockPos(getCenter()),
                    radius + BORDER_MARGIN,
                    PoiManager.Occupancy.ANY).count();
        }
    }

    public int getMaxPopulation() {
        if (world != null && world.getGameTime() - lastBedSync > BED_SYNC_TIME) {
            lastBedSync = world.getGameTime();
            updateMaxPopulation();
        }
        return beds;
    }

    public void markDirty() {
        VillageManager.get(world).setDirty();
    }

    public CompoundTag save() {
        CompoundTag v = new CompoundTag();
        v.putInt("id", id);
        v.putString("name", name);
        v.putInt("beds", beds);
        v.putLong("stockpile", stockpile);
        v.put("buildings", MKNbtHelper.fromList(buildings.values(), Building::save));
        v.putBoolean("autoScan", autoScan);
        return v;
    }

    public long getStockpile() { return stockpile; }
    public void setStockpile(long stockpile) { this.stockpile = stockpile; }
    public void addStockpile(long delta) { this.stockpile += delta; }

    public void merge(Village village) {
        buildings.putAll(village.buildings);
        calculateDimensions();
    }
}
