package com.minekings.minekings.village;

import com.minekings.minekings.politics.Character;
import com.minekings.minekings.politics.Polity;
import com.minekings.minekings.politics.PoliticsEntityEvents;
import com.minekings.minekings.politics.PoliticsManager;
import com.minekings.minekings.politics.PolityViewPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Founding Stone. The village registers and an NPC polity auto-founds
 * when the stone loads (see {@link FoundingStoneBlockEntity}).
 *
 * <p>Right-click:
 * <ul>
 *   <li>NPC-led polity → depose. You replace the baron.</li>
 *   <li>Your own polity → open polity view.</li>
 *   <li>Another player's polity → open polity view (read-only for now).</li>
 * </ul>
 */
public class FoundingStoneBlock extends Block implements EntityBlock {
    public FoundingStoneBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FoundingStoneBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel serverLevel = (ServerLevel) level;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.CONSUME;

        VillageManager vm = VillageManager.get(serverLevel);
        Optional<Village> villageOpt = vm.findNearestVillage(pos, 0);
        if (villageOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal("Founding Stone is still settling — try again in a moment."));
            return InteractionResult.CONSUME;
        }
        Village village = villageOpt.get();
        PoliticsManager mgr = PoliticsManager.get(serverLevel);
        long currentDay = serverLevel.getDayTime() / 24000L;

        Optional<Polity> heldOpt = mgr.findPolityHoldingVillage(village.getId());
        if (heldOpt.isEmpty()) {
            // Safety net: village exists without a polity (shouldn't happen
            // post-auto-found, but handle gracefully by founding as player).
            Polity polity = mgr.foundPlayerPolity(village, serverPlayer, currentDay);
            player.sendSystemMessage(Component.literal(
                    "You claim " + village.getName() + " as "
                    + mgr.getLeaderTitle(polity) + " " + serverPlayer.getGameProfile().getName()
                    + " of " + mgr.getPolityDisplayName(polity) + "."));
            return InteractionResult.CONSUME;
        }

        Polity polity = heldOpt.get();
        Character leader = mgr.getCharacter(polity.getLeaderCharacterId()).orElse(null);

        if (leader != null && !leader.isPlayerBacked()) {
            // Depose NPC.
            String oldTitle = mgr.getLeaderTitle(polity) + " " + leader.getName();
            mgr.deposeAndClaim(polity, serverPlayer, currentDay);
            String newTitle = mgr.getLeaderTitle(polity) + " " + serverPlayer.getGameProfile().getName();
            player.sendSystemMessage(Component.literal(
                    "You depose " + oldTitle + " and seize " + village.getName() + " as " + newTitle + "."));
            return InteractionResult.CONSUME;
        }

        // Player-led (self or other) — show the polity view.
        PolityViewPayload payload = PoliticsEntityEvents.buildPayload(mgr, polity, serverLevel);
        PacketDistributor.sendToPlayer(serverPlayer, payload);
        return InteractionResult.CONSUME;
    }
}
