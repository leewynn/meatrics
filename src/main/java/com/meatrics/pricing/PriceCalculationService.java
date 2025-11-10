package com.meatrics.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for calculating prices using pricing rules.
 * Implements the core pricing engine logic with rule matching and application.
 */
@Service
public class PriceCalculationService {

    private static final Logger log = LoggerFactory.getLogger(PriceCalculationService.class);

    // Constants for GP% capping (removed from calculation, now only for warnings)
    private static final BigDecimal MIN_GP = new BigDecimal("0.10"); // 10%
    private static final BigDecimal MAX_GP = new BigDecimal("0.60"); // 60%

    // Warning thresholds for unusual GP% (configurable - used for alerts only, not enforcement)
    private static final BigDecimal WARNING_LOW_GP = new BigDecimal("0.05");  // 5% - warn if below
    private static final BigDecimal WARNING_HIGH_GP = new BigDecimal("0.70"); // 70% - warn if above

    private final PricingRuleRepository pricingRuleRepository;

    public PriceCalculationService(PricingRuleRepository pricingRuleRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
    }

    /**
     * Calculate price for a grouped line item using pricing rules.
     * Backward compatible method that uses the old single-rule approach.
     *
     * @param item The grouped line item containing product information
     * @param customer The customer for this pricing calculation
     * @return PricingResult containing calculated price and applied rule
     */
    public PricingResult calculatePrice(GroupedLineItem item, Customer customer) {
        // Default to today's date for date-based rule filtering
        return calculatePrice(item, LocalDate.now(), customer);
    }

    /**
     * Calculate price using layered multi-rule approach.
     * Rules are applied layer by layer in category order:
     * 1. BASE_PRICE - Sets initial sell price
     * 2. CUSTOMER_ADJUSTMENT - Apply customer discounts/fees
     * 3. PRODUCT_ADJUSTMENT - Apply product-specific fees
     * 4. PROMOTIONAL - Apply promotional discounts
     *
     * @param item The grouped line item with historical data
     * @param pricingDate The date for which to calculate pricing (for date-based rule filtering)
     * @param customer The customer for customer-specific rules (nullable)
     * @return PricingResult with all applied rules and intermediate prices
     */
    public PricingResult calculatePrice(GroupedLineItem item, LocalDate pricingDate, Customer customer) {
        if (item == null || item.getIncomingCost() == null) {
            log.warn("Cannot calculate price: item or cost is null");
            return new PricingResult(BigDecimal.ZERO, BigDecimal.ZERO, (PricingRule) null, "No data");
        }

        if (pricingDate == null) {
            pricingDate = LocalDate.now();
        }

        BigDecimal currentPrice = item.getIncomingCost();
        List<PricingRule> appliedRules = new ArrayList<>();
        List<BigDecimal> intermediateResults = new ArrayList<>();
        intermediateResults.add(currentPrice); // Starting point (cost)

        // Apply rules layer by layer
        for (RuleCategory category : RuleCategory.values()) {
            List<PricingRule> layerRules = findMatchingRulesInLayer(item, category, pricingDate);

            if (category.isSingleRuleOnly()) {
                // BASE_PRICE: only first matching rule applies
                if (!layerRules.isEmpty()) {
                    PricingRule rule = layerRules.get(0);
                    currentPrice = applyRuleToPrice(currentPrice, rule, item);
                    appliedRules.add(rule);
                    intermediateResults.add(currentPrice);
                    log.debug("Applied {} rule: {} -> {}", category.getDisplayName(), rule.getRuleName(), currentPrice);
                }
            } else {
                // Multiple rules can apply in this layer
                for (PricingRule rule : layerRules) {
                    currentPrice = applyRuleToPrice(currentPrice, rule, item);
                    appliedRules.add(rule);
                    intermediateResults.add(currentPrice);
                    log.debug("Applied {} rule: {} -> {}", category.getDisplayName(), rule.getRuleName(), currentPrice);
                }
            }
        }

        // If no rules applied, use default or return cost
        if (appliedRules.isEmpty()) {
            log.info("No pricing rules matched for product {} customer {}",
                    item.getProductCode(), item.getCustomerCode());
            return new PricingResult(item.getIncomingCost(), item.getIncomingCost(),
                    (PricingRule) null, "No rules matched");
        }

        // Apply final rounding to 6 decimals for storage (display will round to 2)
        BigDecimal finalPrice = currentPrice.setScale(6, RoundingMode.HALF_UP);

        String description = formatMultiRuleDescription(appliedRules);
        return new PricingResult(item.getIncomingCost(), finalPrice,
                appliedRules, intermediateResults, description);
    }

    /**
     * DEPRECATED: Old single-rule calculation method for backward compatibility.
     * This method implements the original first-match-wins strategy.
     * New code should use calculatePrice(item, date, customer) for multi-rule support.
     */
    @Deprecated
    private PricingResult calculatePriceSingleRule(GroupedLineItem item, Customer customer) {
        // Get the cost - use incomingCost if available, otherwise fall back to last cost
        BigDecimal cost = item.getIncomingCost() != null
            ? item.getIncomingCost()
            : (item.getLastCost() != null ? item.getLastCost() : BigDecimal.ZERO);

        // Get product information
        String productCode = item.getProductCode();
        String productCategory = item.getPrimaryGroup();  // Now populated from JOIN

        // Get customer code for error messages and rule lookup
        String customerCode = customer != null ? customer.getCustomerCode() : "NONE";

        // STEP 1: Get customer-specific rules first, then standard rules
        List<PricingRule> rules = new java.util.ArrayList<>();

        if (customer != null && customer.getCustomerCode() != null) {
            // Add customer-specific rules (if any exist)
            rules.addAll(pricingRuleRepository.findByCustomerCode(customer.getCustomerCode()));
        }

        // Add standard rules (customer_code IS NULL)
        rules.addAll(pricingRuleRepository.findStandardRules());

        // Rules are already sorted by priority in repositories, but ensure consistency
        rules.sort(java.util.Comparator.comparing(PricingRule::getPriority));

        // STEP 2: Find first matching rule
        for (PricingRule rule : rules) {
            if (ruleMatchesProduct(rule, productCode, productCategory)) {
                // Apply the pricing method
                BigDecimal calculatedPrice = applyPricingMethod(
                    rule.getPricingMethod(),
                    rule.getPricingValue(),
                    cost,
                    item  // Pass item for MAINTAIN_GP_PERCENT method
                );

                // Format rule description for transparency
                String ruleDescription = formatRuleDescription(rule, item);

                return new PricingResult(cost, calculatedPrice, rule, ruleDescription);
            }
        }

        // STEP 4: No rule matched (should never happen if default rule exists)
        throw new PricingException(
            "No pricing rule matched for customer=" + customerCode +
            ", product=" + productCode +
            ". Ensure at least one default rule (ALL_PRODUCTS) exists."
        );
    }

    /**
     * Find all rules in a specific layer that match the item and are valid on the date.
     *
     * @param item The item to match against
     * @param category The rule category/layer
     * @param pricingDate The date for date-based filtering
     * @return List of matching rules, ordered by layer_order
     */
    private List<PricingRule> findMatchingRulesInLayer(GroupedLineItem item, RuleCategory category, LocalDate pricingDate) {
        // Get all active rules in this category
        List<PricingRule> categoryRules = pricingRuleRepository.findAll().stream()
                .filter(r -> category.equals(r.getRuleCategory()))
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .filter(r -> r.isValidOnDate(pricingDate))
                .sorted(Comparator.comparing(PricingRule::getLayerOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        // Filter by condition matching
        return categoryRules.stream()
                .filter(rule -> ruleMatchesItem(rule, item))
                .collect(Collectors.toList());
    }

    /**
     * Check if a rule matches an item based on its conditions.
     *
     * @param rule The pricing rule to check
     * @param item The grouped line item to match against
     * @return true if the rule should be applied to this item
     */
    private boolean ruleMatchesItem(PricingRule rule, GroupedLineItem item) {
        // Customer-specific rule
        if (rule.getCustomerCode() != null) {
            if (!rule.getCustomerCode().equals(item.getCustomerCode())) {
                return false;
            }
        }

        // Condition-based matching
        String conditionType = rule.getConditionType();
        if (conditionType == null) return true;

        switch (conditionType) {
            case "ALL_PRODUCTS":
                return true;

            case "CATEGORY":
                return rule.getConditionValue() != null
                        && item.getPrimaryGroup() != null
                        && rule.getConditionValue().equalsIgnoreCase(item.getPrimaryGroup());

            case "PRODUCT_CODE":
                return rule.getConditionValue() != null
                        && item.getProductCode() != null
                        && rule.getConditionValue().equalsIgnoreCase(item.getProductCode());

            default:
                return false;
        }
    }

    /**
     * Check if a rule's condition matches the given product
     * DEPRECATED: Use ruleMatchesItem() for new code.
     */
    @Deprecated
    private boolean ruleMatchesProduct(PricingRule rule, String productCode, String productCategory) {
        String conditionType = rule.getConditionType();
        String conditionValue = rule.getConditionValue();

        switch (conditionType) {
            case "ALL_PRODUCTS":
                return true;  // Always matches

            case "CATEGORY":
                // Match if product category equals condition value (case-insensitive)
                return productCategory != null
                    && conditionValue != null
                    && productCategory.equalsIgnoreCase(conditionValue);

            case "PRODUCT_CODE":
                // Match if product code equals condition value (case-insensitive)
                return productCode != null
                    && conditionValue != null
                    && productCode.equalsIgnoreCase(conditionValue);

            default:
                return false;
        }
    }

    /**
     * Apply a single pricing rule to a current price.
     *
     * @param currentPrice The price before applying this rule
     * @param rule The rule to apply
     * @param item The item context (for MAINTAIN_GP_PERCENT)
     * @return The new price after applying the rule
     */
    private BigDecimal applyRuleToPrice(BigDecimal currentPrice, PricingRule rule, GroupedLineItem item) {
        if (currentPrice == null || rule == null) return currentPrice;

        String method = rule.getPricingMethod();
        BigDecimal value = rule.getPricingValue();

        if (value == null && !"MAINTAIN_GP_PERCENT".equals(method)) {
            log.warn("Rule {} has null pricing value, skipping", rule.getRuleName());
            return currentPrice;
        }

        switch (method) {
            case "COST_PLUS_PERCENT":
                // Value is multiplier (1.20 for +20%, 0.80 for -20%)
                // No rounding here - maintain precision for subsequent calculations
                return currentPrice.multiply(value);

            case "COST_PLUS_FIXED":
                // Value is fixed amount to add
                // No rounding here - maintain precision for subsequent calculations
                return currentPrice.add(value);

            case "FIXED_PRICE":
                // Value is the exact price
                // No rounding here - maintain precision for subsequent calculations
                return value;

            case "MAINTAIN_GP_PERCENT":
                // For subsequent layers, this doesn't make sense - skip
                // (MAINTAIN_GP should only be in BASE_PRICE layer)
                if (currentPrice.equals(item.getIncomingCost())) {
                    // This is the first rule (BASE layer)
                    BigDecimal gpPercent = calculateHistoricalGP(item);
                    if (gpPercent == null) {
                        gpPercent = value; // Use default from rule
                    }

                    if (gpPercent == null) {
                        log.warn("MAINTAIN_GP_PERCENT rule has no value and no historical GP%, skipping");
                        return currentPrice;
                    }

                    // price = cost / (1 - GP%)
                    BigDecimal divisor = BigDecimal.ONE.subtract(gpPercent);
                    if (divisor.compareTo(BigDecimal.ZERO) <= 0) {
                        log.warn("Invalid GP% would cause division by zero or negative, skipping");
                        return currentPrice; // Invalid GP%, don't apply
                    }
                    // Use scale 6 for high precision in division
                    return item.getIncomingCost().divide(divisor, 6, RoundingMode.HALF_UP);
                } else {
                    // Not the base price, skip this rule type
                    log.warn("MAINTAIN_GP_PERCENT rule in non-BASE layer, skipping");
                    return currentPrice;
                }

            default:
                log.warn("Unknown pricing method: {}", method);
                return currentPrice;
        }
    }

    /**
     * Apply pricing method calculation to determine sell price
     * DEPRECATED: Use applyRuleToPrice() for new code.
     */
    @Deprecated
    private BigDecimal applyPricingMethod(String method, BigDecimal value, BigDecimal cost, GroupedLineItem item) {
        switch (method) {
            case "COST_PLUS_PERCENT":
                // value is stored as multiplier: 1.20 for 20% markup, 0.80 for -20% rebate
                // Users enter percentages (20 or -20), but they're converted to multipliers before storage
                // price = cost × multiplier
                return cost.multiply(value).setScale(6, RoundingMode.HALF_UP);

            case "COST_PLUS_FIXED":
                // value = 2.50 for $2.50 addition
                // price = cost + 2.50
                return cost.add(value).setScale(6, RoundingMode.HALF_UP);

            case "FIXED_PRICE":
                // value = 28.50 for fixed $28.50
                // price = 28.50 (ignore cost)
                return value.setScale(6, RoundingMode.HALF_UP);

            case "MAINTAIN_GP_PERCENT":
                // value = 0.25 for 25% default GP%
                // Try to calculate historical GP%, fall back to default if no history
                BigDecimal historicalGP = calculateHistoricalGP(item);
                BigDecimal gpToUse = historicalGP != null ? historicalGP : value;

                // Log for debugging
                log.debug("MAINTAIN_GP_PERCENT: product={}, historicalGP={}, gpToUse={}, cost={}, lastGP={}, lastAmount={}",
                    item.getProductCode(),
                    historicalGP != null ? historicalGP.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%" : "null",
                    gpToUse.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%",
                    cost,
                    item.getLastGrossProfit(),
                    item.getLastAmount());

                // Don't cap GP% - maintain exact historical value
                // Only prevent edge cases where GP% >= 100% (would cause division by zero)
                if (gpToUse.compareTo(BigDecimal.ONE) >= 0) {
                    // This is a data quality issue - cap to 99% to prevent calculation errors
                    gpToUse = new BigDecimal("0.99");
                }

                BigDecimal divisor = BigDecimal.ONE.subtract(gpToUse);
                BigDecimal calculatedPrice = cost.divide(divisor, 2, RoundingMode.HALF_UP);

                // Log the result (with safety check for zero price)
                if (calculatedPrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal resultGP = calculatedPrice.subtract(cost).divide(calculatedPrice, 4, RoundingMode.HALF_UP);
                    log.debug("  Result: price={}, resultGP={}", calculatedPrice, resultGP.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%");
                } else {
                    log.debug("  Result: price={} (zero or negative - skipping GP% calculation)", calculatedPrice);
                }

                return calculatedPrice;

            default:
                throw new PricingException("Unknown pricing method: " + method);
        }
    }

    /**
     * Format a description of all applied rules for display.
     */
    private String formatMultiRuleDescription(List<PricingRule> rules) {
        if (rules.isEmpty()) return "No rules";
        if (rules.size() == 1) {
            return formatRuleDescription(rules.get(0), null);
        }

        // Multiple rules: show layer breakdown
        StringBuilder sb = new StringBuilder();
        Map<RuleCategory, List<PricingRule>> byCategory = rules.stream()
                .collect(Collectors.groupingBy(PricingRule::getRuleCategory));

        boolean first = true;
        for (RuleCategory category : RuleCategory.values()) {
            List<PricingRule> categoryRules = byCategory.get(category);
            if (categoryRules != null && !categoryRules.isEmpty()) {
                if (!first) sb.append(" + ");
                sb.append(category.getDisplayName()).append(": ");
                sb.append(categoryRules.stream()
                        .map(PricingRule::getRuleName)
                        .collect(Collectors.joining(", ")));
                first = false;
            }
        }

        return sb.toString();
    }

    /**
     * Format rule description for display in UI.
     * Examples:
     *   "ABC Meats - Ribeye (+12%)"
     *   "Standard Beef (+30%)"
     *   "XYZ Corp - Ribeye (Fixed)"
     *   "Chicken Fee (Cost+$2.50)"
     *   "Discounted Product (-20% rebate)"
     *   "Maintain GP (18.5% GP)" - when historical GP used
     *   "Maintain GP (default 25%)" - when default GP used
     */
    private String formatRuleDescription(PricingRule rule, GroupedLineItem item) {
        StringBuilder sb = new StringBuilder(rule.getRuleName());
        sb.append(" (");

        switch (rule.getPricingMethod()) {
            case "COST_PLUS_PERCENT":
                // Convert multiplier back to percentage for display: 1.20 -> +20%, 0.80 -> -20%
                BigDecimal percentage = rule.getPricingValue().subtract(BigDecimal.ONE)
                        .multiply(new BigDecimal("100"));
                String sign = percentage.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                sb.append(sign).append(percentage.setScale(1, RoundingMode.HALF_UP)).append("%");
                break;
            case "COST_PLUS_FIXED":
                sb.append("Cost+$").append(rule.getPricingValue());
                break;
            case "FIXED_PRICE":
                sb.append("Fixed");
                break;
            case "MAINTAIN_GP_PERCENT":
                BigDecimal historicalGP = calculateHistoricalGP(item);
                if (historicalGP != null) {
                    // Show actual historical GP% used
                    BigDecimal gpPercent = historicalGP.multiply(new BigDecimal("100"));
                    sb.append("Maintained ").append(gpPercent.setScale(1, RoundingMode.HALF_UP)).append("% GP");

                    // Add warning if outside normal range
                    if (historicalGP.compareTo(WARNING_LOW_GP) < 0) {
                        sb.append(" ⚠️ Low");
                    } else if (historicalGP.compareTo(WARNING_HIGH_GP) > 0) {
                        sb.append(" ⚠️ High");
                    }
                } else {
                    // Show default GP%
                    BigDecimal gpPercent = rule.getPricingValue().multiply(new BigDecimal("100"));
                    sb.append("Maintain GP (default ").append(gpPercent.setScale(0, RoundingMode.HALF_UP)).append("%)");
                }
                break;
            default:
                sb.append(rule.getPricingMethod());
        }

        sb.append(")");
        return sb.toString();
    }


    /**
     * Calculate historical gross profit percentage from item's historical data.
     * Formula: GP% = lastGrossProfit / lastAmount
     *
     * This matches the UI's GP% calculation (see PricingSessionsViewNew.calculateGPPercent)
     * to ensure "Maintain GP%" truly maintains the displayed historical GP%.
     *
     * Using totals instead of per-unit values avoids rounding errors that occur when
     * per-unit values are derived from totals (e.g., $3.12 / 2 = $1.56, then recalculated).
     *
     * @param item The grouped line item containing historical pricing data
     * @return Historical GP% as a decimal (0.25 = 25%), or null if no historical data
     */
    private BigDecimal calculateHistoricalGP(GroupedLineItem item) {
        // Check if we have the required historical data
        if (item == null || item.getLastGrossProfit() == null || item.getLastAmount() == null) {
            return null;
        }

        BigDecimal lastGrossProfit = item.getLastGrossProfit();
        BigDecimal lastAmount = item.getLastAmount();

        // Prevent division by zero
        if (lastAmount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        // Calculate: GP / Amount (same as UI formula)

        // Allow negative GP% - maintain historical loss-leader or promotional pricing
        // Use scale 6 for high precision in GP% calculation
        return lastGrossProfit.divide(lastAmount, 6, RoundingMode.HALF_UP);
    }

    /**
     * Cap gross profit percentage to a reasonable range (10%-60%).
     * NOTE: No longer used for MAINTAIN_GP_PERCENT - that rule now uses exact historical GP%.
     * Kept for potential use by other pricing methods.
     *
     * @param gp The gross profit percentage to cap
     * @return The capped GP% within MIN_GP and MAX_GP range
     */
    private BigDecimal capGPPercent(BigDecimal gp) {
        if (gp.compareTo(MIN_GP) < 0) {
            return MIN_GP;
        }
        if (gp.compareTo(MAX_GP) > 0) {
            return MAX_GP;
        }
        return gp;
    }

    /**
     * Exception thrown when pricing calculation fails
     */
    public static class PricingException extends RuntimeException {
        public PricingException(String message) {
            super(message);
        }
    }
}
