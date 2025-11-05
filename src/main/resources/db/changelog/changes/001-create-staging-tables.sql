--liquibase formatted sql

--changeset lee:001-create-staging-tables

-- Import summary table to track file imports
CREATE TABLE import_summary (
    import_id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL UNIQUE,
    record_count INT NOT NULL DEFAULT 0,
    import_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'PENDING'
);

CREATE INDEX idx_import_summary_filename ON import_summary(filename);
CREATE INDEX idx_import_summary_import_date ON import_summary(import_date);

-- Staging table for all imported line items (raw data from Excel)
CREATE TABLE imported_line_items (
    line_id BIGSERIAL PRIMARY KEY,
    import_id BIGINT,
    filename VARCHAR(255) NOT NULL,

    -- Customer fields (duplicated per line - that's OK in staging)
    customer_code VARCHAR(128),
    customer_name VARCHAR(255),

    -- Invoice fields
    invoice_number VARCHAR(50),
    transaction_date DATE,

    -- Product fields
    product_code VARCHAR(128),
    product_description VARCHAR(255),

    -- Line item fields
    quantity DECIMAL(10,3),
    amount DECIMAL(12,2),
    cost DECIMAL(12,2),
    ref1 VARCHAR(50),
    ref2 VARCHAR(50),
    ref3 VARCHAR(50),
    status VARCHAR(255),
    outstanding_amount DECIMAL(12,2),

    -- Metadata
    import_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (import_id) REFERENCES import_summary(import_id) ON DELETE CASCADE
);

CREATE INDEX idx_imported_line_items_import_id ON imported_line_items(import_id);
CREATE INDEX idx_imported_line_items_filename ON imported_line_items(filename);
CREATE INDEX idx_imported_line_items_customer_code ON imported_line_items(customer_code);
CREATE INDEX idx_imported_line_items_invoice_number ON imported_line_items(invoice_number);
CREATE INDEX idx_imported_line_items_product_code ON imported_line_items(product_code);
CREATE INDEX idx_imported_line_items_transaction_date ON imported_line_items(transaction_date);
