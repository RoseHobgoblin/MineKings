package com.minekings.minekings.village;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.politics.CultureManager;
import com.minekings.minekings.politics.PoliticsCommands;
import com.minekings.minekings.politics.PoliticsManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Central event wiring for the MineKings village layer.
 * <p>
 * - {@link #onAddReloadListeners(AddReloadListenerEvent)}: registers the
 *   datapack reload listener for {@link BuildingTypes}. Without this the
 *   building-type JSONs never load and every building validates as the
 *   empty default type.
 * - {@link #onLevelTick(LevelTickEvent.Post)}: drains the building queue
 *   and runs the player-proximity feeder poll.
 * - {@link #onRegisterCommands(RegisterCommandsEvent)}: registers the
 *   {@code /minekings dump} and {@code /minekings scan} debug commands.
 */
@EventBusSubscriber(modid = MineKings.MODID)
public final class MineKingsEvents {
    // Per-level last-poll bookkeeping. Beats a fragile `gameTime % N == 0`
    // gate because it always fires the first poll immediately and then
    // spaces subsequent polls exactly POLL_INTERVAL_TICKS apart.
    private static final Map<ServerLevel, Long> LAST_POLL = new WeakHashMap<>();

    private MineKingsEvents() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BuildingTypes());
        event.addListener(new CultureManager());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        // Drain the building queue every tick. VillageManager.tick() internally
        // rate-limits using buildingCooldown, so this is cheap.
        VillageManager.get(level).tick();

        // Poll POIs around each player on BuildingFeeder's cadence.
        long now = level.getGameTime();
        long last = LAST_POLL.getOrDefault(level, Long.MIN_VALUE);
        if (now - last >= BuildingFeeder.POLL_INTERVAL_TICKS) {
            LAST_POLL.put(level, now);
            BuildingFeeder.poll(level);
        }

        // Polity day tick + embodiment reconciliation — overworld only.
        // dayTick internally gates on game-day boundaries so it's a cheap
        // no-op on most ticks. reconcileEmbodiments runs every 100 ticks
        // (~5 seconds) to rebind any unbound polity leaders.
        if (level.dimension() == Level.OVERWORLD) {
            PoliticsManager mgr = PoliticsManager.get(level);
            mgr.dayTick(level);
            if (level.getGameTime() % 100L == 0L) {
                mgr.reconcileEmbodiments(level);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // NOTE: The top-level /minekings literal is NOT permission-gated
        // anymore — individual subcommands declare their own permission
        // requirements. Reads (dump, polity list/info, character info,
        // culture list, day) are open to all players; mutations (scan,
        // polity swear/revoke/rename, day advance) require permission 2.
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("minekings")
                .then(Commands.literal("dump").executes(MineKingsEvents::dumpVillages))
                .then(Commands.literal("scan")
                        .requires(src -> src.hasPermission(2))
                        .executes(MineKingsEvents::forceScan))
                .then(PoliticsCommands.build());
        event.getDispatcher().register(root);
    }

    private static int dumpVillages(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        VillageManager manager = VillageManager.get(level);

        int count = 0;
        for (Village v : manager) {
            count++;
            var box = v.getBox();
            src.sendSuccess(() -> Component.literal(String.format(
                    "[%d] %s  box=(%d,%d,%d)->(%d,%d,%d)  buildings=%d",
                    v.getId(), v.getName(),
                    box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ(),
                    v.getBuildings().size()
            )), false);

            for (Building b : v.getBuildings().values()) {
                src.sendSuccess(() -> Component.literal(String.format(
                        "    building #%d type=%s source=%s",
                        b.getId(), b.getType(), b.getSourceBlock().toShortString()
                )), false);
            }
        }

        if (count == 0) {
            src.sendSuccess(() -> Component.literal("No villages registered in this level."), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Forces an immediate POI poll centered on the command source, bypassing
     * the 600-tick gate and the dedup cache, and reports exactly what
     * validateBuilding() did to each candidate. The answer we get here tells
     * us whether the detector is seeing the village at all.
     */
    private static int forceScan(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() == null) {
            src.sendFailure(Component.literal("Scan must be run as an entity."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        VillageManager manager = VillageManager.get(level);
        PoiManager poi = level.getPoiManager();
        BlockPos origin = src.getEntity().blockPosition();

        List<BlockPos> found = poi.findAll(
                type -> true,
                pos -> true,
                origin,
                BuildingFeeder.POLL_RADIUS_BLOCKS,
                PoiManager.Occupancy.ANY
        ).toList();

        src.sendSuccess(() -> Component.literal(String.format(
                "Scan at %s (r=%d): %d POIs in range",
                origin.toShortString(), BuildingFeeder.POLL_RADIUS_BLOCKS, found.size()
        )), false);

        if (found.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  (PoiManager returned nothing — either chunks aren't POI-loaded yet or the vanilla scan is empty)"), false);
            return Command.SINGLE_SUCCESS;
        }

        Map<Building.validationResult, Integer> results = new EnumMap<>(Building.validationResult.class);
        for (BlockPos pos : found) {
            manager.cache.add(pos);
            Building.validationResult r = manager.processBuilding(pos, true, false);
            results.merge(r, 1, Integer::sum);
        }

        results.forEach((r, n) -> src.sendSuccess(() -> Component.literal("  " + r + ": " + n), false));

        int villageCount = 0;
        for (Village ignored : manager) villageCount++;
        int finalVillageCount = villageCount;
        src.sendSuccess(() -> Component.literal("Villages now registered: " + finalVillageCount), false);

        return Command.SINGLE_SUCCESS;
    }
}
