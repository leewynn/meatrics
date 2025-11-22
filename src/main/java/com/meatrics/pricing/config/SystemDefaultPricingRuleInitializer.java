package com.meatrics.pricing.config;

import com.meatrics.pricing.rule.PricingRule;
import com.meatrics.pricing.rule.PricingRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Initializes system default pricing rule on application startup.
 * Ensures a fallback MAINTAIN_GP_PERCENT rule always exists with highest execution order (runs last).
 */
@Component
public class SystemDefaultPricingRuleInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemDefaultPricingRuleInitializer.class);

    private static final String SYSTEM_DEFAULT_RULE_NAME = "System Default - Maintain GP%";
    private static final int SYSTEM_DEFAULT_EXECUTION_ORDER = 9999; // Highest execution order - only used as last resort fallback
    private static final BigDecimal DEFAULT_GP_FALLBACK = new BigDecimal("0.27"); // 25% GP fallback

    private final PricingRuleRepository pricingRuleRepository;

    public SystemDefaultPricingRuleInitializer(PricingRuleRepository pricingRuleRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureSystemDefaultRuleExists();
        } catch (Exception e) {
            log.error("Failed to initialize system default pricing rule", e);
            // Don't throw - allow application to start even if this fails
        }
    }

    /**
     * Check if system default rule exists, create it if not
     */
    private void ensureSystemDefaultRuleExists() {
        // Check if a rule with this name already exists
        PricingRule existingRule = pricingRuleRepository.findByRuleName(SYSTEM_DEFAULT_RULE_NAME)
                .orElse(null);

        if (existingRule != null) {
            log.info("System default pricing rule already exists (id={})", existingRule.getId());
            return;
        }

        // Create the system default rule
        PricingRule systemDefaultRule = new PricingRule();
        systemDefaultRule.setRuleName(SYSTEM_DEFAULT_RULE_NAME);
        systemDefaultRule.setCustomerCode(null); // Standard rule (applies to all customers)
        systemDefaultRule.setConditionType("ALL_PRODUCTS");
        systemDefaultRule.setConditionValue(null); // Not needed for ALL_PRODUCTS
        systemDefaultRule.setPricingMethod("MAINTAIN_GP_PERCENT");
        systemDefaultRule.setPricingValue(DEFAULT_GP_FALLBACK); // 25% GP as fallback for products with no history
        systemDefaultRule.setIsActive(true);

        // Set execution order and date validity
        systemDefaultRule.setExecutionOrder(SYSTEM_DEFAULT_EXECUTION_ORDER);     // Highest execution order - runs last as fallback
        systemDefaultRule.setValidFrom(null);  // Always active - no start date
        systemDefaultRule.setValidTo(null);    // No expiration - always valid

        PricingRule savedRule = pricingRuleRepository.save(systemDefaultRule);

        log.info("Created system default pricing rule: id={}, name={}, executionOrder={}, method={}, fallback_gp={}%",
                savedRule.getId(),
                savedRule.getRuleName(),
                savedRule.getExecutionOrder(),
                savedRule.getPricingMethod(),
                DEFAULT_GP_FALLBACK.multiply(new BigDecimal("100")).intValue());
    }
}
