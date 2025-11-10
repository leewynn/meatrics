--liquibase formatted sql

--changeset meatrics:009-create-pricing-rules-table

-- Pricing rules table for dynamic pricing engine
-- Rules can be standard (apply to all customers) or customer-specific
-- Rules are matched by priority (lower number = higher priority)
CREATE TABLE pricing_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(255) NOT NULL,
    customer_code VARCHAR(50),

    -- Condition: what products does this apply to?
    condition_type VARCHAR(50) NOT NULL,
    condition_value VARCHAR(255),

    -- Action: how to calculate the price
    pricing_method VARCHAR(50) NOT NULL,
    pricing_value NUMERIC(10,4),

    -- Priority and status
    priority INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE pricing_rule IS 'Dynamic pricing rules for calculating sell prices based on conditions';
COMMENT ON COLUMN pricing_rule.rule_name IS 'User-friendly name for the rule (e.g., "ABC Meats - Beef Premium")';
COMMENT ON COLUMN pricing_rule.customer_code IS 'NULL for standard rules, customer code for customer-specific rules';
COMMENT ON COLUMN pricing_rule.condition_type IS 'Type of condition: ALL_PRODUCTS, CATEGORY, PRODUCT_CODE';
COMMENT ON COLUMN pricing_rule.condition_value IS 'Value to match against (e.g., "BEEF" for category, NULL for ALL_PRODUCTS)';
COMMENT ON COLUMN pricing_rule.pricing_method IS 'Pricing calculation method: COST_PLUS_PERCENT, COST_PLUS_FIXED, FIXED_PRICE';
COMMENT ON COLUMN pricing_rule.pricing_value IS 'Value for calculation (e.g., 1.20 for 20% markup, 5.00 for $5 markup). Can be NULL for MAINTAIN_GP_PERCENT (uses historical GP% only)';
COMMENT ON COLUMN pricing_rule.priority IS 'Lower number = higher priority. First matching rule wins.';
COMMENT ON COLUMN pricing_rule.is_active IS 'Whether this rule is currently active';

-- Indexes for performance
CREATE INDEX idx_pricing_rule_customer_code ON pricing_rule(customer_code);
CREATE INDEX idx_pricing_rule_condition_type ON pricing_rule(condition_type);
CREATE INDEX idx_pricing_rule_priority ON pricing_rule(priority);
CREATE INDEX idx_pricing_rule_active ON pricing_rule(is_active);

-- Composite index for rule matching queries
CREATE INDEX idx_pricing_rule_matching ON pricing_rule(customer_code, condition_type, is_active, priority);

-- Add foreign key constraint from pricing_session_line_items to pricing_rule
-- This FK was defined in migration 007 but can only be created after pricing_rule table exists
ALTER TABLE pricing_session_line_items
ADD CONSTRAINT fk_pricing_session_line_items_rule
FOREIGN KEY (applied_rule_id) REFERENCES pricing_rule(id) ON DELETE SET NULL;
