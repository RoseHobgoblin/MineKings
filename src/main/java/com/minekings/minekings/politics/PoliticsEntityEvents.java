package com.minekings.minekings.politics;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.village.Village;
import com.minekings.minekings.village.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Event handlers for the politics layer that hook entity lifecycle and
 * player interaction. Kept in its own class to isolate it from the
 * village-layer event wiring in
 * {@link com.minekings.minekings.village.MineKingsEvents}.
 */
@EventBusSubscriber(modid = MineKings.MODID)
public final class PoliticsEntityEvents {
    private PoliticsEntityEvents() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!(villager.level() instanceof ServerLevel serverLevel)) return;
        PoliticsManager.get(serverLevel).handleLeaderDeath(serverLevel, villager.getUUID());
    }

    /**
     * When a player right-clicks a bound leader villager, send them the
     * polity view payload instead of opening the vanilla trading GUI.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) player.level();

        PoliticsManager mgr = PoliticsManager.get(level);
        Optional<java.util.UUID> uuid = Optional.of(villager.getUUID());
        Integer characterId = mgr.getEmbodiedBy(villager.getUUID());
        if (characterId == null) return; // not a bound leader — let vanilla handle it

        // Find which polity this character leads
        Polity polity = null;
        for (Polity p : mgr.getPolities()) {
            if (p.getLeaderCharacterId() == characterId) {
                polity = p;
                break;
            }
        }
        if (polity == null) return;

        // Build the payload
        PolityViewPayload payload = buildPayload(mgr, polity, level);
        PacketDistributor.sendToPlayer(player, payload);

        // Cancel to prevent vanilla trading GUI from opening
        event.setCanceled(true);
    }

    private static PolityViewPayload buildPayload(PoliticsManager mgr, Polity polity, ServerLevel level) {
        Character leader = mgr.getCharacter(polity.getLeaderCharacterId()).orElse(null);
        String leaderTitleName = leader != null
                ? (mgr.getLeaderTitle(polity) + " " + leader.getName())
                : "(vacant)";
        String displayName = mgr.getPolityDisplayName(polity);
        String cultureName = polity.getCultureName();

        // Liege
        String liegeDisplay = "";
        Optional<Allegiance> liege = mgr.getAllegianceFor(polity.getId());
        if (liege.isPresent()) {
            Polity lord = mgr.getPolity(liege.get().lordPolityId()).orElse(null);
            if (lord != null) {
                liegeDisplay = mgr.getPolityDisplayName(lord);
            }
        }

        // Village rows
        VillageManager vm = VillageManager.get(level);
        List<PolityViewPayload.VillageRow> villages = new ArrayList<>();
        for (int villageId : polity.getHeldVillageIds()) {
            vm.getOrEmpty(villageId).ifPresent(v -> villages.add(new PolityViewPayload.VillageRow(
                    v.getName(),
                    v.getPopulation(),
                    v.getFood(),
                    v.getMaterials(),
                    v.getGold(),
                    PoliticsManager.computeVillageBaselineFood(v),
                    PoliticsManager.computeVillageBaselineMaterials(v),
                    PoliticsManager.computeVillageBaselineGold(v),
                    new ArrayList<>(v.getAttributes())
            )));
        }

        // Vassal rows
        List<PolityViewPayload.VassalRow> vassals = mgr.getDirectVassals(polity.getId())
                .map(v -> {
                    Character vLeader = mgr.getCharacter(v.getLeaderCharacterId()).orElse(null);
                    String vTitle = vLeader != null
                            ? (mgr.getLeaderTitle(v) + " " + vLeader.getName())
                            : "(vacant)";
                    return new PolityViewPayload.VassalRow(mgr.getPolityDisplayName(v), vTitle);
                })
                .collect(Collectors.toList());

        return new PolityViewPayload(
                polity.getId(),
                displayName,
                leaderTitleName,
                cultureName,
                polity.getTreasury(),
                polity.getTaxRate(),
                polity.getTributeRate(),
                polity.getFoundedOnDay(),
                liegeDisplay,
                villages,
                vassals
        );
    }
}
