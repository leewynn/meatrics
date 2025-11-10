package com.meatrics.pricing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a price calculation operation.
 * Contains the calculated price, the cost used, the rules that were applied, and a description.
 *
 * <p>This class supports both single-rule and multi-rule pricing scenarios.
 * In multi-rule scenarios, multiple pricing rules are applied sequentially,
 * with each rule modifying the price calculated by the previous rule.</p>
 *
 * <p>The {@code intermediateResults} list tracks the price after each rule application,
 * allowing for detailed price calculation auditing.</p>
 */
public class PricingResult {
    private final BigDecimal cost;
    private final BigDecimal calculatedPrice;
    private final List<PricingRule> appliedRules;        // Multiple rules can be applied
    private final List<BigDecimal> intermediateResults;  // Price after each rule
    private final String ruleDescription;

    /**
     * Constructor for multi-rule pricing results.
     *
     * @param cost The base cost used for calculation
     * @param calculatedPrice The final calculated price after all rules
     * @param appliedRules List of pricing rules applied in order
     * @param intermediateResults List of prices after each rule application (same size as appliedRules)
     * @param ruleDescription Human-readable description of the pricing calculation
     */
    public PricingResult(BigDecimal cost, BigDecimal calculatedPrice,
                        List<PricingRule> appliedRules,
                        List<BigDecimal> intermediateResults,
                        String ruleDescription) {
        this.cost = cost;
        this.calculatedPrice = calculatedPrice;
        this.appliedRules = Collections.unmodifiableList(new ArrayList<>(appliedRules));
        this.intermediateResults = Collections.unmodifiableList(new ArrayList<>(intermediateResults));
        this.ruleDescription = ruleDescription;
    }

    /**
     * Constructor for single-rule pricing results (backward compatible).
     *
     * @param cost The base cost used for calculation
     * @param calculatedPrice The final calculated price
     * @param appliedRule The single pricing rule applied
     * @param ruleDescription Human-readable description of the pricing calculation
     */
    public PricingResult(BigDecimal cost, BigDecimal calculatedPrice,
                        PricingRule appliedRule, String ruleDescription) {
        this.cost = cost;
        this.calculatedPrice = calculatedPrice;
        this.appliedRules = appliedRule != null ? List.of(appliedRule) : List.of();
        this.intermediateResults = List.of(calculatedPrice);
        this.ruleDescription = ruleDescription;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public BigDecimal getCalculatedPrice() {
        return calculatedPrice;
    }

    /**
     * Get all applied pricing rules in the order they were applied.
     *
     * @return Unmodifiable list of applied rules (may be empty)
     */
    public List<PricingRule> getAppliedRules() {
        return appliedRules;
    }

    /**
     * Get the primary (first) applied rule for backward compatibility.
     * In single-rule scenarios, this returns the only rule.
     * In multi-rule scenarios, this returns the base pricing rule.
     *
     * @return The first applied rule, or null if no rules were applied
     */
    public PricingRule getAppliedRule() {
        return appliedRules.isEmpty() ? null : appliedRules.get(0);
    }

    /**
     * Get intermediate calculation results showing price after each rule.
     * The list size equals the number of applied rules.
     * The last element equals the final calculated price.
     *
     * @return Unmodifiable list of intermediate prices (may be empty)
     */
    public List<BigDecimal> getIntermediateResults() {
        return intermediateResults;
    }

    /**
     * Check if multiple rules were applied to calculate the price.
     *
     * @return true if more than one rule was applied, false otherwise
     */
    public boolean isMultiRule() {
        return appliedRules.size() > 1;
    }

    public String getRuleDescription() {
        return ruleDescription;
    }
}
