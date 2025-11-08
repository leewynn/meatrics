package com.meatrics.pricing;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Service for importing pricing data from Excel files
 */
@Service
public class PricingImportService {

    private static final Logger log = LoggerFactory.getLogger(PricingImportService.class);

    private final ImportSummaryRepository importSummaryRepository;
    private final ImportedLineItemRepository importedLineItemRepository;
    private final CustomerRepository customerRepository;
    private final CustomerRatingService customerRatingService;
    private final GroupedLineItemRepository groupedLineItemRepository;

    // Temporary storage for uploaded files awaiting processing
    private final Map<String, File> uploadedFiles = new HashMap<>();

    public PricingImportService(ImportSummaryRepository importSummaryRepository,
                                ImportedLineItemRepository importedLineItemRepository,
                                CustomerRepository customerRepository,
                                CustomerRatingService customerRatingService,
                                GroupedLineItemRepository groupedLineItemRepository) {
        this.importSummaryRepository = importSummaryRepository;
        this.importedLineItemRepository = importedLineItemRepository;
        this.customerRepository = customerRepository;
        this.customerRatingService = customerRatingService;
        this.groupedLineItemRepository = groupedLineItemRepository;
    }

    /**
     * Store uploaded file for later processing
     */
    public void storeUploadedFile(String filename, File file) {
        uploadedFiles.put(filename, file);
        log.info("Stored file for processing: {}", filename);
    }

    /**
     * Remove uploaded file from temporary storage
     */
    public void removeUploadedFile(String filename) {
        uploadedFiles.remove(filename);
        log.info("Removed file from temporary storage: {}", filename);
    }

    /**
     * Get list of uploaded files awaiting processing
     */
    public Set<String> getUploadedFileNames() {
        return uploadedFiles.keySet();
    }

    /**
     * Import all uploaded files
     */
    @Transactional
    public int importAllUploadedFiles() throws IOException {
        int totalRecords = 0;

        for (Map.Entry<String, File> entry : uploadedFiles.entrySet()) {
            String filename = entry.getKey();
            File file = entry.getValue();

            try (InputStream inputStream = new FileInputStream(file)) {
                int recordCount = importFromInputStream(inputStream, filename);
                totalRecords += recordCount;

                log.info("Imported {} records from file: {}", recordCount, filename);
            }
        }

        // Clear uploaded files after import
        uploadedFiles.clear();

        return totalRecords;
    }

    /**
     * Import data from Excel file input stream
     */
    @Transactional
    public int importFromInputStream(InputStream inputStream, String filename) throws IOException {
        // Create import summary record first
        ImportSummary summary = new ImportSummary();
        summary.setFilename(filename);
        summary.setRecordCount(0);
        summary.setStatus("PROCESSING");
        summary = importSummaryRepository.save(summary);

        // Parse Excel file
        List<ImportedLineItem> lineItems = readExcelFile(inputStream, filename, summary.getImportId());

        // Save all line items
        importedLineItemRepository.saveAll(lineItems);

        // Create/update customer records from imported line items
        createOrUpdateCustomers(lineItems);

        // Update import summary with final count and status
        summary.setRecordCount(lineItems.size());
        summary.setStatus("COMPLETED");
        importSummaryRepository.save(summary);

        // Recalculate customer ratings in background
        customerRatingService.recalculateAndSaveAllCustomerRatings();

        log.info("Imported {} line items from {}", lineItems.size(), filename);
        return lineItems.size();
    }

    /**
     * Read Excel file and parse imported line items
     * TODO: Adjust column mapping based on your actual Excel format
     */
    private List<ImportedLineItem> readExcelFile(InputStream inputStream, String filename, Long importId) throws IOException {
        List<ImportedLineItem> lineItems = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            var clientCode = "";
            var clientName = "";
            // Skip header row, start from row 1
            for (int i = 6; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (shouldSkipLine(row)) continue;
                if (endParsing(row)) break;
                if(isNumeric(getCellValueAsString(row.getCell(0)))) {
                    clientCode = getCellValueAsString(row.getCell(0));
                    clientName = getCellValueAsString(row.getCell(1));
                    continue;
                }
                ImportedLineItem lineItem = parseRowToLineItem(clientCode, clientName, row, filename, importId);
                if (lineItem != null) {
                    lineItems.add(lineItem);
                }
            }
        }

        return lineItems;
    }

    /**
     * Parse Excel row to ImportedLineItem
     *
     * TODO: Adjust column indices based on your actual Excel format
     *
     */
    private ImportedLineItem parseRowToLineItem(String clientCode, String clientName, Row row, String filename, Long importId) {
        try {
            ImportedLineItem lineItem = new ImportedLineItem();
            lineItem.setFilename(filename);
            lineItem.setImportId(importId);

            // customer fields
            lineItem.setCustomerCode(clientCode);
            lineItem.setCustomerName(clientName);

            // Parse product fields
            lineItem.setProductCode(getCellValueAsString(row.getCell(0)));
            lineItem.setProductDescription(getCellValueAsString(row.getCell(1)));

            // Parse line item fields
            lineItem.setQuantity(getCellValueAsBigDecimal(row.getCell(2)));
            lineItem.setAmount(getCellValueAsBigDecimal(row.getCell(3)));
            lineItem.setCost(getCellValueAsBigDecimal(row.getCell(4)));
            getCellValueAsBigDecimal(row.getCell(5));

            // Parse invoice fields
            lineItem.setInvoiceNumber(getCellValueAsString(row.getCell(7)));
            lineItem.setTransactionDate(getCellValueAsDate(row.getCell(8)));


            lineItem.setRef1(getCellValueAsString(row.getCell(9)));
            lineItem.setRef2(getCellValueAsString(row.getCell(10)));
            lineItem.setRef3(getCellValueAsString(row.getCell(11)));
//            lineItem.set
            lineItem.setOutstandingAmount(getCellValueAsBigDecimal(row.getCell(14)));

            return lineItem;

        } catch (Exception e) {
            log.warn("Failed to parse row {}: {}", row.getRowNum(), e.getMessage());
            return null;
        }
    }


    private boolean shouldSkipLine(Row row) {
        if (row == null) return true;
        if (row.getCell(0) == null) return true;
        if (getCellValueAsString(row.getCell(0)).equalsIgnoreCase("")) return true;
        if (getCellValueAsString(row.getCell(0)).equalsIgnoreCase("SubTotal")) return true;


        return false;
    }

    private boolean endParsing(Row row) {
        return (getCellValueAsString(row.getCell(0)).equalsIgnoreCase("9999999"));
    }

    private boolean isNumeric(String strNum) {
        if (strNum == null) {
            return false;
        }
        try {
            double d = Double.parseDouble(strNum);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }


    /**
     * Helper methods to extract cell values safely
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private BigDecimal getCellValueAsBigDecimal(Cell cell) {
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> {
                try {
                    yield new BigDecimal(cell.getStringCellValue());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private LocalDate getCellValueAsDate(Cell cell) {
        if (cell == null) return null;

        try {
            // Try to parse as a numeric date-formatted cell
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }

            // Try to parse as a string in dd/MM/yyyy format
            if (cell.getCellType() == CellType.STRING) {
                String dateStr = cell.getStringCellValue().trim();
                if (!dateStr.isEmpty()) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    return LocalDate.parse(dateStr, formatter);
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing date from cell: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get all import summaries
     */
    public List<ImportSummary> getAllImportSummaries() {
        return importSummaryRepository.findAll();
    }

    /**
     * Delete records imported from a specific file
     */
    @Transactional
    public void deleteImportedFile(String filename) {
        // Delete all line items for this filename
        importedLineItemRepository.deleteByFilename(filename);

        // Delete the import summary (will cascade delete line items via FK)
        importSummaryRepository.deleteByFilename(filename);

        log.info("Deleted all records for file: {}", filename);
    }

    /**
     * Get line items for a specific import
     */
    public List<ImportedLineItem> getLineItemsByFilename(String filename) {
        return importedLineItemRepository.findByFilename(filename);
    }

    /**
     * Get count of line items for a filename
     */
    public int getLineItemCount(String filename) {
        return importedLineItemRepository.countByFilename(filename);
    }

    /**
     * Get all imported line items
     */
    public List<ImportedLineItem> getAllLineItems() {
        return importedLineItemRepository.findAll();
    }

    /**
     * Get line items filtered by date range
     */
    public List<ImportedLineItem> getLineItemsByDateRange(LocalDate startDate, LocalDate endDate) {
        return importedLineItemRepository.findByDateRange(startDate, endDate);
    }

    /**
     * Get grouped line items (aggregated by customer + product)
     * Uses database view for efficient aggregation
     */
    public List<GroupedLineItem> getGroupedLineItems() {
        return groupedLineItemRepository.findAll().stream()
                .map(GroupedLineItem::fromRecord)
                .toList();
    }

    /**
     * Get grouped line items filtered by date range
     * Queries base table with grouping since view doesn't have transaction_date
     */
    public List<GroupedLineItem> getGroupedLineItemsByDateRange(LocalDate startDate, LocalDate endDate) {
        return groupedLineItemRepository.findByDateRange(startDate, endDate).stream()
                .map(GroupedLineItem::fromRecord)
                .toList();
    }

    /**
     * Get customer rating report for a specific date range
     * Aggregates sales data by customer and joins with customer ratings
     *
     * @param startDate Start of the date range
     * @param endDate End of the date range
     * @return List of customer rating report data
     */
    public List<CustomerRatingReportDTO> getCustomerRatingReport(LocalDate startDate, LocalDate endDate) {
        return importedLineItemRepository.getCustomerRatingReport(startDate, endDate);
    }

    /**
     * Get cost report for a specific import file
     * Shows line items where the line item cost price is lower than the standard cost
     *
     * @param filename The import filename to filter by
     * @return List of cost report data
     */
    public List<CostReportDTO> getCostReport(String filename) {
        return importedLineItemRepository.getCostReport(filename);
    }

    /**
     * Create or update customer records from imported line items
     */
    private void createOrUpdateCustomers(List<ImportedLineItem> lineItems) {
        // Extract unique customers from line items
        Map<String, String> uniqueCustomers = new HashMap<>();
        for (ImportedLineItem item : lineItems) {
            if (item.getCustomerCode() != null && !item.getCustomerCode().trim().isEmpty()) {
                uniqueCustomers.put(item.getCustomerCode(), item.getCustomerName());
            }
        }

        // Create or update each customer
        for (Map.Entry<String, String> entry : uniqueCustomers.entrySet()) {
            String customerCode = entry.getKey();
            String customerName = entry.getValue();

            Customer customer = new Customer();
            customer.setCustomerCode(customerCode);
            customer.setCustomerName(customerName);

            customerRepository.save(customer);
            log.debug("Created/updated customer: {} - {}", customerCode, customerName);
        }

        log.info("Processed {} unique customers", uniqueCustomers.size());
    }
}
