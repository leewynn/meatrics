package com.meatrics.pricing.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Imported line item entity representing the imported_line_items staging table
 */
public class ImportedLineItem {
    private Long lineId;
    private Long importId;
    private String filename;

    // Customer fields
    private String customerCode;
    private String customerName;

    // Invoice fields
    private String invoiceNumber;
    private LocalDate transactionDate;

    // Product fields
    private String productCode;
    private String productDescription;

    // Line item fields
    private BigDecimal quantity;
    private BigDecimal amount;
    private BigDecimal cost;
    private String ref1;
    private String ref2;
    private String ref3;
    private BigDecimal outstandingAmount;

    // Metadata
    private LocalDateTime importDate;

    // Transient fields to track modifications
    private transient boolean amountModified = false;
    private transient BigDecimal originalAmount = null;

    public ImportedLineItem() {
    }

    // Getters and setters
    public Long getLineId() {
        return lineId;
    }

    public void setLineId(Long lineId) {
        this.lineId = lineId;
    }

    public Long getImportId() {
        return importId;
    }

    public void setImportId(Long importId) {
        this.importId = importId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
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

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        // Store original amount on first modification
        if (originalAmount == null && this.amount != null) {
            originalAmount = this.amount;
        }
        this.amount = amount;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount != null ? originalAmount : amount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public String getRef1() {
        return ref1;
    }

    public void setRef1(String ref1) {
        this.ref1 = ref1;
    }

    public String getRef2() {
        return ref2;
    }

    public void setRef2(String ref2) {
        this.ref2 = ref2;
    }

    public String getRef3() {
        return ref3;
    }

    public void setRef3(String ref3) {
        this.ref3 = ref3;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public void setOutstandingAmount(BigDecimal outstandingAmount) {
        this.outstandingAmount = outstandingAmount;
    }

    public LocalDateTime getImportDate() {
        return importDate;
    }

    public void setImportDate(LocalDateTime importDate) {
        this.importDate = importDate;
    }

    public boolean isAmountModified() {
        return amountModified;
    }

    public void setAmountModified(boolean amountModified) {
        this.amountModified = amountModified;
    }

    /**
     * Get formatted cost as a string
     * Format: $XX.XX
     */
    public String getCostFormatted() {
        if (cost == null) {
            return "$0.00";
        }
        return String.format("$%.2f", cost);
    }

    /**
     * Get formatted amount as a string
     * Format: $XX.XX
     */
    public String getAmountFormatted() {
        if (amount == null) {
            return "$0.00";
        }
        return String.format("$%.2f", amount);
    }

    /**
     * Calculate unit sell price (amount / quantity)
     * Format: $XX.XX
     */
    public String getUnitSellPrice() {
        if (amount == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return "N/A";
        }
        BigDecimal unitPrice = amount.divide(quantity, 2, java.math.RoundingMode.HALF_UP);
        return String.format("$%.2f", unitPrice);
    }

    /**
     * Calculate original unit sell price (original amount / quantity)
     * Format: $XX.XX
     */
    public String getOriginalUnitSellPrice() {
        BigDecimal origAmount = getOriginalAmount();
        if (origAmount == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return "N/A";
        }
        BigDecimal unitPrice = origAmount.divide(quantity, 2, java.math.RoundingMode.HALF_UP);
        return String.format("$%.2f", unitPrice);
    }

    /**
     * Calculate unit cost price (cost / quantity)
     * Format: $XX.XX
     */
    public String getUnitCostPrice() {
        if (cost == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return "N/A";
        }
        BigDecimal unitCost = cost.divide(quantity, 2, java.math.RoundingMode.HALF_UP);
        return String.format("$%.2f", unitCost);
    }

    /**
     * Calculate and return formatted gross profit with percentage
     * Format: $XX.XX (YY.YY%)
     */
    public String getGrossProfitFormatted() {
        if (amount == null || cost == null) {
            return "N/A";
        }

        // Calculate gross profit: Amount - Cost
        BigDecimal grossProfit = amount.subtract(cost);

        // Calculate gross profit percentage: (Amount - Cost) / Amount * 100
        BigDecimal grossProfitPercentage;
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            grossProfitPercentage = BigDecimal.ZERO;
        } else {
            grossProfitPercentage = grossProfit.divide(amount, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        return String.format("$%.2f (%.2f%%)", grossProfit, grossProfitPercentage);
    }
}
