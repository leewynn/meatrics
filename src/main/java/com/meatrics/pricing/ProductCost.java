package com.meatrics.pricing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product cost entity representing the product_costs table
 */
public class ProductCost {
    private Long productCostId;
    private String productCode;
    private String description;

    // Primary cost field
    private BigDecimal standardCost;

    // Additional cost fields
    private BigDecimal latestCost;
    private BigDecimal averageCost;
    private BigDecimal supplierCost;

    // Sell prices
    private BigDecimal sellPrice1;
    private BigDecimal sellPrice2;
    private BigDecimal sellPrice3;
    private BigDecimal sellPrice4;
    private BigDecimal sellPrice5;
    private BigDecimal sellPrice6;
    private BigDecimal sellPrice7;
    private BigDecimal sellPrice8;
    private BigDecimal sellPrice9;
    private BigDecimal sellPrice10;

    // Product attributes
    private Boolean isActive;
    private String unitOfMeasure;
    private BigDecimal weight;
    private BigDecimal cubic;
    private BigDecimal minStock;
    private BigDecimal maxStock;
    private String binCode;

    // Classification
    private String primaryGroup;
    private String secondaryGroup;
    private String tertiaryGroup;
    private String productClass;
    private String supplierName;

    // GL codes
    private String salesGlCode;
    private String purchaseGlCode;
    private String cosGlCode;

    // Tax rates
    private BigDecimal salesTaxRate;
    private BigDecimal purchaseTaxRate;

    // Metadata
    private LocalDateTime importDate;
    private String importFilename;

    public ProductCost() {
    }

    // Getters and setters
    public Long getProductCostId() {
        return productCostId;
    }

    public void setProductCostId(Long productCostId) {
        this.productCostId = productCostId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getStandardCost() {
        return standardCost;
    }

    public void setStandardCost(BigDecimal standardCost) {
        this.standardCost = standardCost;
    }

    public BigDecimal getLatestCost() {
        return latestCost;
    }

    public void setLatestCost(BigDecimal latestCost) {
        this.latestCost = latestCost;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public BigDecimal getSupplierCost() {
        return supplierCost;
    }

    public void setSupplierCost(BigDecimal supplierCost) {
        this.supplierCost = supplierCost;
    }

    public BigDecimal getSellPrice1() {
        return sellPrice1;
    }

    public void setSellPrice1(BigDecimal sellPrice1) {
        this.sellPrice1 = sellPrice1;
    }

    public BigDecimal getSellPrice2() {
        return sellPrice2;
    }

    public void setSellPrice2(BigDecimal sellPrice2) {
        this.sellPrice2 = sellPrice2;
    }

    public BigDecimal getSellPrice3() {
        return sellPrice3;
    }

    public void setSellPrice3(BigDecimal sellPrice3) {
        this.sellPrice3 = sellPrice3;
    }

    public BigDecimal getSellPrice4() {
        return sellPrice4;
    }

    public void setSellPrice4(BigDecimal sellPrice4) {
        this.sellPrice4 = sellPrice4;
    }

    public BigDecimal getSellPrice5() {
        return sellPrice5;
    }

    public void setSellPrice5(BigDecimal sellPrice5) {
        this.sellPrice5 = sellPrice5;
    }

    public BigDecimal getSellPrice6() {
        return sellPrice6;
    }

    public void setSellPrice6(BigDecimal sellPrice6) {
        this.sellPrice6 = sellPrice6;
    }

    public BigDecimal getSellPrice7() {
        return sellPrice7;
    }

    public void setSellPrice7(BigDecimal sellPrice7) {
        this.sellPrice7 = sellPrice7;
    }

    public BigDecimal getSellPrice8() {
        return sellPrice8;
    }

    public void setSellPrice8(BigDecimal sellPrice8) {
        this.sellPrice8 = sellPrice8;
    }

    public BigDecimal getSellPrice9() {
        return sellPrice9;
    }

    public void setSellPrice9(BigDecimal sellPrice9) {
        this.sellPrice9 = sellPrice9;
    }

    public BigDecimal getSellPrice10() {
        return sellPrice10;
    }

    public void setSellPrice10(BigDecimal sellPrice10) {
        this.sellPrice10 = sellPrice10;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public BigDecimal getCubic() {
        return cubic;
    }

    public void setCubic(BigDecimal cubic) {
        this.cubic = cubic;
    }

    public BigDecimal getMinStock() {
        return minStock;
    }

    public void setMinStock(BigDecimal minStock) {
        this.minStock = minStock;
    }

    public BigDecimal getMaxStock() {
        return maxStock;
    }

    public void setMaxStock(BigDecimal maxStock) {
        this.maxStock = maxStock;
    }

    public String getBinCode() {
        return binCode;
    }

    public void setBinCode(String binCode) {
        this.binCode = binCode;
    }

    public String getPrimaryGroup() {
        return primaryGroup;
    }

    public void setPrimaryGroup(String primaryGroup) {
        this.primaryGroup = primaryGroup;
    }

    public String getSecondaryGroup() {
        return secondaryGroup;
    }

    public void setSecondaryGroup(String secondaryGroup) {
        this.secondaryGroup = secondaryGroup;
    }

    public String getTertiaryGroup() {
        return tertiaryGroup;
    }

    public void setTertiaryGroup(String tertiaryGroup) {
        this.tertiaryGroup = tertiaryGroup;
    }

    public String getProductClass() {
        return productClass;
    }

    public void setProductClass(String productClass) {
        this.productClass = productClass;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getSalesGlCode() {
        return salesGlCode;
    }

    public void setSalesGlCode(String salesGlCode) {
        this.salesGlCode = salesGlCode;
    }

    public String getPurchaseGlCode() {
        return purchaseGlCode;
    }

    public void setPurchaseGlCode(String purchaseGlCode) {
        this.purchaseGlCode = purchaseGlCode;
    }

    public String getCosGlCode() {
        return cosGlCode;
    }

    public void setCosGlCode(String cosGlCode) {
        this.cosGlCode = cosGlCode;
    }

    public BigDecimal getSalesTaxRate() {
        return salesTaxRate;
    }

    public void setSalesTaxRate(BigDecimal salesTaxRate) {
        this.salesTaxRate = salesTaxRate;
    }

    public BigDecimal getPurchaseTaxRate() {
        return purchaseTaxRate;
    }

    public void setPurchaseTaxRate(BigDecimal purchaseTaxRate) {
        this.purchaseTaxRate = purchaseTaxRate;
    }

    public LocalDateTime getImportDate() {
        return importDate;
    }

    public void setImportDate(LocalDateTime importDate) {
        this.importDate = importDate;
    }

    public String getImportFilename() {
        return importFilename;
    }

    public void setImportFilename(String importFilename) {
        this.importFilename = importFilename;
    }

    /**
     * Get formatted standard cost
     */
    public String getStandardCostFormatted() {
        if (standardCost == null) {
            return "$0.00";
        }
        return String.format("$%.2f", standardCost);
    }
}
