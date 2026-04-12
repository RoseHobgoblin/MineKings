package com.minekings.minekings.politics;

/**
 * The three economic resource channels. Every building produces and/or
 * consumes specific resources, and village stockpiles track each one
 * independently.
 *
 * <ul>
 *   <li><b>FOOD</b> — sustains population. Produced by farms, bakeries,
 *       fisheries. Consumed by every occupied building (workers need to eat).
 *       Deficit → population shrinks.</li>
 *   <li><b>MATERIALS</b> — raw inputs for workshops. Produced by mines,
 *       quarries, lumber yards. Consumed by blacksmiths, armorers, etc.
 *       Deficit → workshop output stops.</li>
 *   <li><b>GOLD</b> — universal currency. Produced by markets, trade,
 *       taxation of surplus goods. Stored in the polity treasury.
 *       Spent on construction, upgrades, military (future versions).</li>
 * </ul>
 */
public enum Resource {
    FOOD,
    MATERIALS,
    GOLD;

    public static Resource fromString(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
