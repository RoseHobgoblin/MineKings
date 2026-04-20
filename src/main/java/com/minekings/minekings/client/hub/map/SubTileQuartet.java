/*
 * Derived from AntiqueAtlas by the AntiqueAtlasTeam
 * (https://github.com/AntiqueAtlasTeam/AntiqueAtlas), GPL v3.
 */
package com.minekings.minekings.client.hub.map;

import java.util.Iterator;

/**
 * Four subtiles — TL/TR/BL/BR — that together cover a single chunk.
 * Returned from {@link TileRenderIterator} one chunk at a time.
 */
public final class SubTileQuartet implements Iterable<SubTile> {
    public final SubTile d, e, h, i; // AA names preserved: d=TL corner, e=TR, h=BL, i=BR (of the 4x4 neighbor square)

    public SubTileQuartet(SubTile d, SubTile e, SubTile h, SubTile i) {
        this.d = d;
        this.e = e;
        this.h = h;
        this.i = i;
    }

    public void setChunkCoords(int chunkX, int chunkY, int step) {
        d.setChunkCoords(chunkX,        chunkY);
        e.setChunkCoords(chunkX + step, chunkY);
        h.setChunkCoords(chunkX,        chunkY + step);
        i.setChunkCoords(chunkX + step, chunkY + step);
    }

    public void setCoords(int subX, int subY) {
        d.x = subX;      d.y = subY;
        e.x = subX + 1;  e.y = subY;
        h.x = subX;      h.y = subY + 1;
        i.x = subX + 1;  i.y = subY + 1;
    }

    @Override
    public Iterator<SubTile> iterator() {
        return new Iterator<>() {
            int idx = 0;
            @Override public boolean hasNext() { return idx < 4; }
            @Override public SubTile next() {
                return switch (idx++) {
                    case 0 -> d;
                    case 1 -> e;
                    case 2 -> h;
                    default -> i;
                };
            }
        };
    }
}
