/*
 * Derived from AntiqueAtlas by the AntiqueAtlasTeam
 * (https://github.com/AntiqueAtlasTeam/AntiqueAtlas), GPL v3.
 * Port keeps AA's getTextureU()/getTextureV() math verbatim since that's
 * the load-bearing piece of the autotile lookup; only the outer class
 * shape was reworked to use 1.21.1 types.
 */
package com.minekings.minekings.client.hub.map;

import net.minecraft.resources.ResourceLocation;

/**
 * A quarter of a chunk's tile, carrying the texture set key, its
 * {@link SubTileShape stitch shape}, which {@link Part corner} of the chunk
 * it belongs to, and a deterministic variation number for texture picking.
 */
public class SubTile {
    public ResourceLocation tile;
    public int x, y;              // coords on the subtile grid
    public int variationNumber;
    public SubTileShape shape;
    public final Part part;

    public SubTile(Part part) {
        this.part = part;
    }

    public static int generateVariationNumber(int chunkX, int chunkY) {
        // Cheap hash that changes when cx or cy changes, stable otherwise.
        int h = chunkX * 31 + chunkY;
        h = h ^ (chunkX * chunkY);
        return h & 0x7FFFFFFF;
    }

    public void setChunkCoords(int chunkX, int chunkY) {
        this.variationNumber = generateVariationNumber(chunkX, chunkY);
    }

    /** Texture U offset in subtile units (0..3). */
    public int getTextureU() {
        return switch (shape) {
            case SINGLE_OBJECT       -> part.u;
            case CONCAVE             -> 2 + part.u;
            case VERTICAL, CONVEX    -> part.u * 3;
            case HORIZONTAL, FULL    -> 2 - part.u;
        };
    }

    /** Texture V offset in subtile units (0..5). */
    public int getTextureV() {
        return switch (shape) {
            case SINGLE_OBJECT, CONCAVE -> part.v;
            case CONVEX, HORIZONTAL      -> 2 + part.v * 3;
            case FULL, VERTICAL          -> 4 - part.v;
        };
    }

    public enum Part {
        TOP_LEFT(0, 0), TOP_RIGHT(1, 0), BOTTOM_LEFT(0, 1), BOTTOM_RIGHT(1, 1);
        public final int u, v;
        Part(int u, int v) { this.u = u; this.v = v; }
    }
}
