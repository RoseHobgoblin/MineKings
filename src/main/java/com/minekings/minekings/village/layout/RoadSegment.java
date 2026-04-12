package com.minekings.minekings.village.layout;

import net.minecraft.core.BlockPos;

/**
 * A straight road segment between two points. Roads are not plots — they
 * have no economic role — but are stored on the village layout for
 * rendering and building-facing calculations.
 *
 * @param start start position (world coordinates)
 * @param end   end position (world coordinates)
 * @param width road width in blocks (3=main, 2=secondary, 1=tertiary)
 */
public record RoadSegment(BlockPos start, BlockPos end, int width) {

    /** Approximate length of this segment in blocks (horizontal only). */
    public double length() {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Returns the closest point on this segment to the given position
     * (in the XZ plane, ignoring Y).
     */
    public BlockPos closestPointXZ(BlockPos pos) {
        double ax = start.getX(), az = start.getZ();
        double bx = end.getX(), bz = end.getZ();
        double px = pos.getX(), pz = pos.getZ();
        double dx = bx - ax, dz = bz - az;
        double lenSq = dx * dx + dz * dz;
        if (lenSq == 0) return start;
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / lenSq));
        int cx = (int)(ax + t * dx);
        int cz = (int)(az + t * dz);
        return new BlockPos(cx, pos.getY(), cz);
    }
}
