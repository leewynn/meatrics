package com.meatrics.pricing;

import java.math.BigDecimal;

/**
 * Pricing session line item entity representing the pricing_session_line_items table
 * Line items within a pricing session, grouped by customer and product
 */
public class PricingSessionLineItem {
    private Long id;
    private Long sessionId;
    private String customerCode;
    private String customerName;
    private String customerRating;
    private String productCode;
    private String productDescription;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private BigDecimal originalAmount;
    private BigDecimal totalCost;
    private Boolean amountModified;

    // Historical pricing data fields (needed for MAINTAIN_GP_PERCENT rule)
    private BigDecimal lastCost;          // Historical average unit cost
    private BigDecimal lastUnitSellPrice; // Historical average unit sell price
    private BigDecimal incomingCost;      // New incoming cost from product_costs
    private String primaryGroup;          // Product category for rule matching

    public PricingSessionLineItem() {
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

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

    public String getCustomerRating() {
        return customerRating;
    }

    public void setCustomerRating(String customerRating) {
        this.customerRating = customerRating;
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
        this.totalAmount = totalAmount;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public Boolean getAmountModified() {
        return amountModified;
    }

    public void setAmountModified(Boolean amountModified) {
        this.amountModified = amountModified;
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

    public BigDecimal getIncomingCost() {
        return incomingCost;
    }

    public void setIncomingCost(BigDecimal incomingCost) {
        this.incomingCost = incomingCost;
    }

    public String getPrimaryGroup() {
        return primaryGroup;
    }

    public void setPrimaryGroup(String primaryGroup) {
        this.primaryGroup = primaryGroup;
    }
}
