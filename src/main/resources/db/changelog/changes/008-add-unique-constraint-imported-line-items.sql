--liquibase formatted sql

--changeset lee:008-add-unique-constraint-imported-line-items

-- Add unique constraint to prevent duplicate imports
-- A duplicate is defined as a line item with the same:
-- customer_code, invoice_number, product_code, transaction_date, quantity, and amount
ALTER TABLE imported_line_items
ADD CONSTRAINT uk_imported_line_items_no_duplicates
UNIQUE (customer_code, invoice_number, product_code, transaction_date, quantity, amount);
