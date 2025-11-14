package com.meatrics.pricing.ui.pricing;

import com.meatrics.pricing.product.GroupedLineItem;
import com.meatrics.pricing.importer.PricingImportService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages filtering logic for pricing sessions.
 * Handles date range filtering and secondary filters (customer, product).
 */
@Component
public class PricingFilterManager {

    private final PricingImportService pricingImportService;

    public PricingFilterManager(PricingImportService pricingImportService) {
        this.pricingImportService = pricingImportService;
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
}
