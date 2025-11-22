--liquibase formatted sql

--changeset meatrics:001-initial-schema

-- ============================================================================
-- MEATRICS DATABASE SCHEMA - Consolidated Initial Schema
-- ============================================================================
-- This consolidated schema includes all tables, views, indexes, and constraints
-- for the Meatrics pricing application. All pricing/cost columns use NUMERIC(19,6)
-- for maximum precision (6 decimals) following enterprise ERP standards.
--
-- INCLUDES:
-- - Core import/staging tables
-- - Product cost management
-- - Customer/group management with hierarchical support
-- - Pricing sessions and rules
-- - Multi-entity pricing session support
-- - Audit trail for applied rules
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
-- SECTION 3: CUSTOMER TABLE WITH GROUP SUPPORT
-- ============================================================================

CREATE TABLE customers (
    customer_id BIGSERIAL PRIMARY KEY,
    customer_code VARCHAR(128) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_rating VARCHAR(50),

    -- Group pricing support
    entity_type VARCHAR(20) NOT NULL DEFAULT 'COMPANY',
    parent_id BIGINT,

    notes TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_customer_code UNIQUE (customer_code)
);

COMMENT ON TABLE customers IS 'Customer master data with group hierarchy support';
COMMENT ON COLUMN customers.customer_rating IS 'Customer rating category (e.g., A, B, C) for pricing and analysis';
COMMENT ON COLUMN customers.entity_type IS 'Entity type: GROUP or COMPANY';
COMMENT ON COLUMN customers.parent_id IS 'Reference to parent group if this company belongs to a group';

CREATE INDEX idx_customers_code ON customers(customer_code);
CREATE INDEX idx_customers_parent_id ON customers(parent_id);
CREATE INDEX idx_customers_entity_type ON customers(entity_type);

-- Add foreign key constraint for parent relationship
ALTER TABLE customers
ADD CONSTRAINT fk_customers_parent
FOREIGN KEY (parent_id) REFERENCES customers(customer_id);

-- ============================================================================
-- SECTION 3.1: CUSTOMER TAGS TABLE
-- ============================================================================

CREATE TABLE customer_tags (
    customer_id BIGINT NOT NULL,
    tag VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (customer_id, tag),
    CONSTRAINT fk_customer_tags_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id)
        ON DELETE CASCADE
);

COMMENT ON TABLE customer_tags IS 'Business classification tags for customers (PRICE_FILE_CUSTOMER, HIGH_VALUE, etc.)';

CREATE INDEX idx_customer_tags_tag ON customer_tags(tag);

-- ============================================================================
-- SECTION 3.2: CUSTOMER PRICING RULES TABLE
-- ============================================================================

CREATE TABLE customer_pricing_rules (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    execution_order INT NOT NULL,
    applies_to_category VARCHAR(100),
    applies_to_product VARCHAR(100),
    target_value NUMERIC(10,2),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_customer_pricing_rules_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id)
        ON DELETE CASCADE,

    UNIQUE(customer_id, execution_order)
);

COMMENT ON TABLE customer_pricing_rules IS 'Customer or group-specific pricing rules. Only groups (entity_type=GROUP) or standalone companies (parent_id IS NULL) should have rules.';
COMMENT ON COLUMN customer_pricing_rules.execution_order IS 'Order in which rules are applied (lower numbers first)';

CREATE INDEX idx_customer_pricing_rules_customer_id ON customer_pricing_rules(customer_id);
CREATE INDEX idx_customer_pricing_rules_active ON customer_pricing_rules(is_active);

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

-- ============================================================================
-- SECTION 4.1: PRICING SESSION ENTITIES (MULTI-ENTITY SUPPORT)
-- ============================================================================

CREATE TABLE pricing_session_entities (
    session_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    PRIMARY KEY (session_id, customer_id),

    CONSTRAINT fk_pricing_session_entities_session
        FOREIGN KEY (session_id)
        REFERENCES pricing_sessions(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_pricing_session_entities_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id)
        ON DELETE CASCADE
);

COMMENT ON TABLE pricing_session_entities IS 'Maps pricing sessions to multiple customers/groups for multi-entity pricing';

CREATE INDEX idx_pricing_session_entities_session_id ON pricing_session_entities(session_id);
CREATE INDEX idx_pricing_session_entities_customer_id ON pricing_session_entities(customer_id);

-- ============================================================================
-- SECTION 4.2: PRICING SESSION LINE ITEMS
-- ============================================================================

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
-- Global fallback pricing rules with layered multi-rule pricing support

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

COMMENT ON TABLE pricing_rule IS 'Global fallback pricing rules. Used when customers have no customer_pricing_rules defined.';
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
-- SECTION 6: APPLIED RULES AUDIT TABLE
-- ============================================================================

CREATE TABLE pricing_session_applied_rules (
    id BIGSERIAL PRIMARY KEY,
    session_line_item_id BIGINT NOT NULL,
    rule_id BIGINT,  -- Nullable: rule might be deleted later

    -- Immutable snapshot of rule at application time
    rule_name VARCHAR(255) NOT NULL,
    rule_category VARCHAR(50) NOT NULL,  -- BASE_PRICE, CUSTOMER_ADJUSTMENT, etc.
    pricing_method VARCHAR(50) NOT NULL, -- COST_PLUS_PERCENT, FIXED_PRICE, etc.
    pricing_value DECIMAL(15,6),         -- The multiplier or value used

    -- Pricing chain tracking
    application_order INT NOT NULL,      -- Order rule was applied (1, 2, 3...)
    input_price DECIMAL(15,6) NOT NULL,  -- Price before this rule
    output_price DECIMAL(15,6) NOT NULL, -- Price after this rule

    -- Metadata
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Foreign keys
    CONSTRAINT fk_applied_rules_line_item
        FOREIGN KEY (session_line_item_id)
        REFERENCES pricing_session_line_items(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_applied_rules_rule
        FOREIGN KEY (rule_id)
        REFERENCES pricing_rule(id)
        ON DELETE SET NULL  -- Keep audit trail even if rule deleted
);

COMMENT ON TABLE pricing_session_applied_rules IS 'Immutable audit trail of all pricing rules applied to line items';

-- Indexes for performance
CREATE INDEX idx_applied_rules_line_item ON pricing_session_applied_rules(session_line_item_id);
CREATE INDEX idx_applied_rules_rule_id ON pricing_session_applied_rules(rule_id);
CREATE INDEX idx_applied_rules_category ON pricing_session_applied_rules(rule_category);

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
    ili.customer_code,
    ili.customer_name,
    ili.product_code,
    ili.product_description,
    pc.primary_group as category,
    pc.unit_of_measure as unit,
    SUM(ili.quantity) as total_quantity,
    SUM(ili.amount) as total_amount,
    SUM(ili.cost) as total_cost,
    -- Calculate last price from most recent transaction
    (SELECT ili2.amount / NULLIF(ili2.quantity, 0)
     FROM imported_line_items ili2
     WHERE ili2.customer_code = ili.customer_code
       AND ili2.product_code = ili.product_code
     ORDER BY ili2.transaction_date DESC, ili2.import_date DESC
     LIMIT 1) as last_price,
    -- Get current cost from product_costs
    pc.standard_cost as current_cost,
    -- Get customer_id from customers table if exists
    c.customer_id
FROM imported_line_items ili
LEFT JOIN product_costs pc ON pc.product_code = ili.product_code
LEFT JOIN customers c ON c.customer_code = ili.customer_code
WHERE ili.customer_code IS NOT NULL
  AND ili.product_code IS NOT NULL
GROUP BY ili.customer_code, ili.customer_name, ili.product_code, ili.product_description,
         pc.primary_group, pc.unit_of_measure, pc.standard_cost, c.customer_id;

COMMENT ON VIEW v_grouped_line_items IS 'Aggregated line items grouped by customer and product for pricing sessions view. Includes category, unit, last_price, and current_cost.';
