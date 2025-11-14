package com.meatrics.pricing.ui.pricing;

import com.meatrics.base.ui.MainLayout;
import com.meatrics.pricing.customer.Customer;
import com.meatrics.pricing.customer.CustomerRepository;
import com.meatrics.pricing.product.GroupedLineItem;
import com.meatrics.pricing.importer.PricingImportService;
import com.meatrics.pricing.session.PricingSession;
import com.meatrics.pricing.session.PricingSessionService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Redesigned Pricing Sessions view with rule-based pricing engine integration.
 * Features nested column headers, historical vs new pricing comparison, and rule transparency.
 *
 * Refactored to use extracted manager classes for better maintainability.
 */
@Route(value = "pricing-sessions", layout = MainLayout.class)
@PageTitle("Pricing Sessions")
@Menu(order = 1, icon = "vaadin:edit", title = "Pricing Sessions")
public class PricingSessionsView extends VerticalLayout implements BeforeLeaveObserver {

    private static final Logger log = LoggerFactory.getLogger(PricingSessionsView.class);

    // Injected services
    private final PricingImportService pricingImportService;
    private final CustomerRepository customerRepository;
    private final PricingSessionManager sessionManager;
    private final PricingDialogFactory dialogFactory;
    private final PricingGridColumnManager columnManager;
    private final PricingFilterManager filterManager;
    private final PricingCalculator calculator;
    private final PricingSessionService pricingSessionService;

    // UI components
    private final Grid<GroupedLineItem> dataGrid;
    private final DatePicker startDatePicker;
    private final DatePicker endDatePicker;
    private final TextField customerNameFilter;
    private final TextField productFilter;
    private H2 titleComponent;
    private Button saveSessionButton;
    private Button finalizeSessionButton;

    // Backing list that holds date-filtered grouped records
    private List<GroupedLineItem> backingList = new ArrayList<>();

    // Session state tracking
    private PricingSession currentSession = null;
    private boolean hasUnsavedChanges = false;

    // Grid columns and footer (stored for updating totals and visibility control)
    private final PricingGridColumnManager.GridColumns columns = new PricingGridColumnManager.GridColumns();
    private com.vaadin.flow.component.grid.FooterRow footerRow;

    public PricingSessionsView(PricingImportService pricingImportService,
                              CustomerRepository customerRepository,
                              PricingSessionManager sessionManager,
                              PricingDialogFactory dialogFactory,
                              PricingGridColumnManager columnManager,
                              PricingFilterManager filterManager,
                              PricingCalculator calculator,
                              PricingSessionService pricingSessionService) {
        this.pricingImportService = pricingImportService;
        this.customerRepository = customerRepository;
        this.sessionManager = sessionManager;
        this.dialogFactory = dialogFactory;
        this.columnManager = columnManager;
        this.filterManager = filterManager;
        this.calculator = calculator;
        this.pricingSessionService = pricingSessionService;

        addClassName("pricing-sessions-view");
        setSizeFull();
        setSpacing(true);
        setPadding(true);

        // Title and session management buttons
        titleComponent = new H2("Pricing Sessions");

        saveSessionButton = new Button("Save Session", new Icon(VaadinIcon.DISC));
        saveSessionButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveSessionButton.addClickListener(event -> handleSaveSession());

        finalizeSessionButton = new Button("Finalize", new Icon(VaadinIcon.CHECK_CIRCLE));
        finalizeSessionButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        finalizeSessionButton.addClickListener(event -> handleFinalizeOrUnfinalizeSession());

        Button loadSessionButton = new Button("Load Session", new Icon(VaadinIcon.FOLDER_OPEN));
        loadSessionButton.addClickListener(event -> handleLoadSession());

        Button newSessionButton = new Button("New Session", new Icon(VaadinIcon.PLUS));
        newSessionButton.addClickListener(event -> handleNewSession());

        Button columnVisibilityButton = new Button("Show/Hide Columns", new Icon(VaadinIcon.EYE));
        columnVisibilityButton.addClickListener(event -> handleColumnVisibility());

        Button clearDataButton = new Button("Clear", new Icon(VaadinIcon.TRASH));
        clearDataButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        clearDataButton.addClickListener(event -> handleClearWorkingData());

        HorizontalLayout sessionButtonsLayout = new HorizontalLayout(
            saveSessionButton, finalizeSessionButton, loadSessionButton, newSessionButton, clearDataButton, columnVisibilityButton);
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
        applyRulesButton.addClickListener(event -> handleApplyRules());

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
        HorizontalLayout legendLayout = createLegendLayout();

        HorizontalLayout secondaryFilterLayout = new HorizontalLayout(customerNameFilter, productFilter, legendLayout);
        secondaryFilterLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        secondaryFilterLayout.setSpacing(true);

        VerticalLayout filterLayout = new VerticalLayout(dateFilterLayout, secondaryFilterLayout);
        filterLayout.setSpacing(false);
        filterLayout.setPadding(false);

        // Create data grid
        dataGrid = createDataGrid();

        // Load column visibility preferences from browser localStorage
        columnManager.loadColumnVisibility(this, columns);

        // Add components to main layout
        add(titleLayout, filterLayout, dataGrid);

        // Restore working data from session if available
        sessionManager.restoreFromSession(sessionData -> {
            if (sessionData.getBackingList() != null) {
                backingList = sessionData.getBackingList();
                dataGrid.setItems(backingList);
                columnManager.updateFooterTotals(footerRow, backingList, columns);
            }
            if (sessionData.getCurrentSession() != null) {
                currentSession = sessionData.getCurrentSession();
            }
            hasUnsavedChanges = sessionData.hasUnsavedChanges();
            updateTitleStyle();
        });
    }

    /**
     * Create color legend for GP% indicators
     */
    private HorizontalLayout createLegendLayout() {
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
        return legendLayout;
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
        columns.customerCol = grid.addColumn(new ComponentRenderer<>(item -> {
            Button customerButton = new Button(item.getCustomerName());
            customerButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            customerButton.getStyle().set("padding", "0");
            customerButton.addClickListener(e -> dialogFactory.openCustomerEditDialog(item));
            return customerButton;
        })).setHeader(createHeaderWithTooltip("Customer", "Customer name (click to edit customer rating)"))
                .setKey("customerName").setAutoWidth(true).setResizable(true).setSortable(true)
                .setComparator((item1, item2) -> {
                    String name1 = item1.getCustomerName() != null ? item1.getCustomerName() : "";
                    String name2 = item2.getCustomerName() != null ? item2.getCustomerName() : "";
                    return name1.compareToIgnoreCase(name2);
                });

        columns.ratingCol = grid.addColumn(item -> {
            String customerCode = item.getCustomerCode();
            if (customerCode == null || customerCode.trim().isEmpty()) {
                return "";
            }
            return customerRepository.findByCustomerCode(customerCode)
                    .map(customer -> customer.getCustomerRating() != null ? customer.getCustomerRating() : "")
                    .orElse("");
        }).setHeader(createHeaderWithTooltip("Rating", "Customer rating (A, B, C, etc.) for pricing rules"))
                .setKey("rating").setAutoWidth(true).setResizable(true).setSortable(true);

        columns.productCodeCol = grid.addColumn(GroupedLineItem::getProductCode)
                .setHeader(createHeaderWithTooltip("Product Code", "Internal product SKU/code"))
                .setKey("productCode").setAutoWidth(true).setResizable(true).setSortable(true);

        columns.productCol = grid.addColumn(GroupedLineItem::getProductDescription)
                .setHeader(createHeaderWithTooltip("Product", "Product description"))
                .setKey("product").setAutoWidth(true).setFlexGrow(1).setResizable(true).setSortable(true);

        columns.qtyCol = grid.addColumn(item ->
                String.format("%.2f", item.getTotalQuantity() != null ? item.getTotalQuantity() : BigDecimal.ZERO))
                .setHeader(createHeaderWithTooltip("Qty", "Total quantity sold in selected date range"))
                .setKey("quantity").setAutoWidth(true).setResizable(true).setSortable(true);

        // HISTORICAL (LAST) GROUP (5 columns)
        columns.lastCostCol = grid.addColumn(item ->
                calculator.formatCurrency(item.getLastCost()))
                .setHeader(createHeaderWithTooltip("Cost", "Unit cost you paid the supplier in the last cycle"))
                .setKey("lastCost").setAutoWidth(true).setResizable(true).setSortable(true);

        columns.lastPriceCol = grid.addColumn(item ->
                calculator.formatCurrency(item.getLastUnitSellPrice()))
                .setHeader(createHeaderWithTooltip("Price", "Unit sell price charged to customer in the last cycle"))
                .setKey("lastPrice").setAutoWidth(true).setResizable(true).setSortable(true);

        columns.lastAmountCol = grid.addColumn(item ->
                calculator.formatCurrency(item.getLastAmount()))
                .setHeader(createHeaderWithTooltip("Amount", "Total revenue: Price × Quantity from last cycle"))
                .setKey("lastAmount").setAutoWidth(true).setResizable(true).setSortable(true);

        columns.lastGPCol = grid.addColumn(item ->
                calculator.formatCurrency(item.getLastGrossProfit()))
                .setHeader(createHeaderWithTooltip("GP $", "Gross profit in dollars: Amount - (Cost × Quantity)"))
                .setKey("lastGP").setAutoWidth(true).setResizable(true).setSortable(true);

        columns.lastGPPercentCol = grid.addColumn(item ->
                calculator.formatGPPercent(item.getLastGrossProfit(), item.getLastAmount()))
                .setHeader(createHeaderWithTooltip("GP %", "Gross profit percentage: (GP $ / Amount) × 100"))
                .setKey("lastGPPercent").setAutoWidth(true).setResizable(true).setSortable(true);

        // COST DRIFT COLUMN (bridge between Historical and New Pricing)
        columns.costDriftCol = grid.addComponentColumn(item -> createCostDriftCell(item))
                .setHeader(createHeaderWithTooltip("Cost Drift", "Change in unit cost: New Cost - Last Cost (absolute and %)"))
                .setKey("costDrift").setAutoWidth(true).setResizable(true)
                .setComparator(this::compareCostDrift)
                .setSortable(true);

        // NEW PRICING GROUP (5 columns)
        columns.newCostCol = grid.addComponentColumn(item -> createNewCostCell(item))
                .setHeader(createHeaderWithTooltip("Cost", "Current unit cost from supplier (imported from cost file)"))
                .setKey("newCost").setAutoWidth(true).setResizable(true)
                .setComparator((item1, item2) -> {
                    BigDecimal cost1 = item1.getIncomingCost() != null ? item1.getIncomingCost() : BigDecimal.ZERO;
                    BigDecimal cost2 = item2.getIncomingCost() != null ? item2.getIncomingCost() : BigDecimal.ZERO;
                    return cost1.compareTo(cost2);
                })
                .setSortable(true)
                .setClassNameGenerator(item -> "new-pricing");

        columns.newPriceCol = grid.addComponentColumn(item -> createNewPriceCell(item))
                .setHeader(createHeaderWithTooltip("Price", "Proposed unit sell price (calculated by rules or manually set)"))
                .setKey("newPrice").setAutoWidth(true).setResizable(true)
                .setComparator((item1, item2) -> {
                    BigDecimal price1 = item1.getNewUnitSellPrice() != null ? item1.getNewUnitSellPrice() : BigDecimal.ZERO;
                    BigDecimal price2 = item2.getNewUnitSellPrice() != null ? item2.getNewUnitSellPrice() : BigDecimal.ZERO;
                    return price1.compareTo(price2);
                })
                .setSortable(true)
                .setClassNameGenerator(item -> "new-pricing");

        columns.newAmountCol = grid.addColumn(item -> calculator.formatCurrency(item.getNewAmount()))
                .setHeader(createHeaderWithTooltip("Amount", "Projected total revenue: New Price × Quantity"))
                .setKey("newAmount").setAutoWidth(true).setResizable(true).setSortable(true)
                .setClassNameGenerator(item -> "new-pricing");

        columns.newGPCol = grid.addComponentColumn(item -> createNewGPCell(item))
                .setHeader(createHeaderWithTooltip("GP $", "Projected gross profit: New Amount - (New Cost × Quantity)"))
                .setKey("newGP").setAutoWidth(true).setResizable(true)
                .setComparator((item1, item2) -> {
                    BigDecimal gp1 = item1.getNewGrossProfit() != null ? item1.getNewGrossProfit() : BigDecimal.ZERO;
                    BigDecimal gp2 = item2.getNewGrossProfit() != null ? item2.getNewGrossProfit() : BigDecimal.ZERO;
                    return gp1.compareTo(gp2);
                })
                .setSortable(true)
                .setClassNameGenerator(item -> "new-pricing");

        columns.newGPPercentCol = grid.addComponentColumn(item -> createNewGPPercentCell(item))
                .setHeader(createHeaderWithTooltip("GP %", "Projected margin: (New GP $ / New Amount) × 100. Green = improved, Red = decreased"))
                .setKey("newGPPercent").setAutoWidth(true).setResizable(true)
                .setComparator((item1, item2) -> {
                    BigDecimal gp1 = calculator.calculateGPPercent(item1.getNewGrossProfit(), item1.getNewAmount());
                    BigDecimal gp2 = calculator.calculateGPPercent(item2.getNewGrossProfit(), item2.getNewAmount());
                    if (gp1 == null && gp2 == null) return 0;
                    if (gp1 == null) return -1;
                    if (gp2 == null) return 1;
                    return gp1.compareTo(gp2);
                })
                .setSortable(true)
                .setClassNameGenerator(item -> "new-pricing");

        // NOTES column
        columns.notesCol = grid.addColumn(item ->
                item.isManualOverride() ? "Manual Override" : "")
                .setHeader("Notes").setKey("notes").setAutoWidth(true).setResizable(true);

        // Create nested headers
        HeaderRow topHeader = grid.prependHeaderRow();
        topHeader.join(columns.customerCol, columns.ratingCol, columns.productCodeCol, columns.productCol, columns.qtyCol).setText("Basic Info");
        topHeader.join(columns.lastCostCol, columns.lastPriceCol, columns.lastAmountCol, columns.lastGPCol, columns.lastGPPercentCol).setText("Historical (Last)");
        topHeader.getCell(columns.costDriftCol).setText("Δ Cost");
        topHeader.join(columns.newCostCol, columns.newPriceCol, columns.newAmountCol, columns.newGPCol, columns.newGPPercentCol).setText("New Pricing");
        topHeader.getCell(columns.notesCol).setText("");

        // Add footer row for totals
        footerRow = grid.appendFooterRow();

        return grid;
    }

    /**
     * Create cost drift cell with color coding
     */
    private Span createCostDriftCell(GroupedLineItem item) {
        BigDecimal lastCost = item.getLastCost();
        BigDecimal newCost = item.getIncomingCost();

        if (lastCost == null || newCost == null ||
            lastCost.compareTo(BigDecimal.ZERO) == 0 ||
            newCost.compareTo(BigDecimal.ZERO) == 0) {
            Span naSpan = new Span("N/A");
            naSpan.getStyle().set("color", "var(--lumo-disabled-text-color)");
            return naSpan;
        }

        BigDecimal absoluteDrift = newCost.subtract(lastCost);
        BigDecimal percentDrift = absoluteDrift
                .divide(lastCost, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        String sign = absoluteDrift.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        String driftText = String.format("%s%s (%s%.1f%%)",
                sign, calculator.formatCurrency(absoluteDrift).replace("$", "$"),
                sign, percentDrift);

        Span driftSpan = new Span(driftText);

        String color = null;
        if (percentDrift.abs().compareTo(BigDecimal.valueOf(20)) > 0) {
            color = percentDrift.compareTo(BigDecimal.ZERO) > 0 ?
                    "var(--lumo-error-color)" : "var(--lumo-success-color)";
        } else if (percentDrift.abs().compareTo(BigDecimal.valueOf(5)) > 0) {
            color = percentDrift.compareTo(BigDecimal.ZERO) > 0 ?
                    "var(--lumo-warning-text-color)" : "var(--lumo-success-text-color)";
        } else {
            color = "var(--lumo-contrast-70pct)";
        }

        if (color != null) {
            driftSpan.getStyle().set("color", color).set("font-weight", "500");
        }

        return driftSpan;
    }

    /**
     * Compare two items by cost drift for sorting
     */
    private int compareCostDrift(GroupedLineItem item1, GroupedLineItem item2) {
        BigDecimal cost1Last = item1.getLastCost();
        BigDecimal cost1New = item1.getIncomingCost();
        BigDecimal cost2Last = item2.getLastCost();
        BigDecimal cost2New = item2.getIncomingCost();

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
    }

    /**
     * Create new cost cell with change indicator
     */
    private HorizontalLayout createNewCostCell(GroupedLineItem item) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setSpacing(false);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.getStyle().set("gap", "4px");

        Span costSpan = new Span(calculator.formatCurrency(item.getIncomingCost()));

        BigDecimal lastCost = item.getLastCost();
        BigDecimal newCost = item.getIncomingCost();

        if (lastCost != null && newCost != null &&
            lastCost.compareTo(BigDecimal.ZERO) != 0 &&
            newCost.compareTo(BigDecimal.ZERO) != 0) {

            BigDecimal changePercent = newCost.subtract(lastCost)
                    .divide(lastCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            Icon icon = null;
            String colorStyle = null;

            if (changePercent.compareTo(BigDecimal.valueOf(20)) > 0) {
                icon = VaadinIcon.ARROW_CIRCLE_UP.create();
                colorStyle = "var(--lumo-error-color)";
            } else if (changePercent.compareTo(BigDecimal.valueOf(5)) > 0) {
                icon = VaadinIcon.ARROW_UP.create();
                colorStyle = "var(--lumo-warning-text-color)";
            } else if (changePercent.compareTo(BigDecimal.valueOf(-5)) >= 0) {
                icon = null;
            } else if (changePercent.compareTo(BigDecimal.valueOf(-20)) >= 0) {
                icon = VaadinIcon.ARROW_DOWN.create();
                colorStyle = "var(--lumo-success-text-color)";
            } else {
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
            layout.add(costSpan);
        }

        return layout;
    }

    /**
     * Create new price cell (clickable)
     */
    private HorizontalLayout createNewPriceCell(GroupedLineItem item) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setSpacing(false);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        Span priceSpan = new Span(calculator.formatCurrency(item.getNewUnitSellPrice()));

        priceSpan.getStyle()
            .set("color", "#1676f3")
            .set("cursor", "pointer")
            .set("text-decoration", "none");

        priceSpan.addClassName("price-link");
        priceSpan.getElement().executeJs(
            "this.addEventListener('mouseenter', () => { this.style.textDecoration = 'underline'; });" +
            "this.addEventListener('mouseleave', () => { this.style.textDecoration = 'none'; });"
        );

        if (item.isManualOverride()) {
            priceSpan.getStyle()
                .set("background-color", "orange")
                .set("color", "black")
                .set("padding", "2px 4px")
                .set("border-radius", "3px");
        }

        layout.add(priceSpan);
        layout.getStyle().set("cursor", "pointer");
        layout.addClickListener(e -> dialogFactory.openPriceEditDialog(item, updatedItem -> {
            hasUnsavedChanges = true;
            updateTitleStyle();
            applySecondaryFilters();
            sessionManager.saveToSession(backingList, currentSession, hasUnsavedChanges);
        }, currentSession));

        return layout;
    }

    /**
     * Create new GP$ cell with color coding
     */
    private Span createNewGPCell(GroupedLineItem item) {
        BigDecimal newGP = item.getNewGrossProfit();
        Span gpSpan = new Span(calculator.formatCurrency(newGP));

        BigDecimal newGPPercent = calculator.calculateGPPercent(item.getNewGrossProfit(), item.getNewAmount());
        BigDecimal lastGPPercent = calculator.calculateGPPercent(item.getLastGrossProfit(), item.getLastAmount());
        BigDecimal tolerance = new BigDecimal("0.1");

        String color = calculator.determineGPPercentColor(newGPPercent, lastGPPercent, tolerance);

        if (color != null) {
            gpSpan.getStyle().set("color", color).set("font-weight", "bold");
        }

        return gpSpan;
    }

    /**
     * Create new GP% cell with color coding
     */
    private Span createNewGPPercentCell(GroupedLineItem item) {
        String gpPercentText = calculator.formatGPPercent(item.getNewGrossProfit(), item.getNewAmount());
        BigDecimal newGPPercent = calculator.calculateGPPercent(item.getNewGrossProfit(), item.getNewAmount());
        BigDecimal lastGPPercent = calculator.calculateGPPercent(item.getLastGrossProfit(), item.getLastAmount());
        BigDecimal tolerance = new BigDecimal("0.1");

        Span gpPercentSpan = new Span(gpPercentText);
        String color = calculator.determineGPPercentColor(newGPPercent, lastGPPercent, tolerance);

        // Debug logging for specific edge cases
        if (item.getProductCode() != null &&
            (item.getProductCode().equals("SCRM200") || item.getProductCode().equals("LFOCH"))) {
            log.info("GP% Coloring Debug - Product: {}, Customer: {}, " +
                    "NewGP$: {}, NewAmount: {}, NewGP%: {}, " +
                    "LastGP$: {}, LastAmount: {}, LastGP%: {}, " +
                    "Difference: {}, Color: {}",
                    item.getProductCode(), item.getCustomerName(),
                    item.getNewGrossProfit(), item.getNewAmount(), newGPPercent,
                    item.getLastGrossProfit(), item.getLastAmount(), lastGPPercent,
                    newGPPercent != null && lastGPPercent != null ?
                        newGPPercent.subtract(lastGPPercent) : "N/A",
                    color);
        }

        if (color != null) {
            gpPercentSpan.getStyle().set("color", color).set("font-weight", "bold");
        }

        return gpPercentSpan;
    }

    /**
     * Apply date range filter
     */
    private void applyFilter() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (dataGrid != null) {
            backingList = filterManager.applyDateFilter(startDate, endDate);
            applySecondaryFilters();
            sessionManager.saveToSession(backingList, currentSession, hasUnsavedChanges);
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

        List<GroupedLineItem> filteredItems = filterManager.applySecondaryFilters(
            backingList, customerFilter, productFilterValue);

        dataGrid.setItems(filteredItems);
        columnManager.updateFooterTotals(footerRow, filteredItems, columns);
    }

    /**
     * Handle apply pricing rules
     */
    private void handleApplyRules() {
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
                PricingCalculator.PricingApplicationResult result = calculator.applyPricingRules(backingList);

                hasUnsavedChanges = true;
                updateTitleStyle();
                applySecondaryFilters();
                sessionManager.saveToSession(backingList, currentSession, hasUnsavedChanges);

                showSuccessNotification(result.getMessage());

            } catch (Exception e) {
                log.error("Error applying pricing rules", e);
                showErrorNotification("Error applying rules: " + e.getMessage());
            }
        });

        confirmDialog.open();
    }

    /**
     * Handle save session
     */
    private void handleSaveSession() {
        dialogFactory.openSaveSessionDialog(backingList, currentSession, result -> {
            currentSession = result.getSavedSession();
            hasUnsavedChanges = result.hasUnsavedChanges();
            updateTitleStyle();
            // Persist updated session state to Vaadin session
            sessionManager.saveToSession(backingList, currentSession, hasUnsavedChanges);
        });
    }

    /**
     * Handle finalize or unfinalize session
     * Toggles between FINALIZED and IN_PROGRESS status
     */
    private void handleFinalizeOrUnfinalizeSession() {
        if (currentSession == null || currentSession.getId() == null) {
            showErrorNotification("No session loaded. Please save the session first.");
            return;
        }

        boolean isFinalized = "FINALIZED".equals(currentSession.getStatus());

        if (!isFinalized) {
            // Finalizing the session
            if (hasUnsavedChanges) {
                showErrorNotification("Session has unsaved changes. Please save before finalizing.");
                return;
            }

            // Confirm finalization
            ConfirmDialog confirmDialog = new ConfirmDialog();
            confirmDialog.setHeader("Finalize Session");
            confirmDialog.setText(String.format(
                    "Finalize session '%s'? Finalized sessions can be used to generate customer price lists.",
                    currentSession.getSessionName()));
            confirmDialog.setCancelable(true);
            confirmDialog.setCancelText("Cancel");
            confirmDialog.setConfirmText("Finalize");
            confirmDialog.setConfirmButtonTheme("success primary");

            confirmDialog.addConfirmListener(event -> {
                try {
                    pricingSessionService.finalizeSession(currentSession.getId());
                    currentSession.setStatus("FINALIZED");
                    updateButtonStates();

                    showSuccessNotification(String.format(
                            "Session '%s' has been finalized! You can now generate customer price lists from Reports → Customer Price Lists.",
                            currentSession.getSessionName()));

                    log.info("Session finalized: {} (ID: {})", currentSession.getSessionName(), currentSession.getId());

                } catch (Exception e) {
                    log.error("Error finalizing session", e);
                    showErrorNotification("Error finalizing session: " + e.getMessage());
                }
            });

            confirmDialog.open();
        } else {
            // Unfinalizing the session
            ConfirmDialog confirmDialog = new ConfirmDialog();
            confirmDialog.setHeader("Unfinalize Session");
            confirmDialog.setText(String.format(
                    "Unfinalize session '%s'? This will allow you to make changes to the session.",
                    currentSession.getSessionName()));
            confirmDialog.setCancelable(true);
            confirmDialog.setCancelText("Cancel");
            confirmDialog.setConfirmText("Unfinalize");
            confirmDialog.setConfirmButtonTheme("primary");

            confirmDialog.addConfirmListener(event -> {
                try {
                    pricingSessionService.unfinalizeSession(currentSession.getId());
                    currentSession.setStatus("IN_PROGRESS");
                    updateButtonStates();

                    showSuccessNotification(String.format(
                            "Session '%s' has been unfinalized. You can now make changes.",
                            currentSession.getSessionName()));

                    log.info("Session unfinalized: {} (ID: {})", currentSession.getSessionName(), currentSession.getId());

                } catch (Exception e) {
                    log.error("Error unfinalizing session", e);
                    showErrorNotification("Error unfinalizing session: " + e.getMessage());
                }
            });

            confirmDialog.open();
        }
    }

    /**
     * Handle load session
     */
    private void handleLoadSession() {
        dialogFactory.openLoadSessionDialog(hasUnsavedChanges, currentSession, result -> {
            if (result.isSessionDeleted()) {
                currentSession = null;
                hasUnsavedChanges = false;
                updateTitleStyle();
                // Persist cleared session state to Vaadin session
                sessionManager.saveToSession(backingList, currentSession, hasUnsavedChanges);
            } else if (result.getLoadedItems() != null) {
                backingList = new ArrayList<>(result.getLoadedItems());
                currentSession = result.getSession();
                hasUnsavedChanges = result.hasUnsavedChanges();
                updateTitleStyle();
                applySecondaryFilters();
                sessionManager.saveToSession(backingList, currentSession, hasUnsavedChanges);
            }
        });
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
                sessionManager.saveToSession(backingList, currentSession, hasUnsavedChanges);
                showSuccessNotification("Started new session");
            });

            confirmDialog.open();
        } else {
            currentSession = null;
            hasUnsavedChanges = false;
            updateTitleStyle();
            sessionManager.saveToSession(backingList, currentSession, hasUnsavedChanges);
            showSuccessNotification("Started new session");
        }
    }

    /**
     * Handle clear working data
     */
    private void handleClearWorkingData() {
        if (backingList.isEmpty() && currentSession == null) {
            Notification.show("No working data to clear", 3000, Notification.Position.BOTTOM_START);
            return;
        }

        ConfirmDialog confirmDialog = new ConfirmDialog();
        confirmDialog.setHeader("Clear Working Data");
        confirmDialog.setText("This will clear all unsaved work and reset the view. Continue?");
        confirmDialog.setCancelable(true);
        confirmDialog.setCancelText("Cancel");
        confirmDialog.setConfirmText("Clear");
        confirmDialog.setConfirmButtonTheme("error primary");

        confirmDialog.addConfirmListener(event -> {
            backingList.clear();
            dataGrid.setItems(backingList);
            columnManager.updateFooterTotals(footerRow, backingList, columns);
            currentSession = null;
            hasUnsavedChanges = false;
            updateTitleStyle();
            sessionManager.clearSessionData();
            showSuccessNotification("Working data cleared");
        });

        confirmDialog.open();
    }

    /**
     * Handle column visibility dialog
     */
    private void handleColumnVisibility() {
        dialogFactory.openColumnVisibilityDialog(columns, () -> {
            columnManager.saveColumnVisibility(this, columns);
        });
    }

    /**
     * Update button states based on session finalization status
     */
    private void updateButtonStates() {
        boolean isFinalized = currentSession != null && "FINALIZED".equals(currentSession.getStatus());

        if (isFinalized) {
            // Change to Unfinalize button
            finalizeSessionButton.setText("Unfinalize");
            finalizeSessionButton.setIcon(new Icon(VaadinIcon.UNLOCK));
            finalizeSessionButton.removeThemeVariants(ButtonVariant.LUMO_SUCCESS);
            finalizeSessionButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

            // Disable Save button if there are unsaved changes (user must unfinalize first)
            if (hasUnsavedChanges) {
                saveSessionButton.setEnabled(false);
                saveSessionButton.setTooltipText("Cannot save finalized session. Click Unfinalize to make changes.");
            } else {
                saveSessionButton.setEnabled(true);
                saveSessionButton.setTooltipText(null);
            }
        } else {
            // Change to Finalize button
            finalizeSessionButton.setText("Finalize");
            finalizeSessionButton.setIcon(new Icon(VaadinIcon.CHECK_CIRCLE));
            finalizeSessionButton.removeThemeVariants(ButtonVariant.LUMO_CONTRAST);
            finalizeSessionButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

            // Enable Save button
            saveSessionButton.setEnabled(true);
            saveSessionButton.setTooltipText(null);
        }
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

        // Update button states
        updateButtonStates();
    }

    /**
     * BeforeLeaveObserver implementation to warn about unsaved changes
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
