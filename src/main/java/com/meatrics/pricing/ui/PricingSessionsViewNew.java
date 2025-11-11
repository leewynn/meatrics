package com.meatrics.pricing.ui;

import com.meatrics.base.ui.MainLayout;
import com.meatrics.pricing.*;
import com.meatrics.pricing.ui.component.CustomerEditDialog;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import com.vaadin.flow.router.BeforeLeaveEvent.ContinueNavigationAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Redesigned Pricing Sessions view with rule-based pricing engine integration.
 * Features nested column headers, historical vs new pricing comparison, and rule transparency.
 */
@Route(value = "pricing-sessions", layout = MainLayout.class)
@PageTitle("Pricing Sessions")
@Menu(order = 1, icon = "vaadin:edit", title = "Pricing Sessions")
public class PricingSessionsViewNew extends VerticalLayout implements BeforeLeaveObserver {

    private static final Logger log = LoggerFactory.getLogger(PricingSessionsViewNew.class);

    private final PricingImportService pricingImportService;
    private final CustomerRepository customerRepository;
    private final PricingSessionService pricingSessionService;
    private final PriceCalculationService priceCalculationService;

    private final Grid<GroupedLineItem> dataGrid;
    private final DatePicker startDatePicker;
    private final DatePicker endDatePicker;
    private final TextField customerNameFilter;
    private final TextField productFilter;
    private H2 titleComponent;

    // Backing list that holds date-filtered grouped records
    private List<GroupedLineItem> backingList = new ArrayList<>();

    // Session state tracking
    private PricingSession currentSession = null;
    private boolean hasUnsavedChanges = false;

    // Grid columns and footer (stored for updating totals and visibility control)
    private Grid.Column<GroupedLineItem> customerCol;
    private Grid.Column<GroupedLineItem> ratingCol;
    private Grid.Column<GroupedLineItem> productCodeCol;
    private Grid.Column<GroupedLineItem> productCol;
    private Grid.Column<GroupedLineItem> qtyCol;
    private Grid.Column<GroupedLineItem> lastCostCol;
    private Grid.Column<GroupedLineItem> lastPriceCol;
    private Grid.Column<GroupedLineItem> lastAmountCol;
    private Grid.Column<GroupedLineItem> lastGPCol;
    private Grid.Column<GroupedLineItem> lastGPPercentCol;
    private Grid.Column<GroupedLineItem> costDriftCol;
    private Grid.Column<GroupedLineItem> newCostCol;
    private Grid.Column<GroupedLineItem> newPriceCol;
    private Grid.Column<GroupedLineItem> newAmountCol;
    private Grid.Column<GroupedLineItem> newGPCol;
    private Grid.Column<GroupedLineItem> newGPPercentCol;
    private Grid.Column<GroupedLineItem> notesCol;
    private com.vaadin.flow.component.grid.FooterRow footerRow;

    // LocalStorage key for column visibility preferences
    private static final String STORAGE_KEY = "meatrics.pricingSessions.columnVisibility";

    public PricingSessionsViewNew(PricingImportService pricingImportService,
                                  CustomerRepository customerRepository,
                                  PricingSessionService pricingSessionService,
                                  PriceCalculationService priceCalculationService) {
        this.pricingImportService = pricingImportService;
        this.customerRepository = customerRepository;
        this.pricingSessionService = pricingSessionService;
        this.priceCalculationService = priceCalculationService;

        addClassName("pricing-sessions-view");
        setSizeFull();
        setSpacing(true);
        setPadding(true);

        // Title and session management buttons
        titleComponent = new H2("Pricing Sessions");

        Button saveSessionButton = new Button("Save Session", new Icon(VaadinIcon.DISC));
        saveSessionButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveSessionButton.addClickListener(event -> openSaveSessionDialog());

        Button loadSessionButton = new Button("Load Session", new Icon(VaadinIcon.FOLDER_OPEN));
        loadSessionButton.addClickListener(event -> openLoadSessionDialog());

        Button newSessionButton = new Button("New Session", new Icon(VaadinIcon.PLUS));
        newSessionButton.addClickListener(event -> handleNewSession());

        Button columnVisibilityButton = new Button("Show/Hide Columns", new Icon(VaadinIcon.EYE));
        columnVisibilityButton.addClickListener(event -> openColumnVisibilityDialog());

        HorizontalLayout sessionButtonsLayout = new HorizontalLayout(saveSessionButton, loadSessionButton, newSessionButton, columnVisibilityButton);
        sessionButtonsLayout.setSpacing(true);

        HorizontalLayout titleLayout = new HorizontalLayout(titleComponent, sessionButtonsLayout);
        titleLayout.setWidthFull();
        titleLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        titleLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        // Date range filter
        startDatePicker = new DatePicker("Start Date");
        startDatePicker.setPlaceholder("Select start date");
        startDatePicker.setClearButtonVisible(true);
        startDatePicker.setLocale(Locale.UK);
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.withDayOfMonth(1));

        endDatePicker = new DatePicker("End Date");
        endDatePicker.setPlaceholder("Select end date");
        endDatePicker.setClearButtonVisible(true);
        endDatePicker.setLocale(Locale.UK);
        endDatePicker.setValue(now.withDayOfMonth(now.lengthOfMonth()));

        // Date validation listeners
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

        Button applyFilterButton = new Button("Search", new Icon(VaadinIcon.FILTER));
        applyFilterButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        applyFilterButton.addClickListener(event -> applyFilter());

        Button applyRulesButton = new Button("Apply Rules", new Icon(VaadinIcon.MAGIC));
        applyRulesButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        applyRulesButton.addClickListener(event -> applyPricingRules());

        HorizontalLayout dateFilterLayout = new HorizontalLayout(
                startDatePicker, endDatePicker, applyFilterButton, applyRulesButton);
        dateFilterLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        dateFilterLayout.setSpacing(true);

        // Customer and Product filters
        customerNameFilter = new TextField("Customer Name");
        customerNameFilter.setPlaceholder("Filter by customer...");
        customerNameFilter.setClearButtonVisible(true);
        customerNameFilter.setValueChangeMode(ValueChangeMode.LAZY);
        customerNameFilter.addValueChangeListener(e -> applySecondaryFilters());

        productFilter = new TextField("Product");
        productFilter.setPlaceholder("Filter by product...");
        productFilter.setClearButtonVisible(true);
        productFilter.setValueChangeMode(ValueChangeMode.LAZY);
        productFilter.addValueChangeListener(e -> applySecondaryFilters());

        // Color legend for GP% indicators
        HorizontalLayout legendLayout = new HorizontalLayout();
        legendLayout.setSpacing(true);
        legendLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        legendLayout.getStyle()
            .set("padding", "var(--lumo-space-s)")
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        Icon infoIcon = new Icon(VaadinIcon.INFO_CIRCLE);
        infoIcon.setSize("16px");
        infoIcon.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span legendTitle = new Span("GP% Color Guide:");
        legendTitle.getStyle().set("font-weight", "600").set("color", "var(--lumo-body-text-color)");

        Span greenIndicator = new Span("■");
        greenIndicator.getStyle().set("color", "var(--lumo-success-color)").set("font-size", "18px").set("font-weight", "bold");
        Span greenText = new Span("Improved");
        greenText.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span redIndicator = new Span("■");
        redIndicator.getStyle().set("color", "var(--lumo-error-color)").set("font-size", "18px").set("font-weight", "bold");
        Span redText = new Span("Decreased");
        redText.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span grayIndicator = new Span("■");
        grayIndicator.getStyle().set("color", "var(--lumo-contrast-50pct)").set("font-size", "18px").set("font-weight", "bold");
        Span grayText = new Span("Unchanged");
        grayText.getStyle().set("color", "var(--lumo-secondary-text-color)");

        legendLayout.add(infoIcon, legendTitle, greenIndicator, greenText, redIndicator, redText, grayIndicator, grayText);

        HorizontalLayout secondaryFilterLayout = new HorizontalLayout(customerNameFilter, productFilter, legendLayout);
        secondaryFilterLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        secondaryFilterLayout.setSpacing(true);

        VerticalLayout filterLayout = new VerticalLayout(dateFilterLayout, secondaryFilterLayout);
        filterLayout.setSpacing(false);
        filterLayout.setPadding(false);

        // Create data grid
        dataGrid = createDataGrid();

        // Load column visibility preferences from browser localStorage
        loadColumnVisibility();

        // Add components to main layout
        add(titleLayout, filterLayout, dataGrid);
    }

    /**
     * Helper method to create column header with info icon tooltip
     */
    private HorizontalLayout createHeaderWithTooltip(String headerText, String tooltipText) {
        HorizontalLayout header = new HorizontalLayout();
        header.setSpacing(false);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle().set("gap", "4px");

        Span label = new Span(headerText);

        Icon infoIcon = VaadinIcon.INFO_CIRCLE_O.create();
        infoIcon.setSize("12px");
        infoIcon.getStyle().set("color", "var(--lumo-secondary-text-color)");
        infoIcon.setTooltipText(tooltipText);

        header.add(label, infoIcon);
        return header;
    }

    /**
     * Create the data grid with nested headers and all columns
     */
    private Grid<GroupedLineItem> createDataGrid() {
        Grid<GroupedLineItem> grid = new Grid<>(GroupedLineItem.class, false);
        grid.setSizeFull();
        grid.setMinHeight("600px");
        grid.setColumnReorderingAllowed(true);

        // BASIC INFO GROUP (5 columns)
        customerCol = grid.addColumn(new ComponentRenderer<>(item -> {
            Button customerButton = new Button(item.getCustomerName());
            customerButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            customerButton.getStyle().set("padding", "0");
            customerButton.addClickListener(e -> openCustomerEditDialog(item));
            return customerButton;
        })).setHeader(createHeaderWithTooltip("Customer", "Customer name (click to edit customer rating)"))
                .setKey("customerName").setAutoWidth(true).setResizable(true).setSortable(true)
                .setComparator((item1, item2) -> {
                    String name1 = item1.getCustomerName() != null ? item1.getCustomerName() : "";
                    String name2 = item2.getCustomerName() != null ? item2.getCustomerName() : "";
                    return name1.compareToIgnoreCase(name2);
                });

        ratingCol = grid.addColumn(item -> {
            String customerCode = item.getCustomerCode();
            if (customerCode == null || customerCode.trim().isEmpty()) {
                return "";
            }
            return customerRepository.findByCustomerCode(customerCode)
                    .map(customer -> customer.getCustomerRating() != null ? customer.getCustomerRating() : "")
                    .orElse("");
        }).setHeader(createHeaderWithTooltip("Rating", "Customer rating (A, B, C, etc.) for pricing rules"))
                .setKey("rating").setAutoWidth(true).setResizable(true).setSortable(true);

        productCodeCol = grid.addColumn(GroupedLineItem::getProductCode)
                .setHeader(createHeaderWithTooltip("Product Code", "Internal product SKU/code"))
                .setKey("productCode").setAutoWidth(true).setResizable(true).setSortable(true);

        productCol = grid.addColumn(GroupedLineItem::getProductDescription)
                .setHeader(createHeaderWithTooltip("Product", "Product description"))
                .setKey("product").setAutoWidth(true).setFlexGrow(1).setResizable(true).setSortable(true);

        qtyCol = grid.addColumn(item ->
                String.format("%.2f", item.getTotalQuantity() != null ? item.getTotalQuantity() : BigDecimal.ZERO))
                .setHeader(createHeaderWithTooltip("Qty", "Total quantity sold in selected date range"))
                .setKey("quantity").setAutoWidth(true).setResizable(true).setSortable(true);

        // HISTORICAL (LAST) GROUP (5 columns)
        lastCostCol = grid.addColumn(item ->
                formatCurrency(item.getLastCost()))
                .setHeader(createHeaderWithTooltip("Cost", "Unit cost you paid the supplier in the last cycle"))
                .setKey("lastCost").setAutoWidth(true).setResizable(true).setSortable(true);

        lastPriceCol = grid.addColumn(item ->
                formatCurrency(item.getLastUnitSellPrice()))
                .setHeader(createHeaderWithTooltip("Price", "Unit sell price charged to customer in the last cycle"))
                .setKey("lastPrice").setAutoWidth(true).setResizable(true).setSortable(true);

        lastAmountCol = grid.addColumn(item ->
                formatCurrency(item.getLastAmount()))
                .setHeader(createHeaderWithTooltip("Amount", "Total revenue: Price × Quantity from last cycle"))
                .setKey("lastAmount").setAutoWidth(true).setResizable(true).setSortable(true);

        lastGPCol = grid.addColumn(item ->
                formatCurrency(item.getLastGrossProfit()))
                .setHeader(createHeaderWithTooltip("GP $", "Gross profit in dollars: Amount - (Cost × Quantity)"))
                .setKey("lastGP").setAutoWidth(true).setResizable(true).setSortable(true);

        lastGPPercentCol = grid.addColumn(item ->
                formatGPPercent(item.getLastGrossProfit(), item.getLastAmount()))
                .setHeader(createHeaderWithTooltip("GP %", "Gross profit percentage: (GP $ / Amount) × 100"))
                .setKey("lastGPPercent").setAutoWidth(true).setResizable(true).setSortable(true);

        // COST DRIFT COLUMN (bridge between Historical and New Pricing)
        costDriftCol = grid.addComponentColumn(item -> {
            BigDecimal lastCost = item.getLastCost();
            BigDecimal newCost = item.getIncomingCost();

            // Show N/A for missing or zero costs (data quality issue)
            if (lastCost == null || newCost == null ||
                lastCost.compareTo(BigDecimal.ZERO) == 0 ||
                newCost.compareTo(BigDecimal.ZERO) == 0) {
                Span naSpan = new Span("N/A");
                naSpan.getStyle().set("color", "var(--lumo-disabled-text-color)");
                return naSpan;
            }

            // Calculate absolute and percentage drift
            BigDecimal absoluteDrift = newCost.subtract(lastCost);
            BigDecimal percentDrift = absoluteDrift
                    .divide(lastCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Format: +$2.50 (+15.3%) or -$1.20 (-8.2%)
            String sign = absoluteDrift.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            String driftText = String.format("%s%s (%s%.1f%%)",
                    sign, formatCurrency(absoluteDrift).replace("$", "$"),
                    sign, percentDrift);

            Span driftSpan = new Span(driftText);

            // Color code based on magnitude and direction
            String color = null;
            if (percentDrift.abs().compareTo(BigDecimal.valueOf(20)) > 0) {
                // High drift (>20%)
                color = percentDrift.compareTo(BigDecimal.ZERO) > 0 ?
                        "var(--lumo-error-color)" : "var(--lumo-success-color)";
            } else if (percentDrift.abs().compareTo(BigDecimal.valueOf(5)) > 0) {
                // Moderate drift (5-20%)
                color = percentDrift.compareTo(BigDecimal.ZERO) > 0 ?
                        "var(--lumo-warning-text-color)" : "var(--lumo-success-text-color)";
            } else {
                // Small drift (<5%)
                color = "var(--lumo-contrast-70pct)";
            }

            if (color != null) {
                driftSpan.getStyle().set("color", color).set("font-weight", "500");
            }

            return driftSpan;
        }).setHeader(createHeaderWithTooltip("Cost Drift", "Change in unit cost: New Cost - Last Cost (absolute and %)"))
                .setKey("costDrift").setAutoWidth(true).setResizable(true)
                .setComparator((item1, item2) -> {
                    BigDecimal cost1Last = item1.getLastCost();
                    BigDecimal cost1New = item1.getIncomingCost();
                    BigDecimal cost2Last = item2.getLastCost();
                    BigDecimal cost2New = item2.getIncomingCost();

                    // N/A items sort to the end
                    boolean item1Invalid = cost1Last == null || cost1New == null ||
                            cost1Last.compareTo(BigDecimal.ZERO) == 0 ||
                            cost1New.compareTo(BigDecimal.ZERO) == 0;
                    boolean item2Invalid = cost2Last == null || cost2New == null ||
                            cost2Last.compareTo(BigDecimal.ZERO) == 0 ||
                            cost2New.compareTo(BigDecimal.ZERO) == 0;

                    if (item1Invalid && item2Invalid) return 0;
                    if (item1Invalid) return 1;
                    if (item2Invalid) return -1;

                    BigDecimal drift1 = cost1New.subtract(cost1Last).divide(cost1Last, 4, RoundingMode.HALF_UP);
                    BigDecimal drift2 = cost2New.subtract(cost2Last).divide(cost2Last, 4, RoundingMode.HALF_UP);
                    return drift1.compareTo(drift2);
                })
                .setSortable(true);

        // NEW PRICING GROUP (5 columns)
        newCostCol = grid.addComponentColumn(item -> {
            HorizontalLayout layout = new HorizontalLayout();
            layout.setSpacing(false);
            layout.setAlignItems(FlexComponent.Alignment.CENTER);
            layout.getStyle().set("gap", "4px");

            // Cost value
            Span costSpan = new Span(formatCurrency(item.getIncomingCost()));

            BigDecimal lastCost = item.getLastCost();
            BigDecimal newCost = item.getIncomingCost();

            // Only show indicator if we have valid costs (not null, not zero)
            if (lastCost != null && newCost != null &&
                lastCost.compareTo(BigDecimal.ZERO) != 0 &&
                newCost.compareTo(BigDecimal.ZERO) != 0) {

                BigDecimal changePercent = newCost.subtract(lastCost)
                        .divide(lastCost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                Icon icon;
                String colorStyle;

                if (changePercent.compareTo(BigDecimal.valueOf(20)) > 0) {
                    // High increase (>20%) - strong red, circular arrow (bad for margins)
                    icon = VaadinIcon.ARROW_CIRCLE_UP.create();
                    colorStyle = "var(--lumo-error-color)";
                } else if (changePercent.compareTo(BigDecimal.valueOf(5)) > 0) {
                    // Moderate increase (5-20%) - warning arrow
                    icon = VaadinIcon.ARROW_UP.create();
                    colorStyle = "var(--lumo-warning-text-color)";
                } else if (changePercent.compareTo(BigDecimal.valueOf(-5)) >= 0) {
                    // Small change (-5% to +5%) - neutral, no icon
                    icon = null;
                    colorStyle = null;
                } else if (changePercent.compareTo(BigDecimal.valueOf(-20)) >= 0) {
                    // Moderate decrease (-20% to -5%) - success arrow (good for margins)
                    icon = VaadinIcon.ARROW_DOWN.create();
                    colorStyle = "var(--lumo-success-text-color)";
                } else {
                    // High decrease (<-20%) - strong green, circular arrow (great for margins)
                    icon = VaadinIcon.ARROW_CIRCLE_DOWN.create();
                    colorStyle = "var(--lumo-success-color)";
                }

                if (icon != null) {
                    icon.getStyle().set("color", colorStyle);
                    icon.setSize("14px");
                    icon.setTooltipText(String.format("Cost change: %+.1f%%", changePercent));
                    layout.add(costSpan, icon);
                } else {
                    layout.add(costSpan);
                }
            } else {
                // No comparison available (missing or zero costs)
                layout.add(costSpan);
            }

            return layout;
        }).setHeader(createHeaderWithTooltip("Cost", "Current unit cost from supplier (imported from cost file)"))
                .setKey("newCost").setAutoWidth(true).setResizable(true)
                .setComparator((item1, item2) -> {
                    BigDecimal cost1 = item1.getIncomingCost() != null ? item1.getIncomingCost() : BigDecimal.ZERO;
                    BigDecimal cost2 = item2.getIncomingCost() != null ? item2.getIncomingCost() : BigDecimal.ZERO;
                    return cost1.compareTo(cost2);
                })
                .setSortable(true)
                .setClassNameGenerator(item -> "new-pricing");

        newPriceCol = grid.addComponentColumn(item -> {
            HorizontalLayout layout = new HorizontalLayout();
            layout.setSpacing(false);
            layout.setAlignItems(FlexComponent.Alignment.CENTER);

            Span priceSpan = new Span(formatCurrency(item.getNewUnitSellPrice()));

            // Style as a clickable link
            priceSpan.getStyle()
                .set("color", "#1676f3")
                .set("cursor", "pointer")
                .set("text-decoration", "none");

            // Add hover effect using CSS class
            priceSpan.addClassName("price-link");
            priceSpan.getElement().executeJs(
                "this.addEventListener('mouseenter', () => { this.style.textDecoration = 'underline'; });" +
                "this.addEventListener('mouseleave', () => { this.style.textDecoration = 'none'; });"
            );

            // Override with orange background for manual overrides (higher priority)
            if (item.isManualOverride()) {
                priceSpan.getStyle()
                    .set("background-color", "orange")
                    .set("color", "black")
                    .set("padding", "2px 4px")
                    .set("border-radius", "3px");
            }

            layout.add(priceSpan);

            // Make entire layout clickable to open consolidated dialog
            layout.getStyle().set("cursor", "pointer");
            layout.addClickListener(e -> openPriceEditDialog(item));

            return layout;
        }).setHeader(createHeaderWithTooltip("Price", "Proposed unit sell price (calculated by rules or manually set)"))
                .setKey("newPrice").setAutoWidth(true).setResizable(true)
                .setComparator((item1, item2) -> {
                    BigDecimal price1 = item1.getNewUnitSellPrice() != null ? item1.getNewUnitSellPrice() : BigDecimal.ZERO;
                    BigDecimal price2 = item2.getNewUnitSellPrice() != null ? item2.getNewUnitSellPrice() : BigDecimal.ZERO;
                    return price1.compareTo(price2);
                })
                .setSortable(true)
                .setClassNameGenerator(item -> "new-pricing");

        newAmountCol = grid.addColumn(item -> formatCurrency(item.getNewAmount()))
                .setHeader(createHeaderWithTooltip("Amount", "Projected total revenue: New Price × Quantity"))
                .setKey("newAmount").setAutoWidth(true).setResizable(true).setSortable(true)
                .setClassNameGenerator(item -> "new-pricing");

        newGPCol = grid.addComponentColumn(item -> {
            BigDecimal newGP = item.getNewGrossProfit();
            Span gpSpan = new Span(formatCurrency(newGP));

            // Color code based on GP% comparison (same as GP% column)
            // Both GP $ and GP % should show consistent colors based on GP% movement
            BigDecimal newGPPercent = calculateGPPercent(item.getNewGrossProfit(), item.getNewAmount());
            BigDecimal lastGPPercent = calculateGPPercent(item.getLastGrossProfit(), item.getLastAmount());
            BigDecimal tolerance = new BigDecimal("0.1"); // 0.1 percentage point tolerance

            String color = determineGPPercentColor(newGPPercent, lastGPPercent, tolerance);

            if (color != null) {
                gpSpan.getStyle().set("color", color).set("font-weight", "bold");
            }

            return gpSpan;
        }).setHeader(createHeaderWithTooltip("GP $", "Projected gross profit: New Amount - (New Cost × Quantity)"))
                .setKey("newGP").setAutoWidth(true).setResizable(true)
                .setComparator((item1, item2) -> {
                    BigDecimal gp1 = item1.getNewGrossProfit() != null ? item1.getNewGrossProfit() : BigDecimal.ZERO;
                    BigDecimal gp2 = item2.getNewGrossProfit() != null ? item2.getNewGrossProfit() : BigDecimal.ZERO;
                    return gp1.compareTo(gp2);
                })
                .setSortable(true)
                .setClassNameGenerator(item -> "new-pricing");

        newGPPercentCol = grid.addComponentColumn(item -> {
            String gpPercentText = formatGPPercent(item.getNewGrossProfit(), item.getNewAmount());
            BigDecimal newGPPercent = calculateGPPercent(item.getNewGrossProfit(), item.getNewAmount());
            BigDecimal lastGPPercent = calculateGPPercent(item.getLastGrossProfit(), item.getLastAmount());
            BigDecimal tolerance = new BigDecimal("0.1"); // 0.1 percentage point tolerance

            Span gpPercentSpan = new Span(gpPercentText);

            String color = determineGPPercentColor(newGPPercent, lastGPPercent, tolerance);

            // Debug logging for specific edge cases
            if (item.getProductCode() != null &&
                (item.getProductCode().equals("SCRM200") || item.getProductCode().equals("LFOCH"))) {
                log.info("GP% Coloring Debug - Product: {}, Customer: {}, " +
                        "NewGP$: {}, NewAmount: {}, NewGP%_calc: {}, NewGP%_display: {}, " +
                        "LastGP$: {}, LastAmount: {}, LastGP%_calc: {}, LastGP%_display: {}, " +
                        "Difference: {}, AbsDiff: {}, Tolerance: {}, " +
                        "Color: {}, Logic: new>last={}, new<last={}, diff>tol={}",
                        item.getProductCode(), item.getCustomerName(),
                        item.getNewGrossProfit(), item.getNewAmount(), newGPPercent, gpPercentText,
                        item.getLastGrossProfit(), item.getLastAmount(), lastGPPercent,
                        formatGPPercent(item.getLastGrossProfit(), item.getLastAmount()),
                        newGPPercent != null && lastGPPercent != null ?
                            newGPPercent.subtract(lastGPPercent) : "N/A",
                        newGPPercent != null && lastGPPercent != null ?
                            newGPPercent.subtract(lastGPPercent).abs() : "N/A",
                        tolerance, color,
                        newGPPercent != null && lastGPPercent != null ?
                            newGPPercent.compareTo(lastGPPercent) > 0 : "N/A",
                        newGPPercent != null && lastGPPercent != null ?
                            newGPPercent.compareTo(lastGPPercent) < 0 : "N/A",
                        newGPPercent != null && lastGPPercent != null ?
                            newGPPercent.subtract(lastGPPercent).abs().compareTo(tolerance) > 0 : "N/A");
            }

            if (color != null) {
                gpPercentSpan.getStyle().set("color", color).set("font-weight", "bold");
            }

            return gpPercentSpan;
        }).setHeader(createHeaderWithTooltip("GP %", "Projected margin: (New GP $ / New Amount) × 100. Green = improved, Red = decreased"))
                .setKey("newGPPercent").setAutoWidth(true).setResizable(true)
                .setComparator((item1, item2) -> {
                    BigDecimal gp1 = calculateGPPercent(item1.getNewGrossProfit(), item1.getNewAmount());
                    BigDecimal gp2 = calculateGPPercent(item2.getNewGrossProfit(), item2.getNewAmount());
                    if (gp1 == null && gp2 == null) return 0;
                    if (gp1 == null) return -1;
                    if (gp2 == null) return 1;
                    return gp1.compareTo(gp2);
                })
                .setSortable(true)
                .setClassNameGenerator(item -> "new-pricing");

        // NOTES column
        notesCol = grid.addColumn(item ->
                item.isManualOverride() ? "Manual Override" : "")
                .setHeader("Notes").setKey("notes").setAutoWidth(true).setResizable(true);

        // Create nested headers
        HeaderRow topHeader = grid.prependHeaderRow();
        HeaderRow bottomHeader = grid.getHeaderRows().get(0);

        topHeader.join(customerCol, ratingCol, productCodeCol, productCol, qtyCol).setText("Basic Info");
        topHeader.join(lastCostCol, lastPriceCol, lastAmountCol, lastGPCol, lastGPPercentCol).setText("Historical (Last)");
        topHeader.getCell(costDriftCol).setText("Δ Cost");  // Bridge column - cost variance
        topHeader.join(newCostCol, newPriceCol, newAmountCol, newGPCol, newGPPercentCol).setText("New Pricing");
        topHeader.getCell(notesCol).setText("");  // No group header for notes

        // Add footer row for totals
        footerRow = grid.appendFooterRow();
        footerRow.getCell(customerCol).setText("Total:");

        return grid;
    }

    /**
     * Update footer totals based on currently displayed items
     */
    private void updateFooterTotals(List<GroupedLineItem> items) {
        if (footerRow == null || items == null) {
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

        // Calculate weighted average GP%
        BigDecimal lastGPPercent = calculateGPPercent(totalLastGP, totalLastAmount);
        BigDecimal newGPPercent = calculateGPPercent(totalNewGP, totalNewAmount);

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
                    sign, formatCurrency(totalCostDrift).replace("$", "$"),
                    sign, percentDrift);
        }

        // Update footer cells
        footerRow.getCell(qtyCol).setText(String.format("%.2f", totalQty));
        footerRow.getCell(lastAmountCol).setText(formatCurrency(totalLastAmount));
        footerRow.getCell(lastGPCol).setText(formatCurrency(totalLastGP));
        footerRow.getCell(lastGPPercentCol).setText(formatGPPercent(totalLastGP, totalLastAmount));
        footerRow.getCell(costDriftCol).setText(costDriftText);
        footerRow.getCell(newAmountCol).setText(formatCurrency(totalNewAmount));
        footerRow.getCell(newGPCol).setText(formatCurrency(totalNewGP));
        footerRow.getCell(newGPPercentCol).setText(formatGPPercent(totalNewGP, totalNewAmount));
    }

    /**
     * Recalculate intermediate pricing results for loaded sessions.
     * Applies each rule step-by-step to recreate the calculation breakdown.
     */
    private List<BigDecimal> recalculateIntermediateResults(GroupedLineItem item, List<PricingRule> appliedRules) {
        List<BigDecimal> intermediates = new ArrayList<>();
        BigDecimal currentPrice = item.getIncomingCost();
        intermediates.add(currentPrice); // Starting cost

        for (PricingRule rule : appliedRules) {
            currentPrice = applyRuleToPrice(currentPrice, rule, item);
            intermediates.add(currentPrice);
        }

        return intermediates;
    }

    /**
     * Apply a single pricing rule to a price (mirrors PriceCalculationService logic)
     */
    private BigDecimal applyRuleToPrice(BigDecimal currentPrice, PricingRule rule, GroupedLineItem item) {
        if (currentPrice == null || rule == null) return currentPrice;

        String method = rule.getPricingMethod();
        BigDecimal value = rule.getPricingValue();

        if (value == null && !"MAINTAIN_GP_PERCENT".equals(method)) {
            return currentPrice;
        }

        switch (method) {
            case "COST_PLUS_PERCENT":
                return currentPrice.multiply(value);

            case "COST_PLUS_FIXED":
                return currentPrice.add(value);

            case "FIXED_PRICE":
                return value;

            case "MAINTAIN_GP_PERCENT":
                // For loaded sessions, use the stored newUnitSellPrice directly
                // since we don't have the exact historical GP% calculation context
                if (item.getLastGrossProfit() != null && item.getLastAmount() != null
                    && item.getLastAmount().compareTo(BigDecimal.ZERO) != 0) {
                    // Calculate historical GP%
                    BigDecimal historicalGP = item.getLastGrossProfit().divide(item.getLastAmount(), 6, RoundingMode.HALF_UP);
                    BigDecimal divisor = BigDecimal.ONE.subtract(historicalGP);
                    if (divisor.compareTo(BigDecimal.ZERO) > 0) {
                        return item.getIncomingCost().divide(divisor, 6, RoundingMode.HALF_UP);
                    }
                } else if (value != null) {
                    // Use default GP% from rule
                    BigDecimal divisor = BigDecimal.ONE.subtract(value);
                    if (divisor.compareTo(BigDecimal.ZERO) > 0) {
                        return item.getIncomingCost().divide(divisor, 6, RoundingMode.HALF_UP);
                    }
                }
                return currentPrice;

            default:
                return currentPrice;
        }
    }

    /**
     * Format currency value
     */
    private String formatCurrency(BigDecimal value) {
        return value != null ? String.format("$%.2f", value) : "-";
    }

    /**
     * Calculate GP% from gross profit and amount
     * Formula: (GP / Amount) × 100
     * Returns value already multiplied by 100 (e.g., 23.5 for 23.5%)
     * with 6 decimal places precision for accurate comparison
     */
    private BigDecimal calculateGPPercent(BigDecimal grossProfit, BigDecimal amount) {
        if (grossProfit == null || amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        // Use 6 decimal precision throughout for consistency
        return grossProfit.divide(amount, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Format GP% value for display
     */
    private String formatGPPercent(BigDecimal grossProfit, BigDecimal amount) {
        BigDecimal gpPercent = calculateGPPercent(grossProfit, amount);
        return gpPercent != null ? String.format("%.1f%%", gpPercent) : "-";
    }

    /**
     * Determine GP% coloring based on comparison with tolerance
     * Returns: "green" if improved, "red" if declined, null if within tolerance or no change
     */
    private String determineGPPercentColor(BigDecimal newGPPercent, BigDecimal lastGPPercent, BigDecimal tolerance) {
        if (newGPPercent == null || lastGPPercent == null) {
            return null;
        }

        BigDecimal difference = newGPPercent.subtract(lastGPPercent);
        BigDecimal absDifference = difference.abs();

        // No color if within tolerance (0.1 percentage points)
        if (absDifference.compareTo(tolerance) <= 0) {
            return null;
        }

        // Green if GP% increased (positive difference)
        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            return "green";
        }

        // Red if GP% decreased (negative difference)
        if (difference.compareTo(BigDecimal.ZERO) < 0) {
            return "red";
        }

        return null;
    }

    /**
     * Apply pricing rules to all items in the backing list
     */
    private void applyPricingRules() {
        if (backingList.isEmpty()) {
            showErrorNotification("No data loaded. Please apply a date filter first.");
            return;
        }

        ConfirmDialog confirmDialog = new ConfirmDialog();
        confirmDialog.setHeader("Apply Pricing Rules");
        confirmDialog.setText("This will calculate new prices for all " + backingList.size() +
                " items using pricing rules. Continue?");
        confirmDialog.setCancelable(true);
        confirmDialog.setCancelText("Cancel");
        confirmDialog.setConfirmText("Apply Rules");
        confirmDialog.setConfirmButtonTheme("success primary");

        confirmDialog.addConfirmListener(event -> {
            try {
                int successCount = 0;
                int errorCount = 0;

                for (GroupedLineItem item : backingList) {
                    try {
                        // Get customer
                        Customer customer = customerRepository.findByCustomerCode(item.getCustomerCode())
                                .orElse(null);

                        // Calculate price using pricing engine with current date
                        PricingResult result = priceCalculationService.calculatePrice(item, LocalDate.now(), customer);

                        // Apply results to item - support multi-rule
                        item.setNewUnitSellPrice(result.getCalculatedPrice());
                        item.setAppliedRules(result.getAppliedRules());
                        item.setIntermediateResults(result.getIntermediateResults());
                        item.setManualOverride(false);

                        // Recalculate amounts and GP
                        recalculateItemFields(item);

                        successCount++;
                    } catch (Exception e) {
                        log.error("Error calculating price for item: " + item.getGroupingKey(), e);
                        errorCount++;
                    }
                }

                hasUnsavedChanges = true;
                updateTitleStyle();
                applySecondaryFilters(); // Refresh grid

                String message = String.format("Applied rules to %d items", successCount);
                if (errorCount > 0) {
                    message += String.format(" (%d errors)", errorCount);
                }
                showSuccessNotification(message);

                log.info("Applied pricing rules: {} successful, {} errors", successCount, errorCount);

            } catch (Exception e) {
                log.error("Error applying pricing rules", e);
                showErrorNotification("Error applying rules: " + e.getMessage());
            }
        });

        confirmDialog.open();
    }

    /**
     * Recalculate derived fields for an item
     */
    private void recalculateItemFields(GroupedLineItem item) {
        if (item.getNewUnitSellPrice() != null && item.getTotalQuantity() != null) {
            // Calculate new amount - use scale 6 for storage precision
            // Display rounding to 2 decimals handled by formatCurrency()
            item.setNewAmount(item.getNewUnitSellPrice().multiply(item.getTotalQuantity())
                    .setScale(6, RoundingMode.HALF_UP));

            // Calculate new gross profit - use scale 6 for storage precision
            if (item.getIncomingCost() != null) {
                BigDecimal totalCost = item.getIncomingCost().multiply(item.getTotalQuantity());
                item.setNewGrossProfit(item.getNewAmount().subtract(totalCost)
                        .setScale(6, RoundingMode.HALF_UP));
            }
        }
    }

    /**
     * Open consolidated price edit dialog with rule details and price editing
     */
    private void openPriceEditDialog(GroupedLineItem item) {
        try {
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle("Edit Unit Sell Price");
            dialog.setWidth("600px");

            VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSpacing(true);
        mainLayout.setPadding(false);

        // SECTION 1: Rule Details and Layered Breakdown
        List<PricingRule> appliedRules = item.getAppliedRules();
        List<BigDecimal> intermediateResults = item.getIntermediateResults();

        // Check if this is a loaded session without intermediate results
        boolean hasIntermediateResults = !intermediateResults.isEmpty();

        if (!appliedRules.isEmpty() || item.isManualOverride()) {
            VerticalLayout ruleDetailsSection = new VerticalLayout();
            ruleDetailsSection.setSpacing(true);
            ruleDetailsSection.setPadding(true);
            ruleDetailsSection.getStyle()
                .set("background-color", "#f5f5f5")
                .set("border-radius", "4px")
                .set("margin-bottom", "16px");

            H4 ruleHeader = new H4("Pricing Calculation Breakdown");
            ruleHeader.getStyle()
                .set("margin", "0 0 12px 0")
                .set("color", "#333");
            ruleDetailsSection.add(ruleHeader);

            if (item.isManualOverride()) {
                // Manual override display
                HorizontalLayout manualLayout = new HorizontalLayout();
                manualLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                manualLayout.getStyle().set("gap", "8px");

                Span badge = new Span("MANUAL");
                badge.getElement().getThemeList().add("badge");
                badge.getElement().getThemeList().add("warning");

                Span explanation = new Span("This price was manually set and does not use any pricing rule.");
                explanation.getStyle().set("color", "#666");

                manualLayout.add(badge, explanation);
                ruleDetailsSection.add(manualLayout);
            } else if (!appliedRules.isEmpty() && !hasIntermediateResults) {
                // Recalculate intermediate results for loaded sessions
                List<BigDecimal> recalculatedIntermediates = recalculateIntermediateResults(item, appliedRules);

                // Now show the full breakdown with intermediate prices (same as fresh sessions)
                BigDecimal currentPrice = item.getIncomingCost();

                // Starting price
                HorizontalLayout startLayout = new HorizontalLayout();
                startLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                startLayout.setWidthFull();
                startLayout.getStyle().set("gap", "8px");

                Span startLabel = new Span("Starting Cost:");
                startLabel.getStyle()
                    .set("font-weight", "600")
                    .set("color", "#666")
                    .set("min-width", "120px");

                Span startPrice = new Span(formatCurrency(currentPrice));
                startPrice.getStyle()
                    .set("font-weight", "bold")
                    .set("color", "#333");

                startLayout.add(startLabel, startPrice);
                ruleDetailsSection.add(startLayout);

                // Each rule application with calculations
                for (int i = 0; i < appliedRules.size(); i++) {
                    PricingRule rule = appliedRules.get(i);
                    BigDecimal inputPrice = i < recalculatedIntermediates.size() ?
                        recalculatedIntermediates.get(i) : currentPrice;
                    BigDecimal resultPrice = (i + 1) < recalculatedIntermediates.size() ?
                        recalculatedIntermediates.get(i + 1) : item.getNewUnitSellPrice();

                    VerticalLayout ruleLayout = new VerticalLayout();
                    ruleLayout.setSpacing(false);
                    ruleLayout.setPadding(true);
                    ruleLayout.getStyle()
                        .set("background-color", "white")
                        .set("border-left", "4px solid " + getLayerColor(rule.getRuleCategory()))
                        .set("border-radius", "4px")
                        .set("padding", "8px")
                        .set("margin", "4px 0");

                    // Layer and rule name
                    HorizontalLayout headerLayout = new HorizontalLayout();
                    headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                    headerLayout.getStyle().set("gap", "6px");

                    Icon layerIcon = getLayerIcon(rule.getRuleCategory());
                    layerIcon.setSize("16px");
                    layerIcon.getStyle().set("color", getLayerColor(rule.getRuleCategory()));

                    Span layerName = new Span(rule.getRuleCategory() != null ?
                        rule.getRuleCategory().getDisplayName() : "Unknown");
                    layerName.getStyle()
                        .set("font-weight", "600")
                        .set("color", getLayerColor(rule.getRuleCategory()))
                        .set("font-size", "0.85rem");

                    headerLayout.add(layerIcon, layerName);

                    // Rule details
                    Span ruleName = new Span(rule.getRuleName());
                    ruleName.getStyle()
                        .set("font-weight", "600")
                        .set("color", "#333")
                        .set("display", "block");

                    Span ruleMethod = new Span(formatPricingMethod(rule.getPricingMethod(), rule.getPricingValue()));
                    ruleMethod.getStyle()
                        .set("font-size", "0.85rem")
                        .set("color", "#666")
                        .set("display", "block");

                    // Calculation with exact numbers
                    Span calculation = new Span(formatCalculation(rule.getPricingMethod(), rule.getPricingValue(), inputPrice, resultPrice));
                    calculation.getStyle()
                        .set("font-size", "0.85rem")
                        .set("color", "#2196F3")
                        .set("font-family", "monospace")
                        .set("display", "block")
                        .set("margin-top", "4px");

                    // Price result
                    HorizontalLayout priceLayout = new HorizontalLayout();
                    priceLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                    priceLayout.getStyle().set("gap", "8px").set("margin-top", "4px");

                    Span arrow = new Span("→");
                    arrow.getStyle().set("color", "#999");

                    Span resultPriceSpan = new Span(formatCurrency(resultPrice));
                    resultPriceSpan.getStyle()
                        .set("font-weight", "bold")
                        .set("color", "#2196F3")
                        .set("font-size", "1.1rem");

                    priceLayout.add(arrow, resultPriceSpan);

                    ruleLayout.add(headerLayout, ruleName, ruleMethod, calculation, priceLayout);
                    ruleDetailsSection.add(ruleLayout);
                }
            } else if (!appliedRules.isEmpty()) {
                // Show layered calculation with intermediate prices
                BigDecimal currentPrice = item.getIncomingCost();

                // Starting price
                HorizontalLayout startLayout = new HorizontalLayout();
                startLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                startLayout.setWidthFull();
                startLayout.getStyle().set("gap", "8px");

                Span startLabel = new Span("Starting Cost:");
                startLabel.getStyle()
                    .set("font-weight", "600")
                    .set("color", "#666")
                    .set("min-width", "120px");

                Span startPrice = new Span(formatCurrency(currentPrice));
                startPrice.getStyle()
                    .set("font-weight", "bold")
                    .set("color", "#333");

                startLayout.add(startLabel, startPrice);
                ruleDetailsSection.add(startLayout);

                // Each rule application
                for (int i = 0; i < appliedRules.size(); i++) {
                    PricingRule rule = appliedRules.get(i);
                    // intermediateResults[0] is starting cost, intermediateResults[i+1] is result after rule i
                    BigDecimal inputPrice = i < intermediateResults.size() ?
                        intermediateResults.get(i) : currentPrice;
                    BigDecimal resultPrice = (i + 1) < intermediateResults.size() ?
                        intermediateResults.get(i + 1) : item.getNewUnitSellPrice();

                    VerticalLayout ruleLayout = new VerticalLayout();
                    ruleLayout.setSpacing(false);
                    ruleLayout.setPadding(true);
                    ruleLayout.getStyle()
                        .set("background-color", "white")
                        .set("border-left", "4px solid " + getLayerColor(rule.getRuleCategory()))
                        .set("border-radius", "4px")
                        .set("padding", "8px")
                        .set("margin", "4px 0");

                    // Layer and rule name
                    HorizontalLayout headerLayout = new HorizontalLayout();
                    headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                    headerLayout.getStyle().set("gap", "6px");

                    Icon layerIcon = getLayerIcon(rule.getRuleCategory());
                    layerIcon.setSize("16px");
                    layerIcon.getStyle().set("color", getLayerColor(rule.getRuleCategory()));

                    Span layerName = new Span(rule.getRuleCategory() != null ?
                        rule.getRuleCategory().getDisplayName() : "Unknown");
                    layerName.getStyle()
                        .set("font-weight", "600")
                        .set("color", getLayerColor(rule.getRuleCategory()))
                        .set("font-size", "0.85rem");

                    headerLayout.add(layerIcon, layerName);

                    // Rule details
                    Span ruleName = new Span(rule.getRuleName());
                    ruleName.getStyle()
                        .set("font-weight", "600")
                        .set("color", "#333")
                        .set("display", "block");

                    Span ruleMethod = new Span(formatPricingMethod(rule.getPricingMethod(), rule.getPricingValue()));
                    ruleMethod.getStyle()
                        .set("font-size", "0.85rem")
                        .set("color", "#666")
                        .set("display", "block");

                    // Calculation with exact numbers
                    Span calculation = new Span(formatCalculation(rule.getPricingMethod(), rule.getPricingValue(), inputPrice, resultPrice));
                    calculation.getStyle()
                        .set("font-size", "0.85rem")
                        .set("color", "#2196F3")
                        .set("font-family", "monospace")
                        .set("display", "block")
                        .set("margin-top", "4px");

                    // Price result
                    HorizontalLayout priceLayout = new HorizontalLayout();
                    priceLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                    priceLayout.getStyle().set("gap", "8px").set("margin-top", "4px");

                    Span arrow = new Span("→");
                    arrow.getStyle().set("color", "#999");

                    Span resultPriceSpan = new Span(formatCurrency(resultPrice));
                    resultPriceSpan.getStyle()
                        .set("font-weight", "bold")
                        .set("color", "#2196F3")
                        .set("font-size", "1.1rem");

                    priceLayout.add(arrow, resultPriceSpan);

                    ruleLayout.add(headerLayout, ruleName, ruleMethod, calculation, priceLayout);
                    ruleDetailsSection.add(ruleLayout);
                }

                // Final price summary
                HorizontalLayout finalLayout = new HorizontalLayout();
                finalLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                finalLayout.setWidthFull();
                finalLayout.getStyle()
                    .set("gap", "8px")
                    .set("margin-top", "8px")
                    .set("padding-top", "8px")
                    .set("border-top", "2px solid #ddd");

                Span finalLabel = new Span("Final Price:");
                finalLabel.getStyle()
                    .set("font-weight", "bold")
                    .set("color", "#333")
                    .set("min-width", "120px")
                    .set("font-size", "1.1rem");

                Span finalPrice = new Span(formatCurrency(item.getNewUnitSellPrice()));
                finalPrice.getStyle()
                    .set("font-weight", "bold")
                    .set("color", "#4CAF50")
                    .set("font-size", "1.2rem");

                finalLayout.add(finalLabel, finalPrice);
                ruleDetailsSection.add(finalLayout);
            }

            mainLayout.add(ruleDetailsSection);
        }

        // SECTION 2: Price Editing Form
        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setSpacing(true);
        formLayout.setPadding(false);

        // Display fields (read-only)
        TextField productCodeField = new TextField("Product Code");
        productCodeField.setValue(item.getProductCode() != null ? item.getProductCode() : "");
        productCodeField.setReadOnly(true);
        productCodeField.setWidthFull();

        TextField customerField = new TextField("Customer");
        customerField.setValue(item.getCustomerName() != null ? item.getCustomerName() : "");
        customerField.setReadOnly(true);
        customerField.setWidthFull();

        TextField lastPriceField = new TextField("Last Unit Sell Price");
        lastPriceField.setValue(formatCurrency(item.getLastUnitSellPrice()));
        lastPriceField.setReadOnly(true);
        lastPriceField.setWidthFull();

        TextField currentPriceField = new TextField("Current New Price");
        currentPriceField.setValue(formatCurrency(item.getNewUnitSellPrice()));
        currentPriceField.setReadOnly(true);
        currentPriceField.setWidthFull();

        // Editable new price field
        NumberField newPriceField = new NumberField("New Unit Sell Price");
        newPriceField.setWidthFull();
        newPriceField.setPrefixComponent(new Span("$"));
        newPriceField.setStep(0.01);
        newPriceField.setValue(item.getNewUnitSellPrice() != null ? item.getNewUnitSellPrice().doubleValue() : 0.0);
        newPriceField.setHelperText("This will override any pricing rule");

        formLayout.add(productCodeField, customerField, lastPriceField, currentPriceField, newPriceField);
        mainLayout.add(formLayout);

        // Buttons
        Button saveButton = new Button("Save", event -> {
            Double newPrice = newPriceField.getValue();
            if (newPrice != null && newPrice > 0) {
                item.setNewUnitSellPrice(BigDecimal.valueOf(newPrice));
                item.setManualOverride(true);
                item.setAppliedRule(null); // Clear rule when manually overridden
                recalculateItemFields(item);

                hasUnsavedChanges = true;
                updateTitleStyle();
                applySecondaryFilters(); // Refresh grid

                dialog.close();
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", event -> dialog.close());

        HorizontalLayout buttonLayout = new HorizontalLayout(saveButton, cancelButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();

        dialog.add(mainLayout, buttonLayout);
        dialog.open();

        log.info("Price edit dialog opened successfully");
        } catch (Exception e) {
            log.error("Error opening price edit dialog for product: " + item.getProductCode(), e);
            showErrorNotification("Error opening price dialog: " + e.getMessage());
        }
    }

    /**
     * Format pricing method for display
     */
    private String formatPricingMethod(String method, BigDecimal value) {
        switch (method) {
            case "COST_PLUS_PERCENT":
                // Convert multiplier to percentage for display: 1.20 -> +20%, 0.80 -> -20%
                BigDecimal percentage = value.subtract(BigDecimal.ONE)
                        .multiply(new BigDecimal("100"));
                String sign = percentage.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                return String.format("%s%.1f%% markup", sign, percentage);
            case "COST_PLUS_FIXED":
                return String.format("Cost + $%.2f", value);
            case "FIXED_PRICE":
                return String.format("Fixed $%.2f", value);
            case "MAINTAIN_GP_PERCENT":
                if (value != null) {
                    return String.format("Maintain GP%% at %.1f%%", value);
                } else {
                    return "Maintain GP% from last cycle";
                }
            default:
                return method;
        }
    }

    /**
     * Format calculation with exact numbers used
     */
    private String formatCalculation(String method, BigDecimal value, BigDecimal inputPrice, BigDecimal resultPrice) {
        if (inputPrice == null) {
            return "";
        }

        switch (method) {
            case "COST_PLUS_PERCENT":
                if (value == null) return "";
                return String.format("%s × %.2f = %s",
                    formatCurrency(inputPrice), value, formatCurrency(resultPrice));
            case "COST_PLUS_FIXED":
                if (value == null) return "";
                return String.format("%s + %s = %s",
                    formatCurrency(inputPrice), formatCurrency(value), formatCurrency(resultPrice));
            case "FIXED_PRICE":
                if (value == null) return "";
                return String.format("Fixed = %s", formatCurrency(value));
            case "MAINTAIN_GP_PERCENT":
                if (resultPrice == null || inputPrice == null) return "";
                if (resultPrice.compareTo(BigDecimal.ZERO) == 0) return "";

                // Calculate the actual GP% used from the result: GP% = 1 - (cost / price)
                BigDecimal actualGPDecimal = BigDecimal.ONE.subtract(
                    inputPrice.divide(resultPrice, 4, RoundingMode.HALF_UP));
                BigDecimal actualGPPercent = actualGPDecimal.multiply(new BigDecimal("100"));
                BigDecimal divisor = BigDecimal.ONE.subtract(actualGPDecimal);

                // Show whether it's historical or default
                String gpSource = value != null && actualGPDecimal.subtract(value).abs().compareTo(new BigDecimal("0.001")) < 0
                    ? "(default)" : "(historical)";

                return String.format("%s ÷ (1 - %.1f%% %s) = %s ÷ %.4f = %s",
                    formatCurrency(inputPrice), actualGPPercent, gpSource,
                    formatCurrency(inputPrice), divisor, formatCurrency(resultPrice));
            default:
                return "";
        }
    }

    /**
     * Get color for rule category badge
     */
    private String getLayerColor(RuleCategory category) {
        if (category == null) {
            return "#888888"; // Gray for unknown
        }
        switch (category) {
            case BASE_PRICE:
                return "#2196F3"; // Blue
            case CUSTOMER_ADJUSTMENT:
                return "#4CAF50"; // Green
            case PRODUCT_ADJUSTMENT:
                return "#FF9800"; // Orange
            case PROMOTIONAL:
                return "#E91E63"; // Pink
            default:
                return "#888888"; // Gray
        }
    }

    /**
     * Get icon for rule category
     */
    private Icon getLayerIcon(RuleCategory category) {
        if (category == null) {
            return VaadinIcon.QUESTION_CIRCLE.create();
        }
        switch (category) {
            case BASE_PRICE:
                return VaadinIcon.DOLLAR.create();
            case CUSTOMER_ADJUSTMENT:
                return VaadinIcon.USER.create();
            case PRODUCT_ADJUSTMENT:
                return VaadinIcon.PACKAGE.create();
            case PROMOTIONAL:
                return VaadinIcon.TAG.create();
            default:
                return VaadinIcon.QUESTION_CIRCLE.create();
        }
    }

    /**
     * Apply date range filter
     */
    private void applyFilter() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (dataGrid != null) {
            // Update backing list with date-filtered results
            if (startDate != null || endDate != null) {
                backingList = pricingImportService.getGroupedLineItemsByDateRange(startDate, endDate);
            } else {
                backingList = pricingImportService.getGroupedLineItems();
            }

            // Apply secondary filters on top of backing list
            applySecondaryFilters();
        }
    }

    /**
     * Apply customer/product filters on backing list
     */
    private void applySecondaryFilters() {
        if (dataGrid == null) {
            return;
        }

        String customerFilter = customerNameFilter.getValue();
        String productFilterValue = productFilter.getValue();

        List<GroupedLineItem> filteredItems = backingList.stream()
                .filter(item -> {
                    // Customer name filter
                    if (customerFilter != null && !customerFilter.trim().isEmpty()) {
                        String customerName = item.getCustomerName();
                        if (customerName == null ||
                                !customerName.toLowerCase().contains(customerFilter.toLowerCase().trim())) {
                            return false;
                        }
                    }

                    // Product filter
                    if (productFilterValue != null && !productFilterValue.trim().isEmpty()) {
                        String productCode = item.getProductCode();
                        String productDesc = item.getProductDescription();
                        String filterLower = productFilterValue.toLowerCase().trim();

                        boolean matchesCode = productCode != null &&
                                productCode.toLowerCase().contains(filterLower);
                        boolean matchesDesc = productDesc != null &&
                                productDesc.toLowerCase().contains(filterLower);

                        if (!matchesCode && !matchesDesc) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

        dataGrid.setItems(filteredItems);
        updateFooterTotals(filteredItems);
    }

    /**
     * Open customer edit dialog
     */
    private void openCustomerEditDialog(GroupedLineItem item) {
        String customerCode = item.getCustomerCode();
        if (customerCode == null || customerCode.trim().isEmpty()) {
            return;
        }

        customerRepository.findByCustomerCode(customerCode).ifPresent(customer -> {
            CustomerEditDialog dialog = new CustomerEditDialog(customer, customerRepository);
            dialog.open(null);
        });
    }

    /**
     * Update title style based on session state
     */
    private void updateTitleStyle() {
        if (currentSession != null) {
            titleComponent.setText("Pricing Sessions - " + currentSession.getSessionName());
        } else {
            titleComponent.setText("Pricing Sessions");
        }

        if (hasUnsavedChanges) {
            titleComponent.getStyle().set("color", "orange");
        } else {
            titleComponent.getStyle().remove("color");
        }
    }

    /**
     * Open save session dialog
     */
    private void openSaveSessionDialog() {
        if (backingList == null || backingList.isEmpty()) {
            showErrorNotification("No data to save. Please apply a date filter first.");
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Save Pricing Session");
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(true);

        // Session name field
        TextField sessionNameField = new TextField("Session Name");
        sessionNameField.setWidthFull();
        sessionNameField.setPlaceholder("Enter session name...");
        sessionNameField.setRequired(true);
        sessionNameField.setRequiredIndicatorVisible(true);

        // Pre-fill with current session name if editing existing session
        if (currentSession != null) {
            sessionNameField.setValue(currentSession.getSessionName());
        }

        // Notes field
        TextArea notesField = new TextArea("Notes (Optional)");
        notesField.setWidthFull();
        notesField.setPlaceholder("Add any notes about this pricing session...");
        notesField.setHeight("100px");

        if (currentSession != null && currentSession.getNotes() != null) {
            notesField.setValue(currentSession.getNotes());
        }

        // Summary info
        Span summarySpan = new Span(String.format("You are about to save %d line items.", backingList.size()));
        summarySpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        content.add(sessionNameField, notesField, summarySpan);

        // Buttons
        Button saveButton = new Button("Save", event -> {
            String sessionName = sessionNameField.getValue();

            if (sessionName == null || sessionName.trim().isEmpty()) {
                showErrorNotification("Session name is required");
                return;
            }

            // Check if session name already exists
            boolean nameExists = pricingSessionService.sessionNameExists(sessionName.trim());
            boolean isCurrentSession = currentSession != null &&
                    sessionName.trim().equals(currentSession.getSessionName());

            if (nameExists && !isCurrentSession) {
                // Session name exists - ask to overwrite
                ConfirmDialog confirmDialog = new ConfirmDialog();
                confirmDialog.setHeader("Session Already Exists");
                confirmDialog.setText("A session with name '" + sessionName.trim() +
                        "' already exists. Do you want to overwrite it?");
                confirmDialog.setCancelable(true);
                confirmDialog.setCancelText("Cancel");
                confirmDialog.setConfirmText("Overwrite");
                confirmDialog.setConfirmButtonTheme("error primary");

                confirmDialog.addConfirmListener(confirmEvent -> {
                    performSave(sessionName.trim(), notesField.getValue(), dialog);
                });

                confirmDialog.open();
            } else {
                // New session or updating current session
                performSave(sessionName.trim(), notesField.getValue(), dialog);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", event -> dialog.close());

        HorizontalLayout buttonLayout = new HorizontalLayout(saveButton, cancelButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();

        dialog.add(content, buttonLayout);
        dialog.open();
    }

    /**
     * Perform the actual session save operation
     */
    private void performSave(String sessionName, String notes, Dialog dialog) {
        try {
            // Log what we're about to save
            long itemsWithRules = backingList.stream()
                    .filter(item -> !item.getAppliedRules().isEmpty())
                    .count();
            long itemsWithManualOverride = backingList.stream()
                    .filter(GroupedLineItem::isManualOverride)
                    .count();

            log.info("Saving session '{}': {} total items, {} with applied rules, {} with manual override",
                     sessionName, backingList.size(), itemsWithRules, itemsWithManualOverride);

            // Sample first item with rules
            backingList.stream()
                    .filter(item -> !item.getAppliedRules().isEmpty())
                    .findFirst()
                    .ifPresent(item -> log.info("Sample item {} has {} applied rules: {}",
                            item.getProductCode(),
                            item.getAppliedRules().size(),
                            item.getAppliedRules().stream()
                                    .map(PricingRule::getRuleName)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse("none")));

            // Save session with all line items
            PricingSession savedSession = pricingSessionService.saveSession(sessionName, backingList);

            // Update notes if provided
            if (notes != null && !notes.trim().isEmpty()) {
                savedSession.setNotes(notes.trim());
            }

            // Update UI state
            currentSession = savedSession;
            hasUnsavedChanges = false;
            updateTitleStyle();

            dialog.close();
            showSuccessNotification("Session '" + sessionName + "' saved successfully with " +
                    backingList.size() + " line items");

            log.info("Saved pricing session: {} with {} items", sessionName, backingList.size());

        } catch (Exception e) {
            log.error("Error saving pricing session: " + sessionName, e);
            showErrorNotification("Error saving session: " + e.getMessage());
        }
    }

    /**
     * Open load session dialog
     */
    private void openLoadSessionDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Load Pricing Session");
        dialog.setWidth("800px");
        dialog.setHeight("600px");

        VerticalLayout content = new VerticalLayout();
        content.setSizeFull();
        content.setSpacing(true);
        content.setPadding(false);

        // Grid to show all sessions
        Grid<PricingSession> sessionGrid = new Grid<>(PricingSession.class, false);
        sessionGrid.setSizeFull();

        sessionGrid.addColumn(PricingSession::getSessionName)
                .setHeader("Session Name")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        sessionGrid.addColumn(session -> {
            if (session.getCreatedDate() != null) {
                return session.getCreatedDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
            return "";
        }).setHeader("Created")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        sessionGrid.addColumn(session -> {
            if (session.getLastModifiedDate() != null) {
                return session.getLastModifiedDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
            return "";
        }).setHeader("Last Modified")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        sessionGrid.addColumn(PricingSession::getStatus)
                .setHeader("Status")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        sessionGrid.addColumn(session -> {
            String notes = session.getNotes();
            if (notes != null && notes.length() > 50) {
                return notes.substring(0, 50) + "...";
            }
            return notes != null ? notes : "";
        }).setHeader("Notes")
                .setFlexGrow(1)
                .setResizable(true);

        // Load all sessions
        List<PricingSession> sessions = pricingSessionService.getAllSessions();
        sessionGrid.setItems(sessions);

        // Selection mode
        sessionGrid.setSelectionMode(Grid.SelectionMode.SINGLE);

        // Info text
        Span infoSpan = new Span("Select a session and click 'Load' to restore it, or 'Delete' to remove it.");
        infoSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        content.add(infoSpan, sessionGrid);

        // Buttons
        Button loadButton = new Button("Load", new Icon(VaadinIcon.FOLDER_OPEN));
        loadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loadButton.setEnabled(false);

        Button deleteButton = new Button("Delete", new Icon(VaadinIcon.TRASH));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.setEnabled(false);

        Button cancelButton = new Button("Cancel", event -> dialog.close());

        // Enable/disable buttons based on selection
        sessionGrid.addSelectionListener(selection -> {
            boolean hasSelection = selection.getFirstSelectedItem().isPresent();
            loadButton.setEnabled(hasSelection);
            deleteButton.setEnabled(hasSelection);
        });

        // Load button action
        loadButton.addClickListener(event -> {
            sessionGrid.getSelectedItems().stream().findFirst().ifPresent(selectedSession -> {
                // Check for unsaved changes
                if (hasUnsavedChanges) {
                    ConfirmDialog confirmDialog = new ConfirmDialog();
                    confirmDialog.setHeader("Unsaved Changes");
                    confirmDialog.setText("You have unsaved changes. Loading a session will discard them. Continue?");
                    confirmDialog.setCancelable(true);
                    confirmDialog.setCancelText("Cancel");
                    confirmDialog.setConfirmText("Load Session");
                    confirmDialog.setConfirmButtonTheme("error primary");

                    confirmDialog.addConfirmListener(confirmEvent -> {
                        performLoad(selectedSession, dialog);
                    });

                    confirmDialog.open();
                } else {
                    performLoad(selectedSession, dialog);
                }
            });
        });

        // Delete button action
        deleteButton.addClickListener(event -> {
            sessionGrid.getSelectedItems().stream().findFirst().ifPresent(selectedSession -> {
                ConfirmDialog confirmDialog = new ConfirmDialog();
                confirmDialog.setHeader("Delete Session");
                confirmDialog.setText("Are you sure you want to delete session '" +
                        selectedSession.getSessionName() + "'? This cannot be undone.");
                confirmDialog.setCancelable(true);
                confirmDialog.setCancelText("Cancel");
                confirmDialog.setConfirmText("Delete");
                confirmDialog.setConfirmButtonTheme("error primary");

                confirmDialog.addConfirmListener(confirmEvent -> {
                    try {
                        pricingSessionService.deleteSession(selectedSession.getId());

                        // Refresh grid
                        List<PricingSession> updatedSessions = pricingSessionService.getAllSessions();
                        sessionGrid.setItems(updatedSessions);

                        // If deleted session was current, clear current session
                        if (currentSession != null && currentSession.getId().equals(selectedSession.getId())) {
                            currentSession = null;
                            hasUnsavedChanges = false;
                            updateTitleStyle();
                        }

                        showSuccessNotification("Session '" + selectedSession.getSessionName() + "' deleted");
                        log.info("Deleted pricing session: {}", selectedSession.getSessionName());

                    } catch (Exception e) {
                        log.error("Error deleting session: " + selectedSession.getSessionName(), e);
                        showErrorNotification("Error deleting session: " + e.getMessage());
                    }
                });

                confirmDialog.open();
            });
        });

        HorizontalLayout buttonLayout = new HorizontalLayout(loadButton, deleteButton, cancelButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();
        buttonLayout.getStyle().set("padding-top", "var(--lumo-space-m)");

        VerticalLayout dialogLayout = new VerticalLayout(content, buttonLayout);
        dialogLayout.setSizeFull();
        dialogLayout.setSpacing(false);
        dialogLayout.setPadding(false);

        dialog.add(dialogLayout);
        dialog.open();
    }

    /**
     * Perform the actual session load operation
     */
    private void performLoad(PricingSession session, Dialog dialog) {
        try {
            // Load line items from session
            List<GroupedLineItem> loadedItems = pricingSessionService.loadSession(session.getId());

            // Replace backing list with loaded items
            backingList = new ArrayList<>(loadedItems);

            // Update UI state
            currentSession = session;
            hasUnsavedChanges = false;
            updateTitleStyle();

            // Refresh the grid with loaded data
            applySecondaryFilters();

            dialog.close();
            showSuccessNotification("Session '" + session.getSessionName() + "' loaded successfully with " +
                    loadedItems.size() + " line items");

            log.info("Loaded pricing session: {} with {} items", session.getSessionName(), loadedItems.size());

        } catch (Exception e) {
            log.error("Error loading pricing session: " + session.getSessionName(), e);
            showErrorNotification("Error loading session: " + e.getMessage());
        }
    }

    /**
     * Handle new session
     */
    private void handleNewSession() {
        if (hasUnsavedChanges) {
            ConfirmDialog confirmDialog = new ConfirmDialog();
            confirmDialog.setHeader("Unsaved Changes");
            confirmDialog.setText("You have unsaved changes. Starting a new session will discard them. Continue?");
            confirmDialog.setCancelable(true);
            confirmDialog.setCancelText("Cancel");
            confirmDialog.setConfirmText("Start New Session");
            confirmDialog.setConfirmButtonTheme("error primary");

            confirmDialog.addConfirmListener(event -> {
                currentSession = null;
                hasUnsavedChanges = false;
                updateTitleStyle();
                showSuccessNotification("Started new session");
            });

            confirmDialog.open();
        } else {
            currentSession = null;
            hasUnsavedChanges = false;
            updateTitleStyle();
            showSuccessNotification("Started new session");
        }
    }

    /**
     * Save column visibility preferences to browser localStorage
     */
    private void saveColumnVisibility() {
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
            customerCol.isVisible(),
            ratingCol.isVisible(),
            productCodeCol.isVisible(),
            productCol.isVisible(),
            qtyCol.isVisible(),
            lastCostCol.isVisible(),
            lastPriceCol.isVisible(),
            lastAmountCol.isVisible(),
            lastGPCol.isVisible(),
            lastGPPercentCol.isVisible(),
            costDriftCol.isVisible(),
            newCostCol.isVisible(),
            newPriceCol.isVisible(),
            newAmountCol.isVisible(),
            newGPCol.isVisible(),
            newGPPercentCol.isVisible(),
            notesCol.isVisible()
        );

        getElement().executeJs("localStorage.setItem($0, $1)", STORAGE_KEY, json);
    }

    /**
     * Load column visibility preferences from browser localStorage
     */
    private void loadColumnVisibility() {
        getElement().executeJs(
            "return localStorage.getItem($0)", STORAGE_KEY
        ).then(String.class, json -> {
            if (json != null && !json.isEmpty()) {
                try {
                    // Parse JSON manually (simple string parsing)
                    customerCol.setVisible(getBooleanValue(json, "customerName"));
                    ratingCol.setVisible(getBooleanValue(json, "rating"));
                    productCodeCol.setVisible(getBooleanValue(json, "productCode"));
                    productCol.setVisible(getBooleanValue(json, "product"));
                    qtyCol.setVisible(getBooleanValue(json, "quantity"));
                    lastCostCol.setVisible(getBooleanValue(json, "lastCost"));
                    lastPriceCol.setVisible(getBooleanValue(json, "lastPrice"));
                    lastAmountCol.setVisible(getBooleanValue(json, "lastAmount"));
                    lastGPCol.setVisible(getBooleanValue(json, "lastGP"));
                    lastGPPercentCol.setVisible(getBooleanValue(json, "lastGPPercent"));
                    costDriftCol.setVisible(getBooleanValue(json, "costDrift"));
                    newCostCol.setVisible(getBooleanValue(json, "newCost"));
                    newPriceCol.setVisible(getBooleanValue(json, "newPrice"));
                    newAmountCol.setVisible(getBooleanValue(json, "newAmount"));
                    newGPCol.setVisible(getBooleanValue(json, "newGP"));
                    newGPPercentCol.setVisible(getBooleanValue(json, "newGPPercent"));
                    notesCol.setVisible(getBooleanValue(json, "notes"));
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
     * Open column visibility dialog
     */
    private void openColumnVisibilityDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Show/Hide Columns");
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(true);

        // Basic Info group
        H4 basicInfoHeader = new H4("Basic Info");
        basicInfoHeader.getStyle().set("margin-top", "0");

        Checkbox customerCheck = new Checkbox("Customer", customerCol.isVisible());
        customerCheck.addValueChangeListener(e -> {
            customerCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox ratingCheck = new Checkbox("Rating", ratingCol.isVisible());
        ratingCheck.addValueChangeListener(e -> {
            ratingCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox productCodeCheck = new Checkbox("Product Code", productCodeCol.isVisible());
        productCodeCheck.addValueChangeListener(e -> {
            productCodeCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox productCheck = new Checkbox("Product", productCol.isVisible());
        productCheck.addValueChangeListener(e -> {
            productCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox qtyCheck = new Checkbox("Quantity", qtyCol.isVisible());
        qtyCheck.addValueChangeListener(e -> {
            qtyCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        // Historical (Last) group
        H4 historicalHeader = new H4("Historical (Last)");
        historicalHeader.getStyle().set("margin-top", "10px");

        Checkbox lastCostCheck = new Checkbox("Cost", lastCostCol.isVisible());
        lastCostCheck.addValueChangeListener(e -> {
            lastCostCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox lastPriceCheck = new Checkbox("Price", lastPriceCol.isVisible());
        lastPriceCheck.addValueChangeListener(e -> {
            lastPriceCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox lastAmountCheck = new Checkbox("Amount", lastAmountCol.isVisible());
        lastAmountCheck.addValueChangeListener(e -> {
            lastAmountCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox lastGPCheck = new Checkbox("GP $", lastGPCol.isVisible());
        lastGPCheck.addValueChangeListener(e -> {
            lastGPCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox lastGPPercentCheck = new Checkbox("GP %", lastGPPercentCol.isVisible());
        lastGPPercentCheck.addValueChangeListener(e -> {
            lastGPPercentCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        // Cost Drift (standalone)
        H4 costDriftHeader = new H4("Cost Variance");
        costDriftHeader.getStyle().set("margin-top", "10px");

        Checkbox costDriftCheck = new Checkbox("Cost Drift", costDriftCol.isVisible());
        costDriftCheck.addValueChangeListener(e -> {
            costDriftCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        // New Pricing group
        H4 newPricingHeader = new H4("New Pricing");
        newPricingHeader.getStyle().set("margin-top", "10px");

        Checkbox newCostCheck = new Checkbox("Cost", newCostCol.isVisible());
        newCostCheck.addValueChangeListener(e -> {
            newCostCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox newPriceCheck = new Checkbox("Price", newPriceCol.isVisible());
        newPriceCheck.addValueChangeListener(e -> {
            newPriceCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox newAmountCheck = new Checkbox("Amount", newAmountCol.isVisible());
        newAmountCheck.addValueChangeListener(e -> {
            newAmountCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox newGPCheck = new Checkbox("GP $", newGPCol.isVisible());
        newGPCheck.addValueChangeListener(e -> {
            newGPCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        Checkbox newGPPercentCheck = new Checkbox("GP %", newGPPercentCol.isVisible());
        newGPPercentCheck.addValueChangeListener(e -> {
            newGPPercentCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        // Notes
        H4 otherHeader = new H4("Other");
        otherHeader.getStyle().set("margin-top", "10px");

        Checkbox notesCheck = new Checkbox("Notes", notesCol.isVisible());
        notesCheck.addValueChangeListener(e -> {
            notesCol.setVisible(e.getValue());
            saveColumnVisibility();
        });

        // Add all components
        content.add(
                basicInfoHeader,
                customerCheck, ratingCheck, productCodeCheck, productCheck, qtyCheck,
                historicalHeader,
                lastCostCheck, lastPriceCheck, lastAmountCheck, lastGPCheck, lastGPPercentCheck,
                costDriftHeader,
                costDriftCheck,
                newPricingHeader,
                newCostCheck, newPriceCheck, newAmountCheck, newGPCheck, newGPPercentCheck,
                otherHeader,
                notesCheck
        );

        // Buttons
        Button showAllButton = new Button("Show All", event -> {
            customerCheck.setValue(true);
            ratingCheck.setValue(true);
            productCodeCheck.setValue(true);
            productCheck.setValue(true);
            qtyCheck.setValue(true);
            lastCostCheck.setValue(true);
            lastPriceCheck.setValue(true);
            lastAmountCheck.setValue(true);
            lastGPCheck.setValue(true);
            lastGPPercentCheck.setValue(true);
            newCostCheck.setValue(true);
            newPriceCheck.setValue(true);
            newAmountCheck.setValue(true);
            newGPCheck.setValue(true);
            newGPPercentCheck.setValue(true);
            notesCheck.setValue(true);
        });
        showAllButton.addThemeVariants(ButtonVariant.LUMO_SMALL);

        Button hideAllButton = new Button("Hide All", event -> {
            customerCheck.setValue(false);
            ratingCheck.setValue(false);
            productCodeCheck.setValue(false);
            productCheck.setValue(false);
            qtyCheck.setValue(false);
            lastCostCheck.setValue(false);
            lastPriceCheck.setValue(false);
            lastAmountCheck.setValue(false);
            lastGPCheck.setValue(false);
            lastGPPercentCheck.setValue(false);
            newCostCheck.setValue(false);
            newPriceCheck.setValue(false);
            newAmountCheck.setValue(false);
            newGPCheck.setValue(false);
            newGPPercentCheck.setValue(false);
            notesCheck.setValue(false);
        });
        hideAllButton.addThemeVariants(ButtonVariant.LUMO_SMALL);

        Button closeButton = new Button("Close", event -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout buttonLayout = new HorizontalLayout(showAllButton, hideAllButton, closeButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();
        buttonLayout.getStyle().set("margin-top", "10px");

        dialog.add(content, buttonLayout);
        dialog.open();
    }

    /**
     * BeforeLeaveObserver implementation to warn about unsaved changes.
     *
     * Standard Vaadin pattern:
     * - If unsaved changes exist, postpone navigation and show confirmation dialog
     * - If user confirms leaving: clear hasUnsavedChanges flag and call action.proceed()
     * - If user cancels: do nothing - navigation is automatically cancelled by Vaadin
     *
     * This simple pattern works because:
     * - Each navigation attempt triggers beforeLeave() again (if not proceeded)
     * - The postpone() call prevents navigation until proceed() is called
     * - Not calling proceed() automatically cancels the navigation
     */
    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        if (hasUnsavedChanges) {
            ContinueNavigationAction action = event.postpone();

            ConfirmDialog confirmDialog = new ConfirmDialog();
            confirmDialog.setHeader("Unsaved Changes");
            confirmDialog.setText("You have unsaved changes. Are you sure you want to leave?");
            confirmDialog.setCancelable(true);
            confirmDialog.setCancelText("Stay");
            confirmDialog.setConfirmText("Leave");
            confirmDialog.setConfirmButtonTheme("error primary");

            confirmDialog.addConfirmListener(confirmEvent -> {
                // User chose to leave - clear flag and proceed with navigation
                hasUnsavedChanges = false;
                action.proceed();
            });

            confirmDialog.addCancelListener(cancelEvent -> {
                event.getContinueNavigationAction().cancel();
            });
            confirmDialog.open();
        }
    }

    /**
     * Show success notification
     */
    private void showSuccessNotification(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    /**
     * Show error notification
     */
    private void showErrorNotification(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
