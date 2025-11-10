# Archived Migrations

This folder contains the original incremental migrations that were consolidated into `001-initial-schema.sql` for the alpha release.

## Consolidation Date
2025-11-11

## Reason for Consolidation
- Alpha deployment phase (no production databases exist yet)
- Simplified schema management
- Clean starting point with 6-decimal precision throughout

## Original Migrations (in execution order)
1. `001-create-staging-tables.sql` - Import staging tables
2. `002-create-views.sql` - Customer, product, invoice views
3. `003-create-product-cost-tables.sql` - Product costs master data
4. `004-create-customer-table.sql` - Customer master table
5. `006-create-grouped-line-items-view.sql` - Grouped line items view
6. `007-create-pricing-sessions-tables.sql` - Pricing sessions
7. `009-create-pricing-rules-table.sql` - Pricing rules engine
8. `015-add-rule-categories.sql` - Multi-rule layered pricing
9. `016-create-multi-rule-tracking.sql` - Rule application tracking
10. `017-increase-precision-to-6-decimals.sql` - 6-decimal precision upgrade

## What Changed in Consolidation
- All tables created with NUMERIC(19,6) from the start
- Pricing rule table includes `rule_category`, `layer_order`, `valid_from`, `valid_to` columns
- Multi-rule tracking table included
- All indexes, foreign keys, and constraints included
- All comments preserved

## If You Need to Reference Old Migrations
These files are preserved here for historical reference and can be restored if needed. They represent the evolution of the schema during development.

## Important Note
**Do NOT apply these migrations to new databases.** Use `001-initial-schema.sql` instead. These are kept only for reference.
