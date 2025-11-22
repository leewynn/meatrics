package com.meatrics.pricing.ui.pricingrules;

import com.meatrics.pricing.customer.Customer;
import com.meatrics.pricing.customer.CustomerRepository;
import com.meatrics.pricing.calculation.PricePreview;
import com.meatrics.pricing.rule.PricingRule;
import com.meatrics.pricing.rule.PricingRuleService;
import com.meatrics.pricing.product.ProductCostRepository;
import com.meatrics.pricing.rule.RulePreviewResult;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.binder.ValidationResult;
import com.vaadin.flow.data.binder.ValueContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for creating or editing pricing rules.
 * Includes form fields, validation, and "Test This Rule" preview feature.
 */
public class PricingRuleDialog extends Dialog {

    private static final Logger log = LoggerFactory.getLogger(PricingRuleDialog.class);

    private final PricingRule rule;
    private final PricingRuleService pricingRuleService;
    private final CustomerRepository customerRepository;
    private final ProductCostRepository productCostRepository;
    private final boolean isNew;
    private Consumer<PricingRule> onSaveCallback;

    private final Binder<PricingRule> binder = new Binder<>(PricingRule.class);

    // Form fields
    private RadioButtonGroup<String> ruleTypeRadio;
    private ComboBox<Customer> customerDropdown;
    private TextField ruleNameField;
    private IntegerField executionOrderField; // Now represents Execution Order (simplified)
    private DatePicker validFromPicker;
    private DatePicker validToPicker;
    private ComboBox<String> appliesToComboBox;
    private ComboBox<String> conditionValueComboBox;
    private ComboBox<String> pricingMethodComboBox;
    private NumberField pricingValueField;
    private Checkbox activeCheckbox;

    public PricingRuleDialog(PricingRule rule, PricingRuleService pricingRuleService,
                             CustomerRepository customerRepository,
                             ProductCostRepository productCostRepository, boolean isNew) {
        this.rule = rule;
        this.pricingRuleService = pricingRuleService;
        this.customerRepository = customerRepository;
        this.productCostRepository = productCostRepository;
        this.isNew = isNew;

        setHeaderTitle(isNew ? "New Pricing Rule" : "Edit Pricing Rule");
        setWidth("700px");
        setMaxHeight("90vh");

        createFormLayout();
        configureBinder();
        populateForm();
    }

    /**
     * Create the form layout with all fields
     */
    private void createFormLayout() {
        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        FormLayout formLayout = new FormLayout();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );

        // Rule Type: Standard or Customer-Specific
        ruleTypeRadio = new RadioButtonGroup<>();
        ruleTypeRadio.setLabel("Rule Type");
        ruleTypeRadio.setItems("Standard", "Customer-Specific");
        ruleTypeRadio.setValue("Standard");
        ruleTypeRadio.addValueChangeListener(event -> {
            boolean isCustomer = "Customer-Specific".equals(event.getValue());
            customerDropdown.setEnabled(isCustomer);
            customerDropdown.setRequiredIndicatorVisible(isCustomer);

            // Refresh customer list when switching to customer-specific
            if (isCustomer) {
                refreshCustomerList();
            }
        });
        formLayout.add(ruleTypeRadio, 2);

        // Customer Dropdown (for customer-specific rules)
        customerDropdown = new ComboBox<>("Customer");
        customerDropdown.setPlaceholder("Select customer or type customer code...");
        customerDropdown.setEnabled(false);
        customerDropdown.setWidthFull();

        // Initial load of customers from repository
        refreshCustomerList();

        // Display customer name, but work with customer codes
        customerDropdown.setItemLabelGenerator(customer ->
            customer.getCustomerName() + " (" + customer.getCustomerCode() + ")"
        );

        // Allow custom values (for typing customer codes not yet in database)
        customerDropdown.setAllowCustomValue(true);
        customerDropdown.addCustomValueSetListener(event -> {
            // When user types a custom value, create a temporary Customer object
            Customer customCustomer = new Customer();
            customCustomer.setCustomerCode(event.getDetail());
            customCustomer.setCustomerName(event.getDetail());
            customerDropdown.setValue(customCustomer);
        });

        formLayout.add(customerDropdown, 2);

        // Rule Name
        ruleNameField = new TextField("Rule Name");
        ruleNameField.setPlaceholder("e.g., Standard Beef Pricing");
        ruleNameField.setRequiredIndicatorVisible(true);
        ruleNameField.setWidthFull();
        formLayout.add(ruleNameField, 2);

        // Execution Order (simplified - replaces Category + Layer Order + Priority)
        executionOrderField = new IntegerField("Execution Order");
        executionOrderField.setHelperText("Rules execute in order: 1, 2, 3... Lower numbers apply first.");
        executionOrderField.setValue(1);
        executionOrderField.setMin(1);
        executionOrderField.setMax(9999);
        executionOrderField.setStepButtonsVisible(true);
        executionOrderField.setWidthFull();
        executionOrderField.setRequiredIndicatorVisible(true);
        formLayout.add(executionOrderField, 2);

        // Valid From Date
        validFromPicker = new DatePicker("Valid From");
        validFromPicker.setHelperText("Rule becomes active on this date (leave empty for immediate activation)");
        validFromPicker.setClearButtonVisible(true);
        validFromPicker.setWidthFull();
        formLayout.add(validFromPicker, 1);

        // Valid To Date
        validToPicker = new DatePicker("Valid To");
        validToPicker.setHelperText("Rule expires after this date (leave empty for no expiration)");
        validToPicker.setClearButtonVisible(true);
        validToPicker.setWidthFull();
        formLayout.add(validToPicker, 1);

        // Applies To
        appliesToComboBox = new ComboBox<>("Applies To");
        appliesToComboBox.setItems("ALL_PRODUCTS", "CATEGORY", "PRODUCT_CODE");
        appliesToComboBox.setItemLabelGenerator(item -> {
            switch (item) {
                case "ALL_PRODUCTS": return "All Products";
                case "CATEGORY": return "Category";
                case "PRODUCT_CODE": return "Product Code";
                default: return item;
            }
        });
        appliesToComboBox.setValue("ALL_PRODUCTS");
        appliesToComboBox.setRequiredIndicatorVisible(true);
        appliesToComboBox.setWidthFull();
        appliesToComboBox.addValueChangeListener(event -> {
            String value = event.getValue();
            boolean needsValue = !"ALL_PRODUCTS".equals(value);
            conditionValueComboBox.setEnabled(needsValue);
            conditionValueComboBox.setRequiredIndicatorVisible(needsValue);
            if (!needsValue) {
                conditionValueComboBox.clear();
            } else {
                // Load appropriate values based on selection
                loadConditionValues(value);
            }
        });
        formLayout.add(appliesToComboBox, 1);

        // Condition Value - now a ComboBox
        conditionValueComboBox = new ComboBox<>("Primary Group");
        conditionValueComboBox.setPlaceholder("Select category or product code");
        conditionValueComboBox.setEnabled(false);
        conditionValueComboBox.setWidthFull();
        conditionValueComboBox.setAllowCustomValue(true);
        conditionValueComboBox.addCustomValueSetListener(event -> {
            // Allow typing custom values if needed
            conditionValueComboBox.setValue(event.getDetail());
        });
        conditionValueComboBox.setHelperText("Select from list or type custom value");
        formLayout.add(conditionValueComboBox, 1);

        // Pricing Method
        pricingMethodComboBox = new ComboBox<>("Pricing Method");
        pricingMethodComboBox.setItems("COST_PLUS_PERCENT", "COST_PLUS_FIXED", "FIXED_PRICE", "MAINTAIN_GP_PERCENT", "TARGET_GP_PERCENT");
        pricingMethodComboBox.setItemLabelGenerator(item -> {
            switch (item) {
                case "COST_PLUS_PERCENT": return "Cost Plus Percent";
                case "COST_PLUS_FIXED": return "Cost Plus Fixed";
                case "FIXED_PRICE": return "Fixed Price";
                case "MAINTAIN_GP_PERCENT": return "Maintain GP Percent (Historical)";
                case "TARGET_GP_PERCENT": return "Target GP Percent (Specific)";
                default: return item;
            }
        });
        pricingMethodComboBox.setValue("COST_PLUS_PERCENT");
        pricingMethodComboBox.setRequiredIndicatorVisible(true);
        pricingMethodComboBox.setWidthFull();
        pricingMethodComboBox.addValueChangeListener(event -> {
            updatePricingValueFieldForMethod(event.getValue());
        });
        formLayout.add(pricingMethodComboBox, 1);

        // Pricing Value
        pricingValueField = new NumberField("Pricing Value");
        pricingValueField.setRequiredIndicatorVisible(true);
        pricingValueField.setWidthFull();
        pricingValueField.setStep(0.01);
        pricingValueField.setValue(20.0); // Default 20% markup (displayed as percentage)
        updatePricingValueFieldForMethod("COST_PLUS_PERCENT");
        formLayout.add(pricingValueField, 1);

        // Active Checkbox
        activeCheckbox = new Checkbox("Active");
        activeCheckbox.setValue(true);
        activeCheckbox.setHelperText("Inactive rules are ignored during price calculation");
        formLayout.add(activeCheckbox, 1);

        dialogLayout.add(formLayout);

        // Test This Rule Button
        Button testRuleButton = new Button("Test This Rule", event -> testRule());
        testRuleButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        testRuleButton.setWidthFull();
        dialogLayout.add(testRuleButton);

        // Action buttons
        Button saveButton = new Button("Save", event -> handleSave());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", event -> close());

        HorizontalLayout buttonLayout = new HorizontalLayout(saveButton, cancelButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();

        dialogLayout.add(buttonLayout);
        add(dialogLayout);
    }

    /**
     * Configure binder with validation rules
     */
    private void configureBinder() {
        // Rule Name - required
        binder.forField(ruleNameField)
                .asRequired("Rule name is required")
                .bind(PricingRule::getRuleName, PricingRule::setRuleName);

        // Execution Order - required (replaces Category + Layer Order + Priority)
        binder.forField(executionOrderField)
                .asRequired("Execution order is required")
                .bind(PricingRule::getExecutionOrder, PricingRule::setExecutionOrder);

        // Valid From - optional
        binder.forField(validFromPicker)
                .bind(PricingRule::getValidFrom, PricingRule::setValidFrom);

        // Valid To - optional with validation
        binder.forField(validToPicker)
                .withValidator((LocalDate validTo, ValueContext context) -> {
                    LocalDate validFrom = validFromPicker.getValue();
                    if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
                        return ValidationResult.error("Valid To must be after Valid From");
                    }
                    return ValidationResult.ok();
                })
                .bind(PricingRule::getValidTo, PricingRule::setValidTo);

        // Customer Code - conditionally required
        // Convert between Customer object (in ComboBox) and customerCode String (in PricingRule)
        binder.forField(customerDropdown)
                .withConverter(
                        customer -> customer != null ? customer.getCustomerCode() : null,  // Customer -> String
                        customerCode -> {  // String -> Customer
                            if (customerCode == null || customerCode.isEmpty()) {
                                return null;
                            }
                            // Find customer in the list by code
                            return customerRepository.findByCustomerCode(customerCode)
                                    .orElseGet(() -> {
                                        // If not found, create a temporary Customer object for display
                                        Customer temp = new Customer();
                                        temp.setCustomerCode(customerCode);
                                        temp.setCustomerName(customerCode);
                                        return temp;
                                    });
                        }
                )
                .bind(PricingRule::getCustomerCode, PricingRule::setCustomerCode);

        // Condition Type - required
        binder.forField(appliesToComboBox)
                .asRequired("Applies To is required")
                .bind(PricingRule::getConditionType, PricingRule::setConditionType);

        // Condition Value - conditionally required
        binder.forField(conditionValueComboBox)
                .bind(PricingRule::getConditionValue, PricingRule::setConditionValue);

        // Pricing Method - required
        binder.forField(pricingMethodComboBox)
                .asRequired("Pricing method is required")
                .bind(PricingRule::getPricingMethod, PricingRule::setPricingMethod);

        // Pricing Value - conditionally required (optional for MAINTAIN_GP_PERCENT)
        // Convert between user-friendly percentages (UI) and storage format (Database)
        binder.forField(pricingValueField)
                .withConverter(
                        value -> {
                            // UI -> Database conversion
                            if (value == null) return null;
                            String method = pricingMethodComboBox.getValue();
                            BigDecimal result;
                            if ("COST_PLUS_PERCENT".equals(method)) {
                                // Convert percentage to multiplier: 20 -> 1.20, -20 -> 0.80
                                result = BigDecimal.valueOf(value).divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP)
                                        .add(BigDecimal.ONE);
                            } else if ("MAINTAIN_GP_PERCENT".equals(method) || "TARGET_GP_PERCENT".equals(method)) {
                                // Convert percentage to decimal: 25 -> 0.25, -10 -> -0.10
                                result = BigDecimal.valueOf(value).divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP);
                            } else {
                                // For COST_PLUS_FIXED, FIXED_PRICE - store as-is
                                result = BigDecimal.valueOf(value);
                            }
                            log.debug("UI -> DB conversion: method={}, input={}, output={}", method, value, result);
                            return result;
                        },
                        value -> {
                            // Database -> UI conversion
                            if (value == null) return null;
                            String method = pricingMethodComboBox.getValue();
                            double result;
                            if ("COST_PLUS_PERCENT".equals(method)) {
                                // Convert multiplier to percentage: 1.20 -> 20, 0.80 -> -20
                                result = value.subtract(BigDecimal.ONE).multiply(new BigDecimal("100")).doubleValue();
                            } else if ("MAINTAIN_GP_PERCENT".equals(method) || "TARGET_GP_PERCENT".equals(method)) {
                                // Convert decimal to percentage: 0.25 -> 25, -0.10 -> -10
                                result = value.multiply(new BigDecimal("100")).doubleValue();
                            } else {
                                // For COST_PLUS_FIXED, FIXED_PRICE - display as-is
                                result = value.doubleValue();
                            }
                            log.debug("DB -> UI conversion: method={}, input={}, output={}", method, value, result);
                            return result;
                        },
                        "Must be a valid number"
                )
                .withValidator(bd -> {
                    // For MAINTAIN_GP_PERCENT, value is optional (can be null)
                    // For COST_PLUS_PERCENT, allow negative values (rebates) but validate range
                    // For FIXED_PRICE and COST_PLUS_FIXED, allow negative values (credits, refunds)
                    String method = pricingMethodComboBox.getValue();
                    if ("MAINTAIN_GP_PERCENT".equals(method)) {
                        return bd == null || bd.compareTo(BigDecimal.ZERO) > 0;
                    }
                    if ("COST_PLUS_PERCENT".equals(method)) {
                        // Allow rebates down to -100% (which = 0.0 multiplier)
                        // Prevent multipliers that would make price <= 0
                        return bd != null && bd.compareTo(BigDecimal.ZERO) > 0;
                    }
                    if ("FIXED_PRICE".equals(method) || "COST_PLUS_FIXED".equals(method)) {
                        // Allow negative values for credits/refunds
                        return bd != null;
                    }
                    return bd != null && bd.compareTo(BigDecimal.ZERO) > 0;
                }, "Value must be valid for selected pricing method")
                .bind(PricingRule::getPricingValue, PricingRule::setPricingValue);

        // Active
        binder.forField(activeCheckbox)
                .bind(PricingRule::getIsActive, PricingRule::setIsActive);
    }

    /**
     * Populate form with existing rule data (for edit mode)
     */
    private void populateForm() {
        // Load customers first if this is a customer-specific rule
        if (rule.getCustomerCode() != null && !rule.getCustomerCode().isEmpty()) {
            refreshCustomerList();
            ruleTypeRadio.setValue("Customer-Specific");
            customerDropdown.setEnabled(true);
            customerDropdown.setRequiredIndicatorVisible(true);
        }

        // Load condition values first if needed
        if (rule.getConditionType() != null && !"ALL_PRODUCTS".equals(rule.getConditionType())) {
            loadConditionValues(rule.getConditionType());
            conditionValueComboBox.setEnabled(true);
            conditionValueComboBox.setRequiredIndicatorVisible(true);
        }

        // Debug log before loading
        log.debug("Loading rule - Method: {}, Stored Value: {}",
                  rule.getPricingMethod(), rule.getPricingValue());

        // Now populate all fields from the rule (after dropdowns are loaded)
        binder.readBean(rule);

        // Debug log after loading
        log.debug("After readBean - Method field: {}, Value field: {}",
                  pricingMethodComboBox.getValue(), pricingValueField.getValue());
    }

    /**
     * Update pricing value field label, helper text, and default value based on pricing method
     */
    private void updatePricingValueFieldForMethod(String method) {
        if (method == null) return;

        switch (method) {
            case "COST_PLUS_PERCENT":
                pricingValueField.setLabel("Pricing Value");
                pricingValueField.setHelperText("Markup percentage (e.g., 20 for 20% markup, -10 for 10% discount)");
                pricingValueField.setRequiredIndicatorVisible(true);
                if (pricingValueField.isEmpty()) {
                    pricingValueField.setValue(20.0);
                }
                break;
            case "COST_PLUS_FIXED":
                pricingValueField.setLabel("Pricing Value");
                pricingValueField.setHelperText("Fixed amount to add (e.g., 5.00 = cost + $5.00)");
                pricingValueField.setRequiredIndicatorVisible(true);
                break;
            case "FIXED_PRICE":
                pricingValueField.setLabel("Pricing Value");
                pricingValueField.setHelperText("Fixed sell price (e.g., 25.00 = always $25.00)");
                pricingValueField.setRequiredIndicatorVisible(true);
                break;
            case "MAINTAIN_GP_PERCENT":
                pricingValueField.setLabel("GP% Adjustment");
                pricingValueField.setHelperText("Adjustment to historical GP% (e.g., 0 = maintain exact, +3 = add 3 points, -2 = reduce 2 points). Supports positive and negative values.");
                pricingValueField.setRequiredIndicatorVisible(false);
                if (pricingValueField.isEmpty()) {
                    pricingValueField.setValue(0.0); // Default to 0 (no adjustment)
                }
                break;
            case "TARGET_GP_PERCENT":
                pricingValueField.setLabel("Pricing Value");
                pricingValueField.setHelperText("Target GP% (e.g., 25 for 25% GP, 30 for 30% GP)");
                pricingValueField.setRequiredIndicatorVisible(true);
                break;
        }
    }

    /**
     * Test the rule by showing a preview of matching products and calculated prices
     */
    private void testRule() {
        try {
            // Create a temporary rule from form values
            PricingRule testRule = new PricingRule();
            binder.writeBean(testRule);

            // Call preview service
            RulePreviewResult result = pricingRuleService.previewRule(testRule);

            // Show preview dialog
            showPreviewDialog(result, testRule);

        } catch (ValidationException e) {
            showErrorNotification("Please fix validation errors before testing the rule");
        } catch (Exception e) {
            log.error("Error previewing rule", e);
            showErrorNotification("Error testing rule: " + e.getMessage());
        }
    }

    /**
     * Show preview dialog with rule test results
     */
    private void showPreviewDialog(RulePreviewResult result, PricingRule testRule) {
        Dialog previewDialog = new Dialog();
        previewDialog.setHeaderTitle("Rule Preview");
        previewDialog.setWidth("900px");
        previewDialog.setMaxHeight("80vh");

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);

        // Show rule description
        Span ruleDesc = new Span(testRule.getFormattedDescription());
        ruleDesc.getStyle().set("font-weight", "bold");
        content.add(ruleDesc);

        // Show scope indicator - customer-specific vs standard
        Div scopeIndicator = new Div();
        scopeIndicator.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-top", "var(--lumo-space-s)")
            .set("margin-bottom", "var(--lumo-space-m)");

        if (testRule.getCustomerCode() != null && !testRule.getCustomerCode().isEmpty()) {
            // Customer-specific rule
            Span scopeLabel = new Span("Scope: ");
            scopeLabel.getStyle().set("font-weight", "600");

            Span scopeText = new Span("Customer-Specific");
            scopeText.getStyle()
                .set("color", "var(--lumo-primary-text-color)")
                .set("font-weight", "600");

            Div scopeLine = new Div(scopeLabel, scopeText);

            Span customerInfo = new Span("This rule will ONLY apply to customer: " + testRule.getCustomerCode());
            customerInfo.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("display", "block")
                .set("margin-top", "var(--lumo-space-xs)");

            scopeIndicator.add(scopeLine, customerInfo);
        } else {
            // Standard rule - applies to all customers
            Span scopeLabel = new Span("Scope: ");
            scopeLabel.getStyle().set("font-weight", "600");

            Span scopeText = new Span("Standard (All Customers)");
            scopeText.getStyle()
                .set("color", "var(--lumo-success-text-color)")
                .set("font-weight", "600");

            Div scopeLine = new Div(scopeLabel, scopeText);

            Span customerInfo = new Span("This rule will apply to ALL customers who purchase the matching products");
            customerInfo.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("display", "block")
                .set("margin-top", "var(--lumo-space-xs)");

            scopeIndicator.add(scopeLine, customerInfo);
        }
        content.add(scopeIndicator);

        if (result.isAllProducts()) {
            // For ALL_PRODUCTS rules, just show count
            String productMessage = result.getTotalMatchCount() + " products in system";
            if (testRule.getCustomerCode() != null && !testRule.getCustomerCode().isEmpty()) {
                productMessage = "All products when purchased by customer " + testRule.getCustomerCode() +
                    " (" + result.getTotalMatchCount() + " products in system)";
            }
            Span countMessage = new Span(productMessage);
            countMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");
            content.add(countMessage);
        } else {
            // Show grid with matching products and calculated prices
            String matchMessage = result.getTotalMatchCount() + " matching product(s)";
            if (testRule.getCustomerCode() != null && !testRule.getCustomerCode().isEmpty()) {
                matchMessage += " (when purchased by customer " + testRule.getCustomerCode() + ")";
            }
            H3 matchHeader = new H3(matchMessage);
            content.add(matchHeader);

            if (result.getTotalMatchCount() == 0) {
                // Warning: No products match
                Div warningBox = new Div();
                warningBox.getStyle()
                    .set("background-color", "var(--lumo-error-color-10pct)")
                    .set("border-left", "4px solid var(--lumo-error-color)")
                    .set("border-radius", "var(--lumo-border-radius-m)")
                    .set("padding", "var(--lumo-space-m)")
                    .set("margin-top", "var(--lumo-space-m)");

                Span warningIcon = new Span("⚠️ ");
                warningIcon.getStyle().set("font-size", "1.2rem");

                Span warningText = new Span("No products match this rule. This rule will have no effect.");
                warningText.getStyle().set("font-weight", "600");

                Div warningLine = new Div(warningIcon, warningText);

                Span suggestion = new Span("Check your category or product code selection.");
                suggestion.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("display", "block")
                    .set("margin-top", "var(--lumo-space-xs)");

                warningBox.add(warningLine, suggestion);
                content.add(warningBox);
            } else if (result.getTotalMatchCount() > 0) {
                Grid<PricePreview> previewGrid = new Grid<>(PricePreview.class, false);
                previewGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
                previewGrid.setHeight("400px");

                previewGrid.addColumn(PricePreview::getProductCode)
                        .setHeader("Product Code")
                        .setAutoWidth(true);

                previewGrid.addColumn(PricePreview::getProductName)
                        .setHeader("Product Name")
                        .setAutoWidth(true)
                        .setFlexGrow(1);

                previewGrid.addColumn(preview -> String.format("$%.2f", preview.getCost()))
                        .setHeader("Cost")
                        .setAutoWidth(true);

                previewGrid.addColumn(preview -> String.format("$%.2f", preview.getCalculatedPrice()))
                        .setHeader("Calculated Price")
                        .setAutoWidth(true);

                // Add GP% column
                previewGrid.addColumn(preview -> {
                    java.math.BigDecimal price = preview.getCalculatedPrice();
                    java.math.BigDecimal cost = preview.getCost();

                    if (cost.compareTo(java.math.BigDecimal.ZERO) == 0) {
                        return "N/A";
                    }

                    if (price.compareTo(java.math.BigDecimal.ZERO) == 0) {
                        return "0.0%";
                    }

                    // GP% = (Price - Cost) / Price * 100
                    java.math.BigDecimal gpAmount = price.subtract(cost);
                    java.math.BigDecimal gpPercent = gpAmount.divide(price, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new java.math.BigDecimal("100"));

                    return String.format("%.1f%%", gpPercent);
                })
                .setHeader("GP%")
                .setAutoWidth(true);

                // Add GP$ column
                previewGrid.addColumn(preview -> {
                    java.math.BigDecimal price = preview.getCalculatedPrice();
                    java.math.BigDecimal cost = preview.getCost();
                    java.math.BigDecimal gpAmount = price.subtract(cost);
                    return String.format("$%.2f", gpAmount);
                })
                .setHeader("GP$")
                .setAutoWidth(true);

                previewGrid.setItems(result.getPreviews());
                content.add(previewGrid);
            }
        }

        // Buttons
        Button saveFromPreviewButton = new Button("Save Rule", event -> {
            previewDialog.close();
            handleSave();
        });
        saveFromPreviewButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button closeButton = new Button("Close", event -> previewDialog.close());

        HorizontalLayout buttonLayout = new HorizontalLayout(saveFromPreviewButton, closeButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();

        content.add(buttonLayout);
        previewDialog.add(content);
        previewDialog.open();
    }

    /**
     * Handle save action
     */
    private void handleSave() {
        try {
            // Validate all fields
            binder.writeBean(rule);

            // Additional validation for customer-specific rules
            if ("Customer-Specific".equals(ruleTypeRadio.getValue())) {
                if (rule.getCustomerCode() == null || rule.getCustomerCode().trim().isEmpty()) {
                    showErrorNotification("Customer code is required for customer-specific rules");
                    return;
                }
            }

            // Additional validation for condition value
            if (!"ALL_PRODUCTS".equals(rule.getConditionType())) {
                if (rule.getConditionValue() == null || rule.getConditionValue().trim().isEmpty()) {
                    showErrorNotification("Condition value is required for " + rule.getConditionType() + " rules");
                    return;
                }
            }

            // Check for duplicate execution order
            if (hasDuplicateExecutionOrder(rule)) {
                showErrorNotification("A rule with execution order " + rule.getExecutionOrder() +
                    " already exists for " + (rule.getCustomerCode() != null ? "customer " + rule.getCustomerCode() : "standard rules") +
                    ". Please use a different execution order.");
                return;
            }

            // Save the rule
            PricingRule savedRule = pricingRuleService.saveRule(rule);

            if (onSaveCallback != null) {
                onSaveCallback.accept(savedRule);
            }

            close();
            log.info("Saved pricing rule: id={}, name={}", savedRule.getId(), savedRule.getRuleName());

        } catch (ValidationException e) {
            showErrorNotification("Please fix validation errors");
        } catch (Exception e) {
            log.error("Error saving pricing rule", e);
            showErrorNotification("Error saving rule: " + e.getMessage());
        }
    }

    /**
     * Set callback to be invoked after successful save
     */
    public void open(Consumer<PricingRule> callback) {
        this.onSaveCallback = callback;
        open();
    }

    /**
     * Check if another rule with the same execution order already exists
     * for the same scope (same customer or both standard rules)
     */
    private boolean hasDuplicateExecutionOrder(PricingRule ruleToCheck) {
        if (ruleToCheck.getExecutionOrder() == null) {
            return false;
        }

        List<PricingRule> allRules = pricingRuleService.getAllRules();

        for (PricingRule existingRule : allRules) {
            // Skip if it's the same rule (when editing)
            if (existingRule.getId() != null && existingRule.getId().equals(ruleToCheck.getId())) {
                continue;
            }

            // Check if execution order matches
            if (!existingRule.getExecutionOrder().equals(ruleToCheck.getExecutionOrder())) {
                continue;
            }

            // Check if they're in the same scope (both standard, or same customer)
            boolean bothStandard = (existingRule.getCustomerCode() == null && ruleToCheck.getCustomerCode() == null);
            boolean sameCustomer = (existingRule.getCustomerCode() != null &&
                                  existingRule.getCustomerCode().equals(ruleToCheck.getCustomerCode()));

            if (bothStandard || sameCustomer) {
                return true; // Duplicate found
            }
        }

        return false;
    }

    /**
     * Refresh the customer list from the database
     */
    private void refreshCustomerList() {
        try {
            List<Customer> customers = customerRepository.findAll();
            customerDropdown.setItems(customers);
            log.debug("Loaded {} customers for dropdown", customers.size());
        } catch (Exception e) {
            log.error("Error loading customers", e);
            showErrorNotification("Error loading customers: " + e.getMessage());
        }
    }

    /**
     * Load condition values based on the selected condition type
     */
    private void loadConditionValues(String conditionType) {
        try {
            if ("CATEGORY".equals(conditionType)) {
                // Load distinct primary_group values from product_costs
                List<String> categories = productCostRepository.findDistinctPrimaryGroups();
                conditionValueComboBox.setItems(categories);
                log.debug("Loaded {} categories for dropdown", categories.size());
            } else if ("PRODUCT_CODE".equals(conditionType)) {
                // Load distinct product codes from product_costs
                List<String> productCodes = productCostRepository.findDistinctProductCodes();
                conditionValueComboBox.setItems(productCodes);
                log.debug("Loaded {} product codes for dropdown", productCodes.size());
            } else {
                // Clear items for other types
                conditionValueComboBox.setItems();
            }
        } catch (Exception e) {
            log.error("Error loading condition values for type: " + conditionType, e);
            showErrorNotification("Error loading values: " + e.getMessage());
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
