package com.meatrics.pricing.ui.pricing;

import com.meatrics.pricing.product.GroupedLineItem;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.FooterRow;
import com.vaadin.flow.component.grid.Grid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Manages grid column visibility and footer calculations.
 * Handles saving/loading column preferences and updating footer totals.
 */
@org.springframework.stereotype.Component
public class PricingGridColumnManager {

    private static final Logger log = LoggerFactory.getLogger(PricingGridColumnManager.class);
    private static final String STORAGE_KEY = "meatrics.pricingSessions.columnVisibility";

    private final PricingCalculator pricingCalculator;

    public PricingGridColumnManager(PricingCalculator pricingCalculator) {
        this.pricingCalculator = pricingCalculator;
    }

    /**
     * Update footer totals based on currently displayed items
     */
    public void updateFooterTotals(FooterRow footerRow, List<GroupedLineItem> items, GridColumns columns) {
        if (footerRow == null || items == null || columns == null) {
            return;
        }

        // Calculate totals
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalLastAmount = BigDecimal.ZERO;
        BigDecimal totalLastGP = BigDecimal.ZERO;
        BigDecimal totalNewAmount = BigDecimal.ZERO;
        BigDecimal totalNewGP = BigDecimal.ZERO;
        BigDecimal totalLastCostWeighted = BigDecimal.ZERO;
        BigDecimal totalNewCostWeighted = BigDecimal.ZERO;

        for (GroupedLineItem item : items) {
            if (item.getTotalQuantity() != null) {
                totalQty = totalQty.add(item.getTotalQuantity());
            }
            if (item.getLastAmount() != null) {
                totalLastAmount = totalLastAmount.add(item.getLastAmount());
            }
            if (item.getLastGrossProfit() != null) {
                totalLastGP = totalLastGP.add(item.getLastGrossProfit());
            }
            if (item.getNewAmount() != null) {
                totalNewAmount = totalNewAmount.add(item.getNewAmount());
            }
            if (item.getNewGrossProfit() != null) {
                totalNewGP = totalNewGP.add(item.getNewGrossProfit());
            }
            // Calculate weighted costs for drift
            if (item.getLastCost() != null && item.getTotalQuantity() != null) {
                totalLastCostWeighted = totalLastCostWeighted.add(
                    item.getLastCost().multiply(item.getTotalQuantity()));
            }
            if (item.getIncomingCost() != null && item.getTotalQuantity() != null) {
                totalNewCostWeighted = totalNewCostWeighted.add(
                    item.getIncomingCost().multiply(item.getTotalQuantity()));
            }
        }

        // Calculate total cost drift (absolute and percentage)
        BigDecimal totalCostDrift = totalNewCostWeighted.subtract(totalLastCostWeighted);
        String costDriftText = "N/A";
        if (totalLastCostWeighted.compareTo(BigDecimal.ZERO) != 0 &&
            totalNewCostWeighted.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal percentDrift = totalCostDrift
                    .divide(totalLastCostWeighted, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            String sign = totalCostDrift.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            costDriftText = String.format("%s%s (%s%.1f%%)",
                    sign, pricingCalculator.formatCurrency(totalCostDrift).replace("$", "$"),
                    sign, percentDrift);
        }

        // Update footer cells
        footerRow.getCell(columns.customerCol).setText("Total:");
        footerRow.getCell(columns.qtyCol).setText(String.format("%.2f", totalQty));
        footerRow.getCell(columns.lastAmountCol).setText(pricingCalculator.formatCurrency(totalLastAmount));
        footerRow.getCell(columns.lastGPCol).setText(pricingCalculator.formatCurrency(totalLastGP));
        footerRow.getCell(columns.lastGPPercentCol).setText(
            pricingCalculator.formatGPPercent(totalLastGP, totalLastAmount));
        footerRow.getCell(columns.costDriftCol).setText(costDriftText);
        footerRow.getCell(columns.newAmountCol).setText(pricingCalculator.formatCurrency(totalNewAmount));
        footerRow.getCell(columns.newGPCol).setText(pricingCalculator.formatCurrency(totalNewGP));
        footerRow.getCell(columns.newGPPercentCol).setText(
            pricingCalculator.formatGPPercent(totalNewGP, totalNewAmount));
    }

    /**
     * Save column visibility preferences to browser localStorage
     */
    public void saveColumnVisibility(Component element, GridColumns columns) {
        String json = String.format(
            "{" +
            "\"customerName\":%b," +
            "\"rating\":%b," +
            "\"productCode\":%b," +
            "\"product\":%b," +
            "\"quantity\":%b," +
            "\"lastCost\":%b," +
            "\"lastPrice\":%b," +
            "\"lastAmount\":%b," +
            "\"lastGP\":%b," +
            "\"lastGPPercent\":%b," +
            "\"costDrift\":%b," +
            "\"newCost\":%b," +
            "\"newPrice\":%b," +
            "\"newAmount\":%b," +
            "\"newGP\":%b," +
            "\"newGPPercent\":%b," +
            "\"notes\":%b" +
            "}",
            columns.customerCol.isVisible(),
            columns.ratingCol.isVisible(),
            columns.productCodeCol.isVisible(),
            columns.productCol.isVisible(),
            columns.qtyCol.isVisible(),
            columns.lastCostCol.isVisible(),
            columns.lastPriceCol.isVisible(),
            columns.lastAmountCol.isVisible(),
            columns.lastGPCol.isVisible(),
            columns.lastGPPercentCol.isVisible(),
            columns.costDriftCol.isVisible(),
            columns.newCostCol.isVisible(),
            columns.newPriceCol.isVisible(),
            columns.newAmountCol.isVisible(),
            columns.newGPCol.isVisible(),
            columns.newGPPercentCol.isVisible(),
            columns.notesCol.isVisible()
        );

        element.getElement().executeJs("localStorage.setItem($0, $1)", STORAGE_KEY, json);
    }

    /**
     * Load column visibility preferences from browser localStorage
     */
    public void loadColumnVisibility(Component element, GridColumns columns) {
        element.getElement().executeJs(
            "return localStorage.getItem($0)", STORAGE_KEY
        ).then(String.class, json -> {
            if (json != null && !json.isEmpty()) {
                try {
                    // Parse JSON manually (simple string parsing)
                    columns.customerCol.setVisible(getBooleanValue(json, "customerName"));
                    columns.ratingCol.setVisible(getBooleanValue(json, "rating"));
                    columns.productCodeCol.setVisible(getBooleanValue(json, "productCode"));
                    columns.productCol.setVisible(getBooleanValue(json, "product"));
                    columns.qtyCol.setVisible(getBooleanValue(json, "quantity"));
                    columns.lastCostCol.setVisible(getBooleanValue(json, "lastCost"));
                    columns.lastPriceCol.setVisible(getBooleanValue(json, "lastPrice"));
                    columns.lastAmountCol.setVisible(getBooleanValue(json, "lastAmount"));
                    columns.lastGPCol.setVisible(getBooleanValue(json, "lastGP"));
                    columns.lastGPPercentCol.setVisible(getBooleanValue(json, "lastGPPercent"));
                    columns.costDriftCol.setVisible(getBooleanValue(json, "costDrift"));
                    columns.newCostCol.setVisible(getBooleanValue(json, "newCost"));
                    columns.newPriceCol.setVisible(getBooleanValue(json, "newPrice"));
                    columns.newAmountCol.setVisible(getBooleanValue(json, "newAmount"));
                    columns.newGPCol.setVisible(getBooleanValue(json, "newGP"));
                    columns.newGPPercentCol.setVisible(getBooleanValue(json, "newGPPercent"));
                    columns.notesCol.setVisible(getBooleanValue(json, "notes"));
                } catch (Exception e) {
                    log.warn("Failed to parse column visibility from localStorage", e);
                }
            }
        });
    }

    /**
     * Extract boolean value from JSON string (simple parsing)
     */
    private boolean getBooleanValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int index = json.indexOf(pattern);
        if (index == -1) {
            return true; // Default to visible if not found
        }
        int valueStart = index + pattern.length();
        String substring = json.substring(valueStart);
        return substring.startsWith("true");
    }

    /**
     * Data class to hold grid column references
     */
    public static class GridColumns {
        public Grid.Column<GroupedLineItem> customerCol;
        public Grid.Column<GroupedLineItem> ratingCol;
        public Grid.Column<GroupedLineItem> productCodeCol;
        public Grid.Column<GroupedLineItem> productCol;
        public Grid.Column<GroupedLineItem> qtyCol;
        public Grid.Column<GroupedLineItem> lastCostCol;
        public Grid.Column<GroupedLineItem> lastPriceCol;
        public Grid.Column<GroupedLineItem> lastAmountCol;
        public Grid.Column<GroupedLineItem> lastGPCol;
        public Grid.Column<GroupedLineItem> lastGPPercentCol;
        public Grid.Column<GroupedLineItem> costDriftCol;
        public Grid.Column<GroupedLineItem> newCostCol;
        public Grid.Column<GroupedLineItem> newPriceCol;
        public Grid.Column<GroupedLineItem> newAmountCol;
        public Grid.Column<GroupedLineItem> newGPCol;
        public Grid.Column<GroupedLineItem> newGPPercentCol;
        public Grid.Column<GroupedLineItem> notesCol;
    }
}
