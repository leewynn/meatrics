package com.meatrics.pricing.importer;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.meatrics.generated.Tables.COST_IMPORT_SUMMARY;

/**
 * Repository for cost import summary data access
 */
@Repository
public class CostImportSummaryRepository {

    private final DSLContext dsl;

    public CostImportSummaryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Save a cost import summary
     */
    public Long save(CostImportSummary summary) {
        return dsl.insertInto(COST_IMPORT_SUMMARY)
                .set(COST_IMPORT_SUMMARY.FILENAME, summary.getFilename())
                .set(COST_IMPORT_SUMMARY.TOTAL_PRODUCTS, summary.getTotalProducts())
                .set(COST_IMPORT_SUMMARY.ACTIVE_PRODUCTS, summary.getActiveProducts())
                .set(COST_IMPORT_SUMMARY.PRODUCTS_WITH_COST, summary.getProductsWithCost())
                .set(COST_IMPORT_SUMMARY.IMPORT_STATUS, summary.getImportStatus())
                .set(COST_IMPORT_SUMMARY.ERROR_MESSAGE, summary.getErrorMessage())
                .returningResult(COST_IMPORT_SUMMARY.COST_IMPORT_ID)
                .fetchOne()
                .value1();
    }

    /**
     * Get all cost import summaries ordered by import date desc
     */
    public List<CostImportSummary> findAllOrderByImportDateDesc() {
        return dsl.selectFrom(COST_IMPORT_SUMMARY)
                .orderBy(COST_IMPORT_SUMMARY.IMPORT_DATE.desc())
                .fetch(this::mapToCostImportSummary);
    }

    private CostImportSummary mapToCostImportSummary(org.jooq.Record record) {
        CostImportSummary summary = new CostImportSummary();
        summary.setCostImportId(record.get(COST_IMPORT_SUMMARY.COST_IMPORT_ID));
        summary.setFilename(record.get(COST_IMPORT_SUMMARY.FILENAME));
        summary.setImportDate(record.get(COST_IMPORT_SUMMARY.IMPORT_DATE));
        summary.setTotalProducts(record.get(COST_IMPORT_SUMMARY.TOTAL_PRODUCTS));
        summary.setActiveProducts(record.get(COST_IMPORT_SUMMARY.ACTIVE_PRODUCTS));
        summary.setProductsWithCost(record.get(COST_IMPORT_SUMMARY.PRODUCTS_WITH_COST));
        summary.setImportStatus(record.get(COST_IMPORT_SUMMARY.IMPORT_STATUS));
        summary.setErrorMessage(record.get(COST_IMPORT_SUMMARY.ERROR_MESSAGE));
        return summary;
    }
}
