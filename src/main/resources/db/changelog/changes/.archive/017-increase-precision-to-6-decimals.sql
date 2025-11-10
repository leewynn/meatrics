--liquibase formatted sql

--changeset meatrics:017-increase-precision-to-6-decimals

-- Increase precision from 2 to 6 decimals for all pricing and cost columns
-- This eliminates rounding errors in multi-rule pricing calculations and matches industry standards

-- Update pricing_session_line_items table
ALTER TABLE pricing_session_line_items
    ALTER COLUMN total_quantity TYPE NUMERIC(19,6),
    ALTER COLUMN total_amount TYPE NUMERIC(19,6),
    ALTER COLUMN original_amount TYPE NUMERIC(19,6),
    ALTER COLUMN total_cost TYPE NUMERIC(19,6),
    ALTER COLUMN last_cost TYPE NUMERIC(19,6),
    ALTER COLUMN last_unit_sell_price TYPE NUMERIC(19,6),
    ALTER COLUMN last_amount TYPE NUMERIC(19,6),
    ALTER COLUMN last_gross_profit TYPE NUMERIC(19,6),
    ALTER COLUMN incoming_cost TYPE NUMERIC(19,6),
    ALTER COLUMN new_unit_sell_price TYPE NUMERIC(19,6),
    ALTER COLUMN new_amount TYPE NUMERIC(19,6),
    ALTER COLUMN new_gross_profit TYPE NUMERIC(19,6);

COMMENT ON COLUMN pricing_session_line_items.total_quantity IS 'Total quantity for this customer+product combination (6 decimal precision for fractional units)';
COMMENT ON COLUMN pricing_session_line_items.total_amount IS 'Current amount with 6 decimal precision (display rounds to 2)';
COMMENT ON COLUMN pricing_session_line_items.original_amount IS 'Original amount before modifications with 6 decimal precision';
COMMENT ON COLUMN pricing_session_line_items.total_cost IS 'Total cost with 6 decimal precision for accurate GP calculations';
COMMENT ON COLUMN pricing_session_line_items.last_cost IS 'Historical average unit cost with 6 decimal precision';
COMMENT ON COLUMN pricing_session_line_items.last_unit_sell_price IS 'Historical average unit sell price with 6 decimal precision';
COMMENT ON COLUMN pricing_session_line_items.last_amount IS 'Historical total amount with 6 decimal precision';
COMMENT ON COLUMN pricing_session_line_items.last_gross_profit IS 'Historical gross profit with 6 decimal precision';
COMMENT ON COLUMN pricing_session_line_items.incoming_cost IS 'Current unit cost with 6 decimal precision';
COMMENT ON COLUMN pricing_session_line_items.new_unit_sell_price IS 'New unit sell price with 6 decimal precision (display rounds to 2)';
COMMENT ON COLUMN pricing_session_line_items.new_amount IS 'New total amount with 6 decimal precision (display rounds to 2)';
COMMENT ON COLUMN pricing_session_line_items.new_gross_profit IS 'New gross profit with 6 decimal precision (display rounds to 2)';

-- Update pricing_rule table to support 6 decimal precision for pricing values
ALTER TABLE pricing_rule
    ALTER COLUMN pricing_value TYPE NUMERIC(19,6);

COMMENT ON COLUMN pricing_rule.pricing_value IS 'Pricing value with 6 decimal precision (multipliers, fixed amounts, GP percentages)';
