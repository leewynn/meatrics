package com.meatrics.pricing.ui.pricing;

import com.meatrics.pricing.customer.Customer;
import com.meatrics.pricing.customer.CustomerRepository;
import com.meatrics.pricing.product.GroupedLineItem;
import com.meatrics.pricing.product.GroupedLineItemRepository;
import com.meatrics.pricing.importer.PricingImportService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages filtering logic for pricing sessions.
 * Handles date range filtering and secondary filters (customer, product).
 */
@Component
public class PricingFilterManager {

    private final PricingImportService pricingImportService;
    private final GroupedLineItemRepository groupedLineItemRepository;
    private final CustomerRepository customerRepository;

    public PricingFilterManager(PricingImportService pricingImportService,
                                GroupedLineItemRepository groupedLineItemRepository,
                                CustomerRepository customerRepository) {
        this.pricingImportService = pricingImportService;
        this.groupedLineItemRepository = groupedLineItemRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * Apply date range filter and return filtered items
     *
     * @param startDate Start date (can be null)
     * @param endDate End date (can be null)
     * @return Filtered list of grouped line items
     */
    public List<GroupedLineItem> applyDateFilter(LocalDate startDate, LocalDate endDate) {
        if (startDate != null || endDate != null) {
            return pricingImportService.getGroupedLineItemsByDateRange(startDate, endDate);
        } else {
            return pricingImportService.getGroupedLineItems();
        }
    }

    /**
     * Apply secondary filters (customer name, product) on a backing list
     *
     * @param backingList Full list to filter
     * @param customerFilter Customer name filter (can be null or empty)
     * @param productFilter Product filter (can be null or empty)
     * @return Filtered list
     */
    public List<GroupedLineItem> applySecondaryFilters(
            List<GroupedLineItem> backingList,
            String customerFilter,
            String productFilter) {

        return backingList.stream()
                .filter(item -> {
                    // Customer name filter
                    if (customerFilter != null && !customerFilter.trim().isEmpty()) {
                        String customerName = item.getCustomerName();
                        if (customerName == null ||
                                !customerName.toLowerCase().contains(customerFilter.toLowerCase().trim())) {
                            return false;
                        }
                    }

                    // Product filter
                    if (productFilter != null && !productFilter.trim().isEmpty()) {
                        String productCode = item.getProductCode();
                        String productDesc = item.getProductDescription();
                        String filterLower = productFilter.toLowerCase().trim();

                        boolean matchesCode = productCode != null &&
                                productCode.toLowerCase().contains(filterLower);
                        boolean matchesDesc = productDesc != null &&
                                productDesc.toLowerCase().contains(filterLower);

                        if (!matchesCode && !matchesDesc) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Load data for selected entities (groups and/or standalone companies)
     * For groups, loads all products from member companies and aggregates by product code
     */
    public List<GroupedLineItem> loadDataForEntities(List<Customer> selectedEntities, LocalDate startDate, LocalDate endDate) {
        if (selectedEntities == null || selectedEntities.isEmpty()) {
            // No entities selected - load all data
            return applyDateFilter(startDate, endDate);
        }

        List<GroupedLineItem> allItems = new ArrayList<>();

        for (Customer entity : selectedEntities) {
            if (entity.isGroup()) {
                // Load all products from companies in this group and aggregate by product
                List<Customer> members = customerRepository.findCompaniesByGroupId(entity.getCustomerId());
                List<GroupedLineItem> groupItems = new ArrayList<>();

                for (Customer member : members) {
                    List<GroupedLineItem> memberItems = groupedLineItemRepository.findByCustomerId(member.getCustomerId());
                    groupItems.addAll(memberItems);
                }

                // Aggregate products by product code for the group
                List<GroupedLineItem> aggregatedItems = aggregateByProduct(groupItems, entity);
                allItems.addAll(aggregatedItems);
            } else {
                // Standalone company - load its products
                List<GroupedLineItem> companyItems = groupedLineItemRepository.findByCustomerId(entity.getCustomerId());
                allItems.addAll(companyItems);
            }
        }

        // Apply date filter if specified
        // NOTE: Date filtering not currently implemented because v_grouped_line_items view
        // aggregates all transactions and doesn't expose transaction dates.
        // To implement: either modify the view to include earliest/latest dates,
        // or query imported_line_items directly with date range filtering.
        // For now, date range filter UI exists but does not filter results.

        return allItems;
    }

    /**
     * Aggregate line items by product code for a group
     * Combines all products from different companies into single line items per product
     */
    private List<GroupedLineItem> aggregateByProduct(List<GroupedLineItem> items, Customer group) {
        // Group by product code
        Map<String, List<GroupedLineItem>> byProduct = items.stream()
                .collect(Collectors.groupingBy(GroupedLineItem::getProductCode));

        List<GroupedLineItem> aggregated = new ArrayList<>();

        for (Map.Entry<String, List<GroupedLineItem>> entry : byProduct.entrySet()) {
            List<GroupedLineItem> productItems = entry.getValue();
            GroupedLineItem first = productItems.get(0);

            GroupedLineItem aggregatedItem = new GroupedLineItem();

            // Use group name as customer
            aggregatedItem.setCustomerCode(group.getCustomerCode());
            aggregatedItem.setCustomerName(group.getCustomerName());
            aggregatedItem.setCustomerRating(group.getCustomerRating());

            // Product info from first item (same for all)
            aggregatedItem.setProductCode(first.getProductCode());
            aggregatedItem.setProductDescription(first.getProductDescription());
            aggregatedItem.setPrimaryGroup(first.getPrimaryGroup());
            aggregatedItem.setIncomingCost(first.getIncomingCost());

            // Aggregate quantities and amounts
            BigDecimal totalQty = productItems.stream()
                    .map(GroupedLineItem::getTotalQuantity)
                    .filter(q -> q != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalAmt = productItems.stream()
                    .map(GroupedLineItem::getTotalAmount)
                    .filter(a -> a != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCost = productItems.stream()
                    .map(GroupedLineItem::getTotalCost)
                    .filter(c -> c != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            aggregatedItem.setTotalQuantity(totalQty);
            aggregatedItem.setTotalAmount(totalAmt);
            aggregatedItem.setTotalCost(totalCost);
            aggregatedItem.setLastAmount(totalAmt);

            // Calculate average unit sell price from aggregated totals (use 6 decimals precision)
            if (totalQty != null && totalQty.compareTo(BigDecimal.ZERO) > 0 && totalAmt != null) {
                aggregatedItem.setLastUnitSellPrice(totalAmt.divide(totalQty, 6, RoundingMode.HALF_UP));
            }

            // Calculate average cost from aggregated totals (use 6 decimals precision)
            if (totalQty != null && totalQty.compareTo(BigDecimal.ZERO) > 0 && totalCost != null) {
                aggregatedItem.setLastCost(totalCost.divide(totalQty, 6, RoundingMode.HALF_UP));
            }

            // Calculate gross profit
            if (totalAmt != null && totalCost != null) {
                aggregatedItem.setLastGrossProfit(totalAmt.subtract(totalCost));
            }

            aggregated.add(aggregatedItem);
        }

        return aggregated;
    }

    /**
     * Check for price inconsistencies within groups
     * Returns map of group ID to list of products with price mismatches
     */
    public Map<Long, List<PriceMismatch>> detectGroupPriceInconsistencies(List<Customer> selectedEntities) {
        Map<Long, List<PriceMismatch>> inconsistencies = new HashMap<>();

        for (Customer entity : selectedEntities) {
            if (entity.isGroup()) {
                List<PriceMismatch> groupMismatches = checkGroupPriceConsistency(entity.getCustomerId());
                if (!groupMismatches.isEmpty()) {
                    inconsistencies.put(entity.getCustomerId(), groupMismatches);
                }
            }
        }

        return inconsistencies;
    }

    /**
     * Check price consistency for a specific group
     */
    private List<PriceMismatch> checkGroupPriceConsistency(Long groupId) {
        List<PriceMismatch> mismatches = new ArrayList<>();

        // Get all products from group members
        List<Customer> members = customerRepository.findCompaniesByGroupId(groupId);

        // Group products by product code
        Map<String, List<ProductPrice>> productPrices = new HashMap<>();

        for (Customer member : members) {
            List<GroupedLineItem> items = groupedLineItemRepository.findByCustomerId(member.getCustomerId());
            for (GroupedLineItem item : items) {
                productPrices.computeIfAbsent(item.getProductCode(), k -> new ArrayList<>())
                        .add(new ProductPrice(
                                item.getProductCode(),
                                item.getProductDescription(),
                                item.getLastUnitSellPrice(),
                                member.getCustomerCode()
                        ));
            }
        }

        // Find products with multiple different prices
        for (Map.Entry<String, List<ProductPrice>> entry : productPrices.entrySet()) {
            List<ProductPrice> prices = entry.getValue();
            if (prices.size() > 1) {
                // Check if all prices are the same
                Set<BigDecimal> uniquePrices = prices.stream()
                        .map(ProductPrice::getLastPrice)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                if (uniquePrices.size() > 1) {
                    mismatches.add(new PriceMismatch(
                            entry.getKey(),
                            prices.get(0).getProductName(),
                            prices
                    ));
                }
            }
        }

        return mismatches;
    }

    /**
     * DTO for price mismatch information
     */
    public static class PriceMismatch {
        private final String productCode;
        private final String productName;
        private final List<ProductPrice> prices;

        public PriceMismatch(String productCode, String productName, List<ProductPrice> prices) {
            this.productCode = productCode;
            this.productName = productName;
            this.prices = prices;
        }

        public String getProductCode() {
            return productCode;
        }

        public String getProductName() {
            return productName;
        }

        public List<ProductPrice> getPrices() {
            return prices;
        }
    }

    /**
     * DTO for product price information
     */
    public static class ProductPrice {
        private final String productCode;
        private final String productName;
        private final BigDecimal lastPrice;
        private final String companyCode;

        public ProductPrice(String productCode, String productName, BigDecimal lastPrice, String companyCode) {
            this.productCode = productCode;
            this.productName = productName;
            this.lastPrice = lastPrice;
            this.companyCode = companyCode;
        }

        public String getProductCode() {
            return productCode;
        }

        public String getProductName() {
            return productName;
        }

        public BigDecimal getLastPrice() {
            return lastPrice;
        }

        public String getCompanyCode() {
            return companyCode;
        }
    }
}
