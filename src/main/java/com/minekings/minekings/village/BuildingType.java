package com.minekings.minekings.village;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.GsonHelper;

/**
 * Pure-metadata descriptor for a building category. v0.6 dropped all
 * detection-related fields (blocks/groups/grouped/mergeRange/priority
 * matching) — flood-fill is gone, so types are no longer derived from
 * block contents. The fields that remain feed UI rendering and naming.
 */
public final class BuildingType {
    public static final StreamCodec<FriendlyByteBuf, BuildingType> STREAM_CODEC = StreamCodec.of(
            (buf, bt) -> {
                buf.writeUtf(bt.name);
                buf.writeUtf(bt.color);
                buf.writeVarInt(bt.priority);
                buf.writeBoolean(bt.visible);
                buf.writeBoolean(bt.icon);
                buf.writeVarInt(bt.iconU);
                buf.writeVarInt(bt.iconV);
            },
            buf -> new BuildingType(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    private final String name;
    private final String color;
    private final int priority;
    private final boolean visible;
    private final boolean icon;
    private final int iconU;
    private final int iconV;

    private BuildingType(String name, String color, int priority, boolean visible,
                         boolean icon, int iconU, int iconV) {
        this.name = name;
        this.color = color;
        this.priority = priority;
        this.visible = visible;
        this.icon = icon;
        this.iconU = iconU;
        this.iconV = iconV;
    }

    public BuildingType() {
        this("?", "ffffffff", 0, true, false, 0, 0);
    }

    public BuildingType(String name, JsonObject value) {
        this(
                name,
                GsonHelper.getAsString(value, "color", "ffffffff"),
                GsonHelper.getAsInt(value, "priority", 0),
                GsonHelper.getAsBoolean(value, "visible", true),
                GsonHelper.getAsBoolean(value, "icon", false),
                GsonHelper.getAsInt(value, "iconU", 0),
                GsonHelper.getAsInt(value, "iconV", 0)
        );
    }

    public String name() { return name; }
    public String color() { return color; }
    public int priority() { return priority; }
    public boolean visible() { return visible; }
    public int getColor() { return (int) Long.parseLong(color, 16); }
    public boolean isIcon() { return icon; }
    public int iconU() { return iconU * 20; }
    public int iconV() { return iconV * 60; }
}
