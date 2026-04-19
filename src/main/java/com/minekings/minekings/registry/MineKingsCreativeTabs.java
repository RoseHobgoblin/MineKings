package com.minekings.minekings.registry;

import com.minekings.minekings.MineKings;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.CreativeModeTab;

/**
 * No items ship with the mod right now — villages are purely worldgen.
 * The creative tab registry stays so downstream features can add items
 * without re-wiring the mod bus.
 */
public final class MineKingsCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineKings.MODID);

    private MineKingsCreativeTabs() {}
}
