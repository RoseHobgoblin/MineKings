package com.minekings.minekings.politics;

import com.minekings.minekings.MineKings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server→Client payload carrying all data the {@code PolityScreen} needs
 * to render a read-only view of a polity's political and economic state.
 * Sent once on right-click; no continuous sync.
 */
public record PolityViewPayload(
        int polityId,
        String displayName,
        String leaderTitleName,
        String cultureName,
        long treasury,
        float taxRate,
        float tributeRate,
        long foundedOnDay,
        String liegeDisplayName,   // empty string if independent
        List<VillageRow> villages,
        List<VassalRow> vassals
) implements CustomPacketPayload {

    public static final Type<PolityViewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineKings.MODID, "polity_view"));

    public static final StreamCodec<FriendlyByteBuf, PolityViewPayload> STREAM_CODEC =
            StreamCodec.of(PolityViewPayload::encode, PolityViewPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, PolityViewPayload p) {
        buf.writeVarInt(p.polityId);
        buf.writeUtf(p.displayName);
        buf.writeUtf(p.leaderTitleName);
        buf.writeUtf(p.cultureName);
        buf.writeLong(p.treasury);
        buf.writeFloat(p.taxRate);
        buf.writeFloat(p.tributeRate);
        buf.writeLong(p.foundedOnDay);
        buf.writeUtf(p.liegeDisplayName);

        buf.writeVarInt(p.villages.size());
        for (VillageRow v : p.villages) {
            buf.writeUtf(v.name);
            buf.writeVarInt(v.population);
            buf.writeLong(v.food);
            buf.writeLong(v.materials);
            buf.writeLong(v.gold);
            buf.writeLong(v.baselineFood);
            buf.writeLong(v.baselineMaterials);
            buf.writeLong(v.baselineGold);
            buf.writeVarInt(v.attributes.size());
            for (String a : v.attributes) buf.writeUtf(a);
        }

        buf.writeVarInt(p.vassals.size());
        for (VassalRow v : p.vassals) {
            buf.writeUtf(v.displayName);
            buf.writeUtf(v.leaderTitleName);
        }
    }

    private static PolityViewPayload decode(FriendlyByteBuf buf) {
        int polityId = buf.readVarInt();
        String displayName = buf.readUtf();
        String leaderTitleName = buf.readUtf();
        String cultureName = buf.readUtf();
        long treasury = buf.readLong();
        float taxRate = buf.readFloat();
        float tributeRate = buf.readFloat();
        long foundedOnDay = buf.readLong();
        String liegeDisplayName = buf.readUtf();

        int villageCount = buf.readVarInt();
        List<VillageRow> villages = new ArrayList<>(villageCount);
        for (int i = 0; i < villageCount; i++) {
            String vname = buf.readUtf();
            int pop = buf.readVarInt();
            long food = buf.readLong();
            long mats = buf.readLong();
            long gold = buf.readLong();
            long bFood = buf.readLong();
            long bMats = buf.readLong();
            long bGold = buf.readLong();
            int attrCount = buf.readVarInt();
            List<String> attrs = new ArrayList<>(attrCount);
            for (int k = 0; k < attrCount; k++) attrs.add(buf.readUtf());
            villages.add(new VillageRow(vname, pop, food, mats, gold, bFood, bMats, bGold, attrs));
        }

        int vassalCount = buf.readVarInt();
        List<VassalRow> vassals = new ArrayList<>(vassalCount);
        for (int i = 0; i < vassalCount; i++) {
            vassals.add(new VassalRow(buf.readUtf(), buf.readUtf()));
        }

        return new PolityViewPayload(
                polityId, displayName, leaderTitleName, cultureName,
                treasury, taxRate, tributeRate, foundedOnDay,
                liegeDisplayName, villages, vassals
        );
    }

    /** One row per village held by the polity. Carries economic snapshot + baseline. */
    public record VillageRow(
            String name,
            int population,
            long food,
            long materials,
            long gold,
            long baselineFood,
            long baselineMaterials,
            long baselineGold,
            List<String> attributes
    ) {}

    public record VassalRow(String displayName, String leaderTitleName) {}
}
