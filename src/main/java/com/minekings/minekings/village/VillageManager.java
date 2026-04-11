/*
 * Derived from Minecraft Comes Alive Reborn (https://github.com/Luke100000/minecraft-comes-alive)
 * Copyright (c) 2016-2024 The MCA Project contributors
 *
 * Licensed under the GNU General Public License v3.0.
 * See LICENSE in this project's root for full text.
 */
package com.minekings.minekings.village;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.village.util.MKNbtHelper;
import com.minekings.minekings.village.util.MKWorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Server-side registry of detected villages, persisted as the SavedData
 * "minekings_villages" alongside the world. Lifted from MCA Reborn's
 * VillageManager with its bounty-hunter, reaper, spawn-queue, and
 * player-save-data coupling removed. The detection / persistence / merge
 * code is unchanged.
 */
public class VillageManager extends SavedData implements Iterable<Village> {
    public final Set<BlockPos> cache = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Village> villages = new HashMap<>();
    private final List<BlockPos> buildingQueue = new LinkedList<>();
    private final ServerLevel world;
    private int lastBuildingId;
    private int lastVillageId;
    private int buildingCooldown = 21;

    VillageManager(ServerLevel world) {
        this.world = world;
    }

    VillageManager(ServerLevel world, CompoundTag nbt) {
        this.world = world;
        lastBuildingId = nbt.getInt("lastBuildingId");
        lastVillageId = nbt.getInt("lastVillageId");

        ListTag villageList = nbt.getList("villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < villageList.size(); i++) {
            Village village = new Village(villageList.getCompound(i), world);
            if (village.getBuildings().isEmpty()) {
                MineKings.LOGGER.warn("Empty village detected ({}), removing...", village.getName());
                setDirty();
            } else {
                villages.put(village.getId(), village);
            }
        }
    }

    public static VillageManager get(ServerLevel world) {
        return MKWorldUtils.loadData(world, (nbt, provider) -> new VillageManager(world, nbt), VillageManager::new, "minekings_villages");
    }

    public Optional<Village> getOrEmpty(int id) {
        return Optional.ofNullable(villages.get(id));
    }

    public boolean removeVillage(int id) {
        if (villages.remove(id) != null) {
            cache.clear();
            return true;
        }
        return false;
    }

    @Override
    public Iterator<Village> iterator() {
        return villages.values().iterator();
    }

    public Stream<Village> findVillages(Predicate<Village> predicate) {
        return villages.values().stream().filter(predicate);
    }

    public Optional<Village> findNearestVillage(Entity entity) {
        BlockPos p = entity.blockPosition();
        return findVillages(v -> v.isWithinBorder(entity)).min((a, b) -> (int) (a.getCenter().distSqr(p) - b.getCenter().distSqr(p)));
    }

    public Optional<Village> findNearestVillage(BlockPos p, int margin) {
        return findVillages(v -> v.isWithinBorder(p, margin)).min((a, b) -> (int) (a.getCenter().distSqr(p) - b.getCenter().distSqr(p)));
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.putInt("lastBuildingId", lastBuildingId);
        nbt.putInt("lastVillageId", lastVillageId);
        nbt.put("villages", MKNbtHelper.fromList(villages.values(), Village::save));
        return nbt;
    }

    /**
     * Drains one queued building candidate per cooldown interval and feeds
     * it through the flood-fill detector. MineKings intentionally does not
     * run any per-village tick logic here at v0.1 — political simulation
     * will ride on its own day-tick loop at the polity layer.
     */
    public void tick() {
        long time = world.getGameTime();
        if (time % buildingCooldown == 0 && !buildingQueue.isEmpty()) {
            processBuilding(buildingQueue.removeFirst());
        }
    }

    //adds a potential block to the processing queue
    public void reportBuilding(BlockPos pos) {
        cache.add(pos);
        buildingQueue.add(pos);
    }

    public Building.validationResult processBuilding(BlockPos pos) {
        return processBuilding(pos, false, true);
    }

    //checks whether the given block contains a grouped building block, e.g., a town bell or gravestone
    private BuildingType getGroupedBuildingType(BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        for (BuildingType bt : BuildingTypes.getInstance()) {
            if (bt.grouped() && bt.getBlockToGroup().containsKey(BuiltInRegistries.BLOCK.getKey(block))) {
                return bt;
            }
        }
        return null;
    }

    //returns the scan-source blocks of all buildings, used to check for overlaps
    private Set<BlockPos> getBlockedSet(Village village) {
        return village.getBuildings().values().stream()
                .filter(b -> !b.getBuildingType().grouped())
                .map(Building::getSourceBlock)
                .collect(Collectors.toSet());
    }

    //processes a building at the given position
    public Building.validationResult processBuilding(BlockPos pos, boolean enforce, boolean strictScan) {
        //find the closest village
        Optional<Village> optionalVillage = findNearestVillage(pos, Village.MERGE_MARGIN);

        //check if this might be a grouped building
        BuildingType groupedBuildingType = getGroupedBuildingType(pos);

        //block existing buildings to prevent overlaps
        Set<BlockPos> blocked = new HashSet<>();

        //look for existing building
        boolean found = false;
        List<Integer> toRemove = new LinkedList<>();
        if (optionalVillage.isPresent()) {
            Village village = optionalVillage.get();

            blocked = getBlockedSet(village);
            if (groupedBuildingType != null) {
                String name = groupedBuildingType.name();
                double range = groupedBuildingType.mergeRange() * groupedBuildingType.mergeRange();

                //add POI to the nearest one
                Optional<Building> building = village.getBuildings().values().stream()
                        .filter(b -> b.getType().equals(name))
                        .min((a, b) -> (int) (a.getCenter().distSqr(pos) - b.getCenter().distSqr(pos)))
                        .filter(b -> b.getCenter().distSqr(pos) < range);

                if (building.isPresent()) {
                    found = true;
                    building.get().addPOI(world, pos);
                    setDirty();
                }
            } else {
                //verify affected buildings
                for (Building b : village.getBuildings().values()) {
                    if (b.containsPos(pos)) {
                        if (!enforce) {
                            found = true;
                        }
                        if ((enforce || world.getGameTime() - b.getLastScan() > Building.SCAN_COOLDOWN) && b.validateBuilding(world, blocked) != Building.validationResult.SUCCESS) {
                            toRemove.add(b.getId());
                        }
                    }
                }
            }

            //remove buildings, which became invalid for whatever reason
            for (int id : toRemove) {
                village.removeBuilding(id);
                setDirty();
            }

            //village is empty
            if (village.getBuildings().isEmpty()) {
                villages.remove(village.getId());
                optionalVillage = Optional.empty();
                setDirty();
            }
        }

        //add a new building, if no overlap has been found or the player enforced a full add
        if (!found && !blocked.contains(pos)) {
            //create new village
            Village village = optionalVillage.orElse(new Village(lastVillageId++, world));

            //create new building
            Building building = new Building(pos, strictScan);
            if (groupedBuildingType != null) {
                //add initial poi
                building.setType(groupedBuildingType.name());
                building.addPOI(world, pos);
            } else {
                //check its boundaries, count the blocks, etc
                Building.validationResult result = building.validateBuilding(world, blocked);
                if (result == Building.validationResult.SUCCESS) {
                    //the building is valid, but might be identical to an old one with an existing one
                    if (village.getBuildings().values().stream().anyMatch(b -> b.isIdentical(building))) {
                        return Building.validationResult.IDENTICAL;
                    }
                } else {
                    //not valid
                    return result;
                }
            }

            //add to building list
            villages.put(village.getId(), village);
            building.setId(lastBuildingId++);
            village.getBuildings().put(building.getId(), building);
            village.calculateDimensions();

            //attempt to merge
            villages.values().stream()
                    .filter(v -> v != village)
                    .filter(v -> v.getBox().inflatedBy(Village.MERGE_MARGIN).intersects(village.getBox()))
                    .findAny()
                    .ifPresent(v -> {
                                if (v.getBuildings().size() > village.getBuildings().size()) {
                                    merge(v, village);
                                    villages.remove(village.getId());
                                } else {
                                    merge(village, v);
                                    villages.remove(v.getId());
                                }
                            }
                    );

            setDirty();
        }

        return Building.validationResult.SUCCESS;
    }

    public void setBuildingCooldown(int buildingCooldown) {
        this.buildingCooldown = buildingCooldown;
    }

    public void merge(Village into, Village from) {
        into.merge(from);
    }
}
