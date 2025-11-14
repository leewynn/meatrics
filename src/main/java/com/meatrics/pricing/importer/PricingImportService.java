package com.meatrics.pricing.importer;

import com.meatrics.pricing.customer.Customer;
import com.meatrics.pricing.customer.CustomerRatingReportDTO;
import com.meatrics.pricing.customer.CustomerRatingService;
import com.meatrics.pricing.customer.CustomerRepository;
import com.meatrics.pricing.product.GroupedLineItem;
import com.meatrics.pricing.product.GroupedLineItemRepository;
import com.meatrics.pricing.report.CostReportDTO;
import com.meatrics.util.ExcelParsingUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jooq.DSLContext;
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
import java.util.*;

import static com.meatrics.generated.Tables.IMPORTED_LINE_ITEMS;

/**
 * Service for importing pricing data from Excel files
 */
@Service
public class PricingImportService {

    private static final Logger log = LoggerFactory.getLogger(PricingImportService.class);

    private final DSLContext dsl;
    private final ImportSummaryRepository importSummaryRepository;
    private final ImportedLineItemRepository importedLineItemRepository;
    private final CustomerRepository customerRepository;
    private final CustomerRatingService customerRatingService;
    private final GroupedLineItemRepository groupedLineItemRepository;

    // Temporary storage for uploaded files awaiting processing
    private final Map<String, File> uploadedFiles = new HashMap<>();

    public PricingImportService(DSLContext dsl,
                                ImportSummaryRepository importSummaryRepository,
                                ImportedLineItemRepository importedLineItemRepository,
                                CustomerRepository customerRepository,
                                CustomerRatingService customerRatingService,
                                GroupedLineItemRepository groupedLineItemRepository) {
        this.dsl = dsl;
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
     * Get uploaded file by filename
     */
    public File getUploadedFile(String filename) {
        return uploadedFiles.get(filename);
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
                ImportSummary summary = importFromInputStream(inputStream, filename);
                totalRecords += summary.getRecordCount();

                log.info("Imported {} records from file: {}", summary.getRecordCount(), filename);
            }
        }

        // Clear uploaded files after import
        uploadedFiles.clear();

        return totalRecords;
    }

    /**
     * Check if any line items in the import already exist in the database.
     * This method performs pre-import validation to prevent duplicate data.
     *
     * @param lineItems List of line items to check
     * @return List of duplicate descriptions for user feedback
     */
    private List<String> checkForDuplicates(List<ImportedLineItem> lineItems) {
        List<String> duplicates = new ArrayList<>();

        for (ImportedLineItem item : lineItems) {
            // Query database to see if this exact record already exists
            // Matches on: customer_code, invoice_number, product_code, transaction_date, quantity, amount
            boolean exists = dsl.fetchExists(
                dsl.selectFrom(IMPORTED_LINE_ITEMS)
                    .where(IMPORTED_LINE_ITEMS.CUSTOMER_CODE.eq(item.getCustomerCode()))
                    .and(IMPORTED_LINE_ITEMS.INVOICE_NUMBER.eq(item.getInvoiceNumber()))
                    .and(IMPORTED_LINE_ITEMS.PRODUCT_CODE.eq(item.getProductCode()))
                    .and(IMPORTED_LINE_ITEMS.TRANSACTION_DATE.eq(item.getTransactionDate()))
                    .and(IMPORTED_LINE_ITEMS.QUANTITY.eq(item.getQuantity()))
                    .and(IMPORTED_LINE_ITEMS.AMOUNT.eq(item.getAmount()))
            );

            if (exists) {
                duplicates.add(String.format(
                    "Invoice: %s, Customer: %s, Product: %s, Date: %s, Qty: %s, Amount: %s",
                    item.getInvoiceNumber(),
                    item.getCustomerName(),
                    item.getProductCode(),
                    item.getTransactionDate(),
                    item.getQuantity(),
                    item.getAmount()
                ));
            }
        }

        return duplicates;
    }

    /**
     * Import data from Excel file input stream.
     * This method validates for duplicates BEFORE inserting any data.
     * If duplicates are found, the entire import is rejected with no data inserted.
     *
     * @return ImportSummary object containing import details including importId
     */
    @Transactional
    public ImportSummary importFromInputStream(InputStream inputStream, String filename) throws IOException {
        // Parse Excel file first (NO database writes yet)
        List<ImportedLineItem> lineItems = readExcelFile(inputStream, filename, null);

        // CRITICAL: Check for duplicates BEFORE any database writes
        List<String> duplicates = checkForDuplicates(lineItems);
        if (!duplicates.isEmpty()) {
            log.warn("Import rejected for file '{}': {} duplicate record(s) detected", filename, duplicates.size());
            throw new DuplicateImportException(
                "Import rejected: " + duplicates.size() + " duplicate record(s) detected. No data has been imported.",
                duplicates
            );
        }

        // No duplicates found - proceed with import
        // Create import summary record
        ImportSummary summary = new ImportSummary();
        summary.setFilename(filename);
        summary.setRecordCount(0);
        summary.setStatus("PROCESSING");
        summary = importSummaryRepository.save(summary);

        // Now update line items with the import ID
        final Long importId = summary.getImportId();
        lineItems.forEach(item -> item.setImportId(importId));

        // Save all line items (within the same transaction)
        importedLineItemRepository.saveAll(lineItems);

        // Create/update customer records from imported line items
        createOrUpdateCustomers(lineItems);

        // Update import summary with final count and status
        summary.setRecordCount(lineItems.size());
        summary.setStatus("COMPLETED");
        summary = importSummaryRepository.save(summary);

        // Recalculate customer ratings in background
        customerRatingService.recalculateAndSaveAllCustomerRatings();

        log.info("Successfully imported {} line items from {}", lineItems.size(), filename);
        return summary;
    }

    /**
     * Read Excel file and parse imported line items
     *
     * @param inputStream The Excel file input stream
     * @param filename The name of the file being imported
     * @param importId The import ID (can be null during duplicate checking phase)
     * @return List of parsed line items
     * @throws IOException If there's an error reading the file
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
                if(isNumeric(ExcelParsingUtil.getCellValueAsString(row.getCell(0)))) {
                    clientCode = ExcelParsingUtil.getCellValueAsString(row.getCell(0));
                    clientName = ExcelParsingUtil.getCellValueAsString(row.getCell(1));
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
            lineItem.setProductCode(ExcelParsingUtil.getCellValueAsString(row.getCell(0)));
            lineItem.setProductDescription(ExcelParsingUtil.getCellValueAsString(row.getCell(1)));

            // Parse line item fields
            lineItem.setQuantity(ExcelParsingUtil.getCellValueAsBigDecimal(row.getCell(2)));
            lineItem.setAmount(ExcelParsingUtil.getCellValueAsBigDecimal(row.getCell(3)));
            lineItem.setCost(ExcelParsingUtil.getCellValueAsBigDecimal(row.getCell(4)));
            ExcelParsingUtil.getCellValueAsBigDecimal(row.getCell(5));

            // Parse invoice fields
            lineItem.setInvoiceNumber(ExcelParsingUtil.getCellValueAsString(row.getCell(7)));
            lineItem.setTransactionDate(ExcelParsingUtil.getCellValueAsDate(row.getCell(8)));


            lineItem.setRef1(ExcelParsingUtil.getCellValueAsString(row.getCell(9)));
            lineItem.setRef2(ExcelParsingUtil.getCellValueAsString(row.getCell(10)));
            lineItem.setRef3(ExcelParsingUtil.getCellValueAsString(row.getCell(11)));
            lineItem.setOutstandingAmount(ExcelParsingUtil.getCellValueAsBigDecimal(row.getCell(14)));

            return lineItem;

        } catch (Exception e) {
            log.warn("Failed to parse row {}: {}", row.getRowNum(), e.getMessage());
            return null;
        }
    }


    private boolean shouldSkipLine(Row row) {
        if (row == null) return true;
        if (row.getCell(0) == null) return true;
        if (ExcelParsingUtil.getCellValueAsString(row.getCell(0)).equalsIgnoreCase("")) return true;
        if (ExcelParsingUtil.getCellValueAsString(row.getCell(0)).equalsIgnoreCase("SubTotal")) return true;


        return false;
    }

    private boolean endParsing(Row row) {
        return (ExcelParsingUtil.getCellValueAsString(row.getCell(0)).equalsIgnoreCase("9999999"));
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
        return groupedLineItemRepository.findByDateRange(startDate, endDate);
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

    /**
     * Find all line items with zero or null amounts from a specific import
     *
     * @param importId The import ID to check
     * @return List of line items with zero/null amounts
     */
    public List<ImportedLineItem> getZeroAmountItems(Long importId) {
        return dsl.selectFrom(IMPORTED_LINE_ITEMS)
            .where(IMPORTED_LINE_ITEMS.IMPORT_ID.eq(importId))
            .and(IMPORTED_LINE_ITEMS.AMOUNT.isNull()
                .or(IMPORTED_LINE_ITEMS.AMOUNT.eq(BigDecimal.ZERO)))
            .fetch(record -> {
                ImportedLineItem item = new ImportedLineItem();
                item.setLineId(record.getLineId());
                item.setImportId(record.getImportId());
                item.setFilename(record.getFilename());
                item.setCustomerCode(record.getCustomerCode());
                item.setCustomerName(record.getCustomerName());
                item.setInvoiceNumber(record.getInvoiceNumber());
                item.setTransactionDate(record.getTransactionDate());
                item.setProductCode(record.getProductCode());
                item.setProductDescription(record.getProductDescription());
                item.setQuantity(record.getQuantity());
                item.setAmount(record.getAmount());
                item.setCost(record.getCost());
                item.setRef1(record.getRef1());
                item.setRef2(record.getRef2());
                item.setRef3(record.getRef3());
                item.setOutstandingAmount(record.getOutstandingAmount());
                item.setImportDate(record.getImportDate());
                return item;
            });
    }
}
