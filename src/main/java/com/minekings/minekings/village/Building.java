package com.minekings.minekings.village;

import com.minekings.minekings.village.util.MKNbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Data-only building record. v0.6 removed flood-fill detection and
 * tag-based type matching; this class is now a plain container that
 * persists any building explicitly placed inside a village (by worldgen
 * schematics or future Building Markers). Economy no longer reads these.
 */
public class Building {
    private final Map<ResourceLocation, List<BlockPos>> blocks = new HashMap<>();

    private String type = "building";
    private int pos0X, pos0Y, pos0Z;
    private int pos1X, pos1Y, pos1Z;
    private int posX, posY, posZ;
    private int id;

    public Building() {}

    public Building(BlockPos pos) {
        pos0X = pos.getX(); pos0Y = pos.getY(); pos0Z = pos.getZ();
        pos1X = pos0X;      pos1Y = pos0Y;      pos1Z = pos0Z;
        posX  = pos0X;      posY  = pos0Y;      posZ  = pos0Z;
    }

    /** Axis-aligned building spanning {@code pos0 → pos1} inclusive. */
    public Building(BlockPos pos0, BlockPos pos1) {
        this.pos0X = Math.min(pos0.getX(), pos1.getX());
        this.pos0Y = Math.min(pos0.getY(), pos1.getY());
        this.pos0Z = Math.min(pos0.getZ(), pos1.getZ());
        this.pos1X = Math.max(pos0.getX(), pos1.getX());
        this.pos1Y = Math.max(pos0.getY(), pos1.getY());
        this.pos1Z = Math.max(pos0.getZ(), pos1.getZ());
        this.posX  = (pos0X + pos1X) / 2;
        this.posY  = (pos0Y + pos1Y) / 2;
        this.posZ  = (pos0Z + pos1Z) / 2;
    }

    public Building(CompoundTag v) {
        id = v.getInt("id");
        pos0X = v.getInt("pos0X"); pos0Y = v.getInt("pos0Y"); pos0Z = v.getInt("pos0Z");
        pos1X = v.getInt("pos1X"); pos1Y = v.getInt("pos1Y"); pos1Z = v.getInt("pos1Z");
        if (v.contains("posX")) {
            posX = v.getInt("posX"); posY = v.getInt("posY"); posZ = v.getInt("posZ");
        } else {
            BlockPos c = getCenter();
            posX = c.getX(); posY = c.getY(); posZ = c.getZ();
        }
        type = v.contains("type") ? v.getString("type") : "building";

        if (v.contains("blocks2")) {
            blocks.putAll(MKNbtHelper.toMap(v.getCompound("blocks2"),
                    ResourceLocation::parse,
                    l -> MKNbtHelper.toList(l, e -> {
                        CompoundTag c = (CompoundTag) e;
                        return new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z"));
                    })));
        }
    }

    public CompoundTag save() {
        CompoundTag v = new CompoundTag();
        v.putInt("id", id);
        v.putInt("pos0X", pos0X); v.putInt("pos0Y", pos0Y); v.putInt("pos0Z", pos0Z);
        v.putInt("pos1X", pos1X); v.putInt("pos1Y", pos1Y); v.putInt("pos1Z", pos1Z);
        v.putInt("posX",  posX);  v.putInt("posY",  posY);  v.putInt("posZ",  posZ);
        v.putString("type", type);

        CompoundTag b = new CompoundTag();
        MKNbtHelper.fromMap(b, blocks,
                ResourceLocation::toString,
                e -> MKNbtHelper.fromList(e, p -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putInt("x", p.getX());
                    entry.putInt("y", p.getY());
                    entry.putInt("z", p.getZ());
                    return entry;
                })
        );
        v.put("blocks2", b);
        return v;
    }

    public BlockPos getPos0() { return new BlockPos(pos0X, pos0Y, pos0Z); }
    public BlockPos getPos1() { return new BlockPos(pos1X, pos1Y, pos1Z); }

    public int getChunkX() { return pos0X >> 4; }
    public int getChunkZ() { return pos0Z >> 4; }

    public BlockPos getCenter() {
        return new BlockPos((pos0X + pos1X) / 2, (pos0Y + pos1Y) / 2, (pos0Z + pos1Z) / 2);
    }

    public BlockPos getSourceBlock() { return new BlockPos(posX, posY, posZ); }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BuildingType getBuildingType() {
        return BuildingTypes.getInstance().getBuildingType(type);
    }

    public Map<ResourceLocation, List<BlockPos>> getBlocks() { return blocks; }

    public Stream<BlockPos> getBlockPosStream() {
        return blocks.values().stream().flatMap(Collection::stream);
    }

    public int getBlockCount() {
        return blocks.values().stream().mapToInt(List::size).sum();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public boolean containsPos(Vec3i pos) {
        return pos.getX() >= pos0X && pos.getX() <= pos1X
               && pos.getY() >= pos0Y && pos.getY() <= pos1Y
               && pos.getZ() >= pos0Z && pos.getZ() <= pos1Z;
    }

    /** True if this building has enough blocks to render on a future map icon. */
    public boolean isComplete() {
        return getBlockCount() > 0;
    }
}
