--liquibase formatted sql

--changeset system:019-create-applied-rules-audit-table
--comment: Create pricing_session_applied_rules table for immutable rule application audit trail

CREATE TABLE pricing_session_applied_rules (
    id BIGSERIAL PRIMARY KEY,
    session_line_item_id BIGINT NOT NULL,
    rule_id BIGINT,  -- Nullable: rule might be deleted later

    -- Immutable snapshot of rule at application time
    rule_name VARCHAR(255) NOT NULL,
    rule_category VARCHAR(50) NOT NULL,  -- BASE_PRICE, CUSTOMER_ADJUSTMENT, etc.
    pricing_method VARCHAR(50) NOT NULL, -- COST_PLUS_PERCENT, FIXED_PRICE, etc.
    pricing_value DECIMAL(15,6),         -- The multiplier or value used

    -- Pricing chain tracking
    application_order INT NOT NULL,      -- Order rule was applied (1, 2, 3...)
    input_price DECIMAL(15,6) NOT NULL,  -- Price before this rule
    output_price DECIMAL(15,6) NOT NULL, -- Price after this rule

    -- Metadata
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Foreign keys
    CONSTRAINT fk_applied_rules_line_item
        FOREIGN KEY (session_line_item_id)
        REFERENCES pricing_session_line_items(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_applied_rules_rule
        FOREIGN KEY (rule_id)
        REFERENCES pricing_rule(id)
        ON DELETE SET NULL  -- Keep audit trail even if rule deleted
);

-- Indexes for performance
CREATE INDEX idx_applied_rules_line_item ON pricing_session_applied_rules(session_line_item_id);
CREATE INDEX idx_applied_rules_rule_id ON pricing_session_applied_rules(rule_id);
CREATE INDEX idx_applied_rules_category ON pricing_session_applied_rules(rule_category);

--rollback DROP TABLE pricing_session_applied_rules;
