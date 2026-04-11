package com.minekings.minekings;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// Loaded only on the physical client.
@Mod(value = MineKings.MODID, dist = Dist.CLIENT)
public class MineKingsClient {
    public MineKingsClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        MineKings.LOGGER.info("MineKings client setup");
    }
}
