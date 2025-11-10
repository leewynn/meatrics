--liquibase formatted sql

--changeset meatrics:012-make-pricing-value-nullable

-- Make pricing_value nullable to support MAINTAIN_GP_PERCENT without default fallback
-- When null, MAINTAIN_GP_PERCENT will only use historical GP% from transactions
ALTER TABLE pricing_rule
ALTER COLUMN pricing_value DROP NOT NULL;

COMMENT ON COLUMN pricing_rule.pricing_value IS 'Value for calculation (e.g., 1.20 for 20% markup, 5.00 for $5 markup). Can be NULL for MAINTAIN_GP_PERCENT (uses historical GP% only)';
