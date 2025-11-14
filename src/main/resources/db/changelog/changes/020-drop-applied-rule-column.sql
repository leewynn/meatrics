--liquibase formatted sql

--changeset system:020-drop-applied-rule-column
--comment: Drop the old applied_rule VARCHAR column, replaced by pricing_session_applied_rules table

ALTER TABLE pricing_session_line_items DROP COLUMN IF EXISTS applied_rule;

--rollback ALTER TABLE pricing_session_line_items ADD COLUMN applied_rule TEXT;
