package com.minekings.minekings.politics;

import com.minekings.minekings.MineKings;
import com.minekings.minekings.village.Building;
import com.minekings.minekings.village.Village;
import com.minekings.minekings.village.VillageManager;
import com.minekings.minekings.village.util.MKWorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Server-side SavedData holding the political state of a level: all polities,
 * characters, and allegiances, plus a day counter used to gate day-tick work.
 *
 * <p>Loaded once per level via {@link #get(ServerLevel)} which mirrors the
 * v0.1 {@code VillageManager.get} pattern. The save key is
 * {@code "minekings_politics"} — independent of v0.1's
 * {@code "minekings_villages"}.
 *
 * <p>All mutations happen on the server thread (from
 * {@code LevelTickEvent.Post} or from Brigadier command execution), so no
 * concurrent collections are used.
 */
public class PoliticsManager extends SavedData {
    private final ServerLevel world;
    private final Map<Integer, Polity> polities = new HashMap<>();
    private final Map<Integer, Character> characters = new HashMap<>();
    private final List<Allegiance> allegiances = new ArrayList<>();
    /** characterId → villager entity UUID. Serialized. */
    private final Map<Integer, UUID> embodiments = new HashMap<>();
    /** Reverse index (UUID → characterId). Derived from {@link #embodiments}, not serialized. */
    private final Map<UUID, Integer> embodiedBy = new HashMap<>();
    private long lastDay = -1L;
    private int nextPolityId = 0;
    private int nextCharacterId = 0;

    PoliticsManager(ServerLevel world) {
        this.world = world;
    }

    PoliticsManager(ServerLevel world, CompoundTag nbt) {
        this.world = world;
        this.lastDay = nbt.contains("lastDay") ? nbt.getLong("lastDay") : -1L;
        this.nextPolityId = nbt.getInt("nextPolityId");
        this.nextCharacterId = nbt.getInt("nextCharacterId");

        ListTag polityList = nbt.getList("polities", Tag.TAG_COMPOUND);
        for (int i = 0; i < polityList.size(); i++) {
            Polity p = new Polity(polityList.getCompound(i));
            polities.put(p.getId(), p);
        }
        ListTag characterList = nbt.getList("characters", Tag.TAG_COMPOUND);
        for (int i = 0; i < characterList.size(); i++) {
            Character c = new Character(characterList.getCompound(i));
            characters.put(c.getId(), c);
        }
        ListTag allegianceList = nbt.getList("allegiances", Tag.TAG_COMPOUND);
        for (int i = 0; i < allegianceList.size(); i++) {
            allegiances.add(Allegiance.load(allegianceList.getCompound(i)));
        }

        if (nbt.contains("embodiments")) {
            CompoundTag embodimentsNbt = nbt.getCompound("embodiments");
            for (String key : embodimentsNbt.getAllKeys()) {
                if (embodimentsNbt.hasUUID(key)) {
                    try {
                        int characterId = Integer.parseInt(key);
                        UUID uuid = embodimentsNbt.getUUID(key);
                        embodiments.put(characterId, uuid);
                        embodiedBy.put(uuid, characterId);
                    } catch (NumberFormatException e) {
                        MineKings.LOGGER.warn("Ignoring malformed embodiment key: {}", key);
                    }
                }
            }
        }
    }

    public static PoliticsManager get(ServerLevel world) {
        return MKWorldUtils.loadData(
                world,
                (nbt, provider) -> new PoliticsManager(world, nbt),
                PoliticsManager::new,
                "minekings_politics"
        );
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.putLong("lastDay", lastDay);
        nbt.putInt("nextPolityId", nextPolityId);
        nbt.putInt("nextCharacterId", nextCharacterId);

        ListTag polityList = new ListTag();
        for (Polity p : polities.values()) polityList.add(p.save());
        nbt.put("polities", polityList);

        ListTag characterList = new ListTag();
        for (Character c : characters.values()) characterList.add(c.save());
        nbt.put("characters", characterList);

        ListTag allegianceList = new ListTag();
        for (Allegiance a : allegiances) allegianceList.add(a.save());
        nbt.put("allegiances", allegianceList);

        CompoundTag embodimentsNbt = new CompoundTag();
        for (Map.Entry<Integer, UUID> e : embodiments.entrySet()) {
            embodimentsNbt.putUUID(String.valueOf(e.getKey()), e.getValue());
        }
        nbt.put("embodiments", embodimentsNbt);

        return nbt;
    }

    public static long computeCurrentDay(ServerLevel level) {
        return level.getGameTime() / Level.TICKS_PER_DAY;
    }

    public long getLastDay() { return lastDay; }

    public Collection<Polity> getPolities() { return polities.values(); }
    public Collection<Character> getCharacters() { return characters.values(); }
    public List<Allegiance> getAllegiances() { return allegiances; }

    public Optional<Polity> getPolity(int id) { return Optional.ofNullable(polities.get(id)); }
    public Optional<Character> getCharacter(int id) { return Optional.ofNullable(characters.get(id)); }

    /** Derived age in game days. */
    public long getAge(Character c, long currentDay) {
        return c.getAge(currentDay);
    }

    /** Finds the polity currently holding a given village, if any. */
    public Optional<Polity> findPolityHoldingVillage(int villageId) {
        for (Polity p : polities.values()) {
            if (p.holdsVillage(villageId)) return Optional.of(p);
        }
        return Optional.empty();
    }

    // ----- Allegiance graph queries -----

    public Optional<Allegiance> getAllegianceFor(int vassalId) {
        for (Allegiance a : allegiances) {
            if (a.vassalPolityId() == vassalId) return Optional.of(a);
        }
        return Optional.empty();
    }

    public Stream<Polity> getDirectVassals(int lordId) {
        return allegiances.stream()
                .filter(a -> a.lordPolityId() == lordId)
                .map(a -> polities.get(a.vassalPolityId()))
                .filter(p -> p != null);
    }

    /**
     * Recompute the subtree height of the given polity. Height 0 = no vassals,
     * 1 = one or more vassals which themselves have no vassals, etc. Guarded
     * against cycles (which shouldn't exist — {@link #swearFealty} rejects
     * them — but we defend anyway against NBT corruption).
     */
    public int subtreeHeight(int polityId) {
        return subtreeHeightImpl(polityId, new HashSet<>());
    }

    private int subtreeHeightImpl(int polityId, Set<Integer> visited) {
        if (!visited.add(polityId)) {
            MineKings.LOGGER.error("Cycle detected in allegiance graph at polity {}", polityId);
            return 0;
        }
        int max = -1;
        for (Allegiance a : allegiances) {
            if (a.lordPolityId() == polityId) {
                int child = subtreeHeightImpl(a.vassalPolityId(), visited);
                if (child > max) max = child;
            }
        }
        visited.remove(polityId); // allow other branches to revisit
        return max == -1 ? 0 : 1 + max;
    }

    // ----- Title rendering -----

    public Culture getCultureOf(Polity p) {
        return CultureManager.getInstance().findByName(p.getCultureName())
                .orElseGet(() -> CultureManager.getInstance().assignCultureForBiome(null));
    }

    public String getPolityTitle(Polity p) {
        Culture c = getCultureOf(p);
        int h = subtreeHeight(p.getId());
        return c.tierAt(h);
    }

    public String getLeaderTitle(Polity p) {
        Culture c = getCultureOf(p);
        int h = subtreeHeight(p.getId());
        return c.leaderTitleAt(h);
    }

    public String getPolityDisplayName(Polity p) {
        Culture c = getCultureOf(p);
        String title = c.tierAt(subtreeHeight(p.getId()));
        Character leader = characters.get(p.getLeaderCharacterId());
        String leaderName = leader != null ? leader.getName() : "vacant";
        return c.polityNameFormat()
                .replace("{tier}", title)
                .replace("{place}", p.getName())
                .replace("{leader}", leaderName);
    }

    // ----- Founding -----

    /**
     * Creates a new polity holding the given village, sampling the biome
     * at its center to pick a culture. Also generates a founder character
     * using that culture's name pool.
     */
    public Polity foundPolityForVillage(Village village, long currentDay) {
        // 1. Sample biome at village center.
        BlockPos center = new BlockPos(village.getCenter());
        ResourceLocation biomeId = world.getBiome(center).unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);

        // 2. Pick culture via two-pass match.
        Culture culture = CultureManager.getInstance().assignCultureForBiome(biomeId);

        // 3. Create polity first (no leader yet).
        int polityId = nextPolityId++;
        Polity polity = new Polity(
                polityId,
                village.getName(),
                culture.id(),
                Polity.LEADER_VACANT,
                currentDay
        );
        polity.addVillage(village.getId());

        // 4. Generate founder character and wire it in.
        Character founder = generateCharacter(culture, currentDay, polityId);
        polity.setLeaderCharacterId(founder.getId());

        // 5. Commit.
        polities.put(polityId, polity);
        setDirty();

        return polity;
    }

    /**
     * Generates a new {@link Character} from a culture's name pool and
     * registers them in the characters map. Used at founding, and again
     * during day-tick succession when a polity has a vacant leader slot.
     * Does NOT assign them as leader of any polity — caller handles that.
     */
    private Character generateCharacter(Culture culture, long currentDay, int currentPolityId) {
        String first = pickRandom(culture.firstNames(), "Nameless");
        String last = pickRandom(culture.lastNames(), "One");
        String characterName = (first + " " + last).trim();
        int characterId = nextCharacterId++;
        Character c = new Character(
                characterId,
                characterName,
                culture.id(),
                currentDay - 7300L, // cosmetic age ~20 game years at creation
                currentPolityId
        );
        characters.put(characterId, c);
        return c;
    }

    private String pickRandom(List<String> pool, String fallback) {
        if (pool == null || pool.isEmpty()) return fallback;
        return pool.get(world.random.nextInt(pool.size()));
    }

    /**
     * Sums the {@code dailyIncome} of every building in the village,
     * as defined in the building-type JSONs. Returns the rounded total.
     */
    public static long computeVillageDailyIncome(Village village) {
        double total = 0.0;
        for (Building b : village) {
            total += b.getBuildingType().dailyIncome();
        }
        return Math.round(total);
    }

    // ----- Allegiance mutation -----

    public boolean swearFealty(int vassalId, int lordId, long currentDay) {
        if (vassalId == lordId) return false;
        if (!polities.containsKey(vassalId) || !polities.containsKey(lordId)) return false;
        if (getAllegianceFor(vassalId).isPresent()) return false;
        if (wouldCycle(vassalId, lordId)) return false;
        allegiances.add(new Allegiance(vassalId, lordId, currentDay));
        setDirty();
        // Subtree height of the lord (and everything above it) may have
        // increased. Refresh any bound villager names up the chain.
        refreshHierarchyAbove(lordId);
        return true;
    }

    public boolean revokeFealty(int vassalId) {
        // Capture the old lord BEFORE removing so we can refresh that chain.
        Optional<Allegiance> existing = getAllegianceFor(vassalId);
        if (existing.isEmpty()) return false;
        int oldLordId = existing.get().lordPolityId();
        allegiances.removeIf(a -> a.vassalPolityId() == vassalId);
        setDirty();
        // Subtree height of the old lord (and its ancestors) may have
        // decreased. Refresh names up the chain.
        refreshHierarchyAbove(oldLordId);
        return true;
    }

    private boolean wouldCycle(int vassalId, int lordId) {
        int cur = lordId;
        Set<Integer> seen = new HashSet<>();
        while (true) {
            if (cur == vassalId) return true;
            if (!seen.add(cur)) return true; // pre-existing cycle — refuse
            Optional<Allegiance> up = getAllegianceFor(cur);
            if (up.isEmpty()) return false;
            cur = up.get().lordPolityId();
        }
    }

    // ----- Day tick -----

    /**
     * Runs on every level tick; no-ops unless the integer game day has
     * advanced since the last run. On advance, runs the reconciliation pass
     * (new villages get polities founded), accrues treasury, then broadcasts
     * founding notifications.
     */
    public void dayTick(ServerLevel level) {
        long currentDay = computeCurrentDay(level);
        if (currentDay == lastDay) return;
        runDailyReconciliation(level, currentDay);
    }

    /**
     * The actual per-day work. Extracted so {@link #forceDayAdvance} can
     * reuse it without duplicating every step.
     */
    private void runDailyReconciliation(ServerLevel level, long currentDay) {
        // 1. Reconcile villages → polities (auto-founding)
        VillageManager vm = VillageManager.get(level);
        List<Polity> newlyFounded = new ArrayList<>();
        for (Village v : vm) {
            if (findPolityHoldingVillage(v.getId()).isEmpty()) {
                newlyFounded.add(foundPolityForVillage(v, currentDay));
            }
        }

        // 2. Succession — any polity with a vacant leader slot gets a new
        // character generated. Binding to a villager happens in step 4.
        List<Polity> successions = new ArrayList<>();
        for (Polity p : polities.values()) {
            if (p.getLeaderCharacterId() == Polity.LEADER_VACANT) {
                Culture culture = getCultureOf(p);
                Character successor = generateCharacter(culture, currentDay, p.getId());
                p.setLeaderCharacterId(successor.getId());
                successions.add(p);
            }
        }

        // 3. Economy pass: production → village stockpile → polity treasury → tribute
        VillageManager vmEcon = VillageManager.get(level);
        // 3a. Buildings produce into village stockpiles
        for (Village v : vmEcon) {
            long income = computeVillageDailyIncome(v);
            if (income != 0L) {
                v.addStockpile(income);
            }
        }
        // 3b. Polity taxation — skim taxRate from each held village's stockpile
        for (Polity p : polities.values()) {
            for (int villageId : p.getHeldVillageIds()) {
                vmEcon.getOrEmpty(villageId).ifPresent(v -> {
                    long skim = (long) Math.floor(v.getStockpile() * p.getTaxRate());
                    if (skim > 0L) {
                        v.addStockpile(-skim);
                        p.addTreasury(skim);
                    }
                });
            }
        }
        vmEcon.setDirty(); // village stockpiles changed
        // 3c. Tribute flow — vassal treasury → lord treasury
        for (Allegiance a : allegiances) {
            Polity vassal = polities.get(a.vassalPolityId());
            Polity lord = polities.get(a.lordPolityId());
            if (vassal != null && lord != null && vassal.getTreasury() > 0) {
                long tribute = (long) Math.floor(vassal.getTreasury() * vassal.getTributeRate());
                if (tribute > 0L) {
                    vassal.addTreasury(-tribute);
                    lord.addTreasury(tribute);
                }
            }
        }

        // (Character aging is derived from birthDay, not stored.)

        lastDay = currentDay;
        setDirty();

        // 4. Bind any unbound polities immediately so the chat broadcasts
        // below refer to visible villagers where possible.
        reconcileEmbodiments(level);

        // 5. Broadcasts last, after state is consistent.
        for (Polity p : newlyFounded) {
            broadcastFounding(level, p);
        }
        for (Polity p : successions) {
            broadcastSuccession(level, p);
        }
    }

    /**
     * Forces one reconciliation pass without waiting for a game-day boundary.
     * Used by the {@code /minekings day advance} command. Uses a synthetic
     * "next day" increment so treasury still ticks and succession fires.
     */
    public void forceDayAdvance(ServerLevel level) {
        long synthetic = Math.max(computeCurrentDay(level), lastDay + 1);
        runDailyReconciliation(level, synthetic);
    }

    private void broadcastFounding(ServerLevel level, Polity p) {
        Character leader = characters.get(p.getLeaderCharacterId());
        String leaderTitle = getLeaderTitle(p);
        String polityDisplay = getPolityDisplayName(p);
        String leaderName = leader != null ? leader.getName() : "(vacant)";

        Component msg = Component.literal("[MineKings] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("The "))
                .append(Component.literal(polityDisplay).withStyle(ChatFormatting.ITALIC))
                .append(Component.literal(" has been founded under "))
                .append(Component.literal(leaderTitle + " " + leaderName).withStyle(ChatFormatting.BOLD))
                .append(Component.literal("."));

        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    private void broadcastSuccession(ServerLevel level, Polity p) {
        Character leader = characters.get(p.getLeaderCharacterId());
        if (leader == null) return;
        String leaderTitle = getLeaderTitle(p);
        String polityDisplay = getPolityDisplayName(p);

        Component msg = Component.literal("[MineKings] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(leaderTitle + " " + leader.getName()).withStyle(ChatFormatting.BOLD))
                .append(Component.literal(" has taken the throne of the "))
                .append(Component.literal(polityDisplay).withStyle(ChatFormatting.ITALIC))
                .append(Component.literal("."));

        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    private void broadcastDeath(ServerLevel level, Polity p, Character deceased, String leaderTitleBeforeDeath) {
        String polityDisplay = getPolityDisplayName(p);

        Component msg = Component.literal("[MineKings] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(leaderTitleBeforeDeath + " " + deceased.getName()).withStyle(ChatFormatting.BOLD))
                .append(Component.literal(" has been slain. The "))
                .append(Component.literal(polityDisplay).withStyle(ChatFormatting.ITALIC))
                .append(Component.literal(" is without a ruler."));

        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    // =====================================================================
    // Embodiment (villager ↔ character binding)
    // =====================================================================

    public Optional<UUID> getEmbodimentUuid(int characterId) {
        return Optional.ofNullable(embodiments.get(characterId));
    }

    /** Reverse lookup: given a villager UUID, returns the characterId they embody, or null. */
    public Integer getEmbodiedBy(UUID villagerUuid) {
        return embodiedBy.get(villagerUuid);
    }

    /**
     * Binds a character to a specific villager entity. If the character was
     * already bound to another villager, that earlier binding is cleared
     * first (idempotency). Also sets the villager's custom name from the
     * character + culture tier.
     */
    public void bind(Polity polity, Character character, Villager villager) {
        int characterId = character.getId();
        if (embodiments.containsKey(characterId)) {
            unbind(characterId);
        }
        embodiments.put(characterId, villager.getUUID());
        embodiedBy.put(villager.getUUID(), characterId);
        refreshName(polity, character, villager);
        setDirty();
    }

    /**
     * Clears the binding for a character. If the bound villager is still
     * loaded, also clears its custom name.
     */
    public void unbind(int characterId) {
        UUID uuid = embodiments.remove(characterId);
        if (uuid == null) return;
        embodiedBy.remove(uuid);
        Entity e = world.getEntity(uuid);
        if (e instanceof Villager v && !v.isRemoved()) {
            v.setCustomName(null);
            v.setCustomNameVisible(false);
        }
        setDirty();
    }

    /** Applies the culture-appropriate leader title + character name to a villager. */
    public void refreshName(Polity p, Character c, Villager v) {
        String title = getLeaderTitle(p);
        Component name = Component.literal(title + " " + c.getName());
        v.setCustomName(name);
        v.setCustomNameVisible(true);
    }

    /**
     * Per-100-tick pass. For each polity with a living leader who isn't
     * currently embodied by a loaded villager, tries to find a candidate
     * villager in the polity's villages and bind it.
     */
    public void reconcileEmbodiments(ServerLevel level) {
        for (Polity p : polities.values()) {
            int characterId = p.getLeaderCharacterId();
            if (characterId == Polity.LEADER_VACANT) continue; // succession runs on dayTick
            Character leader = characters.get(characterId);
            if (leader == null || !leader.isAlive()) continue;

            UUID boundUuid = embodiments.get(characterId);
            if (boundUuid != null) {
                Entity e = level.getEntity(boundUuid);
                if (e instanceof Villager v && !v.isRemoved()) {
                    continue; // still valid, nothing to do
                }
                // Stale entry — clear it silently (don't call unbind because
                // the entity is gone and we don't want to clear anyone's
                // custom name as a side effect).
                embodiments.remove(characterId);
                embodiedBy.remove(boundUuid);
                setDirty();
            }

            Villager candidate = findCandidateVillager(level, p);
            if (candidate != null) {
                bind(p, leader, candidate);
            }
        }
    }

    /**
     * Searches each of the polity's held villages for an unbound villager
     * that isn't already embodying another character. No age filter —
     * babies are eligible (the "child monarch" scenario is in scope).
     */
    private Villager findCandidateVillager(ServerLevel level, Polity polity) {
        VillageManager vm = VillageManager.get(level);
        for (int villageId : polity.getHeldVillageIds()) {
            Optional<Village> vOpt = vm.getOrEmpty(villageId);
            if (vOpt.isEmpty()) continue;
            BoundingBox box = vOpt.get().getBox().inflatedBy(16);
            AABB aabb = new AABB(
                    box.minX(), box.minY(), box.minZ(),
                    box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1
            );
            List<Villager> candidates = level.getEntitiesOfClass(
                    Villager.class,
                    aabb,
                    v -> !v.isRemoved() && !embodiedBy.containsKey(v.getUUID())
            );
            if (!candidates.isEmpty()) {
                return candidates.get(level.random.nextInt(candidates.size()));
            }
        }
        return null;
    }

    /**
     * Called from {@link PoliticsEntityEvents}'s death handler. Marks the
     * leader character as dead, vacates the polity's leader slot, unbinds
     * the villager, and broadcasts. Succession fires on the next day tick.
     */
    public void handleLeaderDeath(ServerLevel level, UUID villagerUuid) {
        Integer characterId = embodiedBy.get(villagerUuid);
        if (characterId == null) return;
        Character character = characters.get(characterId);
        if (character == null) return;

        // Find the polity this character led.
        Polity leadingPolity = null;
        for (Polity p : polities.values()) {
            if (p.getLeaderCharacterId() == characterId) {
                leadingPolity = p;
                break;
            }
        }

        // Capture title BEFORE we vacate the leader slot — the title
        // depends on subtree height (unchanged by death) but it's cleaner
        // to snapshot it before any mutation.
        String leaderTitleBeforeDeath = leadingPolity != null ? getLeaderTitle(leadingPolity) : "Leader";

        long currentDay = computeCurrentDay(level);
        character.setDeathDay(currentDay);

        if (leadingPolity != null) {
            leadingPolity.setLeaderCharacterId(Polity.LEADER_VACANT);
            // Unbind (removes both maps and clears custom name if still there).
            unbind(characterId);
            broadcastDeath(level, leadingPolity, character, leaderTitleBeforeDeath);
        } else {
            // Character wasn't a leader anywhere — defensive, shouldn't happen in v0.3.
            unbind(characterId);
        }
        setDirty();
    }

    // =====================================================================
    // Hierarchy refresh (called from swearFealty / revokeFealty)
    // =====================================================================

    /**
     * Walks from {@code startPolityId} up the allegiance chain, refreshing
     * the custom name of every bound leader along the way. Used after a
     * swear or revoke changes the subtree height of the affected chain.
     */
    private void refreshHierarchyAbove(int startPolityId) {
        Set<Integer> visited = new HashSet<>();
        int cur = startPolityId;
        while (cur != -1 && visited.add(cur)) {
            Polity p = polities.get(cur);
            if (p == null) break;
            refreshPolityNameIfBound(p);
            Optional<Allegiance> up = getAllegianceFor(cur);
            if (up.isEmpty()) break;
            cur = up.get().lordPolityId();
        }
    }

    private void refreshPolityNameIfBound(Polity p) {
        Character leader = characters.get(p.getLeaderCharacterId());
        if (leader == null) return;
        UUID uuid = embodiments.get(leader.getId());
        if (uuid == null) return;
        Entity e = world.getEntity(uuid);
        if (e instanceof Villager v && !v.isRemoved()) {
            refreshName(p, leader, v);
        }
    }
}
