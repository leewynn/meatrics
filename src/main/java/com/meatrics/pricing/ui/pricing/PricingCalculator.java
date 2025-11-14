package com.meatrics.pricing.ui.pricing;

import com.meatrics.pricing.customer.Customer;
import com.meatrics.pricing.customer.CustomerRepository;
import com.meatrics.pricing.product.GroupedLineItem;
import com.meatrics.pricing.calculation.PriceCalculationService;
import com.meatrics.pricing.calculation.PricingResult;
import com.meatrics.pricing.rule.PricingRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles pricing calculations and business logic for pricing sessions.
 * Manages rule application, field recalculation, and GP% calculations.
 */
@Component
public class PricingCalculator {

    private static final Logger log = LoggerFactory.getLogger(PricingCalculator.class);

    private final PriceCalculationService priceCalculationService;
    private final CustomerRepository customerRepository;

    public PricingCalculator(PriceCalculationService priceCalculationService,
                            CustomerRepository customerRepository) {
        this.priceCalculationService = priceCalculationService;
        this.customerRepository = customerRepository;
    }

    /**
     * Apply pricing rules to all items in the list
     *
     * @return PricingApplicationResult with success/error counts
     */
    public PricingApplicationResult applyPricingRules(List<GroupedLineItem> items) {
        int successCount = 0;
        int errorCount = 0;

        for (GroupedLineItem item : items) {
            try {
                // Get customer
                Customer customer = customerRepository.findByCustomerCode(item.getCustomerCode())
                        .orElse(null);

                // Calculate price using pricing engine with current date
                PricingResult result = priceCalculationService.calculatePrice(item, LocalDate.now(), customer);

                // Apply results to item - support multi-rule
                item.setNewUnitSellPrice(result.getCalculatedPrice());
                item.setAppliedRules(result.getAppliedRules());
                item.setIntermediateResults(result.getIntermediateResults());
                item.setManualOverride(false);

                // Recalculate amounts and GP
                recalculateItemFields(item);

                successCount++;
            } catch (Exception e) {
                log.error("Error calculating price for item: " + item.getGroupingKey(), e);
                errorCount++;
            }
        }

        log.info("Applied pricing rules: {} successful, {} errors", successCount, errorCount);
        return new PricingApplicationResult(successCount, errorCount);
    }

    /**
     * Recalculate derived fields for an item (amount, GP)
     */
    public void recalculateItemFields(GroupedLineItem item) {
        if (item.getNewUnitSellPrice() != null && item.getTotalQuantity() != null) {
            // Calculate new amount - use scale 6 for storage precision
            // Display rounding to 2 decimals handled by formatCurrency()
            item.setNewAmount(item.getNewUnitSellPrice().multiply(item.getTotalQuantity())
                    .setScale(6, RoundingMode.HALF_UP));

            // Calculate new gross profit - use scale 6 for storage precision
            if (item.getIncomingCost() != null) {
                BigDecimal totalCost = item.getIncomingCost().multiply(item.getTotalQuantity());
                item.setNewGrossProfit(item.getNewAmount().subtract(totalCost)
                        .setScale(6, RoundingMode.HALF_UP));
            }
        }
    }

    /**
     * Calculate GP% from gross profit and amount
     * Formula: (GP / Amount) × 100
     * Returns value already multiplied by 100 (e.g., 23.5 for 23.5%)
     * with 6 decimal places precision for accurate comparison
     */
    public BigDecimal calculateGPPercent(BigDecimal grossProfit, BigDecimal amount) {
        if (grossProfit == null || amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        // Use 6 decimal precision throughout for consistency
        return grossProfit.divide(amount, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Format GP% value for display
     */
    public String formatGPPercent(BigDecimal grossProfit, BigDecimal amount) {
        BigDecimal gpPercent = calculateGPPercent(grossProfit, amount);
        return gpPercent != null ? String.format("%.1f%%", gpPercent) : "-";
    }

    /**
     * Determine GP% coloring based on comparison with tolerance
     * Returns: "green" if improved, "red" if declined, null if within tolerance or no change
     */
    public String determineGPPercentColor(BigDecimal newGPPercent, BigDecimal lastGPPercent, BigDecimal tolerance) {
        if (newGPPercent == null || lastGPPercent == null) {
            return null;
        }

        BigDecimal difference = newGPPercent.subtract(lastGPPercent);
        BigDecimal absDifference = difference.abs();

        // No color if within tolerance (0.1 percentage points)
        if (absDifference.compareTo(tolerance) <= 0) {
            return null;
        }

        // Green if GP% increased (positive difference)
        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            return "green";
        }

        // Red if GP% decreased (negative difference)
        if (difference.compareTo(BigDecimal.ZERO) < 0) {
            return "red";
        }

        return null;
    }

    /**
     * Recalculate intermediate pricing results for loaded sessions.
     * Applies each rule step-by-step to recreate the calculation breakdown.
     */
    public List<BigDecimal> recalculateIntermediateResults(GroupedLineItem item, List<PricingRule> appliedRules) {
        List<BigDecimal> intermediates = new ArrayList<>();
        BigDecimal currentPrice = item.getIncomingCost();
        intermediates.add(currentPrice); // Starting cost

        for (PricingRule rule : appliedRules) {
            currentPrice = applyRuleToPrice(currentPrice, rule, item);
            intermediates.add(currentPrice);
        }

        return intermediates;
    }

    /**
     * Apply a single pricing rule to a price (mirrors PriceCalculationService logic)
     */
    private BigDecimal applyRuleToPrice(BigDecimal currentPrice, PricingRule rule, GroupedLineItem item) {
        if (currentPrice == null || rule == null) return currentPrice;

        String method = rule.getPricingMethod();
        BigDecimal value = rule.getPricingValue();

        if (value == null && !"MAINTAIN_GP_PERCENT".equals(method)) {
            return currentPrice;
        }

        switch (method) {
            case "COST_PLUS_PERCENT":
                return currentPrice.multiply(value);

            case "COST_PLUS_FIXED":
                return currentPrice.add(value);

            case "FIXED_PRICE":
                return value;

            case "MAINTAIN_GP_PERCENT":
                // For loaded sessions, use the stored newUnitSellPrice directly
                // since we don't have the exact historical GP% calculation context
                if (item.getLastGrossProfit() != null && item.getLastAmount() != null
                    && item.getLastAmount().compareTo(BigDecimal.ZERO) != 0) {
                    // Calculate historical GP%
                    BigDecimal historicalGP = item.getLastGrossProfit().divide(item.getLastAmount(), 6, RoundingMode.HALF_UP);
                    BigDecimal divisor = BigDecimal.ONE.subtract(historicalGP);
                    if (divisor.compareTo(BigDecimal.ZERO) > 0) {
                        return item.getIncomingCost().divide(divisor, 6, RoundingMode.HALF_UP);
                    }
                } else if (value != null) {
                    // Use default GP% from rule
                    BigDecimal divisor = BigDecimal.ONE.subtract(value);
                    if (divisor.compareTo(BigDecimal.ZERO) > 0) {
                        return item.getIncomingCost().divide(divisor, 6, RoundingMode.HALF_UP);
                    }
                }
                return currentPrice;

            default:
                return currentPrice;
        }
    }

    /**
     * Format currency value
     */
    public String formatCurrency(BigDecimal value) {
        return value != null ? String.format("$%.2f", value) : "-";
    }

    /**
     * Result of applying pricing rules
     */
    public static class PricingApplicationResult {
        private final int successCount;
        private final int errorCount;

        public PricingApplicationResult(int successCount, int errorCount) {
            this.successCount = successCount;
            this.errorCount = errorCount;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public String getMessage() {
            String message = String.format("Applied rules to %d items", successCount);
            if (errorCount > 0) {
                message += String.format(" (%d errors)", errorCount);
            }
            return message;
        }
    }
}
