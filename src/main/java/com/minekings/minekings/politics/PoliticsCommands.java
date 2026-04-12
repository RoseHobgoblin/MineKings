package com.minekings.minekings.politics;

import com.minekings.minekings.village.Village;
import com.minekings.minekings.village.VillageManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Brigadier command tree for the polity layer. Plugs into
 * {@code MineKingsEvents.onRegisterCommands} via {@link #build()}.
 *
 * <p>Read commands are available to all players (permission 0). Mutating
 * commands require op-level 2.
 */
public final class PoliticsCommands {
    private PoliticsCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        SuggestionProvider<CommandSourceStack> polityIdSuggestions = (ctx, builder) -> {
            ServerLevel level = ctx.getSource().getLevel();
            PoliticsManager mgr = PoliticsManager.get(level);
            for (Polity p : mgr.getPolities()) {
                builder.suggest(p.getId());
            }
            return builder.buildFuture();
        };

        SuggestionProvider<CommandSourceStack> characterIdSuggestions = (ctx, builder) -> {
            ServerLevel level = ctx.getSource().getLevel();
            PoliticsManager mgr = PoliticsManager.get(level);
            for (Character c : mgr.getCharacters()) {
                builder.suggest(c.getId());
            }
            return builder.buildFuture();
        };

        return Commands.literal("polity")
                // === read commands (permission 0) ===
                .then(Commands.literal("list")
                        .executes(PoliticsCommands::listPolities))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .suggests(polityIdSuggestions)
                                .executes(PoliticsCommands::polityInfo)))
                .then(Commands.literal("embodiments")
                        .executes(PoliticsCommands::listEmbodiments))
                .then(Commands.literal("culture")
                        .then(Commands.literal("list")
                                .executes(PoliticsCommands::listCultures)))
                .then(Commands.literal("character")
                        .then(Commands.literal("list")
                                .executes(PoliticsCommands::listCharacters))
                        .then(Commands.literal("info")
                                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                        .suggests(characterIdSuggestions)
                                        .executes(PoliticsCommands::characterInfo))))
                .then(Commands.literal("day")
                        .executes(PoliticsCommands::dayPrint)
                        // === day advance requires op ===
                        .then(Commands.literal("advance")
                                .requires(src -> src.hasPermission(2))
                                .executes(PoliticsCommands::dayAdvanceOne)
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 100))
                                        .executes(PoliticsCommands::dayAdvanceN))))
                // === mutating commands (permission 2) ===
                .then(Commands.literal("swear")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("vassalId", IntegerArgumentType.integer(0))
                                .suggests(polityIdSuggestions)
                                .then(Commands.argument("lordId", IntegerArgumentType.integer(0))
                                        .suggests(polityIdSuggestions)
                                        .executes(PoliticsCommands::polSwear))))
                .then(Commands.literal("revoke")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("vassalId", IntegerArgumentType.integer(0))
                                .suggests(polityIdSuggestions)
                                .executes(PoliticsCommands::polRevoke)))
                .then(Commands.literal("rename")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .suggests(polityIdSuggestions)
                                .then(Commands.argument("newName", StringArgumentType.greedyString())
                                        .executes(PoliticsCommands::polRename))))
                .then(Commands.literal("rebind")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .suggests(polityIdSuggestions)
                                .executes(PoliticsCommands::polRebind)))
                .then(Commands.literal("settax")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .suggests(polityIdSuggestions)
                                .then(Commands.argument("rate", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(PoliticsCommands::polSetTax))))
                .then(Commands.literal("settribute")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .suggests(polityIdSuggestions)
                                .then(Commands.argument("rate", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(PoliticsCommands::polSetTribute))));
    }

    // =====================================================================
    // Read commands
    // =====================================================================

    private static int listPolities(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);

        if (mgr.getPolities().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No polities exist yet. Run /minekings polity day advance to found them.").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }

        for (Polity p : mgr.getPolities()) {
            String display = mgr.getPolityDisplayName(p);
            Optional<Allegiance> liege = mgr.getAllegianceFor(p.getId());
            String liegeStr = liege.map(a -> {
                Polity lord = mgr.getPolity(a.lordPolityId()).orElse(null);
                return lord != null ? mgr.getPolityDisplayName(lord) : ("#" + a.lordPolityId());
            }).orElse("none");

            String line = String.format("[%d] %s  culture=%s  villages=%d  treasury=%d  liege=%s",
                    p.getId(), display, p.getCultureName(), p.getHeldVillageIds().size(), p.getTreasury(), liegeStr);
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int polityInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        int id = IntegerArgumentType.getInteger(ctx, "id");

        Optional<Polity> opt = mgr.getPolity(id);
        if (opt.isEmpty()) {
            src.sendFailure(Component.literal("No polity with id " + id));
            return 0;
        }
        Polity p = opt.get();
        String display = mgr.getPolityDisplayName(p);
        String title = mgr.getPolityTitle(p);
        Character leader = mgr.getCharacter(p.getLeaderCharacterId()).orElse(null);
        String leaderTitle = mgr.getLeaderTitle(p);

        src.sendSuccess(() -> Component.literal("=== Polity #" + p.getId() + " ===").withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("Display: " + display), false);
        src.sendSuccess(() -> Component.literal("Base name: " + p.getName()), false);
        src.sendSuccess(() -> Component.literal("Tier: " + title + " (height " + mgr.subtreeHeight(p.getId()) + ")"), false);
        src.sendSuccess(() -> Component.literal("Culture: " + p.getCultureName()), false);
        src.sendSuccess(() -> Component.literal("Founded: day " + p.getFoundedOnDay()), false);
        src.sendSuccess(() -> Component.literal("Treasury: " + p.getTreasury()), false);
        src.sendSuccess(() -> Component.literal(String.format("Tax rate: %.0f%%  Tribute rate: %.0f%%",
                p.getTaxRate() * 100f, p.getTributeRate() * 100f)), false);
        if (leader != null) {
            src.sendSuccess(() -> Component.literal("Leader: " + leaderTitle + " " + leader.getName() + " (#" + leader.getId() + ")"), false);
            UUID embodimentUuid = mgr.getEmbodimentUuid(leader.getId()).orElse(null);
            if (embodimentUuid == null) {
                src.sendSuccess(() -> Component.literal("Embodiment: unbound"), false);
            } else {
                Entity boundEntity = level.getEntity(embodimentUuid);
                if (boundEntity != null && !boundEntity.isRemoved()) {
                    String loc = boundEntity.blockPosition().toShortString();
                    src.sendSuccess(() -> Component.literal("Embodiment: villager at " + loc + " (UUID " + embodimentUuid + ")"), false);
                } else {
                    src.sendSuccess(() -> Component.literal("Embodiment: bound to UUID " + embodimentUuid + " (entity not currently loaded)"), false);
                }
            }
        } else {
            src.sendSuccess(() -> Component.literal("Leader: (vacant)"), false);
        }

        // Held villages
        VillageManager vm = VillageManager.get(level);
        List<String> villageLines = p.getHeldVillageIds().stream()
                .map(vid -> {
                    Optional<Village> v = vm.getOrEmpty(vid);
                    return v.map(value -> {
                        long income = PoliticsManager.computeVillageDailyIncome(value);
                        return String.format("  #%d %s  stockpile=%d  %+d/day", vid, value.getName(), value.getStockpile(), income);
                    }).orElseGet(() -> "  #" + vid + " (missing)");
                })
                .collect(Collectors.toList());
        src.sendSuccess(() -> Component.literal("Villages (" + p.getHeldVillageIds().size() + "):"), false);
        for (String vl : villageLines) {
            src.sendSuccess(() -> Component.literal(vl), false);
        }

        // Liege
        Optional<Allegiance> liege = mgr.getAllegianceFor(p.getId());
        if (liege.isPresent()) {
            Polity lord = mgr.getPolity(liege.get().lordPolityId()).orElse(null);
            String lordStr = lord != null ? (mgr.getPolityDisplayName(lord) + " (#" + lord.getId() + ")") : "(missing)";
            src.sendSuccess(() -> Component.literal("Liege: " + lordStr + " since day " + liege.get().swornOnDay()), false);
        } else {
            src.sendSuccess(() -> Component.literal("Liege: none (independent)"), false);
        }

        // Direct vassals
        List<Polity> vassals = mgr.getDirectVassals(p.getId()).collect(Collectors.toList());
        src.sendSuccess(() -> Component.literal("Vassals (" + vassals.size() + "):"), false);
        for (Polity vassal : vassals) {
            src.sendSuccess(() -> Component.literal("  " + mgr.getPolityDisplayName(vassal) + " (#" + vassal.getId() + ")"), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int listCultures(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        CultureManager cm = CultureManager.getInstance();
        if (cm.getCultures().isEmpty()) {
            src.sendFailure(Component.literal("No cultures loaded. Check data/minekings/cultures/*.json."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("=== Cultures (" + cm.getCultures().size() + ") ===").withStyle(ChatFormatting.GOLD), false);
        for (Culture c : cm) {
            String biomeDesc = c.isWildcard() ? "wildcard" : (c.biomes().size() + " biomes");
            String tierList = String.join(" > ", c.tiers());
            src.sendSuccess(() -> Component.literal(
                    String.format("[%s] %s  (%s)  tiers: %s",
                            c.id(), c.displayName(), biomeDesc, tierList)
            ), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int listCharacters(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        if (mgr.getCharacters().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No characters exist yet.").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        long today = PoliticsManager.computeCurrentDay(level);
        for (Character c : mgr.getCharacters()) {
            Polity p = mgr.getPolity(c.getCurrentPolityId()).orElse(null);
            String polityStr = p != null ? mgr.getPolityDisplayName(p) : "(stateless)";
            String line = String.format("[%d] %s  culture=%s  age=%d  polity=%s",
                    c.getId(), c.getName(), c.getCultureName(), c.getAge(today), polityStr);
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int characterInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        int id = IntegerArgumentType.getInteger(ctx, "id");
        Optional<Character> opt = mgr.getCharacter(id);
        if (opt.isEmpty()) {
            src.sendFailure(Component.literal("No character with id " + id));
            return 0;
        }
        Character c = opt.get();
        long today = PoliticsManager.computeCurrentDay(level);
        Polity p = mgr.getPolity(c.getCurrentPolityId()).orElse(null);

        src.sendSuccess(() -> Component.literal("=== Character #" + c.getId() + " ===").withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("Name: " + c.getName()), false);
        src.sendSuccess(() -> Component.literal("Culture: " + c.getCultureName()), false);
        src.sendSuccess(() -> Component.literal("Born: day " + c.getBirthDay()), false);
        src.sendSuccess(() -> Component.literal("Age: " + c.getAge(today) + " days"), false);
        src.sendSuccess(() -> Component.literal("Status: " + (c.isAlive() ? "alive" : ("died day " + c.getDeathDay()))), false);
        if (p != null) {
            String leaderTitle = p.getLeaderCharacterId() == c.getId() ? (mgr.getLeaderTitle(p) + " of ") : "resident of ";
            src.sendSuccess(() -> Component.literal("Polity: " + leaderTitle + mgr.getPolityDisplayName(p)), false);
        } else {
            src.sendSuccess(() -> Component.literal("Polity: none (stateless)"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int dayPrint(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        long currentDay = PoliticsManager.computeCurrentDay(level);
        long lastDay = mgr.getLastDay();
        long delta = currentDay - lastDay;
        src.sendSuccess(() -> Component.literal(String.format("currentDay=%d  lastDay=%d  delta=%d", currentDay, lastDay, delta)), false);
        return Command.SINGLE_SUCCESS;
    }

    // =====================================================================
    // Mutating commands
    // =====================================================================

    private static int dayAdvanceOne(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        mgr.forceDayAdvance(level);
        src.sendSuccess(() -> Component.literal("Advanced one day. lastDay=" + mgr.getLastDay()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int dayAdvanceN(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        int n = IntegerArgumentType.getInteger(ctx, "n");
        for (int i = 0; i < n; i++) {
            mgr.forceDayAdvance(level);
        }
        src.sendSuccess(() -> Component.literal("Advanced " + n + " day(s). lastDay=" + mgr.getLastDay()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int polSwear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        int vassalId = IntegerArgumentType.getInteger(ctx, "vassalId");
        int lordId = IntegerArgumentType.getInteger(ctx, "lordId");

        Optional<Polity> vassalOpt = mgr.getPolity(vassalId);
        Optional<Polity> lordOpt = mgr.getPolity(lordId);
        if (vassalOpt.isEmpty() || lordOpt.isEmpty()) {
            src.sendFailure(Component.literal("Vassal or lord polity does not exist."));
            return 0;
        }
        if (vassalId == lordId) {
            src.sendFailure(Component.literal("A polity cannot swear to itself."));
            return 0;
        }
        if (mgr.getAllegianceFor(vassalId).isPresent()) {
            src.sendFailure(Component.literal("Vassal is already sworn to another lord."));
            return 0;
        }
        long currentDay = PoliticsManager.computeCurrentDay(level);
        boolean ok = mgr.swearFealty(vassalId, lordId, currentDay);
        if (!ok) {
            src.sendFailure(Component.literal("Swear rejected (would create cycle or other invalid state)."));
            return 0;
        }
        String vassalDisplay = mgr.getPolityDisplayName(vassalOpt.get());
        String lordDisplay = mgr.getPolityDisplayName(lordOpt.get());
        src.sendSuccess(() -> Component.literal(vassalDisplay + " has sworn fealty to " + lordDisplay + ".").withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int polRevoke(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        int vassalId = IntegerArgumentType.getInteger(ctx, "vassalId");
        Optional<Polity> vassalOpt = mgr.getPolity(vassalId);
        if (vassalOpt.isEmpty()) {
            src.sendFailure(Component.literal("No polity with id " + vassalId));
            return 0;
        }
        Optional<Allegiance> existing = mgr.getAllegianceFor(vassalId);
        if (existing.isEmpty()) {
            src.sendFailure(Component.literal("Polity " + vassalId + " has no liege to revoke."));
            return 0;
        }
        mgr.revokeFealty(vassalId);
        String vassalDisplay = mgr.getPolityDisplayName(vassalOpt.get());
        src.sendSuccess(() -> Component.literal(vassalDisplay + " has revoked fealty.").withStyle(ChatFormatting.YELLOW), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int polRename(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        int id = IntegerArgumentType.getInteger(ctx, "id");
        String newName = StringArgumentType.getString(ctx, "newName").trim();
        Optional<Polity> opt = mgr.getPolity(id);
        if (opt.isEmpty()) {
            src.sendFailure(Component.literal("No polity with id " + id));
            return 0;
        }
        if (newName.isEmpty()) {
            src.sendFailure(Component.literal("New name cannot be empty."));
            return 0;
        }
        Polity p = opt.get();
        String oldName = p.getName();
        p.setName(newName);
        mgr.setDirty();
        String newDisplay = mgr.getPolityDisplayName(p);
        src.sendSuccess(() -> Component.literal("Polity " + id + " renamed: " + oldName + " → " + newName + " (" + newDisplay + ")"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int listEmbodiments(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);

        if (mgr.getPolities().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No polities exist.").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }

        for (Polity p : mgr.getPolities()) {
            int leaderId = p.getLeaderCharacterId();
            if (leaderId == Polity.LEADER_VACANT) {
                src.sendSuccess(() -> Component.literal(String.format("[%d] %s: (vacant)", p.getId(), p.getName())), false);
                continue;
            }
            Character leader = mgr.getCharacter(leaderId).orElse(null);
            if (leader == null) {
                src.sendSuccess(() -> Component.literal(String.format("[%d] %s: leader missing (id %d)", p.getId(), p.getName(), leaderId)), false);
                continue;
            }
            UUID uuid = mgr.getEmbodimentUuid(leaderId).orElse(null);
            String leaderName = leader.getName();
            if (uuid == null) {
                src.sendSuccess(() -> Component.literal(String.format("[%d] %s: %s — unbound", p.getId(), p.getName(), leaderName)), false);
                continue;
            }
            Entity e = level.getEntity(uuid);
            if (e == null || e.isRemoved()) {
                src.sendSuccess(() -> Component.literal(String.format("[%d] %s: %s — bound (entity not loaded)", p.getId(), p.getName(), leaderName)), false);
            } else {
                String loc = e.blockPosition().toShortString();
                src.sendSuccess(() -> Component.literal(String.format("[%d] %s: %s at %s", p.getId(), p.getName(), leaderName, loc)), false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int polRebind(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        int id = IntegerArgumentType.getInteger(ctx, "id");

        Optional<Polity> opt = mgr.getPolity(id);
        if (opt.isEmpty()) {
            src.sendFailure(Component.literal("No polity with id " + id));
            return 0;
        }
        Polity p = opt.get();
        if (p.getLeaderCharacterId() == Polity.LEADER_VACANT) {
            src.sendFailure(Component.literal("Polity " + id + " has no leader to rebind (wait for the next day tick to generate one)."));
            return 0;
        }

        mgr.unbind(p.getLeaderCharacterId());
        mgr.reconcileEmbodiments(level);

        UUID newUuid = mgr.getEmbodimentUuid(p.getLeaderCharacterId()).orElse(null);
        if (newUuid != null) {
            Entity e = level.getEntity(newUuid);
            String loc = (e != null && !e.isRemoved()) ? e.blockPosition().toShortString() : "(not loaded)";
            src.sendSuccess(() -> Component.literal("Rebound polity " + id + " leader to villager at " + loc), true);
        } else {
            src.sendSuccess(() -> Component.literal("Polity " + id + " leader is unbound — no candidate villager found in the village bounds."), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int polSetTax(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        int id = IntegerArgumentType.getInteger(ctx, "id");
        float rate = FloatArgumentType.getFloat(ctx, "rate");
        Optional<Polity> opt = mgr.getPolity(id);
        if (opt.isEmpty()) {
            src.sendFailure(Component.literal("No polity with id " + id));
            return 0;
        }
        opt.get().setTaxRate(rate);
        mgr.setDirty();
        src.sendSuccess(() -> Component.literal(String.format("Polity %d tax rate set to %.0f%%", id, rate * 100f)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int polSetTribute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PoliticsManager mgr = PoliticsManager.get(level);
        int id = IntegerArgumentType.getInteger(ctx, "id");
        float rate = FloatArgumentType.getFloat(ctx, "rate");
        Optional<Polity> opt = mgr.getPolity(id);
        if (opt.isEmpty()) {
            src.sendFailure(Component.literal("No polity with id " + id));
            return 0;
        }
        opt.get().setTributeRate(rate);
        mgr.setDirty();
        src.sendSuccess(() -> Component.literal(String.format("Polity %d tribute rate set to %.0f%%", id, rate * 100f)), true);
        return Command.SINGLE_SUCCESS;
    }
}
