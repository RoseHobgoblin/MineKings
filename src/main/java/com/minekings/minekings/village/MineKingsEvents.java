package com.minekings.minekings.village;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.economy.ConversionTable;
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
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Central event wiring for the MineKings village layer.
 *
 * <p>v0.6 removed flood-fill detection: no more building queue, no more
 * POI polling, no more /minekings scan. Villages now only come from the
 * Founding Stone (player placement or worldgen template). The level tick
 * is still needed for the polity day tick.
 */
@EventBusSubscriber(modid = MineKings.MODID)
public final class MineKingsEvents {
    private MineKingsEvents() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BuildingTypes());
        event.addListener(new CultureManager());
        event.addListener(new ConversionTable());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        // Polity day tick + embodiment reconciliation — overworld only.
        // dayTick internally gates on game-day boundaries.
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
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("minekings")
                .then(Commands.literal("dump").executes(MineKingsEvents::dumpVillages))
                .then(Commands.literal("locate").executes(MineKingsEvents::locateNearestVillage))
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
                    "[%d] %s  box=(%d,%d,%d)->(%d,%d,%d)  pop=%d  attrs=%s",
                    v.getId(), v.getName(),
                    box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ(),
                    v.getPopulation(), v.getAttributes()
            )), false);
        }

        if (count == 0) {
            src.sendSuccess(() -> Component.literal("No villages registered in this level."), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Finds the nearest registered village to the command source and
     * reports its name, coordinates, and horizontal distance. Only
     * finds villages whose chunks have been loaded at least once — a
     * never-visited worldgen village won't appear in the registry yet.
     */
    private static int locateNearestVillage(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() == null) {
            src.sendFailure(Component.literal("Locate must be run as an entity."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        VillageManager manager = VillageManager.get(level);
        BlockPos origin = src.getEntity().blockPosition();

        Village best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Village v : manager) {
            BlockPos c = new BlockPos(v.getCenter());
            double dx = c.getX() - origin.getX();
            double dz = c.getZ() - origin.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = v;
            }
        }

        if (best == null) {
            src.sendSuccess(() -> Component.literal(
                    "No registered villages. Explore or place a Founding Stone — villages only register once their chunks have loaded."), false);
            return Command.SINGLE_SUCCESS;
        }
        Village found = best;
        BlockPos c = new BlockPos(found.getCenter());
        int dist = (int) Math.sqrt(bestDistSq);
        src.sendSuccess(() -> Component.literal(String.format(
                "Nearest village: %s at (%d, %d, %d) — %d blocks away",
                found.getName(), c.getX(), c.getY(), c.getZ(), dist
        )), false);

        // Diagnostic: what's actually at the village origin + surrounding pedestal?
        BlockPos stoneAt = c; // Founding Stone should be here
        BlockPos pedestalAt = c.below(4); // base of monument
        String stoneBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(stoneAt).getBlock()).toString();
        String pedBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(pedestalAt).getBlock()).toString();
        src.sendSuccess(() -> Component.literal(String.format(
                "  At stone pos %s: %s", stoneAt.toShortString(), stoneBlock
        )), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "  At pedestal pos %s: %s", pedestalAt.toShortString(), pedBlock
        )), false);
        return Command.SINGLE_SUCCESS;
    }
}
