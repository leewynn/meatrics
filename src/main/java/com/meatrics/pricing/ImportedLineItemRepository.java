package com.meatrics.pricing;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.meatrics.generated.Tables.CUSTOMERS;
import static com.meatrics.generated.Tables.IMPORTED_LINE_ITEMS;
import static com.meatrics.generated.Tables.PRODUCT_COSTS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.field;

/**
 * Repository for ImportedLineItem entities using jOOQ
 */
@Repository
public class ImportedLineItemRepository {

    private final DSLContext dsl;

    public ImportedLineItemRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Find line item by ID
     */
    public Optional<ImportedLineItem> findById(Long id) {
        return dsl.selectFrom(IMPORTED_LINE_ITEMS)
                .where(IMPORTED_LINE_ITEMS.LINE_ID.eq(id))
                .fetchOptional(this::mapToEntity);
    }

    /**
     * Find all line items by filename
     */
    public List<ImportedLineItem> findByFilename(String filename) {
        return dsl.selectFrom(IMPORTED_LINE_ITEMS)
                .where(IMPORTED_LINE_ITEMS.FILENAME.eq(filename))
                .fetch(this::mapToEntity);
    }

    /**
     * Find all line items by import ID
     */
    public List<ImportedLineItem> findByImportId(Long importId) {
        return dsl.selectFrom(IMPORTED_LINE_ITEMS)
                .where(IMPORTED_LINE_ITEMS.IMPORT_ID.eq(importId))
                .fetch(this::mapToEntity);
    }

    /**
     * Find all line items
     */
    public List<ImportedLineItem> findAll() {
        return dsl.selectFrom(IMPORTED_LINE_ITEMS)
                .orderBy(IMPORTED_LINE_ITEMS.IMPORT_DATE.desc())
                .fetch(this::mapToEntity);
    }

    /**
     * Find line items by date range
     */
    public List<ImportedLineItem> findByDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        // Build the query based on provided date parameters
        if (startDate != null && endDate != null) {
            return dsl.selectFrom(IMPORTED_LINE_ITEMS)
                    .where(IMPORTED_LINE_ITEMS.TRANSACTION_DATE.between(startDate, endDate))
                    .orderBy(IMPORTED_LINE_ITEMS.TRANSACTION_DATE.desc())
                    .fetch(this::mapToEntity);
        } else if (startDate != null) {
            return dsl.selectFrom(IMPORTED_LINE_ITEMS)
                    .where(IMPORTED_LINE_ITEMS.TRANSACTION_DATE.greaterOrEqual(startDate))
                    .orderBy(IMPORTED_LINE_ITEMS.TRANSACTION_DATE.desc())
                    .fetch(this::mapToEntity);
        } else if (endDate != null) {
            return dsl.selectFrom(IMPORTED_LINE_ITEMS)
                    .where(IMPORTED_LINE_ITEMS.TRANSACTION_DATE.lessOrEqual(endDate))
                    .orderBy(IMPORTED_LINE_ITEMS.TRANSACTION_DATE.desc())
                    .fetch(this::mapToEntity);
        } else {
            return findAll();
        }
    }

    /**
     * Save a line item
     */
    public ImportedLineItem save(ImportedLineItem lineItem) {
        if (lineItem.getLineId() == null) {
            // Insert new line item
            var record = dsl.insertInto(IMPORTED_LINE_ITEMS)
                    .set(IMPORTED_LINE_ITEMS.IMPORT_ID, lineItem.getImportId())
                    .set(IMPORTED_LINE_ITEMS.FILENAME, lineItem.getFilename())
                    .set(IMPORTED_LINE_ITEMS.CUSTOMER_CODE, lineItem.getCustomerCode())
                    .set(IMPORTED_LINE_ITEMS.CUSTOMER_NAME, lineItem.getCustomerName())
                    .set(IMPORTED_LINE_ITEMS.INVOICE_NUMBER, lineItem.getInvoiceNumber())
                    .set(IMPORTED_LINE_ITEMS.TRANSACTION_DATE, lineItem.getTransactionDate())
                    .set(IMPORTED_LINE_ITEMS.PRODUCT_CODE, lineItem.getProductCode())
                    .set(IMPORTED_LINE_ITEMS.PRODUCT_DESCRIPTION, lineItem.getProductDescription())
                    .set(IMPORTED_LINE_ITEMS.QUANTITY, lineItem.getQuantity())
                    .set(IMPORTED_LINE_ITEMS.AMOUNT, lineItem.getAmount())
                    .set(IMPORTED_LINE_ITEMS.COST, lineItem.getCost())
                    .set(IMPORTED_LINE_ITEMS.REF1, lineItem.getRef1())
                    .set(IMPORTED_LINE_ITEMS.REF2, lineItem.getRef2())
                    .set(IMPORTED_LINE_ITEMS.REF3, lineItem.getRef3())
                    .set(IMPORTED_LINE_ITEMS.OUTSTANDING_AMOUNT, lineItem.getOutstandingAmount())
                    .returning()
                    .fetchOne();

            lineItem.setLineId(record.getLineId());
            lineItem.setImportDate(record.getImportDate());
        }
        return lineItem;
    }

    /**
     * Save multiple line items in batch
     */
    public void saveAll(List<ImportedLineItem> lineItems) {
        lineItems.forEach(this::save);
    }

    /**
     * Delete a line item by ID
     */
    public void deleteById(Long id) {
        dsl.deleteFrom(IMPORTED_LINE_ITEMS)
                .where(IMPORTED_LINE_ITEMS.LINE_ID.eq(id))
                .execute();
    }

    /**
     * Delete all line items for a filename
     */
    public void deleteByFilename(String filename) {
        dsl.deleteFrom(IMPORTED_LINE_ITEMS)
                .where(IMPORTED_LINE_ITEMS.FILENAME.eq(filename))
                .execute();
    }

    /**
     * Delete all line items for an import ID
     */
    public void deleteByImportId(Long importId) {
        dsl.deleteFrom(IMPORTED_LINE_ITEMS)
                .where(IMPORTED_LINE_ITEMS.IMPORT_ID.eq(importId))
                .execute();
    }

    /**
     * Count line items by filename
     */
    public int countByFilename(String filename) {
        return dsl.selectCount()
                .from(IMPORTED_LINE_ITEMS)
                .where(IMPORTED_LINE_ITEMS.FILENAME.eq(filename))
                .fetchOne(0, int.class);
    }

    /**
     * Get customer rating report data for a date range
     * Aggregates sales data by customer and joins with customer ratings
     */
    public List<CustomerRatingReportDTO> getCustomerRatingReport(LocalDate startDate, LocalDate endDate) {
        var totalCost = sum(coalesce(IMPORTED_LINE_ITEMS.COST, BigDecimal.ZERO)).as("total_cost");
        var totalAmount = sum(coalesce(IMPORTED_LINE_ITEMS.AMOUNT, BigDecimal.ZERO)).as("total_amount");

        return dsl.select(
                        IMPORTED_LINE_ITEMS.CUSTOMER_CODE,
                        IMPORTED_LINE_ITEMS.CUSTOMER_NAME,
                        totalCost,
                        totalAmount,
                        CUSTOMERS.CUSTOMER_RATING
                )
                .from(IMPORTED_LINE_ITEMS)
                .leftJoin(CUSTOMERS).on(IMPORTED_LINE_ITEMS.CUSTOMER_CODE.eq(CUSTOMERS.CUSTOMER_CODE))
                .where(IMPORTED_LINE_ITEMS.TRANSACTION_DATE.between(startDate, endDate))
                .groupBy(
                        IMPORTED_LINE_ITEMS.CUSTOMER_CODE,
                        IMPORTED_LINE_ITEMS.CUSTOMER_NAME,
                        CUSTOMERS.CUSTOMER_RATING
                )
                .orderBy(totalAmount.desc())
                .fetch(record -> {
                    String customerCode = record.get(IMPORTED_LINE_ITEMS.CUSTOMER_CODE);
                    String customerName = record.get(IMPORTED_LINE_ITEMS.CUSTOMER_NAME);
                    BigDecimal cost = record.get(totalCost, BigDecimal.class);
                    BigDecimal amount = record.get(totalAmount, BigDecimal.class);
                    String ratingString = record.get(CUSTOMERS.CUSTOMER_RATING);

                    // Calculate GP%
                    BigDecimal gpPercentage = CustomerRatingReportDTO.calculateGPPercentage(amount, cost);

                    // Create DTO
                    CustomerRatingReportDTO dto = new CustomerRatingReportDTO(
                            customerCode,
                            customerName,
                            cost != null ? cost : BigDecimal.ZERO,
                            amount != null ? amount : BigDecimal.ZERO,
                            gpPercentage
                    );

                    // Parse the rating string into separate fields
                    CustomerRatingReportDTO.parseRating(dto, ratingString);

                    return dto;
                });
    }

    /**
     * Get cost report data for a specific import file
     * Shows line items where the line item cost price is lower than the standard cost
     *
     * @param filename The import filename to filter by
     * @return List of cost report DTOs ordered by transaction date desc, customer name, product code
     */
    public List<CostReportDTO> getCostReport(String filename) {
        // Calculate line_item_cost_price = cost / quantity
        var lineItemCostPrice = field("({0} / NULLIF({1}, 0))",
                BigDecimal.class,
                IMPORTED_LINE_ITEMS.COST,
                IMPORTED_LINE_ITEMS.QUANTITY).as("line_item_cost_price");

        // Calculate difference = stdcost - line_item_cost_price
        var difference = field("({0} - ({1} / NULLIF({2}, 0)))",
                BigDecimal.class,
                PRODUCT_COSTS.STANDARD_COST,
                IMPORTED_LINE_ITEMS.COST,
                IMPORTED_LINE_ITEMS.QUANTITY).as("difference");

        return dsl.select(
                        IMPORTED_LINE_ITEMS.PRODUCT_CODE,
                        IMPORTED_LINE_ITEMS.PRODUCT_DESCRIPTION,
                        IMPORTED_LINE_ITEMS.CUSTOMER_NAME,
                        IMPORTED_LINE_ITEMS.INVOICE_NUMBER,
                        IMPORTED_LINE_ITEMS.TRANSACTION_DATE,
                        IMPORTED_LINE_ITEMS.QUANTITY,
                        IMPORTED_LINE_ITEMS.COST,
                        PRODUCT_COSTS.STANDARD_COST,
                        lineItemCostPrice,
                        difference
                )
                .from(IMPORTED_LINE_ITEMS)
                .leftJoin(PRODUCT_COSTS).on(IMPORTED_LINE_ITEMS.PRODUCT_CODE.eq(PRODUCT_COSTS.PRODUCT_CODE))
                .where(IMPORTED_LINE_ITEMS.FILENAME.eq(filename))
                .and(IMPORTED_LINE_ITEMS.QUANTITY.gt(BigDecimal.ZERO)) // Exclude zero or negative quantities
                .and(PRODUCT_COSTS.STANDARD_COST.isNotNull()) // Only include products with standard cost
                .and(field("({0} / NULLIF({1}, 0)) < {2}",
                        Boolean.class,
                        IMPORTED_LINE_ITEMS.COST,
                        IMPORTED_LINE_ITEMS.QUANTITY,
                        PRODUCT_COSTS.STANDARD_COST)) // Filter: line_item_cost_price < stdcost
                .orderBy(
                        IMPORTED_LINE_ITEMS.TRANSACTION_DATE.desc(),
                        IMPORTED_LINE_ITEMS.CUSTOMER_NAME.asc(),
                        IMPORTED_LINE_ITEMS.PRODUCT_CODE.asc()
                )
                .fetch(record -> new CostReportDTO(
                        record.get(IMPORTED_LINE_ITEMS.PRODUCT_CODE),
                        record.get(IMPORTED_LINE_ITEMS.PRODUCT_DESCRIPTION),
                        record.get(IMPORTED_LINE_ITEMS.CUSTOMER_NAME),
                        record.get(IMPORTED_LINE_ITEMS.INVOICE_NUMBER),
                        record.get(IMPORTED_LINE_ITEMS.TRANSACTION_DATE),
                        record.get(IMPORTED_LINE_ITEMS.QUANTITY),
                        record.get(IMPORTED_LINE_ITEMS.COST),
                        record.get(PRODUCT_COSTS.STANDARD_COST)
                ));
    }

    /**
     * Map database record to entity
     */
    private ImportedLineItem mapToEntity(org.jooq.Record record) {
        var lineItem = new ImportedLineItem();
        lineItem.setLineId(record.get(IMPORTED_LINE_ITEMS.LINE_ID));
        lineItem.setImportId(record.get(IMPORTED_LINE_ITEMS.IMPORT_ID));
        lineItem.setFilename(record.get(IMPORTED_LINE_ITEMS.FILENAME));
        lineItem.setCustomerCode(record.get(IMPORTED_LINE_ITEMS.CUSTOMER_CODE));
        lineItem.setCustomerName(record.get(IMPORTED_LINE_ITEMS.CUSTOMER_NAME));
        lineItem.setInvoiceNumber(record.get(IMPORTED_LINE_ITEMS.INVOICE_NUMBER));
        lineItem.setTransactionDate(record.get(IMPORTED_LINE_ITEMS.TRANSACTION_DATE));
        lineItem.setProductCode(record.get(IMPORTED_LINE_ITEMS.PRODUCT_CODE));
        lineItem.setProductDescription(record.get(IMPORTED_LINE_ITEMS.PRODUCT_DESCRIPTION));
        lineItem.setQuantity(record.get(IMPORTED_LINE_ITEMS.QUANTITY));
        lineItem.setAmount(record.get(IMPORTED_LINE_ITEMS.AMOUNT));
        lineItem.setCost(record.get(IMPORTED_LINE_ITEMS.COST));
        lineItem.setRef1(record.get(IMPORTED_LINE_ITEMS.REF1));
        lineItem.setRef2(record.get(IMPORTED_LINE_ITEMS.REF2));
        lineItem.setRef3(record.get(IMPORTED_LINE_ITEMS.REF3));
        lineItem.setOutstandingAmount(record.get(IMPORTED_LINE_ITEMS.OUTSTANDING_AMOUNT));
        lineItem.setImportDate(record.get(IMPORTED_LINE_ITEMS.IMPORT_DATE));
        return lineItem;
    }
}
