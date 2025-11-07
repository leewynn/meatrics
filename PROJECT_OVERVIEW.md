# Meatrics - Project Overview

## Purpose
Meatrics is a pricing management and analysis system for a meat distribution business. The system allows users to import sales data, adjust pricing, analyze margins, and manage customer information.

## Technology Stack
- **Backend**: Java 21, Spring Boot 3.x
- **UI Framework**: Vaadin Flow (Java-based web UI)
- **Database**: PostgreSQL
- **Query Builder**: jOOQ (type-safe SQL)
- **Database Migrations**: Liquibase
- **Excel Processing**: Apache POI (XSSF)
- **Build Tool**: Maven

## Project Structure
```
src/main/java/com/meatrics/
├── Application.java                    # Main Spring Boot application
├── base/                              # Base infrastructure
│   ├── config/                        # Database initialization
│   └── ui/                            # Main layout, toolbars
├── pricing/                           # Core pricing domain
│   ├── ui/                            # Vaadin views
│   │   ├── PricingDataView.java      # Read-only data view (route: "")
│   │   ├── PricingSessionsView.java  # Editable pricing sessions (route: "pricing-sessions")
│   │   └── ImportPricingView.java    # Import management (route: "import-pricing")
│   ├── PricingImportService.java      # Excel import and business logic
│   ├── ImportedLineItemRepository.java # jOOQ data access for line items
│   ├── GroupedLineItemRepository.java  # jOOQ data access for grouped view
│   ├── CustomerRepository.java         # jOOQ data access for customers
│   ├── ImportedLineItem.java          # Entity for individual line items
│   ├── GroupedLineItem.java           # DTO for aggregated customer-product data (wraps VGroupedLineItemsRecord)
│   └── *.java                         # Other entity classes
├── util/                              # Utilities (jOOQ code generation, etc.)
└── generated/                         # jOOQ generated classes (auto-generated)

src/main/resources/
├── db/changelog/                      # Liquibase migrations
│   ├── db.changelog-master.xml       # Master changelog
│   └── changes/
│       ├── 001-create-staging-tables.sql
│       ├── 002-create-views.sql
│       ├── 003-create-product-cost-tables.sql
│       ├── 004-create-customer-table.sql
│       ├── 005-rename-credit-rating-to-customer-rating.sql
│       └── 006-create-grouped-line-items-view.sql
└── application.properties             # Configuration
```

## Database Schema

### Core Tables

#### `imported_line_items`
Stores sales transaction line items imported from Excel files.
- **Key fields**: customer_code, customer_name, product_code, product_description, quantity, amount, cost, invoice_number, transaction_date
- **Purpose**: Staging table for imported sales data
- **Note**: Amount can be modified in UI (transient modifications, not persisted)

#### `v_grouped_line_items` (View)
Aggregates imported_line_items by customer and product.
- **Type**: Database view (not a table)
- **Grouping**: customer_code + customer_name + product_code + product_description
- **Aggregated fields**: total_quantity, total_amount, total_cost (SUM of respective fields)
- **Purpose**: Efficient server-side aggregation for Pricing Sessions view
- **Note**: Does not include transaction_date (use base table query with GROUP BY for date filtering)

#### `import_summary`
Tracks import operations for sales data.
- **Key fields**: filename, import_date, record_count, status
- **Purpose**: Audit trail for imports

#### `product_costs`
Master data for products with cost and pricing information.
- **Key fields**: product_code (unique), description, standard_cost, latest_cost, average_cost, supplier_cost, sell_price_1 through sell_price_10
- **Primary cost field**: `standard_cost` (Column Z/25 from Excel)
- **Strategy**: UPSERT on duplicate product_code - safe replacement of old data
- **Purpose**: Reference data for product costs and sell prices

#### `cost_import_summary`
Tracks import operations for product cost data.
- **Key fields**: filename, import_date, total_products, active_products, products_with_cost, import_status
- **Purpose**: Audit trail for product cost imports

#### `customers`
Master data for customers.
- **Key fields**: customer_code (unique), customer_name, customer_rating, notes, created_date, modified_date
- **Strategy**: Auto-created during pricing data import, editable via UI
- **Purpose**: Centralized customer information and rating management

### Important Notes
- All monetary values use `DECIMAL(12,2)` for precision
- Foreign keys are NOT used between imported_line_items and customers - natural key (customer_code) linking
- modified_date updates handled in application code (no DB triggers)

## Key Features Implemented

### 1. Pricing Data Import (ImportPricingView)
- **Location**: Route "import-pricing", Menu order 2
- **Layout**: Side-by-side sections for pricing data and product cost imports
- **Pricing Import Section**:
  - Drag-and-drop Excel file upload
  - Multiple file staging before import
  - List of uploaded files with remove option
  - Import button processes all staged files
  - Excel parsing starts at row 6, handles customer code/name extraction
  - Statistics display below drop area
- **Cost Import Section**:
  - Separate drag-and-drop for product cost files
  - Current products statistics (total, active, with cost)
  - Statistics displayed BELOW drop area (user preference)
  - UPSERT strategy for safe data replacement

### 2. Pricing Data View (PricingDataView)
- **Location**: Route "" (root/default page), Menu order 0
- **Purpose**: Read-only view of imported sales data
- **Features**:
  - Two-tier filtering system:
    - **Primary**: Date range filter (creates backing list)
    - **Secondary**: Customer name + product filters (work on backing list)
  - Grid with reorderable columns
  - Collapsible column visibility toggles
  - Footer row showing totals: quantity, cost, amount, gross profit (with %)
  - **Clickable customer names**: Opens edit dialog for customer rating and notes
  - **Customer Rating column**: Displays calculated rating for each customer
  - Calculated columns: Unit Sell Price, Unit Cost Price

### 3. Pricing Sessions View (PricingSessionsView)
- **Location**: Route "pricing-sessions", Menu order 1
- **Purpose**: Editable pricing view for price adjustments
- **Data Model**: Uses **GroupedLineItem** (aggregated by customer + product)
  - Unlike PricingDataView which shows individual transactions
  - Each row represents the sum of all transactions for a customer-product combination
  - Data sourced from `v_grouped_line_items` database view (efficient server-side aggregation)
  - For date-filtered queries: queries `imported_line_items` with WHERE + GROUP BY
  - Removes invoice-specific fields (invoice number, transaction date, outstanding amount)
  - Shows aggregated totals: totalQuantity, totalAmount, totalCost
- **Features**:
  - Two-tier filtering system (same as PricingDataView):
    - **Primary**: Date range filter (creates backing list of grouped items)
    - **Secondary**: Customer name + product filters (work on backing list)
  - Grid with reorderable columns
  - Collapsible column visibility toggles
  - Footer row showing totals: quantity, cost, amount, gross profit (with %)
  - **Clickable Unit Sell Price**: Opens dialog to adjust prices
  - Price adjustment dialog shows:
    - Product code, description, unit cost price (read-only)
    - Original unit sell price (read-only)
    - Add to unit sell price field (with auto-calculation)
    - New unit sell price field
    - Apply scope checkboxes:
      - This customer-product combination only (default)
      - All shown records
      - All products in date range
  - Amount calculation: new_unit_sell_price × item.totalQuantity (for grouped item)
  - Green highlighting for modified amounts and gross profit
  - **Undo button**: Single-level undo for last price change
  - **Clickable customer names**: Opens edit dialog for customer rating and notes
  - **Customer Rating column**: Displays calculated rating for each customer
  - **Important**: Changes are transient (in-memory), NOT saved to database

### 4. Customer Management
- Auto-created during pricing data import (extracts unique customer_code/name pairs)
- Customer ratings auto-calculated and stored after each import
- Editable via clickable customer names in both PricingDataView and PricingSessionsView
- Edit dialog fields:
  - Customer code (read-only)
  - Customer name (read-only)
  - Customer rating (stored from last calculation, editable)
    - Format: `original: 23 | modified: 234 | claude: 223`
    - Auto-updates after import or manual recalculation
    - Helper text: "Auto-calculated during import. Use 'Recalculate All Ratings' button to refresh."
    - Can be manually overridden if needed
  - Notes (editable, text area)

## Important Patterns & Conventions

### 1. Backing List Pattern
Used in both PricingDataView and PricingSessionsView:

**PricingDataView** (individual line items):
```java
private List<ImportedLineItem> backingList = new ArrayList<>();

// Date filter updates backing list
private void applyFilter() {
    backingList = pricingImportService.getLineItemsByDateRange(startDate, endDate);
    applySecondaryFilters();
}

// Secondary filters work on backing list
private void applySecondaryFilters() {
    List<ImportedLineItem> filtered = backingList.stream()
        .filter(/* customer and product filters */)
        .collect(Collectors.toList());
    dataGrid.setItems(filtered);
}
```

**PricingSessionsView** (grouped line items from database view):
```java
private List<GroupedLineItem> backingList = new ArrayList<>();

// Date filter updates backing list with grouped data (from DB view or query)
private void applyFilter() {
    // Uses v_grouped_line_items view or queries with GROUP BY
    backingList = pricingImportService.getGroupedLineItemsByDateRange(startDate, endDate);
    applySecondaryFilters();
}

// Secondary filters work on backing list (same pattern)
private void applySecondaryFilters() {
    List<GroupedLineItem> filtered = backingList.stream()
        .filter(/* customer and product filters */)
        .collect(Collectors.toList());
    dataGrid.setItems(filtered);
}
```

### 2. BigDecimal for Financial Calculations
**Always use BigDecimal** for money/price calculations to avoid floating-point precision errors:
```java
BigDecimal result = BigDecimal.valueOf(originalPrice)
    .add(BigDecimal.valueOf(adjustment))
    .setScale(2, RoundingMode.HALF_UP);
```

### 3. Transient Fields for UI State
Both ImportedLineItem and GroupedLineItem use transient fields to track modifications without database persistence:
```java
private transient boolean amountModified = false;
private transient BigDecimal originalAmount = null;

public void setTotalAmount(BigDecimal amount) {
    if (originalAmount == null && this.totalAmount != null) {
        originalAmount = this.totalAmount;  // Capture original on first change
    }
    this.totalAmount = amount;
}
```

### 4. Database Views for Aggregation
Use database views for complex aggregations instead of Java HashMap grouping:
```java
// Repository queries the view
public List<VGroupedLineItemsRecord> findAll() {
    return dsl.selectFrom(V_GROUPED_LINE_ITEMS).fetch();
}

// For date filtering, query base table with GROUP BY
public List<VGroupedLineItemsRecord> findByDateRange(LocalDate start, LocalDate end) {
    return dsl.select(fields...)
        .from(IMPORTED_LINE_ITEMS)
        .where(TRANSACTION_DATE.between(start, end))
        .groupBy(CUSTOMER_CODE, PRODUCT_CODE, ...)
        .fetchInto(VGroupedLineItemsRecord.class);
}

// Service converts records to DTOs
public List<GroupedLineItem> getGroupedLineItems() {
    return repository.findAll().stream()
        .map(GroupedLineItem::fromRecord)
        .toList();
}
```

### 5. UPSERT Pattern (jOOQ)
Safe data replacement without risk of duplicates:
```java
dsl.insertInto(TABLE)
    .set(TABLE.FIELD, value)
    .onDuplicateKeyUpdate()
    .set(TABLE.FIELD, value)
    .execute();
```

### 6. ComponentRenderer for Custom Grid Cells
Used for clickable buttons in grids:
```java
grid.addColumn(new ComponentRenderer<>(item -> {
    Button button = new Button(item.getCustomerName());
    button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
    button.addClickListener(e -> openDialog(item));
    return button;
}))
.setComparator(ImportedLineItem::getCustomerName);  // Important for sorting
```

### 7. jOOQ Code Generation Workflow

#### Adding New Tables
When database schema changes:
1. Update/create Liquibase changelog SQL file
2. Add to `db.changelog-master.xml`
3. Start application (Liquibase runs migrations)
4. Run `mvn jooq-codegen:generate` to generate jOOQ classes
5. Create Repository classes that use generated tables
See `JOOQ_GENERATION.md` for details.

#### Renaming Columns (Two-Phase Approach)
**Phase 1 - Add New Column:**
1. Create migration to ADD new column (don't drop old yet)
2. Restart server (migration runs)
3. Generate jOOQ (now has both old and new columns)
4. Update entity class to use new field name
5. Update repository to use new jOOQ field
6. Update UI labels
7. Optional: Copy data from old to new column if needed

**Phase 2 - Drop Old Column (later):**
1. Create migration to DROP old column
2. Restart server
3. Generate jOOQ
4. Done

This avoids the chicken-and-egg problem of code referencing jOOQ fields that don't exist yet.

### 8. No Database Triggers
Previous attempt to use PostgreSQL triggers failed with Liquibase. Strategy: Handle all timestamp updates in application code.

**Note on current rename:** The credit_rating → customer_rating rename was done with ALTER COLUMN which created this workflow issue. In future, use the two-phase ADD/DROP approach above.

## Excel File Formats

### Pricing Data Import
- **Start Row**: 6 (skips headers)
- **Customer Detection**: Numeric value in column 0 = customer code/name row
- **Line Item Rows**: Non-numeric in column 0 = product line
- **End Marker**: "9999999" in column 0
- **Skip Rows**: Empty, null, or "SubTotal" in column 0
- **Column Mapping**:
  - 0: Product Code
  - 1: Product Description
  - 2: Quantity
  - 3: Amount
  - 4: Cost
  - 7: Invoice Number
  - 8: Transaction Date
  - 9-11: Ref1, Ref2, Ref3
  - 14: Outstanding Amount

### Product Cost Import
- **Start Row**: 1 (row 0 is header)
- **Primary Cost Field**: Column 25 (Z) - `stdcost`
- **Column Mapping**:
  - 0: Product Code (Stockcode)
  - 1: Description
  - 2-11: Sell Price 1-10
  - 12: Latest Cost (M)
  - 13: Average Cost (N)
  - 14-16: Min Stock, Max Stock, Bin Code
  - 19-20: Sales GL Code, Purchase GL Code
  - 21: Is Active (Y/N)
  - 22-23: Weight, Cubic
  - 24: Unit of Measure (Pack)
  - 25: **Standard Cost (PRIMARY)** (Z)
  - 26-27: Sales Tax Rate, Purchase Tax Rate
  - 28: COS GL Code
  - 29: Supplier Cost
  - 32-36: Primary Group, Secondary Group, Product Class, Tertiary Group, Supplier Name

## Calculated Fields

### Unit Sell Price
```java
unit_sell_price = amount / quantity
// Display: "N/A" when quantity is zero
// Format: "$%.2f"
```

### Unit Cost Price
```java
unit_cost_price = cost / quantity
// Display: "N/A" when quantity is zero
// Format: "$%.2f"
```

### Gross Profit
```java
gross_profit = amount - cost
gross_profit_percentage = (gross_profit / amount) × 100
```

## User Experience Decisions

### Key User Preferences
1. **Statistics positioning**: Cost import statistics appear BELOW drop area (not above)
2. **Column visibility**: Text-based toggle (not icon), smaller font size
3. **Price editing**: Via Unit Sell Price (not Amount), with quantity-aware calculation
4. **Green highlighting**: Modified amounts and gross profit show in green
5. **Original value preservation**: Show database value in edit dialog, not previous modification
6. **No emoji**: User prefers no emoji in files unless explicitly requested

### Design Philosophy
- Editable views are separate from read-only views
- Modifications are transient (not saved to database) for "what-if" analysis
- Multi-level filtering with clear hierarchy (date → customer/product)
- Single-level undo for safety
- Natural key linking (no foreign keys) for flexibility

## Future Direction

### Recently Implemented Features

#### 1. Customer Rating System ✅
Implemented three customer rating algorithms with automatic calculation and background processing:

**Algorithms:**
- **Original**: `sqrt((amount / 1000 × GP%) × 100)` - User's proposed formula
- **Modified**: `(amount / 1000) + (GP% × 10)` - Additive version to address multiplicative zero problem
- **Claude**: `(Gross_Profit_Dollars × 0.7) + (Revenue_Percentile × 0.3)` - Weighted approach prioritizing gross profit
- All three calculated and stored in format: `original: 23 | modified: 234 | claude: 223`

**Calculation Triggers:**
- **Automatic**: After every pricing data import (runs in background via @Async)
- **Manual**: "Recalculate All Customer Ratings" button in Import Pricing view
- Ratings stored in database for fast display
- Customer edit dialog shows stored rating (editable)

**Implementation:**
- `CustomerRatingService` - Three algorithm implementations + batch calculation methods
- `@Async` annotation for background processing (configured via `@EnableAsync` in Application.java)
- Logging shows progress: "Updated X of Y customers in Zms"
- Import view button uses synchronous version to show immediate feedback notification

**Potential Future Enhancements:**
- Add behavioral adjustments to Claude algorithm:
  - Payment behavior: ×0.9 if avg days outstanding >60
  - Consistency: ×1.1 if orders every period
  - Growth: ×1.1 if YoY growth >20%
- Store each rating separately for historical analysis
- Add visualization/comparison charts

### Discussed Features (Not Yet Implemented)

#### 1. Product Cost Integration
- Join imported line items with product_costs table
- Compare actual sell price vs. standard cost
- Margin analysis per product
- Identify pricing anomalies

#### 2. Persistence of Price Changes
- Currently all modifications are transient
- Future: Save pricing sessions, export adjusted prices

#### 3. Reporting & Analytics
- Customer profitability reports
- Product margin analysis
- Trend analysis over time periods

## Known Limitations

1. **No Price Persistence**: All price changes in Pricing Sessions are in-memory only
2. **Single Undo Level**: Can only undo the most recent price change
3. **No Batch Operations**: Price changes applied one dialog at a time
4. **Manual Import Triggering**: No automated/scheduled imports
5. **No User Authentication**: System is currently single-user
6. **No Export Functionality**: Cannot export modified pricing data
7. **No Audit Trail**: Changes to customers/prices not tracked historically

## Development Notes

### Important: Maven Command Execution
**DO NOT run Maven commands** - all `mvn` commands should be executed by the developer manually. This includes:
- `mvn clean compile`
- `mvn spring-boot:run`
- `mvn liquibase:update -Pliquibase`
- `mvn generate-sources -Pjooq`

Code changes can be made by AI agents, but compilation and execution is done by the developer.

### Common Tasks

#### Adding a New Table
1. Create SQL migration in `db/changelog/changes/`
2. Add to `db.changelog-master.xml`
3. Run application (migrations run automatically)
4. Generate jOOQ: `mvn jooq-codegen:generate`
5. Create entity class (POJO with getters/setters)
6. Create repository class using jOOQ DSL
7. Add service layer if needed
8. Create/update UI views

#### Adding a New View
1. Create class extending `Main` (or other layout)
2. Add `@Route("path")` annotation
3. Add `@PageTitle("Title")` annotation
4. Add `@Menu(order = X, icon = "vaadin:icon", title = "Menu Title")` for menu
5. Inject required services via constructor
6. Build UI in constructor using Vaadin components

#### Modifying Excel Import Logic
- **Pricing data**: Update `PricingImportService.parseRowToLineItem()`
- **Product costs**: Update `ProductCostImportService.parseProductRow()`
- Always test with user's actual Excel files (samples in `sample_file/`)

### Code Style Preferences
- Use BigDecimal for all financial calculations
- Prefer constructor injection over field injection
- Repository methods return Optional for single results, List for multiple
- Use transient fields for UI-only state
- Format money as "$%.2f"
- Keep view classes focused on UI, move business logic to services

### Testing Approach
- Primary testing: Run application and test in browser
- Key test scenarios:
  - Import Excel files from `sample_file/` directory
  - Apply filters and verify backing list behavior
  - Modify prices and verify green highlighting
  - Test undo functionality
  - Click customer names and edit credit rating/notes

## Configuration

### Database Connection
See `src/main/resources/application.properties`:
- Default: PostgreSQL on localhost:5432
- Database name: `meatrics`
- Liquibase runs on startup, creates schema if needed

### Application Startup
```bash
mvn spring-boot:run
```
Access at: http://localhost:8080

## Key Files to Review for Context

1. **PROJECT_OVERVIEW.md** (this file) - Start here
2. **JOOQ_GENERATION.md** - jOOQ workflow
3. **db/changelog/db.changelog-master.xml** - Database schema evolution
4. **db/changelog/changes/006-create-grouped-line-items-view.sql** - Grouping view definition
5. **pricing/ui/PricingSessionsView.java** - Most complex view, shows grouping and editing patterns
6. **pricing/PricingImportService.java** - Excel parsing logic
7. **pricing/GroupedLineItemRepository.java** - Repository for querying aggregated data
8. **pricing/ImportedLineItem.java** - Core entity with transient fields (individual line items)
9. **pricing/GroupedLineItem.java** - DTO wrapping VGroupedLineItemsRecord with UI state
10. **pricing/CustomerRatingService.java** - Customer rating algorithms (3 implementations)

## Glossary

- **Backing List**: Primary filtered dataset (usually date-filtered)
- **Secondary Filters**: Filters applied on top of backing list
- **Transient Field**: Java field not persisted to database (in-memory only)
- **UPSERT**: Insert or update if exists (conflict resolution)
- **jOOQ**: Java Object Oriented Querying - type-safe SQL builder
- **Liquibase**: Database migration/version control tool
- **Unit Sell Price**: Price per unit (amount ÷ quantity)
- **Unit Cost Price**: Cost per unit (cost ÷ quantity)
- **GP/Gross Profit**: Revenue minus cost (amount - cost)
- **GP%/Gross Profit Percentage**: (Gross Profit ÷ Revenue) × 100
- **Revenue Percentile**: Customer's ranking (0-100) based on total revenue compared to all customers
- **Customer Rating**: Calculated score using one of three algorithms to rank customer value
- **Grouped Line Item**: Aggregated sales data for a unique customer-product combination (sum of all transactions)
- **Grouping Key**: Composite identifier (customer_code + product_code) used to group line items

## Contact / Domain Expert Notes

- **Domain**: Meat distribution business
- **Excel files**: Exported from existing business system
- **User preferences**: Practical, data-focused, prefers simple UI without embellishments
- **Currency**: Uses dollar formatting ($)
- **Date format**: dd/MM/yyyy in Excel imports
- **Primary cost metric**: Standard cost (stdcost), not latest cost

---

**Last Updated**: 2025-11-08
**Project Status**: Active development, core features implemented, customer rating system implemented, pricing sessions grouping implemented, analytics features in planning
