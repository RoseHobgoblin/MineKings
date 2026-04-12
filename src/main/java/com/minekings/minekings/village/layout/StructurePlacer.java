package com.minekings.minekings.village.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Takes a {@link VillageLayout} and places it physically in the world.
 * <p>
 * Phase 2 uses colored wool placeholder buildings:
 * <ul>
 *   <li>Red = manor</li>
 *   <li>White = house</li>
 *   <li>Blue = workshop</li>
 *   <li>Yellow = farm</li>
 *   <li>Light blue = civic</li>
 *   <li>Magenta = market</li>
 * </ul>
 * Roads are placed as {@code dirt_path} blocks at ground level.
 */
public final class StructurePlacer {
    private StructurePlacer() {}

    /** Wall height for placeholder buildings. */
    private static final int WALL_HEIGHT = 4;
    /** Max height difference before retaining walls are placed. */
    private static final int RETAINING_THRESHOLD = 2;

    /**
     * Places an entire village layout in the world.
     */
    public static void place(ServerLevel level, VillageLayout layout, TerrainProfile profile) {
        // 1. Level terrain and place buildings
        for (PlotAssignment plot : layout.plots()) {
            int targetY = computeMedianY(level, plot);
            levelTerrain(level, plot, targetY);
            placeBuilding(level, plot, targetY);
        }

        // 2. Place roads
        for (RoadSegment road : layout.roads()) {
            placeRoad(level, road);
        }
    }

    // ── Terrain Leveling ──────────────────────────────────────────────

    /**
     * Computes the median surface Y across all columns in the plot footprint.
     */
    private static int computeMedianY(ServerLevel level, PlotAssignment plot) {
        List<Integer> heights = new ArrayList<>();
        BlockPos origin = plot.origin();
        for (int dx = 0; dx < plot.footprintX(); dx++) {
            for (int dz = 0; dz < plot.footprintZ(); dz++) {
                int wx = origin.getX() + dx;
                int wz = origin.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
                heights.add(y);
            }
        }
        Collections.sort(heights);
        return heights.get(heights.size() / 2);
    }

    /**
     * Levels terrain under a plot footprint to the target Y.
     * Fills below with dirt, clears above, places retaining walls on
     * exposed edges where the cut/fill exceeds the threshold.
     */
    private static void levelTerrain(ServerLevel level, PlotAssignment plot, int targetY) {
        BlockPos origin = plot.origin();
        int fpX = plot.footprintX();
        int fpZ = plot.footprintZ();

        for (int dx = 0; dx < fpX; dx++) {
            for (int dz = 0; dz < fpZ; dz++) {
                int wx = origin.getX() + dx;
                int wz = origin.getZ() + dz;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;

                if (surfaceY < targetY) {
                    // Fill up to target
                    for (int y = surfaceY + 1; y <= targetY; y++) {
                        BlockState fill = (y == targetY) ? Blocks.GRASS_BLOCK.defaultBlockState()
                                : Blocks.DIRT.defaultBlockState();
                        level.setBlockAndUpdate(new BlockPos(wx, y, wz), fill);
                    }
                } else if (surfaceY > targetY) {
                    // Cut down to target
                    for (int y = targetY + 1; y <= surfaceY + 2; y++) {
                        level.setBlockAndUpdate(new BlockPos(wx, y, wz), Blocks.AIR.defaultBlockState());
                    }
                    // Ensure top is grass
                    level.setBlockAndUpdate(new BlockPos(wx, targetY, wz),
                            Blocks.GRASS_BLOCK.defaultBlockState());
                }
            }
        }

        // Retaining walls on edges where cut/fill > threshold
        placeRetainingWalls(level, origin, fpX, fpZ, targetY);
    }

    /**
     * Places stone brick retaining walls on the perimeter of a leveled
     * plot where the surrounding terrain differs by more than the threshold.
     */
    private static void placeRetainingWalls(ServerLevel level, BlockPos origin,
                                             int fpX, int fpZ, int targetY) {
        // Check all four edges
        for (int i = 0; i < fpX; i++) {
            checkRetaining(level, origin.getX() + i, origin.getZ() - 1, targetY);      // north edge
            checkRetaining(level, origin.getX() + i, origin.getZ() + fpZ, targetY);    // south edge
        }
        for (int i = 0; i < fpZ; i++) {
            checkRetaining(level, origin.getX() - 1, origin.getZ() + i, targetY);      // west edge
            checkRetaining(level, origin.getX() + fpX, origin.getZ() + i, targetY);    // east edge
        }
    }

    private static void checkRetaining(ServerLevel level, int wx, int wz, int targetY) {
        int neighborY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
        int diff = targetY - neighborY;
        if (diff > RETAINING_THRESHOLD) {
            // Platform is higher than neighbor — place retaining wall
            for (int y = neighborY + 1; y <= targetY; y++) {
                level.setBlockAndUpdate(new BlockPos(wx, y, wz),
                        Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
    }

    // ── Placeholder Buildings ─────────────────────────────────────────

    private static void placeBuilding(ServerLevel level, PlotAssignment plot, int targetY) {
        if ("farm".equals(plot.buildingRole())) {
            placeFarm(level, plot, targetY);
        } else {
            placeWoolBuilding(level, plot, targetY);
        }
    }

    /**
     * Places a colored wool box with a door, floor, and open interior.
     */
    private static void placeWoolBuilding(ServerLevel level, PlotAssignment plot, int targetY) {
        BlockState wallBlock = getWoolForRole(plot.buildingRole());
        BlockPos origin = plot.origin();
        int fpX = plot.footprintX();
        int fpZ = plot.footprintZ();
        int floorY = targetY + 1;

        // Floor
        for (int dx = 0; dx < fpX; dx++) {
            for (int dz = 0; dz < fpZ; dz++) {
                level.setBlockAndUpdate(new BlockPos(origin.getX() + dx, floorY, origin.getZ() + dz),
                        Blocks.OAK_PLANKS.defaultBlockState());
            }
        }

        // Walls + clear interior
        for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
            for (int dx = 0; dx < fpX; dx++) {
                for (int dz = 0; dz < fpZ; dz++) {
                    BlockPos pos = new BlockPos(origin.getX() + dx, floorY + dy, origin.getZ() + dz);
                    boolean isEdge = dx == 0 || dx == fpX - 1 || dz == 0 || dz == fpZ - 1;
                    if (isEdge) {
                        level.setBlockAndUpdate(pos, wallBlock);
                    } else {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        // Roof (oak slabs on top)
        for (int dx = 0; dx < fpX; dx++) {
            for (int dz = 0; dz < fpZ; dz++) {
                level.setBlockAndUpdate(new BlockPos(origin.getX() + dx, floorY + WALL_HEIGHT + 1,
                        origin.getZ() + dz), Blocks.OAK_SLAB.defaultBlockState());
            }
        }

        // Clear above roof
        for (int dx = 0; dx < fpX; dx++) {
            for (int dz = 0; dz < fpZ; dz++) {
                for (int dy = WALL_HEIGHT + 2; dy <= WALL_HEIGHT + 4; dy++) {
                    level.setBlockAndUpdate(new BlockPos(origin.getX() + dx, floorY + dy,
                            origin.getZ() + dz), Blocks.AIR.defaultBlockState());
                }
            }
        }

        // Door — 2-block opening on the facing side
        placeDoor(level, plot, floorY);

        // Torch inside for lighting
        level.setBlockAndUpdate(new BlockPos(origin.getX() + fpX / 2, floorY + 2,
                origin.getZ() + fpZ / 2), Blocks.TORCH.defaultBlockState());
    }

    /**
     * Places a door opening on the plot's facing wall.
     */
    private static void placeDoor(ServerLevel level, PlotAssignment plot, int floorY) {
        BlockPos origin = plot.origin();
        int fpX = plot.footprintX();
        int fpZ = plot.footprintZ();
        int midX = origin.getX() + fpX / 2;
        int midZ = origin.getZ() + fpZ / 2;

        // Find door position on the facing wall
        BlockPos doorBottom;
        switch (plot.facing()) {
            case NORTH -> doorBottom = new BlockPos(midX, floorY + 1, origin.getZ());
            case SOUTH -> doorBottom = new BlockPos(midX, floorY + 1, origin.getZ() + fpZ - 1);
            case WEST  -> doorBottom = new BlockPos(origin.getX(), floorY + 1, midZ);
            case EAST  -> doorBottom = new BlockPos(origin.getX() + fpX - 1, floorY + 1, midZ);
            default    -> doorBottom = new BlockPos(midX, floorY + 1, origin.getZ());
        }

        // Clear door opening (2 high)
        level.setBlockAndUpdate(doorBottom, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(doorBottom.above(), Blocks.AIR.defaultBlockState());
    }

    /**
     * Places a farm: farmland with water channels, crops, and a fence border.
     */
    private static void placeFarm(ServerLevel level, PlotAssignment plot, int targetY) {
        BlockPos origin = plot.origin();
        int fpX = plot.footprintX();
        int fpZ = plot.footprintZ();
        int floorY = targetY;

        for (int dx = 0; dx < fpX; dx++) {
            for (int dz = 0; dz < fpZ; dz++) {
                BlockPos pos = new BlockPos(origin.getX() + dx, floorY, origin.getZ() + dz);
                boolean isEdge = dx == 0 || dx == fpX - 1 || dz == 0 || dz == fpZ - 1;
                boolean isWaterChannel = (dx == fpX / 2) || (dz == fpZ / 2);

                if (isEdge) {
                    // Fence border
                    level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                    level.setBlockAndUpdate(pos.above(), Blocks.OAK_FENCE.defaultBlockState());
                } else if (isWaterChannel) {
                    // Water irrigation
                    level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
                } else {
                    // Farmland + wheat
                    level.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
                    level.setBlockAndUpdate(pos.above(), Blocks.WHEAT.defaultBlockState());
                }

                // Clear air above
                for (int dy = 2; dy <= 4; dy++) {
                    level.setBlockAndUpdate(pos.above(dy), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /**
     * Returns the wool color for a given building role.
     */
    private static BlockState getWoolForRole(String role) {
        return switch (role) {
            case "manor"    -> Blocks.RED_WOOL.defaultBlockState();
            case "house"    -> Blocks.WHITE_WOOL.defaultBlockState();
            case "workshop" -> Blocks.BLUE_WOOL.defaultBlockState();
            case "civic"    -> Blocks.LIGHT_BLUE_WOOL.defaultBlockState();
            case "market"   -> Blocks.MAGENTA_WOOL.defaultBlockState();
            default         -> Blocks.GRAY_WOOL.defaultBlockState();
        };
    }

    // ── Road Placement ────────────────────────────────────────────────

    /**
     * Places a road segment as dirt_path blocks, tracing a line from
     * start to end at the given width.
     */
    private static void placeRoad(ServerLevel level, RoadSegment road) {
        double dx = road.end().getX() - road.start().getX();
        double dz = road.end().getZ() - road.start().getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1) return;

        // Unit direction and perpendicular
        double ux = dx / len, uz = dz / len;
        double px = -uz, pz = ux; // perpendicular

        int halfWidth = road.width() / 2;
        int steps = (int) Math.ceil(len);

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double cx = road.start().getX() + dx * t;
            double cz = road.start().getZ() + dz * t;

            for (int w = -halfWidth; w <= halfWidth; w++) {
                int wx = (int)(cx + px * w);
                int wz = (int)(cz + pz * w);
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;

                BlockPos pos = new BlockPos(wx, surfaceY, wz);
                BlockState existing = level.getBlockState(pos);

                // Don't overwrite buildings or water
                if (existing.is(Blocks.WATER) || existing.is(Blocks.FARMLAND)) continue;
                // Don't place path on wool buildings
                if (existing.getBlock().getName().getString().contains("wool")) continue;

                level.setBlockAndUpdate(pos, Blocks.DIRT_PATH.defaultBlockState());
                // Clear above the path
                level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
            }
        }
    }
}
