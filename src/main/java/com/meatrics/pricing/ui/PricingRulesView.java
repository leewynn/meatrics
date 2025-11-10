package com.meatrics.pricing.ui;

import com.meatrics.base.ui.MainLayout;
import com.meatrics.pricing.CustomerRepository;
import com.meatrics.pricing.PricingRule;
import com.meatrics.pricing.PricingRuleService;
import com.meatrics.pricing.ProductCostRepository;
import com.meatrics.pricing.RuleCategory;
import com.meatrics.pricing.ui.component.PricingRuleDialog;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
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
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * View for managing pricing rules.
 * Provides CRUD operations and rule preview/testing functionality.
 */
@Route(value = "pricing-rules", layout = MainLayout.class)
@PageTitle("Pricing Rules")
@Menu(order = 2, icon = "vaadin:calc-book", title = "Pricing Rules")
public class PricingRulesView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(PricingRulesView.class);

    private final PricingRuleService pricingRuleService;
    private final CustomerRepository customerRepository;
    private final ProductCostRepository productCostRepository;
    private final Grid<PricingRule> rulesGrid;
    private List<PricingRule> allRules;

    public PricingRulesView(PricingRuleService pricingRuleService,
                            CustomerRepository customerRepository,
                            ProductCostRepository productCostRepository) {
        this.pricingRuleService = pricingRuleService;
        this.customerRepository = customerRepository;
        this.productCostRepository = productCostRepository;
        addClassName("pricing-rules-view");

        setSizeFull();
        setSpacing(true);
        setPadding(true);

        // Title
        H2 title = new H2("Pricing Rules");

        // Grid - initialize before statusFilter so it can be referenced
        rulesGrid = createRulesGrid();

        // Status filter (left side)
        ComboBox<String> statusFilter = new ComboBox<>("Filter by Status");
        statusFilter.setItems("All", "Active", "Future", "Expired", "Inactive");
        statusFilter.setValue("All");
        statusFilter.addValueChangeListener(event -> {
            String status = event.getValue();
            List<PricingRule> filtered = filterRulesByStatus(status);
            rulesGrid.setItems(filtered);
        });

        // Buttons (right side)
        Button newRuleButton = new Button("New Rule", new Icon(VaadinIcon.PLUS));
        newRuleButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newRuleButton.addClickListener(event -> openRuleDialog(null));

        Button refreshButton = new Button("Refresh", new Icon(VaadinIcon.REFRESH));
        refreshButton.addClickListener(event -> refreshGrid());

        Button helpButton = new Button("Help", new Icon(VaadinIcon.QUESTION_CIRCLE));
        helpButton.addClickListener(event -> showHelpDialog());

        HorizontalLayout buttonGroup = new HorizontalLayout(newRuleButton, refreshButton, helpButton);
        buttonGroup.setSpacing(true);

        // Combined toolbar with filter on left, buttons on right
        HorizontalLayout toolbar = new HorizontalLayout(statusFilter, buttonGroup);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);

        // Add components
        add(title, toolbar, rulesGrid);

        // Load initial data
        refreshGrid();
    }

    /**
     * Create the rules grid with all columns
     */
    private Grid<PricingRule> createRulesGrid() {
        Grid<PricingRule> grid = new Grid<>(PricingRule.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setSizeFull();
        grid.setMinHeight("600px");

        // Rule Type column (Standard/Customer-Specific)
        grid.addColumn(rule -> rule.isStandardRule() ? "Standard" : "Customer")
                .setHeader("Type")
                .setAutoWidth(true)
                .setSortable(true)
                .setKey("type");

        // Rule Name
        grid.addColumn(PricingRule::getRuleName)
                .setHeader("Rule Name")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true)
                .setKey("ruleName");

        // Applies To
        grid.addColumn(rule -> {
                    String applies = rule.getConditionType();
                    if ("ALL_PRODUCTS".equals(applies)) {
                        return "All Products";
                    } else if ("CATEGORY".equals(applies)) {
                        return "Category: " + rule.getConditionValue();
                    } else if ("PRODUCT_CODE".equals(applies)) {
                        return "Product: " + rule.getConditionValue();
                    }
                    return applies;
                })
                .setHeader("Applies To")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true)
                .setKey("appliesTo");

        // Customer Code (for customer-specific rules)
        grid.addColumn(rule -> rule.getCustomerCode() != null ? rule.getCustomerCode() : "-")
                .setHeader("Customer")
                .setAutoWidth(true)
                .setSortable(true)
                .setKey("customerCode");

        // Pricing Method
        grid.addColumn(rule -> formatPricingMethod(rule.getPricingMethod(), rule.getPricingValue()))
                .setHeader("Pricing Method")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true)
                .setKey("pricingMethod");

        // Priority
        grid.addColumn(PricingRule::getPriority)
                .setHeader("Priority")
                .setAutoWidth(true)
                .setSortable(true)
                .setKey("priority");

        // Category Column
        grid.addColumn(rule -> {
                    RuleCategory category = rule.getRuleCategory();
                    return category != null ? category.getDisplayName() : "Base Price";
                })
                .setHeader("Category")
                .setKey("category")
                .setAutoWidth(true)
                .setSortable(true);

        // Layer Order Column
        grid.addColumn(PricingRule::getLayerOrder)
                .setHeader("Layer Order")
                .setKey("layerOrder")
                .setAutoWidth(true)
                .setSortable(true);

        // Status Column with Badge
        grid.addComponentColumn(rule -> {
                    Span statusBadge = new Span();

                    if (rule.isCurrentlyActive()) {
                        statusBadge.setText("Active");
                        statusBadge.getElement().getThemeList().add("badge");
                        statusBadge.getElement().getThemeList().add("success");
                    } else {
                        LocalDate now = LocalDate.now();
                        if (rule.getValidFrom() != null && now.isBefore(rule.getValidFrom())) {
                            statusBadge.setText("Future");
                            statusBadge.getElement().getThemeList().add("badge");
                            statusBadge.getElement().getThemeList().add("contrast");
                        } else if (rule.getValidTo() != null && now.isAfter(rule.getValidTo())) {
                            statusBadge.setText("Expired");
                            statusBadge.getElement().getThemeList().add("badge");
                            statusBadge.getElement().getThemeList().add("error");
                        } else if (!Boolean.TRUE.equals(rule.getIsActive())) {
                            statusBadge.setText("Inactive");
                            statusBadge.getElement().getThemeList().add("badge");
                        }
                    }

                    return statusBadge;
                })
                .setHeader("Status")
                .setKey("status")
                .setAutoWidth(true);

        // Valid From Column
        grid.addColumn(rule -> {
                    LocalDate validFrom = rule.getValidFrom();
                    return validFrom != null ? validFrom.toString() : "Always";
                })
                .setHeader("Valid From")
                .setKey("validFrom")
                .setAutoWidth(true)
                .setSortable(true);

        // Valid To Column
        grid.addColumn(rule -> {
                    LocalDate validTo = rule.getValidTo();
                    return validTo != null ? validTo.toString() : "No Expiration";
                })
                .setHeader("Valid To")
                .setKey("validTo")
                .setAutoWidth(true)
                .setSortable(true);

        // Actions column
        grid.addComponentColumn(rule -> {
                    Button editButton = new Button("Edit", new Icon(VaadinIcon.EDIT));
                    editButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
                    editButton.addClickListener(event -> openRuleDialog(rule));

                    Button deleteButton = new Button("Delete", new Icon(VaadinIcon.TRASH));
                    deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                    deleteButton.addClickListener(event -> confirmDelete(rule));

                    HorizontalLayout actions = new HorizontalLayout(editButton, deleteButton);
                    actions.setSpacing(true);
                    return actions;
                })
                .setHeader("Actions")
                .setAutoWidth(true)
                .setFlexGrow(0);

        return grid;
    }

    /**
     * Format pricing method for display
     */
    private String formatPricingMethod(String method, java.math.BigDecimal value) {
        switch (method) {
            case "COST_PLUS_PERCENT":
                // Convert multiplier to percentage for display: 1.20 -> +20%, 0.80 -> -20%
                java.math.BigDecimal percentage = value.subtract(java.math.BigDecimal.ONE)
                        .multiply(new java.math.BigDecimal("100"));
                String sign = percentage.compareTo(java.math.BigDecimal.ZERO) >= 0 ? "+" : "";
                return String.format("%s%.1f%% markup", sign, percentage);
            case "COST_PLUS_FIXED":
                return String.format("Cost + $%.2f", value);
            case "FIXED_PRICE":
                return String.format("Fixed $%.2f", value);
            case "MAINTAIN_GP_PERCENT":
                java.math.BigDecimal gpPercent = value.multiply(new java.math.BigDecimal("100"));
                return String.format("Maintain GP%% (default %.0f%%)", gpPercent);
            default:
                return method;
        }
    }

    /**
     * Open rule dialog for create or edit
     */
    private void openRuleDialog(PricingRule rule) {
        boolean isNew = (rule == null);
        PricingRule ruleToEdit = isNew ? new PricingRule() : rule;

        PricingRuleDialog dialog = new PricingRuleDialog(ruleToEdit, pricingRuleService,
                customerRepository, productCostRepository, isNew);
        dialog.open(savedRule -> {
            refreshGrid();
            String message = isNew ? "Rule created successfully" : "Rule updated successfully";
            showSuccessNotification(message);
        });
    }

    /**
     * Confirm deletion of a rule
     */
    private void confirmDelete(PricingRule rule) {
        ConfirmDialog confirmDialog = new ConfirmDialog();
        confirmDialog.setHeader("Delete Rule");
        confirmDialog.setText("Are you sure you want to delete the rule '" + rule.getRuleName() + "'?");

        confirmDialog.setCancelable(true);
        confirmDialog.setCancelText("Cancel");

        confirmDialog.setConfirmText("Delete");
        confirmDialog.setConfirmButtonTheme("error primary");
        confirmDialog.addConfirmListener(event -> {
            try {
                pricingRuleService.deleteRule(rule.getId());
                refreshGrid();
                showSuccessNotification("Rule deleted successfully");
                log.info("Deleted pricing rule: id={}, name={}", rule.getId(), rule.getRuleName());
            } catch (IllegalStateException e) {
                showErrorNotification(e.getMessage());
            } catch (Exception e) {
                log.error("Error deleting pricing rule", e);
                showErrorNotification("Error deleting rule: " + e.getMessage());
            }
        });

        confirmDialog.open();
    }

    /**
     * Refresh the grid with latest data
     */
    private void refreshGrid() {
        try {
            allRules = pricingRuleService.getAllRules();
            rulesGrid.setItems(allRules);
            log.debug("Loaded {} pricing rules", allRules.size());
        } catch (Exception e) {
            log.error("Error loading pricing rules", e);
            showErrorNotification("Error loading rules: " + e.getMessage());
        }
    }

    /**
     * Filter rules by status
     */
    private List<PricingRule> filterRulesByStatus(String status) {
        if (allRules == null) {
            return List.of();
        }

        return switch (status) {
            case "Active" -> allRules.stream().filter(PricingRule::isCurrentlyActive).collect(Collectors.toList());
            case "Future" -> allRules.stream().filter(r -> r.getValidFrom() != null &&
                    LocalDate.now().isBefore(r.getValidFrom())).collect(Collectors.toList());
            case "Expired" -> allRules.stream().filter(r -> r.getValidTo() != null &&
                    LocalDate.now().isAfter(r.getValidTo())).collect(Collectors.toList());
            case "Inactive" -> allRules.stream().filter(r -> !Boolean.TRUE.equals(r.getIsActive())).collect(Collectors.toList());
            default -> allRules;
        };
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

    /**
     * Show help dialog explaining rule categories and how they interact
     */
    private void showHelpDialog() {
        com.vaadin.flow.component.dialog.Dialog helpDialog = new com.vaadin.flow.component.dialog.Dialog();
        helpDialog.setHeaderTitle("Pricing Rules Help");
        helpDialog.setWidth("700px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        // Introduction
        Span intro = new Span("The pricing engine applies rules in a layered approach. Rules are organized into categories, and each category is applied in sequence.");
        intro.getStyle().set("margin-bottom", "16px");
        content.add(intro);

        // Category 1: BASE_PRICE
        VerticalLayout basePriceSection = createCategorySection(
            "1. Base Price",
            "#2196F3",
            "💵",
            "Establishes the initial selling price from cost.",
            "Only ONE rule applies (first matching rule)",
            "• Maintain GP% from last cycle\n• Cost + 20% markup\n• Category-specific pricing"
        );
        content.add(basePriceSection);

        // Category 2: CUSTOMER_ADJUSTMENT
        VerticalLayout customerSection = createCategorySection(
            "2. Customer Adjustment",
            "#4CAF50",
            "👤",
            "Apply customer-specific discounts or fees.",
            "Multiple rules CAN apply in sequence",
            "• Volume discount (-10%)\n• Loyalty discount (-5%)\n• Customer rebate"
        );
        content.add(customerSection);

        // Category 3: PRODUCT_ADJUSTMENT
        VerticalLayout productSection = createCategorySection(
            "3. Product Adjustment",
            "#FF9800",
            "📦",
            "Apply product-specific fees or adjustments.",
            "Multiple rules CAN apply in sequence",
            "• Premium cut fee (+$2.00)\n• Packaging fee (+$1.50)\n• Processing charge"
        );
        content.add(productSection);

        // Category 4: PROMOTIONAL
        VerticalLayout promoSection = createCategorySection(
            "4. Promotional",
            "#E91E63",
            "🏷️",
            "Apply temporary promotional discounts.",
            "Multiple rules CAN apply in sequence",
            "• Seasonal sale (-15%)\n• Clearance discount (-20%)\n• Holiday special"
        );
        content.add(promoSection);

        // Example calculation
        VerticalLayout exampleSection = new VerticalLayout();
        exampleSection.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("padding", "var(--lumo-space-m)")
            .set("margin-top", "16px");

        H4 exampleTitle = new H4("Example Calculation");
        exampleTitle.getStyle().set("margin-top", "0");

        Span exampleText = new Span(
            "Cost: $10.00\n" +
            "→ Base Price (Cost + 20%): $12.00\n" +
            "→ Customer Adjustment (Volume -10%): $10.80\n" +
            "→ Customer Adjustment (Loyalty -5%): $10.26\n" +
            "→ Product Adjustment (Premium +$2): $12.26\n" +
            "→ Promotional (Sale -15%): $10.42\n" +
            "\nFinal Price: $10.42"
        );
        exampleText.getStyle().set("font-family", "monospace").set("white-space", "pre");

        exampleSection.add(exampleTitle, exampleText);
        content.add(exampleSection);

        Button closeButton = new Button("Close", event -> helpDialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        helpDialog.add(content);
        helpDialog.getFooter().add(closeButton);
        helpDialog.open();
    }

    /**
     * Create a category section for the help dialog
     */
    private VerticalLayout createCategorySection(String title, String color, String icon,
                                                  String description, String behavior, String examples) {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(false);
        section.setPadding(false);
        section.getStyle()
            .set("border-left", "4px solid " + color)
            .set("padding-left", "12px")
            .set("margin-bottom", "16px");

        HorizontalLayout titleLayout = new HorizontalLayout();
        titleLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        titleLayout.setSpacing(true);

        Span iconSpan = new Span(icon);
        iconSpan.getStyle().set("font-size", "1.2rem");

        Span titleSpan = new Span(title);
        titleSpan.getStyle().set("font-weight", "600").set("color", color);

        titleLayout.add(iconSpan, titleSpan);

        Span descSpan = new Span(description);
        descSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span behaviorSpan = new Span(behavior);
        behaviorSpan.getStyle()
            .set("font-weight", "600")
            .set("font-size", "0.9rem")
            .set("color", "var(--lumo-body-text-color)");

        Span examplesSpan = new Span(examples);
        examplesSpan.getStyle()
            .set("font-size", "0.85rem")
            .set("white-space", "pre-line")
            .set("color", "var(--lumo-secondary-text-color)");

        section.add(titleLayout, descSpan, behaviorSpan, examplesSpan);
        return section;
    }
}
