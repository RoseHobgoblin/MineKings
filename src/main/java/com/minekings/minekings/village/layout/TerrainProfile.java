package com.minekings.minekings.village.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Immutable terrain analysis for a circular region around a village center.
 * Computed once at village founding / layout generation and cached.
 * <p>
 * The heightmap is sampled every {@code SAMPLE_STEP} blocks for performance.
 * All world-coordinate accessors account for the sampling offset.
 */
public final class TerrainProfile {

    /** World-space distance between heightmap samples. */
    public static final int SAMPLE_STEP = 2;

    private final BlockPos center;
    private final int radius;

    /** Heightmap sampled at SAMPLE_STEP intervals. [sampleX][sampleZ]. */
    private final int[][] heightmap;
    private final int originX; // world X of heightmap[0][0]
    private final int originZ; // world Z of heightmap[0][0]
    private final int samplesX;
    private final int samplesZ;

    private final BlockPos peak;
    private final List<BlockPos> flatZones;
    private final List<BlockPos> waterPositions;
    private final ResourceLocation biome;

    public TerrainProfile(BlockPos center, int radius,
                          int[][] heightmap, int originX, int originZ,
                          int samplesX, int samplesZ,
                          BlockPos peak,
                          List<BlockPos> flatZones,
                          List<BlockPos> waterPositions,
                          ResourceLocation biome) {
        this.center = center;
        this.radius = radius;
        this.heightmap = heightmap;
        this.originX = originX;
        this.originZ = originZ;
        this.samplesX = samplesX;
        this.samplesZ = samplesZ;
        this.peak = peak;
        this.flatZones = List.copyOf(flatZones);
        this.waterPositions = List.copyOf(waterPositions);
        this.biome = biome;
    }

    public BlockPos center() { return center; }
    public int radius() { return radius; }
    public BlockPos peak() { return peak; }
    public List<BlockPos> flatZones() { return flatZones; }
    public List<BlockPos> waterPositions() { return waterPositions; }
    public ResourceLocation biome() { return biome; }

    /**
     * Returns the sampled height at the given world coordinates,
     * or {@link Integer#MIN_VALUE} if out of bounds.
     */
    public int heightAt(int worldX, int worldZ) {
        int sx = (worldX - originX) / SAMPLE_STEP;
        int sz = (worldZ - originZ) / SAMPLE_STEP;
        if (sx < 0 || sx >= samplesX || sz < 0 || sz >= samplesZ) return Integer.MIN_VALUE;
        return heightmap[sx][sz];
    }

    /**
     * Returns the height at the nearest sample to the given position.
     */
    public int heightAtNearest(int worldX, int worldZ) {
        int sx = Math.round((float)(worldX - originX) / SAMPLE_STEP);
        int sz = Math.round((float)(worldZ - originZ) / SAMPLE_STEP);
        sx = Math.max(0, Math.min(sx, samplesX - 1));
        sz = Math.max(0, Math.min(sz, samplesZ - 1));
        return heightmap[sx][sz];
    }

    /**
     * Lowest point on the perimeter — candidate for the main gate / road start.
     */
    public BlockPos lowestPerimeterPoint() {
        BlockPos lowest = center;
        int lowestY = Integer.MAX_VALUE;
        // Walk the perimeter at sample resolution
        for (int angle = 0; angle < 360; angle += 5) {
            double rad = Math.toRadians(angle);
            int wx = center.getX() + (int)(Math.cos(rad) * radius);
            int wz = center.getZ() + (int)(Math.sin(rad) * radius);
            int h = heightAtNearest(wx, wz);
            if (h < lowestY) {
                lowestY = h;
                lowest = new BlockPos(wx, h, wz);
            }
        }
        return lowest;
    }
}
