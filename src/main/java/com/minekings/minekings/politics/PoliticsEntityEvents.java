package com.minekings.minekings.politics;

import com.minekings.minekings.MineKings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Event handlers for the politics layer that hook entity lifecycle. Kept
 * in its own class to isolate it from the village-layer event wiring in
 * {@link com.minekings.minekings.village.MineKingsEvents}.
 */
@EventBusSubscriber(modid = MineKings.MODID)
public final class PoliticsEntityEvents {
    private PoliticsEntityEvents() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!(villager.level() instanceof ServerLevel serverLevel)) return;
        // Polity layer is overworld-only in v0.3, matching dayTick gating.
        PoliticsManager.get(serverLevel).handleLeaderDeath(serverLevel, villager.getUUID());
    }
}
