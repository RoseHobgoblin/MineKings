package com.minekings.minekings.village;

import com.minekings.minekings.politics.PoliticsManager;
import com.minekings.minekings.registry.MineKingsBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Invisible data block entity whose only job is to self-register the
 * surrounding village — and auto-found an NPC-led polity — when the
 * chunk loads. Idempotent: running twice on the same position is a
 * no-op.
 *
 * <p>Every village always has a ruler. Player placement results in an
 * NPC baron/thane; the player then right-clicks the stone to depose
 * and claim it. See {@link FoundingStoneBlock#useWithoutItem}.
 */
public class FoundingStoneBlockEntity extends BlockEntity {
    private boolean registrationAttempted = false;

    public FoundingStoneBlockEntity(BlockPos pos, BlockState state) {
        super(MineKingsBlockEntities.FOUNDING_STONE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        tryRegister();
    }

    private void tryRegister() {
        if (registrationAttempted) return;
        Level level = this.level;
        if (!(level instanceof ServerLevel serverLevel)) return;
        registrationAttempted = true;

        VillageManager manager = VillageManager.get(serverLevel);
        Optional<Village> existing = manager.findNearestVillage(getBlockPos(), 0);
        if (existing.isPresent()) return;

        Village village = manager.register(getBlockPos(), null);

        // Auto-found an NPC-led polity so the village has a leader
        // immediately. Player takeover happens via right-click deposition.
        PoliticsManager pm = PoliticsManager.get(serverLevel);
        long currentDay = serverLevel.getDayTime() / 24000L;
        pm.foundPolityForVillage(village, currentDay);
    }
}
