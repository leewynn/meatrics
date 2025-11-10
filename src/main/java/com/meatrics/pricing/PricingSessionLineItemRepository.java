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
     * NOTE: After running migration 014, you must regenerate jOOQ code for the new columns
     */
    public void saveAll(Long sessionId, List<PricingSessionLineItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // Using raw SQL until jOOQ code is regenerated after migration 014
        String sql = "INSERT INTO pricing_session_line_items " +
                "(session_id, customer_code, customer_name, customer_rating, product_code, " +
                "product_description, total_quantity, total_amount, original_amount, total_cost, " +
                "amount_modified, last_cost, last_unit_sell_price, incoming_cost, primary_group) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
                    item.getIncomingCost(),
                    item.getPrimaryGroup()
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

        // Historical pricing fields (after migration 014 and jOOQ regeneration)
        // Using field name strings until jOOQ code is regenerated
        try {
            item.setLastCost(record.get("last_cost", java.math.BigDecimal.class));
            item.setLastUnitSellPrice(record.get("last_unit_sell_price", java.math.BigDecimal.class));
            item.setIncomingCost(record.get("incoming_cost", java.math.BigDecimal.class));
            item.setPrimaryGroup(record.get("primary_group", String.class));
        } catch (Exception e) {
            // Columns don't exist yet - migration 014 hasn't run
            // This is fine, fields will remain null
        }

        return item;
    }
}
