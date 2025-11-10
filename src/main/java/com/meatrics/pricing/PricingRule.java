package com.meatrics.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pricing rule entity representing the pricing_rule table.
 * Defines dynamic pricing rules for calculating sell prices based on conditions.
 *
 * <p><b>Pricing Methods:</b></p>
 * <ul>
 *   <li><b>COST_PLUS_PERCENT:</b> Apply percentage markup or rebate to cost.
 *       Users enter percentages (20 for 20% markup, -20 for 20% rebate).
 *       Stored as multiplier (1.20 or 0.80). Formula: price = cost × multiplier</li>
 *   <li><b>COST_PLUS_FIXED:</b> Add fixed amount to cost (e.g., $2.50)</li>
 *   <li><b>FIXED_PRICE:</b> Set absolute price regardless of cost (e.g., $28.50)</li>
 *   <li><b>MAINTAIN_GP_PERCENT:</b> Maintain historical gross profit percentage.
 *       Calculates historical GP% from lastUnitSellPrice and lastCost, then applies
 *       to new incoming cost. Falls back to pricingValue as default GP% if no history.
 *       Formula: newPrice = incomingCost / (1 - GP%)</li>
 * </ul>
 */
public class PricingRule {
    private Long id;
    private String ruleName;
    private String customerCode;  // NULL = standard rule, value = customer-specific
    private String conditionType;  // 'ALL_PRODUCTS', 'CATEGORY', 'PRODUCT_CODE'
    private String conditionValue; // Category name or product code (NULL for ALL_PRODUCTS)
    private String pricingMethod;  // 'COST_PLUS_PERCENT', 'COST_PLUS_FIXED', 'FIXED_PRICE', 'MAINTAIN_GP_PERCENT'
    private BigDecimal pricingValue; // The multiplier, amount, or default GP% (for MAINTAIN_GP_PERCENT)
    private Integer priority;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Phase 2 additions: Rule categorization and date-based validity
    private RuleCategory ruleCategory;  // Pricing layer (BASE_PRICE, CUSTOMER_ADJUSTMENT, etc.)
    private Integer layerOrder;         // Order within category for deterministic sorting
    private LocalDate validFrom;        // Rule activation date (nullable)
    private LocalDate validTo;          // Rule expiration date (nullable)

    public PricingRule() {
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getConditionType() {
        return conditionType;
    }

    public void setConditionType(String conditionType) {
        this.conditionType = conditionType;
    }

    public String getConditionValue() {
        return conditionValue;
    }

    public void setConditionValue(String conditionValue) {
        this.conditionValue = conditionValue;
    }

    public String getPricingMethod() {
        return pricingMethod;
    }

    public void setPricingMethod(String pricingMethod) {
        this.pricingMethod = pricingMethod;
    }

    public BigDecimal getPricingValue() {
        return pricingValue;
    }

    public void setPricingValue(BigDecimal pricingValue) {
        this.pricingValue = pricingValue;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Check if this is a standard rule (applies to all customers)
     */
    public boolean isStandardRule() {
        return customerCode == null;
    }

    /**
     * Check if this is a customer-specific rule
     */
    public boolean isCustomerSpecific() {
        return customerCode != null;
    }

    // Getters and setters for Phase 2 fields
    public RuleCategory getRuleCategory() {
        return ruleCategory;
    }

    public void setRuleCategory(RuleCategory ruleCategory) {
        this.ruleCategory = ruleCategory;
    }

    public Integer getLayerOrder() {
        return layerOrder;
    }

    public void setLayerOrder(Integer layerOrder) {
        this.layerOrder = layerOrder;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    /**
     * Check if this rule is valid on a specific date.
     * A rule is valid if:
     * - The date is on or after validFrom (if validFrom is set)
     * - The date is on or before validTo (if validTo is set)
     *
     * @param date The date to check (if null, no date validation is performed)
     * @return true if rule is active on this date
     */
    public boolean isValidOnDate(LocalDate date) {
        if (date == null) {
            return true;  // No date check requested
        }

        boolean afterStart = (validFrom == null || !date.isBefore(validFrom));
        boolean beforeEnd = (validTo == null || !date.isAfter(validTo));

        return afterStart && beforeEnd;
    }

    /**
     * Check if this rule is currently active (valid today and enabled).
     * A rule is currently active if:
     * - It is valid on today's date (validFrom/validTo check)
     * - The isActive flag is set to true
     *
     * @return true if the rule should be applied to pricing calculations today
     */
    public boolean isCurrentlyActive() {
        return isValidOnDate(LocalDate.now()) && Boolean.TRUE.equals(isActive);
    }

    /**
     * Get formatted rule description for UI display.
     * Shows rule name, category, pricing method, and date validity status.
     */
    public String getFormattedDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(ruleName != null ? ruleName : "Unnamed Rule");

        // Add category badge
        if (ruleCategory != null) {
            sb.append(" [").append(ruleCategory.getDisplayName()).append("]");
        }

        // Add pricing method
        if (pricingMethod != null) {
            sb.append(" (");

            switch (pricingMethod) {
                case "COST_PLUS_PERCENT":
                    // Convert multiplier back to percentage for display: 1.20 -> +20%, 0.80 -> -20%
                    if (pricingValue != null) {
                        BigDecimal percentage = pricingValue.subtract(BigDecimal.ONE)
                                .multiply(new BigDecimal("100"));
                        String sign = percentage.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                        sb.append(sign).append(percentage.setScale(1, java.math.RoundingMode.HALF_UP)).append("%");
                    }
                    break;
                case "COST_PLUS_FIXED":
                    sb.append("Cost+$").append(pricingValue);
                    break;
                case "FIXED_PRICE":
                    sb.append("Fixed $").append(pricingValue);
                    break;
                case "MAINTAIN_GP_PERCENT":
                    // Show default GP% that will be used as fallback
                    if (pricingValue != null) {
                        BigDecimal gpPercent = pricingValue.multiply(new BigDecimal("100"));
                        sb.append("Maintain GP% (default ").append(gpPercent.setScale(0, java.math.RoundingMode.HALF_UP)).append("%)");
                    } else {
                        sb.append("Maintain GP% (historical only)");
                    }
                    break;
                default:
                    sb.append(pricingMethod);
            }

            sb.append(")");
        }

        // Add date status if applicable
        LocalDate now = LocalDate.now();
        if (!isValidOnDate(now)) {
            if (validFrom != null && now.isBefore(validFrom)) {
                sb.append(" [Future - starts ").append(validFrom).append("]");
            } else if (validTo != null && now.isAfter(validTo)) {
                sb.append(" [Expired - ended ").append(validTo).append("]");
            }
        }

        return sb.toString();
    }
}
