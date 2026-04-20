/*
 * Derived from AntiqueAtlas by the AntiqueAtlasTeam
 * (https://github.com/AntiqueAtlasTeam/AntiqueAtlas), GPL v3.
 *
 * Port notes: the 3x4 neighbor peek, corner-stitch rules, and the
 * a/b/c/d/e/f/g/h/i/j/k/l layout are unchanged from AA's original. The
 * tile lookup is indirected through a {@link TileSource} functional
 * interface so the iterator doesn't depend on AA's ITileStorage.
 */
package com.minekings.minekings.client.hub.map;

import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.function.Function;

/**
 * Iterates through a chunk rectangle emitting one {@link SubTileQuartet}
 * per chunk, with each subtile's {@link SubTileShape} determined by its
 * 3×4 neighborhood of chunks (same algorithm as AntiqueAtlas).
 *
 * <p>Usage:
 * <pre>
 * TileRenderIterator it = new TileRenderIterator((cx, cz) -&gt; cache.getBiomeKey(cx, cz));
 * it.setScope(minX, minY, maxX, maxY);
 * for (SubTileQuartet q : it) { renderer.submit(q); }
 * </pre>
 */
public class TileRenderIterator implements Iterator<SubTileQuartet>, Iterable<SubTileQuartet> {

    /**
     * Returns the tile id (biome resource location) at the given chunk, or
     * {@code null} if unknown / unloaded.
     */
    @FunctionalInterface
    public interface TileSource {
        ResourceLocation getTile(int chunkX, int chunkZ);
    }

    private final TileSource tiles;
    /**
     * Tile → texture set lookup. Exposed so the iterator's stitching rules
     * can ask whether two tiles belong to the same set.
     */
    private final Function<ResourceLocation, TileTextureSet> setLookup;

    private int step = 1;
    public void setStep(int step) { if (step >= 1) this.step = step; }

    private int scopeMinX, scopeMinY, scopeMaxX, scopeMaxY;
    public void setScope(int minX, int minY, int maxX, int maxY) {
        this.scopeMinX = minX; this.scopeMinY = minY;
        this.scopeMaxX = maxX; this.scopeMaxY = maxY;
        this.chunkX = minX;
        this.chunkY = minY;
        this.subtileX = -1;
        this.subtileY = -1;
        a = b = c = d = e = f = g = h = i = j = k = l = null;
    }

    // 3x4 neighborhood state (AA layout):
    //   a | b
    // c d | e f
    // ---------
    // g h | i j
    //   k | l
    // 'i' is the current chunk; the emitted quartet represents the
    // d-e-h-i corner arrangement.
    private ResourceLocation a, b, c, d, e, f, g, h, i, j, k, l;

    private final SubTile _d = new SubTile(SubTile.Part.BOTTOM_RIGHT);
    private final SubTile _e = new SubTile(SubTile.Part.BOTTOM_LEFT);
    private final SubTile _h = new SubTile(SubTile.Part.TOP_RIGHT);
    private final SubTile _i = new SubTile(SubTile.Part.TOP_LEFT);
    private final SubTileQuartet quartet = new SubTileQuartet(_d, _e, _h, _i);

    private int chunkX, chunkY;
    private int subtileX = -1, subtileY = -1;

    public TileRenderIterator(TileSource tiles, Function<ResourceLocation, TileTextureSet> setLookup) {
        this.tiles = tiles;
        this.setLookup = setLookup;
    }

    @Override public Iterator<SubTileQuartet> iterator() { return this; }

    @Override
    public boolean hasNext() {
        return chunkX >= scopeMinX && chunkX <= scopeMaxX + 1 &&
               chunkY >= scopeMinY && chunkY <= scopeMaxY + 1;
    }

    @Override
    public SubTileQuartet next() {
        a = b;
        b = tiles.getTile(chunkX, chunkY - step * 2);
        c = d;
        d = e;
        e = f;
        f = tiles.getTile(chunkX + step, chunkY - step);
        g = h;
        h = i;
        i = j;
        j = tiles.getTile(chunkX + step, chunkY);
        k = l;
        l = tiles.getTile(chunkX, chunkY + step);

        quartet.setChunkCoords(chunkX, chunkY, step);
        quartet.setCoords(subtileX, subtileY);
        _d.tile = d;
        _e.tile = e;
        _h.tile = h;
        _i.tile = i;

        // Default: all convex (isolated corner).
        for (SubTile s : quartet) s.shape = SubTileShape.CONVEX;

        // Horizontal stitches.
        if (shouldStitchToHorizontally(d, e)) stitchHorizontally(_d);
        if (shouldStitchToHorizontally(e, d)) stitchHorizontally(_e);
        if (shouldStitchToHorizontally(h, i)) stitchHorizontally(_h);
        if (shouldStitchToHorizontally(i, h)) stitchHorizontally(_i);

        // Vertical stitches + full-corner promotion.
        if (shouldStitchToVertically(d, h)) {
            stitchVertically(_d);
            if (_d.shape == SubTileShape.CONCAVE && shouldStitchTo(d, i)) _d.shape = SubTileShape.FULL;
        }
        if (shouldStitchToVertically(h, d)) {
            stitchVertically(_h);
            if (_h.shape == SubTileShape.CONCAVE && shouldStitchTo(h, e)) _h.shape = SubTileShape.FULL;
        }
        if (shouldStitchToVertically(e, i)) {
            stitchVertically(_e);
            if (_e.shape == SubTileShape.CONCAVE && shouldStitchTo(e, h)) _e.shape = SubTileShape.FULL;
        }
        if (shouldStitchToVertically(i, e)) {
            stitchVertically(_i);
            if (_i.shape == SubTileShape.CONCAVE && shouldStitchTo(i, d)) _i.shape = SubTileShape.FULL;
        }

        // Convex with no neighbors → SINGLE_OBJECT.
        if (_d.shape == SubTileShape.CONVEX && !shouldStitchToVertically(d, a) && !shouldStitchToHorizontally(d, c)) _d.shape = SubTileShape.SINGLE_OBJECT;
        if (_e.shape == SubTileShape.CONVEX && !shouldStitchToVertically(e, b) && !shouldStitchToHorizontally(e, f)) _e.shape = SubTileShape.SINGLE_OBJECT;
        if (_h.shape == SubTileShape.CONVEX && !shouldStitchToHorizontally(h, g) && !shouldStitchToVertically(h, k)) _h.shape = SubTileShape.SINGLE_OBJECT;
        if (_i.shape == SubTileShape.CONVEX && !shouldStitchToHorizontally(i, j) && !shouldStitchToVertically(i, l)) _i.shape = SubTileShape.SINGLE_OBJECT;

        // Advance.
        chunkX += step;
        subtileX += 2;
        if (chunkX > scopeMaxX + 1) {
            chunkX = scopeMinX;
            subtileX = -1;
            chunkY += step;
            subtileY += 2;
            a = b = c = d = e = null;
            f = tiles.getTile(chunkX, chunkY - step);
            g = h = i = null;
            j = tiles.getTile(chunkX, chunkY);
            k = l = null;
        }
        return quartet;
    }

    // ---- Stitch predicates ---------------------------------------------------

    /** Default rule: two tiles stitch iff they resolve to the same texture set. */
    private boolean sameSet(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        TileTextureSet sa = setLookup.apply(a);
        TileTextureSet sb = setLookup.apply(b);
        return sa != null && sb != null && sa.id().equals(sb.id());
    }

    private boolean shouldStitchTo(ResourceLocation tile, ResourceLocation to) { return sameSet(tile, to); }
    private boolean shouldStitchToHorizontally(ResourceLocation tile, ResourceLocation to) { return sameSet(tile, to); }
    private boolean shouldStitchToVertically(ResourceLocation tile, ResourceLocation to) { return sameSet(tile, to); }

    private static void stitchVertically(SubTile s) {
        if (s.shape == SubTileShape.HORIZONTAL) s.shape = SubTileShape.CONCAVE;
        else if (s.shape == SubTileShape.CONVEX) s.shape = SubTileShape.VERTICAL;
    }

    private static void stitchHorizontally(SubTile s) {
        if (s.shape == SubTileShape.VERTICAL) s.shape = SubTileShape.CONCAVE;
        else if (s.shape == SubTileShape.CONVEX) s.shape = SubTileShape.HORIZONTAL;
    }
}
