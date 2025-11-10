--liquibase formatted sql

--changeset meatrics:014-add-historical-pricing-fields

-- Add historical pricing fields to pricing_session_line_items table
-- These fields are needed for the MAINTAIN_GP_PERCENT pricing rule to work correctly
-- Without these fields, the pricing engine falls back to the default 25% GP instead of
-- maintaining the historical GP% for each product

ALTER TABLE pricing_session_line_items
ADD COLUMN last_cost NUMERIC(19,2),
ADD COLUMN last_unit_sell_price NUMERIC(19,2),
ADD COLUMN incoming_cost NUMERIC(19,2),
ADD COLUMN primary_group VARCHAR(255);

COMMENT ON COLUMN pricing_session_line_items.last_cost IS 'Historical average unit cost from imported data (used for GP% calculation)';
COMMENT ON COLUMN pricing_session_line_items.last_unit_sell_price IS 'Historical average unit sell price from imported data (used for GP% calculation)';
COMMENT ON COLUMN pricing_session_line_items.incoming_cost IS 'Incoming cost from product_costs table (new cost for pricing)';
COMMENT ON COLUMN pricing_session_line_items.primary_group IS 'Product category/primary group for rule matching';
