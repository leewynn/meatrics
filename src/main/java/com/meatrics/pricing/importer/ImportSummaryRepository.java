package com.meatrics.pricing.importer;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.meatrics.generated.Tables.IMPORT_SUMMARY;

/**
 * Repository for ImportSummary entities using jOOQ
 */
@Repository
public class ImportSummaryRepository {

    private final DSLContext dsl;

    public ImportSummaryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Find import summary by ID
     */
    public Optional<ImportSummary> findById(Long id) {
        return dsl.selectFrom(IMPORT_SUMMARY)
                .where(IMPORT_SUMMARY.IMPORT_ID.eq(id))
                .fetchOptional(this::mapToEntity);
    }

    /**
     * Find import summary by filename
     */
    public Optional<ImportSummary> findByFilename(String filename) {
        return dsl.selectFrom(IMPORT_SUMMARY)
                .where(IMPORT_SUMMARY.FILENAME.eq(filename))
                .fetchOptional(this::mapToEntity);
    }

    /**
     * Find all import summaries ordered by import date descending
     */
    public List<ImportSummary> findAll() {
        return dsl.selectFrom(IMPORT_SUMMARY)
                .orderBy(IMPORT_SUMMARY.IMPORT_DATE.desc())
                .fetch(this::mapToEntity);
    }

    /**
     * Save an import summary
     */
    public ImportSummary save(ImportSummary importSummary) {
        if (importSummary.getImportId() == null) {
            // Insert new import summary
            var record = dsl.insertInto(IMPORT_SUMMARY)
                    .set(IMPORT_SUMMARY.FILENAME, importSummary.getFilename())
                    .set(IMPORT_SUMMARY.RECORD_COUNT, importSummary.getRecordCount())
                    .set(IMPORT_SUMMARY.STATUS, importSummary.getStatus())
                    .returning()
                    .fetchOne();

            importSummary.setImportId(record.getImportId());
            importSummary.setImportDate(record.getImportDate());
        } else {
            // Update existing import summary
            dsl.update(IMPORT_SUMMARY)
                    .set(IMPORT_SUMMARY.FILENAME, importSummary.getFilename())
                    .set(IMPORT_SUMMARY.RECORD_COUNT, importSummary.getRecordCount())
                    .set(IMPORT_SUMMARY.STATUS, importSummary.getStatus())
                    .where(IMPORT_SUMMARY.IMPORT_ID.eq(importSummary.getImportId()))
                    .execute();
        }
        return importSummary;
    }

    /**
     * Delete an import summary by ID (will cascade delete line items)
     */
    public void deleteById(Long id) {
        dsl.deleteFrom(IMPORT_SUMMARY)
                .where(IMPORT_SUMMARY.IMPORT_ID.eq(id))
                .execute();
    }

    /**
     * Delete an import summary by filename (will cascade delete line items)
     */
    public void deleteByFilename(String filename) {
        dsl.deleteFrom(IMPORT_SUMMARY)
                .where(IMPORT_SUMMARY.FILENAME.eq(filename))
                .execute();
    }

    /**
     * Map database record to entity
     */
    private ImportSummary mapToEntity(org.jooq.Record record) {
        return new ImportSummary(
                record.get(IMPORT_SUMMARY.IMPORT_ID),
                record.get(IMPORT_SUMMARY.FILENAME),
                record.get(IMPORT_SUMMARY.RECORD_COUNT),
                record.get(IMPORT_SUMMARY.IMPORT_DATE),
                record.get(IMPORT_SUMMARY.STATUS)
        );
    }
}
