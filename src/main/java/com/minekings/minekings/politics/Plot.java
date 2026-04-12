package com.minekings.minekings.politics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/**
 * A registered parcel of land within a village. The plot is the ledger
 * entry; the physical structure in the world is its projection. The
 * economy reads from plots, not from block scanning.
 *
 * <p>For MineKings-generated villages (v0.5b+), plots are allocated at
 * generation time and new plots are added when the village AI decides to
 * grow. For legacy detected villages (v0.1–v0.4), plots are auto-migrated
 * from the MCA-style {@code Building} records on first contact.
 *
 * <p>Plots support a lifecycle: {@code EMPTY → UNDER_CONSTRUCTION → BUILT}
 * for new builds, {@code BUILT → DEMOLISHING → EMPTY} for teardowns, and
 * {@code BUILT → UNDER_CONSTRUCTION → BUILT} for upgrades (the tier
 * increments after construction completes). In v0.5a, all migrated plots
 * start as {@link PlotState#BUILT} — lifecycle transitions are wired in
 * v0.6.
 */
public final class Plot {
    private final int id;
    private BlockPos origin;
    private Direction facing;
    private String buildingTypeId;  // "" = no building assigned
    private int tier;
    private PlotState state;
    private long stateChangedOnDay;
    private int constructionDaysNeeded;

    /** Creates a fresh empty plot. */
    public Plot(int id, BlockPos origin, Direction facing) {
        this.id = id;
        this.origin = origin;
        this.facing = facing;
        this.buildingTypeId = "";
        this.tier = 0;
        this.state = PlotState.EMPTY;
        this.stateChangedOnDay = -1;
        this.constructionDaysNeeded = 0;
    }

    /** Creates a pre-built plot (used for migration from detected buildings). */
    public Plot(int id, BlockPos origin, Direction facing, String buildingTypeId, int tier) {
        this.id = id;
        this.origin = origin;
        this.facing = facing;
        this.buildingTypeId = buildingTypeId;
        this.tier = tier;
        this.state = PlotState.BUILT;
        this.stateChangedOnDay = -1;
        this.constructionDaysNeeded = 0;
    }

    public Plot(CompoundTag nbt) {
        this.id = nbt.getInt("id");
        this.origin = new BlockPos(nbt.getInt("ox"), nbt.getInt("oy"), nbt.getInt("oz"));
        this.facing = Direction.byName(nbt.getString("facing"));
        if (this.facing == null) this.facing = Direction.NORTH;
        this.buildingTypeId = nbt.getString("buildingTypeId");
        this.tier = nbt.getInt("tier");
        this.state = PlotState.fromString(nbt.getString("state"));
        this.stateChangedOnDay = nbt.contains("stateChangedOnDay") ? nbt.getLong("stateChangedOnDay") : -1;
        this.constructionDaysNeeded = nbt.getInt("constructionDaysNeeded");
    }

    public CompoundTag save() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("id", id);
        nbt.putInt("ox", origin.getX());
        nbt.putInt("oy", origin.getY());
        nbt.putInt("oz", origin.getZ());
        nbt.putString("facing", facing.getSerializedName());
        nbt.putString("buildingTypeId", buildingTypeId);
        nbt.putInt("tier", tier);
        nbt.putString("state", state.name());
        nbt.putLong("stateChangedOnDay", stateChangedOnDay);
        nbt.putInt("constructionDaysNeeded", constructionDaysNeeded);
        return nbt;
    }

    // ----- Getters / setters -----

    public int getId() { return id; }
    public BlockPos getOrigin() { return origin; }
    public void setOrigin(BlockPos origin) { this.origin = origin; }
    public Direction getFacing() { return facing; }
    public void setFacing(Direction facing) { this.facing = facing; }
    public String getBuildingTypeId() { return buildingTypeId; }
    public void setBuildingTypeId(String buildingTypeId) { this.buildingTypeId = buildingTypeId; }
    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }
    public PlotState getState() { return state; }
    public void setState(PlotState state) { this.state = state; }
    public long getStateChangedOnDay() { return stateChangedOnDay; }
    public void setStateChangedOnDay(long day) { this.stateChangedOnDay = day; }
    public int getConstructionDaysNeeded() { return constructionDaysNeeded; }
    public void setConstructionDaysNeeded(int days) { this.constructionDaysNeeded = days; }

    /** True if this plot has a completed building that contributes to the economy. */
    public boolean isProductive() {
        return state == PlotState.BUILT && !buildingTypeId.isEmpty();
    }

    /** True if this plot is available for new construction. */
    public boolean isEmpty() {
        return state == PlotState.EMPTY;
    }
}
