package com.minekings.minekings.village.layout;

import com.minekings.minekings.MineKings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Central algorithm for generating village layouts. Given a terrain profile
 * and a target development level, produces a {@link VillageLayout} describing
 * where every building and road should go.
 * <p>
 * Phase 1: pure data output, no block placement. The layout can be inspected
 * via {@code /minekings village layout} and verified before Phase 2 places
 * actual structures.
 */
public final class VillageLayoutEngine {
    private VillageLayoutEngine() {}

    // ── Development level recipes ──────────────────────────────────────
    // Each entry: {houses, farms, workshops, civic, market, manor}
    private static final int[][] RECIPES = {
            // Level 0: Hamlet (3-5 plots)
            {2, 1, 1, 0, 0, 0},
            // Level 1: Village (8-15 plots)
            {5, 2, 3, 1, 0, 0},
            // Level 2: Town (20-35 plots)
            {10, 4, 5, 2, 1, 1},
            // Level 3: Large Town (40-60 plots)
            {18, 7, 10, 3, 1, 1},
            // Level 4: Fortified Town (60-80 plots)
            {25, 10, 15, 4, 1, 1},
            // Level 5: City (80-100+ plots)
            {35, 15, 20, 5, 2, 1},
    };

    private static final int IDX_HOUSE = 0;
    private static final int IDX_FARM = 1;
    private static final int IDX_WORKSHOP = 2;
    private static final int IDX_CIVIC = 3;
    private static final int IDX_MARKET = 4;
    private static final int IDX_MANOR = 5;

    /** Default footprint for most buildings. */
    private static final int DEFAULT_FOOTPRINT = 7;
    /** Farm footprint is larger. */
    private static final int FARM_FOOTPRINT = 11;
    /** Manor footprint. */
    private static final int MANOR_FOOTPRINT = 11;

    // ── Spacing rules (center-to-center, in blocks) ───────────────────
    private static final int HOUSE_MIN_SPACING = 4;
    private static final int WORKSHOP_MIN_SPACING = 6;
    private static final int FARM_MIN_DISTANCE_FROM_CENTER = 16;

    /**
     * Generates a village layout for the given terrain and development level.
     *
     * @param profile terrain analysis around the village center
     * @param level   development level 0-5
     * @param seed    random seed for reproducibility
     * @return a complete village layout
     */
    public static VillageLayout generate(TerrainProfile profile, int level, long seed) {
        level = Math.max(0, Math.min(level, RECIPES.length - 1));
        int[] recipe = RECIPES[level];
        Random rng = new Random(seed);

        List<PlotAssignment> plots = new ArrayList<>();
        List<RoadSegment> roads = new ArrayList<>();
        int nextPlotId = 0;

        BlockPos center = profile.center();
        BlockPos peak = profile.peak();
        BlockPos gatePos = profile.lowestPerimeterPoint();

        // ── Step 1: place manor at peak (if level >= 2) ───────────────
        if (recipe[IDX_MANOR] > 0) {
            Direction manorFacing = directionFromTo(peak, center);
            plots.add(new PlotAssignment(
                    nextPlotId++, peak, manorFacing,
                    "manor", 1, MANOR_FOOTPRINT, MANOR_FOOTPRINT
            ));
        }

        // ── Step 2: main road from gate to manor/center ───────────────
        BlockPos roadTarget = recipe[IDX_MANOR] > 0 ? peak : center;
        roads.add(new RoadSegment(gatePos, roadTarget, 3));

        // ── Step 3: place core buildings along main road ──────────────
        // Workshops and civic buildings go near the center, along the main road
        List<BlockPos> mainRoadSlots = slotsAlongRoad(gatePos, roadTarget, profile,
                DEFAULT_FOOTPRINT + 2, 0.2, 0.7);

        int workshopsPlaced = 0;
        int civicPlaced = 0;
        int marketsPlaced = 0;

        for (BlockPos slot : mainRoadSlots) {
            String role = null;
            int footprint = DEFAULT_FOOTPRINT;

            if (marketsPlaced < recipe[IDX_MARKET]) {
                // Market goes at the midpoint of the main road
                role = "market";
                marketsPlaced++;
            } else if (civicPlaced < recipe[IDX_CIVIC]) {
                role = "civic";
                civicPlaced++;
            } else if (workshopsPlaced < recipe[IDX_WORKSHOP]) {
                role = "workshop";
                workshopsPlaced++;
            }

            if (role == null) break;

            // Offset the building to one side of the road
            int side = (plots.size() % 2 == 0) ? 1 : -1;
            BlockPos plotOrigin = offsetFromRoad(slot, gatePos, roadTarget, footprint, side);
            int plotY = profile.heightAtNearest(plotOrigin.getX(), plotOrigin.getZ());
            plotOrigin = new BlockPos(plotOrigin.getX(), plotY, plotOrigin.getZ());

            Direction facing = directionToward(plotOrigin, slot);
            if (canPlace(plotOrigin, footprint, footprint, plots, WORKSHOP_MIN_SPACING)) {
                plots.add(new PlotAssignment(nextPlotId++, plotOrigin, facing,
                        role, 0, footprint, footprint));
            }
        }

        // ── Step 4: secondary roads branching off main road ───────────
        List<BlockPos> branchPoints = slotsAlongRoad(gatePos, roadTarget, profile,
                20, 0.25, 0.75);
        List<RoadSegment> secondaryRoads = new ArrayList<>();
        for (int i = 0; i < branchPoints.size() && secondaryRoads.size() < 4; i++) {
            BlockPos bp = branchPoints.get(i);
            // Branch perpendicular to the main road
            int mainDx = roadTarget.getX() - gatePos.getX();
            int mainDz = roadTarget.getZ() - gatePos.getZ();
            double mainLen = Math.sqrt(mainDx * mainDx + mainDz * mainDz);
            if (mainLen < 1) continue;
            // Perpendicular direction
            int perpX = (int)(-mainDz / mainLen * 25);
            int perpZ = (int)(mainDx / mainLen * 25);
            // Alternate sides
            int side = (i % 2 == 0) ? 1 : -1;
            BlockPos branchEnd = bp.offset(perpX * side, 0, perpZ * side);
            int endY = profile.heightAtNearest(branchEnd.getX(), branchEnd.getZ());
            branchEnd = new BlockPos(branchEnd.getX(), endY, branchEnd.getZ());
            RoadSegment branch = new RoadSegment(bp, branchEnd, 2);
            secondaryRoads.add(branch);
            roads.add(branch);
        }

        // ── Step 5: place houses along roads ──────────────────────────
        int housesPlaced = 0;
        // Place along secondary roads first, then main road overflow
        List<RoadSegment> houseRoads = new ArrayList<>(secondaryRoads);
        houseRoads.add(new RoadSegment(gatePos, roadTarget, 3)); // main road fallback

        for (RoadSegment road : houseRoads) {
            if (housesPlaced >= recipe[IDX_HOUSE]) break;
            List<BlockPos> houseSlots = slotsAlongRoad(
                    road.start(), road.end(), profile,
                    DEFAULT_FOOTPRINT + 1, 0.1, 0.9);
            for (BlockPos slot : houseSlots) {
                if (housesPlaced >= recipe[IDX_HOUSE]) break;
                int side = (housesPlaced % 2 == 0) ? 1 : -1;
                BlockPos plotOrigin = offsetFromRoad(slot, road.start(), road.end(),
                        DEFAULT_FOOTPRINT, side);
                int plotY = profile.heightAtNearest(plotOrigin.getX(), plotOrigin.getZ());
                plotOrigin = new BlockPos(plotOrigin.getX(), plotY, plotOrigin.getZ());
                Direction facing = directionToward(plotOrigin, slot);
                if (canPlace(plotOrigin, DEFAULT_FOOTPRINT, DEFAULT_FOOTPRINT,
                        plots, HOUSE_MIN_SPACING)) {
                    plots.add(new PlotAssignment(nextPlotId++, plotOrigin, facing,
                            "house", 0, DEFAULT_FOOTPRINT, DEFAULT_FOOTPRINT));
                    housesPlaced++;
                }
            }
        }

        // ── Step 6: place farms on flat outskirts ─────────────────────
        int farmsPlaced = 0;
        List<BlockPos> flatZones = new ArrayList<>(profile.flatZones());
        // Shuffle for variety, then sort by distance from center (farthest first)
        java.util.Collections.shuffle(flatZones, rng);
        flatZones.sort((a, b) -> {
            double da = distSqXZ(a, center);
            double db = distSqXZ(b, center);
            return Double.compare(db, da); // farthest first
        });

        for (BlockPos flat : flatZones) {
            if (farmsPlaced >= recipe[IDX_FARM]) break;
            double distFromCenter = Math.sqrt(distSqXZ(flat, center));
            if (distFromCenter < FARM_MIN_DISTANCE_FROM_CENTER) continue;

            int plotY = profile.heightAtNearest(flat.getX(), flat.getZ());
            BlockPos plotOrigin = new BlockPos(flat.getX(), plotY, flat.getZ());
            Direction facing = directionToward(plotOrigin, center);
            if (canPlace(plotOrigin, FARM_FOOTPRINT, FARM_FOOTPRINT, plots, HOUSE_MIN_SPACING)) {
                plots.add(new PlotAssignment(nextPlotId++, plotOrigin, facing,
                        "farm", 0, FARM_FOOTPRINT, FARM_FOOTPRINT));
                farmsPlaced++;

                // Add a tertiary path connecting the farm back to the nearest road
                BlockPos closestRoadPoint = findClosestRoadPoint(plotOrigin, roads);
                if (closestRoadPoint != null) {
                    roads.add(new RoadSegment(plotOrigin, closestRoadPoint, 1));
                }
            }
        }

        // ── Step 7: wall perimeter (data only, Phase 5 places blocks) ──
        int perimeterRadius = 0;
        List<BlockPos> gates = new ArrayList<>();
        if (level >= 2) {
            // Walls enclose the built-up area. Radius = max distance from center to any plot + margin
            double maxDist = 0;
            for (PlotAssignment p : plots) {
                double d = Math.sqrt(p.distSqXZ(center));
                if (d > maxDist) maxDist = d;
            }
            perimeterRadius = (int)(maxDist + 8); // 8-block margin beyond outermost building
            gates.add(gatePos);
            // Add a second gate opposite to the main gate if town is large enough
            if (level >= 3) {
                int oppositeX = center.getX() * 2 - gatePos.getX();
                int oppositeZ = center.getZ() * 2 - gatePos.getZ();
                int oppositeY = profile.heightAtNearest(oppositeX, oppositeZ);
                gates.add(new BlockPos(oppositeX, oppositeY, oppositeZ));
            }
        }

        MineKings.LOGGER.info("VLE generated level-{} layout: {} plots, {} roads, perimeter={}",
                level, plots.size(), roads.size(), perimeterRadius);

        return new VillageLayout(plots, roads, center, perimeterRadius, gates, level);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Generates evenly-spaced positions along a road segment between
     * {@code tMin} and {@code tMax} (0.0 = start, 1.0 = end).
     */
    private static List<BlockPos> slotsAlongRoad(BlockPos start, BlockPos end,
                                                  TerrainProfile profile,
                                                  int spacing, double tMin, double tMax) {
        List<BlockPos> slots = new ArrayList<>();
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < spacing) {
            // Road too short for even one slot; place one at midpoint
            double t = (tMin + tMax) / 2;
            int sx = start.getX() + (int)(dx * t);
            int sz = start.getZ() + (int)(dz * t);
            int sy = profile.heightAtNearest(sx, sz);
            slots.add(new BlockPos(sx, sy, sz));
            return slots;
        }

        int count = (int)(len * (tMax - tMin) / spacing);
        for (int i = 0; i <= count; i++) {
            double t = tMin + (tMax - tMin) * i / Math.max(1, count);
            int sx = start.getX() + (int)(dx * t);
            int sz = start.getZ() + (int)(dz * t);
            int sy = profile.heightAtNearest(sx, sz);
            slots.add(new BlockPos(sx, sy, sz));
        }
        return slots;
    }

    /**
     * Offsets a point perpendicular to a road direction by the building
     * footprint plus road width.
     */
    private static BlockPos offsetFromRoad(BlockPos roadPoint, BlockPos roadStart,
                                           BlockPos roadEnd, int footprint, int side) {
        double dx = roadEnd.getX() - roadStart.getX();
        double dz = roadEnd.getZ() - roadStart.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1) return roadPoint;
        // Perpendicular: rotate 90 degrees
        double perpX = -dz / len;
        double perpZ = dx / len;
        int offset = (footprint / 2) + 3; // half footprint + road half-width + gap
        return roadPoint.offset((int)(perpX * offset * side), 0, (int)(perpZ * offset * side));
    }

    /**
     * Checks that a new plot at {@code origin} with the given footprint
     * doesn't overlap with any existing plot (center-to-center spacing).
     */
    private static boolean canPlace(BlockPos origin, int fpX, int fpZ,
                                    List<PlotAssignment> existing, int minSpacing) {
        BlockPos center = origin.offset(fpX / 2, 0, fpZ / 2);
        for (PlotAssignment p : existing) {
            double dist = Math.sqrt(distSqXZ(center, p.center()));
            double minDist = minSpacing + (p.footprintX() + fpX) / 2.0;
            if (dist < minDist) return false;
        }
        return true;
    }

    /** Finds the closest point on any existing road to the given position. */
    private static BlockPos findClosestRoadPoint(BlockPos pos, List<RoadSegment> roads) {
        BlockPos closest = null;
        double bestDist = Double.MAX_VALUE;
        for (RoadSegment road : roads) {
            BlockPos cp = road.closestPointXZ(pos);
            double d = distSqXZ(pos, cp);
            if (d < bestDist) {
                bestDist = d;
                closest = cp;
            }
        }
        return closest;
    }

    /** Squared XZ distance between two positions. */
    private static double distSqXZ(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** Cardinal direction from {@code from} toward {@code to}. */
    private static Direction directionFromTo(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    /** Cardinal direction from {@code from} toward {@code to} (alias for readability). */
    private static Direction directionToward(BlockPos from, BlockPos to) {
        return directionFromTo(from, to);
    }
}
