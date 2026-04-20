package com.minekings.minekings.politics.compat.mca;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.politics.Character;
import com.minekings.minekings.politics.Polity;
import com.minekings.minekings.politics.embodiment.EmbodimentAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

/**
 * Embodiment adapter for MCA Reborn ({@code net.conczin.mca}, GPL v3),
 * resolved entirely through reflection so MineKings ships as a single jar
 * that lights up automatically when MCA is in the mods folder and
 * degrades silently when it isn't.
 *
 * <p>All reflective lookups happen once in the constructor and are cached
 * as {@link MethodHandle}s (roughly as fast as direct calls after JIT).
 * If any lookup fails — MCA missing, API renamed, signature changed —
 * construction throws and {@code Embodiment.init} falls back to the
 * vanilla adapter.
 *
 * <h2>Target API (MCA source, {@code net.conczin.mca}):</h2>
 * <ul>
 *   <li>{@code entity.VillagerEntityMCA extends Villager}
 *       — {@code VillagerBrain<?> getVillagerBrain()}</li>
 *   <li>{@code entity.ai.relationship.EntityRelationship}
 *       — {@code static Optional<EntityRelationship> of(Entity)},
 *         {@code FamilyTreeNode getFamilyEntry()}</li>
 *   <li>{@code server.world.data.FamilyTreeNode}
 *       — {@code String getName()}, {@code void setName(String)}</li>
 *   <li>{@code entity.ai.brain.VillagerBrain}
 *       — {@code Personality getPersonality()}</li>
 *   <li>{@code entity.ai.relationship.Personality extends Enum}</li>
 * </ul>
 */
public final class MCAEmbodimentAdapter implements EmbodimentAdapter {
    private final Class<?> villagerMcaClass;
    private final MethodHandle entityRelationshipOf;       // static: (Entity) -> Optional<EntityRelationship>
    private final MethodHandle getFamilyEntry;              // (EntityRelationship) -> FamilyTreeNode
    private final MethodHandle familyGetName;               // (FamilyTreeNode) -> String
    private final MethodHandle familySetName;               // (FamilyTreeNode, String) -> void
    private final MethodHandle getVillagerBrain;            // (VillagerEntityMCA) -> VillagerBrain
    private final MethodHandle brainGetPersonality;         // (VillagerBrain) -> Personality

    public MCAEmbodimentAdapter() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();

        this.villagerMcaClass = Class.forName("net.conczin.mca.entity.VillagerEntityMCA");
        Class<?> entityRelationshipClass = Class.forName("net.conczin.mca.entity.ai.relationship.EntityRelationship");
        Class<?> familyTreeNodeClass = Class.forName("net.conczin.mca.server.world.data.FamilyTreeNode");
        Class<?> villagerBrainClass = Class.forName("net.conczin.mca.entity.ai.brain.VillagerBrain");

        this.entityRelationshipOf = lookup.findStatic(
                entityRelationshipClass, "of",
                MethodType.methodType(Optional.class, Entity.class));
        this.getFamilyEntry = lookup.findVirtual(
                entityRelationshipClass, "getFamilyEntry",
                MethodType.methodType(familyTreeNodeClass));
        this.familyGetName = lookup.findVirtual(
                familyTreeNodeClass, "getName",
                MethodType.methodType(String.class));
        this.familySetName = lookup.findVirtual(
                familyTreeNodeClass, "setName",
                MethodType.methodType(void.class, String.class));
        this.getVillagerBrain = lookup.findVirtual(
                villagerMcaClass, "getVillagerBrain",
                MethodType.methodType(villagerBrainClass));
        this.brainGetPersonality = lookup.findVirtual(
                villagerBrainClass, "getPersonality",
                MethodType.methodType(Class.forName("net.conczin.mca.entity.ai.relationship.Personality")));

        MineKings.LOGGER.info("MineKings: MCA reflection bindings resolved.");
    }

    @Override
    public void applyLeaderIdentity(Villager villager, String leaderTitle, Character character) {
        villager.setCustomName(Component.literal(leaderTitle + " " + character.getName()));
        villager.setCustomNameVisible(true);

        if (!villagerMcaClass.isInstance(villager)) return;
        Object familyEntry = resolveFamilyEntry(villager);
        if (familyEntry == null) return;
        try {
            familySetName.invoke(familyEntry, character.getName());
        } catch (Throwable t) {
            MineKings.LOGGER.debug("MCA setName failed on {}", villager.getUUID(), t);
        }
    }

    @Override
    public void clearLeaderIdentity(Villager villager) {
        villager.setCustomName(null);
        villager.setCustomNameVisible(false);
    }

    @Override
    public float candidateScore(Villager villager, Polity polity, Character character) {
        if (villager.isBaby()) return 0.3f;
        if (!villagerMcaClass.isInstance(villager)) return 0.5f;
        return readExistingName(villager).isPresent() ? 0.8f : 0.7f;
    }

    @Override
    public Optional<String> readExistingName(Villager villager) {
        if (!villagerMcaClass.isInstance(villager)) return Optional.empty();
        Object familyEntry = resolveFamilyEntry(villager);
        if (familyEntry == null) return Optional.empty();
        try {
            String name = (String) familyGetName.invoke(familyEntry);
            return (name == null || name.isBlank()) ? Optional.empty() : Optional.of(name);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> readPersonalityHint(Villager villager) {
        if (!villagerMcaClass.isInstance(villager)) return Optional.empty();
        try {
            Object brain = getVillagerBrain.invoke(villager);
            if (brain == null) return Optional.empty();
            Object personality = brainGetPersonality.invoke(brain);
            if (personality == null) return Optional.empty();
            return Optional.of(((Enum<?>) personality).name());
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** Resolves {@code EntityRelationship.of(villager).getFamilyEntry()} or null. */
    private Object resolveFamilyEntry(Villager villager) {
        try {
            Optional<?> rel = (Optional<?>) entityRelationshipOf.invoke((Entity) villager);
            if (rel.isEmpty()) return null;
            return getFamilyEntry.invoke(rel.get());
        } catch (Throwable t) {
            return null;
        }
    }
}
