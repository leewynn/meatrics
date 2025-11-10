package com.meatrics.pricing;

import java.util.List;

/**
 * Result of previewing a pricing rule before saving.
 * Contains the count of matching products and (for specific rules) a list of price previews.
 */
public class RulePreviewResult {
    private final int totalMatchCount;
    private final boolean isAllProducts;
    private final List<PricePreview> previews;

    public RulePreviewResult(int totalMatchCount, boolean isAllProducts, List<PricePreview> previews) {
        this.totalMatchCount = totalMatchCount;
        this.isAllProducts = isAllProducts;
        this.previews = previews;
    }

    /**
     * Factory method for ALL_PRODUCTS rules - no detailed preview needed for performance
     */
    public static RulePreviewResult allProducts(int count) {
        return new RulePreviewResult(count, true, List.of());
    }

    public int getTotalMatchCount() {
        return totalMatchCount;
    }

    public boolean isAllProducts() {
        return isAllProducts;
    }

    public List<PricePreview> getPreviews() {
        return previews;
    }
}
