/*
 * Derived from Minecraft Comes Alive Reborn (https://github.com/Luke100000/minecraft-comes-alive)
 * Copyright (c) 2016-2024 The MCA Project contributors
 *
 * Licensed under the GNU General Public License v3.0.
 * See LICENSE in this project's root for full text.
 */
package com.minekings.minekings.village;

import com.minekings.minekings.MineKings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;

import java.util.List;

/**
 * Replacement for MCA Reborn's Residency.reportBuildings() feeder. MCA
 * scanned vanilla POIs from inside its own villager AI tick; MineKings
 * does not lift MCA's villagers, so instead we poll PoiManager around each
 * player on a 600-tick cadence and hand every new POI position to
 * VillageManager.reportBuilding(). Matches the cadence, radius, and filter
 * of MCA's Residency.java:149-165.
 *
 * Driven from {@link MineKingsEvents#onLevelTick}.
 */
public final class BuildingFeeder {
    public static final int POLL_INTERVAL_TICKS = 600; // 30s, matches MCA Residency cadence
    public static final int POLL_RADIUS_BLOCKS = 48;   // matches MCA Residency radius

    private BuildingFeeder() {}

    public static void poll(ServerLevel level) {
        if (level.players().isEmpty()) return;

        VillageManager manager = VillageManager.get(level);
        PoiManager poi = level.getPoiManager();

        int totalReported = 0;
        for (var player : level.players()) {
            List<net.minecraft.core.BlockPos> found = poi.findAll(
                    type -> true,
                    pos -> !manager.cache.contains(pos),
                    player.blockPosition(),
                    POLL_RADIUS_BLOCKS,
                    PoiManager.Occupancy.ANY
            ).toList();
            for (var pos : found) {
                manager.reportBuilding(pos);
            }
            totalReported += found.size();
        }
        if (totalReported > 0) {
            MineKings.LOGGER.info("BuildingFeeder: queued {} new POI candidates from {} player(s) in {}",
                    totalReported, level.players().size(), level.dimension().location());
        }
    }
}
