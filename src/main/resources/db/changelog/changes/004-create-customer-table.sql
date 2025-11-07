--liquibase formatted sql

--changeset meatrics:004-create-customer-table
CREATE TABLE customers (
    customer_id BIGSERIAL PRIMARY KEY,
    customer_code VARCHAR(128) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    credit_rating VARCHAR(50),
    notes TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_customer_code UNIQUE (customer_code)
);

-- Index for lookups by customer code
CREATE INDEX idx_customers_code ON customers(customer_code);
