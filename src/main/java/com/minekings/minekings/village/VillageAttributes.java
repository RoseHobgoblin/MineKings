package com.minekings.minekings.village;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Derives abstract attribute flags (has_smithy, has_food_industry, etc.)
 * from a village's detected building types. The attribute set is what the
 * economy layer consumes — NOT the building list itself. This keeps the
 * economy decoupled from spatial detection: flood-fill gives us building
 * types, we flatten that into flags, economy reads flags.
 *
 * <p>v0.5 hardcodes the mapping; future versions may datapack it.
 */
public final class VillageAttributes {
    private VillageAttributes() {}

    /** Mapping from building-type id (matches building_types JSON filenames) to attribute flag(s). */
    private static final Map<String, String[]> BUILDING_TO_ATTRS = Map.ofEntries(
            // Food industry
            Map.entry("bakery",         new String[]{"has_food_industry"}),
            Map.entry("butcher",        new String[]{"has_food_industry"}),
            Map.entry("fishermans_hut", new String[]{"has_food_industry", "coastal"}),

            // Smithing / weapons / armor
            Map.entry("blacksmith",     new String[]{"has_smithy"}),
            Map.entry("toolsmith",      new String[]{"has_smithy"}),
            Map.entry("weaponsmith",    new String[]{"has_smithy", "has_arms"}),
            Map.entry("armorer",        new String[]{"has_smithy", "has_arms"}),
            Map.entry("armory",         new String[]{"has_arms"}),

            // General craftsmen
            Map.entry("mason",          new String[]{"has_craftsmen"}),
            Map.entry("fletcher",       new String[]{"has_craftsmen"}),
            Map.entry("leatherworker",  new String[]{"has_craftsmen"}),
            Map.entry("weaving_mill",   new String[]{"has_craftsmen"}),

            // Civic / commercial
            Map.entry("storage",        new String[]{"has_granary"}),
            Map.entry("inn",            new String[]{"has_inn"}),
            Map.entry("library",        new String[]{"has_library"}),
            Map.entry("infirmary",      new String[]{"has_infirmary"}),
            Map.entry("bookkeeper",     new String[]{"has_bookkeeper"}),
            Map.entry("cartographer",   new String[]{"has_bookkeeper"}),
            Map.entry("prison",         new String[]{"has_prison"}),
            Map.entry("town_center",    new String[]{"is_central"}),
            Map.entry("music_store",    new String[]{"has_music"}),

            // Housing
            Map.entry("house",          new String[]{"has_housing"}),
            Map.entry("big_house",      new String[]{"has_housing"}),
            Map.entry("building",       new String[]{"has_housing"})
    );

    /** Returns the attribute flags that this village's buildings yield. */
    public static Set<String> derive(Village village) {
        Set<String> attrs = new HashSet<>();
        for (Building b : village) {
            String type = b.getType();
            String[] flags = BUILDING_TO_ATTRS.get(type);
            if (flags != null) {
                for (String f : flags) attrs.add(f);
            }
        }
        return attrs;
    }

    /** Derives and applies attributes to the village. Does not mark dirty. */
    public static void refresh(Village village) {
        Set<String> next = derive(village);
        village.setAttributes(next);
    }
}
