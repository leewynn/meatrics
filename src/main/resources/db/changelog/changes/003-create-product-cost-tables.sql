-- Product cost master table
CREATE TABLE product_costs (
    product_cost_id BIGSERIAL PRIMARY KEY,
    product_code VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    
    -- Primary cost field
    standard_cost DECIMAL(12,2),
    
    -- Additional cost fields
    latest_cost DECIMAL(12,2),
    average_cost DECIMAL(12,2),
    supplier_cost DECIMAL(12,2),
    
    -- Sell prices (multiple tiers)
    sell_price_1 DECIMAL(12,2),
    sell_price_2 DECIMAL(12,2),
    sell_price_3 DECIMAL(12,2),
    sell_price_4 DECIMAL(12,2),
    sell_price_5 DECIMAL(12,2),
    sell_price_6 DECIMAL(12,2),
    sell_price_7 DECIMAL(12,2),
    sell_price_8 DECIMAL(12,2),
    sell_price_9 DECIMAL(12,2),
    sell_price_10 DECIMAL(12,2),
    
    -- Product attributes
    is_active BOOLEAN DEFAULT TRUE,
    unit_of_measure VARCHAR(20),
    weight DECIMAL(10,3),
    cubic DECIMAL(10,3),
    min_stock DECIMAL(10,2),
    max_stock DECIMAL(10,2),
    bin_code VARCHAR(50),
    
    -- Classification
    primary_group VARCHAR(100),
    secondary_group VARCHAR(100),
    tertiary_group VARCHAR(100),
    product_class VARCHAR(100),
    supplier_name VARCHAR(255),
    
    -- GL codes
    sales_gl_code VARCHAR(50),
    purchase_gl_code VARCHAR(50),
    cos_gl_code VARCHAR(50),
    
    -- Tax rates
    sales_tax_rate DECIMAL(5,2),
    purchase_tax_rate DECIMAL(5,2),
    
    -- Metadata
    import_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    import_filename VARCHAR(255),
    
    CONSTRAINT uq_product_code UNIQUE (product_code)
);

CREATE INDEX idx_product_costs_code ON product_costs(product_code);
CREATE INDEX idx_product_costs_active ON product_costs(is_active);
CREATE INDEX idx_product_costs_group ON product_costs(primary_group);

-- Cost import summary table
CREATE TABLE cost_import_summary (
    cost_import_id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    import_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_products INTEGER,
    active_products INTEGER,
    products_with_cost INTEGER,
    import_status VARCHAR(50),
    error_message TEXT
);
