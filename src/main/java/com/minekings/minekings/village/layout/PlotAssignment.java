package com.minekings.minekings.village.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A planned building placement within a {@link VillageLayout}. Pure data —
 * no blocks are placed. Phase 2 converts these into physical structures.
 *
 * @param plotId         unique ID within this layout
 * @param origin         bottom-corner block position
 * @param facing         direction the building faces (toward nearest road)
 * @param buildingRole   placement role: "manor", "house", "farm", "workshop", "market", "civic"
 * @param tier           building tier (0 = basic)
 * @param footprintX     east-west footprint in blocks
 * @param footprintZ     north-south footprint in blocks
 */
public record PlotAssignment(
        int plotId,
        BlockPos origin,
        Direction facing,
        String buildingRole,
        int tier,
        int footprintX,
        int footprintZ
) {
    /** Center of the footprint at origin Y. */
    public BlockPos center() {
        return origin.offset(footprintX / 2, 0, footprintZ / 2);
    }

    /** Squared XZ distance between this plot's center and another position. */
    public double distSqXZ(BlockPos other) {
        BlockPos c = center();
        double dx = c.getX() - other.getX();
        double dz = c.getZ() - other.getZ();
        return dx * dx + dz * dz;
    }
}
