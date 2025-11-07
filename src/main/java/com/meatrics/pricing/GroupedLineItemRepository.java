package com.meatrics.pricing;

import com.meatrics.generated.tables.records.VGroupedLineItemsRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.meatrics.generated.tables.ImportedLineItems.IMPORTED_LINE_ITEMS;
import static com.meatrics.generated.tables.VGroupedLineItems.V_GROUPED_LINE_ITEMS;
import static org.jooq.impl.DSL.sum;

/**
 * Repository for querying grouped line items (aggregated by customer + product)
 */
@Repository
public class GroupedLineItemRepository {

    private final DSLContext dsl;

    public GroupedLineItemRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Get all grouped line items (uses database view)
     */
    public List<VGroupedLineItemsRecord> findAll() {
        return dsl.selectFrom(V_GROUPED_LINE_ITEMS)
                .fetch();
    }

    /**
     * Get grouped line items filtered by date range
     * (queries base table with grouping since view doesn't have transaction_date)
     */
    public List<VGroupedLineItemsRecord> findByDateRange(LocalDate startDate, LocalDate endDate) {
        // Build WHERE conditions
        List<Condition> conditions = new ArrayList<>();
        if (startDate != null) {
            conditions.add(IMPORTED_LINE_ITEMS.TRANSACTION_DATE.greaterOrEqual(startDate));
        }
        if (endDate != null) {
            conditions.add(IMPORTED_LINE_ITEMS.TRANSACTION_DATE.lessOrEqual(endDate));
        }

        var baseQuery = dsl.select(
                IMPORTED_LINE_ITEMS.CUSTOMER_CODE,
                IMPORTED_LINE_ITEMS.CUSTOMER_NAME,
                IMPORTED_LINE_ITEMS.PRODUCT_CODE,
                IMPORTED_LINE_ITEMS.PRODUCT_DESCRIPTION,
                sum(IMPORTED_LINE_ITEMS.QUANTITY).as("total_quantity"),
                sum(IMPORTED_LINE_ITEMS.AMOUNT).as("total_amount"),
                sum(IMPORTED_LINE_ITEMS.COST).as("total_cost")
        )
        .from(IMPORTED_LINE_ITEMS);

        // Apply conditions if any exist
        var query = conditions.isEmpty()
            ? baseQuery
            : baseQuery.where(conditions);

        return query.groupBy(
                IMPORTED_LINE_ITEMS.CUSTOMER_CODE,
                IMPORTED_LINE_ITEMS.CUSTOMER_NAME,
                IMPORTED_LINE_ITEMS.PRODUCT_CODE,
                IMPORTED_LINE_ITEMS.PRODUCT_DESCRIPTION
        )
        .fetchInto(VGroupedLineItemsRecord.class);
    }
}
