package com.minekings.minekings.politics.embodiment;

import com.minekings.minekings.politics.Character;
import com.minekings.minekings.politics.Polity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;

/**
 * Fallback adapter used when no villager-overhaul mod is detected.
 * Preserves MineKings v0.5 behavior: identity lives entirely in the
 * villager's custom name.
 */
public final class VanillaEmbodimentAdapter implements EmbodimentAdapter {
    @Override
    public void applyLeaderIdentity(Villager villager, String leaderTitle, Character character) {
        villager.setCustomName(Component.literal(leaderTitle + " " + character.getName()));
        villager.setCustomNameVisible(true);
    }

    @Override
    public void clearLeaderIdentity(Villager villager) {
        villager.setCustomName(null);
        villager.setCustomNameVisible(false);
    }

    @Override
    public float candidateScore(Villager villager, Polity polity, Character character) {
        return villager.isBaby() ? 0.3f : 0.5f;
    }
}
