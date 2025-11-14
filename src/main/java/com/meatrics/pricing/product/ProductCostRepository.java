package com.meatrics.pricing.product;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.meatrics.generated.Tables.PRODUCT_COSTS;

/**
 * Repository for product cost data access
 */
@Repository
public class ProductCostRepository {

    private final DSLContext dsl;

    public ProductCostRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Save a product cost record
     */
    public void save(ProductCost productCost) {
        dsl.insertInto(PRODUCT_COSTS)
                .set(PRODUCT_COSTS.PRODUCT_CODE, productCost.getProductCode())
                .set(PRODUCT_COSTS.DESCRIPTION, productCost.getDescription())
                .set(PRODUCT_COSTS.STANDARD_COST, productCost.getStandardCost())
                .set(PRODUCT_COSTS.LATEST_COST, productCost.getLatestCost())
                .set(PRODUCT_COSTS.AVERAGE_COST, productCost.getAverageCost())
                .set(PRODUCT_COSTS.SUPPLIER_COST, productCost.getSupplierCost())
                .set(PRODUCT_COSTS.SELL_PRICE_1, productCost.getSellPrice1())
                .set(PRODUCT_COSTS.SELL_PRICE_2, productCost.getSellPrice2())
                .set(PRODUCT_COSTS.SELL_PRICE_3, productCost.getSellPrice3())
                .set(PRODUCT_COSTS.SELL_PRICE_4, productCost.getSellPrice4())
                .set(PRODUCT_COSTS.SELL_PRICE_5, productCost.getSellPrice5())
                .set(PRODUCT_COSTS.SELL_PRICE_6, productCost.getSellPrice6())
                .set(PRODUCT_COSTS.SELL_PRICE_7, productCost.getSellPrice7())
                .set(PRODUCT_COSTS.SELL_PRICE_8, productCost.getSellPrice8())
                .set(PRODUCT_COSTS.SELL_PRICE_9, productCost.getSellPrice9())
                .set(PRODUCT_COSTS.SELL_PRICE_10, productCost.getSellPrice10())
                .set(PRODUCT_COSTS.IS_ACTIVE, productCost.getIsActive())
                .set(PRODUCT_COSTS.UNIT_OF_MEASURE, productCost.getUnitOfMeasure())
                .set(PRODUCT_COSTS.WEIGHT, productCost.getWeight())
                .set(PRODUCT_COSTS.CUBIC, productCost.getCubic())
                .set(PRODUCT_COSTS.MIN_STOCK, productCost.getMinStock())
                .set(PRODUCT_COSTS.MAX_STOCK, productCost.getMaxStock())
                .set(PRODUCT_COSTS.BIN_CODE, productCost.getBinCode())
                .set(PRODUCT_COSTS.PRIMARY_GROUP, productCost.getPrimaryGroup())
                .set(PRODUCT_COSTS.SECONDARY_GROUP, productCost.getSecondaryGroup())
                .set(PRODUCT_COSTS.TERTIARY_GROUP, productCost.getTertiaryGroup())
                .set(PRODUCT_COSTS.PRODUCT_CLASS, productCost.getProductClass())
                .set(PRODUCT_COSTS.SUPPLIER_NAME, productCost.getSupplierName())
                .set(PRODUCT_COSTS.SALES_GL_CODE, productCost.getSalesGlCode())
                .set(PRODUCT_COSTS.PURCHASE_GL_CODE, productCost.getPurchaseGlCode())
                .set(PRODUCT_COSTS.COS_GL_CODE, productCost.getCosGlCode())
                .set(PRODUCT_COSTS.SALES_TAX_RATE, productCost.getSalesTaxRate())
                .set(PRODUCT_COSTS.PURCHASE_TAX_RATE, productCost.getPurchaseTaxRate())
                .set(PRODUCT_COSTS.IMPORT_FILENAME, productCost.getImportFilename())
                .onDuplicateKeyUpdate()
                .set(PRODUCT_COSTS.DESCRIPTION, productCost.getDescription())
                .set(PRODUCT_COSTS.STANDARD_COST, productCost.getStandardCost())
                .set(PRODUCT_COSTS.LATEST_COST, productCost.getLatestCost())
                .set(PRODUCT_COSTS.AVERAGE_COST, productCost.getAverageCost())
                .set(PRODUCT_COSTS.SUPPLIER_COST, productCost.getSupplierCost())
                .set(PRODUCT_COSTS.SELL_PRICE_1, productCost.getSellPrice1())
                .set(PRODUCT_COSTS.SELL_PRICE_2, productCost.getSellPrice2())
                .set(PRODUCT_COSTS.SELL_PRICE_3, productCost.getSellPrice3())
                .set(PRODUCT_COSTS.SELL_PRICE_4, productCost.getSellPrice4())
                .set(PRODUCT_COSTS.SELL_PRICE_5, productCost.getSellPrice5())
                .set(PRODUCT_COSTS.IS_ACTIVE, productCost.getIsActive())
                .set(PRODUCT_COSTS.UNIT_OF_MEASURE, productCost.getUnitOfMeasure())
                .set(PRODUCT_COSTS.PRIMARY_GROUP, productCost.getPrimaryGroup())
                .set(PRODUCT_COSTS.IMPORT_FILENAME, productCost.getImportFilename())
                .execute();
    }

    /**
     * Delete all product cost records
     */
    public int deleteAll() {
        return dsl.deleteFrom(PRODUCT_COSTS).execute();
    }

    /**
     * Get all product costs
     */
    public List<ProductCost> findAll() {
        return dsl.selectFrom(PRODUCT_COSTS)
                .fetch(this::mapToProductCost);
    }

    /**
     * Get count of all products
     */
    public int count() {
        return dsl.fetchCount(PRODUCT_COSTS);
    }

    /**
     * Get count of active products
     */
    public int countActive() {
        return dsl.fetchCount(PRODUCT_COSTS, PRODUCT_COSTS.IS_ACTIVE.eq(true));
    }

    /**
     * Get count of products with standard cost
     */
    public int countWithStandardCost() {
        return dsl.fetchCount(PRODUCT_COSTS,
            PRODUCT_COSTS.STANDARD_COST.isNotNull().and(PRODUCT_COSTS.STANDARD_COST.gt(java.math.BigDecimal.ZERO)));
    }

    /**
     * Get distinct product categories (primary_group values)
     * Used for populating category dropdown in pricing rules
     */
    public List<String> findDistinctPrimaryGroups() {
        return dsl.selectDistinct(PRODUCT_COSTS.PRIMARY_GROUP)
                .from(PRODUCT_COSTS)
                .where(PRODUCT_COSTS.PRIMARY_GROUP.isNotNull())
                .orderBy(PRODUCT_COSTS.PRIMARY_GROUP.asc())
                .fetch(PRODUCT_COSTS.PRIMARY_GROUP);
    }

    /**
     * Get distinct product codes
     * Used for populating product code dropdown in pricing rules
     */
    public List<String> findDistinctProductCodes() {
        return dsl.selectDistinct(PRODUCT_COSTS.PRODUCT_CODE)
                .from(PRODUCT_COSTS)
                .where(PRODUCT_COSTS.PRODUCT_CODE.isNotNull())
                .orderBy(PRODUCT_COSTS.PRODUCT_CODE.asc())
                .fetch(PRODUCT_COSTS.PRODUCT_CODE);
    }

    private ProductCost mapToProductCost(org.jooq.Record record) {
        ProductCost pc = new ProductCost();
        pc.setProductCostId(record.get(PRODUCT_COSTS.PRODUCT_COST_ID));
        pc.setProductCode(record.get(PRODUCT_COSTS.PRODUCT_CODE));
        pc.setDescription(record.get(PRODUCT_COSTS.DESCRIPTION));
        pc.setStandardCost(record.get(PRODUCT_COSTS.STANDARD_COST));
        pc.setLatestCost(record.get(PRODUCT_COSTS.LATEST_COST));
        pc.setAverageCost(record.get(PRODUCT_COSTS.AVERAGE_COST));
        pc.setSupplierCost(record.get(PRODUCT_COSTS.SUPPLIER_COST));
        pc.setSellPrice1(record.get(PRODUCT_COSTS.SELL_PRICE_1));
        pc.setIsActive(record.get(PRODUCT_COSTS.IS_ACTIVE));
        pc.setUnitOfMeasure(record.get(PRODUCT_COSTS.UNIT_OF_MEASURE));
        pc.setPrimaryGroup(record.get(PRODUCT_COSTS.PRIMARY_GROUP));
        pc.setImportDate(record.get(PRODUCT_COSTS.IMPORT_DATE));
        pc.setImportFilename(record.get(PRODUCT_COSTS.IMPORT_FILENAME));
        return pc;
    }
}
