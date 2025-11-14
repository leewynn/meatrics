package com.meatrics.pricing.calculation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * UNIT TESTS: Pricing formulas.
 * Tests the mathematical correctness of pricing calculations.
 * These are fast, isolated tests with no dependencies.
 *
 * Type: Unit/Technical
 * Speed: Fast (milliseconds)
 * Purpose: Verify formulas work correctly
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Pricing Formula Unit Tests")
class PricingFormulaTest {

    @Test
    @DisplayName("COST_PLUS_PERCENT: Should apply markup correctly")
    void costPlusPercent_withPositiveMarkup_shouldIncreasePrice() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal multiplier = new BigDecimal("1.20"); // 20% markup

        // When - Formula: price = cost × multiplier
        BigDecimal price = cost.multiply(multiplier);

        // Then
        assertThat(price).isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    @DisplayName("COST_PLUS_PERCENT: Should apply discount correctly")
    void costPlusPercent_withNegativeMarkup_shouldDecreasePrice() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal multiplier = new BigDecimal("0.90"); // 10% discount

        // When
        BigDecimal price = cost.multiply(multiplier);

        // Then
        assertThat(price).isEqualByComparingTo(new BigDecimal("9.00"));
    }

    @Test
    @DisplayName("COST_PLUS_FIXED: Should add fixed amount")
    void costPlusFixed_shouldAddAmount() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal fixedAmount = new BigDecimal("2.50");

        // When - Formula: price = cost + fixed
        BigDecimal price = cost.add(fixedAmount);

        // Then
        assertThat(price).isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    @DisplayName("FIXED_PRICE: Should use fixed price regardless of cost")
    void fixedPrice_shouldIgnoreCost() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal fixedPrice = new BigDecimal("15.00");

        // When - Fixed price always wins
        BigDecimal price = fixedPrice;

        // Then
        assertThat(price).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(price).isNotEqualByComparingTo(cost);
    }

    @Test
    @DisplayName("MAINTAIN_GP_PERCENT: Should calculate price for target GP%")
    void maintainGP_with25Percent_shouldCalculateCorrectPrice() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal gpPercent = new BigDecimal("0.25"); // 25%

        // When - Formula: price = cost / (1 - GP%)
        BigDecimal divisor = BigDecimal.ONE.subtract(gpPercent); // 1 - 0.25 = 0.75
        BigDecimal price = cost.divide(divisor, 2, RoundingMode.HALF_UP);

        // Then - $10.00 / 0.75 = $13.33
        assertThat(price).isEqualByComparingTo(new BigDecimal("13.33"));

        // Verify reverse calculation (allow for rounding)
        BigDecimal gp = price.subtract(cost); // $3.33
        BigDecimal calculatedGP = gp.divide(price, 4, RoundingMode.HALF_UP);
        // Allow small rounding error due to price rounding to 2 decimals
        assertThat(calculatedGP).isCloseTo(gpPercent, within(new BigDecimal("0.0002")));
    }

    @Test
    @DisplayName("GP% Calculation: Should calculate from price and cost")
    void calculateGPPercent_standardCase_shouldReturnCorrectPercentage() {
        // Given
        BigDecimal price = new BigDecimal("15.00");
        BigDecimal cost = new BigDecimal("10.00");

        // When - Formula: GP% = (price - cost) / price
        BigDecimal gp = price.subtract(cost); // $5.00
        BigDecimal gpPercent = gp.divide(price, 4, RoundingMode.HALF_UP);

        // Then - $5 / $15 = 0.3333 (33.33%)
        assertThat(gpPercent).isEqualByComparingTo(new BigDecimal("0.3333"));
    }

    @Test
    @DisplayName("Multi-layer pricing: Should apply rules sequentially")
    void multiLayer_shouldApplyRulesInSequence() {
        // Given - Example from documentation
        BigDecimal startingCost = new BigDecimal("10.00");

        // When - Apply rules in sequence (round each step to 2 decimals like currency)
        // Layer 1: Base Price (Cost + 20%)
        BigDecimal afterBase = startingCost.multiply(new BigDecimal("1.20"))
            .setScale(2, RoundingMode.HALF_UP);

        // Layer 2: Customer Adjustment (Volume -10%)
        BigDecimal afterVolume = afterBase.multiply(new BigDecimal("0.90"))
            .setScale(2, RoundingMode.HALF_UP);

        // Layer 3: Customer Adjustment (Loyalty -5%)
        BigDecimal afterLoyalty = afterVolume.multiply(new BigDecimal("0.95"))
            .setScale(2, RoundingMode.HALF_UP);

        // Layer 4: Product Adjustment (Premium +$2)
        BigDecimal afterProduct = afterLoyalty.add(new BigDecimal("2.00"))
            .setScale(2, RoundingMode.HALF_UP);

        // Layer 5: Promotional (Sale -15%)
        BigDecimal finalPrice = afterProduct.multiply(new BigDecimal("0.85"))
            .setScale(2, RoundingMode.HALF_UP);

        // Then - Verify each step
        assertThat(afterBase).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(afterVolume).isEqualByComparingTo(new BigDecimal("10.80"));
        assertThat(afterLoyalty).isEqualByComparingTo(new BigDecimal("10.26"));
        assertThat(afterProduct).isEqualByComparingTo(new BigDecimal("12.26"));
        assertThat(finalPrice).isEqualByComparingTo(new BigDecimal("10.42"));
    }

    @Test
    @DisplayName("Cost Drift: Should calculate percentage change")
    void costDrift_withIncrease_shouldReturnPositivePercentage() {
        // Given
        BigDecimal lastCost = new BigDecimal("10.00");
        BigDecimal newCost = new BigDecimal("12.00");

        // When - Formula: drift = (new - old) / old
        BigDecimal difference = newCost.subtract(lastCost);
        BigDecimal drift = difference.divide(lastCost, 4, RoundingMode.HALF_UP);

        // Then - ($12 - $10) / $10 = 0.20 (20%)
        assertThat(drift).isEqualByComparingTo(new BigDecimal("0.20"));
    }

    @Test
    @DisplayName("Cost Drift: Should handle price decrease")
    void costDrift_withDecrease_shouldReturnNegativePercentage() {
        // Given
        BigDecimal lastCost = new BigDecimal("10.00");
        BigDecimal newCost = new BigDecimal("8.00");

        // When
        BigDecimal difference = newCost.subtract(lastCost);
        BigDecimal drift = difference.divide(lastCost, 4, RoundingMode.HALF_UP);

        // Then - ($8 - $10) / $10 = -0.20 (-20%)
        assertThat(drift).isEqualByComparingTo(new BigDecimal("-0.20"));
    }

    @Test
    @DisplayName("Precision: Should maintain 6 decimal places")
    void precision_shouldMaintainSixDecimals() {
        // Given - Values with 6 decimals
        BigDecimal value1 = new BigDecimal("10.123456");
        BigDecimal value2 = new BigDecimal("1.234567");

        // When
        BigDecimal result = value1.multiply(value2);

        // Then - Should maintain high precision
        assertThat(result.scale()).isGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("Rounding: Should round to 2 decimals for currency")
    void rounding_shouldRoundToTwoDecimalsForCurrency() {
        // Given
        BigDecimal value = new BigDecimal("12.3456789");

        // When
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);

        // Then
        assertThat(rounded).isEqualByComparingTo(new BigDecimal("12.35"));
        assertThat(rounded.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Historical GP: Should calculate GP% from last cycle")
    void historicalGP_fromLastCycle_shouldMaintainSameMargin() {
        // Given - Last cycle data
        BigDecimal lastPrice = new BigDecimal("15.00");
        BigDecimal lastCost = new BigDecimal("10.00");
        BigDecimal newCost = new BigDecimal("12.00");

        // When - Calculate historical GP% and apply to new cost
        BigDecimal lastGP = lastPrice.subtract(lastCost);
        BigDecimal historicalGPPercent = lastGP.divide(lastPrice, 4, RoundingMode.HALF_UP);

        // Apply same GP% to new cost
        BigDecimal divisor = BigDecimal.ONE.subtract(historicalGPPercent);
        BigDecimal newPrice = newCost.divide(divisor, 2, RoundingMode.HALF_UP);

        // Then
        // Historical GP% = ($15 - $10) / $15 = 0.3333 (33.33%)
        assertThat(historicalGPPercent).isEqualByComparingTo(new BigDecimal("0.3333"));

        // New price = $12 / (1 - 0.3333) = $12 / 0.6667 = $18.00
        assertThat(newPrice).isEqualByComparingTo(new BigDecimal("18.00"));

        // Verify GP% is maintained
        BigDecimal newGP = newPrice.subtract(newCost);
        BigDecimal newGPPercent = newGP.divide(newPrice, 4, RoundingMode.HALF_UP);
        assertThat(newGPPercent).isEqualByComparingTo(historicalGPPercent);
    }
}
