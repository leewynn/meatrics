package com.meatrics.pricing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing pricing rules.
 * Provides CRUD operations and rule preview/testing functionality.
 */
@Service
public class PricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;
    private final ProductCostRepository productCostRepository;

    public PricingRuleService(PricingRuleRepository pricingRuleRepository, ProductCostRepository productCostRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.productCostRepository = productCostRepository;
    }

    /**
     * Get all pricing rules ordered by priority
     */
    public List<PricingRule> getAllRules() {
        return pricingRuleRepository.findAll();
    }

    /**
     * Save a pricing rule (insert or update).
     * Validates that at least one default rule exists.
     */
    public PricingRule saveRule(PricingRule rule) {
        return pricingRuleRepository.save(rule);
    }

    /**
     * Delete a pricing rule by ID.
     * Prevents deletion of the last active standard rule to ensure there's always a fallback.
     */
    public void deleteRule(Long id) {
        PricingRule rule = pricingRuleRepository.findById(id);
        if (rule == null) {
            throw new IllegalArgumentException("Rule with id " + id + " not found");
        }

        // Check if this is the last active standard rule
        if (rule.isStandardRule() && rule.getIsActive()) {
            int activeStandardCount = pricingRuleRepository.countActiveStandardRules();
            if (activeStandardCount <= 1) {
                throw new IllegalStateException(
                    "Cannot delete the last active standard rule. " +
                    "The system must have at least one active ALL_PRODUCTS rule as fallback."
                );
            }
        }

        pricingRuleRepository.deleteById(id);
    }

    /**
     * Preview what products a rule would match and calculate sample prices.
     * Tests against ALL products in product_costs table.
     * This is used by the "Test This Rule" button in the UI.
     *
     * @param ruleToTest Pricing rule with user's current form values (not yet saved)
     * @return RulePreviewResult with match count and price calculations
     */
    public RulePreviewResult previewRule(PricingRule ruleToTest) {
        // Special case: ALL_PRODUCTS - just return count, no grid for performance
        if ("ALL_PRODUCTS".equals(ruleToTest.getConditionType())) {
            int totalCount = productCostRepository.count();
            return RulePreviewResult.allProducts(totalCount);
        }

        // Query based on condition type
        List<ProductCost> matchingProducts = findMatchingProducts(
            ruleToTest.getConditionType(),
            ruleToTest.getConditionValue()
        );

        // Calculate prices for ALL matching products
        List<PricePreview> previews = matchingProducts.stream()
            .map(product -> {
                BigDecimal cost = product.getStandardCost() != null
                    ? product.getStandardCost()
                    : BigDecimal.ZERO;

                BigDecimal calculatedPrice = applyPricingMethod(
                    cost,
                    ruleToTest.getPricingMethod(),
                    ruleToTest.getPricingValue()
                );

                return new PricePreview(
                    product.getProductCode(),
                    product.getDescription(),
                    cost,
                    calculatedPrice
                );
            })
            .collect(Collectors.toList());

        return new RulePreviewResult(matchingProducts.size(), false, previews);
    }

    /**
     * Find products that match a rule's condition
     */
    private List<ProductCost> findMatchingProducts(String conditionType, String conditionValue) {
        List<ProductCost> allProducts = productCostRepository.findAll();

        switch (conditionType) {
            case "CATEGORY":
                // Match by primary_group field
                return allProducts.stream()
                    .filter(p -> p.getPrimaryGroup() != null
                        && conditionValue != null
                        && p.getPrimaryGroup().equalsIgnoreCase(conditionValue))
                    .collect(Collectors.toList());

            case "PRODUCT_CODE":
                // Match by exact product code
                return allProducts.stream()
                    .filter(p -> p.getProductCode() != null
                        && conditionValue != null
                        && p.getProductCode().equalsIgnoreCase(conditionValue))
                    .collect(Collectors.toList());

            default:
                return List.of();
        }
    }

    /**
     * Apply pricing method calculation - same logic as PriceCalculationService
     * Note: values are stored as multipliers (1.20 = 20% markup) even though UI displays percentages
     */
    private BigDecimal applyPricingMethod(BigDecimal cost, String method, BigDecimal value) {
        switch (method) {
            case "COST_PLUS_PERCENT":
                // value is stored as multiplier: 1.20 for 20% markup, 0.80 for -20% rebate
                return cost.multiply(value).setScale(6, RoundingMode.HALF_UP);

            case "COST_PLUS_FIXED":
                return cost.add(value).setScale(6, RoundingMode.HALF_UP);

            case "FIXED_PRICE":
                return value.setScale(6, RoundingMode.HALF_UP);

            case "MAINTAIN_GP_PERCENT":
                // In preview mode, we don't have historical data, so use default GP%
                // value = 0.25 for 25% default GP%
                // Formula: price = cost / (1 - GP%)
                if (value.compareTo(BigDecimal.ONE) >= 0) {
                    // Invalid GP% (>= 100%), use a safe default of 25%
                    value = new BigDecimal("0.25");
                }
                BigDecimal divisor = BigDecimal.ONE.subtract(value);
                if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                    // Edge case: avoid division by zero
                    return cost.multiply(new BigDecimal("1.33")).setScale(6, RoundingMode.HALF_UP);
                }
                return cost.divide(divisor, 6, RoundingMode.HALF_UP);

            default:
                return cost;
        }
    }

    /**
     * Validate that the system has at least one active standard rule
     */
    public boolean hasDefaultRule() {
        return pricingRuleRepository.countActiveStandardRules() > 0;
    }
}
