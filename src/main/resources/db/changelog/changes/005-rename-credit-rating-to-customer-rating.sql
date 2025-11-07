--liquibase formatted sql

--changeset meatrics:005-rename-credit-rating-to-customer-rating
ALTER TABLE customers RENAME COLUMN credit_rating TO customer_rating;
