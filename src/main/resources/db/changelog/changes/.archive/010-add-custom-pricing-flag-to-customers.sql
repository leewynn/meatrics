--liquibase formatted sql

--changeset meatrics:010-add-custom-pricing-flag-to-customers

-- Add flag to indicate if a customer has custom pricing rules
-- If TRUE, system will first check customer-specific rules
-- If no customer rule matches, it falls back to standard rules
ALTER TABLE customers
ADD COLUMN has_custom_pricing BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN customers.has_custom_pricing IS 'TRUE if customer has custom pricing rules; system will prioritize customer-specific rules but fall back to standard rules if no match';

-- Index for filtering customers with custom pricing
CREATE INDEX idx_customers_custom_pricing ON customers(has_custom_pricing);
