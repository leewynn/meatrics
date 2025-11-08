package com.meatrics.pricing.ui;

import com.meatrics.pricing.CostImportSummary;
import com.meatrics.pricing.CustomerRatingService;
import com.meatrics.pricing.DuplicateImportException;
import com.meatrics.pricing.ImportSummary;
import com.meatrics.pricing.ImportedLineItem;
import com.meatrics.pricing.PricingImportService;
import com.meatrics.pricing.ProductCostImportService;
import com.meatrics.pricing.ReportExportService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Import pricing data view with Excel file upload
 */
@Route("import-pricing")
@PageTitle("Import Pricing")
@Menu(order = 3, icon = "vaadin:upload", title = "Import Pricing")
public class ImportPricingView extends Main {

    private static final Logger log = LoggerFactory.getLogger(ImportPricingView.class);

    private final PricingImportService pricingImportService;
    private final ProductCostImportService productCostImportService;
    private final CustomerRatingService customerRatingService;
    private final ReportExportService reportExportService;
    private final Grid<ImportSummary> importGrid;
    private final Grid<CostImportSummary> costImportGrid;
    private Upload upload;
    private Upload costUpload;
    private Paragraph uploadedFilesList;
    private File uploadedCostFile;

    public ImportPricingView(PricingImportService pricingImportService,
                           ProductCostImportService productCostImportService,
                           CustomerRatingService customerRatingService,
                           ReportExportService reportExportService) {
        this.pricingImportService = pricingImportService;
        this.productCostImportService = productCostImportService;
        this.customerRatingService = customerRatingService;
        this.reportExportService = reportExportService;
        addClassName("import-pricing-view");

        // Main layout
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setSpacing(true);
        mainLayout.setPadding(true);

        // Title
        H2 pageTitle = new H2("Import Data");

        // Recalculate All Ratings button
        Button recalculateRatingsButton = new Button("Recalculate All Customer Ratings", new Icon(VaadinIcon.REFRESH));
        recalculateRatingsButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        recalculateRatingsButton.addClickListener(event -> recalculateAllRatings());

        // Create two sections side by side
        HorizontalLayout sectionsLayout = new HorizontalLayout();
        sectionsLayout.setSizeFull();
        sectionsLayout.setSpacing(true);

        // Left section: Pricing Data Import
        VerticalLayout pricingSection = createPricingImportSection();
        pricingSection.setWidth("50%");

        // Right section: Cost Import
        VerticalLayout costSection = createCostImportSection();
        costSection.setWidth("50%");

        sectionsLayout.add(pricingSection, costSection);

        // Import history sections
        HorizontalLayout historyLayout = new HorizontalLayout();
        historyLayout.setSizeFull();
        historyLayout.setSpacing(true);

        // Pricing import history
        importGrid = createImportHistoryGrid();
        VerticalLayout pricingHistorySection = new VerticalLayout();
        pricingHistorySection.setWidth("50%");
        pricingHistorySection.add(new H3("Pricing Import History"), importGrid);

        // Cost import history
        costImportGrid = createCostImportHistoryGrid();
        VerticalLayout costHistorySection = new VerticalLayout();
        costHistorySection.setWidth("50%");
        costHistorySection.add(new H3("Cost Import History"), costImportGrid);

        historyLayout.add(pricingHistorySection, costHistorySection);

        // Add all sections to main layout
        mainLayout.add(pageTitle, recalculateRatingsButton, sectionsLayout, historyLayout);

        add(mainLayout);
        refreshGrid();
    }

    private VerticalLayout createPricingImportSection() {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(true);
        section.setPadding(true);
        section.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        section.getStyle().set("border-radius", "var(--lumo-border-radius-m)");

        H3 sectionTitle = new H3("Pricing Data Import");

        // Upload section
        VerticalLayout uploadSection = createUploadSection();

        // Uploaded files list
        uploadedFilesList = new Paragraph();
        updateUploadedFilesList();

        // Import button
        Button importButton = new Button("Import All Files", new Icon(VaadinIcon.DATABASE));
        importButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        importButton.addClickListener(event -> importFiles());

        Button clearButton = new Button("Clear All", new Icon(VaadinIcon.CLOSE));
        clearButton.addClickListener(event -> clearAllFiles());

        HorizontalLayout buttonLayout = new HorizontalLayout(importButton, clearButton);
        buttonLayout.setSpacing(true);

        section.add(sectionTitle, uploadSection, uploadedFilesList, buttonLayout);
        return section;
    }

    private VerticalLayout createCostImportSection() {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(true);
        section.setPadding(true);
        section.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        section.getStyle().set("border-radius", "var(--lumo-border-radius-m)");

        H3 sectionTitle = new H3("Cost Import");

        // Upload section
        VerticalLayout costUploadSection = createCostUploadSection();

        // Statistics display (moved below drop area)
        ProductCostImportService.CostImportStats stats = productCostImportService.getStats();
        Div statsDiv = new Div();
        statsDiv.add(new Paragraph("Current Products: " + stats.getTotalProducts()));
        statsDiv.add(new Paragraph("Active Products: " + stats.getActiveProducts()));
        statsDiv.add(new Paragraph("Products with Standard Cost: " + stats.getProductsWithCost()));
        statsDiv.getStyle().set("background", "var(--lumo-contrast-5pct)");
        statsDiv.getStyle().set("padding", "var(--lumo-space-m)");
        statsDiv.getStyle().set("border-radius", "var(--lumo-border-radius-m)");

        // Import button
        Button importCostButton = new Button("Import Cost File", new Icon(VaadinIcon.DATABASE));
        importCostButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        importCostButton.addClickListener(event -> importCostFile());

        section.add(sectionTitle, costUploadSection, statsDiv, importCostButton);
        return section;
    }

    private VerticalLayout createUploadSection() {
        VerticalLayout uploadLayout = new VerticalLayout();
        uploadLayout.setSpacing(true);
        uploadLayout.setPadding(false);
        uploadLayout.setMaxWidth("800px");

        // Create upload handler
        UploadHandler uploadHandler = UploadHandler.toTempFile((metadata, file) -> {
            try {
                String fileName = metadata.fileName();

                // Store file for later processing
                pricingImportService.storeUploadedFile(fileName, file);

                // UI updates must be wrapped in UI.access()
                getUI().ifPresent(ui -> ui.access(() -> {
                    updateUploadedFilesList();
                    Notification notification = Notification.show(
                        "File ready for import: " + fileName,
                        3000,
                        Notification.Position.TOP_CENTER
                    );
                    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                }));

            } catch (Exception e) {
                getUI().ifPresent(ui -> ui.access(() -> {
                    Notification notification = Notification.show(
                        "Error uploading file: " + e.getMessage(),
                        5000,
                        Notification.Position.TOP_CENTER
                    );
                    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                }));
            }
        });

        upload = new Upload(uploadHandler);

        // Configure upload
        upload.setAcceptedFileTypes(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        upload.setMaxFileSize(10 * 1024 * 1024); // 10MB

        // Custom upload area styling
        upload.getStyle()
            .set("border", "2px dashed var(--lumo-contrast-30pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("padding", "var(--lumo-space-xl)")
            .set("background", "var(--lumo-contrast-5pct)");

        // Upload area content
        Div uploadContent = new Div();
        uploadContent.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("align-items", "center")
            .set("gap", "var(--lumo-space-m)");

        // Upload icon
        Div icon = new Div();
        icon.getElement().setProperty("innerHTML", VaadinIcon.UPLOAD.create().getElement().getOuterHTML());
        icon.getStyle()
            .set("font-size", "48px")
            .set("color", "var(--lumo-contrast-60pct)");

        // Upload title
        H3 title = new H3("Drop Excel files here");
        title.getStyle()
            .set("margin", "0")
            .set("color", "var(--lumo-contrast-60pct)");

        // Upload subtitle
        Paragraph subtitle = new Paragraph("or click to browse");
        subtitle.getStyle()
            .set("margin", "0")
            .set("color", "var(--lumo-contrast-60pct)")
            .set("font-size", "var(--lumo-font-size-s)");

        uploadContent.add(icon, title, subtitle);
        upload.setDropLabel(uploadContent);

        // Failed handler
        upload.addFileRejectedListener(event -> {
            Notification notification = Notification.show(
                "File rejected: " + event.getErrorMessage(),
                5000,
                Notification.Position.TOP_CENTER
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        Paragraph description = new Paragraph("Upload Excel (.xlsx) files to import pricing data. Files will be queued for import.");

        uploadLayout.add(description, upload);
        return uploadLayout;
    }

    private Grid<ImportSummary> createImportHistoryGrid() {
        Grid<ImportSummary> grid = new Grid<>(ImportSummary.class, false);
        grid.setSizeFull();
        grid.setMinHeight("400px");

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        grid.addColumn(ImportSummary::getFilename).setHeader("Filename").setAutoWidth(true).setResizable(true);
        grid.addColumn(ImportSummary::getRecordCount).setHeader("Records").setAutoWidth(true);
        grid.addColumn(ImportSummary::getStatus).setHeader("Status").setAutoWidth(true);
        grid.addColumn(item -> item.getImportDate() != null ? item.getImportDate().format(dateTimeFormatter) : "")
            .setHeader("Import Date")
            .setAutoWidth(true);

        // Delete button column
        grid.addColumn(new ComponentRenderer<>(Button::new, (deleteButton, importSummary) -> {
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_ERROR);
            deleteButton.setIcon(new Icon(VaadinIcon.TRASH));
            deleteButton.setTooltipText("Delete import");
            deleteButton.addClickListener(event -> showDeleteConfirmation(importSummary));
        })).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);

        return grid;
    }

    private VerticalLayout createCostUploadSection() {
        VerticalLayout uploadLayout = new VerticalLayout();
        uploadLayout.setSpacing(true);
        uploadLayout.setPadding(false);
        uploadLayout.setMaxWidth("800px");

        // Create upload handler for cost file
        UploadHandler costUploadHandler = UploadHandler.toTempFile((metadata, file) -> {
            try {
                // Store the uploaded file reference
                uploadedCostFile = file;

                // UI updates must be wrapped in UI.access()
                getUI().ifPresent(ui -> ui.access(() -> {
                    Notification notification = Notification.show(
                        "File ready for import: " + metadata.fileName(),
                        3000,
                        Notification.Position.TOP_CENTER
                    );
                    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                }));

            } catch (Exception e) {
                getUI().ifPresent(ui -> ui.access(() -> {
                    Notification notification = Notification.show(
                        "Error uploading file: " + e.getMessage(),
                        5000,
                        Notification.Position.TOP_CENTER
                    );
                    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                }));
            }
        });

        costUpload = new Upload(costUploadHandler);

        // Configure upload
        costUpload.setAcceptedFileTypes(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        costUpload.setMaxFileSize(10 * 1024 * 1024); // 10MB
        costUpload.setMaxFiles(1); // Only one file at a time

        // Custom upload area styling
        costUpload.getStyle()
            .set("border", "2px dashed var(--lumo-contrast-30pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("padding", "var(--lumo-space-xl)")
            .set("background", "var(--lumo-contrast-5pct)");

        // Upload area content
        Div uploadContent = new Div();
        uploadContent.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("align-items", "center")
            .set("gap", "var(--lumo-space-m)");

        // Upload icon
        Div icon = new Div();
        icon.getElement().setProperty("innerHTML", VaadinIcon.UPLOAD.create().getElement().getOuterHTML());
        icon.getStyle()
            .set("font-size", "48px")
            .set("color", "var(--lumo-contrast-60pct)");

        // Upload title
        H3 title = new H3("Drop Cost Excel file here");
        title.getStyle()
            .set("margin", "0")
            .set("color", "var(--lumo-contrast-60pct)");

        // Upload subtitle
        Paragraph subtitle = new Paragraph("or click to browse");
        subtitle.getStyle()
            .set("margin", "0")
            .set("color", "var(--lumo-contrast-60pct)")
            .set("font-size", "var(--lumo-font-size-s)");

        uploadContent.add(icon, title, subtitle);
        costUpload.setDropLabel(uploadContent);

        // Failed handler
        costUpload.addFileRejectedListener(event -> {
            Notification notification = Notification.show(
                "File rejected: " + event.getErrorMessage(),
                5000,
                Notification.Position.TOP_CENTER
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        Paragraph description = new Paragraph("Upload product cost Excel file. This will replace all existing cost data.");

        uploadLayout.add(description, costUpload);
        return uploadLayout;
    }

    private Grid<CostImportSummary> createCostImportHistoryGrid() {
        Grid<CostImportSummary> grid = new Grid<>(CostImportSummary.class, false);
        grid.setSizeFull();
        grid.setMinHeight("400px");

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        grid.addColumn(CostImportSummary::getFilename).setHeader("Filename").setAutoWidth(true).setResizable(true);
        grid.addColumn(CostImportSummary::getTotalProducts).setHeader("Total Products").setAutoWidth(true);
        grid.addColumn(CostImportSummary::getActiveProducts).setHeader("Active").setAutoWidth(true);
        grid.addColumn(CostImportSummary::getProductsWithCost).setHeader("With Cost").setAutoWidth(true);
        grid.addColumn(CostImportSummary::getImportStatus).setHeader("Status").setAutoWidth(true);
        grid.addColumn(item -> item.getImportDate() != null ? item.getImportDate().format(dateTimeFormatter) : "")
            .setHeader("Import Date")
            .setAutoWidth(true);

        return grid;
    }

    private void importCostFile() {
        if (uploadedCostFile == null) {
            Notification.show(
                "Please upload a cost file first",
                3000,
                Notification.Position.TOP_CENTER
            ).addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            // Import the file
            FileInputStream fis = new FileInputStream(uploadedCostFile);
            CostImportSummary summary = productCostImportService.importFromExcel(fis, uploadedCostFile.getName());
            fis.close();

            if ("SUCCESS".equals(summary.getImportStatus())) {
                Notification.show(
                    String.format("Successfully imported %d products (%d active, %d with cost)",
                        summary.getTotalProducts(),
                        summary.getActiveProducts(),
                        summary.getProductsWithCost()),
                    5000,
                    Notification.Position.TOP_CENTER
                ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                // Clear the uploaded file reference
                uploadedCostFile = null;
                costUpload.clearFileList();

                // Refresh grids
                refreshGrid();
            } else {
                Notification.show(
                    "Import failed: " + summary.getErrorMessage(),
                    5000,
                    Notification.Position.TOP_CENTER
                ).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }

        } catch (Exception e) {
            Notification.show(
                "Error importing cost file: " + e.getMessage(),
                5000,
                Notification.Position.TOP_CENTER
            ).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void showDeleteConfirmation(ImportSummary importSummary) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Delete Import?");

        Paragraph message = new Paragraph(
            String.format("Are you sure you want to delete the import of \"%s\"? " +
                "This will remove the import record (but not the actual data).",
                importSummary.getFilename())
        );
        dialog.add(message);

        Button deleteButton = new Button("Delete", event -> {
            try {
                pricingImportService.deleteImportedFile(importSummary.getFilename());
                refreshGrid();
                dialog.close();

                Notification.show(
                    "Import deleted successfully",
                    3000,
                    Notification.Position.TOP_CENTER
                ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            } catch (Exception e) {
                Notification.show(
                    "Error deleting import: " + e.getMessage(),
                    5000,
                    Notification.Position.TOP_CENTER
                ).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        deleteButton.getStyle().set("margin-right", "auto");

        Button cancelButton = new Button("Cancel", event -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(deleteButton, cancelButton);
        dialog.open();
    }

    private void importFiles() {
        try {
            // Get list of files before import
            Set<String> filesToImport = new HashSet<>(pricingImportService.getUploadedFileNames());

            // Import files one by one to check each for zero amounts
            List<ImportSummary> importSummaries = new ArrayList<>();
            int totalRecords = 0;

            for (String filename : filesToImport) {
                try {
                    // Get the file and import it
                    File file = pricingImportService.getUploadedFile(filename);
                    if (file != null) {
                        try (FileInputStream fis = new FileInputStream(file)) {
                            ImportSummary summary = pricingImportService.importFromInputStream(fis, filename);
                            importSummaries.add(summary);
                            totalRecords += summary.getRecordCount();

                            // Check for zero-amount items immediately after successful import
                            checkAndShowZeroAmountItems(summary.getImportId(), filename);
                        }
                        // Remove from uploaded files after successful import
                        pricingImportService.removeUploadedFile(filename);
                    }
                } catch (DuplicateImportException e) {
                    // Show error for this specific file but continue with others
                    showDuplicateErrorDialog(e);
                    log.error("Duplicate import detected for file {}: {}", filename, e.getMessage());
                    // Don't remove from uploaded files so user can decide what to do
                }
            }

            upload.clearFileList();
            updateUploadedFilesList();
            refreshGrid();

            if (totalRecords > 0) {
                Notification.show(
                    String.format("Successfully imported %d records from %d file(s)",
                        totalRecords, importSummaries.size()),
                    5000,
                    Notification.Position.TOP_CENTER
                ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }

        } catch (Exception e) {
            Notification.show(
                "Error importing files: " + e.getMessage(),
                0,
                Notification.Position.BOTTOM_CENTER
            ).addThemeVariants(NotificationVariant.LUMO_ERROR);
            log.error("Error importing files", e);
        }
    }


    private void clearAllFiles() {
        pricingImportService.getUploadedFileNames().forEach(
                pricingImportService::removeUploadedFile
        );
        upload.clearFileList();
        updateUploadedFilesList();

        Notification.show(
            "All uploaded files cleared",
            3000,
            Notification.Position.TOP_CENTER
        ).addThemeVariants(NotificationVariant.LUMO_CONTRAST);
    }

    private void updateUploadedFilesList() {
        var fileNames = pricingImportService.getUploadedFileNames();
        if (fileNames.isEmpty()) {
            uploadedFilesList.setText("No files uploaded");
            uploadedFilesList.getStyle().set("color", "var(--lumo-contrast-60pct)");
        } else {
            uploadedFilesList.setText("Files ready for import: " + String.join(", ", fileNames));
            uploadedFilesList.getStyle().set("color", "var(--lumo-contrast-90pct)");
        }
    }

    private void refreshGrid() {
        if(importGrid != null) {
            importGrid.setItems(pricingImportService.getAllImportSummaries());
        }
        if(costImportGrid != null) {
            costImportGrid.setItems(productCostImportService.getAllCostImportSummaries());
        }
    }

    /**
     * Display detailed error dialog for duplicate imports
     */
    private void showDuplicateErrorDialog(DuplicateImportException exception) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Duplicate Records Detected");
        dialog.setWidth("800px");
        dialog.setMaxHeight("80vh");

        // Create scrollable content area
        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);

        // Error summary
        Paragraph summary = new Paragraph(exception.getMessage());
        summary.getStyle()
            .set("font-weight", "bold")
            .set("color", "var(--lumo-error-text-color)")
            .set("margin-bottom", "var(--lumo-space-m)");

        // Explanation
        Paragraph explanation = new Paragraph(
            "The following records already exist in the database. " +
            "No data has been imported to maintain data integrity."
        );
        explanation.getStyle().set("margin-bottom", "var(--lumo-space-m)");

        content.add(summary, explanation);

        // Duplicate details in a scrollable area
        if (exception.getDuplicateDetails() != null && !exception.getDuplicateDetails().isEmpty()) {
            Div duplicatesContainer = new Div();
            duplicatesContainer.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("overflow-y", "auto")
                .set("max-height", "400px");

            H4 detailsTitle = new H4("Duplicate Records:");
            detailsTitle.getStyle().set("margin-top", "0");
            duplicatesContainer.add(detailsTitle);

            // Limit display to first 50 duplicates to avoid overwhelming the UI
            int displayCount = Math.min(50, exception.getDuplicateDetails().size());
            for (int i = 0; i < displayCount; i++) {
                Paragraph item = new Paragraph("• " + exception.getDuplicateDetails().get(i));
                item.getStyle()
                    .set("margin", "var(--lumo-space-xs) 0")
                    .set("font-family", "monospace")
                    .set("font-size", "var(--lumo-font-size-s)");
                duplicatesContainer.add(item);
            }

            if (exception.getDuplicateDetails().size() > 50) {
                Paragraph more = new Paragraph(
                    String.format("... and %d more duplicate(s)", exception.getDuplicateDetails().size() - 50)
                );
                more.getStyle()
                    .set("font-style", "italic")
                    .set("color", "var(--lumo-contrast-60pct)");
                duplicatesContainer.add(more);
            }

            content.add(duplicatesContainer);
        }

        // Recommendation
        Paragraph recommendation = new Paragraph(
            "To import new data, please ensure you're not re-importing files that have already been processed."
        );
        recommendation.getStyle()
            .set("margin-top", "var(--lumo-space-m)")
            .set("font-style", "italic");

        content.add(recommendation);
        dialog.add(content);

        // Close button
        Button closeButton = new Button("Close", event -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(closeButton);

        dialog.open();
    }

    private void recalculateAllRatings() {
        // Use synchronous version so we can show result to user
        int count = customerRatingService.recalculateAndSaveAllCustomerRatingsSync();

        Notification notification = Notification.show(
                String.format("Successfully recalculated ratings for %d customers", count),
                5000,
                Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    /**
     * Check for zero-amount items and show dialog if any found
     *
     * @param importId The import ID to check
     * @param filename The imported filename
     */
    private void checkAndShowZeroAmountItems(Long importId, String filename) {
        List<ImportedLineItem> zeroAmountItems = pricingImportService.getZeroAmountItems(importId);

        if (!zeroAmountItems.isEmpty()) {
            showZeroAmountItemsDialog(zeroAmountItems, filename);
        }
    }

    /**
     * Show dialog with zero-amount items
     *
     * @param items List of zero-amount items
     * @param filename Original import filename
     */
    private void showZeroAmountItemsDialog(List<ImportedLineItem> items, String filename) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Data Quality Warning: Zero Amount Items Detected");
        dialog.setWidth("1200px");
        dialog.setHeight("600px");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setSizeFull();
        dialogLayout.setSpacing(true);
        dialogLayout.setPadding(false);

        // Warning message
        Span warningMessage = new Span(
            "The following " + items.size() + " line item(s) were imported with zero or null amounts. " +
            "This may indicate a data quality issue in the source file."
        );
        warningMessage.getStyle()
            .set("color", "var(--lumo-warning-text-color)")
            .set("font-weight", "500")
            .set("padding", "var(--lumo-space-m)")
            .set("background-color", "var(--lumo-warning-color-10pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        // Create grid to show zero-amount items
        Grid<ImportedLineItem> grid = new Grid<>(ImportedLineItem.class, false);
        grid.setSizeFull();
        grid.setItems(items);

        grid.addColumn(ImportedLineItem::getInvoiceNumber)
            .setHeader("Invoice #")
            .setAutoWidth(true)
            .setResizable(true);

        grid.addColumn(ImportedLineItem::getTransactionDate)
            .setHeader("Date")
            .setAutoWidth(true)
            .setResizable(true);

        grid.addColumn(ImportedLineItem::getCustomerCode)
            .setHeader("Customer Code")
            .setAutoWidth(true)
            .setResizable(true);

        grid.addColumn(ImportedLineItem::getCustomerName)
            .setHeader("Customer Name")
            .setAutoWidth(true)
            .setResizable(true)
            .setFlexGrow(1);

        grid.addColumn(ImportedLineItem::getProductCode)
            .setHeader("Product Code")
            .setAutoWidth(true)
            .setResizable(true);

        grid.addColumn(ImportedLineItem::getProductDescription)
            .setHeader("Product Description")
            .setAutoWidth(true)
            .setResizable(true)
            .setFlexGrow(1);

        grid.addColumn(item -> item.getQuantity() != null ? String.format("%.2f", item.getQuantity()) : "0.00")
            .setHeader("Quantity")
            .setAutoWidth(true)
            .setResizable(true);

        grid.addColumn(item -> item.getAmount() != null ? String.format("$%.2f", item.getAmount()) : "$0.00")
            .setHeader("Amount")
            .setAutoWidth(true)
            .setResizable(true);

        grid.addColumn(item -> item.getCost() != null ? String.format("$%.2f", item.getCost()) : "$0.00")
            .setHeader("Cost")
            .setAutoWidth(true)
            .setResizable(true);

        dialogLayout.add(warningMessage, grid);

        // Buttons
        Button downloadButton = new Button("Download as Excel", new Icon(VaadinIcon.DOWNLOAD));
        downloadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        downloadButton.addClickListener(event -> downloadZeroAmountItems(items, filename));

        Button closeButton = new Button("Close", event -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout buttonLayout = new HorizontalLayout(downloadButton, closeButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();

        VerticalLayout fullLayout = new VerticalLayout(dialogLayout, buttonLayout);
        fullLayout.setSizeFull();
        fullLayout.setSpacing(true);
        fullLayout.setPadding(true);

        dialog.add(fullLayout);
        dialog.open();
    }

    /**
     * Download zero-amount items as Excel file
     *
     * @param items List of zero-amount items
     * @param originalFilename Original import filename
     */
    private void downloadZeroAmountItems(List<ImportedLineItem> items, String originalFilename) {
        try {
            byte[] excelBytes = reportExportService.generateZeroAmountItemsXLS(items, originalFilename);

            String filename = String.format("Zero_Amount_Items_%s_%s.xlsx",
                originalFilename.replace(".xlsx", "").replace(".xls", ""),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            );

            // Use modern DownloadHandler pattern (Anchor with lambda)
            Anchor downloadLink = new Anchor("", "");
            downloadLink.getElement().setAttribute("download", true);
            downloadLink.getElement().setAttribute("style", "display:none");

            downloadLink.setHref(event -> {
                event.setFileName(filename);
                event.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                event.setContentLength(excelBytes.length);

                try (OutputStream out = event.getOutputStream()) {
                    out.write(excelBytes);

                    // Show success notification (thread-safe)
                    event.getUI().access(() -> {
                        Notification.show("Excel file downloaded successfully!", 3000, Notification.Position.BOTTOM_START)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    });
                } catch (IOException e) {
                    log.error("Error writing zero-amount items Excel file", e);
                    event.getUI().access(() -> {
                        Notification.show("Error downloading file: " + e.getMessage(), 5000, Notification.Position.BOTTOM_START)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    });
                    event.getResponse().setStatus(500);
                }
            });

            add(downloadLink);
            downloadLink.getElement().callJsFunction("click");

            log.info("Generated zero-amount items Excel: {} records", items.size());

        } catch (IOException e) {
            log.error("Error generating zero-amount items Excel file", e);
            Notification.show(
                "Error generating Excel file: " + e.getMessage(),
                5000,
                Notification.Position.BOTTOM_START
            ).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
