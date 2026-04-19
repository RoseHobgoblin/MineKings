package com.minekings.minekings.village;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * A placed Founding Stone declares a new village centered at its position.
 * Right-click empty-handed: if no village already claims this spot, a new
 * village is registered with an auto-generated name. Re-clicking inside an
 * existing village reports its identity.
 */
public class FoundingStoneBlock extends Block {
    public FoundingStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        VillageManager manager = VillageManager.get(serverLevel);

        Optional<Village> existing = manager.findNearestVillage(pos, 0);
        if (existing.isPresent()) {
            Village v = existing.get();
            player.sendSystemMessage(Component.literal(
                    "This land already belongs to " + v.getName() + " (id " + v.getId() + ")."
            ));
            return InteractionResult.CONSUME;
        }

        Village v = manager.register(pos, null);
        player.sendSystemMessage(Component.literal(
                "Founded village: " + v.getName() + " (id " + v.getId() + ")."
        ));
        return InteractionResult.CONSUME;
    }
}
