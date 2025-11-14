package com.meatrics.pricing.calculation;

import java.math.BigDecimal;

/**
 * Preview of how a pricing rule would price a single product.
 * Used in the rule preview/test feature to show calculated prices before saving.
 */
public class PricePreview {
    private final String productCode;
    private final String productName;
    private final BigDecimal cost;
    private final BigDecimal calculatedPrice;

    public PricePreview(String productCode, String productName, BigDecimal cost, BigDecimal calculatedPrice) {
        this.productCode = productCode;
        this.productName = productName;
        this.cost = cost;
        this.calculatedPrice = calculatedPrice;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public BigDecimal getCalculatedPrice() {
        return calculatedPrice;
    }
}
