package com.minekings.minekings.village;

import com.minekings.minekings.MineKings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodically rescans each village's bounding box for signature blocks and
 * rewrites its {@link Building} set with one Building per chunk, tagged with
 * the dominant building type ({@code house}, {@code farm}, {@code blacksmith},
 * ...). Runs server-side on {@link LevelTickEvent.Post}, overworld only, one
 * village per second. Only loaded chunks are scanned so the scanner never
 * forces generation; unloaded chunks are treated as "nothing here" and wait
 * for the next pass.
 *
 * <p>The emitted Buildings drive the hub map's per-building tile rendering
 * and the culture-colored village outline. Because the scan is retroactive,
 * it works on villages generated before this system shipped and on any that
 * get modified by players.
 */
@EventBusSubscriber(modid = MineKings.MODID)
public final class BuildingScanner {
    /** Ticks between scans. 20 = one village per in-game second. */
    private static final long SCAN_INTERVAL_TICKS = 20L;
    /** Minimum path blocks in a chunk to classify it as a path tile. */
    private static final int PATH_THRESHOLD = 6;
    /** Minimum farmland blocks to classify a chunk as farm. */
    private static final int FARM_THRESHOLD = 3;
    /** Minimum bookshelves to classify a chunk as library. */
    private static final int LIBRARY_THRESHOLD = 4;

    /** Round-robin cursor into each level's village list, by dimension. */
    private static final java.util.Map<net.minecraft.resources.ResourceLocation, Integer> CURSORS = new java.util.HashMap<>();

    private BuildingScanner() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % SCAN_INTERVAL_TICKS != 0L) return;

        VillageManager vm = VillageManager.get(level);
        List<Village> villages = new ArrayList<>();
        for (Village v : vm) villages.add(v);
        if (villages.isEmpty()) return;

        var key = level.dimension().location();
        int cursor = CURSORS.getOrDefault(key, 0);
        cursor = cursor % villages.size();
        Village target = villages.get(cursor);
        CURSORS.put(key, (cursor + 1) % villages.size());

        try {
            scanVillage(level, target);
        } catch (Exception e) {
            MineKings.LOGGER.warn("BuildingScanner failed on village {} ({}): {}",
                    target.getId(), target.getName(), e.toString());
        }
    }

    /** Rewrites {@code village.buildings} from a fresh scan of its bounding box. */
    private static void scanVillage(ServerLevel level, Village village) {
        BoundingBox box = village.getBox();
        if (box.getXSpan() <= 1 || box.getZSpan() <= 1) return; // unseeded

        int minCX = box.minX() >> 4;
        int minCZ = box.minZ() >> 4;
        int maxCX = box.maxX() >> 4;
        int maxCZ = box.maxZ() >> 4;

        int y0 = Math.max(level.getMinBuildHeight(), box.minY());
        int y1 = Math.min(level.getMaxBuildHeight() - 1, box.maxY());

        List<Building> scanned = new ArrayList<>();
        int nextId = village.nextBuildingId();

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;

                ChunkTally tally = scanChunk(level, cx, cz, y0, y1);
                String type = tally.classify();
                if (type == null) continue;

                Building b = new Building(
                        new BlockPos(cx << 4,        y0, cz << 4),
                        new BlockPos((cx << 4) + 15, y1, (cz << 4) + 15));
                b.setId(nextId++);
                b.setType(type);
                scanned.add(b);
            }
        }
        village.replaceBuildings(scanned);
    }

    private static ChunkTally scanChunk(ServerLevel level, int cx, int cz, int y0, int y1) {
        ChunkTally t = new ChunkTally();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int bx0 = cx << 4;
        int bz0 = cz << 4;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = bx0 + dx;
                int z = bz0 + dz;
                for (int y = y1; y >= y0; y--) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir()) continue;
                    classifyBlock(s, t);
                    if (t.columnClassified) {
                        t.columnClassified = false;
                        break;
                    }
                }
            }
        }
        return t;
    }

    private static void classifyBlock(BlockState s, ChunkTally t) {
        var block = s.getBlock();
        // Beds first so "house" wins even in chunks that also have a composter etc.
        if (s.is(Blocks.WHITE_BED) || s.is(Blocks.ORANGE_BED) || s.is(Blocks.MAGENTA_BED)
                || s.is(Blocks.LIGHT_BLUE_BED) || s.is(Blocks.YELLOW_BED) || s.is(Blocks.LIME_BED)
                || s.is(Blocks.PINK_BED) || s.is(Blocks.GRAY_BED) || s.is(Blocks.LIGHT_GRAY_BED)
                || s.is(Blocks.CYAN_BED) || s.is(Blocks.PURPLE_BED) || s.is(Blocks.BLUE_BED)
                || s.is(Blocks.BROWN_BED) || s.is(Blocks.GREEN_BED) || s.is(Blocks.RED_BED)
                || s.is(Blocks.BLACK_BED)) {
            t.beds++; t.columnClassified = true; return;
        }
        if (block == Blocks.BELL) { t.bells++; t.columnClassified = true; return; }
        if (block == Blocks.SMITHING_TABLE || block == Blocks.BLAST_FURNACE || block == Blocks.ANVIL
                || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL) {
            t.smithy++; t.columnClassified = true; return;
        }
        if (block == Blocks.LECTERN || block == Blocks.BOOKSHELF) {
            t.books++; t.columnClassified = true; return;
        }
        if (block == Blocks.COMPOSTER || block == Blocks.SMOKER) {
            t.bakery++; t.columnClassified = true; return;
        }
        if (block == Blocks.FARMLAND) { t.farmland++; t.columnClassified = true; return; }
        if (block == Blocks.DIRT_PATH) { t.path++; t.columnClassified = true; return; }
    }

    /** Per-chunk counts used for dominant-type classification. */
    private static final class ChunkTally {
        int beds, bells, smithy, books, bakery, farmland, path;
        boolean columnClassified;

        /** @return building type string, or {@code null} if this chunk has
         *  no village signature at all (empty → skip). */
        String classify() {
            if (bells > 0) return "town_center";
            if (smithy > 0) return "blacksmith";
            if (books >= LIBRARY_THRESHOLD) return "library";
            if (bakery > 0) return "bakery";
            if (farmland >= FARM_THRESHOLD && beds == 0) return "farm";
            if (path >= PATH_THRESHOLD && beds == 0) return "path_x"; // axis refinement deferred
            if (beds > 0) return "house";
            return null;
        }
    }
}
