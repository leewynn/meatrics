package com.meatrics.pricing.ui.component;

import com.meatrics.pricing.*;
import com.meatrics.pricing.ProductCostRepository;
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
import org.springframework.beans.factory.annotation.Autowired;

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
    private ComboBox<RuleCategory> categoryComboBox;
    private IntegerField layerOrderField;
    private DatePicker validFromPicker;
    private DatePicker validToPicker;
    private ComboBox<String> appliesToComboBox;
    private ComboBox<String> conditionValueComboBox;
    private ComboBox<String> pricingMethodComboBox;
    private NumberField pricingValueField;
    private IntegerField priorityField;
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

        // Rule Category
        categoryComboBox = new ComboBox<>("Rule Category");
        categoryComboBox.setItems(RuleCategory.values());
        categoryComboBox.setItemLabelGenerator(RuleCategory::getDisplayName);
        categoryComboBox.setHelperText("Layer in which this rule applies (Base → Customer → Product → Promotional)");
        categoryComboBox.setRequiredIndicatorVisible(true);
        categoryComboBox.setWidthFull();
        categoryComboBox.addValueChangeListener(event -> {
            if (event.getValue() != null && layerOrderField.isEmpty()) {
                // Set default layer order based on category
                layerOrderField.setValue(event.getValue().getOrder() * 100);
            }
        });
        formLayout.add(categoryComboBox, 1);

        // Layer Order
        layerOrderField = new IntegerField("Layer Order");
        layerOrderField.setHelperText("Order within category (lower numbers apply first)");
        layerOrderField.setValue(1);
        layerOrderField.setMin(1);
        layerOrderField.setMax(999999);
        layerOrderField.setStepButtonsVisible(true);
        layerOrderField.setWidthFull();
        formLayout.add(layerOrderField, 1);

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
        pricingMethodComboBox.setItems("COST_PLUS_PERCENT", "COST_PLUS_FIXED", "FIXED_PRICE", "MAINTAIN_GP_PERCENT");
        pricingMethodComboBox.setItemLabelGenerator(item -> {
            switch (item) {
                case "COST_PLUS_PERCENT": return "Cost Plus Percent";
                case "COST_PLUS_FIXED": return "Cost Plus Fixed";
                case "FIXED_PRICE": return "Fixed Price";
                case "MAINTAIN_GP_PERCENT": return "Maintain GP Percent";
                default: return item;
            }
        });
        pricingMethodComboBox.setValue("COST_PLUS_PERCENT");
        pricingMethodComboBox.setRequiredIndicatorVisible(true);
        pricingMethodComboBox.setWidthFull();
        pricingMethodComboBox.addValueChangeListener(event -> {
            updatePricingValueHelperText(event.getValue());
            // For MAINTAIN_GP_PERCENT, pricing value is optional (fallback default)
            boolean isRequired = !"MAINTAIN_GP_PERCENT".equals(event.getValue());
            pricingValueField.setRequiredIndicatorVisible(isRequired);
        });
        formLayout.add(pricingMethodComboBox, 1);

        // Pricing Value
        pricingValueField = new NumberField("Pricing Value");
        pricingValueField.setRequiredIndicatorVisible(true);
        pricingValueField.setWidthFull();
        pricingValueField.setStep(0.01);
        pricingValueField.setValue(20.0); // Default 20% markup (displayed as percentage)
        updatePricingValueHelperText("COST_PLUS_PERCENT");
        formLayout.add(pricingValueField, 1);

        // Priority
        priorityField = new IntegerField("Priority");
        priorityField.setRequiredIndicatorVisible(true);
        priorityField.setWidthFull();
        priorityField.setValue(5000);
        priorityField.setHelperText("Lower number = higher priority. Ranges: 1-999 (specific), 1000-4999 (category), 5000+ (default)");
        priorityField.setMin(1);
        priorityField.setMax(99999);
        formLayout.add(priorityField, 1);

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

        // Rule Category - required
        binder.forField(categoryComboBox)
                .asRequired("Rule category is required")
                .bind(PricingRule::getRuleCategory, PricingRule::setRuleCategory);

        // Layer Order - required
        binder.forField(layerOrderField)
                .asRequired("Layer order is required")
                .bind(PricingRule::getLayerOrder, PricingRule::setLayerOrder);

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
        // For COST_PLUS_PERCENT, convert between percentage (UI) and multiplier (storage)
        binder.forField(pricingValueField)
                .withConverter(
                        value -> {
                            // UI -> Database conversion
                            if (value == null) return null;
                            String method = pricingMethodComboBox.getValue();
                            if ("COST_PLUS_PERCENT".equals(method)) {
                                // Convert percentage to multiplier: 20 -> 1.20, -20 -> 0.80
                                return BigDecimal.valueOf(1 + value / 100.0);
                            } else {
                                // For other methods, store as-is
                                return BigDecimal.valueOf(value);
                            }
                        },
                        value -> {
                            // Database -> UI conversion
                            if (value == null) return null;
                            String method = pricingMethodComboBox.getValue();
                            if ("COST_PLUS_PERCENT".equals(method)) {
                                // Convert multiplier to percentage: 1.20 -> 20, 0.80 -> -20
                                return (value.doubleValue() - 1.0) * 100.0;
                            } else {
                                // For other methods, display as-is
                                return value.doubleValue();
                            }
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

        // Priority - required
        binder.forField(priorityField)
                .asRequired("Priority is required")
                .bind(PricingRule::getPriority, PricingRule::setPriority);

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

        // Now populate all fields from the rule (after dropdowns are loaded)
        binder.readBean(rule);
    }

    /**
     * Update helper text for pricing value field based on selected method
     */
    private void updatePricingValueHelperText(String method) {
        if (method == null) return;

        switch (method) {
            case "COST_PLUS_PERCENT":
                pricingValueField.setHelperText("Percentage (e.g., 20 for 20% markup, -20 for 20% rebate/discount)");
                break;
            case "COST_PLUS_FIXED":
                pricingValueField.setHelperText("Fixed amount to add (e.g., 2.50 for $2.50)");
                break;
            case "FIXED_PRICE":
                pricingValueField.setHelperText("Fixed price (e.g., 28.50 for $28.50)");
                break;
            case "MAINTAIN_GP_PERCENT":
                pricingValueField.setHelperText("Optional: Default GP% fallback as decimal (e.g., 0.25 for 25% GP). Leave empty to use historical GP% only.");
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

        if (result.isAllProducts()) {
            // For ALL_PRODUCTS rules, just show count
            Span countMessage = new Span(
                    "This rule will apply to ALL products (" + result.getTotalMatchCount() + " products in system)");
            countMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");
            content.add(countMessage);
        } else {
            // Show grid with matching products and calculated prices
            H3 matchHeader = new H3(result.getTotalMatchCount() + " matching products");
            content.add(matchHeader);

            if (result.getTotalMatchCount() > 0) {
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
