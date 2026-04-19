package com.minekings.minekings.economy;

import com.minekings.minekings.village.Village;

/**
 * The daily economic rules for a Village: baseline production from
 * attributes + population, consumption, and population dynamics.
 *
 * <p><b>Philosophy:</b> player-dominant. Baseline production only sustains
 * a modest population (5-10 max, depending on attributes). Hundreds-of-pop
 * villages exist only because the player is actively pumping resources
 * into them via Storehouses.
 *
 * <p>All numbers are tunable; this is where game balance lives.
 */
public final class EconomyRules {
    private EconomyRules() {}

    // ── Consumption ───────────────────────────────────────────────────
    /** Each villager eats this much food per day. */
    public static final double FOOD_CONSUMPTION_PER_POP = 1.0;

    // ── Baseline production ───────────────────────────────────────────
    /** Tiny food production from being settled at all (gardens, foraging). */
    public static final double BASE_FOOD = 1.0;
    /** Tiny materials production (gathering, scavenging). */
    public static final double BASE_MATERIALS = 0.5;
    /** Zero baseline gold — gold is entirely player/commerce driven. */
    public static final double BASE_GOLD = 0.0;

    // ── Attribute bonuses to daily production ─────────────────────────
    /** Attribute → (food_delta, materials_delta, gold_delta) per day. */
    public static Delta bonusFor(String attribute) {
        switch (attribute) {
            case "has_food_industry":   return new Delta(5.0, 0.0, 0.5);
            case "coastal":             return new Delta(3.0, 0.0, 0.0);
            case "has_smithy":          return new Delta(0.0, 4.0, 1.0);
            case "has_arms":            return new Delta(0.0, 2.0, 2.0);
            case "has_craftsmen":       return new Delta(0.0, 3.0, 1.0);
            case "has_granary":         return new Delta(2.0, 0.0, 0.0);
            case "has_inn":             return new Delta(0.5, 0.0, 2.0);
            case "has_library":         return new Delta(0.0, 0.0, 1.0);
            case "has_infirmary":       return new Delta(0.0, 0.0, 0.0);
            case "has_bookkeeper":      return new Delta(0.0, 0.0, 1.5);
            case "is_central":          return new Delta(0.0, 0.0, 3.0);
            case "has_music":           return new Delta(0.0, 0.0, 0.5);
            default:                    return Delta.ZERO;
        }
    }

    // ── Population growth ─────────────────────────────────────────────
    /**
     * A village grows by 1 population when its food stockpile exceeds
     * this many days of consumption for the current population. Growth
     * consumes some of the stockpile.
     */
    public static final double GROWTH_THRESHOLD_DAYS = 5.0;
    public static final double GROWTH_FOOD_COST_MULT = 3.0; // pop grows by 1 costs 3 days of food

    /** Maximum population a village can grow to per day (prevents absurd spikes). */
    public static final int MAX_GROWTH_PER_DAY = 2;

    // ── Population shrinkage (starvation) ─────────────────────────────
    /** When a village has 0 food and food income is negative, lose this much pop per day. */
    public static final int STARVATION_LOSS_PER_DAY = 1;

    /** A Delta carries one day's worth of resource change. */
    public static final class Delta {
        public static final Delta ZERO = new Delta(0.0, 0.0, 0.0);
        public final double food;
        public final double materials;
        public final double gold;

        public Delta(double food, double materials, double gold) {
            this.food = food;
            this.materials = materials;
            this.gold = gold;
        }

        public Delta plus(Delta other) {
            return new Delta(food + other.food, materials + other.materials, gold + other.gold);
        }

        public Delta times(double scale) {
            return new Delta(food * scale, materials * scale, gold * scale);
        }
    }

    /** Total baseline daily production for a village, from attributes only (no population scaling here). */
    public static Delta baselineProduction(Village village) {
        Delta total = new Delta(BASE_FOOD, BASE_MATERIALS, BASE_GOLD);
        for (String attr : village.getAttributes()) {
            total = total.plus(bonusFor(attr));
        }
        return total;
    }

    /** Food consumed by the current population in one day. */
    public static double dailyFoodConsumption(Village village) {
        return village.getPopulation() * FOOD_CONSUMPTION_PER_POP;
    }
}
