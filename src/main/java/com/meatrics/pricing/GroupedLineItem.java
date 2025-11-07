package com.meatrics.pricing;

import com.meatrics.generated.tables.records.VGroupedLineItemsRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    // Customer rating (populated from session when loading saved session)
    private String customerRating;

    // Aggregated values
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private BigDecimal totalCost;

    // Transient fields for UI state (modifications)
    private transient boolean amountModified = false;
    private transient BigDecimal originalAmount = null;

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
}
