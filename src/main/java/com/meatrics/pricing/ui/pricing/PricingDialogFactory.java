package com.meatrics.pricing.ui.pricing;

import com.meatrics.pricing.customer.Customer;
import com.meatrics.pricing.customer.CustomerPricingRuleRepository;
import com.meatrics.pricing.customer.CustomerRepository;
import com.meatrics.pricing.customer.CustomerTagRepository;
import com.meatrics.pricing.product.GroupedLineItem;
import com.meatrics.pricing.product.GroupedLineItemRepository;
import com.meatrics.pricing.rule.PricingRule;
import com.meatrics.pricing.session.PricingSession;
import com.meatrics.pricing.ui.customer.CustomerCompanyDialog;
import com.meatrics.pricing.ui.customer.CustomerGroupDialog;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Factory for creating all pricing-related dialogs.
 * Centralizes dialog creation logic for better maintainability.
 */
@Component
public class PricingDialogFactory {

    private static final Logger log = LoggerFactory.getLogger(PricingDialogFactory.class);

    private final PricingCalculator pricingCalculator;
    private final PricingSessionManager sessionManager;
    private final PricingGridColumnManager columnManager;
    private final CustomerRepository customerRepository;
    private final CustomerTagRepository customerTagRepository;
    private final CustomerPricingRuleRepository customerPricingRuleRepository;
    private final GroupedLineItemRepository groupedLineItemRepository;

    public PricingDialogFactory(PricingCalculator pricingCalculator,
                               PricingSessionManager sessionManager,
                               PricingGridColumnManager columnManager,
                               CustomerRepository customerRepository,
                               CustomerTagRepository customerTagRepository,
                               CustomerPricingRuleRepository customerPricingRuleRepository,
                               GroupedLineItemRepository groupedLineItemRepository) {
        this.pricingCalculator = pricingCalculator;
        this.sessionManager = sessionManager;
        this.columnManager = columnManager;
        this.customerRepository = customerRepository;
        this.customerTagRepository = customerTagRepository;
        this.customerPricingRuleRepository = customerPricingRuleRepository;
        this.groupedLineItemRepository = groupedLineItemRepository;
    }

    /**
     * Open price edit dialog with rule details and price editing
     *
     * @param item The item to edit
     * @param onSave Callback when price is saved (receives the updated item)
     * @param currentSession The current pricing session (used to check if finalized and disable save)
     */
    public void openPriceEditDialog(GroupedLineItem item, Consumer<GroupedLineItem> onSave, PricingSession currentSession) {
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
                VerticalLayout ruleDetailsSection = createRuleDetailsSection(item, appliedRules, intermediateResults, hasIntermediateResults);
                mainLayout.add(ruleDetailsSection);
            }

            // SECTION 2: Price Editing Form
            VerticalLayout formLayout = createPriceEditForm(item);
            mainLayout.add(formLayout);

            // Buttons
            NumberField newPriceField = (NumberField) formLayout.getComponentAt(formLayout.getComponentCount() - 1);

            boolean isFinalized = currentSession != null && "FINALIZED".equals(currentSession.getStatus());

            Button saveButton = new Button("Save", event -> {
                Double newPrice = newPriceField.getValue();
                if (newPrice != null && newPrice > 0) {
                    item.setNewUnitSellPrice(BigDecimal.valueOf(newPrice));
                    item.setManualOverride(true);
                    item.setAppliedRules(List.of()); // Clear rules when manually overridden
                    pricingCalculator.recalculateItemFields(item);

                    onSave.accept(item);
                    dialog.close();
                }
            });
            saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

            // Disable save button and input field if session is finalized
            if (isFinalized) {
                saveButton.setEnabled(false);
                saveButton.setTooltipText("Cannot modify finalized session. Click Unfinalize to make changes.");
                newPriceField.setEnabled(false);
                newPriceField.setHelperText("Session is finalized - click Unfinalize to edit prices");
            }

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
     * Create the rule details section showing pricing breakdown
     */
    private VerticalLayout createRuleDetailsSection(GroupedLineItem item,
                                                    List<PricingRule> appliedRules,
                                                    List<BigDecimal> intermediateResults,
                                                    boolean hasIntermediateResults) {
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
            ruleDetailsSection.add(createManualOverrideBadge());
        } else if (!appliedRules.isEmpty() && !hasIntermediateResults) {
            // Recalculate intermediate results for loaded sessions
            List<BigDecimal> recalculatedIntermediates = pricingCalculator.recalculateIntermediateResults(item, appliedRules);
            addRuleBreakdown(ruleDetailsSection, item, appliedRules, recalculatedIntermediates);
        } else if (!appliedRules.isEmpty()) {
            // Show layered calculation with intermediate prices
            addRuleBreakdown(ruleDetailsSection, item, appliedRules, intermediateResults);
        }

        return ruleDetailsSection;
    }

    /**
     * Create manual override badge
     */
    private HorizontalLayout createManualOverrideBadge() {
        HorizontalLayout manualLayout = new HorizontalLayout();
        manualLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        manualLayout.getStyle().set("gap", "8px");

        Span badge = new Span("MANUAL");
        badge.getElement().getThemeList().add("badge");
        badge.getElement().getThemeList().add("warning");

        Span explanation = new Span("This price was manually set and does not use any pricing rule.");
        explanation.getStyle().set("color", "#666");

        manualLayout.add(badge, explanation);
        return manualLayout;
    }

    /**
     * Add rule breakdown to the details section
     */
    private void addRuleBreakdown(VerticalLayout section, GroupedLineItem item,
                                  List<PricingRule> appliedRules,
                                  List<BigDecimal> intermediateResults) {
        BigDecimal currentPrice = item.getIncomingCost();

        // Starting price
        section.add(createStartingPriceLayout(currentPrice));

        // Each rule application
        for (int i = 0; i < appliedRules.size(); i++) {
            PricingRule rule = appliedRules.get(i);
            BigDecimal inputPrice = i < intermediateResults.size() ?
                intermediateResults.get(i) : currentPrice;
            BigDecimal resultPrice = (i + 1) < intermediateResults.size() ?
                intermediateResults.get(i + 1) : item.getNewUnitSellPrice();

            section.add(createRuleLayerLayout(rule, inputPrice, resultPrice));
        }

        // Final price summary
        if (intermediateResults.size() > 1) {
            section.add(createFinalPriceLayout(item.getNewUnitSellPrice()));
        }
    }

    /**
     * Create starting price layout
     */
    private HorizontalLayout createStartingPriceLayout(BigDecimal startingCost) {
        HorizontalLayout startLayout = new HorizontalLayout();
        startLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        startLayout.setWidthFull();
        startLayout.getStyle().set("gap", "8px");

        Span startLabel = new Span("Starting Cost:");
        startLabel.getStyle()
            .set("font-weight", "600")
            .set("color", "#666")
            .set("min-width", "120px");

        Span startPrice = new Span(pricingCalculator.formatCurrency(startingCost));
        startPrice.getStyle()
            .set("font-weight", "bold")
            .set("color", "#333");

        startLayout.add(startLabel, startPrice);
        return startLayout;
    }

    /**
     * Create rule layer layout showing one rule's calculation
     */
    private VerticalLayout createRuleLayerLayout(PricingRule rule, BigDecimal inputPrice, BigDecimal resultPrice) {
        VerticalLayout ruleLayout = new VerticalLayout();
        ruleLayout.setSpacing(false);
        ruleLayout.setPadding(true);
        ruleLayout.getStyle()
            .set("background-color", "white")
            .set("border-left", "4px solid #2196F3")
            .set("border-radius", "4px")
            .set("padding", "8px")
            .set("margin", "4px 0");

        // Rule header with execution order
        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        headerLayout.getStyle().set("gap", "6px");

        Icon ruleIcon = VaadinIcon.COG.create();
        ruleIcon.setSize("16px");
        ruleIcon.getStyle().set("color", "#2196F3");

        Span executionOrder = new Span("Rule #" + (rule.getExecutionOrder() != null ? rule.getExecutionOrder() : "?"));
        executionOrder.getStyle()
            .set("font-weight", "600")
            .set("color", "#2196F3")
            .set("font-size", "0.85rem");

        headerLayout.add(ruleIcon, executionOrder);

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

        Span resultPriceSpan = new Span(pricingCalculator.formatCurrency(resultPrice));
        resultPriceSpan.getStyle()
            .set("font-weight", "bold")
            .set("color", "#2196F3")
            .set("font-size", "1.1rem");

        priceLayout.add(arrow, resultPriceSpan);

        ruleLayout.add(headerLayout, ruleName, ruleMethod, calculation, priceLayout);
        return ruleLayout;
    }

    /**
     * Create final price summary layout
     */
    private HorizontalLayout createFinalPriceLayout(BigDecimal finalPrice) {
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

        Span finalPriceSpan = new Span(pricingCalculator.formatCurrency(finalPrice));
        finalPriceSpan.getStyle()
            .set("font-weight", "bold")
            .set("color", "#4CAF50")
            .set("font-size", "1.2rem");

        finalLayout.add(finalLabel, finalPriceSpan);
        return finalLayout;
    }

    /**
     * Create price edit form
     */
    private VerticalLayout createPriceEditForm(GroupedLineItem item) {
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
        lastPriceField.setValue(pricingCalculator.formatCurrency(item.getLastUnitSellPrice()));
        lastPriceField.setReadOnly(true);
        lastPriceField.setWidthFull();

        TextField currentPriceField = new TextField("Current New Price");
        currentPriceField.setValue(pricingCalculator.formatCurrency(item.getNewUnitSellPrice()));
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
        return formLayout;
    }

    /**
     * Open customer edit dialog - opens appropriate dialog based on entity type
     */
    public void openCustomerEditDialog(GroupedLineItem item) {
        String customerCode = item.getCustomerCode();
        if (customerCode == null || customerCode.trim().isEmpty()) {
            return;
        }

        customerRepository.findByCustomerCode(customerCode).ifPresent(customer -> {
            if (customer.isGroup()) {
                // Open group dialog
                CustomerGroupDialog dialog = new CustomerGroupDialog(
                        customer,
                        customerRepository,
                        customerTagRepository,
                        customerPricingRuleRepository,
                        groupedLineItemRepository
                );
                dialog.open();
            } else {
                // Open company dialog
                CustomerCompanyDialog dialog = new CustomerCompanyDialog(
                        customer,
                        customerRepository,
                        customerTagRepository,
                        customerPricingRuleRepository,
                        groupedLineItemRepository
                );
                dialog.open();
            }
        });
    }

    /**
     * Open save session dialog
     */
    public void openSaveSessionDialog(List<GroupedLineItem> backingList, PricingSession currentSession,
                                     Consumer<SaveSessionResult> onSave) {
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
            boolean nameExists = sessionManager.sessionNameExists(sessionName.trim());
            boolean isCurrentSession = currentSession != null &&
                    sessionName.trim().equals(currentSession.getSessionName());

            if (nameExists && !isCurrentSession) {
                // Session name exists - ask to overwrite
                openOverwriteConfirmDialog(sessionName.trim(), notesField.getValue(),
                                          backingList, dialog, onSave);
            } else {
                // New session or updating current session
                performSave(sessionName.trim(), notesField.getValue(), backingList, dialog, onSave);
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
     * Open overwrite confirmation dialog
     */
    private void openOverwriteConfirmDialog(String sessionName, String notes,
                                           List<GroupedLineItem> backingList,
                                           Dialog parentDialog,
                                           Consumer<SaveSessionResult> onSave) {
        ConfirmDialog confirmDialog = new ConfirmDialog();
        confirmDialog.setHeader("Session Already Exists");
        confirmDialog.setText("A session with name '" + sessionName +
                "' already exists. Do you want to overwrite it?");
        confirmDialog.setCancelable(true);
        confirmDialog.setCancelText("Cancel");
        confirmDialog.setConfirmText("Overwrite");
        confirmDialog.setConfirmButtonTheme("error primary");

        confirmDialog.addConfirmListener(confirmEvent -> {
            performSave(sessionName, notes, backingList, parentDialog, onSave);
        });

        confirmDialog.open();
    }

    /**
     * Perform the actual save operation
     */
    private void performSave(String sessionName, String notes, List<GroupedLineItem> backingList,
                           Dialog dialog, Consumer<SaveSessionResult> onSave) {
        try {
            PricingSession savedSession = sessionManager.performSave(sessionName, notes, backingList);

            dialog.close();
            showSuccessNotification("Session '" + sessionName + "' saved successfully with " +
                    backingList.size() + " line items");

            onSave.accept(new SaveSessionResult(savedSession, false));

        } catch (Exception e) {
            showErrorNotification("Error saving session: " + e.getMessage());
        }
    }

    /**
     * Open load session dialog
     */
    public void openLoadSessionDialog(boolean hasUnsavedChanges, PricingSession currentSession,
                                      Consumer<LoadSessionResult> onLoad) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Load Pricing Session");
        dialog.setWidth("800px");
        dialog.setHeight("600px");

        VerticalLayout content = new VerticalLayout();
        content.setSizeFull();
        content.setSpacing(true);
        content.setPadding(false);

        // Grid to show all sessions
        Grid<PricingSession> sessionGrid = createSessionGrid();

        // Load all sessions
        List<PricingSession> sessions = sessionManager.getAllSessions();
        sessionGrid.setItems(sessions);

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
                if (hasUnsavedChanges) {
                    openUnsavedChangesConfirmDialog(() -> performLoad(selectedSession, dialog, onLoad));
                } else {
                    performLoad(selectedSession, dialog, onLoad);
                }
            });
        });

        // Delete button action
        deleteButton.addClickListener(event -> {
            sessionGrid.getSelectedItems().stream().findFirst().ifPresent(selectedSession -> {
                openDeleteConfirmDialog(selectedSession, sessionGrid, currentSession, onLoad);
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
     * Create session grid for load dialog
     */
    private Grid<PricingSession> createSessionGrid() {
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

        sessionGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        return sessionGrid;
    }

    /**
     * Open unsaved changes confirmation dialog
     */
    private void openUnsavedChangesConfirmDialog(Runnable onConfirm) {
        ConfirmDialog confirmDialog = new ConfirmDialog();
        confirmDialog.setHeader("Unsaved Changes");
        confirmDialog.setText("You have unsaved changes. Loading a session will discard them. Continue?");
        confirmDialog.setCancelable(true);
        confirmDialog.setCancelText("Cancel");
        confirmDialog.setConfirmText("Load Session");
        confirmDialog.setConfirmButtonTheme("error primary");

        confirmDialog.addConfirmListener(confirmEvent -> onConfirm.run());
        confirmDialog.open();
    }

    /**
     * Open delete confirmation dialog
     */
    private void openDeleteConfirmDialog(PricingSession selectedSession,
                                        Grid<PricingSession> sessionGrid,
                                        PricingSession currentSession,
                                        Consumer<LoadSessionResult> onSessionChange) {
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
                sessionManager.deleteSession(selectedSession.getId());

                // Refresh grid
                List<PricingSession> updatedSessions = sessionManager.getAllSessions();
                sessionGrid.setItems(updatedSessions);

                // If deleted session was current, clear current session
                if (currentSession != null && currentSession.getId().equals(selectedSession.getId())) {
                    onSessionChange.accept(new LoadSessionResult(null, null, false, true));
                }

                showSuccessNotification("Session '" + selectedSession.getSessionName() + "' deleted");

            } catch (Exception e) {
                showErrorNotification("Error deleting session: " + e.getMessage());
            }
        });

        confirmDialog.open();
    }

    /**
     * Perform the actual load operation
     */
    private void performLoad(PricingSession session, Dialog dialog, Consumer<LoadSessionResult> onLoad) {
        try {
            List<GroupedLineItem> loadedItems = sessionManager.performLoad(session.getId());

            dialog.close();
            showSuccessNotification("Session '" + session.getSessionName() + "' loaded successfully with " +
                    loadedItems.size() + " line items");

            onLoad.accept(new LoadSessionResult(loadedItems, session, false, false));

        } catch (Exception e) {
            showErrorNotification("Error loading session: " + e.getMessage());
        }
    }

    /**
     * Open column visibility dialog
     */
    public void openColumnVisibilityDialog(PricingGridColumnManager.GridColumns columns,
                                          Runnable onColumnChange) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Show/Hide Columns");
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(true);

        // Basic Info group
        content.add(new H4("Basic Info") {{
            getStyle().set("margin-top", "0");
        }});
        content.add(createColumnCheckbox("Customer", columns.customerCol, onColumnChange));
        content.add(createColumnCheckbox("Rating", columns.ratingCol, onColumnChange));
        content.add(createColumnCheckbox("Product Code", columns.productCodeCol, onColumnChange));
        content.add(createColumnCheckbox("Product", columns.productCol, onColumnChange));
        content.add(createColumnCheckbox("Quantity", columns.qtyCol, onColumnChange));

        // Historical (Last) group
        content.add(new H4("Historical (Last)") {{
            getStyle().set("margin-top", "10px");
        }});
        content.add(createColumnCheckbox("Cost", columns.lastCostCol, onColumnChange));
        content.add(createColumnCheckbox("Price", columns.lastPriceCol, onColumnChange));
        content.add(createColumnCheckbox("Amount", columns.lastAmountCol, onColumnChange));
        content.add(createColumnCheckbox("GP $", columns.lastGPCol, onColumnChange));
        content.add(createColumnCheckbox("GP %", columns.lastGPPercentCol, onColumnChange));

        // Cost Drift (standalone)
        content.add(new H4("Cost Variance") {{
            getStyle().set("margin-top", "10px");
        }});
        content.add(createColumnCheckbox("Cost Drift", columns.costDriftCol, onColumnChange));

        // New Pricing group
        content.add(new H4("New Pricing") {{
            getStyle().set("margin-top", "10px");
        }});
        content.add(createColumnCheckbox("Cost", columns.newCostCol, onColumnChange));
        content.add(createColumnCheckbox("Price", columns.newPriceCol, onColumnChange));
        content.add(createColumnCheckbox("Amount", columns.newAmountCol, onColumnChange));
        content.add(createColumnCheckbox("GP $", columns.newGPCol, onColumnChange));
        content.add(createColumnCheckbox("GP %", columns.newGPPercentCol, onColumnChange));

        // Notes
        content.add(new H4("Other") {{
            getStyle().set("margin-top", "10px");
        }});
        content.add(createColumnCheckbox("Notes", columns.notesCol, onColumnChange));

        // Buttons
        Button closeButton = new Button("Close", event -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout buttonLayout = new HorizontalLayout(closeButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();
        buttonLayout.getStyle().set("margin-top", "10px");

        dialog.add(content, buttonLayout);
        dialog.open();
    }

    /**
     * Create a column visibility checkbox
     */
    private Checkbox createColumnCheckbox(String label, Grid.Column<GroupedLineItem> column, Runnable onChange) {
        Checkbox checkbox = new Checkbox(label, column.isVisible());
        checkbox.addValueChangeListener(e -> {
            column.setVisible(e.getValue());
            onChange.run();
        });
        return checkbox;
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
            case "TARGET_GP_PERCENT":
                if (value != null) {
                    BigDecimal gpPercent = value.multiply(new BigDecimal("100"));
                    return String.format("Target GP%% at %.1f%%", gpPercent);
                } else {
                    return "Target GP% (no value set)";
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
                    pricingCalculator.formatCurrency(inputPrice), value, pricingCalculator.formatCurrency(resultPrice));
            case "COST_PLUS_FIXED":
                if (value == null) return "";
                return String.format("%s + %s = %s",
                    pricingCalculator.formatCurrency(inputPrice), pricingCalculator.formatCurrency(value),
                    pricingCalculator.formatCurrency(resultPrice));
            case "FIXED_PRICE":
                if (value == null) return "";
                return String.format("Fixed = %s", pricingCalculator.formatCurrency(value));
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
                    pricingCalculator.formatCurrency(inputPrice), actualGPPercent, gpSource,
                    pricingCalculator.formatCurrency(inputPrice), divisor, pricingCalculator.formatCurrency(resultPrice));
            case "TARGET_GP_PERCENT":
                if (value == null || resultPrice == null || inputPrice == null) return "";
                if (resultPrice.compareTo(BigDecimal.ZERO) == 0) return "";

                // value is the target GP% as decimal (e.g., 0.25 for 25%)
                BigDecimal targetGPPercent = value.multiply(new BigDecimal("100"));
                BigDecimal targetDivisor = BigDecimal.ONE.subtract(value);

                return String.format("%s ÷ (1 - %.1f%%) = %s ÷ %.4f = %s",
                    pricingCalculator.formatCurrency(inputPrice), targetGPPercent,
                    pricingCalculator.formatCurrency(inputPrice), targetDivisor, pricingCalculator.formatCurrency(resultPrice));
            default:
                return "";
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

    /**
     * Result of save session operation
     */
    public static class SaveSessionResult {
        private final PricingSession savedSession;
        private final boolean hasUnsavedChanges;

        public SaveSessionResult(PricingSession savedSession, boolean hasUnsavedChanges) {
            this.savedSession = savedSession;
            this.hasUnsavedChanges = hasUnsavedChanges;
        }

        public PricingSession getSavedSession() {
            return savedSession;
        }

        public boolean hasUnsavedChanges() {
            return hasUnsavedChanges;
        }
    }

    /**
     * Result of load session operation
     */
    public static class LoadSessionResult {
        private final List<GroupedLineItem> loadedItems;
        private final PricingSession session;
        private final boolean hasUnsavedChanges;
        private final boolean sessionDeleted;

        public LoadSessionResult(List<GroupedLineItem> loadedItems, PricingSession session,
                               boolean hasUnsavedChanges, boolean sessionDeleted) {
            this.loadedItems = loadedItems;
            this.session = session;
            this.hasUnsavedChanges = hasUnsavedChanges;
            this.sessionDeleted = sessionDeleted;
        }

        public List<GroupedLineItem> getLoadedItems() {
            return loadedItems;
        }

        public PricingSession getSession() {
            return session;
        }

        public boolean hasUnsavedChanges() {
            return hasUnsavedChanges;
        }

        public boolean isSessionDeleted() {
            return sessionDeleted;
        }
    }
}
