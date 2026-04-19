package com.minekings.minekings.economy;

/**
 * The three resource channels v0.5 supports. Stored as separate long fields
 * on Village rather than a Map for compactness and type safety. When we add
 * more resources (stone, furs, etc.) this probably becomes an enum registry
 * or datapack-defined type, but for now three is enough.
 */
public enum ResourceType {
    FOOD,
    MATERIALS,
    GOLD;

    public static ResourceType parse(String s) {
        if (s == null) return null;
        switch (s.toLowerCase()) {
            case "food": return FOOD;
            case "materials": return MATERIALS;
            case "gold": return GOLD;
            default: return null;
        }
    }
}
