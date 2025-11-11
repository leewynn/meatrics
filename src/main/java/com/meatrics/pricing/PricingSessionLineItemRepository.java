package com.meatrics.pricing;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.meatrics.generated.Tables.PRICING_SESSION_LINE_ITEMS;

/**
 * Repository for pricing session line item data access
 */
@Repository
public class PricingSessionLineItemRepository {

    private final DSLContext dsl;

    public PricingSessionLineItemRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Find all line items for a session
     */
    public List<PricingSessionLineItem> findBySessionId(Long sessionId) {
        return dsl.selectFrom(PRICING_SESSION_LINE_ITEMS)
                .where(PRICING_SESSION_LINE_ITEMS.SESSION_ID.eq(sessionId))
                .fetch(this::mapToPricingSessionLineItem);
    }

    /**
     * Delete all line items for a session
     */
    public void deleteBySessionId(Long sessionId) {
        dsl.deleteFrom(PRICING_SESSION_LINE_ITEMS)
                .where(PRICING_SESSION_LINE_ITEMS.SESSION_ID.eq(sessionId))
                .execute();
    }

    /**
     * Save all line items for a session (batch insert)
     * Includes all historical and new pricing data fields
     */
    public void saveAll(Long sessionId, List<PricingSessionLineItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // Using raw SQL with all pricing fields
        String sql = "INSERT INTO pricing_session_line_items " +
                "(session_id, customer_code, customer_name, customer_rating, product_code, " +
                "product_description, total_quantity, total_amount, original_amount, total_cost, " +
                "amount_modified, last_cost, last_unit_sell_price, last_amount, last_gross_profit, " +
                "incoming_cost, primary_group, new_unit_sell_price, new_amount, new_gross_profit, " +
                "applied_rule, manual_override) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dsl.batch(
            items.stream()
                .map(item -> dsl.query(sql,
                    sessionId,
                    item.getCustomerCode(),
                    item.getCustomerName(),
                    item.getCustomerRating(),
                    item.getProductCode(),
                    item.getProductDescription(),
                    item.getTotalQuantity(),
                    item.getTotalAmount(),
                    item.getOriginalAmount(),
                    item.getTotalCost(),
                    item.getAmountModified() != null ? item.getAmountModified() : false,
                    item.getLastCost(),
                    item.getLastUnitSellPrice(),
                    item.getLastAmount(),
                    item.getLastGrossProfit(),
                    item.getIncomingCost(),
                    item.getPrimaryGroup(),
                    item.getNewUnitSellPrice(),
                    item.getNewAmount(),
                    item.getNewGrossProfit(),
                    item.getAppliedRule(), // Save rule names as TEXT
                    item.getManualOverride() != null ? item.getManualOverride() : false
                ))
                .toArray(org.jooq.Query[]::new)
        ).execute();
    }

    private PricingSessionLineItem mapToPricingSessionLineItem(org.jooq.Record record) {
        PricingSessionLineItem item = new PricingSessionLineItem();
        item.setId(record.get(PRICING_SESSION_LINE_ITEMS.ID));
        item.setSessionId(record.get(PRICING_SESSION_LINE_ITEMS.SESSION_ID));
        item.setCustomerCode(record.get(PRICING_SESSION_LINE_ITEMS.CUSTOMER_CODE));
        item.setCustomerName(record.get(PRICING_SESSION_LINE_ITEMS.CUSTOMER_NAME));
        item.setCustomerRating(record.get(PRICING_SESSION_LINE_ITEMS.CUSTOMER_RATING));
        item.setProductCode(record.get(PRICING_SESSION_LINE_ITEMS.PRODUCT_CODE));
        item.setProductDescription(record.get(PRICING_SESSION_LINE_ITEMS.PRODUCT_DESCRIPTION));
        item.setTotalQuantity(record.get(PRICING_SESSION_LINE_ITEMS.TOTAL_QUANTITY));
        item.setTotalAmount(record.get(PRICING_SESSION_LINE_ITEMS.TOTAL_AMOUNT));
        item.setOriginalAmount(record.get(PRICING_SESSION_LINE_ITEMS.ORIGINAL_AMOUNT));
        item.setTotalCost(record.get(PRICING_SESSION_LINE_ITEMS.TOTAL_COST));
        item.setAmountModified(record.get(PRICING_SESSION_LINE_ITEMS.AMOUNT_MODIFIED));

        // Historical pricing fields - load all available fields
        try {
            item.setLastCost(record.get("last_cost", java.math.BigDecimal.class));
            item.setLastUnitSellPrice(record.get("last_unit_sell_price", java.math.BigDecimal.class));
            item.setLastAmount(record.get("last_amount", java.math.BigDecimal.class));
            item.setLastGrossProfit(record.get("last_gross_profit", java.math.BigDecimal.class));
            item.setIncomingCost(record.get("incoming_cost", java.math.BigDecimal.class));
            item.setPrimaryGroup(record.get("primary_group", String.class));
        } catch (Exception e) {
            // Historical fields might not exist in older sessions - this is fine
        }

        // New pricing fields - load all available fields
        try {
            item.setNewUnitSellPrice(record.get("new_unit_sell_price", java.math.BigDecimal.class));
            item.setNewAmount(record.get("new_amount", java.math.BigDecimal.class));
            item.setNewGrossProfit(record.get("new_gross_profit", java.math.BigDecimal.class));
            item.setAppliedRule(record.get("applied_rule", String.class));
            item.setManualOverride(record.get("manual_override", Boolean.class));
        } catch (Exception e) {
            // New pricing fields might not exist in older sessions - this is fine
        }

        return item;
    }
}
