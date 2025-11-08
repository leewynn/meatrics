# Meatrics - Quick Handoff Guide for Claude

**Last Updated**: 2025-11-09

This document provides a quick-start guide for future Claude AI sessions working on the Meatrics project. For comprehensive details, see PROJECT_OVERVIEW.md.

## What This Application Does

Meatrics is a **meat distribution pricing and analysis tool** that helps a business:
1. Import sales transaction data from Excel files
2. View and analyze pricing, costs, and margins
3. Create "what-if" pricing scenarios using sessions
4. Rate customers based on profitability metrics
5. Generate Excel reports for cost analysis

**Key Point**: This is a pricing ANALYSIS tool, not a production pricing system. Changes are for analysis and saved in sessions, not pushed back to the source system.

## Technology Stack (Critical to Know)

- **Backend**: Java 21, Spring Boot 3.x
- **UI**: Vaadin Flow (server-side Java UI framework - NO JavaScript/React/Vue)
- **Database**: PostgreSQL with Liquibase migrations
- **Query Builder**: jOOQ (type-safe SQL, generates classes from schema)
- **Excel**: Apache POI for reading/writing .xlsx files
- **Build**: Maven

**Important**: This is NOT a REST API + React app. It's a traditional server-side MVC app where UI is written in Java using Vaadin components.

## Quick Start Commands

```bash
# Start application (from project root)
./mvnw spring-boot:run

# Access at: http://localhost:8080

# Regenerate jOOQ classes after schema changes
mvn jooq-codegen:generate
```

**Note**: DO NOT run Maven commands as AI - inform user to run them manually.

## Application Structure - The Four Views

The application has 4 main views accessible via menu:

### 1. Pricing Data (Route: "" - default)
**What**: Read-only view of imported sales transactions
**File**: `src/main/java/com/meatrics/pricing/ui/PricingDataView.java`
**Data**: Shows individual line items from `imported_line_items` table
**Key Features**:
- Date range filtering + customer/product filters
- Clickable customer names → edit customer dialog
- Footer totals for quantity, cost, amount, GP%
- Column visibility toggles (saved to localStorage)

### 2. Pricing Sessions (Route: "pricing-sessions")
**What**: Editable pricing view with session save/load
**File**: `src/main/java/com/meatrics/pricing/ui/PricingSessionsView.java`
**Data**: Shows GROUPED items (aggregated by customer + product) from `v_grouped_line_items` view
**Key Features**:
- Edit unit sell prices (affects amount calculation)
- Save sessions to database for later comparison
- Load/delete sessions with unsaved changes protection
- Single-level undo for price changes
- Orange title when unsaved changes exist
- Green highlighting for modified values

**Critical Distinction**: This view shows AGGREGATED data, not individual transactions. Each row is the sum of all transactions for a customer-product pair.

### 3. Import Pricing (Route: "import-pricing")
**What**: Excel file upload for sales data and product costs
**File**: `src/main/java/com/meatrics/pricing/ui/ImportPricingView.java`
**Features**: Drag-and-drop upload, staging area, UPSERT for costs

### 4. Reports (Route: "reports")
**What**: Generate Excel reports (Customer Rating, Cost Analysis)
**File**: `src/main/java/com/meatrics/pricing/ui/ReportsView.java`
**Key Features**:
- Tabbed interface
- Modern DownloadHandler pattern for Excel exports
- Customer rating shows 3 algorithm results
- Cost report shows items where cost < STDCOST

## Critical Patterns You MUST Understand

### 1. Backing List Pattern (Used in Both Main Views)

Both PricingDataView and PricingSessionsView use a two-tier filtering system:

```java
// Primary filter: Updates backing list from database
private List<ImportedLineItem> backingList = new ArrayList<>();

private void applyFilter() {
    // Query database with date range
    backingList = service.getItemsByDateRange(startDate, endDate);
    applySecondaryFilters(); // Then apply customer/product filters
}

// Secondary filters: Work on the backing list in memory
private void applySecondaryFilters() {
    List<ImportedLineItem> filtered = backingList.stream()
        .filter(/* customer and product filters */)
        .collect(Collectors.toList());

    dataGrid.setItems(filtered);
    updateFooterTotals(filtered);
}
```

**Why This Matters**:
- Date filter = expensive DB query
- Customer/product filters = cheap in-memory filtering
- Always operate on the backing list for secondary filters
- Don't re-query DB for every customer name change

### 2. AbstractGridView Base Class

Both main views extend `AbstractGridView` for column visibility:

```java
public class PricingDataView extends AbstractGridView {
    private static final String STORAGE_PREFIX = "PricingDataView-column-";

    @Override
    protected String getStoragePrefix() {
        return STORAGE_PREFIX;
    }

    // Save when checkbox changes
    checkbox.addValueChangeListener(e -> {
        grid.getColumnByKey("customerName").setVisible(e.getValue());
        saveColumnVisibility("customerName", e.getValue());
    });

    // Restore on init
    restoreColumn("customerName", customerNameCheck);
}
```

**Location**: `/mnt/d/dev/meatrics/src/main/java/com/meatrics/base/ui/AbstractGridView.java`

### 3. Transient Fields for UI State

Entities use transient fields to track modifications without DB persistence:

```java
public class GroupedLineItem {
    private BigDecimal totalAmount;
    private transient BigDecimal originalAmount = null;  // Not persisted
    private transient boolean amountModified = false;    // Not persisted

    public void setTotalAmount(BigDecimal amount) {
        if (originalAmount == null && this.totalAmount != null) {
            originalAmount = this.totalAmount;  // Capture first
        }
        this.totalAmount = amount;
    }

    public boolean isAmountModified() {
        return amountModified;
    }
}
```

**Why**: Allows price modifications in UI without polluting database entities.

### 4. Modern Download Handler Pattern (Vaadin)

**Do NOT use StreamResource** - it's deprecated. Use Anchor with DownloadHandler:

```java
// Generate bytes
byte[] excelBytes = reportExportService.generateReport(...);

// Create invisible Anchor
Anchor downloadLink = new Anchor(event -> {
    try {
        event.setFileName("Report.xlsx");
        event.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        event.setContentLength(excelBytes.length);

        try (OutputStream out = event.getOutputStream()) {
            out.write(excelBytes);
        }

        event.getUI().access(() ->
            showSuccessNotification("Downloaded!"));
    } catch (IOException e) {
        event.getResponse().setStatus(500);
        event.getUI().access(() ->
            showErrorNotification("Error: " + e.getMessage()));
    }
}, "");

downloadLink.getElement().setAttribute("style", "display: none;");
add(downloadLink);
downloadLink.getElement().callJsFunction("click");
```

**Used in**: ReportsView for both Customer Rating and Cost Report exports.

### 5. BigDecimal for Money (ALWAYS)

**NEVER use double or float for financial calculations**:

```java
// CORRECT
BigDecimal price = BigDecimal.valueOf(10.50);
BigDecimal quantity = BigDecimal.valueOf(3);
BigDecimal total = price.multiply(quantity)
    .setScale(2, RoundingMode.HALF_UP);

// WRONG - will have rounding errors
double total = 10.50 * 3;
```

### 6. jOOQ Code Generation Workflow

When database schema changes:

1. Create/modify SQL file in `src/main/resources/db/changelog/changes/`
2. Add entry to `db.changelog-master.xml`
3. Start application (Liquibase auto-runs migrations)
4. Run `mvn jooq-codegen:generate` to create Java classes
5. Use generated classes in repositories

**Generated code location**: `src/main/java/com/meatrics/generated/`

**Important**: Generated code IS committed to git (tracked).

## Database Schema Quick Reference

**Key Tables:**
- `imported_line_items` - Individual sales transactions (from Excel imports)
- `v_grouped_line_items` - VIEW aggregating line items by customer + product
- `customers` - Customer master data with ratings
- `product_costs` - Product cost and pricing data (STDCOST is primary field)
- `import_summary` - Audit trail of sales imports
- `pricing_sessions` - Saved pricing scenarios
- `pricing_session_line_items` - Line items within sessions (FK to sessions)

**No Foreign Keys** between imported_line_items and customers - uses natural key (customer_code).

**One Foreign Key**: pricing_session_line_items.session_id → pricing_sessions.id (CASCADE DELETE).

## Common Tasks - How To

### Add a New Column to Grid

```java
// In createDataGrid() method
grid.addColumn(Item::getNewField)
    .setHeader("New Field")
    .setKey("newField")  // Key for visibility toggle
    .setAutoWidth(true)
    .setResizable(true)
    .setSortable(true);

// Add checkbox in createColumnVisibilityToggles()
newFieldCheck = new Checkbox("New Field", true);
newFieldCheck.addValueChangeListener(e -> {
    dataGrid.getColumnByKey("newField").setVisible(e.getValue());
    saveColumnVisibility("newField", e.getValue());
});

// Add to checkbox layout
checkboxLayout.add(..., newFieldCheck);

// Restore in restoreColumnVisibility()
restoreColumn("newField", newFieldCheck);
```

### Add a Database Migration

```bash
# 1. Create new SQL file
touch src/main/resources/db/changelog/changes/008-your-change.sql

# 2. Write SQL
# ALTER TABLE or CREATE TABLE statements

# 3. Add to master changelog
# Edit db.changelog-master.xml, add:
# <include file="changes/008-your-change.sql" relativeToChangelogFile="true"/>

# 4. User runs application (migrations execute)

# 5. User runs: mvn jooq-codegen:generate
```

### Create a Reusable Component

See `CustomerEditDialog` as example:
- Extend `Dialog`
- Accept dependencies in constructor
- Provide `open(callback)` method for flexibility
- Keep component stateless where possible

### Handle Unsaved Changes Dialog

See `PricingSessionsView.openLoadSessionDialog()` for pattern:
- Use `ConfirmDialog` with 3 buttons
- Primary action (green): "Save & [Action]"
- Destructive action (red): "Discard & [Action]"
- Cancel: Always available

## Important Gotchas

1. **Vaadin is Server-Side**: Don't think in terms of REST APIs. UI state lives on server, sent to browser as HTML.

2. **Grid Data Refresh**: Call `grid.setItems(newList)` and `grid.getDataProvider().refreshAll()` to update.

3. **UI Thread Safety**: When updating UI from async operations, wrap in `ui.access(() -> {...})`.

4. **Date Pickers**: Set locale to `Locale.UK` for dd/MM/yyyy format (user preference).

5. **Session Management**: Sessions store SNAPSHOTS. Loading a session replaces the backing list entirely.

6. **Customer Rating**: Stored as string with format "original: X | modified: Y | claude: Z" - not separate columns.

7. **No Emoji**: User explicitly requested no emoji in code or docs.

8. **Outstanding Column**: Was removed from PricingDataView - don't re-add it.

## Current State & Known Issues

**What's Working Well:**
- All four views fully functional
- Session save/load/delete with proper UI
- Excel import and report export
- Customer rating calculations
- Column visibility persistence
- Modern download handler

**Known Limitations:**
- Single-level undo only
- Session notes field not fully integrated
- No automated imports (manual only)
- No user authentication (single-user app)
- No export back to source system

**Recent Changes:**
- Session loading dialog improved with clear action names
- DownloadHandler pattern implemented for reports
- AbstractGridView base class created
- Outstanding column removed from PricingDataView

## Testing Approach

**Primary testing**: Run app and test in browser

**Test Scenarios:**
1. Import Excel files from `sample_file/` directory
2. Apply date range filter, verify backing list updates
3. Apply customer/product filters, verify they work on backing list
4. Modify prices, verify green highlighting
5. Save session, load session, verify data restored
6. Generate reports, verify Excel downloads

**No Unit Tests**: Project focuses on integration testing via browser.

## File Locations Cheat Sheet

```
Main Views:
- /mnt/d/dev/meatrics/src/main/java/com/meatrics/pricing/ui/PricingDataView.java
- /mnt/d/dev/meatrics/src/main/java/com/meatrics/pricing/ui/PricingSessionsView.java
- /mnt/d/dev/meatrics/src/main/java/com/meatrics/pricing/ui/ReportsView.java
- /mnt/d/dev/meatrics/src/main/java/com/meatrics/pricing/ui/ImportPricingView.java

Base Classes:
- /mnt/d/dev/meatrics/src/main/java/com/meatrics/base/ui/AbstractGridView.java
- /mnt/d/dev/meatrics/src/main/java/com/meatrics/base/ui/MainLayout.java

Components:
- /mnt/d/dev/meatrics/src/main/java/com/meatrics/pricing/ui/component/CustomerEditDialog.java

Services:
- /mnt/d/dev/meatrics/src/main/java/com/meatrics/pricing/PricingImportService.java
- /mnt/d/dev/meatrics/src/main/java/com/meatrics/pricing/PricingSessionService.java
- /mnt/d/dev/meatrics/src/main/java/com/meatrics/pricing/ReportExportService.java
- /mnt/d/dev/meatrics/src/main/java/com/meatrics/pricing/CustomerRatingService.java

Database:
- /mnt/d/dev/meatrics/src/main/resources/db/changelog/db.changelog-master.xml
- /mnt/d/dev/meatrics/src/main/resources/db/changelog/changes/*.sql

Documentation:
- /mnt/d/dev/meatrics/PROJECT_OVERVIEW.md - Comprehensive documentation
- /mnt/d/dev/meatrics/HANDOFF.md - This file (quick reference)
- /mnt/d/dev/meatrics/JOOQ_GENERATION.md - jOOQ workflow
- /mnt/d/dev/meatrics/README.md - Setup and running instructions
```

## Getting Help

1. **First**: Read PROJECT_OVERVIEW.md for comprehensive details
2. **Code Examples**: Look at PricingSessionsView (most complex view)
3. **Patterns**: Review AbstractGridView and CustomerEditDialog
4. **Database**: Check migration files in db/changelog/changes/
5. **Excel**: See PricingImportService and ReportExportService

## Project Philosophy

- **Pragmatic over Perfect**: Working code over theoretical purity
- **User-Driven**: Features based on actual user workflow needs
- **Analysis Tool**: Not a production system, but a decision support tool
- **Incremental**: Add features as needed, don't over-engineer
- **Clear UX**: User explicitly prefers simple, clear interfaces

---

**Quick Question Checklist Before Starting Work:**

- [ ] Do I understand if this affects PricingDataView (individual items) or PricingSessionsView (grouped items)?
- [ ] Will this require a database migration?
- [ ] Do I need to regenerate jOOQ classes?
- [ ] Am I using BigDecimal for all financial calculations?
- [ ] Am I following the backing list pattern for filtering?
- [ ] If adding UI components, am I extending AbstractGridView if needed?
- [ ] If downloading files, am I using the DownloadHandler pattern (not StreamResource)?
- [ ] Have I checked if a reusable component already exists?

**Remember**: This is a Vaadin server-side application. Think in terms of Java components and server-side state, not REST APIs and client-side JavaScript.

Good luck! The codebase is well-organized and follows clear patterns. When in doubt, look at existing code for examples.
