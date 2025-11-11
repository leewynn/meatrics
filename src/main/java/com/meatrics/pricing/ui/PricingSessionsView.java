package com.meatrics.pricing.ui;

import com.meatrics.base.ui.AbstractGridView;
import com.meatrics.pricing.Customer;
import com.meatrics.pricing.CustomerRatingService;
import com.meatrics.pricing.CustomerRepository;
import com.meatrics.pricing.GroupedLineItem;
import com.meatrics.pricing.PricingImportService;
import com.meatrics.pricing.PricingSession;
import com.meatrics.pricing.PricingSessionService;
import com.meatrics.pricing.ui.component.CustomerEditDialog;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Pricing sessions view for editing and adjusting prices
 * DISABLED - Replaced by PricingSessionsViewNew
 */
// @Route("pricing-sessions")
// @PageTitle("Pricing Sessions")
// @Menu(order = 1, icon = "vaadin:edit", title = "Pricing Sessions")
public class PricingSessionsView extends AbstractGridView {

    private static final String STORAGE_PREFIX = "PricingSessionsView-column-";

    @Override
    protected String getStoragePrefix() {
        return STORAGE_PREFIX;
    }

    private final PricingImportService pricingImportService;
    private final CustomerRepository customerRepository;
    private final CustomerRatingService customerRatingService;
    private final PricingSessionService pricingSessionService;
    private final Grid<GroupedLineItem> dataGrid;
    private final DatePicker startDatePicker;
    private final DatePicker endDatePicker;
    private final TextField customerNameFilter;
    private final TextField productFilter;
    private Button undoButton;
    private H2 titleComponent;

    // Backing list that holds date-filtered grouped records
    private List<GroupedLineItem> backingList = new ArrayList<>();

    // Undo state tracking
    private List<UndoState> lastUndoState = null;

    // Session state tracking
    private PricingSession currentSession = null;
    private boolean hasUnsavedChanges = false;

    // Column visibility checkboxes
    private Checkbox customerNameCheck;
    private Checkbox customerRatingCheck;
    private Checkbox productCodeCheck;
    private Checkbox productCheck;
    private Checkbox quantityCheck;
    private Checkbox amountCheck;
    private Checkbox unitSellPriceCheck;
    private Checkbox costCheck;
    private Checkbox unitCostPriceCheck;
    private Checkbox grossProfitCheck;

    // Inner class to track undo state
    private static class UndoState {
        GroupedLineItem item;
        BigDecimal originalAmount;
        boolean originalModified;
        BigDecimal savedOriginalAmount;

        UndoState(GroupedLineItem item) {
            this.item = item;
            this.originalAmount = item.getTotalAmount();
            this.originalModified = item.isAmountModified();
            this.savedOriginalAmount = item.getOriginalAmount();
        }
    }

    public PricingSessionsView(PricingImportService pricingImportService,
                               CustomerRepository customerRepository,
                               CustomerRatingService customerRatingService,
                               PricingSessionService pricingSessionService) {
        this.pricingImportService = pricingImportService;
        this.customerRepository = customerRepository;
        this.customerRatingService = customerRatingService;
        this.pricingSessionService = pricingSessionService;
        addClassName("pricing-sessions-view");

        // Main layout
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setSpacing(true);
        mainLayout.setPadding(true);

        // Title and top buttons
        titleComponent = new H2("Pricing Sessions");

        Button saveSessionButton = new Button("Save Session", new Icon(VaadinIcon.DISC));
        saveSessionButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveSessionButton.addClickListener(event -> openSaveSessionDialog());

        Button loadSessionButton = new Button("Load Session", new Icon(VaadinIcon.FOLDER_OPEN));
        loadSessionButton.addClickListener(event -> openLoadSessionDialog());

        Button newSessionButton = new Button("New Session", new Icon(VaadinIcon.PLUS));
        newSessionButton.addClickListener(event -> handleNewSession());

        HorizontalLayout topButtonsLayout = new HorizontalLayout(saveSessionButton, loadSessionButton, newSessionButton);
        topButtonsLayout.setSpacing(true);

        HorizontalLayout titleLayout = new HorizontalLayout(titleComponent, topButtonsLayout);
        titleLayout.setWidthFull();
        titleLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        titleLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        // Date range filter
        startDatePicker = new DatePicker("Start Date");
        startDatePicker.setPlaceholder("Select start date");
        startDatePicker.setClearButtonVisible(true);
        startDatePicker.setLocale(Locale.UK); // Use dd/MM/yyyy format
        // Set default to first day of current month
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.withDayOfMonth(1));

        endDatePicker = new DatePicker("End Date");
        endDatePicker.setPlaceholder("Select end date");
        endDatePicker.setClearButtonVisible(true);
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

        Button applyFilterButton = new Button("Search", new Icon(VaadinIcon.FILTER));
        applyFilterButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        applyFilterButton.addClickListener(event -> applyFilter());


        undoButton = new Button("Undo", new Icon(VaadinIcon.ARROW_BACKWARD));
        undoButton.setEnabled(false);
        undoButton.addClickListener(event -> undoLastAction());

        HorizontalLayout dateFilterLayout = new HorizontalLayout(startDatePicker, endDatePicker, applyFilterButton, undoButton);
        dateFilterLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        dateFilterLayout.setSpacing(true);

        // Customer and Product filters (work on backing list)
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

        HorizontalLayout secondaryFilterLayout = new HorizontalLayout(customerNameFilter, productFilter);
        secondaryFilterLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        secondaryFilterLayout.setSpacing(true);

        VerticalLayout filterLayout = new VerticalLayout(dateFilterLayout, secondaryFilterLayout);
        filterLayout.setSpacing(false);
        filterLayout.setPadding(false);

        // Create data grid
        dataGrid = createDataGrid();

        // Create column visibility toggles (collapsible)
        Details columnToggles = createColumnVisibilityToggles();

        // Add components to main layout
        mainLayout.add(titleLayout, filterLayout, columnToggles, dataGrid);

        add(mainLayout);

        // Restore column visibility from localStorage
        restoreColumnVisibility();

        // Removed automatic data loading - data loads only when user clicks "Apply Filter"
    }

    private Grid<GroupedLineItem> createDataGrid() {
        Grid<GroupedLineItem> grid = new Grid<>(GroupedLineItem.class, false);
        grid.setSizeFull();
        grid.setMinHeight("600px");

        // Enable column reordering
        grid.setColumnReorderingAllowed(true);

        // Add columns with keys for visibility toggling
        // Customer name column - clickable to edit customer details
        grid.addColumn(new ComponentRenderer<>(item -> {
                    Button customerButton = new Button(item.getCustomerName());
                    customerButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
                    customerButton.getStyle().set("padding", "0");
                    customerButton.addClickListener(e -> openCustomerEditDialog(item));
                    return customerButton;
                }))
                .setHeader("Customer Name")
                .setKey("customerName")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true)
                .setComparator(GroupedLineItem::getCustomerName);

        // Customer rating column (uses session data if available, otherwise from repository)
        grid.addColumn(item -> {
                    // If session is loaded and item has customerRating, use that
                    if (item.getCustomerRating() != null && !item.getCustomerRating().trim().isEmpty()) {
                        return item.getCustomerRating();
                    }

                    // Otherwise, fetch from repository
                    String customerCode = item.getCustomerCode();
                    if (customerCode == null || customerCode.trim().isEmpty()) {
                        return "";
                    }
                    return customerRepository.findByCustomerCode(customerCode)
                            .map(customer -> customer.getCustomerRating() != null ? customer.getCustomerRating() : "")
                            .orElse("");
                })
                .setHeader("Customer Rating")
                .setKey("customerRating")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(GroupedLineItem::getProductCode)
                .setHeader("Product Code")
                .setKey("productCode")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(GroupedLineItem::getProductDescription)
                .setHeader("Product")
                .setKey("productDescription")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(GroupedLineItem::getQuantityFormatted)
                .setHeader("Quantity")
                .setKey("quantity")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(GroupedLineItem::getCostFormatted)
                .setHeader("Cost")
                .setKey("cost")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(new ComponentRenderer<>(item -> {
                    Span amountSpan = new Span(item.getAmountFormatted());
                    if (item.isAmountModified()) {
                        amountSpan.getStyle().set("color", "green");
                    }
                    return amountSpan;
                }))
                .setHeader("Amount")
                .setKey("amount")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true)
                .setComparator((item1, item2) -> {
                    // Compare BigDecimal values directly
                    if (item1.getTotalAmount() == null && item2.getTotalAmount() == null) return 0;
                    if (item1.getTotalAmount() == null) return -1;
                    if (item2.getTotalAmount() == null) return 1;
                    return item1.getTotalAmount().compareTo(item2.getTotalAmount());
                });

        grid.addColumn(GroupedLineItem::getUnitCostPrice)
                .setHeader("Unit Cost Price")
                .setKey("unitCostPrice")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(new ComponentRenderer<>(item -> {
                    Button unitSellPriceButton = new Button(item.getUnitSellPrice());
                    unitSellPriceButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                    if (item.isAmountModified()) {
                        unitSellPriceButton.getStyle().set("color", "green");
                    }
                    unitSellPriceButton.addClickListener(event -> openUnitSellPriceEditDialog(item));
                    return unitSellPriceButton;
                }))
                .setHeader("Unit Sell Price")
                .setKey("unitSellPrice")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(new ComponentRenderer<>(item -> {
                    Span grossProfitSpan = new Span(item.getGrossProfitFormatted());
                    if (item.isAmountModified()) {
                        grossProfitSpan.getStyle().set("color", "green");
                    }
                    return grossProfitSpan;
                }))
                .setHeader("Gross Profit")
                .setKey("grossProfit")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        // Add footer row for totals
        var footerRow = grid.appendFooterRow();
        footerRow.getCell(grid.getColumnByKey("customerName")).setText("Total:");

        return grid;
    }

    private Details createColumnVisibilityToggles() {
        HorizontalLayout checkboxLayout = new HorizontalLayout();
        checkboxLayout.setSpacing(true);

        // Create checkboxes for each column
        customerNameCheck = new Checkbox("Customer Name", true);
        customerNameCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("customerName").setVisible(e.getValue());
            saveColumnVisibility("customerName", e.getValue());
        });

        customerRatingCheck = new Checkbox("Customer Rating", true);
        customerRatingCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("customerRating").setVisible(e.getValue());
            saveColumnVisibility("customerRating", e.getValue());
        });

        productCodeCheck = new Checkbox("Product Code", true);
        productCodeCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("productCode").setVisible(e.getValue());
            saveColumnVisibility("productCode", e.getValue());
        });

        productCheck = new Checkbox("Product", true);
        productCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("productDescription").setVisible(e.getValue());
            saveColumnVisibility("productDescription", e.getValue());
        });

        quantityCheck = new Checkbox("Quantity", true);
        quantityCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("quantity").setVisible(e.getValue());
            saveColumnVisibility("quantity", e.getValue());
        });

        amountCheck = new Checkbox("Amount", true);
        amountCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("amount").setVisible(e.getValue());
            saveColumnVisibility("amount", e.getValue());
        });

        unitSellPriceCheck = new Checkbox("Unit Sell Price", true);
        unitSellPriceCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("unitSellPrice").setVisible(e.getValue());
            saveColumnVisibility("unitSellPrice", e.getValue());
        });

        costCheck = new Checkbox("Cost", true);
        costCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("cost").setVisible(e.getValue());
            saveColumnVisibility("cost", e.getValue());
        });

        unitCostPriceCheck = new Checkbox("Unit Cost Price", true);
        unitCostPriceCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("unitCostPrice").setVisible(e.getValue());
            saveColumnVisibility("unitCostPrice", e.getValue());
        });

        grossProfitCheck = new Checkbox("Gross Profit", true);
        grossProfitCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("grossProfit").setVisible(e.getValue());
            saveColumnVisibility("grossProfit", e.getValue());
        });

        checkboxLayout.add(
            customerNameCheck, customerRatingCheck,
            productCodeCheck, productCheck, quantityCheck,
            amountCheck, unitSellPriceCheck, costCheck, unitCostPriceCheck,
            grossProfitCheck
        );

        // Wrap in Details component (collapsible)
        Span columnsText = new Span("Columns");
        columnsText.getStyle().set("font-size", "var(--lumo-font-size-s)");
        Details details = new Details(columnsText, checkboxLayout);
        details.setOpened(false); // Closed by default

        return details;
    }

    private void refreshGrid() {
        if (dataGrid != null) {
            backingList = pricingImportService.getGroupedLineItems();
            // Clear undo state when refreshing
            lastUndoState = null;
            undoButton.setEnabled(false);
            applySecondaryFilters();
        }
    }

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

            // Clear undo state when applying new date filter
            lastUndoState = null;
            undoButton.setEnabled(false);

            // Apply secondary filters on top of backing list
            applySecondaryFilters();
        }
    }

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

                // Product filter (checks both product code and description)
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

    private void updateFooterTotals(List<GroupedLineItem> items) {
        // Calculate totals
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalGrossProfit = BigDecimal.ZERO;

        for (GroupedLineItem item : items) {
            if (item.getTotalQuantity() != null) {
                totalQuantity = totalQuantity.add(item.getTotalQuantity());
            }
            if (item.getTotalCost() != null) {
                totalCost = totalCost.add(item.getTotalCost());
            }
            if (item.getTotalAmount() != null) {
                totalAmount = totalAmount.add(item.getTotalAmount());
                if (item.getTotalCost() != null) {
                    totalGrossProfit = totalGrossProfit.add(item.getTotalAmount().subtract(item.getTotalCost()));
                }
            }
        }

        // Update footer cells
        var footerRow = dataGrid.getFooterRows().getFirst();

        footerRow.getCell(dataGrid.getColumnByKey("quantity")).setText(
            String.format("%.2f", totalQuantity));

        footerRow.getCell(dataGrid.getColumnByKey("cost")).setText(
            String.format("$%.2f", totalCost));

        footerRow.getCell(dataGrid.getColumnByKey("amount")).setText(
            String.format("$%.2f", totalAmount));

        // Calculate gross profit percentage
        BigDecimal grossProfitPercentage = BigDecimal.ZERO;
        if (totalAmount.compareTo(BigDecimal.ZERO) != 0) {
            grossProfitPercentage = totalGrossProfit.divide(totalAmount, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        }

        footerRow.getCell(dataGrid.getColumnByKey("grossProfit")).setText(
            String.format("$%.2f (%.2f%%)", totalGrossProfit, grossProfitPercentage));
    }

    /**
     * Restore column visibility from browser localStorage
     */
    private void restoreColumnVisibility() {
        restoreColumn("customerName", customerNameCheck);
        restoreColumn("customerRating", customerRatingCheck);
        restoreColumn("productCode", productCodeCheck);
        restoreColumn("productDescription", productCheck);
        restoreColumn("quantity", quantityCheck);
        restoreColumn("amount", amountCheck);
        restoreColumn("unitSellPrice", unitSellPriceCheck);
        restoreColumn("cost", costCheck);
        restoreColumn("unitCostPrice", unitCostPriceCheck);
        restoreColumn("grossProfit", grossProfitCheck);
    }

    private void openUnitSellPriceEditDialog(GroupedLineItem item) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Unit Sell Price");
        dialog.setWidth("400px");

        // Create form layout
        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setSpacing(true);
        formLayout.setPadding(false);

        // Display fields (read-only)
        TextField productCodeField = new TextField("Product Code");
        productCodeField.setValue(item.getProductCode() != null ? item.getProductCode() : "");
        productCodeField.setReadOnly(true);
        productCodeField.setWidthFull();

        TextField productField = new TextField("Product");
        productField.setValue(item.getProductDescription() != null ? item.getProductDescription() : "");
        productField.setReadOnly(true);
        productField.setWidthFull();

        TextField unitCostField = new TextField("Unit Cost Price");
        unitCostField.setValue(item.getUnitCostPrice());
        unitCostField.setReadOnly(true);
        unitCostField.setWidthFull();

        // Display original unit sell price
        TextField originalUnitSellPriceField = new TextField("Original Unit Sell Price");
        originalUnitSellPriceField.setValue(item.getOriginalUnitSellPrice());
        originalUnitSellPriceField.setReadOnly(true);
        originalUnitSellPriceField.setWidthFull();

        // Add unit sell price field (to add/subtract from current)
        NumberField addUnitSellPriceField = new NumberField("Add to Unit Sell Price");
        addUnitSellPriceField.setWidthFull();
        addUnitSellPriceField.setPrefixComponent(new Span("$"));
        addUnitSellPriceField.setStep(0.01);
        addUnitSellPriceField.setHelperText("Enter a value to add (use negative to subtract)");
        addUnitSellPriceField.setValue(0.0);

        // Editable unit sell price field
        NumberField newUnitSellPriceField = new NumberField("New Unit Sell Price");
        newUnitSellPriceField.setWidthFull();
        newUnitSellPriceField.setPrefixComponent(new Span("$"));
        newUnitSellPriceField.setStep(0.01);

        // Calculate current unit sell price and set as default
        final double currentUnitSellPrice;
        if (item.getTotalAmount() != null && item.getTotalQuantity() != null &&
            item.getTotalQuantity().compareTo(BigDecimal.ZERO) != 0) {
            currentUnitSellPrice = item.getTotalAmount().divide(item.getTotalQuantity(), 2,
                java.math.RoundingMode.HALF_UP).doubleValue();
            newUnitSellPriceField.setValue(currentUnitSellPrice);
        } else {
            currentUnitSellPrice = 0.0;
            newUnitSellPriceField.setValue(0.0);
        }

        // Checkboxes for apply scope (declare before listeners that reference them)
        Checkbox applyToAllShownCheckbox = new Checkbox("Apply to all records shown");
        applyToAllShownCheckbox.setValue(false);

        Checkbox applyToAllCheckbox = new Checkbox("Apply to all customers with the same product code");
        applyToAllCheckbox.setValue(false);

        // Flag to track if update is from the add field
        final boolean[] isUpdatingFromAddField = {false};

        // Link add unit sell price field to new unit sell price field
        final double originalUnitSellPrice = currentUnitSellPrice;
        addUnitSellPriceField.addValueChangeListener(event -> {
            Double addValue = event.getValue();
            if (addValue != null) {
                isUpdatingFromAddField[0] = true;
                // Use BigDecimal to avoid floating-point precision issues
                BigDecimal result = BigDecimal.valueOf(originalUnitSellPrice)
                    .add(BigDecimal.valueOf(addValue))
                    .setScale(6, java.math.RoundingMode.HALF_UP);
                newUnitSellPriceField.setValue(result.doubleValue());
                isUpdatingFromAddField[0] = false;

                // Re-enable checkboxes when using add field
                applyToAllShownCheckbox.setEnabled(true);
                applyToAllCheckbox.setEnabled(true);
            }
        });

        // Add listener to detect manual changes to new unit sell price field
        newUnitSellPriceField.addValueChangeListener(event -> {
            if (!isUpdatingFromAddField[0] && event.isFromClient()) {
                // User manually changed the value
                applyToAllShownCheckbox.setValue(false);
                applyToAllCheckbox.setValue(false);
                applyToAllShownCheckbox.setEnabled(false);
                applyToAllCheckbox.setEnabled(false);
            }
        });

        Span helpText = new Span("Unchecked: Apply only to this customer-product combination. Note: Checkboxes are only available when using 'Add to Unit Sell Price'.");
        helpText.getStyle().set("font-size", "var(--lumo-font-size-s)");
        helpText.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // Make checkboxes mutually exclusive
        applyToAllShownCheckbox.addValueChangeListener(event -> {
            if (event.getValue()) {
                applyToAllCheckbox.setValue(false);
            }
        });

        applyToAllCheckbox.addValueChangeListener(event -> {
            if (event.getValue()) {
                applyToAllShownCheckbox.setValue(false);
            }
        });

        // Create a container for the add field and checkboxes with dashed border
        VerticalLayout addPriceGroup = new VerticalLayout();
        addPriceGroup.setSpacing(true);
        addPriceGroup.setPadding(true);
        addPriceGroup.getStyle().set("border", "1px dashed var(--lumo-contrast-30pct)");
        addPriceGroup.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        addPriceGroup.add(addUnitSellPriceField, applyToAllShownCheckbox, applyToAllCheckbox, helpText);

        formLayout.add(productCodeField, productField, unitCostField, originalUnitSellPriceField,
                      addPriceGroup, newUnitSellPriceField);

        // Buttons
        Button saveButton = new Button("Apply", event -> {
            Double newUnitSellPrice = newUnitSellPriceField.getValue();
            if (newUnitSellPrice != null) {
                // Calculate the price change delta
                Double priceChange = newUnitSellPrice - currentUnitSellPrice;
                applyUnitSellPriceChange(item, newUnitSellPrice, priceChange, applyToAllShownCheckbox.getValue(), applyToAllCheckbox.getValue());
                dialog.close();
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", event -> dialog.close());

        HorizontalLayout buttonLayout = new HorizontalLayout(saveButton, cancelButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();

        dialog.add(formLayout, buttonLayout);
        dialog.open();
    }

    private void applyUnitSellPriceChange(GroupedLineItem originalItem, Double newUnitSellPrice, Double priceChange,
                                          boolean applyToAllShown, boolean applyToAllInDateRange) {
        BigDecimal unitSellPrice = BigDecimal.valueOf(newUnitSellPrice);
        BigDecimal priceChangeDelta = BigDecimal.valueOf(priceChange);

        // Capture state for undo
        lastUndoState = new ArrayList<>();

        if (applyToAllShown) {
            // Apply to all currently visible/filtered records
            String customerFilter = customerNameFilter.getValue();
            String productFilterValue = productFilter.getValue();

            for (GroupedLineItem item : backingList) {
                // Apply same filtering logic as applySecondaryFilters
                boolean matches = true;

                // Customer name filter
                if (customerFilter != null && !customerFilter.trim().isEmpty()) {
                    String customerName = item.getCustomerName();
                    if (customerName == null ||
                        !customerName.toLowerCase().contains(customerFilter.toLowerCase().trim())) {
                        matches = false;
                    }
                }

                // Product filter
                if (matches && productFilterValue != null && !productFilterValue.trim().isEmpty()) {
                    String productCode = item.getProductCode();
                    String productDesc = item.getProductDescription();
                    String filterLower = productFilterValue.toLowerCase().trim();

                    boolean matchesCode = productCode != null &&
                        productCode.toLowerCase().contains(filterLower);
                    boolean matchesDesc = productDesc != null &&
                        productDesc.toLowerCase().contains(filterLower);

                    if (!matchesCode && !matchesDesc) {
                        matches = false;
                    }
                }

                if (matches && item.getTotalQuantity() != null) {
                    lastUndoState.add(new UndoState(item));

                    // Check if this is the original item
                    boolean isOriginalItem = originalItem.getCustomerName().equals(item.getCustomerName()) &&
                                            originalItem.getProductCode().equals(item.getProductCode());

                    BigDecimal newAmount;
                    if (isOriginalItem) {
                        // For the original item, use the absolute new price
                        newAmount = unitSellPrice.multiply(item.getTotalQuantity());
                    } else {
                        // For other items, add the delta to their current unit price
                        BigDecimal currentUnitPrice = item.getTotalAmount().divide(item.getTotalQuantity(), 2, java.math.RoundingMode.HALF_UP);
                        BigDecimal newUnitPrice = currentUnitPrice.add(priceChangeDelta);
                        newAmount = newUnitPrice.multiply(item.getTotalQuantity());
                    }

                    item.setTotalAmount(newAmount);
                    // Only mark as modified if the amount is different from the original
                    BigDecimal originalAmt = item.getOriginalAmount();
                    boolean isModified = (originalAmt != null && newAmount.compareTo(originalAmt) != 0);
                    item.setAmountModified(isModified);
                }
            }
        } else if (applyToAllInDateRange) {
            // Apply to all records with the same product code in backing list
            String targetProductCode = originalItem.getProductCode();
            for (GroupedLineItem item : backingList) {
                if (targetProductCode != null && targetProductCode.equals(item.getProductCode()) &&
                    item.getTotalQuantity() != null) {
                    lastUndoState.add(new UndoState(item));

                    // Check if this is the original item
                    boolean isOriginalItem = originalItem.getCustomerName().equals(item.getCustomerName()) &&
                                            originalItem.getProductCode().equals(item.getProductCode());

                    BigDecimal newAmount;
                    if (isOriginalItem) {
                        // For the original item, use the absolute new price
                        newAmount = unitSellPrice.multiply(item.getTotalQuantity());
                    } else {
                        // For other items, add the delta to their current unit price
                        BigDecimal currentUnitPrice = item.getTotalAmount().divide(item.getTotalQuantity(), 2, java.math.RoundingMode.HALF_UP);
                        BigDecimal newUnitPrice = currentUnitPrice.add(priceChangeDelta);
                        newAmount = newUnitPrice.multiply(item.getTotalQuantity());
                    }

                    item.setTotalAmount(newAmount);
                    // Only mark as modified if the amount is different from the original
                    BigDecimal originalAmt = item.getOriginalAmount();
                    boolean isModified = (originalAmt != null && newAmount.compareTo(originalAmt) != 0);
                    item.setAmountModified(isModified);
                }
            }
        } else {
            // Apply only to this specific customer-product combination
            String targetCustomer = originalItem.getCustomerName();
            String targetProductCode = originalItem.getProductCode();

            for (GroupedLineItem item : backingList) {
                if (targetCustomer != null && targetCustomer.equals(item.getCustomerName()) &&
                    targetProductCode != null && targetProductCode.equals(item.getProductCode()) &&
                    item.getTotalQuantity() != null) {
                    lastUndoState.add(new UndoState(item));
                    // For the original item (the only one in this case), use the absolute new price
                    BigDecimal newAmount = unitSellPrice.multiply(item.getTotalQuantity());
                    item.setTotalAmount(newAmount);
                    // Only mark as modified if the amount is different from the original
                    BigDecimal originalAmt = item.getOriginalAmount();
                    boolean isModified = (originalAmt != null && newAmount.compareTo(originalAmt) != 0);
                    item.setAmountModified(isModified);
                }
            }
        }

        // Enable undo button
        undoButton.setEnabled(true);

        // Mark as having unsaved changes
        hasUnsavedChanges = true;
        updateTitleStyle();

        // Refresh the grid to show updated amounts
        applySecondaryFilters();
    }

    private void applyAmountChange(GroupedLineItem originalItem, Double newAmount,
                                   boolean applyToAllShown, boolean applyToAllInDateRange) {
        BigDecimal newAmountValue = BigDecimal.valueOf(newAmount);

        // Capture state for undo
        lastUndoState = new ArrayList<>();

        if (applyToAllShown) {
            // Apply to all currently visible/filtered records
            String customerFilter = customerNameFilter.getValue();
            String productFilterValue = productFilter.getValue();

            for (GroupedLineItem item : backingList) {
                // Apply same filtering logic as applySecondaryFilters
                boolean matches = true;

                // Customer name filter
                if (customerFilter != null && !customerFilter.trim().isEmpty()) {
                    String customerName = item.getCustomerName();
                    if (customerName == null ||
                        !customerName.toLowerCase().contains(customerFilter.toLowerCase().trim())) {
                        matches = false;
                    }
                }

                // Product filter
                if (matches && productFilterValue != null && !productFilterValue.trim().isEmpty()) {
                    String productCode = item.getProductCode();
                    String productDesc = item.getProductDescription();
                    String filterLower = productFilterValue.toLowerCase().trim();

                    boolean matchesCode = productCode != null &&
                        productCode.toLowerCase().contains(filterLower);
                    boolean matchesDesc = productDesc != null &&
                        productDesc.toLowerCase().contains(filterLower);

                    if (!matchesCode && !matchesDesc) {
                        matches = false;
                    }
                }

                if (matches) {
                    lastUndoState.add(new UndoState(item));
                    item.setTotalAmount(newAmountValue);
                    // Only mark as modified if the amount is different from the original
                    BigDecimal originalAmt = item.getOriginalAmount();
                    boolean isModified = (originalAmt != null && newAmountValue.compareTo(originalAmt) != 0);
                    item.setAmountModified(isModified);
                }
            }
        } else if (applyToAllInDateRange) {
            // Apply to all records with the same product code in backing list
            String targetProductCode = originalItem.getProductCode();
            for (GroupedLineItem item : backingList) {
                if (targetProductCode != null && targetProductCode.equals(item.getProductCode())) {
                    lastUndoState.add(new UndoState(item));
                    item.setTotalAmount(newAmountValue);
                    // Only mark as modified if the amount is different from the original
                    BigDecimal originalAmt = item.getOriginalAmount();
                    boolean isModified = (originalAmt != null && newAmountValue.compareTo(originalAmt) != 0);
                    item.setAmountModified(isModified);
                }
            }
        } else {
            // Apply only to this specific customer-product combination
            String targetCustomer = originalItem.getCustomerName();
            String targetProductCode = originalItem.getProductCode();

            for (GroupedLineItem item : backingList) {
                if (targetCustomer != null && targetCustomer.equals(item.getCustomerName()) &&
                    targetProductCode != null && targetProductCode.equals(item.getProductCode())) {
                    lastUndoState.add(new UndoState(item));
                    item.setTotalAmount(newAmountValue);
                    // Only mark as modified if the amount is different from the original
                    BigDecimal originalAmt = item.getOriginalAmount();
                    boolean isModified = (originalAmt != null && newAmountValue.compareTo(originalAmt) != 0);
                    item.setAmountModified(isModified);
                }
            }
        }

        // Enable undo button
        undoButton.setEnabled(true);

        // Mark as having unsaved changes
        hasUnsavedChanges = true;
        updateTitleStyle();

        // Refresh the grid to show updated amounts
        applySecondaryFilters();
    }

    private void undoLastAction() {
        if (lastUndoState != null && !lastUndoState.isEmpty()) {
            // Restore all items to their previous state
            for (UndoState state : lastUndoState) {
                state.item.setOriginalAmount(state.savedOriginalAmount);
                state.item.setTotalAmount(state.originalAmount);
                state.item.setAmountModified(state.originalModified);
            }

            // Clear undo state and disable button
            lastUndoState = null;
            undoButton.setEnabled(false);

            // Mark as having unsaved changes
            hasUnsavedChanges = true;
            updateTitleStyle();

            // Refresh the grid to show restored values
            applySecondaryFilters();
        }
    }

    private void openCustomerEditDialog(GroupedLineItem item) {
        // Find customer by customer code
        String customerCode = item.getCustomerCode();
        if (customerCode == null || customerCode.trim().isEmpty()) {
            return;
        }

        customerRepository.findByCustomerCode(customerCode).ifPresent(customer -> {
            CustomerEditDialog dialog = new CustomerEditDialog(customer, customerRepository);
            dialog.open(null); // No callback needed for this view
        });
    }

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

    private void openSaveSessionDialog() {
        // If we have a current session, save directly without showing dialog
        if (currentSession != null) {
            try {
                currentSession = pricingSessionService.saveSession(currentSession.getSessionName(), backingList);
                hasUnsavedChanges = false;
                updateTitleStyle();

                Notification notification = Notification.show("Session '" + currentSession.getSessionName() + "' saved successfully", 3000, Notification.Position.BOTTOM_START);
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception e) {
                Notification.show("Error saving session: " + e.getMessage(), 5000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
            return;
        }

        // Otherwise, show dialog to create new session
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Save Pricing Session");
        dialog.setWidth("500px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        TextField sessionNameField = new TextField("Session Name");
        sessionNameField.setWidthFull();
        sessionNameField.setRequired(true);
        sessionNameField.setPlaceholder("Enter session name...");

        TextArea notesField = new TextArea("Notes (optional)");
        notesField.setWidthFull();
        notesField.setHeight("100px");

        dialogLayout.add(sessionNameField, notesField);

        Span errorSpan = new Span();
        errorSpan.getStyle().set("color", "red");
        errorSpan.setVisible(false);
        dialogLayout.add(errorSpan);

        Button saveButton = new Button("Save", event -> {
            String sessionName = sessionNameField.getValue();
            if (sessionName == null || sessionName.trim().isEmpty()) {
                errorSpan.setText("Session name cannot be empty");
                errorSpan.setVisible(true);
                return;
            }

            // Check if session name already exists
            if (pricingSessionService.sessionNameExists(sessionName)) {
                errorSpan.setText("Session name already exists. Please choose a different name.");
                errorSpan.setVisible(true);
                return;
            }

            try {
                // Save session
                PricingSession savedSession = pricingSessionService.saveSession(sessionName, backingList);
                savedSession.setNotes(notesField.getValue());

                // Update the notes separately if needed
                if (notesField.getValue() != null && !notesField.getValue().trim().isEmpty()) {
                    // The service doesn't handle notes in saveSession, so we need to update via repository
                    // For now, just set it on the object (it will be lost, but it's optional)
                }

                currentSession = savedSession;
                hasUnsavedChanges = false;
                updateTitleStyle();

                Notification notification = Notification.show("Session saved successfully!", 3000, Notification.Position.BOTTOM_START);
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                dialog.close();
            } catch (Exception e) {
                errorSpan.setText("Error saving session: " + e.getMessage());
                errorSpan.setVisible(true);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", event -> dialog.close());

        HorizontalLayout buttonLayout = new HorizontalLayout(saveButton, cancelButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();

        dialog.add(dialogLayout, buttonLayout);
        dialog.open();
    }

    private void openLoadSessionDialog() {
        // Check for unsaved changes
        if (hasUnsavedChanges) {
            ConfirmDialog confirmDialog = new ConfirmDialog();
            confirmDialog.setHeader("Unsaved Changes");

            // Make the message more informative
            String message = "You have unsaved changes in your current pricing session.";
            if (currentSession != null) {
                message += "\n\nCurrent session: " + currentSession.getSessionName();
            }
            confirmDialog.setText(message);

            // Configure Cancel button - this should be easily accessible
            confirmDialog.setCancelable(true);
            confirmDialog.setCancelText("Cancel");
            confirmDialog.setCancelButtonTheme("tertiary");

            // Configure Save & Load button (primary action)
            confirmDialog.setConfirmText("Save & Load");
            confirmDialog.setConfirmButtonTheme("success primary");
            confirmDialog.addConfirmListener(e -> {
                // Save current session first
                if (currentSession != null) {
                    try {
                        pricingSessionService.saveSession(currentSession.getSessionName(), backingList);
                        hasUnsavedChanges = false;
                        updateTitleStyle();
                        Notification.show("Session saved successfully", 3000, Notification.Position.BOTTOM_START)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        showLoadSessionDialog();
                    } catch (Exception ex) {
                        Notification.show("Error saving session: " + ex.getMessage(), 5000, Notification.Position.BOTTOM_START)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                        // Don't proceed to load dialog if save failed
                    }
                } else {
                    // No current session - save first with a name, then show load dialog
                    openSaveSessionDialogThenLoad();
                }
            });

            // Configure Discard & Load button (destructive action)
            confirmDialog.setRejectText("Discard & Load");
            confirmDialog.setRejectButtonTheme("error tertiary");
            confirmDialog.addRejectListener(e -> {
                hasUnsavedChanges = false;
                updateTitleStyle();
                Notification.show("Changes discarded", 3000, Notification.Position.BOTTOM_START);
                showLoadSessionDialog();
            });

            confirmDialog.open();
        } else {
            showLoadSessionDialog();
        }
    }

    /**
     * Opens the save session dialog, and upon successful save, opens the load session dialog
     */
    private void openSaveSessionDialogThenLoad() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Save Session Before Loading");
        dialog.setWidth("400px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        Span instructionText = new Span("Please provide a name for your current session before loading another one.");
        instructionText.getStyle().set("color", "var(--lumo-secondary-text-color)");

        TextField sessionNameField = new TextField("Session Name");
        sessionNameField.setWidthFull();
        sessionNameField.setRequired(true);
        sessionNameField.setPlaceholder("Enter session name");
        sessionNameField.focus();

        TextArea notesField = new TextArea("Notes (Optional)");
        notesField.setWidthFull();
        notesField.setPlaceholder("Add any notes about this session");

        dialogLayout.add(instructionText, sessionNameField, notesField);

        Button saveAndLoadButton = new Button("Save & Load", event -> {
            String sessionName = sessionNameField.getValue();
            if (sessionName == null || sessionName.trim().isEmpty()) {
                Notification.show("Please enter a session name", 3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                sessionNameField.focus();
                return;
            }

            try {
                String notes = notesField.getValue();
                if (notes != null && !notes.trim().isEmpty()) {
                    // If notes were provided, we'd need to update the session
                    // For now, just save with the name
                }

                pricingSessionService.saveSession(sessionName.trim(), backingList);
                hasUnsavedChanges = false;
                updateTitleStyle();

                Notification.show("Session '" + sessionName + "' saved successfully", 3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                dialog.close();
                showLoadSessionDialog();
            } catch (Exception ex) {
                Notification.show("Error saving session: " + ex.getMessage(), 5000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveAndLoadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", event -> dialog.close());

        HorizontalLayout buttonLayout = new HorizontalLayout(saveAndLoadButton, cancelButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();

        VerticalLayout fullLayout = new VerticalLayout(dialogLayout, buttonLayout);
        fullLayout.setSpacing(true);
        fullLayout.setPadding(true);

        dialog.add(fullLayout);
        dialog.open();
    }

    private void showLoadSessionDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Load Pricing Session");
        dialog.setWidth("800px");
        dialog.setHeight("600px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSizeFull();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        // Create grid to show sessions
        Grid<PricingSession> sessionsGrid = new Grid<>(PricingSession.class, false);
        sessionsGrid.setSizeFull();

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        sessionsGrid.addColumn(PricingSession::getSessionName)
                .setHeader("Session Name")
                .setAutoWidth(true)
                .setResizable(true);

        sessionsGrid.addColumn(session -> session.getCreatedDate() != null ? session.getCreatedDate().format(dateTimeFormatter) : "")
                .setHeader("Created Date")
                .setAutoWidth(true)
                .setResizable(true);

        sessionsGrid.addColumn(session -> session.getLastModifiedDate() != null ? session.getLastModifiedDate().format(dateTimeFormatter) : "")
                .setHeader("Last Modified")
                .setAutoWidth(true)
                .setResizable(true);

        sessionsGrid.addColumn(PricingSession::getStatus)
                .setHeader("Status")
                .setAutoWidth(true)
                .setResizable(true);

        // Load sessions
        List<PricingSession> sessions = pricingSessionService.getAllSessions();
        sessionsGrid.setItems(sessions);

        sessionsGrid.addItemDoubleClickListener(event -> {
            PricingSession selectedSession = event.getItem();
            loadSessionData(selectedSession);
            dialog.close();
        });

        dialogLayout.add(sessionsGrid);

        Button loadButton = new Button("Load", event -> {
            PricingSession selectedSession = sessionsGrid.asSingleSelect().getValue();
            if (selectedSession != null) {
                loadSessionData(selectedSession);
                dialog.close();
            } else {
                Notification.show("Please select a session to load", 3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        loadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button deleteButton = new Button("Delete", event -> {
            PricingSession selectedSession = sessionsGrid.asSingleSelect().getValue();
            if (selectedSession != null) {
                ConfirmDialog confirmDelete = new ConfirmDialog();
                confirmDelete.setHeader("Delete Session");
                confirmDelete.setText("Are you sure you want to delete session '" + selectedSession.getSessionName() + "'?");
                confirmDelete.setCancelable(true);
                confirmDelete.setConfirmText("Delete");
                confirmDelete.setConfirmButtonTheme("error primary");
                confirmDelete.addConfirmListener(e -> {
                    try {
                        pricingSessionService.deleteSession(selectedSession.getId());
                        sessions.remove(selectedSession);
                        sessionsGrid.getDataProvider().refreshAll();
                        Notification.show("Session deleted successfully", 3000, Notification.Position.BOTTOM_START)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    } catch (Exception ex) {
                        Notification.show("Error deleting session: " + ex.getMessage(), 5000, Notification.Position.BOTTOM_START)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    }
                });
                confirmDelete.open();
            } else {
                Notification.show("Please select a session to delete", 3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        Button cancelButton = new Button("Cancel", event -> dialog.close());

        HorizontalLayout buttonLayout = new HorizontalLayout(loadButton, deleteButton, cancelButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();

        VerticalLayout fullLayout = new VerticalLayout(dialogLayout, buttonLayout);
        fullLayout.setSizeFull();
        fullLayout.setSpacing(true);
        fullLayout.setPadding(true);

        dialog.add(fullLayout);
        dialog.open();
    }

    private void loadSessionData(PricingSession session) {
        try {
            List<GroupedLineItem> loadedItems = pricingSessionService.loadSession(session.getId());

            // Replace backing list with loaded data
            backingList = loadedItems;

            // Set current session
            currentSession = session;
            hasUnsavedChanges = false;

            // Clear undo state
            lastUndoState = null;
            undoButton.setEnabled(false);

            // Update title
            updateTitleStyle();

            // Refresh grid
            applySecondaryFilters();

            Notification.show("Session loaded successfully!", 3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            Notification.show("Error loading session: " + e.getMessage(), 5000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void handleNewSession() {
        if (hasUnsavedChanges) {
            ConfirmDialog confirmDialog = new ConfirmDialog();
            confirmDialog.setHeader("Unsaved Changes");
            confirmDialog.setText("You have unsaved changes. What would you like to do?");

            confirmDialog.setCancelable(true);
            confirmDialog.setCancelText("Cancel");

            confirmDialog.setConfirmText("Save");
            confirmDialog.setConfirmButtonTheme("primary");
            confirmDialog.addConfirmListener(e -> {
                openSaveSessionDialog();
            });

            confirmDialog.setRejectText("Discard");
            confirmDialog.setRejectButtonTheme("error");
            confirmDialog.addRejectListener(e -> {
                startNewSession();
            });

            confirmDialog.open();
        } else {
            startNewSession();
        }
    }

    private void startNewSession() {
        currentSession = null;
        hasUnsavedChanges = false;
        updateTitleStyle();

        Notification.show("Started new session. You can continue working with current data or query new data.",
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
