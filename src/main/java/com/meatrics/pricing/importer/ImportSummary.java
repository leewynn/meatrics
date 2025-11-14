package com.meatrics.pricing.importer;

import java.time.LocalDateTime;

/**
 * Import summary entity representing the import_summary table
 */
public class ImportSummary {
    private Long importId;
    private String filename;
    private Integer recordCount;
    private LocalDateTime importDate;
    private String status;

    public ImportSummary() {
    }

    public ImportSummary(Long importId, String filename, Integer recordCount, LocalDateTime importDate, String status) {
        this.importId = importId;
        this.filename = filename;
        this.recordCount = recordCount;
        this.importDate = importDate;
        this.status = status;
    }

    public Long getImportId() {
        return importId;
    }

    public void setImportId(Long importId) {
        this.importId = importId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public LocalDateTime getImportDate() {
        return importDate;
    }

    public void setImportDate(LocalDateTime importDate) {
        this.importDate = importDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
