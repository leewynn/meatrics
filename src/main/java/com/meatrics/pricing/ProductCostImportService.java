package com.meatrics.pricing;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for importing product cost data from Excel files
 */
@Service
public class ProductCostImportService {

    private final ProductCostRepository productCostRepository;
    private final CostImportSummaryRepository costImportSummaryRepository;

    public ProductCostImportService(ProductCostRepository productCostRepository,
                                   CostImportSummaryRepository costImportSummaryRepository) {
        this.productCostRepository = productCostRepository;
        this.costImportSummaryRepository = costImportSummaryRepository;
    }

    /**
     * Import product costs from Excel file
     * Strategy: Import new data first, then delete old data only if import is successful
     */
    @Transactional
    public CostImportSummary importFromExcel(InputStream inputStream, String filename) {
        CostImportSummary summary = new CostImportSummary();
        summary.setFilename(filename);

        try {
            // Parse Excel file
            List<ProductCost> products = parseExcelFile(inputStream, filename);

            // Save all products
            for (ProductCost product : products) {
                productCostRepository.save(product);
            }

            // Calculate statistics
            int activeCount = (int) products.stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).count();
            int withCostCount = (int) products.stream()
                    .filter(p -> p.getStandardCost() != null && p.getStandardCost().compareTo(BigDecimal.ZERO) > 0)
                    .count();

            summary.setTotalProducts(products.size());
            summary.setActiveProducts(activeCount);
            summary.setProductsWithCost(withCostCount);
            summary.setImportStatus("SUCCESS");

            // Save summary
            costImportSummaryRepository.save(summary);

            return summary;

        } catch (Exception e) {
            summary.setImportStatus("FAILED");
            summary.setErrorMessage(e.getMessage());
            costImportSummaryRepository.save(summary);
            throw new RuntimeException("Failed to import product costs: " + e.getMessage(), e);
        }
    }

    /**
     * Delete all existing product cost data
     * Should be called after successful import
     */
    @Transactional
    public int clearOldData() {
        return productCostRepository.deleteAll();
    }

    /**
     * Get all product costs
     */
    public List<ProductCost> getAllProductCosts() {
        return productCostRepository.findAll();
    }

    /**
     * Get all cost import summaries
     */
    public List<CostImportSummary> getAllCostImportSummaries() {
        return costImportSummaryRepository.findAllOrderByImportDateDesc();
    }

    /**
     * Get statistics about current product costs
     */
    public CostImportStats getStats() {
        return new CostImportStats(
                productCostRepository.count(),
                productCostRepository.countActive(),
                productCostRepository.countWithStandardCost()
        );
    }

    private List<ProductCost> parseExcelFile(InputStream inputStream, String filename) throws Exception {
        List<ProductCost> products = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            // Skip header row, start from row 1
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                ProductCost product = parseProductRow(row, filename);
                if (product != null && product.getProductCode() != null && !product.getProductCode().trim().isEmpty()) {
                    products.add(product);
                }
            }
        }

        return products;
    }

    private ProductCost parseProductRow(Row row, String filename) {
        ProductCost product = new ProductCost();
        product.setImportFilename(filename);

        // Column 0 (A): Stockcode
        product.setProductCode(getCellValueAsString(row.getCell(0)));

        // Column 1 (B): Description
        product.setDescription(getCellValueAsString(row.getCell(1)));

        // Sell prices (Columns 2-11, C-L)
        product.setSellPrice1(getCellValueAsBigDecimal(row.getCell(2)));
        product.setSellPrice2(getCellValueAsBigDecimal(row.getCell(3)));
        product.setSellPrice3(getCellValueAsBigDecimal(row.getCell(4)));
        product.setSellPrice4(getCellValueAsBigDecimal(row.getCell(5)));
        product.setSellPrice5(getCellValueAsBigDecimal(row.getCell(6)));
        product.setSellPrice6(getCellValueAsBigDecimal(row.getCell(7)));
        product.setSellPrice7(getCellValueAsBigDecimal(row.getCell(8)));
        product.setSellPrice8(getCellValueAsBigDecimal(row.getCell(9)));
        product.setSellPrice9(getCellValueAsBigDecimal(row.getCell(10)));
        product.setSellPrice10(getCellValueAsBigDecimal(row.getCell(11)));

        // Column 12 (M): Latestcost
        product.setLatestCost(getCellValueAsBigDecimal(row.getCell(12)));

        // Column 13 (N): Avecost
        product.setAverageCost(getCellValueAsBigDecimal(row.getCell(13)));

        // Column 14-16 (O-Q): Stock levels
        product.setMinStock(getCellValueAsBigDecimal(row.getCell(14)));
        product.setMaxStock(getCellValueAsBigDecimal(row.getCell(15)));
        product.setBinCode(getCellValueAsString(row.getCell(16)));

        // Column 21 (V): Isactive
        String isActive = getCellValueAsString(row.getCell(21));
        product.setIsActive("Y".equalsIgnoreCase(isActive));

        // Column 22-23 (W-X): Weight and Cubic
        product.setWeight(getCellValueAsBigDecimal(row.getCell(22)));
        product.setCubic(getCellValueAsBigDecimal(row.getCell(23)));

        // Column 24 (Y): Pack (unit of measure)
        product.setUnitOfMeasure(getCellValueAsString(row.getCell(24)));

        // Column 25 (Z): Stdcost - PRIMARY COST FIELD
        product.setStandardCost(getCellValueAsBigDecimal(row.getCell(25)));

        // Column 26-27 ([, \): Tax rates
        product.setSalesTaxRate(getCellValueAsBigDecimal(row.getCell(26)));
        product.setPurchaseTaxRate(getCellValueAsBigDecimal(row.getCell(27)));

        // Column 29 (^): Suppliercost
        product.setSupplierCost(getCellValueAsBigDecimal(row.getCell(29)));

        // Column 32-36 (a-e): Classification
        product.setPrimaryGroup(getCellValueAsString(row.getCell(32)));
        product.setSecondaryGroup(getCellValueAsString(row.getCell(33)));
        product.setProductClass(getCellValueAsString(row.getCell(34)));
        product.setTertiaryGroup(getCellValueAsString(row.getCell(35)));
        product.setSupplierName(getCellValueAsString(row.getCell(36)));

        // GL codes (Columns 19, 20, 28)
        product.setSalesGlCode(getCellValueAsString(row.getCell(19)));
        product.setPurchaseGlCode(getCellValueAsString(row.getCell(20)));
        product.setCosGlCode(getCellValueAsString(row.getCell(28)));

        return product;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }

    private BigDecimal getCellValueAsBigDecimal(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING:
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty()) {
                        return null;
                    }
                    // Remove currency symbols and commas
                    value = value.replace("$", "").replace(",", "");
                    return new BigDecimal(value);
                case FORMULA:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Statistics holder class
     */
    public static class CostImportStats {
        private final int totalProducts;
        private final int activeProducts;
        private final int productsWithCost;

        public CostImportStats(int totalProducts, int activeProducts, int productsWithCost) {
            this.totalProducts = totalProducts;
            this.activeProducts = activeProducts;
            this.productsWithCost = productsWithCost;
        }

        public int getTotalProducts() {
            return totalProducts;
        }

        public int getActiveProducts() {
            return activeProducts;
        }

        public int getProductsWithCost() {
            return productsWithCost;
        }
    }
}
