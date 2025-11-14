package com.meatrics.pricing.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Data Transfer Object for Cost Report
 * Shows imported line items where the line item cost price is lower than the standard cost
 */
public class CostReportDTO {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private String productCode;
    private String productDescription;
    private String customerName;
    private String invoiceNumber;
    private LocalDate transactionDate;
    private BigDecimal quantity;
    private BigDecimal cost;
    private BigDecimal stdcost;
    private BigDecimal lineItemCostPrice; // calculated: cost/quantity
    private BigDecimal difference; // calculated: stdcost - lineItemCostPrice

    public CostReportDTO() {
    }

    public CostReportDTO(String productCode, String productDescription, String customerName,
                        String invoiceNumber, LocalDate transactionDate, BigDecimal quantity,
                        BigDecimal cost, BigDecimal stdcost) {
        this.productCode = productCode;
        this.productDescription = productDescription;
        this.customerName = customerName;
        this.invoiceNumber = invoiceNumber;
        this.transactionDate = transactionDate;
        this.quantity = quantity;
        this.cost = cost;
        this.stdcost = stdcost;

        // Calculate derived fields
        this.lineItemCostPrice = calculateLineItemCostPrice(cost, quantity);
        this.difference = calculateDifference(stdcost, this.lineItemCostPrice);
    }

    // Getters and setters

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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
        // Recalculate derived fields when quantity changes
        this.lineItemCostPrice = calculateLineItemCostPrice(this.cost, quantity);
        this.difference = calculateDifference(this.stdcost, this.lineItemCostPrice);
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
        // Recalculate derived fields when cost changes
        this.lineItemCostPrice = calculateLineItemCostPrice(cost, this.quantity);
        this.difference = calculateDifference(this.stdcost, this.lineItemCostPrice);
    }

    public BigDecimal getStdcost() {
        return stdcost;
    }

    public void setStdcost(BigDecimal stdcost) {
        this.stdcost = stdcost;
        // Recalculate difference when stdcost changes
        this.difference = calculateDifference(stdcost, this.lineItemCostPrice);
    }

    public BigDecimal getLineItemCostPrice() {
        return lineItemCostPrice;
    }

    public void setLineItemCostPrice(BigDecimal lineItemCostPrice) {
        this.lineItemCostPrice = lineItemCostPrice;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public void setDifference(BigDecimal difference) {
        this.difference = difference;
    }

    // Formatted getters for display

    /**
     * Get formatted transaction date as dd/MM/yyyy
     */
    public String getFormattedTransactionDate() {
        if (transactionDate == null) {
            return "";
        }
        return transactionDate.format(DATE_FORMATTER);
    }

    /**
     * Get formatted quantity
     */
    public String getFormattedQuantity() {
        if (quantity == null) {
            return "0.00";
        }
        return String.format("%,.2f", quantity);
    }

    /**
     * Get formatted cost with currency symbol
     */
    public String getFormattedCost() {
        if (cost == null) {
            return "$0.00";
        }
        return String.format("$%,.2f", cost);
    }

    /**
     * Get formatted standard cost with currency symbol
     */
    public String getFormattedStdcost() {
        if (stdcost == null) {
            return "$0.00";
        }
        return String.format("$%,.2f", stdcost);
    }

    /**
     * Get formatted line item cost price with currency symbol
     */
    public String getFormattedLineItemCostPrice() {
        if (lineItemCostPrice == null) {
            return "$0.00";
        }
        return String.format("$%,.2f", lineItemCostPrice);
    }

    /**
     * Get formatted difference with currency symbol
     */
    public String getFormattedDifference() {
        if (difference == null) {
            return "$0.00";
        }
        return String.format("$%,.2f", difference);
    }

    // Calculation methods

    /**
     * Calculate line item cost price (cost / quantity)
     * Handles division by zero by returning null
     *
     * @param cost The total cost
     * @param quantity The quantity
     * @return The line item cost price, or null if quantity is zero or null
     */
    private static BigDecimal calculateLineItemCostPrice(BigDecimal cost, BigDecimal quantity) {
        if (cost == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return cost.divide(quantity, 4, RoundingMode.HALF_UP)
                   .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate difference (stdcost - lineItemCostPrice)
     *
     * @param stdcost The standard cost
     * @param lineItemCostPrice The calculated line item cost price
     * @return The difference, or null if either parameter is null
     */
    private static BigDecimal calculateDifference(BigDecimal stdcost, BigDecimal lineItemCostPrice) {
        if (stdcost == null || lineItemCostPrice == null) {
            return null;
        }
        return stdcost.subtract(lineItemCostPrice);
    }
}
