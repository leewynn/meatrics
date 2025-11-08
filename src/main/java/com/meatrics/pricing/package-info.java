/**
 * Pricing data import and management system.
 *
 * <h2>Overview</h2>
 * This package handles the import of pricing data from Excel files using a staging table approach.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li><strong>Staging Table Pattern</strong> - All imported data is stored in {@code imported_line_items}
 *       with full traceability to source files</li>
 *   <li><strong>Database Views</strong> - Virtual tables ({@code v_customers}, {@code v_products},
 *       {@code v_invoices}) provide clean access to aggregated data</li>
 *   <li><strong>jOOQ Integration</strong> - Type-safe database access with generated code</li>
 * </ul>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.meatrics.pricing.ImportedLineItem} - Raw data entity from Excel imports</li>
 *   <li>{@link com.meatrics.pricing.ImportSummary} - Tracks import metadata and status</li>
 *   <li>{@link com.meatrics.pricing.PricingImportService} - Orchestrates file uploads and imports</li>
 *   <li>{@link com.meatrics.pricing.ui.ImportPricingView} - User interface for file upload and import history</li>
 * </ul>
 *
 * <h2>Import Workflow</h2>
 * <ol>
 *   <li>User uploads Excel files via drag-and-drop UI</li>
 *   <li>Files are stored temporarily in memory</li>
 *   <li>User triggers import via "Import All Files" button</li>
 *   <li>Service parses Excel files and creates {@code ImportedLineItem} records</li>
 *   <li>{@code ImportSummary} records track each import with metadata</li>
 *   <li>Database views automatically reflect new data</li>
 * </ol>
 *
 * <h2>Data Model</h2>
 * <pre>
 * import_summary (1) ----< (N) imported_line_items
 *                               |
 *                               +---> v_customers (view)
 *                               +---> v_products (view)
 *                               +---> v_invoices (view)
 *                               +---> v_invoice_line_items (view)
 * </pre>
 *
 * <h2>Benefits</h2>
 * <ul>
 *   <li>Complete audit trail - every record knows its source file</li>
 *   <li>Easy deletion - remove all records from a bad import</li>
 *   <li>Flexible reporting - create new views without modifying raw data</li>
 *   <li>No data loss - all Excel columns preserved in staging table</li>
 * </ul>
 *
 * @since 1.0
 */
package com.meatrics.pricing;
