package com.minekings.minekings.economy;

import com.minekings.minekings.village.Building;
import com.minekings.minekings.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Map;

/**
 * Once per day, for each Village, drains convertible items out of the
 * chests inside buildings classified as type "storage" (and "big_house",
 * as a stretch category) and adds the converted resources to the
 * village's stockpile. Items without a conversion entry stay in the chest.
 *
 * <p>This is the mechanism by which player-built production (Create mills,
 * vanilla farms piped into chests, Mekanism mass-driver setups, etc.)
 * actually feeds the abstract village economy. The player builds
 * automation, pipes output into a storehouse chest, and the next day
 * tick converts it to food/materials/gold.
 *
 * <p>No custom block is needed — any vanilla {@link Container} BlockEntity
 * inside a detected "storage" building qualifies.
 */
public final class StorehouseDrain {
    private StorehouseDrain() {}

    /** Building-type names whose chests act as Storehouses. */
    private static final String[] STOREHOUSE_BUILDING_TYPES = {"storage"};

    /** Drains all storehouses in the village, converting items to resources in place. */
    public static void drainAll(ServerLevel level, Village village) {
        ConversionTable table = ConversionTable.getInstance();
        if (table.all().isEmpty()) return;

        for (Building building : village) {
            if (!isStorehouseType(building.getType())) continue;
            drainBuilding(level, village, building, table);
        }
    }

    private static boolean isStorehouseType(String type) {
        for (String s : STOREHOUSE_BUILDING_TYPES) {
            if (s.equals(type)) return true;
        }
        return false;
    }

    /**
     * For one storage building, iterate every detected block position that
     * corresponds to a chest-like container, open it, drain convertible
     * items, and credit the village's stockpiles.
     */
    private static void drainBuilding(ServerLevel level, Village village, Building building, ConversionTable table) {
        Map<ResourceLocation, List<BlockPos>> blocks = building.getBlocks();
        for (Map.Entry<ResourceLocation, List<BlockPos>> entry : blocks.entrySet()) {
            for (BlockPos pos : entry.getValue()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof Container container) {
                    drainContainer(village, container, table);
                }
            }
        }
    }

    /** Drains convertible items from a single container into the village's stockpiles. */
    private static void drainContainer(Village village, Container container, ConversionTable table) {
        double foodAccum = 0, matsAccum = 0, goldAccum = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            ResourceConversion conv = table.lookup(stack);
            if (conv == null) continue;

            int count = stack.getCount();
            double amount = count * conv.perItem();
            switch (conv.resource()) {
                case FOOD:      foodAccum += amount; break;
                case MATERIALS: matsAccum += amount; break;
                case GOLD:      goldAccum += amount; break;
            }
            container.removeItem(slot, count);
        }
        if (foodAccum > 0) village.addFood((long) Math.round(foodAccum));
        if (matsAccum > 0) village.addMaterials((long) Math.round(matsAccum));
        if (goldAccum > 0) village.addGold((long) Math.round(goldAccum));
        if (foodAccum > 0 || matsAccum > 0 || goldAccum > 0) {
            container.setChanged();
        }
    }
}
