package com.minekings.minekings.economy;

/**
 * One entry in the conversion table: item id -> resource + units per item.
 * Datapack-loaded via {@link ConversionTable}.
 */
public final class ResourceConversion {
    private final ResourceType resource;
    private final double perItem;

    public ResourceConversion(ResourceType resource, double perItem) {
        this.resource = resource;
        this.perItem = perItem;
    }

    public ResourceType resource() { return resource; }
    public double perItem() { return perItem; }
}
