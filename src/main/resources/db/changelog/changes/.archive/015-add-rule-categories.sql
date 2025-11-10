--liquibase formatted sql

--changeset meatrics:015-add-rule-categories

-- Add multi-rule layered pricing support to pricing_rule table
-- Enables rules to work in layers (BASE → CUSTOMER → PRODUCT → PROMOTIONAL)
-- and supports date-based activation/expiration for time-bound pricing

-- Add rule_category column to define which layer this rule operates in
ALTER TABLE pricing_rule
ADD COLUMN rule_category VARCHAR(50) NOT NULL DEFAULT 'BASE_PRICE';

COMMENT ON COLUMN pricing_rule.rule_category IS 'Category/layer of the pricing rule: BASE_PRICE (foundation layer), CUSTOMER_ADJUSTMENT (customer-specific modifications), PRODUCT_ADJUSTMENT (product-specific adjustments), PROMOTIONAL (time-bound promotions)';

-- Add layer_order to control execution sequence within the same category
ALTER TABLE pricing_rule
ADD COLUMN layer_order INT NOT NULL DEFAULT 1;

COMMENT ON COLUMN pricing_rule.layer_order IS 'Execution order within the same rule_category. Lower number executes first. Used for fine-grained control when multiple rules in the same layer apply.';

-- Add valid_from date for time-based activation
ALTER TABLE pricing_rule
ADD COLUMN valid_from DATE;

COMMENT ON COLUMN pricing_rule.valid_from IS 'Date when this rule becomes active. NULL means the rule is always active (no start date restriction). Used for scheduling price changes and promotions.';

-- Add valid_to date for time-based expiration
ALTER TABLE pricing_rule
ADD COLUMN valid_to DATE;

COMMENT ON COLUMN pricing_rule.valid_to IS 'Date when this rule expires. NULL means the rule never expires (no end date restriction). Used for time-bound promotions and seasonal pricing.';

-- Create index for efficient category and layer queries
CREATE INDEX idx_pricing_rule_category ON pricing_rule(rule_category, layer_order);

-- Create index for efficient date-based queries (finding active rules)
CREATE INDEX idx_pricing_rule_dates ON pricing_rule(valid_from, valid_to);

-- Categorize existing rules based on their characteristics
-- This ensures backward compatibility by automatically assigning appropriate categories

-- Customer-specific rules → CUSTOMER_ADJUSTMENT layer
UPDATE pricing_rule
SET rule_category = 'CUSTOMER_ADJUSTMENT',
    layer_order = priority
WHERE customer_code IS NOT NULL;

-- Product-specific rules (exact product code matches) → PRODUCT_ADJUSTMENT layer
UPDATE pricing_rule
SET rule_category = 'PRODUCT_ADJUSTMENT',
    layer_order = priority
WHERE customer_code IS NULL
  AND condition_type = 'PRODUCT_CODE';

-- Broad base rules (ALL_PRODUCTS, CATEGORY) → BASE_PRICE layer
UPDATE pricing_rule
SET rule_category = 'BASE_PRICE',
    layer_order = priority
WHERE customer_code IS NULL
  AND condition_type IN ('ALL_PRODUCTS', 'CATEGORY');

-- Any remaining rules default to BASE_PRICE (already set by DEFAULT constraint)
-- This handles any edge cases or new condition types

-- Date columns remain NULL for existing rules, meaning they are always active
-- This preserves existing behavior where rules don't expire

-- Add validation check to ensure valid_to is after valid_from when both are set
ALTER TABLE pricing_rule
ADD CONSTRAINT chk_pricing_rule_date_range
CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_to >= valid_from);

COMMENT ON CONSTRAINT chk_pricing_rule_date_range ON pricing_rule IS 'Ensures valid_to date is not before valid_from date when both are specified';
