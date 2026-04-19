package com.minekings.minekings.registry;

import com.minekings.minekings.MineKings;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MineKingsItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MineKings.MODID);

    public static final DeferredItem<BlockItem> FOUNDING_STONE = ITEMS.registerSimpleBlockItem(
            MineKingsBlocks.FOUNDING_STONE,
            new Item.Properties()
    );

    private MineKingsItems() {}
}
