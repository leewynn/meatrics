--liquibase formatted sql

--changeset lee:002-create-views

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

-- Invoice line items view - individual line items with calculated fields
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
