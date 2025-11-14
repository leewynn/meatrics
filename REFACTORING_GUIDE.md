# Pricing Package Refactoring Guide

## Overview

This document describes the refactoring of the `com.meatrics.pricing` package from a flat structure (34 files in one package) to a Domain-Driven Design (DDD) structure organized by feature domains.

## Motivation

The flat package structure made it difficult to:
- Understand the system's domain boundaries
- Navigate and locate files quickly
- Maintain separation of concerns
- Scale the codebase as features grow

The new DDD structure groups related classes together, making the architecture clearer and more maintainable.

## New Structure

### Before (Flat Structure)
```
com.meatrics.pricing/
  ├── Customer.java
  ├── CustomerRepository.java
  ├── CustomerRatingService.java
  ├── PricingRule.java
  ├── PricingRuleRepository.java
  ├── PricingSession.java
  ... (34 files total)
```

### After (DDD Structure)
```
com.meatrics.pricing/
  ├── customer/           (4 files)  - Customer domain
  │   ├── Customer.java
  │   ├── CustomerRepository.java
  │   ├── CustomerRatingService.java
  │   └── CustomerRatingReportDTO.java
  │
  ├── rule/               (5 files)  - Pricing rule domain
  │   ├── PricingRule.java
  │   ├── PricingRuleRepository.java
  │   ├── PricingRuleService.java
  │   ├── RuleCategory.java
  │   └── RulePreviewResult.java
  │
  ├── session/            (5 files)  - Pricing session domain
  │   ├── PricingSession.java
  │   ├── PricingSessionRepository.java
  │   ├── PricingSessionService.java
  │   ├── PricingSessionLineItem.java
  │   └── PricingSessionLineItemRepository.java
  │
  ├── calculation/        (3 files)  - Price calculation engine
  │   ├── PriceCalculationService.java
  │   ├── PricePreview.java
  │   └── PricingResult.java
  │
  ├── importer/           (9 files)  - Data import functionality
  │   ├── ImportSummary.java
  │   ├── ImportSummaryRepository.java
  │   ├── ImportedLineItem.java
  │   ├── ImportedLineItemRepository.java
  │   ├── PricingImportService.java
  │   ├── ProductCostImportService.java
  │   ├── CostImportSummary.java
  │   ├── CostImportSummaryRepository.java
  │   └── DuplicateImportException.java
  │
  ├── product/            (4 files)  - Product and cost management
  │   ├── ProductCost.java
  │   ├── ProductCostRepository.java
  │   ├── GroupedLineItem.java
  │   └── GroupedLineItemRepository.java
  │
  ├── report/             (2 files)  - Reporting functionality
  │   ├── ReportExportService.java
  │   └── CostReportDTO.java
  │
  ├── config/             (1 file)   - Configuration and initialization
  │   └── SystemDefaultPricingRuleInitializer.java
  │
  └── ui/                 (existing)  - User interface components
      ├── pricing/
      ├── pricingrules/
      ├── importer/
      ├── reports/
      ├── data/
      └── component/
```

## Domain Descriptions

### customer
Customer management, customer rating calculations, and customer-related DTOs.

### rule
Pricing rule definitions, repository, service, and rule categories. This is the core pricing logic configuration.

### session
Pricing sessions for reviewing and modifying prices. Includes session management and line items.

### calculation
The pricing calculation engine. Applies rules to products to calculate sell prices.

### importer
Excel file import functionality for pricing data and product costs.

### product
Product entities, product costs, and grouped line items for pricing.

### report
Export and reporting services for generating pricing reports.

### config
Configuration and initialization classes (e.g., system default rules).

## Changes Made

### 1. File Moves
- **33 files** moved from `com.meatrics.pricing` to domain-specific sub-packages
- Package declarations updated in all moved files

### 2. Import Updates
All import statements across the codebase were updated:
- **Specific imports**: `import com.meatrics.pricing.Customer;` → `import com.meatrics.pricing.customer.Customer;`
- **Wildcard imports**: `import com.meatrics.pricing.*;` → Multiple specific domain imports

### 3. Files Affected
- All files in `com.meatrics.pricing` package (moved files)
- All files in `com.meatrics.pricing.ui` package (import updates)
- Files in `com.meatrics.base` package (if any references exist)
- `com.meatrics.Application.java` (if references exist)
- Files in `com.meatrics.util` package (if references exist)

## Verification Steps

After running the refactoring:

1. **Check file structure**:
   ```bash
   find src/main/java/com/meatrics/pricing -name "*.java" | head -20
   ```

2. **Verify compilation**:
   ```bash
   mvn clean compile
   ```

3. **Run tests**:
   ```bash
   mvn test
   ```

4. **Check for orphaned files**:
   ```bash
   # Should show only package-info.java and ui/ directory
   ls src/main/java/com/meatrics/pricing/
   ```

## Benefits

1. **Clearer Architecture**: Domain boundaries are explicit
2. **Easier Navigation**: Related files are grouped together
3. **Better Maintainability**: Changes to one domain are localized
4. **Scalability**: New features can be added as new domains
5. **Onboarding**: New developers can understand the system structure faster
6. **IDE Support**: Better code organization for modern IDEs

## Migration Guide

If you have local branches or uncommitted changes:

1. **Commit or stash** your work before running the refactoring
2. **Run the refactoring** using the provided script
3. **Resolve conflicts** if any arise during merge
4. **Update imports** in your local changes to match the new package structure

## Rollback

If you need to rollback:
```bash
git reset --hard HEAD~1  # If you've committed
# OR
git checkout -- .        # If changes are uncommitted
```

## Execution

To perform the refactoring:

```bash
# Dry-run first (recommended)
python3 refactor_pricing_package.py --dry-run

# Execute refactoring
python3 refactor_pricing_package.py
```

## Questions or Issues?

If you encounter any issues after refactoring:
1. Check that all imports have been updated correctly
2. Verify `mvn compile` succeeds
3. Check for any wildcard imports that may need manual adjustment
4. Review git diff to ensure all changes are as expected

## Notes

- The `ui` package structure was already organized and remains unchanged
- jOOQ generated classes (`com.meatrics.generated`) are not affected
- This refactoring does not change any business logic
- All existing tests should continue to pass without modification
