# Meatrics - Pricing Analysis Application

## Overview

Meatrics is a pricing analysis and management application for meat distribution businesses. It imports sales transaction data from Excel files, provides pricing analysis tools, and enables what-if scenario planning through pricing sessions.

### Key Features

- **Excel Import**: Drag-and-drop sales data and product cost imports
- **Pricing Analysis**: View individual transactions or aggregated customer-product data
- **Pricing Sessions**: Create, save, and load pricing scenarios with modifications
- **Customer Ratings**: Automatic calculation using three different algorithms
- **Reports**: Generate Excel reports for customer ratings and cost analysis
- **Column Customization**: Reorderable columns with visibility preferences saved to browser
- PostgreSQL database with automatic schema management via Liquibase
- Type-safe database queries with jOOQ code generation

## Project Structure

The application follows a **feature-based package structure**, organizing code by functional domains:

```
src/main/java/com/meatrics/
├── Application.java              # Spring Boot main application entry point
├── base/                         # Base infrastructure
│   ├── config/                   # Database initialization
│   └── ui/                       # Main layout and base UI classes
│       ├── AbstractGridView.java # Base class for column visibility management
│       └── MainLayout.java       # Application layout with menu
├── pricing/                      # Core pricing domain
│   ├── ui/                       # Vaadin views (server-side UI)
│   │   ├── PricingDataView.java      # Read-only transaction view
│   │   ├── PricingSessionsView.java  # Editable pricing sessions
│   │   ├── ReportsView.java          # Report generation
│   │   ├── ImportPricingView.java    # Excel import
│   │   └── component/                # Reusable UI components
│   ├── *Service.java             # Business logic services
│   ├── *Repository.java          # jOOQ data access
│   └── *.java                    # Entity classes and DTOs
├── util/                         # Utilities (jOOQ code gen, Excel parsing)
└── generated/                    # jOOQ generated classes (auto-generated from DB schema)

src/main/resources/
├── db/changelog/                 # Liquibase database migrations
│   ├── db.changelog-master.xml  # Master changelog
│   └── changes/*.sql             # Individual migration files
└── application.properties        # Application configuration

sample_file/                      # Sample Excel files for testing imports
```

**Key Architectural Patterns:**
- **Feature-based packages**: `pricing` contains all pricing-related code (UI, services, repositories, entities)
- **AbstractGridView pattern**: Base class provides column visibility management across views
- **Reusable components**: `CustomerEditDialog` used by multiple views
- **Service layer**: Business logic separated from UI and data access
- **jOOQ repositories**: Type-safe SQL queries with compile-time validation

## Prerequisites

Before running the application, ensure:

1. **PostgreSQL is installed and running** on `localhost:5432`
2. **PostgreSQL credentials** match the defaults in `application.properties`:
   - Username: `postgres`
   - Password: `postgres`

You can override these using environment variables (`DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`).

## Starting in Development Mode

The application uses an **automatic build process** that:
1. Creates the PostgreSQL database if it doesn't exist
2. Runs Liquibase migrations to set up the schema
3. Generates jOOQ classes from the database
4. Compiles and runs the application

**Simply run:**

```bash
./mvnw
```

Or from your IDE, run the `Application` class.

The first build may take longer as it sets up the database and generates code.

## Using the Application

### Application Views

The application has four main views accessible via the menu:

#### 1. Pricing Data (Default View)
**Read-only view of imported sales transactions**

- Filter by date range to create a backing list
- Filter by customer name or product (works on backing list)
- View individual line items with invoice numbers and dates
- See calculated unit prices and gross profit
- Click customer names to edit customer ratings and notes
- Customize column visibility (saved to browser)

#### 2. Pricing Sessions
**Create and manage pricing scenarios**

- View aggregated data (grouped by customer + product)
- Edit unit sell prices to test different pricing strategies
- Apply price changes to single items, all shown records, or all with same product
- Save sessions with custom names for later comparison
- Load previously saved sessions
- Undo last price change
- Visual indicators: green for modified values, orange title for unsaved changes

**Important**: This view shows AGGREGATED data, not individual transactions. Changes are saved in sessions, not applied to base data.

#### 3. Import Pricing
**Import sales and cost data from Excel files**

**Pricing Data Import:**
1. Navigate to "Import Pricing" view
2. Drag and drop Excel (.xlsx) files onto the upload area
3. Multiple files can be staged before import
4. Click "Import" to process all staged files
5. Customer ratings are automatically calculated after import

**Product Cost Import:**
1. Use the separate "Product Costs" section
2. Drag and drop product cost Excel file
3. System uses UPSERT strategy (safe replacement of existing data)
4. Standard cost (STDCOST) from column Z is the primary cost field

**Excel Format Requirements:**
- **Sales Data**: Starts at row 6, numeric value in column 0 indicates customer code/name row
- **Product Costs**: Starts at row 1 (row 0 is header), STDCOST in column Z (25)
- See PROJECT_OVERVIEW.md for detailed column mappings

#### 4. Reports
**Generate Excel reports for analysis**

**Customer Rating Report:**
- Select date range
- View customer profitability with three rating algorithms
- Export to Excel with modern download handler

**Cost Report:**
- Select an import file from dropdown
- Shows line items where cost < standard cost (potential issues)
- Compare actual costs with STDCOST
- Export to Excel for further analysis

### Working with Pricing Sessions

**Creating a Session:**
1. Go to "Pricing Sessions" view
2. Apply date filter to load data
3. Modify prices as needed (click Unit Sell Price)
4. Click "Save Session" and provide a name
5. Session saved to database with all modifications

**Loading a Session:**
1. Click "Load Session" button
2. If unsaved changes exist, dialog offers three choices:
   - **Save & Load**: Save current session first, then load selected
   - **Discard & Load**: Abandon changes and load selected
   - **Cancel**: Stay in current session
3. Double-click a session or select and click "Load"

**Session Features:**
- Title shows current session name
- Orange title indicates unsaved changes
- Delete old sessions you no longer need
- Sessions capture complete snapshot of grouped line items

## Building for Production

To build the application in production mode, run:

```bash
./mvnw -Pproduction package
```

To build a Docker image, run:

```bash
docker build -t my-application:latest .
```

If you use commercial components, pass the license key as a build secret:

```bash
docker build --secret id=proKey,src=$HOME/.vaadin/proKey .
```

## Configuration

All application configuration is in `src/main/resources/application.properties`.

### Database Settings

Default PostgreSQL connection:
- **Host**: localhost
- **Port**: 5432
- **Database**: meatrics
- **Username**: postgres
- **Password**: postgres
- **Client Name**: meatrics-app

Override via environment variables:

```bash
# Linux/Mac
export DB_NAME=myapp
export DB_USERNAME=myuser
export DB_PASSWORD=mypass
./mvnw

# Windows
set DB_NAME=myapp
set DB_USERNAME=myuser
set DB_PASSWORD=mypass
mvnw.cmd
```

## Additional Documentation

For more detailed information, see:

- **PROJECT_OVERVIEW.md** - Comprehensive project documentation covering:
  - Complete database schema with all tables and views
  - Detailed feature descriptions for all four views
  - Architecture patterns (AbstractGridView, backing list pattern, etc.)
  - Important coding conventions and patterns
  - jOOQ code generation workflow
  - Excel file format specifications
  - Customer rating algorithms
  - Recent changes and known limitations

- **HANDOFF.md** - Quick reference guide for developers/AI assistants:
  - Quick start commands and file locations
  - Critical patterns explained with code examples
  - Common tasks and how to perform them
  - Important gotchas and warnings
  - Technology stack overview

- **JOOQ_GENERATION.md** - Guide for jOOQ code generation:
  - When and how to regenerate jOOQ classes
  - Database connection configuration
  - Troubleshooting tips

## Technology Resources

- [Vaadin Documentation](https://vaadin.com/docs/latest) - Server-side Java UI framework
- [jOOQ Documentation](https://www.jooq.org/doc/latest/manual/) - Type-safe SQL queries
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/) - Application framework
- [Liquibase Documentation](https://docs.liquibase.com/) - Database migration management
