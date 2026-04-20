package com.minekings.minekings.politics.embodiment;

import com.minekings.minekings.politics.Character;
import com.minekings.minekings.politics.Polity;
import net.minecraft.world.entity.npc.Villager;

import java.util.Optional;

/**
 * Presentation layer for the embodiment system. Abstracts how a
 * {@link Character}'s identity is written onto (and read back from) a
 * {@link Villager} so that third-party villager mods (notably MCA Reborn)
 * can participate through their own channels instead of fighting vanilla
 * {@code setCustomName}.
 *
 * <p>Exactly one implementation is active per JVM, selected at
 * {@code FMLCommonSetupEvent} via {@link Embodiment#init()} and held in
 * {@link Embodiment#ACTIVE}. Implementations must be stateless.
 */
public interface EmbodimentAdapter {
    /**
     * Apply the leader's display identity (culture title + character name)
     * to a villager. Called on bind and on hierarchy refreshes.
     */
    void applyLeaderIdentity(Villager villager, String leaderTitle, Character character);

    /**
     * Reverse of {@link #applyLeaderIdentity}. Called on unbind and when a
     * tagged villager is discovered to be stale on chunk load.
     */
    void clearLeaderIdentity(Villager villager);

    /**
     * Preference score in [0, 1] for picking this villager as the
     * embodiment of {@code character} in {@code polity}. Higher wins.
     * Ties broken randomly by caller.
     */
    default float candidateScore(Villager villager, Polity polity, Character character) {
        return 0.5f;
    }

    /**
     * Pre-existing "given name" the villager carries through another
     * system (e.g. MCA's family tree). MineKings may adopt this as the
     * Character's name when binding to an already-named villager.
     */
    default Optional<String> readExistingName(Villager villager) {
        return Optional.empty();
    }

    /**
     * Adapter-defined personality string. Opaque to {@code PoliticsManager}
     * today; reserved for a future character-trait system.
     */
    default Optional<String> readPersonalityHint(Villager villager) {
        return Optional.empty();
    }
}
