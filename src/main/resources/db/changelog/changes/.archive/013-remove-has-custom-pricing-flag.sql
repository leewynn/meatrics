--liquibase formatted sql

--changeset meatrics:013-remove-has-custom-pricing-flag

-- Remove has_custom_pricing column - not needed
-- The pricing engine will simply check if customer-specific rules exist
DROP INDEX IF EXISTS idx_customers_custom_pricing;

ALTER TABLE customers
DROP COLUMN has_custom_pricing;

COMMENT ON TABLE customers IS 'Customer master data. Customer-specific pricing is determined by existence of pricing rules with matching customer_code.';
