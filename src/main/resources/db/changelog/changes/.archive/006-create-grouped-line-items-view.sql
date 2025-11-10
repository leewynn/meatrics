--liquibase formatted sql

--changeset meatrics:006-create-grouped-line-items-view
CREATE OR REPLACE VIEW v_grouped_line_items AS
SELECT
    customer_code,
    customer_name,
    product_code,
    product_description,
    SUM(quantity) as total_quantity,
    SUM(amount) as total_amount,
    SUM(cost) as total_cost
FROM imported_line_items
GROUP BY customer_code, customer_name, product_code, product_description;

COMMENT ON VIEW v_grouped_line_items IS 'Aggregated line items grouped by customer and product for pricing sessions view';
