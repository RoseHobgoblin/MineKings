/*
 * Derived from Minecraft Comes Alive Reborn (https://github.com/Luke100000/minecraft-comes-alive)
 * Copyright (c) 2016-2024 The MCA Project contributors
 *
 * Licensed under the GNU General Public License v3.0.
 * See LICENSE in this project's root for full text.
 */
package com.minekings.minekings.village;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minekings.minekings.MineKings;
import com.minekings.minekings.village.util.MKRegistryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;

import java.util.*;

public final class BuildingType {
    public static final StreamCodec<FriendlyByteBuf, BuildingType> STREAM_CODEC = StreamCodec.of(
            (buf, buildingType) -> {
                buf.writeUtf(buildingType.name);
                buf.writeVarInt(buildingType.margin);
                buf.writeUtf(buildingType.color);
                buf.writeVarInt(buildingType.priority);
                buf.writeBoolean(buildingType.visible);
                buf.writeBoolean(buildingType.noBeds);
                buf.writeBoolean(buildingType.icon);
                buf.writeVarInt(buildingType.iconU);
                buf.writeVarInt(buildingType.iconV);
                buf.writeBoolean(buildingType.grouped);
                buf.writeVarInt(buildingType.mergeRange);
                buf.writeDouble(buildingType.dailyIncome);

                // Serialize produces/consumes maps
                writeStringDoubleMap(buf, buildingType.produces);
                writeStringDoubleMap(buf, buildingType.consumes);

                // Serialize blocks map
                buf.writeVarInt(buildingType.blocks.size());
                buildingType.blocks.forEach((key, value) -> {
                    buf.writeUtf(key);
                    buf.writeVarInt(value);
                });
            },
            buf -> {
                String name = buf.readUtf();
                int margin = buf.readVarInt();
                String color = buf.readUtf();
                int priority = buf.readVarInt();
                boolean visible = buf.readBoolean();
                boolean noBeds = buf.readBoolean();
                boolean icon = buf.readBoolean();
                int iconU = buf.readVarInt();
                int iconV = buf.readVarInt();
                boolean grouped = buf.readBoolean();
                int mergeRange = buf.readVarInt();
                double dailyIncome = buf.readDouble();

                Map<String, Double> produces = readStringDoubleMap(buf);
                Map<String, Double> consumes = readStringDoubleMap(buf);

                // Deserialize blocks map
                int blocksSize = buf.readVarInt();
                Map<String, Integer> blocks = new HashMap<>(blocksSize);
                for (int i = 0; i < blocksSize; i++) {
                    blocks.put(buf.readUtf(), buf.readVarInt());
                }

                return new BuildingType(name, margin, color, priority, visible, noBeds, icon, iconU, iconV, grouped, mergeRange, dailyIncome, produces, consumes, blocks);
            }
    );

    private static void writeStringDoubleMap(net.minecraft.network.FriendlyByteBuf buf, Map<String, Double> map) {
        buf.writeVarInt(map.size());
        map.forEach((k, v) -> { buf.writeUtf(k); buf.writeDouble(v); });
    }

    private static Map<String, Double> readStringDoubleMap(net.minecraft.network.FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, Double> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) map.put(buf.readUtf(), buf.readDouble());
        return map;
    }
    private final String name;
    private final int margin;
    private final String color;
    private final int priority;
    private final boolean visible;
    private final boolean noBeds;
    private final Map<String, Integer> blocks;
    private final boolean icon;
    private final int iconU;
    private final int iconV;
    private final boolean grouped;
    private final int mergeRange;
    private final double dailyIncome;
    /** Per-resource production per day. Keys are lowercase resource names ("food", "materials", "gold"). */
    private final Map<String, Double> produces;
    /** Per-resource consumption per day. Same key format as produces. */
    private final Map<String, Double> consumes;
    private transient Map<ResourceLocation, ResourceLocation> blockToGroup;
    private transient Map<ResourceLocation, Integer> groups;

    // Private constructor for network deserialization
    private BuildingType(String name, int margin, String color, int priority, boolean visible, boolean noBeds,
                         boolean icon, int iconU, int iconV, boolean grouped, int mergeRange, double dailyIncome,
                         Map<String, Double> produces, Map<String, Double> consumes, Map<String, Integer> blocks) {
        this.name = name;
        this.margin = margin;
        this.color = color;
        this.priority = priority;
        this.visible = visible;
        this.noBeds = noBeds;
        this.icon = icon;
        this.iconU = iconU;
        this.iconV = iconV;
        this.grouped = grouped;
        this.mergeRange = mergeRange;
        this.dailyIncome = dailyIncome;
        this.produces = produces;
        this.consumes = consumes;
        this.blocks = blocks;
    }

    public BuildingType() {
        this.name = "?";
        this.margin = 0;
        this.color = "ffffffff";
        this.priority = 0;
        this.visible = true;
        this.noBeds = false;
        this.blocks = Map.of("#minecraft:beds", 1000000000);
        this.blockToGroup = null;
        this.icon = false;
        this.iconU = 0;
        this.iconV = 0;
        this.grouped = false;
        this.mergeRange = 32;
        this.dailyIncome = 0.0;
        this.produces = Map.of();
        this.consumes = Map.of();
    }

    public BuildingType(String name, JsonObject value) {
        this.name = name;
        this.margin = GsonHelper.getAsInt(value, "margin", 0);
        this.color = GsonHelper.getAsString(value, "color", "ffffffff");
        this.priority = GsonHelper.getAsInt(value, "priority", 0);
        this.visible = GsonHelper.getAsBoolean(value, "visible", true);
        this.noBeds = GsonHelper.getAsBoolean(value, "noBeds", false);

        this.icon = GsonHelper.getAsBoolean(value, "icon", false);
        this.iconU = GsonHelper.getAsInt(value, "iconU", 0);
        this.iconV = GsonHelper.getAsInt(value, "iconV", 0);

        this.grouped = GsonHelper.getAsBoolean(value, "grouped", false);
        this.mergeRange = GsonHelper.getAsInt(value, "mergeRange", 0);
        this.dailyIncome = value.has("dailyIncome") ? value.get("dailyIncome").getAsDouble() : 0.0;

        this.produces = new HashMap<>();
        if (GsonHelper.isObjectNode(value, "produces")) {
            JsonObject prod = GsonHelper.getAsJsonObject(value, "produces");
            for (Map.Entry<String, JsonElement> entry : prod.entrySet()) {
                this.produces.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }
        this.consumes = new HashMap<>();
        if (GsonHelper.isObjectNode(value, "consumes")) {
            JsonObject cons = GsonHelper.getAsJsonObject(value, "consumes");
            for (Map.Entry<String, JsonElement> entry : cons.entrySet()) {
                this.consumes.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }

        this.blocks = new HashMap<>();
        if (GsonHelper.isObjectNode(value, "blocks")) {
            JsonObject blocks = GsonHelper.getAsJsonObject(value, "blocks");
            for (Map.Entry<String, JsonElement> entry : blocks.entrySet()) {
                this.blocks.put(
                        entry.getKey(),
                        entry.getValue().getAsInt()
                );
            }
        }

        this.groups = new HashMap<>();
        if (GsonHelper.isObjectNode(value, "groups")) {
            JsonObject blocks = GsonHelper.getAsJsonObject(value, "groups");
            for (Map.Entry<String, JsonElement> entry : blocks.entrySet()) {
                this.groups.put(
                        ResourceLocation.parse(entry.getKey()),
                        entry.getValue().getAsInt()
                );
            }
        }
    }

    public String name() {
        return name;
    }

    public String color() {
        return color;
    }

    public int priority() {
        return priority;
    }

    public boolean visible() {
        return visible;
    }

    public int getColor() {
        return (int) Long.parseLong(color, 16);
    }

    /**
     * @return a mapping between block identifiers and groups (tags or individual blocks)
     */
    public Map<ResourceLocation, ResourceLocation> getBlockToGroup() {
        if (blockToGroup == null) {
            blockToGroup = new HashMap<>();
            groups = new HashMap<>();
            for (Map.Entry<String, Integer> requirement : blocks.entrySet()) {
                ResourceLocation identifier;
                if (requirement.getKey().startsWith("#")) {
                    identifier = ResourceLocation.parse(requirement.getKey().substring(1));
                    TagKey<Block> tag = TagKey.create(Registries.BLOCK, identifier);
                    if (MKRegistryHelper.isTagEmpty(tag)) {
                        MineKings.LOGGER.error("Unknown building type tag {}", identifier);
                    } else {
                        var entries = MKRegistryHelper.getEntries(tag);
                        entries.ifPresent(registryEntries -> {
                            for (Block b : registryEntries.stream().map(Holder::value).toList()) {
                                blockToGroup.putIfAbsent(BuiltInRegistries.BLOCK.getKey(b), identifier);
                            }
                        });
                    }
                } else {
                    identifier = ResourceLocation.parse(requirement.getKey());
                    blockToGroup.put(identifier, identifier);
                }
                groups.put(identifier, requirement.getValue());
            }
        }
        return blockToGroup;
    }

    public Map<ResourceLocation, Integer> getGroups() {
        getBlockToGroup();
        return groups;
    }

    /**
     * @param blocks the map of block positions per block type of building
     * @return a filtered and grouped map of block types relevant for this building type
     */
    public Map<ResourceLocation, List<BlockPos>> getGroups(Map<ResourceLocation, List<BlockPos>> blocks) {
        HashMap<ResourceLocation, List<BlockPos>> available = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<BlockPos>> entry : blocks.entrySet()) {
            Optional.ofNullable(getBlockToGroup().get(entry.getKey())).ifPresent(v ->
                    available.computeIfAbsent(v, k -> new LinkedList<>()).addAll(entry.getValue())
            );
        }
        return available;
    }

    public boolean isIcon() {
        return icon;
    }

    public int iconU() {
        return iconU * 20;
    }

    public int iconV() {
        return iconV * 60;
    }

    public boolean grouped() {
        return grouped;
    }

    public int mergeRange() {
        return mergeRange;
    }

    public boolean noBeds() {
        return noBeds;
    }

    public int getMargin() {
        return margin;
    }

    public int getMinBlocks() {
        return blocks.values().stream().mapToInt(v -> v).sum();
    }

    public double dailyIncome() {
        return dailyIncome;
    }

    /** Per-resource production per day. Keys are lowercase resource names. */
    public Map<String, Double> produces() { return produces; }

    /** Per-resource consumption per day. Keys are lowercase resource names. */
    public Map<String, Double> consumes() { return consumes; }

    /** True if this building type has explicit per-resource produces/consumes data (v0.5+). */
    public boolean hasResourceEconomy() {
        return !produces.isEmpty() || !consumes.isEmpty();
    }
}
