package com.minekings.minekings;

import com.minekings.minekings.client.hub.map.ClientMapCapture;
import com.minekings.minekings.client.hub.map.TileTextureSetManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

// Loaded only on the physical client.
@Mod(value = MineKings.MODID, dist = Dist.CLIENT)
public class MineKingsClient {
    public MineKingsClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerClientReloadListeners);
        // Chunk-load capture & logout reset live on the game bus.
        NeoForge.EVENT_BUS.register(ClientMapCapture.class);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        MineKings.LOGGER.info("MineKings client setup");
    }

    private void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new TileTextureSetManager());
    }
}
