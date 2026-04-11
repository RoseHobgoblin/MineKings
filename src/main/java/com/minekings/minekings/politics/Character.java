package com.minekings.minekings.politics;

import net.minecraft.nbt.CompoundTag;

/**
 * A political actor, stored as pure data. Characters are not tied to any
 * villager entity — the entity (when loaded) would be a <em>projection</em>
 * of the character record, not the other way around. This lets characters
 * persist and be simulated independently of whether their chunk is loaded.
 *
 * <p>v0.2 carries only the minimum fields needed to render a leader in
 * commands: id, name, culture, birth/death days, and current polity. Age
 * is derived from {@code birthDay} and the current game day. Personality,
 * traits, opinions, gender, parentage — all deferred to v0.3+.
 *
 * <p>Note: this class intentionally shadows {@code java.lang.Character}
 * within the {@code com.minekings.minekings.politics} package. Files that
 * need both should fully-qualify {@code java.lang.Character}.
 */
public final class Character {
    public static final long DEATH_DAY_ALIVE = -1L;
    public static final int POLITY_NONE = -1;

    private final int id;
    private String name;
    private String cultureName;
    private long birthDay;
    private long deathDay;
    private int currentPolityId;

    public Character(int id, String name, String cultureName, long birthDay, int currentPolityId) {
        this.id = id;
        this.name = name;
        this.cultureName = cultureName;
        this.birthDay = birthDay;
        this.deathDay = DEATH_DAY_ALIVE;
        this.currentPolityId = currentPolityId;
    }

    public Character(CompoundTag nbt) {
        this.id = nbt.getInt("id");
        this.name = nbt.getString("name");
        this.cultureName = nbt.getString("cultureName");
        this.birthDay = nbt.getLong("birthDay");
        this.deathDay = nbt.contains("deathDay") ? nbt.getLong("deathDay") : DEATH_DAY_ALIVE;
        this.currentPolityId = nbt.contains("currentPolityId") ? nbt.getInt("currentPolityId") : POLITY_NONE;
    }

    public CompoundTag save() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("id", id);
        nbt.putString("name", name);
        nbt.putString("cultureName", cultureName);
        nbt.putLong("birthDay", birthDay);
        nbt.putLong("deathDay", deathDay);
        nbt.putInt("currentPolityId", currentPolityId);
        return nbt;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCultureName() { return cultureName; }
    public long getBirthDay() { return birthDay; }
    public long getDeathDay() { return deathDay; }
    public void setDeathDay(long deathDay) { this.deathDay = deathDay; }
    public boolean isAlive() { return deathDay == DEATH_DAY_ALIVE; }
    public int getCurrentPolityId() { return currentPolityId; }
    public void setCurrentPolityId(int currentPolityId) { this.currentPolityId = currentPolityId; }

    /** Derived age in game days. */
    public long getAge(long currentDay) {
        long endDay = isAlive() ? currentDay : deathDay;
        return Math.max(0L, endDay - birthDay);
    }
}
