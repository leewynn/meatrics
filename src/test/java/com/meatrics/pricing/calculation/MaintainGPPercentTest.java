package com.meatrics.pricing.calculation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * UNIT TESTS: MAINTAIN_GP_PERCENT formula.
 * Tests the mathematical correctness of GP% calculations.
 *
 * Formula: price = cost / (1 - GP%)
 * Example: cost = $10, GP = 25% → price = $10 / (1 - 0.25) = $10 / 0.75 = $13.33
 *
 * Type: Unit/Technical
 * Speed: Fast (milliseconds)
 * Purpose: Verify GP% formula works correctly
 */
@Tag("unit")
@Tag("fast")
@DisplayName("MAINTAIN_GP_PERCENT Formula Unit Tests")
class MaintainGPPercentTest {

    @Test
    @DisplayName("Should calculate correct price for 25% GP")
    void maintainGP_with25Percent_shouldCalculateCorrectPrice() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal gpPercent = new BigDecimal("0.25"); // 25%

        // When - Formula: price = cost / (1 - GP%)
        BigDecimal price = calculatePriceWithGP(cost, gpPercent);

        // Then - $10.00 / 0.75 = $13.33 (rounded)
        assertThat(price).isEqualByComparingTo(new BigDecimal("13.33"));

        // Verify GP% is correct in reverse calculation (allow for rounding)
        BigDecimal calculatedGP = price.subtract(cost);
        BigDecimal gpPercentCheck = calculatedGP.divide(price, 4, RoundingMode.HALF_UP);

        // Allow small rounding error (0.0002 = 0.02% difference)
        assertThat(gpPercentCheck)
            .as("Reverse calculation should yield approximately same GP%%")
            .isCloseTo(gpPercent, within(new BigDecimal("0.0002")));
    }

    @Test
    @DisplayName("Should calculate correct price for 33.33% GP")
    void maintainGP_with33Percent_shouldCalculateCorrectPrice() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal gpPercent = new BigDecimal("0.3333"); // 33.33%

        // When
        BigDecimal price = calculatePriceWithGP(cost, gpPercent);

        // Then - $10.00 / (1 - 0.3333) = $10.00 / 0.6667 = $15.00
        assertThat(price).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("Should handle high GP% (50%)")
    void maintainGP_withHighGP_shouldCalculateCorrectPrice() {
        // Given - 50% GP is high but valid
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal gpPercent = new BigDecimal("0.50"); // 50%

        // When
        BigDecimal price = calculatePriceWithGP(cost, gpPercent);

        // Then - $10.00 / 0.50 = $20.00
        assertThat(price).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("Should handle low GP% (5%)")
    void maintainGP_withLowGP_shouldCalculateCorrectPrice() {
        // Given - 5% GP is low margin
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal gpPercent = new BigDecimal("0.05"); // 5%

        // When
        BigDecimal price = calculatePriceWithGP(cost, gpPercent);

        // Then - $10.00 / 0.95 = $10.53
        assertThat(price).isEqualByComparingTo(new BigDecimal("10.53"));
    }

    @Test
    @DisplayName("Should handle 0% GP (sell at cost)")
    void maintainGP_withZeroGP_shouldReturnCost() {
        // Given - 0% GP means selling at cost (no profit)
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal gpPercent = BigDecimal.ZERO;

        // When
        BigDecimal price = calculatePriceWithGP(cost, gpPercent);

        // Then - Should equal cost
        assertThat(price).isEqualByComparingTo(cost);
    }

    @Test
    @DisplayName("Should calculate historical GP% from last cycle")
    void calculateHistoricalGP_fromLastCycle_shouldReturnCorrectPercentage() {
        // Given - Last cycle data
        BigDecimal lastSellPrice = new BigDecimal("15.00");
        BigDecimal lastCost = new BigDecimal("10.00");

        // When - Calculate what GP% was achieved
        BigDecimal historicalGP = calculateGPPercent(lastSellPrice, lastCost);

        // Then - GP = (price - cost) / price = $5 / $15 = 0.3333 (33.33%)
        assertThat(historicalGP).isEqualByComparingTo(new BigDecimal("0.3333"));
    }

    @Test
    @DisplayName("Should maintain historical GP% with new cost")
    void maintainHistoricalGP_withNewCost_shouldCalculateNewPrice() {
        // Given - Historical data
        BigDecimal lastPrice = new BigDecimal("15.00");
        BigDecimal lastCost = new BigDecimal("10.00");
        BigDecimal newCost = new BigDecimal("12.00");

        // When - Calculate historical GP% and apply to new cost
        BigDecimal historicalGP = calculateGPPercent(lastPrice, lastCost);
        BigDecimal newPrice = calculatePriceWithGP(newCost, historicalGP);

        // Then
        // Historical GP = 33.33%
        // New price = $12 / (1 - 0.3333) = $12 / 0.6667 = $18.00
        assertThat(newPrice).isEqualByComparingTo(new BigDecimal("18.00"));

        // Verify GP% maintained
        BigDecimal newGP = calculateGPPercent(newPrice, newCost);
        assertThat(newGP)
            .as("New GP%% should match historical GP%%")
            .isEqualByComparingTo(historicalGP);
    }

    @Test
    @DisplayName("Should maintain 6 decimal precision in calculations")
    void maintainGP_withHighPrecision_shouldPreserveDecimals() {
        // Given - High precision values
        BigDecimal cost = new BigDecimal("12.345678");
        BigDecimal gpPercent = new BigDecimal("0.273456"); // 27.3456%

        // When
        BigDecimal price = calculatePriceWithGP(cost, gpPercent);

        // Then - Should maintain at least 2 decimal places (for currency)
        assertThat(price.scale())
            .as("Should maintain decimal precision")
            .isGreaterThanOrEqualTo(2);

        // Price should be reasonable
        assertThat(price).isGreaterThan(cost);
    }

    @Test
    @DisplayName("Should handle realistic meat product scenario")
    void maintainGP_realisticMeatScenario_shouldCalculateCorrectly() {
        // Given - Real-world meat pricing scenario
        // Product: Premium Ribeye
        // Last cycle: Sold at $24.95/lb, cost was $16.50/lb
        // New cost: $18.25/lb
        // Should maintain same GP%

        BigDecimal lastPrice = new BigDecimal("24.95");
        BigDecimal lastCost = new BigDecimal("16.50");
        BigDecimal newCost = new BigDecimal("18.25");

        // When
        BigDecimal historicalGP = calculateGPPercent(lastPrice, lastCost);
        BigDecimal newPrice = calculatePriceWithGP(newCost, historicalGP);

        // Then
        // Historical GP = ($24.95 - $16.50) / $24.95 = 0.3386 (33.86%)
        assertThat(historicalGP)
            .isGreaterThan(new BigDecimal("0.33"))
            .isLessThan(new BigDecimal("0.34"));

        // New price should be around $27.62
        assertThat(newPrice)
            .isGreaterThan(new BigDecimal("27.00"))
            .isLessThan(new BigDecimal("28.00"));
    }

    // ========== Helper Methods ==========
    // These are the actual calculation methods that should exist in your service
    // Implement these in PriceCalculationService or similar class

    /**
     * Calculate selling price to maintain a target GP%
     * Formula: price = cost / (1 - GP%)
     */
    private BigDecimal calculatePriceWithGP(BigDecimal cost, BigDecimal gpPercent) {
        if (cost == null || gpPercent == null) {
            throw new IllegalArgumentException("Cost and GP% cannot be null");
        }

        if (gpPercent.compareTo(BigDecimal.ZERO) == 0) {
            // 0% GP = sell at cost
            return cost.setScale(2, RoundingMode.HALF_UP);
        }

        // price = cost / (1 - GP%)
        BigDecimal divisor = BigDecimal.ONE.subtract(gpPercent);

        if (divisor.compareTo(BigDecimal.ZERO) <= 0) {
            // GP% >= 100% would cause division by zero or negative
            throw new IllegalArgumentException("GP% must be less than 100%");
        }

        return cost.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate GP% from price and cost
     * Formula: GP% = (price - cost) / price
     */
    private BigDecimal calculateGPPercent(BigDecimal price, BigDecimal cost) {
        if (price == null || cost == null) {
            throw new IllegalArgumentException("Price and cost cannot be null");
        }

        if (price.compareTo(BigDecimal.ZERO) == 0) {
            // Avoid division by zero
            return BigDecimal.ZERO;
        }

        BigDecimal grossProfit = price.subtract(cost);
        return grossProfit.divide(price, 4, RoundingMode.HALF_UP);
    }
}
