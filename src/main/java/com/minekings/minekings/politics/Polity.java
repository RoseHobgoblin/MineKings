package com.minekings.minekings.politics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A political unit. Holds one or more village IDs, has a leader (a
 * {@link Character}), a treasury, and a culture. Does not store its own
 * "rank" — the tier label shown to players is computed at render time from
 * the polity's subtree height in the allegiance graph, looked up against
 * its {@link Culture}'s tier list.
 *
 * <p>Conquest does NOT change {@code cultureName}. A subjugated polity
 * keeps its cultural identity regardless of who its liege is.
 */
public final class Polity {
    public static final int LEADER_VACANT = -1;
    public static final float DEFAULT_TAX_RATE = 0.25f;
    public static final float DEFAULT_TRIBUTE_RATE = 0.10f;

    private final int id;
    private String name;
    private final String cultureName;
    private int leaderCharacterId;
    private final List<Integer> heldVillageIds;
    private long treasury;
    private float taxRate;
    private float tributeRate;
    private final long foundedOnDay;

    public Polity(int id, String name, String cultureName, int leaderCharacterId, long foundedOnDay) {
        this.id = id;
        this.name = name;
        this.cultureName = cultureName;
        this.leaderCharacterId = leaderCharacterId;
        this.heldVillageIds = new ArrayList<>();
        this.treasury = 0L;
        this.taxRate = DEFAULT_TAX_RATE;
        this.tributeRate = DEFAULT_TRIBUTE_RATE;
        this.foundedOnDay = foundedOnDay;
    }

    public Polity(CompoundTag nbt) {
        this.id = nbt.getInt("id");
        this.name = nbt.getString("name");
        this.cultureName = nbt.getString("cultureName");
        this.leaderCharacterId = nbt.contains("leaderCharacterId") ? nbt.getInt("leaderCharacterId") : LEADER_VACANT;
        this.heldVillageIds = new ArrayList<>();
        if (nbt.contains("heldVillages")) {
            int[] arr = nbt.getIntArray("heldVillages");
            for (int v : arr) this.heldVillageIds.add(v);
        }
        this.treasury = nbt.getLong("treasury");
        this.taxRate = nbt.contains("taxRate") ? nbt.getFloat("taxRate") : DEFAULT_TAX_RATE;
        this.tributeRate = nbt.contains("tributeRate") ? nbt.getFloat("tributeRate") : DEFAULT_TRIBUTE_RATE;
        this.foundedOnDay = nbt.contains("foundedOnDay") ? nbt.getLong("foundedOnDay") : 0L;
    }

    public CompoundTag save() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("id", id);
        nbt.putString("name", name);
        nbt.putString("cultureName", cultureName);
        nbt.putInt("leaderCharacterId", leaderCharacterId);
        int[] arr = new int[heldVillageIds.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = heldVillageIds.get(i);
        nbt.put("heldVillages", new IntArrayTag(arr));
        nbt.putLong("treasury", treasury);
        nbt.putFloat("taxRate", taxRate);
        nbt.putFloat("tributeRate", tributeRate);
        nbt.putLong("foundedOnDay", foundedOnDay);
        return nbt;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCultureName() { return cultureName; }
    public int getLeaderCharacterId() { return leaderCharacterId; }
    public void setLeaderCharacterId(int leaderCharacterId) { this.leaderCharacterId = leaderCharacterId; }
    public List<Integer> getHeldVillageIds() { return Collections.unmodifiableList(heldVillageIds); }
    public long getTreasury() { return treasury; }
    public void setTreasury(long treasury) { this.treasury = treasury; }
    public void addTreasury(long delta) { this.treasury += delta; }
    public float getTaxRate() { return taxRate; }
    public void setTaxRate(float taxRate) { this.taxRate = Math.max(0f, Math.min(1f, taxRate)); }
    public float getTributeRate() { return tributeRate; }
    public void setTributeRate(float tributeRate) { this.tributeRate = Math.max(0f, Math.min(1f, tributeRate)); }
    public long getFoundedOnDay() { return foundedOnDay; }

    /** Adds a held village, deduping silently. */
    public void addVillage(int villageId) {
        if (!heldVillageIds.contains(villageId)) {
            heldVillageIds.add(villageId);
        }
    }

    public boolean removeVillage(int villageId) {
        return heldVillageIds.remove((Integer) villageId);
    }

    public boolean holdsVillage(int villageId) {
        return heldVillageIds.contains(villageId);
    }
}
