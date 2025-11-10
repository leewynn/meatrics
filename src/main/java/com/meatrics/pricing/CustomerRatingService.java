package com.meatrics.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating customer ratings using different algorithms
 */
@Service
public class CustomerRatingService {

    private static final Logger log = LoggerFactory.getLogger(CustomerRatingService.class);

    private final ImportedLineItemRepository importedLineItemRepository;
    private final CustomerRepository customerRepository;

    public CustomerRatingService(ImportedLineItemRepository importedLineItemRepository,
                                 CustomerRepository customerRepository) {
        this.importedLineItemRepository = importedLineItemRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * Calculate all three rating algorithms for a customer
     */
    public CustomerRatingResult calculateRatings(String customerCode) {
        // Get all line items for this customer
        List<ImportedLineItem> items = importedLineItemRepository.findAll().stream()
                .filter(item -> customerCode.equals(item.getCustomerCode()))
                .toList();

        if (items.isEmpty()) {
            return new CustomerRatingResult(0, 0, 0);
        }

        // Calculate totals for this customer
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (ImportedLineItem item : items) {
            if (item.getAmount() != null) {
                totalAmount = totalAmount.add(item.getAmount());
            }
            if (item.getCost() != null) {
                totalCost = totalCost.add(item.getCost());
            }
        }

        BigDecimal grossProfit = totalAmount.subtract(totalCost);
        BigDecimal gpPercentage = BigDecimal.ZERO;
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            gpPercentage = grossProfit.divide(totalAmount, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        // Calculate the three ratings
        int originalRating = calculateOriginalRating(totalAmount, gpPercentage);
        int modifiedRating = calculateModifiedRating(totalAmount, gpPercentage);
        int claudeRating = calculateClaudeRating(customerCode, grossProfit, totalAmount);

        return new CustomerRatingResult(originalRating, modifiedRating, claudeRating);
    }

    /**
     * Original user formula: sqrt((amount / 1000 * GP%) * 100)
     */
    private int calculateOriginalRating(BigDecimal amount, BigDecimal gpPercentage) {
        // amount / 1000
        BigDecimal amountPart = amount.divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);

        // (amount / 1000) * GP%
        BigDecimal product = amountPart.multiply(gpPercentage);

        // sqrt(product * 100)
        double result = Math.sqrt(product.multiply(new BigDecimal("100")).doubleValue());

        return (int) Math.round(result);
    }

    /**
     * Modified formula (additive instead of multiplicative): (amount / 1000) + (GP% * 10)
     * This addresses the zero-multiplication problem
     */
    private int calculateModifiedRating(BigDecimal amount, BigDecimal gpPercentage) {
        // amount / 1000
        BigDecimal amountPart = amount.divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);

        // GP% * 10
        BigDecimal gpPart = gpPercentage.multiply(new BigDecimal("10"));

        // Sum them
        BigDecimal result = amountPart.add(gpPart);

        return result.intValue();
    }

    /**
     * Option 4: Modified Current Formula (Quick Fix)
     * Score = sqrt(Revenue_Score + Margin_Score)
     * Where:
     *   Revenue_Score = (amount / 1000) × 50
     *   Margin_Score = GP% × 50
     */
    private int calculateClaudeRating(String customerCode, BigDecimal grossProfit, BigDecimal revenue) {
        // Calculate GP%
        BigDecimal gpPercentage = BigDecimal.ZERO;
        if (revenue.compareTo(BigDecimal.ZERO) > 0) {
            gpPercentage = grossProfit.divide(revenue, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        // Revenue_Score = (amount / 1000) × 50
        BigDecimal revenueScore = revenue.divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("50"));

        // Margin_Score = GP% × 50
        BigDecimal marginScore = gpPercentage.multiply(new BigDecimal("50"));

        // Score = sqrt(Revenue_Score + Margin_Score)
        BigDecimal sum = revenueScore.add(marginScore);
        double result = Math.sqrt(sum.doubleValue());

        return (int) Math.round(result);
    }

    /**
     * Result holder for the three rating calculations
     */
    public static class CustomerRatingResult {
        private final int originalRating;
        private final int modifiedRating;
        private final int claudeRating;

        public CustomerRatingResult(int originalRating, int modifiedRating, int claudeRating) {
            this.originalRating = originalRating;
            this.modifiedRating = modifiedRating;
            this.claudeRating = claudeRating;
        }

        public int getOriginalRating() {
            return originalRating;
        }

        public int getModifiedRating() {
            return modifiedRating;
        }

        public int getClaudeRating() {
            return claudeRating;
        }

        public String getFormattedRatings() {
            return String.format("original: %d | modified: %d | claude: %d",
                    originalRating, modifiedRating, claudeRating);
        }
    }

    /**
     * Calculate and save ratings for all customers (runs in background)
     */
    @Async
    public void recalculateAndSaveAllCustomerRatings() {
        recalculateAllRatingsInternal();
    }

    /**
     * Calculate and save ratings for all customers (synchronous version for when needed)
     */
    public int recalculateAndSaveAllCustomerRatingsSync() {
        return recalculateAllRatingsInternal();
    }

    /**
     * Internal method containing the shared logic for rating calculation.
     * Used by both async and sync public methods.
     *
     * @return The number of customers successfully updated
     */
    private int recalculateAllRatingsInternal() {
        log.info("Starting customer rating recalculation...");
        long startTime = System.currentTimeMillis();

        List<Customer> allCustomers = customerRepository.findAll();
        int count = 0;

        for (Customer customer : allCustomers) {
            try {
                CustomerRatingResult ratings = calculateRatings(customer.getCustomerCode());
                String formattedRatings = ratings.getFormattedRatings();
                customer.setCustomerRating(formattedRatings);
                customerRepository.save(customer);
                count++;
            } catch (Exception e) {
                log.warn("Failed to calculate rating for customer {}: {}",
                        customer.getCustomerCode(), e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Customer rating recalculation complete. Updated {} of {} customers in {}ms",
                count, allCustomers.size(), duration);

        return count;
    }
}
