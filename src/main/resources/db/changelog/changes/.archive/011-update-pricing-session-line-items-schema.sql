--liquibase formatted sql

--changeset meatrics:011-update-pricing-session-line-items-schema

-- Update pricing_session_line_items to support new pricing comparison workflow
-- Adds fields to track historical pricing vs new pricing calculated by rules

-- Historical pricing data (what actually happened)
ALTER TABLE pricing_session_line_items
ADD COLUMN IF NOT EXISTS last_cost NUMERIC(10,2),
ADD COLUMN IF NOT EXISTS last_unit_sell_price NUMERIC(10,2),
ADD COLUMN IF NOT EXISTS last_amount NUMERIC(10,2),
ADD COLUMN IF NOT EXISTS last_gross_profit NUMERIC(10,2);

COMMENT ON COLUMN pricing_session_line_items.last_cost IS 'Historical cost when the product was sold';
COMMENT ON COLUMN pricing_session_line_items.last_unit_sell_price IS 'Historical unit sell price from actual transactions';
COMMENT ON COLUMN pricing_session_line_items.last_amount IS 'Historical total amount (last_unit_sell_price × quantity)';
COMMENT ON COLUMN pricing_session_line_items.last_gross_profit IS 'Historical gross profit (last_amount - last_cost × quantity)';

-- New pricing data (calculated by pricing rules)
ALTER TABLE pricing_session_line_items
ADD COLUMN IF NOT EXISTS incoming_cost NUMERIC(10,2),
ADD COLUMN IF NOT EXISTS new_unit_sell_price NUMERIC(10,2),
ADD COLUMN IF NOT EXISTS new_amount NUMERIC(10,2),
ADD COLUMN IF NOT EXISTS new_gross_profit NUMERIC(10,2);

COMMENT ON COLUMN pricing_session_line_items.incoming_cost IS 'Current cost from product_costs.stdcost';
COMMENT ON COLUMN pricing_session_line_items.new_unit_sell_price IS 'New unit sell price calculated by pricing rules or manually set';
COMMENT ON COLUMN pricing_session_line_items.new_amount IS 'New total amount (new_unit_sell_price × quantity)';
COMMENT ON COLUMN pricing_session_line_items.new_gross_profit IS 'New gross profit (new_amount - incoming_cost × quantity)';

-- Pricing rule metadata
ALTER TABLE pricing_session_line_items
ADD COLUMN IF NOT EXISTS applied_rule_id BIGINT,
ADD COLUMN IF NOT EXISTS manual_override BOOLEAN;

-- Set default for manual_override if it was just created as null
UPDATE pricing_session_line_items SET manual_override = FALSE WHERE manual_override IS NULL;

-- Make manual_override NOT NULL with default after data update
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pricing_session_line_items'
        AND column_name = 'manual_override'
        AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE pricing_session_line_items
        ALTER COLUMN manual_override SET NOT NULL,
        ALTER COLUMN manual_override SET DEFAULT FALSE;
    END IF;
END $$;

COMMENT ON COLUMN pricing_session_line_items.applied_rule_id IS 'Foreign key to pricing_rule table - which rule calculated this price (NULL if manual)';
COMMENT ON COLUMN pricing_session_line_items.manual_override IS 'TRUE if user manually overrode the rule-calculated price';

-- Foreign key constraint (nullable - manual prices won't have a rule)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_pricing_session_line_items_rule'
        AND table_name = 'pricing_session_line_items'
    ) THEN
        ALTER TABLE pricing_session_line_items
        ADD CONSTRAINT fk_pricing_session_line_items_rule
        FOREIGN KEY (applied_rule_id) REFERENCES pricing_rule(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Index for rule references
CREATE INDEX IF NOT EXISTS idx_pricing_session_line_items_rule ON pricing_session_line_items(applied_rule_id);
