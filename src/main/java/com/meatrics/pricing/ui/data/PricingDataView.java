package com.meatrics.pricing.ui.data;

import com.meatrics.base.ui.AbstractGridView;
import com.meatrics.pricing.customer.Customer;
import com.meatrics.pricing.customer.CustomerRatingService;
import com.meatrics.pricing.customer.CustomerRepository;
import com.meatrics.pricing.importer.ImportedLineItem;
import com.meatrics.pricing.importer.PricingImportService;
import com.meatrics.pricing.ui.component.CustomerEditDialog;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Read-only pricing data view for viewing imported line items
 */
@Route("")
@PageTitle("Pricing Data")
@Menu(order = 0, icon = "vaadin:table", title = "Pricing Data")
public class PricingDataView extends AbstractGridView {

    private static final String STORAGE_PREFIX = "PricingDataView-column-";

    @Override
    protected String getStoragePrefix() {
        return STORAGE_PREFIX;
    }

    private final PricingImportService pricingImportService;
    private final CustomerRepository customerRepository;
    private final CustomerRatingService customerRatingService;
    private final Grid<ImportedLineItem> dataGrid;
    private final DatePicker startDatePicker;
    private final DatePicker endDatePicker;
    private final TextField customerNameFilter;
    private final TextField productFilter;

    // Backing list that holds date-filtered records
    private List<ImportedLineItem> backingList = new ArrayList<>();

    // Column visibility checkboxes
    private Checkbox customerNameCheck;
    private Checkbox customerRatingCheck;
    private Checkbox invoiceCheck;
    private Checkbox dateCheck;
    private Checkbox productCodeCheck;
    private Checkbox productCheck;
    private Checkbox quantityCheck;
    private Checkbox amountCheck;
    private Checkbox unitSellPriceCheck;
    private Checkbox costCheck;
    private Checkbox unitCostPriceCheck;
    private Checkbox grossProfitCheck;

    public PricingDataView(PricingImportService pricingImportService,
                           CustomerRepository customerRepository,
                           CustomerRatingService customerRatingService) {
        this.pricingImportService = pricingImportService;
        this.customerRepository = customerRepository;
        this.customerRatingService = customerRatingService;
        addClassName("pricing-data-view");

        // Main layout
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setSpacing(true);
        mainLayout.setPadding(true);

        // Title
        H2 title = new H2("Pricing Data");

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


        HorizontalLayout dateFilterLayout = new HorizontalLayout(startDatePicker, endDatePicker, applyFilterButton);
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
        mainLayout.add(title, filterLayout, columnToggles, dataGrid);

        add(mainLayout);

        // Restore column visibility from localStorage
        restoreColumnVisibility();

        // Removed automatic data loading - data loads only when user clicks "Apply Filter"
    }

    private Grid<ImportedLineItem> createDataGrid() {
        Grid<ImportedLineItem> grid = new Grid<>(ImportedLineItem.class, false);
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
                .setComparator(ImportedLineItem::getCustomerName);

        // Customer rating column
        grid.addColumn(item -> {
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

        grid.addColumn(ImportedLineItem::getInvoiceNumber)
                .setHeader("Invoice #")
                .setKey("invoiceNumber")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getTransactionDate)
                .setHeader("Date")
                .setKey("transactionDate")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getProductCode)
                .setHeader("Product Code")
                .setKey("productCode")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getProductDescription)
                .setHeader("Product")
                .setKey("productDescription")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getQuantity)
                .setHeader("Quantity")
                .setKey("quantity")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getCostFormatted)
                .setHeader("Cost")
                .setKey("cost")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getAmountFormatted)
                .setHeader("Amount")
                .setKey("amount")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getUnitCostPrice)
                .setHeader("Unit Cost Price")
                .setKey("unitCostPrice")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getUnitSellPrice)
                .setHeader("Unit Sell Price")
                .setKey("unitSellPrice")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getGrossProfitFormatted)
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

        invoiceCheck = new Checkbox("Invoice #", true);
        invoiceCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("invoiceNumber").setVisible(e.getValue());
            saveColumnVisibility("invoiceNumber", e.getValue());
        });

        dateCheck = new Checkbox("Date", true);
        dateCheck.addValueChangeListener(e -> {
            dataGrid.getColumnByKey("transactionDate").setVisible(e.getValue());
            saveColumnVisibility("transactionDate", e.getValue());
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
            customerNameCheck, customerRatingCheck, invoiceCheck, dateCheck,
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
            backingList = pricingImportService.getAllLineItems();
            applySecondaryFilters();
        }
    }

    private void applyFilter() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (dataGrid != null) {
            // Update backing list with date-filtered results
            if (startDate != null || endDate != null) {
                backingList = pricingImportService.getLineItemsByDateRange(startDate, endDate);
            } else {
                backingList = pricingImportService.getAllLineItems();
            }

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

        List<ImportedLineItem> filteredItems = backingList.stream()
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

    private void updateFooterTotals(List<ImportedLineItem> items) {
        // Calculate totals
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalGrossProfit = BigDecimal.ZERO;

        for (ImportedLineItem item : items) {
            if (item.getQuantity() != null) {
                totalQuantity = totalQuantity.add(item.getQuantity());
            }
            if (item.getCost() != null) {
                totalCost = totalCost.add(item.getCost());
            }
            if (item.getAmount() != null) {
                totalAmount = totalAmount.add(item.getAmount());
                if (item.getCost() != null) {
                    totalGrossProfit = totalGrossProfit.add(item.getAmount().subtract(item.getCost()));
                }
            }
        }

        // Update footer cells
        var footerRow = dataGrid.getFooterRows().get(0);

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

    private void clearFilter() {
        // Only clear customer and product filters, keep date range
        customerNameFilter.clear();
        productFilter.clear();
        applySecondaryFilters();
    }

    /**
     * Restore column visibility from browser localStorage
     */
    private void restoreColumnVisibility() {
        restoreColumn("customerName", customerNameCheck);
        restoreColumn("customerRating", customerRatingCheck);
        restoreColumn("invoiceNumber", invoiceCheck);
        restoreColumn("transactionDate", dateCheck);
        restoreColumn("productCode", productCodeCheck);
        restoreColumn("productDescription", productCheck);
        restoreColumn("quantity", quantityCheck);
        restoreColumn("amount", amountCheck);
        restoreColumn("unitSellPrice", unitSellPriceCheck);
        restoreColumn("cost", costCheck);
        restoreColumn("unitCostPrice", unitCostPriceCheck);
        restoreColumn("grossProfit", grossProfitCheck);
    }

    private void openCustomerEditDialog(ImportedLineItem item) {
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
}
