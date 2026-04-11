package com.minekings.minekings.politics;

import net.minecraft.nbt.CompoundTag;

/**
 * An immutable vassal→lord pledge. Not a parent pointer on Polity — it's a
 * first-class object so that future versions can attach tribute, levy,
 * opinion modifiers, and a sworn-on-day record to the link itself.
 *
 * <p>Identity is the {@code (vassalPolityId, lordPolityId)} pair. A vassal
 * can have at most one lord at a time (enforced by {@link PoliticsManager}).
 */
public record Allegiance(int vassalPolityId, int lordPolityId, long swornOnDay) {

    public CompoundTag save() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("vassal", vassalPolityId);
        nbt.putInt("lord", lordPolityId);
        nbt.putLong("day", swornOnDay);
        return nbt;
    }

    public static Allegiance load(CompoundTag nbt) {
        return new Allegiance(nbt.getInt("vassal"), nbt.getInt("lord"), nbt.getLong("day"));
    }
}
