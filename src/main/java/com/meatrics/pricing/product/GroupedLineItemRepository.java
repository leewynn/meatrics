package com.meatrics.pricing.product;

import com.meatrics.generated.tables.records.VGroupedLineItemsRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.meatrics.generated.tables.Customers.CUSTOMERS;
import static com.meatrics.generated.tables.ImportedLineItems.IMPORTED_LINE_ITEMS;
import static com.meatrics.generated.tables.ProductCosts.PRODUCT_COSTS;
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
     * Get grouped line items filtered by date range.
     * Enhanced with JOINs to populate customer rating and incoming cost for pricing engine.
     */
    public List<GroupedLineItem> findByDateRange(LocalDate startDate, LocalDate endDate) {
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
                sum(IMPORTED_LINE_ITEMS.COST).as("total_cost"),
                CUSTOMERS.CUSTOMER_RATING,
                PRODUCT_COSTS.STANDARD_COST.as("incoming_cost"),
                PRODUCT_COSTS.PRIMARY_GROUP
        )
        .from(IMPORTED_LINE_ITEMS)
        .leftJoin(CUSTOMERS).on(IMPORTED_LINE_ITEMS.CUSTOMER_CODE.eq(CUSTOMERS.CUSTOMER_CODE))
        .leftJoin(PRODUCT_COSTS).on(IMPORTED_LINE_ITEMS.PRODUCT_CODE.eq(PRODUCT_COSTS.PRODUCT_CODE));

        // Apply conditions if any exist
        var query = conditions.isEmpty()
            ? baseQuery
            : baseQuery.where(conditions);

        var records = query.groupBy(
                IMPORTED_LINE_ITEMS.CUSTOMER_CODE,
                IMPORTED_LINE_ITEMS.CUSTOMER_NAME,
                IMPORTED_LINE_ITEMS.PRODUCT_CODE,
                IMPORTED_LINE_ITEMS.PRODUCT_DESCRIPTION,
                CUSTOMERS.CUSTOMER_RATING,
                PRODUCT_COSTS.STANDARD_COST,
                PRODUCT_COSTS.PRIMARY_GROUP
        )
        .fetch();

        // Map to GroupedLineItem with all fields populated
        return records.stream()
            .map(this::mapToGroupedLineItem)
            .collect(Collectors.toList());
    }

    /**
     * Get grouped line items for a specific customer by customer_id
     * Used for group pricing to load products from member companies
     */
    public List<GroupedLineItem> findByCustomerId(Long customerId) {
        var records = dsl.select(
                V_GROUPED_LINE_ITEMS.CUSTOMER_CODE,
                V_GROUPED_LINE_ITEMS.CUSTOMER_NAME,
                V_GROUPED_LINE_ITEMS.PRODUCT_CODE,
                V_GROUPED_LINE_ITEMS.PRODUCT_DESCRIPTION,
                V_GROUPED_LINE_ITEMS.CATEGORY,
                V_GROUPED_LINE_ITEMS.UNIT,
                V_GROUPED_LINE_ITEMS.TOTAL_QUANTITY,
                V_GROUPED_LINE_ITEMS.TOTAL_AMOUNT,
                V_GROUPED_LINE_ITEMS.TOTAL_COST,
                V_GROUPED_LINE_ITEMS.LAST_PRICE,
                V_GROUPED_LINE_ITEMS.CURRENT_COST,
                V_GROUPED_LINE_ITEMS.CUSTOMER_ID,
                CUSTOMERS.CUSTOMER_RATING
        )
        .from(V_GROUPED_LINE_ITEMS)
        .leftJoin(CUSTOMERS).on(V_GROUPED_LINE_ITEMS.CUSTOMER_ID.eq(CUSTOMERS.CUSTOMER_ID))
        .where(V_GROUPED_LINE_ITEMS.CUSTOMER_ID.eq(customerId))
        .fetch();

        return records.stream()
                .map(this::mapViewRecordToGroupedLineItem)
                .collect(Collectors.toList());
    }

    /**
     * Map view record to GroupedLineItem entity
     */
    private GroupedLineItem mapViewRecordToGroupedLineItem(Record record) {
        GroupedLineItem item = new GroupedLineItem();

        item.setCustomerCode(record.get(V_GROUPED_LINE_ITEMS.CUSTOMER_CODE));
        item.setCustomerName(record.get(V_GROUPED_LINE_ITEMS.CUSTOMER_NAME));
        item.setProductCode(record.get(V_GROUPED_LINE_ITEMS.PRODUCT_CODE));
        item.setProductDescription(record.get(V_GROUPED_LINE_ITEMS.PRODUCT_DESCRIPTION));
        item.setPrimaryGroup(record.get(V_GROUPED_LINE_ITEMS.CATEGORY));

        BigDecimal totalQty = record.get(V_GROUPED_LINE_ITEMS.TOTAL_QUANTITY);
        BigDecimal totalAmt = record.get(V_GROUPED_LINE_ITEMS.TOTAL_AMOUNT);
        BigDecimal totalCost = record.get(V_GROUPED_LINE_ITEMS.TOTAL_COST);

        item.setTotalQuantity(totalQty);
        item.setTotalAmount(totalAmt);
        item.setTotalCost(totalCost);

        // Historical/last price data
        BigDecimal lastPrice = record.get(V_GROUPED_LINE_ITEMS.LAST_PRICE);
        item.setLastUnitSellPrice(lastPrice);
        item.setLastAmount(totalAmt);

        // Calculate last cost (use 6 decimals precision)
        if (totalCost != null && totalQty != null && totalQty.compareTo(BigDecimal.ZERO) > 0) {
            item.setLastCost(totalCost.divide(totalQty, 6, RoundingMode.HALF_UP));
        }

        // Last gross profit
        if (totalAmt != null && totalCost != null) {
            item.setLastGrossProfit(totalAmt.subtract(totalCost));
        }

        // Incoming cost from product_costs
        item.setIncomingCost(record.get(V_GROUPED_LINE_ITEMS.CURRENT_COST));

        // Customer rating
        item.setCustomerRating(record.get(CUSTOMERS.CUSTOMER_RATING));

        return item;
    }

    /**
     * Map database record to GroupedLineItem entity with all fields
     */
    private GroupedLineItem mapToGroupedLineItem(Record record) {
        GroupedLineItem item = new GroupedLineItem();

        // Basic grouping fields
        item.setCustomerCode(record.get(IMPORTED_LINE_ITEMS.CUSTOMER_CODE));
        item.setCustomerName(record.get(IMPORTED_LINE_ITEMS.CUSTOMER_NAME));
        item.setProductCode(record.get(IMPORTED_LINE_ITEMS.PRODUCT_CODE));
        item.setProductDescription(record.get(IMPORTED_LINE_ITEMS.PRODUCT_DESCRIPTION));

        // Aggregated values
        BigDecimal totalQty = record.get("total_quantity", BigDecimal.class);
        BigDecimal totalAmt = record.get("total_amount", BigDecimal.class);
        BigDecimal totalCost = record.get("total_cost", BigDecimal.class);

        item.setTotalQuantity(totalQty);
        item.setTotalAmount(totalAmt);
        item.setTotalCost(totalCost);

        // Historical data - populate from aggregated values (use 6 decimals precision)
        // Last cost = average unit cost from historical data
        if (totalCost != null && totalQty != null && totalQty.compareTo(BigDecimal.ZERO) > 0) {
            item.setLastCost(totalCost.divide(totalQty, 6, RoundingMode.HALF_UP));
        }

        // Last unit sell price = average sell price from historical data
        if (totalAmt != null && totalQty != null && totalQty.compareTo(BigDecimal.ZERO) > 0) {
            item.setLastUnitSellPrice(totalAmt.divide(totalQty, 6, RoundingMode.HALF_UP));
        }

        item.setLastAmount(totalAmt);

        // Last gross profit = total amount - total cost
        if (totalAmt != null && totalCost != null) {
            item.setLastGrossProfit(totalAmt.subtract(totalCost));
        }

        // Customer rating from JOIN
        item.setCustomerRating(record.get(CUSTOMERS.CUSTOMER_RATING));

        // Incoming cost from product_costs.standard_cost
        item.setIncomingCost(record.get("incoming_cost", BigDecimal.class));

        // Product category for rule matching
        item.setPrimaryGroup(record.get(PRODUCT_COSTS.PRIMARY_GROUP));

        return item;
    }

    /**
     * Get distinct product categories for dropdown filtering
     */
    public List<String> findDistinctCategories() {
        return dsl.selectDistinct(PRODUCT_COSTS.PRIMARY_GROUP)
                .from(PRODUCT_COSTS)
                .where(PRODUCT_COSTS.PRIMARY_GROUP.isNotNull())
                .orderBy(PRODUCT_COSTS.PRIMARY_GROUP)
                .fetch(PRODUCT_COSTS.PRIMARY_GROUP);
    }

    /**
     * Get distinct product codes for dropdown filtering
     */
    public List<String> findDistinctProductCodes() {
        return dsl.selectDistinct(PRODUCT_COSTS.PRODUCT_CODE)
                .from(PRODUCT_COSTS)
                .where(PRODUCT_COSTS.PRODUCT_CODE.isNotNull())
                .orderBy(PRODUCT_COSTS.PRODUCT_CODE)
                .fetch(PRODUCT_COSTS.PRODUCT_CODE);
    }
}
