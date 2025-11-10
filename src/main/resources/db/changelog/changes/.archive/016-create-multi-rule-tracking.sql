--liquibase formatted sql

--changeset meatrics:016-create-multi-rule-tracking

-- Create table to track multiple pricing rules applied to each line item
-- Supports layered pricing where multiple rules can be applied in sequence
-- Records the complete pricing calculation chain: BASE → CUSTOMER → PRODUCT → PROMOTIONAL

CREATE TABLE pricing_session_line_item_rules (
    id BIGSERIAL PRIMARY KEY,

    -- Link to the line item this rule was applied to
    line_item_id BIGINT NOT NULL,

    -- Link to the pricing rule that was applied
    rule_id BIGINT NOT NULL,

    -- Category/layer this rule belongs to
    layer_category VARCHAR(50) NOT NULL,

    -- Order in which this rule was applied (1, 2, 3...)
    application_order INT NOT NULL,

    -- Price before this rule was applied
    price_before NUMERIC(10,2) NOT NULL,

    -- Price after this rule was applied
    price_after NUMERIC(10,2) NOT NULL,

    -- Audit timestamp
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key to pricing_session_line_items table
    CONSTRAINT fk_line_item_rules_line_item
        FOREIGN KEY (line_item_id)
        REFERENCES pricing_session_line_items(id)
        ON DELETE CASCADE,

    -- Foreign key to pricing_rule table
    CONSTRAINT fk_line_item_rules_rule
        FOREIGN KEY (rule_id)
        REFERENCES pricing_rule(id)
        ON DELETE CASCADE,

    -- Prevent the same rule from being applied twice to the same line item
    CONSTRAINT uq_line_item_rule
        UNIQUE (line_item_id, rule_id)
);

COMMENT ON TABLE pricing_session_line_item_rules IS 'Tracks all pricing rules applied to each line item in a pricing session. Supports multi-rule layered pricing by recording the complete calculation chain.';

COMMENT ON COLUMN pricing_session_line_item_rules.line_item_id IS 'Foreign key to pricing_session_line_items - which line item this rule was applied to';

COMMENT ON COLUMN pricing_session_line_item_rules.rule_id IS 'Foreign key to pricing_rule - which rule was applied';

COMMENT ON COLUMN pricing_session_line_item_rules.layer_category IS 'Category/layer of the rule: BASE_PRICE, CUSTOMER_ADJUSTMENT, PRODUCT_ADJUSTMENT, or PROMOTIONAL. Determines execution order.';

COMMENT ON COLUMN pricing_session_line_item_rules.application_order IS 'Sequence order in which this rule was applied (1 = first rule, 2 = second rule, etc.). Used to reconstruct the pricing calculation chain.';

COMMENT ON COLUMN pricing_session_line_item_rules.price_before IS 'Price before this rule was applied. For the first rule (order=1), this is the base cost. For subsequent rules, this is price_after from the previous rule.';

COMMENT ON COLUMN pricing_session_line_item_rules.price_after IS 'Price after this rule was applied. This becomes price_before for the next rule in the chain.';

COMMENT ON COLUMN pricing_session_line_item_rules.created_at IS 'Timestamp when this rule application was recorded';

-- Create index for efficient lookup by line item
CREATE INDEX idx_line_item_rules_line_item ON pricing_session_line_item_rules(line_item_id);

-- Create index for efficient lookup by rule (to see where a rule is being used)
CREATE INDEX idx_line_item_rules_rule ON pricing_session_line_item_rules(rule_id);

-- Create composite index for efficient queries by line item and application order
-- Useful for reconstructing the pricing calculation chain in order
CREATE INDEX idx_line_item_rules_order ON pricing_session_line_item_rules(line_item_id, application_order);
