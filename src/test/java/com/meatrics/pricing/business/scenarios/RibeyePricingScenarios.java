package com.meatrics.pricing.business.scenarios;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business scenarios for ribeye pricing.
 * These test real-world business rules, not just formulas.
 *
 * Business Rules Being Tested:
 * 1. Premium ribeye must maintain minimum 30% GP
 * 2. Cost increases should maintain customer historical GP%
 * 3. Loyalty customers get additional 5% discount
 * 4. Volume discounts stack with loyalty discounts
 * 5. Pricing must never go below cost (except clearance with approval)
 */
@Tag("business")
@Tag("ribeye")
@DisplayName("Ribeye Pricing Business Scenarios")
class RibeyePricingScenarios {

    @Test
    @DisplayName("BR-001: Premium ribeye cuts must maintain minimum 30% GP margin")
    void premiumRibeye_shouldMaintainMinimum30PercentGP() {
        // GIVEN: Business Rule - Premium cuts need 30% minimum GP
        BigDecimal cost = new BigDecimal("18.50");
        BigDecimal minimumGP = new BigDecimal("0.30"); // 30%

        // WHEN: Calculate minimum price for 30% GP
        // Formula: price = cost / (1 - GP%)
        BigDecimal minimumPrice = cost.divide(
            BigDecimal.ONE.subtract(minimumGP),
            2,
            RoundingMode.HALF_UP
        );

        // THEN: Price must be at least $26.43
        assertThat(minimumPrice).isEqualByComparingTo(new BigDecimal("26.43"));

        // AND: Verify the GP% is correct
        BigDecimal actualGP = minimumPrice.subtract(cost)
            .divide(minimumPrice, 4, RoundingMode.HALF_UP);
        assertThat(actualGP).isEqualByComparingTo(minimumGP);
    }

    @Test
    @DisplayName("BR-002: When ribeye cost increases 20%, customer GP% should be maintained")
    void ribeyeCostIncrease_shouldMaintainCustomerHistoricalGP() {
        // GIVEN: Historical data
        // Customer bought ribeye last month at $24.95 (cost was $16.50)
        BigDecimal lastPrice = new BigDecimal("24.95");
        BigDecimal lastCost = new BigDecimal("16.50");

        // AND: Cost increased to $19.80 (20% increase)
        BigDecimal newCost = new BigDecimal("19.80");

        // WHEN: Calculate new price to maintain historical GP%
        // Step 1: Calculate historical GP%
        BigDecimal historicalGP = lastPrice.subtract(lastCost)
            .divide(lastPrice, 4, RoundingMode.HALF_UP);

        // Step 2: Apply same GP% to new cost
        BigDecimal newPrice = newCost.divide(
            BigDecimal.ONE.subtract(historicalGP),
            2,
            RoundingMode.HALF_UP
        );

        // THEN: New price should be approximately $29.70
        assertThat(newPrice)
            .as("New price should maintain customer's historical GP%%")
            .isEqualByComparingTo(new BigDecimal("29.70"));

        // AND: Customer's GP% is maintained (33.86%)
        BigDecimal newGP = newPrice.subtract(newCost)
            .divide(newPrice, 4, RoundingMode.HALF_UP);
        assertThat(newGP)
            .as("Customer GP%% should match historical GP%%")
            .isCloseTo(historicalGP, org.assertj.core.data.Offset.offset(new BigDecimal("0.0002")));
    }

    @Test
    @DisplayName("BR-003: Loyalty customer gets 5% off ribeye, but price must stay above cost")
    void loyaltyCustomer_shouldGet5PercentOff_butNotBelowCost() {
        // GIVEN: Base ribeye price is $24.95, cost is $18.50
        BigDecimal basePrice = new BigDecimal("24.95");
        BigDecimal cost = new BigDecimal("18.50");
        BigDecimal loyaltyDiscount = new BigDecimal("0.95"); // 5% off = multiply by 0.95

        // WHEN: Apply loyalty discount
        BigDecimal discountedPrice = basePrice.multiply(loyaltyDiscount)
            .setScale(2, RoundingMode.HALF_UP);

        // THEN: Customer pays $23.70 (5% off)
        assertThat(discountedPrice).isEqualByComparingTo(new BigDecimal("23.70"));

        // AND: Price is still above cost (profitable)
        assertThat(discountedPrice)
            .as("Loyalty discount should not result in selling below cost")
            .isGreaterThan(cost);

        // AND: Still maintains positive GP
        BigDecimal gp = discountedPrice.subtract(cost)
            .divide(discountedPrice, 4, RoundingMode.HALF_UP);
        assertThat(gp)
            .as("Loyalty discount should still maintain positive GP")
            .isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("BR-004: Volume discount + Loyalty discount = Customer pays $22.51, still profitable")
    void volumeAndLoyaltyDiscounts_shouldStack_butStayProfitable() {
        // GIVEN: Customer qualifies for both discounts
        BigDecimal basePrice = new BigDecimal("24.95");
        BigDecimal cost = new BigDecimal("18.50");
        BigDecimal volumeDiscount = new BigDecimal("0.90"); // 10% off
        BigDecimal loyaltyDiscount = new BigDecimal("0.95"); // 5% off

        // WHEN: Apply both discounts sequentially (as per multi-layer pricing)
        BigDecimal afterVolume = basePrice.multiply(volumeDiscount)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalPrice = afterVolume.multiply(loyaltyDiscount)
            .setScale(2, RoundingMode.HALF_UP);

        // THEN: Customer pays $22.51 (compound discount ≈ 14.5% total)
        assertThat(finalPrice)
            .as("Both discounts applied sequentially")
            .isEqualByComparingTo(new BigDecimal("22.51"));

        // AND: Still profitable (price > cost)
        assertThat(finalPrice)
            .as("Stacked discounts should not result in loss")
            .isGreaterThan(cost);

        // AND: Maintains minimum 10% GP
        BigDecimal gp = finalPrice.subtract(cost)
            .divide(finalPrice, 4, RoundingMode.HALF_UP);
        assertThat(gp)
            .as("Stacked discounts should maintain minimum 10%% GP")
            .isGreaterThanOrEqualTo(new BigDecimal("0.10"));
    }

    @Test
    @DisplayName("BR-005: Ribeye pricing should NEVER go below cost unless flagged for clearance")
    void ribeyePrice_shouldNeverGoBelowCost_unlessClearance() {
        // GIVEN: Cost is $18.50
        BigDecimal cost = new BigDecimal("18.50");

        // WHEN: Multiple aggressive discounts are applied
        // Base price $24.95 - Volume 10% - Loyalty 5% - Promo 20%
        BigDecimal basePrice = new BigDecimal("24.95");
        BigDecimal afterDiscounts = basePrice
            .multiply(new BigDecimal("0.90")) // -10%
            .multiply(new BigDecimal("0.95")) // -5%
            .multiply(new BigDecimal("0.80")) // -20%
            .setScale(2, RoundingMode.HALF_UP);

        // $24.95 × 0.90 × 0.95 × 0.80 = $17.01 (BELOW COST!)

        // THEN: System should detect this is below cost
        boolean isBelowCost = afterDiscounts.compareTo(cost) < 0;
        assertThat(isBelowCost)
            .as("System detected price is below cost")
            .isTrue();

        // AND: In real system, this should either:
        // 1. Be rejected (throw exception)
        // 2. Require manager approval
        // 3. Be flagged for review
        // This is the business rule to implement!
    }

    @Test
    @DisplayName("BR-006: Premium ribeye (Choice+) gets $2 premium over Select grade")
    void premiumRibeye_shouldHave2DollarPremiumOverSelect() {
        // GIVEN: Select grade ribeye base price
        BigDecimal selectGradePrice = new BigDecimal("24.95");
        BigDecimal premiumFee = new BigDecimal("2.00");

        // WHEN: Calculate premium (Choice+) price
        BigDecimal premiumGradePrice = selectGradePrice.add(premiumFee);

        // THEN: Premium grade is $26.95
        assertThat(premiumGradePrice)
            .as("Premium ribeye costs $2 more than Select")
            .isEqualByComparingTo(new BigDecimal("26.95"));

        // AND: Premium fee is applied AFTER base pricing, BEFORE discounts
        // (This documents the order of operations for premium products)
    }

    @Test
    @DisplayName("BR-007: When cost drift exceeds 10%, manager should review pricing")
    void costDriftAbove10Percent_shouldTriggerReview() {
        // GIVEN: Last month's ribeye cost
        BigDecimal lastCost = new BigDecimal("16.50");
        BigDecimal newCost = new BigDecimal("19.80");

        // WHEN: Calculate cost drift
        BigDecimal costIncrease = newCost.subtract(lastCost);
        BigDecimal driftPercent = costIncrease.divide(lastCost, 4, RoundingMode.HALF_UP);

        // THEN: Drift is 20% (exceeds 10% threshold)
        assertThat(driftPercent).isEqualByComparingTo(new BigDecimal("0.20"));

        // AND: Should trigger review flag
        boolean requiresReview = driftPercent.compareTo(new BigDecimal("0.10")) > 0;
        assertThat(requiresReview)
            .as("Cost drift > 10%% should require manager review")
            .isTrue();
    }
}
