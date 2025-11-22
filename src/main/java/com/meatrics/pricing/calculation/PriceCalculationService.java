package com.meatrics.pricing.calculation;

import com.meatrics.pricing.customer.Customer;
import com.meatrics.pricing.customer.CustomerPricingRule;
import com.meatrics.pricing.customer.CustomerPricingRuleRepository;
import com.meatrics.pricing.product.GroupedLineItem;
import com.meatrics.pricing.rule.PricingRule;
import com.meatrics.pricing.rule.PricingRuleRepository;
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

    // Warning thresholds for unusual GP% (configurable - used for alerts only, not enforcement)
    private static final BigDecimal WARNING_LOW_GP = new BigDecimal("0.05");  // 5% - warn if below
    private static final BigDecimal WARNING_HIGH_GP = new BigDecimal("0.70"); // 70% - warn if above

    private final PricingRuleRepository pricingRuleRepository;
    private final CustomerPricingRuleRepository customerPricingRuleRepository;

    public PriceCalculationService(PricingRuleRepository pricingRuleRepository,
                                   CustomerPricingRuleRepository customerPricingRuleRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.customerPricingRuleRepository = customerPricingRuleRepository;
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
     * Calculate price using sequential multi-rule approach.
     * Rules are applied in execution order (1, 2, 3...), with each rule
     * using the output price from the previous rule as its input.
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
        List<com.meatrics.pricing.session.AppliedRuleSnapshot> ruleSnapshots = new ArrayList<>();
        intermediateResults.add(currentPrice); // Starting point (cost)

        int applicationOrder = 1; // Track order of rule application

        // Find all matching rules and apply them in execution order
        List<PricingRule> matchingRules = findAllMatchingRules(item, pricingDate, customer);

        log.debug("Found {} matching rules for product {} customer {}",
                matchingRules.size(), item.getProductCode(), customer != null ? customer.getCustomerCode() : "N/A");

        // Apply all matching rules in execution order (1, 2, 3...)
        for (PricingRule rule : matchingRules) {
            BigDecimal inputPrice = currentPrice;
            currentPrice = applyRuleToPrice(currentPrice, rule, item);
            appliedRules.add(rule);
            intermediateResults.add(currentPrice);

            // Create immutable snapshot
            // For customer-specific rules (negative ID), use null to avoid FK constraint violation
            Long snapshotRuleId = rule.getId() != null && rule.getId() < 0 ? null : rule.getId();
            ruleSnapshots.add(new com.meatrics.pricing.session.AppliedRuleSnapshot(
                snapshotRuleId,
                rule.getRuleName(),
                rule.getPricingMethod(),
                rule.getPricingValue(),
                applicationOrder++,
                inputPrice,
                currentPrice
            ));

            log.debug("Applied rule #{} ({}): {} -> {}",
                    rule.getExecutionOrder(), rule.getRuleName(), inputPrice, currentPrice);
        }

        // Store snapshots in the item for later persistence
        item.setAppliedRuleSnapshots(ruleSnapshots);

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
     * Find all rules that match the item and are valid on the date.
     * Simplified: no more category layers, just execution order.
     *
     * @param item The item to match against
     * @param pricingDate The date for date-based filtering
     * @param customer The customer (for customer-specific rules)
     * @return List of matching rules, ordered by execution_order field
     */
    private List<PricingRule> findAllMatchingRules(GroupedLineItem item, LocalDate pricingDate, Customer customer) {
        List<PricingRule> allRules = new ArrayList<>();

        // Check if customer has custom pricing rules
        boolean hasCustomerRules = false;
        if (customer != null && customer.getCustomerId() != null) {
            hasCustomerRules = customerPricingRuleRepository.hasRules(customer.getCustomerId());
            log.debug("Customer {} has custom rules: {}", customer.getCustomerCode(), hasCustomerRules);
        }

        // 1. Load customer-specific rules if customer is provided
        if (customer != null && customer.getCustomerId() != null) {
            List<CustomerPricingRule> customerRules = customerPricingRuleRepository.findActiveByCustomerId(customer.getCustomerId());
            log.debug("Found {} active customer-specific rules for customer {}",
                customerRules.size(), customer.getCustomerCode());

            // Convert CustomerPricingRule to PricingRule for processing
            for (CustomerPricingRule custRule : customerRules) {
                PricingRule convertedRule = convertCustomerRuleToPricingRule(custRule);
                if (convertedRule != null) {
                    allRules.add(convertedRule);
                }
            }
        }

        // 2. Load global/system rules (only if customer doesn't have custom rules)
        // Database already sorts by execution order, so no need to sort again
        if (!hasCustomerRules) {
            List<PricingRule> globalRules = pricingRuleRepository.findActiveRulesOnDate(pricingDate);
            log.debug("Loaded {} active global rules valid on date {} from database",
                globalRules.size(), pricingDate);

            allRules.addAll(globalRules);
        }

        log.debug("Total rules before condition matching: {}", allRules.size());

        // 3. Filter by condition matching (already sorted by database)
        List<PricingRule> matchingRules = allRules.stream()
                .filter(rule -> ruleMatchesItem(rule, item))
                .collect(Collectors.toList());

        log.debug("Rules after condition matching: {} (product={}, customer={})",
            matchingRules.size(), item.getProductCode(), item.getCustomerCode());

        return matchingRules;
    }

    /**
     * Convert a CustomerPricingRule to a PricingRule for processing
     * Simplified: no longer uses categories, just execution order
     */
    private PricingRule convertCustomerRuleToPricingRule(CustomerPricingRule custRule) {
        PricingRule rule = new PricingRule();

        // Use negative ID to distinguish customer rules from global rules
        rule.setId(custRule.getId() != null ? -custRule.getId() : null);
        rule.setRuleName(custRule.getName());
        rule.setPricingMethod(custRule.getRuleType());
        rule.setPricingValue(custRule.getTargetValue());
        rule.setExecutionOrder(custRule.getExecutionOrder());
        rule.setIsActive(custRule.getIsActive());

        // Set condition matching (PricingRule uses conditionType and conditionValue)
        if (custRule.getAppliesToProduct() != null) {
            rule.setConditionType("PRODUCT_CODE");
            rule.setConditionValue(custRule.getAppliesToProduct());
        } else if (custRule.getAppliesToCategory() != null) {
            rule.setConditionType("CATEGORY");
            rule.setConditionValue(custRule.getAppliesToCategory());
        } else {
            rule.setConditionType("ALL_PRODUCTS");
            rule.setConditionValue(null);
        }

        return rule;
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
                // This rule should typically be the first rule applied
                // For subsequent rules, it doesn't make sense to maintain GP% from a modified price
                if (currentPrice.equals(item.getIncomingCost())) {
                    // This is the first rule being applied
                    BigDecimal historicalGP = calculateHistoricalGP(item);

                    if (historicalGP == null) {
                        log.warn("MAINTAIN_GP_PERCENT rule requires historical pricing data, skipping");
                        return currentPrice;
                    }

                    // Apply adjustment to historical GP%
                    // value is the adjustment (e.g., 0.03 for +3%, -0.02 for -2%, 0.00 for no adjustment)
                    BigDecimal adjustment = value != null ? value : BigDecimal.ZERO;
                    BigDecimal targetGP = historicalGP.add(adjustment);

                    log.debug("MAINTAIN_GP_PERCENT: historical={}%, adjustment={}%, target={}%",
                        historicalGP.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
                        adjustment.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
                        targetGP.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));

                    // Validate target GP% is reasonable (must be < 100% to avoid divide-by-zero)
                    // Note: Negative GP% is allowed (selling below cost is a valid business scenario)
                    if (targetGP.compareTo(BigDecimal.ONE) >= 0) {
                        log.warn("Adjusted GP% is >= 100% ({}%), capping at 99% to prevent divide-by-zero",
                            targetGP.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
                        targetGP = new BigDecimal("0.99");
                    }

                    // price = cost / (1 - GP%)
                    BigDecimal divisor = BigDecimal.ONE.subtract(targetGP);
                    return item.getIncomingCost().divide(divisor, 6, RoundingMode.HALF_UP);
                } else {
                    // This rule should only be applied as the first rule
                    log.warn("MAINTAIN_GP_PERCENT rule not applied as first rule, skipping");
                    return currentPrice;
                }

            case "TARGET_GP_PERCENT":
                // Always uses the specified target GP%, does not fall back to historical
                // Can be used at any point in the rule execution sequence
                if (value == null) {
                    log.warn("TARGET_GP_PERCENT rule requires a target value, skipping");
                    return currentPrice;
                }

                // price = cost / (1 - target_GP%)
                // If this is the first rule, use incoming cost
                // Otherwise, calculate GP% on current price from previous rule
                BigDecimal costToUse = currentPrice.equals(item.getIncomingCost())
                    ? item.getIncomingCost()
                    : currentPrice;

                BigDecimal divisor = BigDecimal.ONE.subtract(value);
                if (divisor.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("Invalid target GP% would cause division by zero or negative, skipping");
                    return currentPrice;
                }
                // Use scale 6 for high precision in division
                return costToUse.divide(divisor, 6, RoundingMode.HALF_UP);

            default:
                log.warn("Unknown pricing method: {}", method);
                return currentPrice;
        }
    }

    /**
     * Format a description of all applied rules for display.
     * Simplified: no longer uses categories, just shows rule names in execution order.
     */
    private String formatMultiRuleDescription(List<PricingRule> rules) {
        if (rules.isEmpty()) return "No rules";
        if (rules.size() == 1) {
            return formatRuleDescription(rules.get(0), null);
        }

        // Multiple rules: show all rules in execution order
        return rules.stream()
                .map(PricingRule::getRuleName)
                .collect(Collectors.joining(" → "));
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
     * This matches the UI's GP% calculation (see PricingSessionsView.calculateGPPercent)
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
     * Exception thrown when pricing calculation fails
     */
    public static class PricingException extends RuntimeException {
        public PricingException(String message) {
            super(message);
        }
    }
}
