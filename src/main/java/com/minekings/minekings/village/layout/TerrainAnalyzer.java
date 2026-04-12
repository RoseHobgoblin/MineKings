package com.minekings.minekings.village.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes terrain in a circular region to produce a {@link TerrainProfile}.
 * All sampling uses the motion-blocking heightmap for solid-surface detection.
 */
public final class TerrainAnalyzer {
    private TerrainAnalyzer() {}

    /** Default analysis radius in blocks. */
    public static final int DEFAULT_RADIUS = 64;

    /**
     * Analyzes terrain around {@code center} within {@code radius} blocks.
     *
     * @return an immutable {@link TerrainProfile} describing the terrain
     */
    public static TerrainProfile analyze(ServerLevel level, BlockPos center, int radius) {
        int step = TerrainProfile.SAMPLE_STEP;
        int originX = center.getX() - radius;
        int originZ = center.getZ() - radius;
        int diameter = radius * 2;
        int samplesX = diameter / step + 1;
        int samplesZ = diameter / step + 1;

        int[][] heightmap = new int[samplesX][samplesZ];
        BlockPos peak = center;
        int peakY = Integer.MIN_VALUE;

        // === Pass 1: sample heightmap and find peak ===
        for (int sx = 0; sx < samplesX; sx++) {
            for (int sz = 0; sz < samplesZ; sz++) {
                int wx = originX + sx * step;
                int wz = originZ + sz * step;
                // MOTION_BLOCKING gives the top non-air block Y + 1; subtract 1 for the surface
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
                heightmap[sx][sz] = surfaceY;

                // Only consider points within the circular radius for peak
                int dx = wx - center.getX();
                int dz = wz - center.getZ();
                if (dx * dx + dz * dz <= radius * radius && surfaceY > peakY) {
                    peakY = surfaceY;
                    peak = new BlockPos(wx, surfaceY, wz);
                }
            }
        }

        // === Pass 2: find flat zones (height variance < 2 over 8x8 block area = 4x4 samples) ===
        List<BlockPos> flatZones = new ArrayList<>();
        int flatWindow = 4; // 4 samples = 8 blocks
        for (int sx = 0; sx <= samplesX - flatWindow; sx++) {
            for (int sz = 0; sz <= samplesZ - flatWindow; sz++) {
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                int sum = 0;
                for (int dx = 0; dx < flatWindow; dx++) {
                    for (int dz = 0; dz < flatWindow; dz++) {
                        int h = heightmap[sx + dx][sz + dz];
                        if (h < min) min = h;
                        if (h > max) max = h;
                        sum += h;
                    }
                }
                if (max - min < 2) {
                    int avgY = sum / (flatWindow * flatWindow);
                    int wx = originX + (sx + flatWindow / 2) * step;
                    int wz = originZ + (sz + flatWindow / 2) * step;
                    // Only include if within the radius circle
                    int ddx = wx - center.getX();
                    int ddz = wz - center.getZ();
                    if (ddx * ddx + ddz * ddz <= radius * radius) {
                        flatZones.add(new BlockPos(wx, avgY, wz));
                    }
                }
            }
        }

        // === Pass 3: scan for water within radius ===
        List<BlockPos> waterPositions = new ArrayList<>();
        // Sample water at a coarser grid (every 4 blocks) to keep it fast
        for (int sx = 0; sx < samplesX; sx += 2) {
            for (int sz = 0; sz < samplesZ; sz += 2) {
                int wx = originX + sx * step;
                int wz = originZ + sz * step;
                int dx = wx - center.getX();
                int dz = wz - center.getZ();
                if (dx * dx + dz * dz > radius * radius) continue;

                int surfaceY = heightmap[sx][sz];
                BlockState state = level.getBlockState(new BlockPos(wx, surfaceY, wz));
                if (state.is(Blocks.WATER) || state.is(BlockTags.ICE)) {
                    waterPositions.add(new BlockPos(wx, surfaceY, wz));
                }
            }
        }

        // === Biome at center ===
        ResourceLocation biome = level.getBiome(center)
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);

        return new TerrainProfile(
                center, radius,
                heightmap, originX, originZ, samplesX, samplesZ,
                peak, flatZones, waterPositions, biome
        );
    }
}
