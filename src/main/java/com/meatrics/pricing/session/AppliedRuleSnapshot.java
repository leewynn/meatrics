package com.meatrics.pricing.session;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable snapshot of a pricing rule application.
 * Records exactly how a rule was applied to a line item, including
 * the rule's state at that moment and the pricing chain step.
 *
 * This provides a complete audit trail even if the original rule is
 * modified or deleted later.
 */
public class AppliedRuleSnapshot {

    private Long id;
    private Long sessionLineItemId;
    private Long ruleId;  // May be null if rule was deleted

    // Immutable snapshot of rule at application time
    private String ruleName;
    private String ruleCategory;      // BASE_PRICE, CUSTOMER_ADJUSTMENT, etc.
    private String pricingMethod;     // COST_PLUS_PERCENT, FIXED_PRICE, etc.
    private BigDecimal pricingValue;  // The multiplier or value used

    // Pricing chain tracking
    private Integer applicationOrder;  // Order rule was applied (1, 2, 3...)
    private BigDecimal inputPrice;     // Price before this rule
    private BigDecimal outputPrice;    // Price after this rule

    // Metadata
    private LocalDateTime appliedAt;

    public AppliedRuleSnapshot() {
    }

    /**
     * Constructor for creating a snapshot from a rule application
     */
    public AppliedRuleSnapshot(Long ruleId, String ruleName, String ruleCategory,
                               String pricingMethod, BigDecimal pricingValue,
                               Integer applicationOrder, BigDecimal inputPrice,
                               BigDecimal outputPrice) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.ruleCategory = ruleCategory;
        this.pricingMethod = pricingMethod;
        this.pricingValue = pricingValue;
        this.applicationOrder = applicationOrder;
        this.inputPrice = inputPrice;
        this.outputPrice = outputPrice;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionLineItemId() {
        return sessionLineItemId;
    }

    public void setSessionLineItemId(Long sessionLineItemId) {
        this.sessionLineItemId = sessionLineItemId;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleCategory() {
        return ruleCategory;
    }

    public void setRuleCategory(String ruleCategory) {
        this.ruleCategory = ruleCategory;
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

    public Integer getApplicationOrder() {
        return applicationOrder;
    }

    public void setApplicationOrder(Integer applicationOrder) {
        this.applicationOrder = applicationOrder;
    }

    public BigDecimal getInputPrice() {
        return inputPrice;
    }

    public void setInputPrice(BigDecimal inputPrice) {
        this.inputPrice = inputPrice;
    }

    public BigDecimal getOutputPrice() {
        return outputPrice;
    }

    public void setOutputPrice(BigDecimal outputPrice) {
        this.outputPrice = outputPrice;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    /**
     * Check if this snapshot represents a rebate rule
     */
    public boolean isRebate() {
        return "CUSTOMER_ADJUSTMENT".equals(ruleCategory)
            && "COST_PLUS_PERCENT".equals(pricingMethod)
            && pricingValue != null
            && pricingValue.compareTo(BigDecimal.ONE) < 0;
    }

    @Override
    public String toString() {
        return String.format("AppliedRuleSnapshot[%s: %s -> %s via %s(%.4f)]",
            ruleName, inputPrice, outputPrice, pricingMethod, pricingValue);
    }
}
