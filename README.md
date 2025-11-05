# App README

## Overview

Meatrics is a sales data management application that imports and analyzes sales data from Excel files.

### Key Features

- Import sales data from Excel (.xlsx) files
- View and analyze sales records
- PostgreSQL database with automatic schema management
- Type-safe database queries with jOOQ

## Project Structure

The sources of your App have the following structure:

```
src
├── main/frontend
│   └── themes
│       └── default
│           ├── styles.css
│           └── theme.json
├── main/java/com/meatrics
│   ├── base
│   │   ├── config
│   │   │   └── DatabaseInitializer.java
│   │   └── ui
│   │       ├── component
│   │       │   └── ViewToolbar.java
│   │       └── MainLayout.java
│   ├── sales
│   │   ├── ui
│   │   │   └── SalesView.java
│   │   ├── SalesRecord.java
│   │   ├── SalesRepository.java
│   │   ├── SalesService.java
│   │   └── SalesImportService.java
│   ├── util
│   │   └── ExcelReader.java
│   └── Application.java
├── main/resources
│   └── db/changelog
│       └── changes
│           └── 001-create-sales-record-table.xml
└── sample_file
    └── [Excel files for import]
```

The main entry point into the application is `Application.java`. This class contains the `main()` method that starts up
the Spring Boot application.

The application follows a *feature-based package structure*, organizing code by *functional units*:

* The `base` package contains reusable UI components and configuration classes
* The `sales` package handles all sales data import and management functionality
* The `util` package contains utility classes for Excel file processing

The `src/main/frontend` directory contains an empty theme called `default`, based on the Lumo theme. It is activated in
the `Application` class, using the `@Theme` annotation.

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

### Importing Sales Data

The application provides two ways to import Excel files:

**Method 1: Drag and Drop (Recommended)**
1. Start the application with `./mvnw`
2. Navigate to http://localhost:8080
3. **Drag and drop** your Excel (.xlsx) file onto the upload area
4. Or click the upload area to browse and select a file
5. The sales data grid will automatically update with imported records

**Method 2: Import from Sample Directory**
1. Place your Excel (.xlsx) file in the `sample_file` directory
2. Start the application
3. Click "Import from Sample File" button
4. The application will import the first .xlsx file found

### Expected Excel Format

The application expects Excel files with the following columns (in order):
- Product Code
- Product Name
- Category
- Price
- Quantity
- Total Amount
- Sale Date
- Region
- Notes

### Managing Data

- **Drag & Drop Upload**: Drag Excel files directly onto the upload area for instant import
- **Import from Sample File**: Imports from Excel files in the `sample_file` directory
- **Clear All Data**: Removes all records from the database
- **Grid View**: Displays all sales records with sortable columns

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

## Getting Started

The [Getting Started](https://vaadin.com/docs/latest/getting-started) guide will quickly familiarize you with your new
App implementation. You'll learn how to set up your development environment, understand the project
structure, and find resources to help you add muscles to your skeleton — transforming it into a fully-featured
application.
