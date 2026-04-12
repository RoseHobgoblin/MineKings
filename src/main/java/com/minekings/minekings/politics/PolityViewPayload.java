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
            buf.writeLong(v.food);
            buf.writeLong(v.materials);
            buf.writeLong(v.gold);
            buf.writeLong(v.incFood);
            buf.writeLong(v.incMaterials);
            buf.writeLong(v.incGold);
            buf.writeVarInt(v.plotCount);
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
            villages.add(new VillageRow(
                    buf.readUtf(),
                    buf.readLong(), buf.readLong(), buf.readLong(),
                    buf.readLong(), buf.readLong(), buf.readLong(),
                    buf.readVarInt()
            ));
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

    /** Per-village economic summary carried in the payload. */
    public record VillageRow(String name, long food, long materials, long gold,
                             long incFood, long incMaterials, long incGold, int plotCount) {}
    public record VassalRow(String displayName, String leaderTitleName) {}
}
