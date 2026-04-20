/*
 * Derived from AntiqueAtlas by the AntiqueAtlasTeam
 * (https://github.com/AntiqueAtlasTeam/AntiqueAtlas), GPL v3.
 * This enum is unchanged from AA's inner enum
 * {@code SubTile.Shape} in {@code client/SubTile.java}, renamed to a
 * top-level type.
 */
package com.minekings.minekings.client.hub.map;

/**
 * The six stitch shapes a subtile can take.
 *
 * <p>Layout inside an autotile PNG (4 cols × 6 rows of 8×8 subtile cells):
 * <pre>
 *   col  0   1   2   3
 *   row 0: SINGLE_OBJECT top row
 *   row 1: SINGLE_OBJECT bottom + CONCAVE top
 *   row 2: CONCAVE bottom + CONVEX top + HORIZONTAL
 *   row 3: CONVEX bottom + VERTICAL / FULL
 *   row 4: (VERTICAL, FULL continued)
 *   row 5: (VERTICAL, FULL continued)
 * </pre>
 */
public enum SubTileShape {
    CONVEX, CONCAVE, HORIZONTAL, VERTICAL, FULL, SINGLE_OBJECT
}
