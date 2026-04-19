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
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Map;

/**
 * Central event wiring for the MineKings village layer.
 *
 * <p>Villages are now produced by vanilla's jigsaw system using our
 * datapack-defined {@code minekings:village_*} structures. Registration
 * into the {@link VillageManager} / polity layer is done by watching
 * {@link ChunkEvent.Load} for structure starts in our namespace — no
 * block entity marker required.
 */
@EventBusSubscriber(modid = MineKings.MODID)
public final class MineKingsEvents {
    /** Structure tag grouping every MineKings village variant. */
    public static final TagKey<Structure> VILLAGE_TAG =
            TagKey.create(Registries.STRUCTURE, MineKings.locate("village"));

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

    /**
     * When a chunk is loaded, check whether any MineKings village structure
     * starts here and, if so, register the village + auto-found an NPC
     * polity. Idempotent: a village whose bounds already contain this
     * structure's center is skipped.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ChunkAccess chunk = event.getChunk();
        Map<Structure, StructureStart> starts = chunk.getAllStarts();
        if (starts.isEmpty()) return;

        Registry<Structure> structureReg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        VillageManager vm = null;
        PoliticsManager pm = null;

        for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
            StructureStart start = entry.getValue();
            if (!start.isValid()) continue;

            ResourceLocation id = structureReg.getKey(entry.getKey());
            if (id == null || !MineKings.MODID.equals(id.getNamespace())) continue;
            if (!id.getPath().startsWith("village")) continue;

            Vec3i c = start.getBoundingBox().getCenter();
            BlockPos center = new BlockPos(c.getX(), c.getY(), c.getZ());

            if (vm == null) vm = VillageManager.get(level);
            if (vm.findNearestVillage(center, 0).isPresent()) continue;

            Village village = vm.register(center, null);
            if (pm == null) pm = PoliticsManager.get(level);
            long currentDay = level.getDayTime() / 24000L;
            pm.foundPolityForVillage(village, currentDay);
            MineKings.LOGGER.info("[MK] Registered worldgen village {} at {} from structure {}",
                    village.getId(), center.toShortString(), id);
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
     * Asks the chunk generator abstractly (no chunk loading) where the
     * nearest MineKings village would generate. Uses the same code path
     * as vanilla's {@code /locate structure} — runs each candidate
     * structure's {@code findGenerationPoint} against the noise sampler.
     * If the nearest chunk has already been loaded + registered, the
     * registered village's in-world name is also reported.
     */
    private static int locateNearestVillage(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (src.getEntity() == null) {
            src.sendFailure(Component.literal("Locate must be run as an entity."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        BlockPos origin = src.getEntity().blockPosition();

        BlockPos found = level.findNearestMapStructure(VILLAGE_TAG, origin, 100, false);
        if (found == null) {
            src.sendSuccess(() -> Component.literal(
                    "No MineKings village found within ~1600 blocks."), false);
            return Command.SINGLE_SUCCESS;
        }

        int dx = found.getX() - origin.getX();
        int dz = found.getZ() - origin.getZ();
        int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);

        // Try to match an already-registered village at this position
        // so we can report its chosen name + culture too.
        VillageManager vm = VillageManager.get(level);
        Village registered = vm.findNearestVillage(found, 48).orElse(null);

        final BlockPos f = found;
        if (registered != null) {
            src.sendSuccess(() -> Component.literal(String.format(
                    "Nearest village: %s at (~%d, %d, ~%d) — %d blocks away",
                    registered.getName(), f.getX(), f.getY(), f.getZ(), dist
            )), false);
        } else {
            src.sendSuccess(() -> Component.literal(String.format(
                    "Nearest village at (~%d, %d, ~%d) — %d blocks away (ungenerated)",
                    f.getX(), f.getY(), f.getZ(), dist
            )), false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
