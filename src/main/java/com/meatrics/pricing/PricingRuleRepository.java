package com.meatrics.pricing;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.meatrics.generated.Tables.PRICING_RULE;

/**
 * Repository for pricing rule data access using jOOQ.
 * Handles CRUD operations and specialized queries for rule matching.
 */
@Repository
public class PricingRuleRepository {

    private final DSLContext dsl;

    public PricingRuleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Find all pricing rules ordered by priority
     */
    public List<PricingRule> findAll() {
        return dsl.selectFrom(PRICING_RULE)
                .orderBy(PRICING_RULE.PRIORITY.asc(), PRICING_RULE.ID.asc())
                .fetch(this::mapToPricingRule);
    }

    /**
     * Find pricing rule by ID
     */
    public PricingRule findById(Long id) {
        return dsl.selectFrom(PRICING_RULE)
                .where(PRICING_RULE.ID.eq(id))
                .fetchOne(this::mapToPricingRule);
    }

    /**
     * Find pricing rule by name
     */
    public Optional<PricingRule> findByRuleName(String ruleName) {
        return Optional.ofNullable(
            dsl.selectFrom(PRICING_RULE)
                .where(PRICING_RULE.RULE_NAME.eq(ruleName))
                .fetchOne(this::mapToPricingRule)
        );
    }

    /**
     * Find active rules for a specific customer, sorted by priority ascending.
     * Returns customer-specific rules only (where customer_code = customerCode).
     */
    public List<PricingRule> findByCustomerCode(String customerCode) {
        return dsl.selectFrom(PRICING_RULE)
                .where(PRICING_RULE.CUSTOMER_CODE.eq(customerCode))
                .and(PRICING_RULE.IS_ACTIVE.eq(true))
                .orderBy(PRICING_RULE.PRIORITY.asc(), PRICING_RULE.ID.asc())
                .fetch(this::mapToPricingRule);
    }

    /**
     * Find active standard rules (where customer_code IS NULL), sorted by priority ascending.
     * These rules apply to all customers as fallback.
     */
    public List<PricingRule> findStandardRules() {
        return dsl.selectFrom(PRICING_RULE)
                .where(PRICING_RULE.CUSTOMER_CODE.isNull())
                .and(PRICING_RULE.IS_ACTIVE.eq(true))
                .orderBy(PRICING_RULE.PRIORITY.asc(), PRICING_RULE.ID.asc())
                .fetch(this::mapToPricingRule);
    }

    /**
     * Find applicable rules for a customer.
     * Returns both customer-specific and standard rules.
     * Results are sorted by priority ascending for first-match-wins evaluation.
     */
    public List<PricingRule> findApplicableRules(String customerCode) {
        // Include both customer-specific and standard rules
        return dsl.selectFrom(PRICING_RULE)
                .where(PRICING_RULE.IS_ACTIVE.eq(true))
                .and(
                    PRICING_RULE.CUSTOMER_CODE.isNull()  // Standard rules
                    .or(PRICING_RULE.CUSTOMER_CODE.eq(customerCode))  // Customer-specific rules
                )
                .orderBy(PRICING_RULE.PRIORITY.asc(), PRICING_RULE.ID.asc())
                .fetch(this::mapToPricingRule);
    }

    /**
     * Find all active rules in a specific category, ordered by layer_order.
     * Note: Since jOOQ code needs regeneration, this uses in-memory filtering.
     * After jOOQ regeneration, replace with database-level filtering.
     *
     * @param ruleCategory The category to filter by
     * @return List of rules in that category, ordered by layer_order ASC
     */
    public List<PricingRule> findByRuleCategoryOrderByLayerOrderAsc(RuleCategory ruleCategory) {
        // Temporary implementation using in-memory filtering until jOOQ is regenerated
        return findAll().stream()
                .filter(rule -> rule.getRuleCategory() != null && rule.getRuleCategory().equals(ruleCategory))
                .filter(rule -> Boolean.TRUE.equals(rule.getIsActive()))
                .sorted(Comparator.comparing(PricingRule::getLayerOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Find active rules for a customer in a specific category.
     *
     * @param customerCode Customer code
     * @param ruleCategory Category to filter by
     * @return List of matching rules ordered by layer_order
     */
    public List<PricingRule> findByCustomerCodeAndRuleCategoryOrderByLayerOrderAsc(String customerCode, RuleCategory ruleCategory) {
        // Temporary implementation using in-memory filtering until jOOQ is regenerated
        return findAll().stream()
                .filter(rule -> customerCode.equals(rule.getCustomerCode()))
                .filter(rule -> rule.getRuleCategory() != null && rule.getRuleCategory().equals(ruleCategory))
                .filter(rule -> Boolean.TRUE.equals(rule.getIsActive()))
                .sorted(Comparator.comparing(PricingRule::getLayerOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Find standard (non-customer-specific) active rules in a category.
     *
     * @param ruleCategory Category to filter by
     * @return List of standard rules ordered by layer_order
     */
    public List<PricingRule> findByCustomerCodeIsNullAndRuleCategoryOrderByLayerOrderAsc(RuleCategory ruleCategory) {
        // Temporary implementation using in-memory filtering until jOOQ is regenerated
        return findAll().stream()
                .filter(rule -> rule.getCustomerCode() == null)
                .filter(rule -> rule.getRuleCategory() != null && rule.getRuleCategory().equals(ruleCategory))
                .filter(rule -> Boolean.TRUE.equals(rule.getIsActive()))
                .sorted(Comparator.comparing(PricingRule::getLayerOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Find all rules that are valid on a specific date.
     * A rule is valid if:
     * - (valid_from IS NULL OR valid_from <= date) AND
     * - (valid_to IS NULL OR valid_to >= date) AND
     * - is_active = true
     *
     * @param date The date to check
     * @return List of rules active on that date
     */
    public List<PricingRule> findActiveRulesOnDate(LocalDate date) {
        // Temporary implementation using in-memory filtering until jOOQ is regenerated
        return findAll().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getIsActive()))
                .filter(rule -> rule.isValidOnDate(date))
                .sorted(Comparator.comparing(PricingRule::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * Find active rules in a specific category that are valid on a date.
     *
     * @param ruleCategory The category to filter by
     * @param date The date to check validity
     * @return List of rules in category, active on date
     */
    public List<PricingRule> findByRuleCategoryAndActiveOnDate(RuleCategory ruleCategory, LocalDate date) {
        // Temporary implementation using in-memory filtering until jOOQ is regenerated
        return findAll().stream()
                .filter(rule -> rule.getRuleCategory() != null && rule.getRuleCategory().equals(ruleCategory))
                .filter(rule -> Boolean.TRUE.equals(rule.getIsActive()))
                .filter(rule -> rule.isValidOnDate(date))
                .sorted(Comparator.comparing(PricingRule::getLayerOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Save a pricing rule (insert or update).
     * If id is null, inserts new record. Otherwise updates existing.
     */
    public PricingRule save(PricingRule rule) {
        if (rule.getId() == null) {
            // Insert new rule
            if (rule.getCreatedAt() == null) {
                rule.setCreatedAt(LocalDateTime.now());
            }

            // Set defaults for new fields if not provided
            if (rule.getRuleCategory() == null) {
                rule.setRuleCategory(RuleCategory.BASE_PRICE);
            }
            if (rule.getLayerOrder() == null) {
                rule.setLayerOrder(rule.getPriority()); // Default to priority value
            }

            // TODO: After jOOQ regeneration, uncomment the new field setters below
            Long newId = dsl.insertInto(PRICING_RULE)
                    .set(PRICING_RULE.RULE_NAME, rule.getRuleName())
                    .set(PRICING_RULE.CUSTOMER_CODE, rule.getCustomerCode())
                    .set(PRICING_RULE.CONDITION_TYPE, rule.getConditionType())
                    .set(PRICING_RULE.CONDITION_VALUE, rule.getConditionValue())
                    .set(PRICING_RULE.PRICING_METHOD, rule.getPricingMethod())
                    .set(PRICING_RULE.PRICING_VALUE, rule.getPricingValue())
                    .set(PRICING_RULE.PRIORITY, rule.getPriority())
                    .set(PRICING_RULE.IS_ACTIVE, rule.getIsActive() != null ? rule.getIsActive() : true)
                    .set(PRICING_RULE.CREATED_AT, rule.getCreatedAt())
                    .set(PRICING_RULE.RULE_CATEGORY, rule.getRuleCategory() != null ? rule.getRuleCategory().name() : null)
                    .set(PRICING_RULE.LAYER_ORDER, rule.getLayerOrder())
                    .set(PRICING_RULE.VALID_FROM, rule.getValidFrom())
                    .set(PRICING_RULE.VALID_TO, rule.getValidTo())
                    .returningResult(PRICING_RULE.ID)
                    .fetchOne()
                    .getValue(PRICING_RULE.ID);

            rule.setId(newId);
            return rule;
        } else {
            // Update existing rule
            rule.setUpdatedAt(LocalDateTime.now());

            dsl.update(PRICING_RULE)
                    .set(PRICING_RULE.RULE_NAME, rule.getRuleName())
                    .set(PRICING_RULE.CUSTOMER_CODE, rule.getCustomerCode())
                    .set(PRICING_RULE.CONDITION_TYPE, rule.getConditionType())
                    .set(PRICING_RULE.CONDITION_VALUE, rule.getConditionValue())
                    .set(PRICING_RULE.PRICING_METHOD, rule.getPricingMethod())
                    .set(PRICING_RULE.PRICING_VALUE, rule.getPricingValue())
                    .set(PRICING_RULE.PRIORITY, rule.getPriority())
                    .set(PRICING_RULE.IS_ACTIVE, rule.getIsActive())
                    .set(PRICING_RULE.UPDATED_AT, rule.getUpdatedAt())
                    .set(PRICING_RULE.RULE_CATEGORY, rule.getRuleCategory() != null ? rule.getRuleCategory().name() : null)
                    .set(PRICING_RULE.LAYER_ORDER, rule.getLayerOrder())
                    .set(PRICING_RULE.VALID_FROM, rule.getValidFrom())
                    .set(PRICING_RULE.VALID_TO, rule.getValidTo())
                    .where(PRICING_RULE.ID.eq(rule.getId()))
                    .execute();

            return rule;
        }
    }

    /**
     * Delete a pricing rule by ID
     */
    public void deleteById(Long id) {
        dsl.deleteFrom(PRICING_RULE)
                .where(PRICING_RULE.ID.eq(id))
                .execute();
    }

    /**
     * Count all pricing rules
     */
    public int countAll() {
        return dsl.fetchCount(PRICING_RULE);
    }

    /**
     * Count active standard rules (used to prevent deletion of last default rule)
     */
    public int countActiveStandardRules() {
        return dsl.fetchCount(
            PRICING_RULE,
            PRICING_RULE.CUSTOMER_CODE.isNull()
                .and(PRICING_RULE.IS_ACTIVE.eq(true))
        );
    }

    /**
     * Map jOOQ record to PricingRule entity
     */
    private PricingRule mapToPricingRule(org.jooq.Record record) {
        PricingRule rule = new PricingRule();
        rule.setId(record.get(PRICING_RULE.ID));
        rule.setRuleName(record.get(PRICING_RULE.RULE_NAME));
        rule.setCustomerCode(record.get(PRICING_RULE.CUSTOMER_CODE));
        rule.setConditionType(record.get(PRICING_RULE.CONDITION_TYPE));
        rule.setConditionValue(record.get(PRICING_RULE.CONDITION_VALUE));
        rule.setPricingMethod(record.get(PRICING_RULE.PRICING_METHOD));
        rule.setPricingValue(record.get(PRICING_RULE.PRICING_VALUE));
        rule.setPriority(record.get(PRICING_RULE.PRIORITY));
        rule.setIsActive(record.get(PRICING_RULE.IS_ACTIVE));
        rule.setCreatedAt(record.get(PRICING_RULE.CREATED_AT));
        rule.setUpdatedAt(record.get(PRICING_RULE.UPDATED_AT));

        // Map new Phase 2 fields
        String categoryStr = record.get(PRICING_RULE.RULE_CATEGORY);
        rule.setRuleCategory(categoryStr != null ? RuleCategory.valueOf(categoryStr) : RuleCategory.BASE_PRICE);
        rule.setLayerOrder(record.get(PRICING_RULE.LAYER_ORDER));
        rule.setValidFrom(record.get(PRICING_RULE.VALID_FROM));
        rule.setValidTo(record.get(PRICING_RULE.VALID_TO));

        return rule;
    }
}
