package com.meatrics.pricing.ui;

import com.meatrics.pricing.ImportedLineItem;
import com.meatrics.pricing.PricingImportService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.LocalDate;
import java.util.List;

/**
 * Pricing data view showing imported line items
 */
@Route("")
@PageTitle("Pricing Data")
@Menu(order = 0, icon = "vaadin:chart-line", title = "Pricing Data")
public class PricingView extends Main {

    private final PricingImportService pricingImportService;
    private final Grid<ImportedLineItem> dataGrid;
    private final DatePicker startDatePicker;
    private final DatePicker endDatePicker;

    public PricingView(PricingImportService pricingImportService) {
        this.pricingImportService = pricingImportService;
        addClassName("pricing-view");

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

        endDatePicker = new DatePicker("End Date");
        endDatePicker.setPlaceholder("Select end date");
        endDatePicker.setClearButtonVisible(true);

        Button applyFilterButton = new Button("Apply Filter", new Icon(VaadinIcon.FILTER));
        applyFilterButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        applyFilterButton.addClickListener(event -> applyFilter());

        Button clearFilterButton = new Button("Clear Filter", new Icon(VaadinIcon.CLOSE_SMALL));
        clearFilterButton.addClickListener(event -> clearFilter());

        HorizontalLayout filterLayout = new HorizontalLayout(startDatePicker, endDatePicker, applyFilterButton, clearFilterButton);
        filterLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        filterLayout.setSpacing(true);

        // Create data grid
        dataGrid = createDataGrid();

        // Create column visibility toggles (collapsible)
        Details columnToggles = createColumnVisibilityToggles();

        // Add components to main layout
        mainLayout.add(title, filterLayout, columnToggles, dataGrid);

        add(mainLayout);
        refreshGrid();
    }

    private Grid<ImportedLineItem> createDataGrid() {
        Grid<ImportedLineItem> grid = new Grid<>(ImportedLineItem.class, false);
        grid.setSizeFull();
        grid.setMinHeight("600px");

        // Enable column reordering
        grid.setColumnReorderingAllowed(true);

        // Add columns with keys for visibility toggling
        grid.addColumn(ImportedLineItem::getCustomerName)
                .setHeader("Customer Name")
                .setKey("customerName")
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

        grid.addColumn(ImportedLineItem::getAmount)
                .setHeader("Amount")
                .setKey("amount")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getCost)
                .setHeader("Cost")
                .setKey("cost")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getGrossProfitFormatted)
                .setHeader("Gross Profit")
                .setKey("grossProfit")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(ImportedLineItem::getOutstandingAmount)
                .setHeader("Outstanding")
                .setKey("outstanding")
                .setAutoWidth(true)
                .setResizable(true)
                .setSortable(true);

        return grid;
    }

    private Details createColumnVisibilityToggles() {
        HorizontalLayout checkboxLayout = new HorizontalLayout();
        checkboxLayout.setSpacing(true);

        // Create checkboxes for each column
        Checkbox customerNameCheck = new Checkbox("Customer Name", true);
        customerNameCheck.addValueChangeListener(e ->
            dataGrid.getColumnByKey("customerName").setVisible(e.getValue()));

        Checkbox invoiceCheck = new Checkbox("Invoice #", true);
        invoiceCheck.addValueChangeListener(e ->
            dataGrid.getColumnByKey("invoiceNumber").setVisible(e.getValue()));

        Checkbox dateCheck = new Checkbox("Date", true);
        dateCheck.addValueChangeListener(e ->
            dataGrid.getColumnByKey("transactionDate").setVisible(e.getValue()));

        Checkbox productCodeCheck = new Checkbox("Product Code", true);
        productCodeCheck.addValueChangeListener(e ->
            dataGrid.getColumnByKey("productCode").setVisible(e.getValue()));

        Checkbox productCheck = new Checkbox("Product", true);
        productCheck.addValueChangeListener(e ->
            dataGrid.getColumnByKey("productDescription").setVisible(e.getValue()));

        Checkbox quantityCheck = new Checkbox("Quantity", true);
        quantityCheck.addValueChangeListener(e ->
            dataGrid.getColumnByKey("quantity").setVisible(e.getValue()));

        Checkbox amountCheck = new Checkbox("Amount", true);
        amountCheck.addValueChangeListener(e ->
            dataGrid.getColumnByKey("amount").setVisible(e.getValue()));

        Checkbox costCheck = new Checkbox("Cost", true);
        costCheck.addValueChangeListener(e ->
            dataGrid.getColumnByKey("cost").setVisible(e.getValue()));

        Checkbox grossProfitCheck = new Checkbox("Gross Profit", true);
        grossProfitCheck.addValueChangeListener(e ->
            dataGrid.getColumnByKey("grossProfit").setVisible(e.getValue()));

        Checkbox outstandingCheck = new Checkbox("Outstanding", true);
        outstandingCheck.addValueChangeListener(e ->
            dataGrid.getColumnByKey("outstanding").setVisible(e.getValue()));

        checkboxLayout.add(
            customerNameCheck, invoiceCheck, dateCheck,
            productCodeCheck, productCheck, quantityCheck,
            amountCheck, costCheck, grossProfitCheck, outstandingCheck
        );

        // Wrap in Details component (collapsible)
        Details details = new Details("Column Visibility", checkboxLayout);
        details.setOpened(false); // Closed by default

        return details;
    }

    private void refreshGrid() {
        if (dataGrid != null) {
            List<ImportedLineItem> allItems = pricingImportService.getAllLineItems();
            dataGrid.setItems(allItems);
        }
    }

    private void applyFilter() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (dataGrid != null) {
            List<ImportedLineItem> filteredItems;

            if (startDate != null || endDate != null) {
                filteredItems = pricingImportService.getLineItemsByDateRange(startDate, endDate);
            } else {
                filteredItems = pricingImportService.getAllLineItems();
            }

            dataGrid.setItems(filteredItems);
        }
    }

    private void clearFilter() {
        startDatePicker.clear();
        endDatePicker.clear();
        refreshGrid();
    }
}
