package com.meatrics.pricing.importer;

import java.time.LocalDateTime;

/**
 * Cost import summary entity representing the cost_import_summary table
 */
public class CostImportSummary {
    private Long costImportId;
    private String filename;
    private LocalDateTime importDate;
    private Integer totalProducts;
    private Integer activeProducts;
    private Integer productsWithCost;
    private String importStatus;
    private String errorMessage;

    public CostImportSummary() {
    }

    // Getters and setters
    public Long getCostImportId() {
        return costImportId;
    }

    public void setCostImportId(Long costImportId) {
        this.costImportId = costImportId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public LocalDateTime getImportDate() {
        return importDate;
    }

    public void setImportDate(LocalDateTime importDate) {
        this.importDate = importDate;
    }

    public Integer getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(Integer totalProducts) {
        this.totalProducts = totalProducts;
    }

    public Integer getActiveProducts() {
        return activeProducts;
    }

    public void setActiveProducts(Integer activeProducts) {
        this.activeProducts = activeProducts;
    }

    public Integer getProductsWithCost() {
        return productsWithCost;
    }

    public void setProductsWithCost(Integer productsWithCost) {
        this.productsWithCost = productsWithCost;
    }

    public String getImportStatus() {
        return importStatus;
    }

    public void setImportStatus(String importStatus) {
        this.importStatus = importStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
