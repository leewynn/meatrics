package com.meatrics.pricing.product;

import com.meatrics.generated.tables.records.VGroupedLineItemsRecord;
import com.meatrics.pricing.rule.PricingRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Grouped line item representing aggregated sales data for a customer-product combination
 * Used in Pricing Sessions view to show consolidated pricing patterns
 * Wraps jOOQ VGroupedLineItemsRecord with transient UI state
 */
public class GroupedLineItem {
    // Grouping keys
    private String customerCode;
    private String customerName;
    private String productCode;
    private String productDescription;

    // Product category for rule matching
    private String primaryGroup;

    // Customer rating for context
    private String customerRating;

    // Aggregated values (original/existing)
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private BigDecimal totalCost;

    // Historical data (from v_grouped_line_items)
    private BigDecimal lastCost;          // Historical cost when sold
    private BigDecimal lastUnitSellPrice; // Historical sell price
    private BigDecimal lastAmount;        // Historical total amount
    private BigDecimal lastGrossProfit;   // Historical gross profit

    // New pricing data
    private BigDecimal incomingCost;      // From product_costs.stdcost
    private BigDecimal newUnitSellPrice;  // Calculated by rules or manual
    private BigDecimal newAmount;         // Calculated: newUnitSellPrice × qty
    private BigDecimal newGrossProfit;    // Calculated: newAmount - (incomingCost × qty)

    // Transient fields for UI state (modifications and rule tracking)
    private transient boolean amountModified = false;
    private transient BigDecimal originalAmount = null;
    private transient boolean manualOverride;   // User edited price flag

    // Multi-rule support
    private transient List<PricingRule> appliedRules;          // Multiple rules can be applied
    private transient List<BigDecimal> intermediateResults;    // Prices after each rule

    // Immutable rule snapshots for audit trail
    private transient List<com.meatrics.pricing.session.AppliedRuleSnapshot> appliedRuleSnapshots;

    public GroupedLineItem() {
    }

    /**
     * Factory method to create GroupedLineItem from jOOQ record
     */
    public static GroupedLineItem fromRecord(VGroupedLineItemsRecord record) {
        GroupedLineItem item = new GroupedLineItem();
        item.setCustomerCode(record.getCustomerCode());
        item.setCustomerName(record.getCustomerName());
        item.setProductCode(record.getProductCode());
        item.setProductDescription(record.getProductDescription());
        item.setTotalQuantity(record.getTotalQuantity());
        item.setTotalAmount(record.getTotalAmount());
        item.setTotalCost(record.getTotalCost());
        return item;
    }

    // Getters and setters
    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public String getCustomerRating() {
        return customerRating;
    }

    public void setCustomerRating(String customerRating) {
        this.customerRating = customerRating;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(BigDecimal totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        // Store original on first modification
        if (originalAmount == null && this.totalAmount != null) {
            originalAmount = this.totalAmount;
        }
        this.totalAmount = totalAmount;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public boolean isAmountModified() {
        return amountModified;
    }

    public void setAmountModified(boolean amountModified) {
        this.amountModified = amountModified;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    // Calculated fields (formatted for display)
    public String getQuantityFormatted() {
        return totalQuantity != null ? String.format("%.2f", totalQuantity) : "0.00";
    }

    public String getAmountFormatted() {
        return totalAmount != null ? String.format("$%.2f", totalAmount) : "$0.00";
    }

    public String getCostFormatted() {
        return totalCost != null ? String.format("$%.2f", totalCost) : "$0.00";
    }

    public String getUnitSellPrice() {
        if (totalAmount == null || totalQuantity == null || totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return "N/A";
        }
        BigDecimal unitPrice = totalAmount.divide(totalQuantity, 2, RoundingMode.HALF_UP);
        return String.format("$%.2f", unitPrice);
    }

    public String getUnitCostPrice() {
        if (totalCost == null || totalQuantity == null || totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return "N/A";
        }
        BigDecimal unitCost = totalCost.divide(totalQuantity, 2, RoundingMode.HALF_UP);
        return String.format("$%.2f", unitCost);
    }

    public String getOriginalUnitSellPrice() {
        BigDecimal origAmount = getOriginalAmount();
        if (origAmount == null || totalQuantity == null || totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return "N/A";
        }
        BigDecimal unitPrice = origAmount.divide(totalQuantity, 2, RoundingMode.HALF_UP);
        return String.format("$%.2f", unitPrice);
    }

    public BigDecimal getGrossProfit() {
        if (totalAmount == null || totalCost == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.subtract(totalCost);
    }

    public String getGrossProfitFormatted() {
        return String.format("$%.2f", getGrossProfit());
    }

    // Helper method for grouping key
    public String getGroupingKey() {
        return customerCode + "|" + productCode;
    }

    // Getters and setters for new fields

    public String getPrimaryGroup() {
        return primaryGroup;
    }

    public void setPrimaryGroup(String primaryGroup) {
        this.primaryGroup = primaryGroup;
    }

    public BigDecimal getLastCost() {
        return lastCost;
    }

    public void setLastCost(BigDecimal lastCost) {
        this.lastCost = lastCost;
    }

    public BigDecimal getLastUnitSellPrice() {
        return lastUnitSellPrice;
    }

    public void setLastUnitSellPrice(BigDecimal lastUnitSellPrice) {
        this.lastUnitSellPrice = lastUnitSellPrice;
    }

    public BigDecimal getLastAmount() {
        return lastAmount;
    }

    public void setLastAmount(BigDecimal lastAmount) {
        this.lastAmount = lastAmount;
    }

    public BigDecimal getLastGrossProfit() {
        return lastGrossProfit;
    }

    public void setLastGrossProfit(BigDecimal lastGrossProfit) {
        this.lastGrossProfit = lastGrossProfit;
    }

    public BigDecimal getIncomingCost() {
        return incomingCost;
    }

    public void setIncomingCost(BigDecimal incomingCost) {
        this.incomingCost = incomingCost;
    }

    public BigDecimal getNewUnitSellPrice() {
        return newUnitSellPrice;
    }

    public void setNewUnitSellPrice(BigDecimal newUnitSellPrice) {
        this.newUnitSellPrice = newUnitSellPrice;
    }

    public BigDecimal getNewAmount() {
        return newAmount;
    }

    public void setNewAmount(BigDecimal newAmount) {
        this.newAmount = newAmount;
    }

    public BigDecimal getNewGrossProfit() {
        return newGrossProfit;
    }

    public void setNewGrossProfit(BigDecimal newGrossProfit) {
        this.newGrossProfit = newGrossProfit;
    }

    public boolean isManualOverride() {
        return manualOverride;
    }

    public void setManualOverride(boolean manualOverride) {
        this.manualOverride = manualOverride;
    }

    // Multi-rule support getters and setters

    /**
     * Get all applied pricing rules in the order they were applied.
     *
     * @return List of applied rules (never null, may be empty)
     */
    public List<PricingRule> getAppliedRules() {
        return appliedRules != null ? appliedRules : List.of();
    }

    /**
     * Set multiple applied pricing rules.
     *
     * @param appliedRules List of pricing rules to set
     */
    public void setAppliedRules(List<PricingRule> appliedRules) {
        this.appliedRules = appliedRules;
    }

    /**
     * Get intermediate calculation results showing price after each rule.
     *
     * @return List of intermediate prices (never null, may be empty)
     */
    public List<BigDecimal> getIntermediateResults() {
        return intermediateResults != null ? intermediateResults : List.of();
    }

    /**
     * Set intermediate calculation results.
     *
     * @param intermediateResults List of prices after each rule application
     */
    public void setIntermediateResults(List<BigDecimal> intermediateResults) {
        this.intermediateResults = intermediateResults;
    }

    /**
     * Get immutable rule application snapshots (for audit trail).
     *
     * @return List of rule snapshots, or empty list if none
     */
    public List<com.meatrics.pricing.session.AppliedRuleSnapshot> getAppliedRuleSnapshots() {
        return appliedRuleSnapshots != null ? appliedRuleSnapshots : List.of();
    }

    /**
     * Set immutable rule application snapshots.
     *
     * @param appliedRuleSnapshots List of rule snapshots
     */
    public void setAppliedRuleSnapshots(List<com.meatrics.pricing.session.AppliedRuleSnapshot> appliedRuleSnapshots) {
        this.appliedRuleSnapshots = appliedRuleSnapshots;
    }
}
