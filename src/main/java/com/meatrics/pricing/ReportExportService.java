package com.meatrics.pricing;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating Excel reports using Apache POI
 */
@Service
public class ReportExportService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Generate Customer Rating Report as Excel file
     *
     * @param data List of customer rating report data
     * @param startDate Start date of the report period
     * @param endDate End date of the report period
     * @return Excel file as byte array
     */
    public byte[] generateCustomerRatingReportXLS(List<CustomerRatingReportDTO> data,
                                                   LocalDate startDate,
                                                   LocalDate endDate) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Rating Report");

            // Create cell styles
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle percentageStyle = createPercentageStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            int rowNum = 0;

            // Title row
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            String title = String.format("Rating Report - %s to %s",
                    startDate.format(DATE_FORMATTER),
                    endDate.format(DATE_FORMATTER));
            titleCell.setCellValue(title);
            titleCell.setCellStyle(titleStyle);

            // Empty row for spacing
            rowNum++;

            // Header row
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Customer", "Cost", "Amount", "GP%", "Original Rating", "Modified Rating", "Claude Rating"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            BigDecimal totalCost = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;

            for (CustomerRatingReportDTO dto : data) {
                Row dataRow = sheet.createRow(rowNum++);

                // Customer
                Cell customerCell = dataRow.createCell(0);
                customerCell.setCellValue(dto.getCustomerDisplay());
                customerCell.setCellStyle(normalStyle);

                // Cost
                Cell costCell = dataRow.createCell(1);
                if (dto.getTotalCost() != null) {
                    costCell.setCellValue(dto.getTotalCost().doubleValue());
                    totalCost = totalCost.add(dto.getTotalCost());
                } else {
                    costCell.setCellValue(0.0);
                }
                costCell.setCellStyle(currencyStyle);

                // Amount
                Cell amountCell = dataRow.createCell(2);
                if (dto.getTotalAmount() != null) {
                    amountCell.setCellValue(dto.getTotalAmount().doubleValue());
                    totalAmount = totalAmount.add(dto.getTotalAmount());
                } else {
                    amountCell.setCellValue(0.0);
                }
                amountCell.setCellStyle(currencyStyle);

                // GP%
                Cell gpCell = dataRow.createCell(3);
                if (dto.getGrossProfitPercentage() != null) {
                    // Convert percentage to decimal for Excel (e.g., 25% = 0.25)
                    gpCell.setCellValue(dto.getGrossProfitPercentage().doubleValue() / 100.0);
                } else {
                    gpCell.setCellValue(0.0);
                }
                gpCell.setCellStyle(percentageStyle);

                // Original Rating
                Cell originalRatingCell = dataRow.createCell(4);
                originalRatingCell.setCellValue(dto.getOriginalRating() != null ? dto.getOriginalRating() : "");
                originalRatingCell.setCellStyle(normalStyle);

                // Modified Rating
                Cell modifiedRatingCell = dataRow.createCell(5);
                modifiedRatingCell.setCellValue(dto.getModifiedRating() != null ? dto.getModifiedRating() : "");
                modifiedRatingCell.setCellStyle(normalStyle);

                // Claude Rating
                Cell claudeRatingCell = dataRow.createCell(6);
                claudeRatingCell.setCellValue(dto.getClaudeRating() != null ? dto.getClaudeRating() : "");
                claudeRatingCell.setCellStyle(normalStyle);
            }

            // Add totals row if there's data
            if (!data.isEmpty()) {
                rowNum++; // Empty row before totals
                Row totalRow = sheet.createRow(rowNum++);

                // "Total" label
                Cell totalLabelCell = totalRow.createCell(0);
                totalLabelCell.setCellValue("Total");
                totalLabelCell.setCellStyle(totalStyle);

                // Total Cost
                Cell totalCostCell = totalRow.createCell(1);
                totalCostCell.setCellValue(totalCost.doubleValue());
                CellStyle totalCurrencyStyle = createTotalCurrencyStyle(workbook);
                totalCostCell.setCellStyle(totalCurrencyStyle);

                // Total Amount
                Cell totalAmountCell = totalRow.createCell(2);
                totalAmountCell.setCellValue(totalAmount.doubleValue());
                totalAmountCell.setCellStyle(totalCurrencyStyle);

                // Overall GP%
                Cell totalGpCell = totalRow.createCell(3);
                BigDecimal overallGP = CustomerRatingReportDTO.calculateGPPercentage(totalAmount, totalCost);
                totalGpCell.setCellValue(overallGP.doubleValue() / 100.0);
                CellStyle totalPercentageStyle = createTotalPercentageStyle(workbook);
                totalGpCell.setCellStyle(totalPercentageStyle);

                // Empty rating cells (no totals for ratings)
                totalRow.createCell(4).setCellStyle(totalStyle);
                totalRow.createCell(5).setCellStyle(totalStyle);
                totalRow.createCell(6).setCellStyle(totalStyle);
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Add extra width for better readability
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
            }

            // Write workbook to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("Generated Customer Rating Report XLS with {} rows", data.size());

            return outputStream.toByteArray();
        }
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
     * Create header cell style (bold, with background)
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * Create currency cell style ($#,##0.00)
     */
    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        return style;
    }

    /**
     * Create percentage cell style (0.00%)
     */
    private CellStyle createPercentageStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));
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
     * Create total row style (bold)
     */
    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        return style;
    }

    /**
     * Create total currency style (bold + currency format)
     */
    private CellStyle createTotalCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        style.setBorderTop(BorderStyle.THIN);
        return style;
    }

    /**
     * Create total percentage style (bold + percentage format)
     */
    private CellStyle createTotalPercentageStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));
        style.setBorderTop(BorderStyle.THIN);
        return style;
    }

    /**
     * Generate Cost Report as Excel file
     * Shows imported line items where the line item cost price is lower than the standard cost
     *
     * @param data List of cost report data
     * @param filename The import filename this report is for
     * @return Excel file as byte array
     */
    public byte[] generateCostReportXLS(List<CostReportDTO> data, String filename) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Cost Report");

            // Create cell styles
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            int rowNum = 0;

            // Title row
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            String title = String.format("Cost Report - %s", filename);
            titleCell.setCellValue(title);
            titleCell.setCellStyle(titleStyle);

            // Empty row for spacing
            rowNum++;

            // Header row
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {
                "Stock Code",
                "Product Description",
                "Customer Name",
                "Invoice Number",
                "Transaction Date",
                "Quantity",
                "Cost",
                "STDCOST",
                "Line Item Cost Price",
                "Difference"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            for (CostReportDTO dto : data) {
                Row dataRow = sheet.createRow(rowNum++);

                // Stock Code
                Cell stockCodeCell = dataRow.createCell(0);
                stockCodeCell.setCellValue(dto.getProductCode() != null ? dto.getProductCode() : "");
                stockCodeCell.setCellStyle(normalStyle);

                // Product Description
                Cell descriptionCell = dataRow.createCell(1);
                descriptionCell.setCellValue(dto.getProductDescription() != null ? dto.getProductDescription() : "");
                descriptionCell.setCellStyle(normalStyle);

                // Customer Name
                Cell customerCell = dataRow.createCell(2);
                customerCell.setCellValue(dto.getCustomerName() != null ? dto.getCustomerName() : "");
                customerCell.setCellStyle(normalStyle);

                // Invoice Number
                Cell invoiceCell = dataRow.createCell(3);
                invoiceCell.setCellValue(dto.getInvoiceNumber() != null ? dto.getInvoiceNumber() : "");
                invoiceCell.setCellStyle(normalStyle);

                // Transaction Date
                Cell dateCell = dataRow.createCell(4);
                if (dto.getTransactionDate() != null) {
                    dateCell.setCellValue(dto.getFormattedTransactionDate());
                } else {
                    dateCell.setCellValue("");
                }
                dateCell.setCellStyle(normalStyle);

                // Quantity
                Cell quantityCell = dataRow.createCell(5);
                if (dto.getQuantity() != null) {
                    quantityCell.setCellValue(dto.getQuantity().doubleValue());
                } else {
                    quantityCell.setCellValue(0.0);
                }
                quantityCell.setCellStyle(normalStyle);

                // Cost
                Cell costCell = dataRow.createCell(6);
                if (dto.getCost() != null) {
                    costCell.setCellValue(dto.getCost().doubleValue());
                } else {
                    costCell.setCellValue(0.0);
                }
                costCell.setCellStyle(currencyStyle);

                // STDCOST
                Cell stdcostCell = dataRow.createCell(7);
                if (dto.getStdcost() != null) {
                    stdcostCell.setCellValue(dto.getStdcost().doubleValue());
                } else {
                    stdcostCell.setCellValue(0.0);
                }
                stdcostCell.setCellStyle(currencyStyle);

                // Line Item Cost Price
                Cell lineItemCostPriceCell = dataRow.createCell(8);
                if (dto.getLineItemCostPrice() != null) {
                    lineItemCostPriceCell.setCellValue(dto.getLineItemCostPrice().doubleValue());
                } else {
                    lineItemCostPriceCell.setCellValue(0.0);
                }
                lineItemCostPriceCell.setCellStyle(currencyStyle);

                // Difference
                Cell differenceCell = dataRow.createCell(9);
                if (dto.getDifference() != null) {
                    differenceCell.setCellValue(dto.getDifference().doubleValue());
                } else {
                    differenceCell.setCellValue(0.0);
                }
                differenceCell.setCellStyle(currencyStyle);
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Add extra width for better readability
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
            }

            // Write workbook to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("Generated Cost Report XLS with {} rows for file: {}", data.size(), filename);

            return outputStream.toByteArray();
        }
    }

    /**
     * Create date cell style (dd/MM/yyyy format)
     */
    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("dd/mm/yyyy"));
        return style;
    }

    /**
     * Generate Excel file for zero-amount line items
     *
     * @param items List of zero-amount items
     * @param filename Original import filename
     * @return Excel file as byte array
     * @throws IOException if file generation fails
     */
    public byte[] generateZeroAmountItemsXLS(List<ImportedLineItem> items, String filename) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Zero Amount Items");

            // Create cell styles
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);

            int rowNum = 0;

            // Title row
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            String title = String.format("Zero Amount Items - %s", filename);
            titleCell.setCellValue(title);
            titleCell.setCellStyle(titleStyle);

            // Empty row for spacing
            rowNum++;

            // Header row
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {
                "Invoice #", "Transaction Date", "Customer Code", "Customer Name",
                "Product Code", "Product Description", "Quantity", "Amount", "Cost"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            for (ImportedLineItem item : items) {
                Row row = sheet.createRow(rowNum++);

                // Invoice #
                Cell invoiceCell = row.createCell(0);
                invoiceCell.setCellValue(item.getInvoiceNumber() != null ? item.getInvoiceNumber() : "");
                invoiceCell.setCellStyle(normalStyle);

                // Transaction Date
                Cell dateCell = row.createCell(1);
                if (item.getTransactionDate() != null) {
                    dateCell.setCellValue(item.getTransactionDate().format(DATE_FORMATTER));
                } else {
                    dateCell.setCellValue("");
                }
                dateCell.setCellStyle(normalStyle);

                // Customer Code
                Cell customerCodeCell = row.createCell(2);
                customerCodeCell.setCellValue(item.getCustomerCode() != null ? item.getCustomerCode() : "");
                customerCodeCell.setCellStyle(normalStyle);

                // Customer Name
                Cell customerNameCell = row.createCell(3);
                customerNameCell.setCellValue(item.getCustomerName() != null ? item.getCustomerName() : "");
                customerNameCell.setCellStyle(normalStyle);

                // Product Code
                Cell productCodeCell = row.createCell(4);
                productCodeCell.setCellValue(item.getProductCode() != null ? item.getProductCode() : "");
                productCodeCell.setCellStyle(normalStyle);

                // Product Description
                Cell productDescCell = row.createCell(5);
                productDescCell.setCellValue(item.getProductDescription() != null ? item.getProductDescription() : "");
                productDescCell.setCellStyle(normalStyle);

                // Quantity
                Cell quantityCell = row.createCell(6);
                if (item.getQuantity() != null) {
                    quantityCell.setCellValue(item.getQuantity().doubleValue());
                } else {
                    quantityCell.setCellValue(0.0);
                }
                quantityCell.setCellStyle(normalStyle);

                // Amount
                Cell amountCell = row.createCell(7);
                if (item.getAmount() != null) {
                    amountCell.setCellValue(item.getAmount().doubleValue());
                } else {
                    amountCell.setCellValue(0.0);
                }
                amountCell.setCellStyle(currencyStyle);

                // Cost
                Cell costCell = row.createCell(8);
                if (item.getCost() != null) {
                    costCell.setCellValue(item.getCost().doubleValue());
                } else {
                    costCell.setCellValue(0.0);
                }
                costCell.setCellStyle(currencyStyle);
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

            log.info("Generated Zero Amount Items XLS with {} rows for file: {}", items.size(), filename);

            return outputStream.toByteArray();
        }
    }
}
