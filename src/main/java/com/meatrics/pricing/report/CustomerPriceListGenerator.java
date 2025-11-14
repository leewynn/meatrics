package com.meatrics.pricing.report;

import com.meatrics.pricing.product.GroupedLineItem;
import com.meatrics.pricing.session.PricingSession;
import com.meatrics.pricing.session.PricingSessionLineItem;
import com.meatrics.pricing.session.PricingSessionLineItemRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service for generating customer price list reports from finalized pricing sessions
 */
@Service
public class CustomerPriceListGenerator {

    private static final Logger log = LoggerFactory.getLogger(CustomerPriceListGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final BigDecimal GST_RATE = new BigDecimal("1.10"); // 10% GST

    private final PricingSessionLineItemRepository lineItemRepository;
    private final com.meatrics.pricing.session.AppliedRuleSnapshotRepository appliedRuleSnapshotRepository;

    public CustomerPriceListGenerator(PricingSessionLineItemRepository lineItemRepository,
                                      com.meatrics.pricing.session.AppliedRuleSnapshotRepository appliedRuleSnapshotRepository) {
        this.lineItemRepository = lineItemRepository;
        this.appliedRuleSnapshotRepository = appliedRuleSnapshotRepository;
    }

    /**
     * Generate customer price lists for all customers in a session and ZIP them
     *
     * @param session The finalized pricing session
     * @param effectiveDate The date when new prices become effective
     * @return ZIP file as byte array containing individual customer Excel files
     */
    public byte[] generateCustomerPriceListsZip(PricingSession session, LocalDate effectiveDate) throws IOException {
        if (session == null || session.getId() == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }

        log.info("Generating customer price lists for session: {} (ID: {})",
                session.getSessionName(), session.getId());

        // Load all line items for this session
        List<PricingSessionLineItem> allItems = lineItemRepository.findBySessionId(session.getId());

        if (allItems.isEmpty()) {
            throw new IllegalArgumentException("Session has no line items");
        }

        // Group line items by customer
        Map<String, List<PricingSessionLineItem>> itemsByCustomer = allItems.stream()
                .filter(item -> item.getCustomerCode() != null && item.getNewUnitSellPrice() != null)
                .collect(Collectors.groupingBy(PricingSessionLineItem::getCustomerCode));

        log.info("Found {} customers in session", itemsByCustomer.size());

        // Create ZIP file
        ByteArrayOutputStream zipOutputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipOutputStream)) {

            // Generate Excel file for each customer
            for (Map.Entry<String, List<PricingSessionLineItem>> entry : itemsByCustomer.entrySet()) {
                String customerCode = entry.getKey();
                List<PricingSessionLineItem> customerItems = entry.getValue();

                // Get customer name from first item
                String customerName = customerItems.get(0).getCustomerName();
                if (customerName == null) {
                    customerName = customerCode;
                }

                // Generate Excel file for this customer
                byte[] excelBytes = generateCustomerPriceListExcel(
                        customerName,
                        customerCode,
                        customerItems,
                        session,
                        effectiveDate
                );

                // Add to ZIP
                String filename = sanitizeFilename(customerName) + "_PriceList.xlsx";
                ZipEntry entry1 = new ZipEntry(filename);
                zip.putNextEntry(entry1);
                zip.write(excelBytes);
                zip.closeEntry();

                log.info("Generated price list for customer: {} ({} items)", customerName, customerItems.size());
            }
        }

        log.info("Successfully generated ZIP with {} customer price lists", itemsByCustomer.size());
        return zipOutputStream.toByteArray();
    }

    /**
     * Generate Excel price list for a single customer
     */
    private byte[] generateCustomerPriceListExcel(String customerName,
                                                   String customerCode,
                                                   List<PricingSessionLineItem> items,
                                                   PricingSession session,
                                                   LocalDate effectiveDate) throws IOException {

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Price List");

            // Create cell styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyExGstStyle = createCurrencyExGstStyle(workbook);
            CellStyle currencyIncGstStyle = createCurrencyIncGstStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);

            int rowNum = 0;

            // Title row with effective date
            if (effectiveDate != null) {
                Row titleRow = sheet.createRow(rowNum++);
                Cell titleCell = titleRow.createCell(0);
                String title = String.format("%s Price List - Commencing %s",
                        customerName,
                        effectiveDate.format(DATE_FORMATTER));
                titleCell.setCellValue(title);
                CellStyle titleStyle = createTitleStyle(workbook);
                titleCell.setCellStyle(titleStyle);
                rowNum++; // Empty row
            }

            // Check if this customer has any rebates applied
            boolean hasRebates = customerHasRebates(items);
            log.info("Customer {} has rebates: {}", customerName, hasRebates);

            // Header row
            Row headerRow = sheet.createRow(rowNum++);

            // Format effective date for column headers
            String effectiveDateStr = effectiveDate != null
                    ? effectiveDate.format(DATE_FORMATTER)
                    : "TBD";

            // Conditional headers based on rebate presence
            String[] headers;
            if (hasRebates) {
                headers = new String[]{
                        "Description",
                        "Stock Code",
                        "Current Sell Price",
                        "Current Sell price less Rebate",
                        "New Sell Price from " + effectiveDateStr,
                        "New Sell Price from " + effectiveDateStr + " less Rebate"
                };
            } else {
                headers = new String[]{
                        "Description",
                        "Stock Code",
                        "Current Sell Price",
                        "New Sell Price from " + effectiveDateStr
                };
            }

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Group items by primaryGroup, maintaining insertion order
            Map<String, List<PricingSessionLineItem>> itemsByCategory = new LinkedHashMap<>();
            for (PricingSessionLineItem item : items) {
                String category = item.getPrimaryGroup();
                if (category == null || category.trim().isEmpty()) {
                    category = "Other";
                }
                itemsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
            }

            // Sort items within each category by product code
            for (List<PricingSessionLineItem> categoryItems : itemsByCategory.values()) {
                categoryItems.sort(Comparator.comparing(PricingSessionLineItem::getProductCode,
                        Comparator.nullsLast(Comparator.naturalOrder())));
            }

            // Create category header style
            CellStyle categoryHeaderStyle = createCategoryHeaderStyle(workbook);

            // Data rows with category headers
            for (Map.Entry<String, List<PricingSessionLineItem>> entry : itemsByCategory.entrySet()) {
                String categoryName = entry.getKey();
                List<PricingSessionLineItem> categoryItems = entry.getValue();

                // Add category header row
                Row categoryRow = sheet.createRow(rowNum++);
                Cell categoryCell = categoryRow.createCell(0);
                categoryCell.setCellValue(formatCategoryName(categoryName));
                categoryCell.setCellStyle(categoryHeaderStyle);

                // Merge cells across all columns
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                        categoryRow.getRowNum(), categoryRow.getRowNum(), 0, headers.length - 1));

                // Add items in this category
                for (PricingSessionLineItem item : categoryItems) {
                    Row dataRow = sheet.createRow(rowNum++);

                    // Column 0: Description
                    Cell descCell = dataRow.createCell(0);
                    descCell.setCellValue(item.getProductDescription() != null ? item.getProductDescription() : "");
                    descCell.setCellStyle(normalStyle);

                    // Column 1: Stock Code
                    Cell stockCodeCell = dataRow.createCell(1);
                    stockCodeCell.setCellValue(item.getProductCode() != null ? item.getProductCode() : "");
                    stockCodeCell.setCellStyle(normalStyle);

                    if (hasRebates) {
                        // Get rebate multiplier for this item
                        BigDecimal rebateMultiplier = getTotalRebateMultiplier(item);
                        boolean itemHasRebate = rebateMultiplier.compareTo(BigDecimal.ONE) < 0;

                        // Column 2: Current Sell Price
                        Cell currentPriceCell = dataRow.createCell(2);
                        if (item.getLastUnitSellPrice() != null) {
                            currentPriceCell.setCellValue(item.getLastUnitSellPrice().doubleValue());
                        } else {
                            currentPriceCell.setCellValue("");
                        }
                        currentPriceCell.setCellStyle(currencyExGstStyle);

                        // Column 3: Current Sell price less Rebate (same as current price - historical rebates not tracked)
                        Cell currentRebateCell = dataRow.createCell(3);
                        if (item.getLastUnitSellPrice() != null) {
                            currentRebateCell.setCellValue(item.getLastUnitSellPrice().doubleValue());
                        } else {
                            currentRebateCell.setCellValue("");
                        }
                        currentRebateCell.setCellStyle(currencyExGstStyle);

                        // Column 4: New Sell Price from [date] (BEFORE rebate if rebate applied)
                        Cell newPriceCell = dataRow.createCell(4);
                        if (item.getNewUnitSellPrice() != null) {
                            if (itemHasRebate) {
                                // Reverse calculate: priceAfterRebate / rebateMultiplier = priceBeforeRebate
                                BigDecimal priceBeforeRebate = item.getNewUnitSellPrice()
                                    .divide(rebateMultiplier, 6, RoundingMode.HALF_UP);
                                newPriceCell.setCellValue(priceBeforeRebate.doubleValue());
                                log.debug("Product {}: Price before rebate = {} (after = {}, multiplier = {})",
                                    item.getProductCode(), priceBeforeRebate, item.getNewUnitSellPrice(), rebateMultiplier);
                            } else {
                                newPriceCell.setCellValue(item.getNewUnitSellPrice().doubleValue());
                            }
                        } else {
                            newPriceCell.setCellValue("");
                        }
                        newPriceCell.setCellStyle(currencyExGstStyle);

                        // Column 5: New Sell Price from [date] less Rebate (AFTER rebate - actual price)
                        Cell newRebateCell = dataRow.createCell(5);
                        if (item.getNewUnitSellPrice() != null) {
                            newRebateCell.setCellValue(item.getNewUnitSellPrice().doubleValue());
                        } else {
                            newRebateCell.setCellValue("");
                        }
                        newRebateCell.setCellStyle(currencyExGstStyle);
                    } else {
                        // No rebates - only show 4 columns
                        // Column 2: Current Sell Price
                        Cell currentPriceCell = dataRow.createCell(2);
                        if (item.getLastUnitSellPrice() != null) {
                            currentPriceCell.setCellValue(item.getLastUnitSellPrice().doubleValue());
                        } else {
                            currentPriceCell.setCellValue("");
                        }
                        currentPriceCell.setCellStyle(currencyExGstStyle);

                        // Column 3: New Sell Price from [date]
                        Cell newPriceCell = dataRow.createCell(3);
                        if (item.getNewUnitSellPrice() != null) {
                            newPriceCell.setCellValue(item.getNewUnitSellPrice().doubleValue());
                        } else {
                            newPriceCell.setCellValue("");
                        }
                        newPriceCell.setCellStyle(currencyExGstStyle);
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Add extra width for better readability
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
            }

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            return outputStream.toByteArray();
        }
    }

    /**
     * Sanitize filename to remove invalid characters
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "Unknown";
        }
        // Remove invalid filename characters
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Create title cell style (bold, larger font)
     */
    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    /**
     * Create header cell style (bold, white text on blue background)
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    /**
     * Create currency style for Ex GST prices ($#,##0.00)
     */
    private CellStyle createCurrencyExGstStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        return style;
    }

    /**
     * Create currency style for Inc GST prices ($#,##0.00)
     */
    private CellStyle createCurrencyIncGstStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        return style;
    }

    /**
     * Create normal text cell style
     */
    private CellStyle createNormalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    /**
     * Create category header style (bold, white text on blue background, spanning columns)
     */
    private CellStyle createCategoryHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * Check if any items in the customer's list have rebates applied
     */
    private boolean customerHasRebates(List<PricingSessionLineItem> items) {
        for (PricingSessionLineItem item : items) {
            if (item.getId() != null) {
                List<com.meatrics.pricing.session.AppliedRuleSnapshot> snapshots =
                    appliedRuleSnapshotRepository.findBySessionLineItemId(item.getId());

                for (com.meatrics.pricing.session.AppliedRuleSnapshot snapshot : snapshots) {
                    if (snapshot.isRebate()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Calculate the total rebate multiplier for an item by multiplying all rebate rule values
     * For example: 5% rebate (0.95) + 3% rebate (0.97) = 0.95 × 0.97 = 0.9215
     *
     * @param item The pricing session line item
     * @return The combined rebate multiplier, or BigDecimal.ONE if no rebates
     */
    private BigDecimal getTotalRebateMultiplier(PricingSessionLineItem item) {
        if (item.getId() == null) {
            return BigDecimal.ONE;
        }

        List<com.meatrics.pricing.session.AppliedRuleSnapshot> snapshots =
            appliedRuleSnapshotRepository.findBySessionLineItemId(item.getId());

        BigDecimal totalMultiplier = BigDecimal.ONE;

        for (com.meatrics.pricing.session.AppliedRuleSnapshot snapshot : snapshots) {
            if (snapshot.isRebate()) {
                totalMultiplier = totalMultiplier.multiply(snapshot.getPricingValue());
                log.debug("Rebate found: {} with multiplier {}", snapshot.getRuleName(), snapshot.getPricingValue());
            }
        }

        return totalMultiplier;
    }

    /**
     * Format category name - convert all uppercase to title case
     */
    private String formatCategoryName(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "Other";
        }

        String trimmed = category.trim();

        // Check if all uppercase
        if (trimmed.equals(trimmed.toUpperCase())) {
            // Convert to title case
            String[] words = trimmed.toLowerCase().split("\\s+");
            StringBuilder result = new StringBuilder();

            for (String word : words) {
                if (!word.isEmpty()) {
                    if (result.length() > 0) {
                        result.append(" ");
                    }
                    // Capitalize first letter, rest lowercase
                    result.append(Character.toUpperCase(word.charAt(0)));
                    if (word.length() > 1) {
                        result.append(word.substring(1));
                    }
                }
            }

            return result.toString();
        }

        // Return as-is if not all uppercase
        return trimmed;
    }
}
