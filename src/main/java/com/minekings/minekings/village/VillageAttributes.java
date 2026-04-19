package com.minekings.minekings.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Derives abstract attribute flags (has_smithy, has_food_industry, etc.)
 * from workstation blocks present within the village's bounds.
 *
 * <p>v0.6 replaced the flood-fill-building approach with direct block
 * scanning: the block IS the industry. A smithing_table anywhere in the
 * village radius gives has_smithy, regardless of whether it's inside a
 * "house" structure or a mega-build. This makes the economy work for
 * bunkers, creative builds, and modded workstations.
 */
public final class VillageAttributes {
    private VillageAttributes() {}

    /** Workstation / marker block → attribute flag(s). */
    private static final Map<Block, String[]> BLOCK_TO_ATTRS = new HashMap<>();
    static {
        // Food industry
        BLOCK_TO_ATTRS.put(Blocks.SMOKER,            new String[]{"has_food_industry"});
        BLOCK_TO_ATTRS.put(Blocks.COMPOSTER,         new String[]{"has_food_industry"});
        BLOCK_TO_ATTRS.put(Blocks.BARREL,            new String[]{"has_food_industry"}); // fisherman workstation

        // Smithing / weapons / armor
        BLOCK_TO_ATTRS.put(Blocks.SMITHING_TABLE,    new String[]{"has_smithy", "has_arms"});
        BLOCK_TO_ATTRS.put(Blocks.GRINDSTONE,        new String[]{"has_smithy"});
        BLOCK_TO_ATTRS.put(Blocks.BLAST_FURNACE,     new String[]{"has_smithy"});
        BLOCK_TO_ATTRS.put(Blocks.ANVIL,             new String[]{"has_smithy"});
        BLOCK_TO_ATTRS.put(Blocks.CHIPPED_ANVIL,     new String[]{"has_smithy"});
        BLOCK_TO_ATTRS.put(Blocks.DAMAGED_ANVIL,     new String[]{"has_smithy"});
        BLOCK_TO_ATTRS.put(Blocks.FLETCHING_TABLE,   new String[]{"has_arms"});

        // Craftsmen
        BLOCK_TO_ATTRS.put(Blocks.LOOM,              new String[]{"has_craftsmen"});
        BLOCK_TO_ATTRS.put(Blocks.STONECUTTER,       new String[]{"has_craftsmen"});
        BLOCK_TO_ATTRS.put(Blocks.CAULDRON,          new String[]{"has_craftsmen"}); // leatherworker
        BLOCK_TO_ATTRS.put(Blocks.CARTOGRAPHY_TABLE, new String[]{"has_bookkeeper"});

        // Civic / commercial
        BLOCK_TO_ATTRS.put(Blocks.BELL,              new String[]{"is_central"});
        BLOCK_TO_ATTRS.put(Blocks.LECTERN,           new String[]{"has_library"});
        BLOCK_TO_ATTRS.put(Blocks.BREWING_STAND,     new String[]{"has_infirmary"});
        BLOCK_TO_ATTRS.put(Blocks.ENCHANTING_TABLE,  new String[]{"has_library"});
        BLOCK_TO_ATTRS.put(Blocks.JUKEBOX,           new String[]{"has_music"});

        // Housing proxy — a bed implies someone lives there
        BLOCK_TO_ATTRS.put(Blocks.RED_BED,           new String[]{"has_housing"});
        BLOCK_TO_ATTRS.put(Blocks.WHITE_BED,         new String[]{"has_housing"});
    }

    /** Returns the attribute flags implied by the blocks inside the village bounds. */
    public static Set<String> derive(ServerLevel level, Village village) {
        Set<String> attrs = new HashSet<>();
        BoundingBox box = village.getBox();
        if (box == null) return attrs;

        // Scan the full box. Villages are typically ~96 blocks wide so this is
        // a few tens of thousands of block reads — cheap enough to do once per
        // day tick per village.
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    m.set(x, y, z);
                    Block b = level.getBlockState(m).getBlock();
                    String[] flags = BLOCK_TO_ATTRS.get(b);
                    if (flags != null) {
                        for (String f : flags) attrs.add(f);
                    }
                }
            }
        }

        // Population proxy: count beds via vanilla PoiManager (faster than
        // bed-block scanning, already used by Village.updateMaxPopulation).
        PoiManager poi = level.getPoiManager();
        long bedCount = poi.findAll(
                holder -> holder.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                pos -> box.isInside(pos),
                new BlockPos(box.getCenter()),
                Math.max(box.getXSpan(), box.getZSpan()),
                PoiManager.Occupancy.ANY
        ).count();
        if (bedCount > 0) attrs.add("has_housing");

        return attrs;
    }

    /** Derives and applies attributes to the village. Does not mark dirty. */
    public static void refresh(ServerLevel level, Village village) {
        Set<String> next = derive(level, village);
        village.setAttributes(next);
    }
}
