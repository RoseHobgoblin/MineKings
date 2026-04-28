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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private boolean founded = false;      // true if created by player/worldgen (not flood-fill)
    // Start-chunk anchor for worldgen-founded villages. Stable across reloads
    // even when the jigsaw structure's bounding box grows as pieces are placed,
    // so it's the correct dedup key on ChunkEvent.Load. Null for player-founded
    // villages (Founding Stone) which have no structure start.
    private ChunkPos startChunkPos = null;
    // === Economic state (v0.5) ===
    private int population = 1;             // current inhabitants
    private long food = 0L;                  // food stockpile
    private long materials = 0L;             // wood/stone/iron aggregate
    private long gold = 0L;                  // absorbs the old `stockpile` on migration
    private final Set<String> attributes = new HashSet<>(); // derived flags like "has_smithy"
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
        this.world = world;

        // --- Economy fields (migrate old `stockpile` into `gold` if present) ---
        if (v.contains("gold")) {
            this.gold = v.getLong("gold");
        } else if (v.contains("stockpile")) {
            this.gold = v.getLong("stockpile");  // migration
        }
        this.food = v.contains("food") ? v.getLong("food") : 0L;
        this.materials = v.contains("materials") ? v.getLong("materials") : 0L;
        this.population = v.contains("population") ? v.getInt("population") : 1;
        if (v.contains("attributes")) {
            ListTag attrList = v.getList("attributes", Tag.TAG_STRING);
            for (int i = 0; i < attrList.size(); i++) {
                attributes.add(attrList.getString(i));
            }
        }

        if (v.contains("autoScan")) {
            this.autoScan = v.getBoolean("autoScan");
        } else {
            this.autoScan = false;
        }
        this.founded = v.getBoolean("founded");
        if (v.contains("startChunkX") && v.contains("startChunkZ")) {
            this.startChunkPos = new ChunkPos(v.getInt("startChunkX"), v.getInt("startChunkZ"));
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
        } else if (v.contains("box")) {
            // Worldgen/Founding-Stone villages have no Building records —
            // their bounds come from seedBounds(), so we must persist them
            // explicitly. Without this, post-reload box = (0,0,0,0,0,0),
            // which breaks marker queries and border checks.
            int[] bx = v.getIntArray("box");
            if (bx.length == 6) {
                this.box = new BoundingBox(bx[0], bx[1], bx[2], bx[3], bx[4], bx[5]);
            }
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
        return population;
    }

    public void setPopulation(int population) {
        this.population = Math.max(0, population);
    }

    public void addPopulation(int delta) {
        setPopulation(this.population + delta);
    }

    public boolean isPositionValidBed(BlockPos pos) {
        return true; // v0.6: no flood-fill, every bed inside bounds counts
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
        v.putInt("population", population);
        v.putLong("food", food);
        v.putLong("materials", materials);
        v.putLong("gold", gold);
        ListTag attrList = new ListTag();
        for (String a : attributes) attrList.add(StringTag.valueOf(a));
        v.put("attributes", attrList);
        v.put("buildings", MKNbtHelper.fromList(buildings.values(), Building::save));
        v.putBoolean("autoScan", autoScan);
        v.putBoolean("founded", founded);
        if (startChunkPos != null) {
            v.putInt("startChunkX", startChunkPos.x);
            v.putInt("startChunkZ", startChunkPos.z);
        }
        v.putIntArray("box", new int[] {
                box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ()
        });
        return v;
    }

    public boolean isFounded() { return founded; }
    public void setFounded(boolean founded) { this.founded = founded; markDirty(); }

    public ChunkPos getStartChunkPos() { return startChunkPos; }
    public void setStartChunkPos(ChunkPos pos) { this.startChunkPos = pos; markDirty(); }

    // === Resource stockpiles (v0.5 multi-resource economy) ===
    public long getFood() { return food; }
    public void setFood(long food) { this.food = Math.max(0L, food); }
    public void addFood(long delta) { setFood(this.food + delta); }

    public long getMaterials() { return materials; }
    public void setMaterials(long materials) { this.materials = Math.max(0L, materials); }
    public void addMaterials(long delta) { setMaterials(this.materials + delta); }

    public long getGold() { return gold; }
    public void setGold(long gold) { this.gold = Math.max(0L, gold); }
    public void addGold(long delta) { setGold(this.gold + delta); }

    // === Attributes (derived flags like "has_smithy") ===
    public Set<String> getAttributes() { return Collections.unmodifiableSet(attributes); }
    public boolean hasAttribute(String attr) { return attributes.contains(attr); }

    /** Replaces the attribute set. Called from {@link VillageAttributes#refresh}. */
    public void setAttributes(Set<String> newAttributes) {
        attributes.clear();
        attributes.addAll(newAttributes);
    }

    public void merge(Village village) {
        buildings.putAll(village.buildings);
        calculateDimensions();
    }

    /**
     * Atomic bulk replace used by the per-village block-scanner. Clears the
     * existing building set and rewrites from {@code scanned}. Recomputes
     * dimensions only if the scan produced at least one building, so an
     * empty scan preserves the seeded bounds rather than collapsing the
     * village box to the origin.
     */
    public void replaceBuildings(java.util.Collection<Building> scanned) {
        buildings.clear();
        for (Building b : scanned) buildings.put(b.getId(), b);
        if (!buildings.isEmpty()) calculateDimensions();
        markDirty();
    }

    /** Monotonic next id the scanner uses when allocating rebuilt Buildings. */
    public int nextBuildingId() {
        int max = 0;
        for (Integer id : buildings.keySet()) if (id > max) max = id;
        return max + 1;
    }

    /**
     * Sets an initial bounding box centered on {@code center} with the
     * given radius. Used by player- or worldgen-founded villages that
     * have no buildings yet — the box would otherwise be degenerate at
     * origin (0,0,0) because {@link #calculateDimensions()} requires
     * buildings to compute a box.
     */
    public void seedBounds(BlockPos center, int radius) {
        this.box = new BoundingBox(
                center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                center.getX() + radius, center.getY() + radius, center.getZ() + radius
        );
        markDirty();
    }
}
