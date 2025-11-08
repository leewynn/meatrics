package com.meatrics.pricing.ui.component;

import com.meatrics.pricing.Customer;
import com.meatrics.pricing.CustomerRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import java.util.function.Consumer;

/**
 * Reusable dialog component for editing customer details.
 * Provides a consistent UI for customer editing across different views.
 */
public class CustomerEditDialog extends Dialog {

    private final Customer customer;
    private final CustomerRepository customerRepository;
    private final TextField customerCodeField;
    private final TextField customerNameField;
    private final TextField customerRatingField;
    private final TextArea notesField;
    private Consumer<Customer> onSaveCallback;

    /**
     * Constructor that creates and configures the customer edit dialog.
     *
     * @param customer The customer to edit
     * @param customerRepository Repository for persisting customer changes
     */
    public CustomerEditDialog(Customer customer, CustomerRepository customerRepository) {
        this.customer = customer;
        this.customerRepository = customerRepository;

        setHeaderTitle("Edit Customer");
        setWidth("500px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        // Customer code (read-only)
        customerCodeField = new TextField("Customer Code");
        customerCodeField.setValue(customer.getCustomerCode());
        customerCodeField.setReadOnly(true);
        customerCodeField.setWidthFull();

        // Customer name (read-only)
        customerNameField = new TextField("Customer Name");
        customerNameField.setValue(customer.getCustomerName());
        customerNameField.setReadOnly(true);
        customerNameField.setWidthFull();

        // Customer rating (stored from last calculation, editable)
        customerRatingField = new TextField("Customer Rating");
        if (customer.getCustomerRating() != null && !customer.getCustomerRating().trim().isEmpty()) {
            customerRatingField.setValue(customer.getCustomerRating());
        }
        customerRatingField.setWidthFull();
        customerRatingField.setHelperText("Auto-calculated during import. Use 'Recalculate All Ratings' button to refresh.");

        // Notes (editable)
        notesField = new TextArea("Notes");
        if (customer.getNotes() != null) {
            notesField.setValue(customer.getNotes());
        }
        notesField.setWidthFull();
        notesField.setHeight("150px");

        dialogLayout.add(customerCodeField, customerNameField, customerRatingField, notesField);

        // Buttons
        Button saveButton = new Button("Save", event -> handleSave());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", event -> close());

        HorizontalLayout buttonLayout = new HorizontalLayout(saveButton, cancelButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();

        add(dialogLayout, buttonLayout);
    }

    /**
     * Set a callback to be invoked after successful save.
     *
     * @param callback Consumer that receives the saved customer
     */
    public void setOnSaveCallback(Consumer<Customer> callback) {
        this.onSaveCallback = callback;
    }

    /**
     * Open the dialog with an optional save callback.
     *
     * @param onSave Callback invoked after successful save (can be null)
     */
    public void open(Consumer<Customer> onSave) {
        this.onSaveCallback = onSave;
        open();
    }

    /**
     * Handle the save action - persist changes and invoke callback.
     */
    private void handleSave() {
        customer.setCustomerRating(customerRatingField.getValue());
        customer.setNotes(notesField.getValue());
        customerRepository.save(customer);

        if (onSaveCallback != null) {
            onSaveCallback.accept(customer);
        }

        close();
    }
}
