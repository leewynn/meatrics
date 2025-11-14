package com.meatrics.pricing.calculation;

import com.meatrics.pricing.rule.PricingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * UNIT TESTS: PricingResult data structure.
 * Tests that the result container works correctly.
 *
 * Type: Unit/Technical
 * Speed: Fast (milliseconds)
 * Purpose: Verify data structure integrity
 */
@Tag("unit")
@Tag("fast")
@DisplayName("PricingResult Data Structure Tests")
class PricingResultTest {

    @Test
    void constructor_withSingleRule_shouldStorePriceAndRule() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal price = new BigDecimal("12.00");
        PricingRule rule = createTestRule("Test Rule", "COST_PLUS_PERCENT");

        // When
        PricingResult result = new PricingResult(
            cost, price, rule, "Cost $10.00 × 1.20 = $12.00"
        );

        // Then
        assertThat(result.getCost()).isEqualByComparingTo(cost);
        assertThat(result.getCalculatedPrice()).isEqualByComparingTo(price);
        assertThat(result.getAppliedRule()).isEqualTo(rule);
        assertThat(result.getAppliedRules()).hasSize(1);
        assertThat(result.getAppliedRules().get(0)).isEqualTo(rule);
        assertThat(result.isMultiRule()).isFalse();
        assertThat(result.getRuleDescription()).isEqualTo("Cost $10.00 × 1.20 = $12.00");
    }

    @Test
    void constructor_withMultipleRules_shouldStoreAllRulesInOrder() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal finalPrice = new BigDecimal("10.26");

        PricingRule baseRule = createTestRule("Base Price", "COST_PLUS_PERCENT");
        PricingRule customerRule = createTestRule("Volume Discount", "COST_PLUS_PERCENT");
        PricingRule promoRule = createTestRule("Seasonal Sale", "COST_PLUS_PERCENT");

        List<PricingRule> rules = List.of(baseRule, customerRule, promoRule);
        List<BigDecimal> intermediates = List.of(
            new BigDecimal("12.00"),  // After base price
            new BigDecimal("10.80"),  // After customer discount
            new BigDecimal("10.26")   // After promo (final)
        );

        // When
        PricingResult result = new PricingResult(
            cost, finalPrice, rules, intermediates, "Multi-layer pricing applied"
        );

        // Then
        assertThat(result.getCost()).isEqualByComparingTo(cost);
        assertThat(result.getCalculatedPrice()).isEqualByComparingTo(finalPrice);
        assertThat(result.getAppliedRules()).hasSize(3);
        assertThat(result.getAppliedRules()).containsExactly(baseRule, customerRule, promoRule);
        assertThat(result.getAppliedRule()).isEqualTo(baseRule); // First rule for backward compatibility
        assertThat(result.isMultiRule()).isTrue();
        assertThat(result.getIntermediateResults()).hasSize(3);
        assertThat(result.getIntermediateResults().get(2)).isEqualByComparingTo(finalPrice);
    }

    @Test
    void constructor_withNullRule_shouldHandleGracefully() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal price = new BigDecimal("10.00");

        // When
        PricingResult result = new PricingResult(
            cost, price, null, "No rule applied"
        );

        // Then
        assertThat(result.getCost()).isEqualByComparingTo(cost);
        assertThat(result.getCalculatedPrice()).isEqualByComparingTo(price);
        assertThat(result.getAppliedRule()).isNull();
        assertThat(result.getAppliedRules()).isEmpty();
        assertThat(result.isMultiRule()).isFalse();
    }

    @Test
    void getIntermediateResults_withSingleRule_shouldContainFinalPrice() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal price = new BigDecimal("12.00");
        PricingRule rule = createTestRule("Test Rule", "COST_PLUS_PERCENT");

        // When
        PricingResult result = new PricingResult(cost, price, rule, "Test");

        // Then
        assertThat(result.getIntermediateResults()).hasSize(1);
        assertThat(result.getIntermediateResults().get(0)).isEqualByComparingTo(price);
    }

    @Test
    void getIntermediateResults_withMultipleRules_shouldReturnImmutableList() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal finalPrice = new BigDecimal("10.26");

        List<PricingRule> rules = List.of(
            createTestRule("Rule 1", "COST_PLUS_PERCENT"),
            createTestRule("Rule 2", "COST_PLUS_PERCENT")
        );
        List<BigDecimal> intermediates = List.of(
            new BigDecimal("12.00"),
            new BigDecimal("10.26")
        );

        PricingResult result = new PricingResult(cost, finalPrice, rules, intermediates, "Test");

        // When - try to modify the returned list
        List<BigDecimal> returnedIntermediates = result.getIntermediateResults();

        // Then - should be immutable
        assertThat(returnedIntermediates).hasSize(2);
        assertThatThrownBy(() -> returnedIntermediates.add(BigDecimal.ONE))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getAppliedRules_shouldReturnImmutableList() {
        // Given
        PricingRule rule = createTestRule("Test Rule", "COST_PLUS_PERCENT");
        List<PricingRule> rules = List.of(rule);
        List<BigDecimal> intermediates = List.of(new BigDecimal("12.00"));

        PricingResult result = new PricingResult(
            new BigDecimal("10.00"),
            new BigDecimal("12.00"),
            rules,
            intermediates,
            "Test"
        );

        // When - try to modify the returned list
        List<PricingRule> returnedRules = result.getAppliedRules();

        // Then - should be immutable
        assertThat(returnedRules).hasSize(1);
        assertThatThrownBy(() -> returnedRules.add(createTestRule("New", "FIXED_PRICE")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // Helper method to create test pricing rules
    private PricingRule createTestRule(String name, String method) {
        PricingRule rule = new PricingRule();
        rule.setRuleName(name);
        rule.setPricingMethod(method);
        rule.setPricingValue(new BigDecimal("1.20"));
        return rule;
    }
}
