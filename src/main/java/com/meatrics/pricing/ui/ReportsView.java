package com.meatrics.pricing.ui;

import com.meatrics.pricing.CostReportDTO;
import com.meatrics.pricing.CustomerRatingReportDTO;
import com.meatrics.pricing.ImportSummary;
import com.meatrics.pricing.PricingImportService;
import com.meatrics.pricing.ReportExportService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reports view with tabbed layout for multiple report types
 */
@Route("reports")
@PageTitle("Reports")
@Menu(order = 2, icon = "vaadin:chart", title = "Reports")
public class ReportsView extends Main {

    private static final Logger log = LoggerFactory.getLogger(ReportsView.class);

    private final PricingImportService pricingImportService;
    private final ReportExportService reportExportService;

    // Customer Rating Report components
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private Grid<CustomerRatingReportDTO> reportGrid;
    private Button loadReportButton;
    private Button generateXlsButton;

    private List<CustomerRatingReportDTO> currentReportData;

    // Cost Report components
    private ComboBox<ImportSummary> fileSelectionComboBox;
    private Grid<CostReportDTO> costReportGrid;
    private Button loadCostReportButton;
    private Button generateCostReportXlsButton;

    private List<CostReportDTO> currentCostReportData;

    public ReportsView(PricingImportService pricingImportService,
                      ReportExportService reportExportService) {
        this.pricingImportService = pricingImportService;
        this.reportExportService = reportExportService;
        this.currentReportData = new ArrayList<>();
        this.currentCostReportData = new ArrayList<>();

        addClassName("reports-view");

        // Main layout
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setSpacing(true);
        mainLayout.setPadding(true);

        // Title
        H2 title = new H2("Reports");

        // Create tabs
        Tab customerRatingTab = new Tab("Customer Rating Report");
        Tab costReportTab = new Tab("Cost Report");

        Tabs tabs = new Tabs(customerRatingTab, costReportTab);
        tabs.setWidthFull();

        // Content container for tab panels
        Div contentContainer = new Div();
        contentContainer.setSizeFull();

        // Create the Customer Rating Report panel
        VerticalLayout customerRatingPanel = createCustomerRatingReportPanel();

        // Create the Cost Report panel
        VerticalLayout costReportPanel = createCostReportPanel();

        // Show the first tab by default
        contentContainer.add(customerRatingPanel);

        // Tab change listener
        tabs.addSelectedChangeListener(event -> {
            contentContainer.removeAll();
            Tab selectedTab = event.getSelectedTab();

            if (selectedTab == customerRatingTab) {
                contentContainer.add(customerRatingPanel);
            } else if (selectedTab == costReportTab) {
                contentContainer.add(costReportPanel);
            }
        });

        mainLayout.add(title, tabs, contentContainer);
        add(mainLayout);
    }

    /**
     * Create the Customer Rating Report panel with all its components
     * @return VerticalLayout containing the complete report UI
     */
    private VerticalLayout createCustomerRatingReportPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setSizeFull();
        panel.setSpacing(true);
        panel.setPadding(false);

        // Date range pickers
        startDatePicker = new DatePicker("Start Date");
        startDatePicker.setPlaceholder("Select start date");
        startDatePicker.setClearButtonVisible(true);
        startDatePicker.setWidth("200px");
        startDatePicker.setLocale(Locale.UK); // Use dd/MM/yyyy format
        // Set default to first day of current month
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.withDayOfMonth(1));

        endDatePicker = new DatePicker("End Date");
        endDatePicker.setPlaceholder("Select end date");
        endDatePicker.setClearButtonVisible(true);
        endDatePicker.setWidth("200px");
        endDatePicker.setLocale(Locale.UK); // Use dd/MM/yyyy format
        // Set default to last day of current month
        endDatePicker.setValue(now.withDayOfMonth(now.lengthOfMonth()));

        // Add date validation listeners
        startDatePicker.addValueChangeListener(event -> {
            LocalDate startDate = event.getValue();
            LocalDate endDate = endDatePicker.getValue();
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                endDatePicker.setValue(startDate);
            }
        });

        endDatePicker.addValueChangeListener(event -> {
            LocalDate endDate = event.getValue();
            LocalDate startDate = startDatePicker.getValue();
            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                startDatePicker.setValue(endDate);
            }
        });

        // Load Report button
        loadReportButton = new Button("Load Report", event -> loadReport());
        loadReportButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Generate XLS button
        generateXlsButton = new Button("Generate XLS", event -> generateXlsReport());
        generateXlsButton.setEnabled(false);
        generateXlsButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        // Report Grid
        reportGrid = new Grid<>(CustomerRatingReportDTO.class, false);
        reportGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        reportGrid.setHeight("600px");

        // Configure grid columns
        reportGrid.addColumn(CustomerRatingReportDTO::getCustomerDisplay)
                .setHeader("Customer")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true);

        reportGrid.addColumn(CustomerRatingReportDTO::getFormattedCost)
                .setHeader("Cost")
                .setAutoWidth(true)
                .setKey("cost")
                .setSortable(true);

        reportGrid.addColumn(CustomerRatingReportDTO::getFormattedAmount)
                .setHeader("Amount")
                .setAutoWidth(true)
                .setKey("amount")
                .setSortable(true);

        reportGrid.addColumn(CustomerRatingReportDTO::getFormattedGPPercentage)
                .setHeader("GP%")
                .setAutoWidth(true)
                .setKey("gp")
                .setSortable(true);

        reportGrid.addColumn(CustomerRatingReportDTO::getOriginalRating)
                .setHeader("Original Rating")
                .setKey("originalRating")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true)
                .setComparator((dto1, dto2) -> compareRatingStrings(dto1.getOriginalRating(), dto2.getOriginalRating()));

        reportGrid.addColumn(CustomerRatingReportDTO::getModifiedRating)
                .setHeader("Modified Rating")
                .setKey("modifiedRating")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true)
                .setComparator((dto1, dto2) -> compareRatingStrings(dto1.getModifiedRating(), dto2.getModifiedRating()));

        reportGrid.addColumn(CustomerRatingReportDTO::getClaudeRating)
                .setHeader("Claude Rating")
                .setKey("claudeRating")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true)
                .setComparator((dto1, dto2) -> compareRatingStrings(dto1.getClaudeRating(), dto2.getClaudeRating()));

        // Add footer row for totals
        addGridFooter();

        // Date range filter layout
        HorizontalLayout filterLayout = new HorizontalLayout();
        filterLayout.setSpacing(true);
        filterLayout.setAlignItems(HorizontalLayout.Alignment.END);
        filterLayout.add(startDatePicker, endDatePicker, loadReportButton);

        // Button layout
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.setSpacing(true);
        buttonLayout.add(generateXlsButton);

        // Add all components to panel
        panel.add(filterLayout, reportGrid, buttonLayout);

        return panel;
    }

    /**
     * Add footer row to grid showing totals
     */
    private void addGridFooter() {
        var footerRow = reportGrid.appendFooterRow();

        // Set totals in respective columns
        footerRow.getCell(reportGrid.getColumnByKey("cost")).setText(calculateTotalCost());
        footerRow.getCell(reportGrid.getColumnByKey("amount")).setText(calculateTotalAmount());
        footerRow.getCell(reportGrid.getColumnByKey("gp")).setText(calculateOverallGP());
    }

    /**
     * Load report data from service
     */
    private void loadReport() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        // Validate dates
        if (startDate == null || endDate == null) {
            showErrorNotification("Please select both start and end dates");
            return;
        }

        if (startDate.isAfter(endDate)) {
            showErrorNotification("Start date must be before end date");
            return;
        }

        try {
            // Load data from service
            currentReportData = pricingImportService.getCustomerRatingReport(startDate, endDate);

            // Update grid
            reportGrid.setItems(currentReportData);

            // Update footer totals
            updateGridFooter();

            // Enable/disable XLS button based on data
            generateXlsButton.setEnabled(!currentReportData.isEmpty());

            // Show success notification
            String message = String.format("Loaded %d customer records", currentReportData.size());
            showSuccessNotification(message);

            log.info("Loaded customer rating report: {} records from {} to {}",
                    currentReportData.size(), startDate, endDate);

        } catch (Exception e) {
            log.error("Error loading customer rating report", e);
            showErrorNotification("Error loading report: " + e.getMessage());
        }
    }

    /**
     * Generate and download XLS report
     */
    private void generateXlsReport() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (currentReportData.isEmpty()) {
            showErrorNotification("No data to export");
            return;
        }

        try {
            // Generate Excel file
            byte[] excelBytes = reportExportService.generateCustomerRatingReportXLS(
                    currentReportData, startDate, endDate);

            // Create filename with date range
            String filename = String.format("Rating_Report_%s_to_%s.xlsx",
                    startDate.toString().replace("-", ""),
                    endDate.toString().replace("-", ""));

            // Create download resource using StreamResource
            StreamResource resource = new StreamResource(filename,
                    () -> new ByteArrayInputStream(excelBytes));
            resource.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            // Create anchor for download
            Anchor downloadLink = new Anchor(resource, "");
            downloadLink.getElement().setAttribute("download", true);
            downloadLink.setTarget("_blank");
            downloadLink.getStyle().set("display", "none"); // Hide the anchor

            // Add to UI (Vaadin will clean up on navigation)
            add(downloadLink);

            // Trigger download immediately
            downloadLink.getElement().callJsFunction("click");

            showSuccessNotification("Report generated successfully! Downloading...");

            log.info("Generated XLS report: {} records, {} bytes", currentReportData.size(), excelBytes.length);

        } catch (IOException e) {
            log.error("Error generating XLS report", e);
            showErrorNotification("Error generating Excel file: " + e.getMessage());
        }
    }

    /**
     * Update grid footer with recalculated totals
     */
    private void updateGridFooter() {
        var footerRow = reportGrid.getFooterRows().get(0);

        // Update totals in respective columns
        footerRow.getCell(reportGrid.getColumnByKey("cost")).setText(calculateTotalCost());
        footerRow.getCell(reportGrid.getColumnByKey("amount")).setText(calculateTotalAmount());
        footerRow.getCell(reportGrid.getColumnByKey("gp")).setText(calculateOverallGP());
    }

    /**
     * Calculate total cost from current data
     */
    private String calculateTotalCost() {
        BigDecimal total = currentReportData.stream()
                .map(CustomerRatingReportDTO::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return String.format("$%,.2f", total);
    }

    /**
     * Calculate total amount from current data
     */
    private String calculateTotalAmount() {
        BigDecimal total = currentReportData.stream()
                .map(CustomerRatingReportDTO::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return String.format("$%,.2f", total);
    }

    /**
     * Calculate overall GP% from current data
     */
    private String calculateOverallGP() {
        BigDecimal totalCost = currentReportData.stream()
                .map(CustomerRatingReportDTO::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAmount = currentReportData.stream()
                .map(CustomerRatingReportDTO::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal gp = CustomerRatingReportDTO.calculateGPPercentage(totalAmount, totalCost);
        return String.format("%.2f%%", gp);
    }

    /**
     * Show success notification
     */
    private void showSuccessNotification(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    /**
     * Show error notification
     */
    private void showErrorNotification(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    /**
     * Compare two rating strings numerically.
     * Handles empty strings and non-numeric values.
     *
     * @param rating1 First rating string to compare
     * @param rating2 Second rating string to compare
     * @return Negative if rating1 < rating2, zero if equal, positive if rating1 > rating2
     */
    private int compareRatingStrings(String rating1, String rating2) {
        // Handle nulls and empty strings - sort them last
        if (rating1 == null || rating1.trim().isEmpty()) {
            return (rating2 == null || rating2.trim().isEmpty()) ? 0 : 1;
        }
        if (rating2 == null || rating2.trim().isEmpty()) {
            return -1;
        }

        try {
            Double value1 = Double.parseDouble(rating1.trim());
            Double value2 = Double.parseDouble(rating2.trim());
            return value1.compareTo(value2);
        } catch (NumberFormatException e) {
            // If parsing fails, fall back to string comparison
            return rating1.compareTo(rating2);
        }
    }

    /**
     * Create the Cost Report panel with all its components
     * @return VerticalLayout containing the complete cost report UI
     */
    private VerticalLayout createCostReportPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setSizeFull();
        panel.setSpacing(true);
        panel.setPadding(false);

        // File selection ComboBox
        fileSelectionComboBox = new ComboBox<>("Select Import File");
        fileSelectionComboBox.setPlaceholder("Choose a file...");
        fileSelectionComboBox.setWidth("400px");
        fileSelectionComboBox.setClearButtonVisible(true);

        // Load import summaries and set items
        List<ImportSummary> importSummaries = pricingImportService.getAllImportSummaries();
        fileSelectionComboBox.setItems(importSummaries);

        // Set item label generator to show filename and date
        fileSelectionComboBox.setItemLabelGenerator(summary -> {
            if (summary.getImportDate() != null) {
                return String.format("%s (%s)",
                        summary.getFilename(),
                        summary.getImportDate().toLocalDate().format(
                                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            }
            return summary.getFilename();
        });

        // Load Report button
        loadCostReportButton = new Button("Load Report", event -> loadCostReport());
        loadCostReportButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loadCostReportButton.setEnabled(false); // Disabled until file is selected

        // Enable/disable load button based on file selection
        fileSelectionComboBox.addValueChangeListener(event -> {
            loadCostReportButton.setEnabled(event.getValue() != null);
        });

        // Generate XLS button
        generateCostReportXlsButton = new Button("Generate XLS", event -> generateCostReportXls());
        generateCostReportXlsButton.setEnabled(false);
        generateCostReportXlsButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        // Cost Report Grid
        costReportGrid = new Grid<>(CostReportDTO.class, false);
        costReportGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        costReportGrid.setHeight("600px");

        // Configure grid columns
        costReportGrid.addColumn(CostReportDTO::getProductCode)
                .setHeader("Stock Code")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true);

        costReportGrid.addColumn(CostReportDTO::getProductDescription)
                .setHeader("Product Description")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true);

        costReportGrid.addColumn(CostReportDTO::getCustomerName)
                .setHeader("Customer Name")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true);

        costReportGrid.addColumn(CostReportDTO::getInvoiceNumber)
                .setHeader("Invoice Number")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true);

        costReportGrid.addColumn(CostReportDTO::getFormattedTransactionDate)
                .setHeader("Transaction Date")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator((dto1, dto2) -> {
                    LocalDate date1 = dto1.getTransactionDate();
                    LocalDate date2 = dto2.getTransactionDate();
                    if (date1 == null && date2 == null) return 0;
                    if (date1 == null) return 1;
                    if (date2 == null) return -1;
                    return date1.compareTo(date2);
                });

        costReportGrid.addColumn(CostReportDTO::getFormattedQuantity)
                .setHeader("Quantity")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator((dto1, dto2) -> {
                    BigDecimal qty1 = dto1.getQuantity();
                    BigDecimal qty2 = dto2.getQuantity();
                    if (qty1 == null && qty2 == null) return 0;
                    if (qty1 == null) return 1;
                    if (qty2 == null) return -1;
                    return qty1.compareTo(qty2);
                });

        costReportGrid.addColumn(CostReportDTO::getFormattedCost)
                .setHeader("Cost")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator((dto1, dto2) -> {
                    BigDecimal cost1 = dto1.getCost();
                    BigDecimal cost2 = dto2.getCost();
                    if (cost1 == null && cost2 == null) return 0;
                    if (cost1 == null) return 1;
                    if (cost2 == null) return -1;
                    return cost1.compareTo(cost2);
                });

        costReportGrid.addColumn(CostReportDTO::getFormattedStdcost)
                .setHeader("STDCOST")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator((dto1, dto2) -> {
                    BigDecimal stdcost1 = dto1.getStdcost();
                    BigDecimal stdcost2 = dto2.getStdcost();
                    if (stdcost1 == null && stdcost2 == null) return 0;
                    if (stdcost1 == null) return 1;
                    if (stdcost2 == null) return -1;
                    return stdcost1.compareTo(stdcost2);
                });

        costReportGrid.addColumn(CostReportDTO::getFormattedLineItemCostPrice)
                .setHeader("Line Item Cost Price")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator((dto1, dto2) -> {
                    BigDecimal price1 = dto1.getLineItemCostPrice();
                    BigDecimal price2 = dto2.getLineItemCostPrice();
                    if (price1 == null && price2 == null) return 0;
                    if (price1 == null) return 1;
                    if (price2 == null) return -1;
                    return price1.compareTo(price2);
                });

        costReportGrid.addColumn(CostReportDTO::getFormattedDifference)
                .setHeader("Difference")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator((dto1, dto2) -> {
                    BigDecimal diff1 = dto1.getDifference();
                    BigDecimal diff2 = dto2.getDifference();
                    if (diff1 == null && diff2 == null) return 0;
                    if (diff1 == null) return 1;
                    if (diff2 == null) return -1;
                    return diff1.compareTo(diff2);
                });

        // Filter layout
        HorizontalLayout filterLayout = new HorizontalLayout();
        filterLayout.setSpacing(true);
        filterLayout.setAlignItems(HorizontalLayout.Alignment.END);
        filterLayout.add(fileSelectionComboBox, loadCostReportButton);

        // Button layout
        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.setSpacing(true);
        buttonLayout.add(generateCostReportXlsButton);

        // Add all components to panel
        panel.add(filterLayout, costReportGrid, buttonLayout);

        return panel;
    }

    /**
     * Load cost report data from service
     */
    private void loadCostReport() {
        ImportSummary selectedFile = fileSelectionComboBox.getValue();

        if (selectedFile == null) {
            showErrorNotification("Please select an import file");
            return;
        }

        try {
            // Load data from service
            currentCostReportData = pricingImportService.getCostReport(selectedFile.getFilename());

            // Update grid
            costReportGrid.setItems(currentCostReportData);

            // Enable/disable XLS button based on data
            generateCostReportXlsButton.setEnabled(!currentCostReportData.isEmpty());

            // Show success notification
            String message = String.format("Loaded %d line items with cost below standard cost",
                    currentCostReportData.size());
            showSuccessNotification(message);

            log.info("Loaded cost report: {} records from file {}",
                    currentCostReportData.size(), selectedFile.getFilename());

        } catch (Exception e) {
            log.error("Error loading cost report", e);
            showErrorNotification("Error loading report: " + e.getMessage());
        }
    }

    /**
     * Generate and download XLS cost report
     */
    private void generateCostReportXls() {
        ImportSummary selectedFile = fileSelectionComboBox.getValue();

        if (selectedFile == null || currentCostReportData.isEmpty()) {
            showErrorNotification("No data to export");
            return;
        }

        try {
            // Generate Excel file
            byte[] excelBytes = reportExportService.generateCostReportXLS(
                    currentCostReportData, selectedFile.getFilename());

            // Create filename
            String filename = String.format("Cost_Report_%s.xlsx",
                    selectedFile.getFilename().replace(".xlsx", "").replace(".xls", ""));

            // Create download resource using StreamResource
            StreamResource resource = new StreamResource(filename,
                    () -> new ByteArrayInputStream(excelBytes));
            resource.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            // Create anchor for download
            Anchor downloadLink = new Anchor(resource, "");
            downloadLink.getElement().setAttribute("download", true);
            downloadLink.setTarget("_blank");
            downloadLink.getStyle().set("display", "none"); // Hide the anchor

            // Add to UI (Vaadin will clean up on navigation)
            add(downloadLink);

            // Trigger download immediately
            downloadLink.getElement().callJsFunction("click");

            showSuccessNotification("Report generated successfully! Downloading...");

            log.info("Generated Cost Report XLS: {} records, {} bytes",
                    currentCostReportData.size(), excelBytes.length);

        } catch (IOException e) {
            log.error("Error generating Cost Report XLS", e);
            showErrorNotification("Error generating Excel file: " + e.getMessage());
        }
    }
}
