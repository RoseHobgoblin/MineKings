/*
 * Derived from AntiqueAtlas by the AntiqueAtlasTeam
 * (https://github.com/AntiqueAtlasTeam/AntiqueAtlas), GPL v3.
 *
 * Port keeps AA's "weighted list of tile textures" shape but lives in the
 * minekings namespace so our data/minekings/atlas/texture_sets/*.json files
 * resolve correctly.
 */
package com.minekings.minekings.client.hub.map;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * An immutable list of PNG texture variants that a chunk of a given biome
 * can render as. Each variant has an integer weight; higher-weight
 * variants appear more often when a chunk deterministically picks one.
 */
public record TileTextureSet(ResourceLocation id, List<Variant> variants, int totalWeight) {

    public record Variant(ResourceLocation texture, int weight) {}

    public static TileTextureSet of(ResourceLocation id, List<Variant> variants) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(variants);
        int total = 0;
        for (Variant v : variants) total += Math.max(1, v.weight);
        return new TileTextureSet(id, List.copyOf(variants), Math.max(1, total));
    }

    /**
     * Deterministic variant pick from a chunk-coord-derived seed. The
     * same chunk always picks the same texture across re-opens.
     */
    public ResourceLocation pickForSeed(int seed) {
        if (variants.isEmpty()) return id;
        int r = Math.floorMod(seed, totalWeight);
        for (Variant v : variants) {
            r -= Math.max(1, v.weight);
            if (r < 0) return v.texture;
        }
        return variants.get(variants.size() - 1).texture;
    }
}
