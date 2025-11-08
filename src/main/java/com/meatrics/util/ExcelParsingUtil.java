package com.meatrics.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for parsing Excel cell values.
 * Provides consistent, safe extraction of values from Excel cells across all import services.
 */
public final class ExcelParsingUtil {

    private static final Logger log = LoggerFactory.getLogger(ExcelParsingUtil.class);

    // Private constructor to prevent instantiation
    private ExcelParsingUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Extract cell value as String, handling different cell types safely.
     *
     * @param cell Excel cell to extract value from
     * @return String value or null if cell is empty
     */
    public static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        yield String.valueOf((long) cell.getNumericCellValue());
                    } catch (Exception ex) {
                        log.warn("Failed to parse FORMULA cell as string or numeric: {}", ex.getMessage());
                        yield null;
                    }
                }
            }
            default -> null;
        };
    }

    /**
     * Extract cell value as BigDecimal, handling different cell types safely.
     *
     * @param cell Excel cell to extract value from
     * @return BigDecimal value or null if cell is empty or cannot be parsed
     */
    public static BigDecimal getCellValueAsBigDecimal(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> {
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty()) {
                        yield null;
                    }
                    // Remove currency symbols and commas
                    value = value.replace("$", "").replace(",", "");
                    yield new BigDecimal(value);
                }
                case FORMULA -> {
                    try {
                        yield BigDecimal.valueOf(cell.getNumericCellValue());
                    } catch (Exception e) {
                        log.warn("Failed to parse FORMULA cell as numeric: {}", e.getMessage());
                        yield null;
                    }
                }
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Error parsing cell as BigDecimal: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract cell value as LocalDate, handling both date-formatted cells and string dates.
     *
     * @param cell Excel cell to extract value from
     * @return LocalDate value or null if cell is empty or cannot be parsed
     */
    public static LocalDate getCellValueAsDate(Cell cell) {
        if (cell == null) {
            return null;
        }

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

            // Try to handle FORMULA cells
            if (cell.getCellType() == CellType.FORMULA) {
                try {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getLocalDateTimeCellValue().toLocalDate();
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse FORMULA cell as date: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing date from cell: {}", e.getMessage());
        }

        return null;
    }
}
