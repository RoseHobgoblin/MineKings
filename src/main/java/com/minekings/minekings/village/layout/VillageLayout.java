package com.minekings.minekings.village.layout;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Complete spatial plan for a village at a given development level.
 * Pure data — describes where everything goes, places nothing.
 *
 * @param plots            all building assignments
 * @param roads            connecting road segments
 * @param perimeterCenter  center of the wall perimeter (if any)
 * @param perimeterRadius  radius of the wall ring (0 = no walls)
 * @param gatePositions    positions where walls have road-aligned gaps
 * @param developmentLevel the level this layout was generated for (0-5)
 */
public record VillageLayout(
        List<PlotAssignment> plots,
        List<RoadSegment> roads,
        BlockPos perimeterCenter,
        int perimeterRadius,
        List<BlockPos> gatePositions,
        int developmentLevel
) {
    public VillageLayout {
        plots = List.copyOf(plots);
        roads = List.copyOf(roads);
        gatePositions = List.copyOf(gatePositions);
    }

    /** Total population estimate (2 per house, 1 per other occupied building). */
    public int estimatedPopulation() {
        int pop = 0;
        for (PlotAssignment p : plots) {
            if ("house".equals(p.buildingRole())) pop += 2;
            else pop += 1;
        }
        return pop;
    }
}
