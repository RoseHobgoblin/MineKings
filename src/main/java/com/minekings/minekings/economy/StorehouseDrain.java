package com.minekings.minekings.economy;

import com.minekings.minekings.village.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Once per day, drains convertible items out of any container inside the
 * village bounds whose custom display name matches "Storehouse". This is
 * the v0.6 opt-in replacement for the old flood-fill "storage" building
 * detection.
 *
 * <p>Player workflow: rename a chest/barrel/hopper/shulker via anvil to
 * "Storehouse" (case-insensitive), drop it anywhere inside the village.
 * Pipe your Create / vanilla farm / Mekanism output into it. The daily
 * tick converts the contents to food/materials/gold.
 */
public final class StorehouseDrain {
    private StorehouseDrain() {}

    private static final String STOREHOUSE_NAME = "storehouse";

    /** Drains all named Storehouse containers in the village bounds. */
    public static void drainAll(ServerLevel level, Village village) {
        ConversionTable table = ConversionTable.getInstance();
        if (table.all().isEmpty()) return;

        BoundingBox box = village.getBox();
        if (box == null) return;

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    m.set(x, y, z);
                    BlockEntity be = level.getBlockEntity(m);
                    if (!(be instanceof Container container)) continue;
                    if (!isStorehouse(be)) continue;
                    drainContainer(village, container, table);
                }
            }
        }
    }

    private static boolean isStorehouse(BlockEntity be) {
        if (!(be instanceof Nameable nameable)) return false;
        if (!nameable.hasCustomName()) return false;
        Component name = nameable.getCustomName();
        if (name == null) return false;
        return name.getString().trim().equalsIgnoreCase(STOREHOUSE_NAME);
    }

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
