--liquibase formatted sql

--changeset meatrics:007-create-pricing-sessions-tables

-- Pricing sessions table to track user pricing review sessions
-- Each session captures a snapshot of pricing modifications made by users
CREATE TABLE pricing_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_name VARCHAR(255) NOT NULL UNIQUE,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    notes TEXT
);

COMMENT ON TABLE pricing_sessions IS 'Tracks pricing review sessions where users can modify and save pricing data';
COMMENT ON COLUMN pricing_sessions.session_name IS 'Unique name for the pricing session';
COMMENT ON COLUMN pricing_sessions.status IS 'Session status: IN_PROGRESS, COMPLETED, ARCHIVED';
COMMENT ON COLUMN pricing_sessions.notes IS 'Optional notes or comments about this pricing session';

CREATE INDEX idx_pricing_sessions_session_name ON pricing_sessions(session_name);

-- Pricing session line items table
-- Stores the grouped line items (customer + product combinations) for each session
-- Tracks price modifications and customer ratings specific to each session
CREATE TABLE pricing_session_line_items (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    customer_code VARCHAR(255),
    customer_name VARCHAR(255),
    customer_rating VARCHAR(50),
    product_code VARCHAR(255),
    product_description TEXT,
    total_quantity NUMERIC(19,2),
    total_amount NUMERIC(19,2),
    original_amount NUMERIC(19,2),
    total_cost NUMERIC(19,2),
    amount_modified BOOLEAN NOT NULL DEFAULT FALSE,

    FOREIGN KEY (session_id) REFERENCES pricing_sessions(id) ON DELETE CASCADE
);

COMMENT ON TABLE pricing_session_line_items IS 'Line items within a pricing session, grouped by customer and product';
COMMENT ON COLUMN pricing_session_line_items.session_id IS 'Foreign key to pricing_sessions table';
COMMENT ON COLUMN pricing_session_line_items.customer_rating IS 'Session-specific customer rating (can be modified within the session)';
COMMENT ON COLUMN pricing_session_line_items.original_amount IS 'Original amount before any modifications (for tracking changes)';
COMMENT ON COLUMN pricing_session_line_items.total_amount IS 'Current amount (may be modified from original)';
COMMENT ON COLUMN pricing_session_line_items.amount_modified IS 'Flag indicating whether the amount was modified from the original';

CREATE INDEX idx_pricing_session_line_items_session_id ON pricing_session_line_items(session_id);
CREATE INDEX idx_pricing_session_line_items_composite ON pricing_session_line_items(session_id, customer_code, product_code);
