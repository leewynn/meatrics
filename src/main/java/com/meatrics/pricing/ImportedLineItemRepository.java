package com.meatrics.pricing;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.meatrics.generated.Tables.IMPORTED_LINE_ITEMS;

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
