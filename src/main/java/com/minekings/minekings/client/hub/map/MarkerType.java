package com.minekings.minekings.client.hub.map;

import com.minekings.minekings.MineKings;
import net.minecraft.resources.ResourceLocation;

/**
 * Classification of a village marker on the map. Server picks the type
 * per village; client picks an icon + LOD mip per type + current zoom.
 *
 * <p>{@code id} values are stable byte encodings for the wire format —
 * never renumber without bumping the payload version.
 */
public enum MarkerType {
    TOWN(0) {
        @Override
        public ResourceLocation iconFor(int pxPerChunk) {
            return pxPerChunk <= 4
                    ? loc("textures/gui/map/markers/village_mipped_16.png")
                    : loc("textures/gui/map/markers/village.png");
        }
    },
    SEAT_OF_VASSAL(1) {
        @Override
        public ResourceLocation iconFor(int pxPerChunk) {
            return pxPerChunk <= 4
                    ? loc("textures/gui/map/markers/village_mipped_16.png")
                    : loc("textures/gui/map/markers/village.png");
        }
    },
    CAPITAL(2) {
        @Override
        public ResourceLocation iconFor(int pxPerChunk) {
            return pxPerChunk <= 4
                    ? loc("textures/gui/map/markers/capital_mipped_16.png")
                    : loc("textures/gui/map/markers/capital.png");
        }
    };

    public final int id;
    MarkerType(int id) { this.id = id; }

    public abstract ResourceLocation iconFor(int pxPerChunk);

    public static MarkerType fromId(int id) {
        return switch (id) {
            case 1 -> SEAT_OF_VASSAL;
            case 2 -> CAPITAL;
            default -> TOWN;
        };
    }

    /** Base on-screen size in px; larger type → larger marker. */
    public int baseSize(int pxPerChunk) {
        int scaled = Math.max(12, Math.min(36, pxPerChunk + 8));
        return switch (this) {
            case CAPITAL        -> scaled + 8;
            case SEAT_OF_VASSAL -> scaled + 4;
            case TOWN           -> scaled;
        };
    }

    private static ResourceLocation loc(String p) {
        return ResourceLocation.fromNamespaceAndPath(MineKings.MODID, p);
    }
}
