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
     */
    public void saveAll(Long sessionId, List<PricingSessionLineItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        var insertQuery = dsl.insertInto(PRICING_SESSION_LINE_ITEMS,
                PRICING_SESSION_LINE_ITEMS.SESSION_ID,
                PRICING_SESSION_LINE_ITEMS.CUSTOMER_CODE,
                PRICING_SESSION_LINE_ITEMS.CUSTOMER_NAME,
                PRICING_SESSION_LINE_ITEMS.CUSTOMER_RATING,
                PRICING_SESSION_LINE_ITEMS.PRODUCT_CODE,
                PRICING_SESSION_LINE_ITEMS.PRODUCT_DESCRIPTION,
                PRICING_SESSION_LINE_ITEMS.TOTAL_QUANTITY,
                PRICING_SESSION_LINE_ITEMS.TOTAL_AMOUNT,
                PRICING_SESSION_LINE_ITEMS.ORIGINAL_AMOUNT,
                PRICING_SESSION_LINE_ITEMS.TOTAL_COST,
                PRICING_SESSION_LINE_ITEMS.AMOUNT_MODIFIED);

        for (PricingSessionLineItem item : items) {
            insertQuery = insertQuery.values(
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
                    item.getAmountModified() != null ? item.getAmountModified() : false
            );
        }

        insertQuery.execute();
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
        return item;
    }
}
