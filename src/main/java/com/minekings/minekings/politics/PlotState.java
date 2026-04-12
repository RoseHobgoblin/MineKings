package com.minekings.minekings.politics;

public enum PlotState {
    EMPTY,
    UNDER_CONSTRUCTION,
    BUILT,
    DEMOLISHING;

    public static PlotState fromString(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EMPTY;
        }
    }
}
