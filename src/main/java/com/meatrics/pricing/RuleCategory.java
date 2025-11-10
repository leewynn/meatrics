package com.meatrics.pricing;

/**
 * Defines the layered pricing categories.
 * Rules are applied in order by category, with multiple rules possible per category.
 *
 * <p>This enum defines the hierarchical pricing layers used in the pricing engine.
 * Each category has a specific order of application, and some categories allow
 * multiple rules to be applied sequentially.</p>
 */
public enum RuleCategory {
    /**
     * Base pricing layer - establishes initial sell price from cost.
     * Typically uses MAINTAIN_GP_PERCENT or category-based COST_PLUS_PERCENT.
     * Only ONE rule from this layer applies per product (first match wins within layer).
     */
    BASE_PRICE(1, "Base Price", true),

    /**
     * Customer-specific adjustments - volume discounts, customer rebates.
     * Multiple rules CAN apply (e.g., volume discount + loyalty discount).
     */
    CUSTOMER_ADJUSTMENT(2, "Customer Adjustment", false),

    /**
     * Product-specific adjustments - premium fees, processing fees.
     * Multiple rules CAN apply (e.g., premium cut fee + packaging fee).
     */
    PRODUCT_ADJUSTMENT(3, "Product Adjustment", false),

    /**
     * Promotional pricing - temporary discounts, seasonal specials.
     * Multiple rules CAN apply (e.g., holiday sale + clearance).
     */
    PROMOTIONAL(4, "Promotional", false);

    private final int order;
    private final String displayName;
    private final boolean singleRuleOnly;

    /**
     * Constructor for RuleCategory enum.
     *
     * @param order The sequence in which this category is applied (1 = first)
     * @param displayName Human-readable name for UI display
     * @param singleRuleOnly If true, only one rule from this category applies per product
     */
    RuleCategory(int order, String displayName, boolean singleRuleOnly) {
        this.order = order;
        this.displayName = displayName;
        this.singleRuleOnly = singleRuleOnly;
    }

    /**
     * Gets the application order of this category.
     * Lower numbers are applied first.
     *
     * @return The order value (1-4)
     */
    public int getOrder() {
        return order;
    }

    /**
     * Gets the human-readable display name for this category.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Indicates whether only one rule from this category can apply per product.
     *
     * @return true if only one rule from this category should be applied, false if multiple rules can apply
     */
    public boolean isSingleRuleOnly() {
        return singleRuleOnly;
    }

    /**
     * Converts a string value to a RuleCategory enum.
     * Returns BASE_PRICE as default if the value is null or invalid.
     *
     * @param category The string representation of the category
     * @return The corresponding RuleCategory enum value, or BASE_PRICE if invalid
     */
    public static RuleCategory fromString(String category) {
        if (category == null) {
            return BASE_PRICE;
        }
        try {
            return valueOf(category);
        } catch (IllegalArgumentException e) {
            return BASE_PRICE;
        }
    }
}
