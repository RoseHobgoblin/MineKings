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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Server-side registry of villages, persisted as the SavedData
 * "minekings_villages" alongside the world.
 *
 * <p>v0.6 replaced the MCA-lifted flood-fill detection with explicit
 * founding: villages come into existence via {@link #register(BlockPos, String)}
 * (player-placed Founding Stone or worldgen-placed template). The old
 * processBuilding / BuildingFeeder / POI polling machinery is gone.
 */
public class VillageManager extends SavedData implements Iterable<Village> {
    private final Map<Integer, Village> villages = new HashMap<>();
    private final ServerLevel world;
    private int lastVillageId;

    VillageManager(ServerLevel world) {
        this.world = world;
    }

    VillageManager(ServerLevel world, CompoundTag nbt) {
        this.world = world;
        lastVillageId = nbt.getInt("lastVillageId");

        ListTag villageList = nbt.getList("villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < villageList.size(); i++) {
            Village village = new Village(villageList.getCompound(i), world);
            if (village.getBuildings().isEmpty() && !village.isFounded()) {
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
        return villages.remove(id) != null;
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
        nbt.putInt("lastVillageId", lastVillageId);
        nbt.put("villages", MKNbtHelper.fromList(villages.values(), Village::save));
        return nbt;
    }

    /**
     * Player- or worldgen-driven founding. Creates a new village centered
     * on {@code center} with a fixed seed bounding box (initial claim radius).
     * Idempotent at the caller level — existing village detection is the
     * caller's responsibility.
     *
     * @return the newly registered Village
     */
    public Village register(BlockPos center, String name) {
        Village village = new Village(lastVillageId++, world);
        if (name != null && !name.isBlank()) {
            village.setName(name);
        }
        village.seedBounds(center, 48);
        village.setFounded(true);
        // Starter stockpile + population so new villages feel lived-in
        // immediately. Food at 5× consumption buys ~5 days of survival
        // before starvation, giving the player / NPC polity time to set
        // up production.
        village.setPopulation(10);
        village.addFood(50);
        village.addMaterials(20);
        villages.put(village.getId(), village);
        setDirty();
        return village;
    }
}
