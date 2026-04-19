package com.minekings.minekings.client;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.client.hub.HubScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = MineKings.MODID, value = Dist.CLIENT)
public final class MineKingsClientEvents {
    private MineKingsClientEvents() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MineKingsKeyMappings.OPEN_HUB);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        while (MineKingsKeyMappings.OPEN_HUB.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new HubScreen());
            }
        }
    }
}
