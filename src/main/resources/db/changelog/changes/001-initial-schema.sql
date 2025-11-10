--liquibase formatted sql

--changeset meatrics:001-initial-schema

-- ============================================================================
-- MEATRICS DATABASE SCHEMA - Consolidated Initial Schema
-- ============================================================================
-- This consolidated schema includes all tables, views, indexes, and constraints
-- for the Meatrics pricing application. All pricing/cost columns use NUMERIC(19,6)
-- for maximum precision (6 decimals) following enterprise ERP standards.
-- ============================================================================

-- ============================================================================
-- SECTION 1: IMPORT STAGING TABLES
-- ============================================================================
-- Tables for importing and tracking sales data from Excel files

-- Import summary table to track file imports
CREATE TABLE import_summary (
    import_id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL UNIQUE,
    record_count INT NOT NULL DEFAULT 0,
    import_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'PENDING'
);

COMMENT ON TABLE import_summary IS 'Tracks Excel file imports and their processing status';

CREATE INDEX idx_import_summary_filename ON import_summary(filename);
CREATE INDEX idx_import_summary_import_date ON import_summary(import_date);

-- Staging table for all imported line items (raw data from Excel)
CREATE TABLE imported_line_items (
    line_id BIGSERIAL PRIMARY KEY,
    import_id BIGINT,
    filename VARCHAR(255) NOT NULL,

    -- Customer fields
    customer_code VARCHAR(128),
    customer_name VARCHAR(255),

    -- Invoice fields
    invoice_number VARCHAR(50),
    transaction_date DATE,

    -- Product fields
    product_code VARCHAR(128),
    product_description VARCHAR(255),

    -- Line item fields
    quantity NUMERIC(19,6),
    amount NUMERIC(19,6),
    cost NUMERIC(19,6),
    ref1 VARCHAR(50),
    ref2 VARCHAR(50),
    ref3 VARCHAR(50),
    status VARCHAR(255),
    outstanding_amount NUMERIC(19,6),

    -- Metadata
    import_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (import_id) REFERENCES import_summary(import_id) ON DELETE CASCADE,

    -- Unique constraint to prevent duplicate imports
    CONSTRAINT uk_imported_line_items_no_duplicates
        UNIQUE (customer_code, invoice_number, product_code, transaction_date, quantity, amount)
);

COMMENT ON TABLE imported_line_items IS 'Staging table for raw sales data imported from Excel files';

CREATE INDEX idx_imported_line_items_import_id ON imported_line_items(import_id);
CREATE INDEX idx_imported_line_items_filename ON imported_line_items(filename);
CREATE INDEX idx_imported_line_items_customer_code ON imported_line_items(customer_code);
CREATE INDEX idx_imported_line_items_invoice_number ON imported_line_items(invoice_number);
CREATE INDEX idx_imported_line_items_product_code ON imported_line_items(product_code);
CREATE INDEX idx_imported_line_items_transaction_date ON imported_line_items(transaction_date);

-- ============================================================================
-- SECTION 2: PRODUCT COST TABLES
-- ============================================================================

-- Cost import summary table
CREATE TABLE cost_import_summary (
    cost_import_id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    import_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_products INTEGER,
    active_products INTEGER,
    products_with_cost INTEGER,
    import_status VARCHAR(50),
    error_message TEXT
);

COMMENT ON TABLE cost_import_summary IS 'Tracks product cost file imports and statistics';

CREATE INDEX idx_cost_import_summary_filename ON cost_import_summary(filename);

-- Product costs table
CREATE TABLE product_costs (
    product_cost_id BIGSERIAL PRIMARY KEY,
    product_code VARCHAR(128) NOT NULL,
    description VARCHAR(255),

    -- Primary cost field
    standard_cost NUMERIC(19,6),

    -- Additional cost fields
    latest_cost NUMERIC(19,6),
    average_cost NUMERIC(19,6),
    supplier_cost NUMERIC(19,6),

    -- Sell prices (multiple tiers)
    sell_price_1 NUMERIC(19,6),
    sell_price_2 NUMERIC(19,6),
    sell_price_3 NUMERIC(19,6),
    sell_price_4 NUMERIC(19,6),
    sell_price_5 NUMERIC(19,6),
    sell_price_6 NUMERIC(19,6),
    sell_price_7 NUMERIC(19,6),
    sell_price_8 NUMERIC(19,6),
    sell_price_9 NUMERIC(19,6),
    sell_price_10 NUMERIC(19,6),

    -- Product attributes
    is_active BOOLEAN DEFAULT TRUE,
    unit_of_measure VARCHAR(20),
    weight NUMERIC(10,3),
    cubic NUMERIC(10,3),
    min_stock NUMERIC(19,6),
    max_stock NUMERIC(19,6),
    bin_code VARCHAR(50),

    -- Classification
    primary_group VARCHAR(100),
    secondary_group VARCHAR(100),
    tertiary_group VARCHAR(100),
    product_class VARCHAR(100),
    supplier_name VARCHAR(255),

    -- GL codes
    sales_gl_code VARCHAR(50),
    purchase_gl_code VARCHAR(50),
    cos_gl_code VARCHAR(50),

    -- Tax rates
    sales_tax_rate NUMERIC(5,2),
    purchase_tax_rate NUMERIC(5,2),

    -- Metadata
    import_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    import_filename VARCHAR(255),

    CONSTRAINT uq_product_code UNIQUE (product_code)
);

COMMENT ON TABLE product_costs IS 'Master product data including costs and pricing';
COMMENT ON COLUMN product_costs.standard_cost IS 'Standard cost per unit (6 decimal precision)';
COMMENT ON COLUMN product_costs.primary_group IS 'Product category for rule matching';
COMMENT ON COLUMN product_costs.is_active IS 'Whether this product is active (TRUE) or discontinued (FALSE)';

CREATE INDEX idx_product_costs_code ON product_costs(product_code);
CREATE INDEX idx_product_costs_active ON product_costs(is_active);
CREATE INDEX idx_product_costs_group ON product_costs(primary_group);

-- ============================================================================
-- SECTION 3: CUSTOMER TABLE
-- ============================================================================

CREATE TABLE customers (
    customer_id BIGSERIAL PRIMARY KEY,
    customer_code VARCHAR(128) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_rating VARCHAR(50),
    notes TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_customer_code UNIQUE (customer_code)
);

COMMENT ON TABLE customers IS 'Customer master data with ratings for pricing analysis';
COMMENT ON COLUMN customers.customer_rating IS 'Customer rating category (e.g., A, B, C) for pricing and analysis';

CREATE INDEX idx_customers_code ON customers(customer_code);

-- ============================================================================
-- SECTION 4: PRICING SESSIONS TABLES
-- ============================================================================

-- Pricing sessions table
CREATE TABLE pricing_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_name VARCHAR(255) NOT NULL UNIQUE,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    notes TEXT
);

COMMENT ON TABLE pricing_sessions IS 'Tracks pricing review sessions where users modify and save pricing data';
COMMENT ON COLUMN pricing_sessions.session_name IS 'Unique name for the pricing session';
COMMENT ON COLUMN pricing_sessions.status IS 'Session status: IN_PROGRESS, COMPLETED, ARCHIVED';

CREATE INDEX idx_pricing_sessions_session_name ON pricing_sessions(session_name);

-- Pricing session line items table (with 6-decimal precision)
CREATE TABLE pricing_session_line_items (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    customer_code VARCHAR(255),
    customer_name VARCHAR(255),
    customer_rating VARCHAR(50),
    product_code VARCHAR(255),
    product_description TEXT,
    primary_group VARCHAR(255),
    total_quantity NUMERIC(19,6),
    total_amount NUMERIC(19,6),
    original_amount NUMERIC(19,6),
    total_cost NUMERIC(19,6),
    amount_modified BOOLEAN NOT NULL DEFAULT FALSE,

    -- Historical pricing data (what actually happened in imported transactions)
    last_cost NUMERIC(19,6),
    last_unit_sell_price NUMERIC(19,6),
    last_amount NUMERIC(19,6),
    last_gross_profit NUMERIC(19,6),

    -- New pricing data (calculated by pricing rules or manually set)
    incoming_cost NUMERIC(19,6),
    new_unit_sell_price NUMERIC(19,6),
    new_amount NUMERIC(19,6),
    new_gross_profit NUMERIC(19,6),

    -- Pricing rule metadata (FK added after pricing_rule table created)
    applied_rule_id BIGINT,
    manual_override BOOLEAN NOT NULL DEFAULT FALSE,

    FOREIGN KEY (session_id) REFERENCES pricing_sessions(id) ON DELETE CASCADE
);

COMMENT ON TABLE pricing_session_line_items IS 'Line items within a pricing session, grouped by customer and product';
COMMENT ON COLUMN pricing_session_line_items.total_quantity IS 'Total quantity for this customer+product combination (6 decimal precision for fractional units)';
COMMENT ON COLUMN pricing_session_line_items.total_amount IS 'Current amount with 6 decimal precision (display rounds to 2)';
COMMENT ON COLUMN pricing_session_line_items.last_cost IS 'Historical average unit cost with 6 decimal precision';
COMMENT ON COLUMN pricing_session_line_items.last_unit_sell_price IS 'Historical average unit sell price with 6 decimal precision';
COMMENT ON COLUMN pricing_session_line_items.incoming_cost IS 'Current unit cost with 6 decimal precision';
COMMENT ON COLUMN pricing_session_line_items.new_unit_sell_price IS 'New unit sell price with 6 decimal precision (display rounds to 2)';
COMMENT ON COLUMN pricing_session_line_items.new_amount IS 'New total amount with 6 decimal precision (display rounds to 2)';
COMMENT ON COLUMN pricing_session_line_items.new_gross_profit IS 'New gross profit with 6 decimal precision (display rounds to 2)';
COMMENT ON COLUMN pricing_session_line_items.applied_rule_id IS 'Foreign key to pricing_rule table - which rule calculated this price (NULL if manual)';
COMMENT ON COLUMN pricing_session_line_items.manual_override IS 'TRUE if user manually overrode the rule-calculated price';

CREATE INDEX idx_pricing_session_line_items_session_id ON pricing_session_line_items(session_id);
CREATE INDEX idx_pricing_session_line_items_composite ON pricing_session_line_items(session_id, customer_code, product_code);
CREATE INDEX idx_pricing_session_line_items_rule ON pricing_session_line_items(applied_rule_id);

-- ============================================================================
-- SECTION 5: PRICING RULES TABLE
-- ============================================================================
-- Includes layered multi-rule pricing support and date-based activation

CREATE TABLE pricing_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(255) NOT NULL,
    customer_code VARCHAR(50),

    -- Condition: what products does this apply to?
    condition_type VARCHAR(50) NOT NULL,
    condition_value VARCHAR(255),

    -- Action: how to calculate the price
    pricing_method VARCHAR(50) NOT NULL,
    pricing_value NUMERIC(19,6),

    -- Priority and status
    priority INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Multi-rule layered pricing support
    rule_category VARCHAR(50) NOT NULL DEFAULT 'BASE_PRICE',
    layer_order INT NOT NULL DEFAULT 1,

    -- Date-based activation
    valid_from DATE,
    valid_to DATE,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Ensure valid_to is after valid_from
    CONSTRAINT chk_pricing_rule_date_range
        CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_to >= valid_from)
);

COMMENT ON TABLE pricing_rule IS 'Dynamic pricing rules for calculating sell prices with layered multi-rule support';
COMMENT ON COLUMN pricing_rule.rule_name IS 'User-friendly name for the rule';
COMMENT ON COLUMN pricing_rule.customer_code IS 'NULL for standard rules, customer code for customer-specific rules';
COMMENT ON COLUMN pricing_rule.condition_type IS 'Type of condition: ALL_PRODUCTS, CATEGORY, PRODUCT_CODE';
COMMENT ON COLUMN pricing_rule.condition_value IS 'Value to match against (e.g., "BEEF" for category, NULL for ALL_PRODUCTS)';
COMMENT ON COLUMN pricing_rule.pricing_method IS 'Pricing calculation method: COST_PLUS_PERCENT, COST_PLUS_FIXED, FIXED_PRICE, MAINTAIN_GP_PERCENT';
COMMENT ON COLUMN pricing_rule.pricing_value IS 'Pricing value with 6 decimal precision (multipliers, fixed amounts, GP percentages)';
COMMENT ON COLUMN pricing_rule.priority IS 'Lower number = higher priority. Used for sorting within same layer.';
COMMENT ON COLUMN pricing_rule.rule_category IS 'Category/layer of the pricing rule: BASE_PRICE, CUSTOMER_ADJUSTMENT, PRODUCT_ADJUSTMENT, PROMOTIONAL';
COMMENT ON COLUMN pricing_rule.layer_order IS 'Execution order within the same rule_category. Lower number executes first.';
COMMENT ON COLUMN pricing_rule.valid_from IS 'Date when this rule becomes active. NULL means always active.';
COMMENT ON COLUMN pricing_rule.valid_to IS 'Date when this rule expires. NULL means never expires.';

-- Indexes for performance
CREATE INDEX idx_pricing_rule_customer_code ON pricing_rule(customer_code);
CREATE INDEX idx_pricing_rule_condition_type ON pricing_rule(condition_type);
CREATE INDEX idx_pricing_rule_priority ON pricing_rule(priority);
CREATE INDEX idx_pricing_rule_active ON pricing_rule(is_active);
CREATE INDEX idx_pricing_rule_category ON pricing_rule(rule_category, layer_order);
CREATE INDEX idx_pricing_rule_dates ON pricing_rule(valid_from, valid_to);

-- Composite index for rule matching queries
CREATE INDEX idx_pricing_rule_matching ON pricing_rule(customer_code, condition_type, is_active, priority);

-- Add foreign key constraint from pricing_session_line_items to pricing_rule
ALTER TABLE pricing_session_line_items
ADD CONSTRAINT fk_pricing_session_line_items_rule
FOREIGN KEY (applied_rule_id) REFERENCES pricing_rule(id) ON DELETE SET NULL;

-- ============================================================================
-- SECTION 6: MULTI-RULE TRACKING TABLE
-- ============================================================================

CREATE TABLE pricing_session_line_item_rules (
    id BIGSERIAL PRIMARY KEY,
    line_item_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    layer_category VARCHAR(50) NOT NULL,
    application_order INT NOT NULL,
    price_before NUMERIC(19,6) NOT NULL,
    price_after NUMERIC(19,6) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_line_item_rules_line_item
        FOREIGN KEY (line_item_id)
        REFERENCES pricing_session_line_items(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_line_item_rules_rule
        FOREIGN KEY (rule_id)
        REFERENCES pricing_rule(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_line_item_rule
        UNIQUE (line_item_id, rule_id)
);

COMMENT ON TABLE pricing_session_line_item_rules IS 'Tracks all pricing rules applied to each line item. Supports multi-rule layered pricing by recording the complete calculation chain.';
COMMENT ON COLUMN pricing_session_line_item_rules.price_before IS 'Price before this rule was applied (6 decimal precision)';
COMMENT ON COLUMN pricing_session_line_item_rules.price_after IS 'Price after this rule was applied (6 decimal precision)';

CREATE INDEX idx_line_item_rules_line_item ON pricing_session_line_item_rules(line_item_id);
CREATE INDEX idx_line_item_rules_rule ON pricing_session_line_item_rules(rule_id);
CREATE INDEX idx_line_item_rules_order ON pricing_session_line_item_rules(line_item_id, application_order);

-- ============================================================================
-- SECTION 7: VIEWS
-- ============================================================================

-- Customer view - distinct customers from imported data
CREATE OR REPLACE VIEW v_customers AS
SELECT
    ROW_NUMBER() OVER (ORDER BY customer_code) as customer_id,
    customer_code,
    customer_name,
    MIN(import_date) as first_seen,
    MAX(import_date) as last_seen,
    COUNT(DISTINCT invoice_number) as invoice_count,
    COUNT(*) as line_item_count,
    SUM(amount) as total_amount,
    SUM(cost) as total_cost
FROM imported_line_items
WHERE customer_code IS NOT NULL
GROUP BY customer_code, customer_name;

-- Product view - distinct products from imported data
CREATE OR REPLACE VIEW v_products AS
SELECT
    ROW_NUMBER() OVER (ORDER BY product_code) as product_id,
    product_code,
    product_description,
    MIN(import_date) as first_seen,
    MAX(import_date) as last_seen,
    COUNT(*) as times_sold,
    SUM(quantity) as total_quantity_sold,
    SUM(amount) as total_revenue,
    SUM(cost) as total_cost
FROM imported_line_items
WHERE product_code IS NOT NULL
GROUP BY product_code, product_description;

-- Invoice view - invoices with totals and line counts
CREATE OR REPLACE VIEW v_invoices AS
SELECT
    ROW_NUMBER() OVER (ORDER BY transaction_date DESC, invoice_number) as invoice_id,
    invoice_number,
    customer_code,
    customer_name,
    transaction_date,
    SUM(quantity) as total_quantity,
    SUM(amount) as total_amount,
    SUM(cost) as total_cost,
    SUM(outstanding_amount) as total_outstanding,
    COUNT(*) as line_count,
    MAX(import_date) as import_date,
    MAX(filename) as source_file
FROM imported_line_items
WHERE invoice_number IS NOT NULL
GROUP BY invoice_number, customer_code, customer_name, transaction_date;

-- Invoice line items view
CREATE OR REPLACE VIEW v_invoice_line_items AS
SELECT
    line_id,
    invoice_number,
    customer_code,
    customer_name,
    product_code,
    product_description,
    transaction_date,
    quantity,
    amount,
    cost,
    ref1,
    ref2,
    ref3,
    outstanding_amount,
    filename,
    import_date
FROM imported_line_items
WHERE invoice_number IS NOT NULL;

-- Grouped line items view - aggregated by customer and product
CREATE OR REPLACE VIEW v_grouped_line_items AS
SELECT
    customer_code,
    customer_name,
    product_code,
    product_description,
    SUM(quantity) as total_quantity,
    SUM(amount) as total_amount,
    SUM(cost) as total_cost
FROM imported_line_items
GROUP BY customer_code, customer_name, product_code, product_description;

COMMENT ON VIEW v_grouped_line_items IS 'Aggregated line items grouped by customer and product for pricing sessions view';
