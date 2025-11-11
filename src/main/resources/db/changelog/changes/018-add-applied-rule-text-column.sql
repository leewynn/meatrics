-- Add applied_rule TEXT column to store comma-separated rule names
-- This complements applied_rule_id (which stores FK to single rule)
-- Applied_rule stores the full chain of rules applied (e.g., "COST_PLUS_15,VOLUME_DISCOUNT")

ALTER TABLE pricing_session_line_items
ADD COLUMN IF NOT EXISTS applied_rule TEXT;

COMMENT ON COLUMN pricing_session_line_items.applied_rule IS 'Comma-separated list of rule names that were applied to calculate this price';
