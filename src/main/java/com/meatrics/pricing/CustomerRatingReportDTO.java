package com.meatrics.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Data Transfer Object for Customer Rating Report
 * Contains aggregated customer data for a specific date range
 */
public class CustomerRatingReportDTO {

    private String customerCode;
    private String customerName;
    private BigDecimal totalCost;
    private BigDecimal totalAmount;
    private BigDecimal grossProfitPercentage;
    private String originalRating;
    private String modifiedRating;
    private String claudeRating;

    public CustomerRatingReportDTO() {
    }

    public CustomerRatingReportDTO(String customerCode, String customerName,
                                  BigDecimal totalCost, BigDecimal totalAmount,
                                  BigDecimal grossProfitPercentage) {
        this.customerCode = customerCode;
        this.customerName = customerName;
        this.totalCost = totalCost;
        this.totalAmount = totalAmount;
        this.grossProfitPercentage = grossProfitPercentage;
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

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getGrossProfitPercentage() {
        return grossProfitPercentage;
    }

    public void setGrossProfitPercentage(BigDecimal grossProfitPercentage) {
        this.grossProfitPercentage = grossProfitPercentage;
    }

    public String getOriginalRating() {
        return originalRating;
    }

    public void setOriginalRating(String originalRating) {
        this.originalRating = originalRating;
    }

    public String getModifiedRating() {
        return modifiedRating;
    }

    public void setModifiedRating(String modifiedRating) {
        this.modifiedRating = modifiedRating;
    }

    public String getClaudeRating() {
        return claudeRating;
    }

    public void setClaudeRating(String claudeRating) {
        this.claudeRating = claudeRating;
    }

    // Formatted getters for display in UI

    /**
     * Get customer display name (combined code and name)
     */
    public String getCustomerDisplay() {
        if (customerCode != null && customerName != null) {
            return customerCode + " - " + customerName;
        } else if (customerName != null) {
            return customerName;
        } else if (customerCode != null) {
            return customerCode;
        }
        return "Unknown";
    }

    /**
     * Get formatted cost with currency symbol
     */
    public String getFormattedCost() {
        if (totalCost == null) {
            return "$0.00";
        }
        return String.format("$%,.2f", totalCost);
    }

    /**
     * Get formatted amount with currency symbol
     */
    public String getFormattedAmount() {
        if (totalAmount == null) {
            return "$0.00";
        }
        return String.format("$%,.2f", totalAmount);
    }

    /**
     * Get formatted GP percentage
     */
    public String getFormattedGPPercentage() {
        if (grossProfitPercentage == null) {
            return "0.00%";
        }
        return String.format("%.2f%%", grossProfitPercentage);
    }

    /**
     * Calculate gross profit amount
     */
    public BigDecimal getGrossProfitAmount() {
        if (totalAmount == null || totalCost == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.subtract(totalCost);
    }

    /**
     * Get formatted gross profit amount
     */
    public String getFormattedGrossProfitAmount() {
        return String.format("$%,.2f", getGrossProfitAmount());
    }

    /**
     * Safe division for calculating GP percentage
     */
    public static BigDecimal calculateGPPercentage(BigDecimal totalAmount, BigDecimal totalCost) {
        if (totalAmount == null || totalCost == null || totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal grossProfit = totalAmount.subtract(totalCost);
        return grossProfit.divide(totalAmount, 4, RoundingMode.HALF_UP)
                          .multiply(new BigDecimal("100"))
                          .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Parse rating string into separate rating fields
     * Expected format: "original: 23 | modified: 234 | claude: 223"
     *
     * @param dto The DTO to populate with parsed rating values
     * @param ratingString The rating string to parse
     */
    public static void parseRating(CustomerRatingReportDTO dto, String ratingString) {
        if (ratingString == null || ratingString.trim().isEmpty()) {
            dto.setOriginalRating("");
            dto.setModifiedRating("");
            dto.setClaudeRating("");
            return;
        }

        // Parse format: "original: 23 | modified: 234 | claude: 223"
        String[] parts = ratingString.split("\\|");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("original:")) {
                dto.setOriginalRating(trimmed.substring("original:".length()).trim());
            } else if (trimmed.startsWith("modified:")) {
                dto.setModifiedRating(trimmed.substring("modified:".length()).trim());
            } else if (trimmed.startsWith("claude:")) {
                dto.setClaudeRating(trimmed.substring("claude:".length()).trim());
            }
        }
    }
}
