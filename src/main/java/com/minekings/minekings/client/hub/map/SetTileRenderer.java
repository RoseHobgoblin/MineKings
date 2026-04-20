/*
 * Derived from AntiqueAtlas by the AntiqueAtlasTeam
 * (https://github.com/AntiqueAtlasTeam/AntiqueAtlas), GPL v3.
 *
 * Port notes: drops AA's Texture abstraction in favor of direct
 * {@link GuiGraphics#blit} calls, since we're rendering inside a screen
 * rather than in a world pass. Batching-by-texture preserved.
 */
package com.minekings.minekings.client.hub.map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Batches subtile draw commands by texture so a whole chunk of tiles gets
 * rendered with one texture bind. Source rect math follows AA's 4-col ×
 * 6-row autotile layout, with each subtile cell being 8×8 source pixels.
 */
public final class SetTileRenderer {
    /** AA autotile: 4 subtile columns × 6 subtile rows. */
    private static final int TEX_W_PX = 32;
    private static final int TEX_H_PX = 48;
    private static final int SUBTILE_PX = 8;

    private final GuiGraphics gfx;
    /** Destination pixel size for one subtile (half of PIXELS_PER_CHUNK). */
    private final int destSubtileSize;

    private final Map<ResourceLocation, List<Call>> byTexture = new HashMap<>();

    private record Call(int destX, int destY, int srcU, int srcV) {}

    public SetTileRenderer(GuiGraphics gfx, int pixelsPerChunk) {
        this.gfx = gfx;
        this.destSubtileSize = Math.max(1, pixelsPerChunk / 2);
    }

    /**
     * Queue a subtile draw. {@code destX/destY} is the top-left corner of
     * this subtile on screen. {@code srcU/srcV} are in subtile-unit counts
     * (what {@link SubTile#getTextureU}/{@link SubTile#getTextureV} return).
     */
    public void add(ResourceLocation texture, int destX, int destY, int srcU, int srcV) {
        byTexture.computeIfAbsent(texture, k -> new ArrayList<>())
                 .add(new Call(destX, destY, srcU * SUBTILE_PX, srcV * SUBTILE_PX));
    }

    /** Execute all queued draws, grouped per texture. */
    public void flush() {
        for (Map.Entry<ResourceLocation, List<Call>> e : byTexture.entrySet()) {
            ResourceLocation tex = e.getKey();
            for (Call c : e.getValue()) {
                gfx.blit(tex,
                        c.destX, c.destY,
                        destSubtileSize, destSubtileSize,
                        c.srcU, c.srcV,
                        SUBTILE_PX, SUBTILE_PX,
                        TEX_W_PX, TEX_H_PX);
            }
        }
        byTexture.clear();
    }
}
