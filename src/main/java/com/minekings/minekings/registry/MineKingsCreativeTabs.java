package com.minekings.minekings.registry;

import com.minekings.minekings.MineKings;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MineKingsCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineKings.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.minekings.main"))
                    .icon(() -> MineKingsItems.FOUNDING_STONE.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(MineKingsItems.FOUNDING_STONE.get());
                    })
                    .build()
    );

    private MineKingsCreativeTabs() {}
}
