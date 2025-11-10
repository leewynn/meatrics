# Pricing Engine Design Discussion

**Document Created:** 2025-11-09
**Last Updated:** 2025-11-09 (Added MAINTAIN_GP_PERCENT pricing method - Phase 2 Complete)
**Status:** Phase 1 Complete, Phase 2 Backend Complete
**Related Documents:** `proposed_pricing_solution.md`, `PROJECT_OVERVIEW.md`

## Purpose

This document captures the ongoing design discussion for implementing a rule-based pricing engine in the Meatrics application. The engine will provide flexible, configurable pricing logic to replace the current manual price adjustment workflow.

## Background

### Current System State

**Existing Data Structures:**
- **product_costs table**: Contains standard costs and multiple sell price columns (sell_price_1 through sell_price_10)
- **customers table**: Contains customer information including calculated customer ratings
- **pricing_sessions**: Saved pricing scenarios with line items and modifications
- **imported_line_items**: Sales transaction data with actual prices and costs

**Current Workflow:**
1. User imports sales data from Excel files
2. User creates/loads a pricing session (grouped by customer + product)
3. User manually adjusts unit sell prices via dialog interface
4. System calculates total amount based on: new_unit_sell_price × quantity
5. User saves pricing session for later analysis

**Limitations:**
- No automated pricing logic based on business rules
- Manual, time-consuming price adjustments
- No systematic way to apply pricing strategies (cost-plus, customer-based, category-based)
- Sell price columns in product_costs table are not currently utilized by the application

### Customer Rating System (Existing)

**Rating Storage Format:**
Customer ratings are stored as a composite string in the format:
```
"original: X | modified: Y | claude: Z"
```

**Three Rating Algorithms:**
1. **Original**: `sqrt((amount / 1000 × GP%) × 100)` - User's proposed formula
2. **Modified**: `(amount / 1000) + (GP% × 10)` - Additive version addressing multiplicative zero problem
3. **Claude**: `(Gross_Profit_Dollars × 0.7) + (Revenue_Percentile × 0.3)` - Weighted approach prioritizing gross profit

**Rating Calculation:**
- Automatically calculated after every pricing data import (background @Async)
- Manual recalculation available via "Recalculate All Customer Ratings" button
- Ratings represent customer profitability/value based on historical sales data

**Current Usage:**
- Displayed in grids for informational purposes
- Available for editing in customer edit dialog
- **Not yet used for automated pricing decisions** - this is a key opportunity for the new pricing engine

## Proposed Solution Overview

From `proposed_pricing_solution.md`, the plan includes:

### Backend Components
1. **New Database Table**: `pricing_rule` - stores pricing rules
2. **New Services**:
   - `PricingRuleService` - CRUD operations for pricing rules
   - `PriceCalculationService` - central price calculation engine using rules
3. **Modified Service**: `PricingSessionService` - integrate with new calculation engine

### Frontend Components
1. **New View**: `PricingRulesView` - UI for managing pricing rules
2. **Enhanced View**: `PricingSessionsView` - trigger rule-based pricing instead of manual

### Finalized Database Schema

**Single-Table Design (MVP):**
```sql
CREATE TABLE pricing_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(255) NOT NULL,
    customer_code VARCHAR(50),        -- NULL = standard rule, value = customer-specific
    condition_type VARCHAR(50),        -- 'ALL_PRODUCTS', 'CATEGORY', 'PRODUCT_CODE'
    condition_value VARCHAR(255),      -- Category name or product code (NULL for ALL_PRODUCTS)
    pricing_method VARCHAR(50),        -- 'COST_PLUS_PERCENT', 'COST_PLUS_FIXED', 'FIXED_PRICE', 'MAINTAIN_GP_PERCENT'
    pricing_value DECIMAL(10,4),       -- Multiplier, amount, or default GP% (depends on pricing_method)
    priority INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);
```

**Design Rationale:**
- Simple single-table approach minimizes complexity for MVP
- Customer-specific rules distinguished by `customer_code` field (NULL = standard)
- Three straightforward condition types cover most use cases
- Priority system handles rule precedence and fallback logic
- Can be extended later with additional tables if needed

## Design Discussion Points

### 1. User Experience for Creating Pricing Rules

**DECISION FINALIZED: Rule Preview/Test Feature**

Users need confidence before saving pricing rules. The Rule Preview feature allows users to test their rule and see exactly which products it will match and what prices it will calculate BEFORE saving.

**User Workflow:**
1. User creates or edits a pricing rule in the dialog
2. User clicks "Test This Rule" button
3. System opens preview dialog showing:
   - For specific rules (CATEGORY or PRODUCT_CODE): Full scrollable grid of ALL matching products with calculated prices
   - For "All Products" rules: Simple message showing total count (no grid for performance)
4. User reviews the preview and can save directly from preview dialog

**Key Benefits:**
- Eliminates guesswork - users see exact impact before committing
- Builds confidence in the pricing engine
- Helps catch mistakes (e.g., wrong category name, wrong multiplier)
- Educational - helps users understand how rules work
- Prevents "oops" moments with incorrect rules

**UI Patterns Implemented:**
- Grid-based rule management (similar to existing views)
- Rule builder with dropdowns for conditions and actions
- Preview/dry-run functionality to see rule impact before saving
- Rule enable/disable toggles for testing

### 2. Customer-Specific Pricing Rules

**DECISION FINALIZED:**

**Custom Pricing with Fallback:**
- Customer-specific rules are identified by the `customer_code` field in the `pricing_rule` table
- When calculating prices for a customer:
  1. System searches for both customer-specific rules (where `customer_code = <customer>`) and standard rules (where `customer_code IS NULL`)
  2. Rules are evaluated in priority order (lower priority number = higher precedence)
  3. First matching rule is applied
- This provides flexibility - partial custom pricing is valid and safe

**Key Benefits:**
- No need to create exhaustive custom rules covering every product
- Customers can have selective custom pricing (e.g., only for beef products)
- All other products automatically use standard pricing
- Simple implementation - rule matching considers both customer-specific and standard rules

**"All Products" Default Pattern (KEY FEATURE):**
Rules can apply to "ALL_PRODUCTS" using the `ALL_PRODUCTS` condition type:
- Example: "ABC Meats - Default Markup: All Products Cost + 20%"
- Then create specific overrides with higher priority:
  - "ABC Meats - Beef: Category BEEF, Cost + 15%" (Priority 1000)
  - "ABC Meats - Ribeye Special: Product RIBEYE, Cost + 12%" (Priority 100)
- Priority determines which rule wins (lower number = higher priority)
- Provides a safety net while allowing granular control

**Rule Creation Workflow:**
- When creating customer-specific rules, users start from a blank slate
- No "copy from standard" feature initially (can be added later if requested)
- The fallback mechanism makes this safe:
  - If you create 3 custom rules for a customer, those 3 products use custom pricing
  - All other products fall back to standard rules automatically
  - No risk of missing products or undefined pricing

**Implementation Requirements:**
1. Customer-specific rules distinguished by `customer_code` field (NULL = standard, value = customer-specific)
2. Rule engine searches for applicable rules combining both customer-specific and standard rules
3. Priority system ensures customer-specific rules can override standard rules when needed
4. Standard rules are always evaluated as fallback for ALL customers

### 3. Rule Condition Types

**DECISION FINALIZED:**

The MVP will support three simple, powerful condition types:

**1. ALL_PRODUCTS**
- Matches every product for this rule's customer scope
- `condition_type = 'ALL_PRODUCTS'`
- `condition_value = NULL`
- Used for default markup rules
- Example: "ABC Meats - Default: All Products Cost + 20%"

**2. CATEGORY**
- Matches products in a specific category
- `condition_type = 'CATEGORY'`
- `condition_value = <category name>` (e.g., 'BEEF', 'PORK', 'CHICKEN')
- Uses product's primary_group field for matching
- Example: "Standard Beef Pricing: Category BEEF, Cost + 30%"

**3. PRODUCT_CODE**
- Matches a specific product by its code
- `condition_type = 'PRODUCT_CODE'`
- `condition_value = <product code>` (e.g., 'RIBEYE', 'T-BONE')
- Exact match on product code
- Example: "ABC Ribeye Special: Product RIBEYE, Cost + 12%"

**Why This Is Sufficient:**
- These three types cover 90% of real-world pricing scenarios
- Simple to understand and implement
- Customer-specific rules are handled by the `customer_code` field, not conditions
- Rating-based tiers are deferred to Phase 2 (not in MVP)
- Additional condition types can be added later without schema changes

**No Multi-Condition Logic Initially:**
- Each rule has exactly one condition (or none for ALL_PRODUCTS)
- No AND/OR logic between conditions in MVP
- Complex scenarios handled by multiple rules with different priorities
- Keeps rule creation simple and predictable

### 4. Pricing Methods (Rule Actions)

**DECISION FINALIZED:**

The system supports four essential pricing methods:

**1. COST_PLUS_PERCENT**
- Multiply cost by a value to add percentage markup
- `sell_price = cost × pricing_value`
- Example: `pricing_value = 1.20` means cost × 1.20 (20% markup)
- Example: `pricing_value = 1.15` means cost × 1.15 (15% markup)
- Most common method, simple and predictable
- **Note:** Store the multiplier, not the percentage (1.20, not 20)

**2. COST_PLUS_FIXED**
- Add a fixed dollar amount to cost
- `sell_price = cost + pricing_value`
- Example: `pricing_value = 5.00` means cost + $5.00
- Useful for low-cost items where percentage would be too small
- Example use case: "Always add $2.50 to processing fees"

**3. FIXED_PRICE**
- Ignore cost, use a fixed price
- `sell_price = pricing_value`
- Example: `pricing_value = 25.00` means always $25.00 regardless of cost
- Useful for contract pricing or standardized products
- Example use case: "ABC Corp contract price for Ribeye is always $28.50/lb"

**4. MAINTAIN_GP_PERCENT** (Phase 2 - IMPLEMENTED)
- Calculate price to maintain historical gross profit percentage
- `sell_price = incoming_cost / (1 - historical_gp%)`
- Data-driven: Uses actual historical transaction data when available
- Fallback: Uses `pricing_value` as default GP% when no historical data exists
- GP% Capping: Automatically caps between 10% and 60% to prevent unrealistic margins
- Example with historical data: If last sale was $10 cost, $12 price (16.7% GP), and new cost is $11, then new price = $11 / (1 - 0.167) = $13.21
- Example with default: If no history and `pricing_value = 0.25`, price = cost / 0.75
- **Use case:** Maintain consistent profit margins for customer relationships, automatically adjust prices as costs change
- **Implementation status:** TESTED and DEPLOYED

**Historical GP% Calculation:**
```
Historical GP% = (lastUnitSellPrice - lastCost) / lastUnitSellPrice
```
Where `lastUnitSellPrice` and `lastCost` come from `v_grouped_line_items` historical data.

**Safety Features:**
- Constants: `MIN_GP = 10%`, `MAX_GP = 60%`
- Prevents negative GP% (would cause invalid pricing)
- Prevents division by zero (if historical price is 0)
- Falls back gracefully when historical data unavailable
- Caps unrealistic margins automatically

**Why These Four:**
- Cover 95% of real-world pricing scenarios
- Mix of static (methods 1-3) and data-driven (method 4) approaches
- MAINTAIN_GP_PERCENT adds intelligence and automation
- Simple to implement and understand
- Can combine using priority system for sophisticated pricing
- Additional methods (quantity breaks, dynamic) can be added in Phase 3

**Future Methods (Deferred to Phase 3):**
- Sell Price Column Reference (use sell_price_1 through sell_price_10 from product_costs)
- Quantity Break Pricing (different margins for quantity tiers)
- Rating-Based Dynamic Pricing (adjust based on customer value)

### 5. Rule Priority and Application

**DECISION FINALIZED:**

**First Match Wins Strategy:**
- Each rule has a numeric priority (lower number = higher priority)
- Rules evaluated in priority order from lowest to highest (1, 2, 3, ...)
- **First matching rule wins** - short-circuit evaluation, no further rules checked
- Simplest to understand and predict
- Forces clear thinking about rule organization

**Priority Ranges (Recommended Convention):**
```
1-999:      Specific product overrides (highest priority)
            Examples: Custom contract pricing, special product deals

1000-4999:  Category-based rules
            Examples: "All Beef Products", "Pork Category Pricing"

5000-8999:  "All Products" customer defaults and broad rules
            Examples: "ABC Meats - Default Markup for All Products"

9000+:      System defaults and fallbacks
            Examples: "Standard Default Pricing for All Products"
```

**Rule Matching Logic:**
1. Filter rules by customer scope:
   - Include customer-specific rules (customer_code = X) for the given customer
   - Always include standard rules (customer_code IS NULL) as fallback
2. Filter rules by product match:
   - Check if product matches the rule's condition (ALL_PRODUCTS, CATEGORY, or PRODUCT_CODE)
3. Filter only active rules:
   - `is_active = TRUE`
4. Sort by priority (ascending):
   - Lowest number first
5. Return first match:
   - Apply the pricing method and return calculated price

**Conflict Resolution:**
- If multiple rules at the same priority match, use the rule with the lowest `id` (oldest first)
- This is deterministic and predictable
- Users should avoid same-priority conflicts by using the priority ranges appropriately

**No-Match Scenario:**
- Every system MUST have at least one default rule (ALL_PRODUCTS, priority 9999, customer_code NULL)
- This ensures every product always gets priced
- System should validate this constraint and prevent deletion of the last default rule

### 6. Customer Rating Integration

**DECISION FINALIZED: DEFERRED TO PHASE 2**

**Rationale for Deferral:**
- Customer ratings are valuable but add complexity to MVP
- Current rating system has three algorithms (original/modified/claude) - unclear which to use
- Rating-based tiers require additional UI and configuration
- Focus MVP on core customer-specific and product/category rules
- Rating integration can be added later without breaking existing rules

**Current State:**
- Customer ratings are calculated and stored
- Available in the database for future use
- Displayed in UI for informational purposes

**Future Integration (Phase 2):**
When rating-based pricing is added, likely approach will be:
- Add a new condition type: `CUSTOMER_RATING`
- Allow rules to specify rating algorithm (original/modified/claude)
- Support numeric range matching (e.g., rating between 150-250)
- Example: "High-Value Customers (Claude Rating > 200): Cost + 15%"

**Current Workaround:**
- Users can create customer-specific rules for high-value customers manually
- Set the customer_code field to the specific customer in the pricing rule
- Create explicit rules for those customers
- This provides similar functionality without automated rating tiers

## Concrete Rule Examples

These examples demonstrate the finalized design in action:

### Example 1: ABC Meats Customer Setup (Custom Pricing with "All Products" + Overrides)

**Customer Configuration:**
- Customer Code: `ABC_MEATS`

**Rules:**
```sql
-- Rule 1: Default for all ABC Meats products
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('ABC Meats - Default Markup', 'ABC_MEATS', 'ALL_PRODUCTS', NULL, 'COST_PLUS_PERCENT', 1.20, 5000);

-- Rule 2: Override for all beef products
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('ABC Meats - Beef Category', 'ABC_MEATS', 'CATEGORY', 'BEEF', 'COST_PLUS_PERCENT', 1.15, 1000);

-- Rule 3: Specific override for Ribeye
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('ABC Meats - Ribeye Special', 'ABC_MEATS', 'PRODUCT_CODE', 'RIBEYE', 'COST_PLUS_PERCENT', 1.12, 100);
```

**How It Works:**
- ABC_MEATS orders Ribeye → matches Rule 3 (priority 100) → Cost × 1.12 = 12% markup
- ABC_MEATS orders T-Bone (BEEF category) → matches Rule 2 (priority 1000) → Cost × 1.15 = 15% markup
- ABC_MEATS orders Pork Chops → matches Rule 1 (priority 5000) → Cost × 1.20 = 20% markup
- ABC_MEATS orders obscure product not in rules → falls back to standard rules

### Example 2: Standard Rules (No Customer-Specific Pricing)

**Rules:**
```sql
-- Standard pricing for beef category
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('Standard Beef Pricing', NULL, 'CATEGORY', 'BEEF', 'COST_PLUS_PERCENT', 1.30, 1000);

-- Standard pricing for pork category
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('Standard Pork Pricing', NULL, 'CATEGORY', 'PORK', 'COST_PLUS_PERCENT', 1.25, 1000);

-- Default fallback for all products
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('Default Markup', NULL, 'ALL_PRODUCTS', NULL, 'COST_PLUS_PERCENT', 1.35, 9000);
```

**How It Works:**
- Any customer (without custom pricing) orders beef → Cost × 1.30 = 30% markup
- Any customer orders pork → Cost × 1.25 = 25% markup
- Any customer orders chicken (no specific rule) → Cost × 1.35 = 35% markup

### Example 3: Mixed Custom and Standard (Partial Custom Pricing)

**Customer Configuration:**
- Customer Code: `XYZ_CORP`

**Rules:**
```sql
-- XYZ Corp only has custom pricing for one specific product
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('XYZ Corp - Ribeye Contract', 'XYZ_CORP', 'PRODUCT_CODE', 'RIBEYE', 'FIXED_PRICE', 28.50, 100);

-- Standard rules still exist (from Example 2 above)
```

**How It Works:**
- XYZ_CORP orders Ribeye → matches custom rule → Fixed price $28.50 (ignores cost)
- XYZ_CORP orders T-Bone → no custom rule matches → falls back to "Standard Beef Pricing" → Cost × 1.30
- XYZ_CORP orders Pork → no custom rule matches → falls back to "Standard Pork Pricing" → Cost × 1.25

This demonstrates the power of fallback: XYZ_CORP only needs ONE custom rule for their contract item, everything else uses standard pricing automatically.

### Example 4: Fixed Price for Contract Items

**Rules:**
```sql
-- Government contract pricing (fixed regardless of cost)
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('Gov Contract - Ground Beef', 'GOV_AGENCY', 'PRODUCT_CODE', 'GROUND_BEEF', 'FIXED_PRICE', 12.00, 50);

-- Same customer, different product with cost-plus
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('Gov Contract - Other Beef', 'GOV_AGENCY', 'CATEGORY', 'BEEF', 'COST_PLUS_PERCENT', 1.18, 1000);
```

**How It Works:**
- GOV_AGENCY orders Ground Beef → Fixed price $12.00 per lb (regardless of whether cost is $8 or $10)
- GOV_AGENCY orders Ribeye → Cost × 1.18 = 18% markup (dynamic based on actual cost)

### Example 5: Cost Plus Fixed Amount

**Rules:**
```sql
-- Add fixed processing fee to all chicken products
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('Chicken Processing Fee', NULL, 'CATEGORY', 'CHICKEN', 'COST_PLUS_FIXED', 2.50, 1000);
```

**How It Works:**
- Any customer orders chicken breast (cost $5.00) → $5.00 + $2.50 = $7.50
- Any customer orders chicken thighs (cost $3.50) → $3.50 + $2.50 = $6.00
- Useful when processing/handling fees are consistent regardless of product value

### Example 6: Maintain GP Percentage (Data-Driven Pricing)

**Rules:**
```sql
-- Maintain historical margins for premium customer
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('Premium Corp - Maintain Margins', 'PREMIUM_CORP', 'ALL_PRODUCTS', NULL, 'MAINTAIN_GP_PERCENT', 0.25, 5000);

-- Maintain category-specific margins for all customers
INSERT INTO pricing_rule (rule_name, customer_code, condition_type, condition_value, pricing_method, pricing_value, priority)
VALUES ('Standard Beef - Auto Adjust', NULL, 'CATEGORY', 'BEEF', 'MAINTAIN_GP_PERCENT', 0.30, 1000);
```

**How It Works - With Historical Data:**
- PREMIUM_CORP orders Ribeye
- Historical data: Last cost $10.00, Last price $12.00 → GP% = ($12-$10)/$12 = 16.7%
- Incoming cost: $10.50 (cost increased)
- Calculated price: $10.50 / (1 - 0.167) = $10.50 / 0.833 = $12.60
- **Result:** Price automatically increases to maintain 16.7% GP despite cost increase

**How It Works - Without Historical Data (Fallback):**
- NEW_CUSTOMER orders Pork (no historical transactions)
- Rule specifies default GP% = 25% (pricing_value = 0.25)
- Incoming cost: $6.00
- Calculated price: $6.00 / (1 - 0.25) = $6.00 / 0.75 = $8.00
- **Result:** Uses default 25% GP since no historical data exists

**How It Works - With GP% Capping:**
- Customer has historical GP% of 75% (unusually high from special promotion)
- System caps at MAX_GP = 60% to prevent unrealistic pricing
- Incoming cost: $5.00
- Calculated price: $5.00 / (1 - 0.60) = $5.00 / 0.40 = $12.50 (instead of $20.00 at 75%)
- **Result:** Prevents extreme pricing from anomalous historical data

**Display in UI:**
- With historical data: "Maintained 16.7% GP"
- Without historical data: "Maintain GP% (default 25%)"
- After capping: "Maintained 60% GP (capped)"

## Implementation Phases

### Phase 1: Core Rule Engine (MVP)
**Goal:** Simple, functional rule-based pricing with essential features

**Database Changes:**
1. Create `pricing_rule` table (single table design from schema above)
2. Create Liquibase migration: `008-create-pricing-rules.sql`
3. Generate jOOQ classes for new table

**Backend Components:**

**Entities and Repositories:**
- `PricingRule` entity (maps to pricing_rule table)
- `PricingRuleRepository` (jOOQ-based CRUD operations)

**Services:**
- `PricingRuleService` - CRUD operations for pricing rules
  - Create, read, update, delete rules
  - Validate at least one default rule exists
  - Prevent deletion of last default rule
- `PriceCalculationService` - core rule evaluation engine
  - Find matching rules for a given customer/product combination
  - Apply first-match-wins logic with priority ordering
  - Calculate price using the matched rule's pricing method
  - Return both the price and the rule that was applied (for transparency)

**Rule Matching Logic (in PriceCalculationService):**
```java
PricingResult calculatePrice(String customerCode, String productCode, String productCategory, BigDecimal cost) {
    // 1. Build rule query:
    //    - Include customer-specific rules (customer_code = X)
    //    - Include standard rules (customer_code IS NULL)
    //    - Filter by is_active = TRUE
    //    - Order by priority ASC, id ASC
    // 2. For each rule (in priority order):
    //    - Check if condition matches:
    //      - ALL_PRODUCTS: always matches
    //      - CATEGORY: matches if productCategory equals condition_value
    //      - PRODUCT_CODE: matches if productCode equals condition_value
    //    - If match found, apply pricing method and return
    // 3. If no match (should never happen if default rule exists), throw exception
}
```

**Pricing Methods (in PriceCalculationService):**
- COST_PLUS_PERCENT: `price = cost × pricing_value`
- COST_PLUS_FIXED: `price = cost + pricing_value`
- FIXED_PRICE: `price = pricing_value`
- MAINTAIN_GP_PERCENT: `price = cost / (1 - historical_gp%)` with fallback to `cost / (1 - pricing_value)`

**Frontend Components:**

**New View: PricingRulesView**
- Grid showing all pricing rules
- Columns: Rule Name, Customer Code, Condition Type, Condition Value, Pricing Method, Pricing Value, Priority, Active
- Add/Edit/Delete rule buttons
- Simple form dialog for rule creation/editing
- Validation: Ensure at least one default rule exists

**Enhanced View: PricingSessionsView**
- Add "Apply Pricing Rules" button
- When clicked, recalculate all line item prices using PriceCalculationService
- Add column: "Applied Rule" - shows which rule calculated the price
- Format: "Rule Name (Method)" e.g., "ABC Beef Premium (Cost + 15%)"
- Allow manual override of rule-calculated prices (store override flag)

**Enhanced View: Customer Edit Dialog**
- Display information about creating customer-specific pricing rules
- Explain how to set customer_code in pricing rules to create customer-specific rules

**Transparency Feature (CRITICAL):**
- Every pricing session line item must show which rule was applied
- Store this in pricing_session_line_items table (add column: applied_rule_name)
- Display in grid column or tooltip
- Format examples:
  - "ABC Meats - Ribeye Special (Cost × 1.12)"
  - "Standard Beef Pricing (Cost × 1.30)"
  - "Default Markup (Cost × 1.35)"
  - "Manual Override" (if user manually changed the price)

### Phase 2: Enhanced Features
**Goal:** Add features deferred from MVP
**Status:** PARTIALLY COMPLETE - MAINTAIN_GP_PERCENT implemented and tested

**Completed in Phase 2:**
- [x] **MAINTAIN_GP_PERCENT Pricing Method** - IMPLEMENTED
  - Data-driven pricing using historical GP%
  - Formula: `sell_price = cost / (1 - historical_gp%)`
  - Fallback to default GP% when no historical data exists
  - Safety features: MIN_GP (10%), MAX_GP (60%), negative GP% handling, division by zero protection
  - Full integration with pricing engine and UI transparency
  - Tested and deployed

**Customer Rating Integration (Deferred to Phase 3):**
- Add CUSTOMER_RATING condition type
- Allow rules to specify which rating algorithm to use (original/modified/claude)
- Support numeric range matching (e.g., rating between 150-250)
- Example: "High-Value Customers (Claude Rating > 200): Cost + 15%"

**Additional Pricing Methods (Deferred to Phase 3):**
- Sell Price Column Reference: Use sell_price_1, sell_price_2, etc. from product_costs
- Quantity Break Pricing: Different margins for different quantity ranges

**Pattern Matching (Deferred to Phase 3):**
- Product code patterns (e.g., "BEEF*" matches all beef product codes)
- Category hierarchies (e.g., match on secondary_group, tertiary_group)

**Rule Testing and Preview (Deferred to Future):**
- "Preview" mode - show what prices would be without saving
- Rule simulation - test rule against current session data
- Impact analysis - show how many line items affected by each rule

### Phase 3: Advanced Features
**Goal:** Enterprise-grade capabilities

**Multi-Condition Rules:**
- Support AND/OR logic between conditions
- Separate pricing_rule_condition table for complex matching
- Example: "Customer rating > 150 AND product category = BEEF AND quantity > 100"

**Analytics and Optimization:**
- Rule effectiveness reports (which rules are used most frequently)
- Price variance analysis (compare rule-calculated vs. historical prices)
- Margin optimization suggestions
- Profitability dashboards

**Workflow and Governance:**
- Rule versioning/history (track changes over time)
- Rule approval workflow for large organizations
- Rule templates/cloning
- Bulk rule import/export (CSV, Excel)
- Scheduled rule activation (effective date ranges)

**Advanced Pricing:**
- Competitive pricing integration (if competitor data available)
- Dynamic pricing based on market conditions
- Volume discount schedules
- Promotional pricing campaigns

## Technical Considerations

### Performance

**Rule Evaluation Efficiency:**
- With many rules, evaluation could be slow
- Index priority and is_active for fast filtering
- Cache compiled rule conditions
- Consider pre-computing eligible rules per product/customer combination

**Database Queries:**
- Avoid N+1 queries when loading rules with conditions and actions
- Use joins or batch loading
- Consider denormalizing for performance if needed

**Session Processing:**
- Pricing sessions can have hundreds or thousands of line items
- Batch processing with progress indicator
- Allow rule application to be cancelled mid-process

### Data Integrity

**Rule Validation:**
- Ensure at least one default rule exists
- Warn if rules have duplicate priorities
- Validate condition values (e.g., numeric ranges are valid)
- Prevent deletion of last default rule

**Audit Trail:**
- Log which rule calculated which price
- Store rule snapshot with each pricing session
- Enable "why this price?" explanation for any line item

**Rule Dependencies:**
- Warn if changing/deleting a rule affects existing saved sessions
- Consider "archive" instead of delete for rules used in sessions

### User Experience

**Transparency:**
- Always show which rule determined a price
- Provide "explain this price" feature
- Visual indicators for rule-based vs. manually adjusted prices

**Flexibility:**
- Allow manual overrides of rule-calculated prices
- Support temporary rule disable without deletion
- Easy rule reordering (priority management)

**Safety:**
- Confirm before applying rules to large datasets
- Preview/dry-run before committing changes
- Undo/rollback for pricing sessions

## Design Questions - Status

### RESOLVED Questions

1. **Which rating algorithm should be the default?**
   - **RESOLVED:** Deferred to Phase 2. No rating-based pricing in MVP.

2. **Should users be able to manually override rule-calculated prices?**
   - **RESOLVED:** Yes. Store override flag and show "Manual Override" in applied rule column.

3. **How should conflicts between rules at the same priority be resolved?**
   - **RESOLVED:** Use rule with lowest ID (oldest first). Deterministic and predictable.

4. **Should rule application be automatic or require user action?**
   - **RESOLVED:** Require user action. "Apply Pricing Rules" button in Pricing Sessions view.

5. **What should happen if a product has no matching rules?**
   - **RESOLVED:** Must have at least one default rule (ALL_PRODUCTS, priority 9000+). System validates this.

6. **Should rules be global or session-specific?**
   - **RESOLVED:** Global rules. Sessions use rules at time of application. Manual overrides are session-specific.

7. **Should there be multi-condition logic (AND/OR)?**
   - **RESOLVED:** Not in MVP. Deferred to Phase 3. Simple single-condition rules for now.

8. **What condition types should be supported?**
   - **RESOLVED:** Three types: ALL_PRODUCTS, CATEGORY, PRODUCT_CODE. Sufficient for MVP.

9. **What pricing methods should be supported?**
   - **RESOLVED:** Three methods: COST_PLUS_PERCENT, COST_PLUS_FIXED, FIXED_PRICE.

10. **How should customer-specific pricing work?**
    - **RESOLVED:** Customer-specific rules identified by customer_code field + fallback to standard rules. Simple and safe.

### Open Questions (Future Phases)

1. **How should seasonal/date-based pricing work?**
   - Phase 3: Add effective_date_from and effective_date_to columns to pricing_rule table

2. **Should there be rule approval workflow for large organizations?**
   - Phase 3: Add approval_status, approved_by, approved_at columns

3. **How should we handle products with missing cost data?**
   - Edge case. Can only use FIXED_PRICE method. Validate and warn user.

4. **Should rules support complex mathematical expressions?**
   - Phase 3 enhancement if needed. Not critical for initial release.

5. **Should we integrate with external pricing APIs?**
   - Future consideration. Would require new pricing methods and integration layer.

6. **Should rules be exportable/importable for backup or sharing?**
   - Phase 2 or 3: Add export/import functionality (CSV, Excel)

## Next Steps - Implementation Roadmap

### Phase 1 Implementation Tasks (MVP - Ready to Start)

**Database Tasks:**
1. Create Liquibase migration `008-create-pricing-rules.sql`
   - CREATE TABLE pricing_rule (as defined in schema above)
2. Create Liquibase migration `009-pricing-sessions-comparison-columns.sql`
   - ALTER TABLE pricing_session_line_items - add columns for historical vs new comparison data
   - Add columns: last_cost, last_unit_sell_price, last_amount, last_gross_profit
   - Add columns: incoming_cost, new_unit_sell_price, new_amount, new_gross_profit
   - Add columns: applied_rule_id (FK to pricing_rule), manual_override (boolean)
   - Add foreign key constraint and index for applied_rule_id
3. Run migrations to apply schema changes
4. Generate jOOQ classes: `mvn clean generate-sources`
5. Verify generated PricingRule and updated PricingSessionLineItem entities

**Backend Implementation:**
1. Create `PricingRule.java` entity class (package: com.meatrics.pricing)
2. Create `PricingRuleRepository.java` (jOOQ-based repository)
3. Create `PricingRuleService.java`:
   - CRUD operations for pricing rules
   - Validation: Ensure at least one default rule exists
   - Prevent deletion of last default rule
   - **Rule Preview/Test method** (see detailed implementation below)
4. Create `PriceCalculationService.java`:
   - Implement rule matching logic (pseudocode provided in Phase 1 section above)
   - Implement pricing method calculations (COST_PLUS_PERCENT, COST_PLUS_FIXED, FIXED_PRICE)
   - Return PricingResult with calculated price and applied rule name
5. Update `PricingSessionLineItemRepository.java` to support applied_rule_name field

**Frontend Implementation:**
1. Create `PricingRulesView.java`:
   - Grid with columns: Rule Name, Customer Code, Condition Type, Condition Value, Pricing Method, Pricing Value, Priority, Active
   - Add/Edit/Delete buttons
   - Form dialog for rule creation/editing with **"Test This Rule" button**
   - Validation and user-friendly error messages
   - **Rule Preview Dialog** (see detailed implementation below)
2. **REDESIGN `PricingSessionsView.java` - MAJOR FEATURE** (see "Pricing Sessions View - Complete Redesign" section):
   - Implement nested header structure with grouped columns
   - Add "Historical (Last)" column group showing old pricing data
   - Add "New Pricing" column group showing rule-calculated pricing
   - Add customer rating column for context
   - Implement "Apply Rules" button with full pricing engine integration
   - Implement visual indicators: orange backgrounds for manual overrides, info icons for rule transparency
   - Implement GP color coding (green for improvement, red for degradation)
   - Add rule details popup dialog (click info icon to see calculation details)
   - Support manual price override with inline editing
   - Update session save/load to persist all comparison data and metadata
3. Update Customer edit dialog:
   - Add information about creating customer-specific pricing rules
   - Add help text explaining how to set customer_code in rules

**Testing:**
1. Create default rule: "Default Markup" (ALL_PRODUCTS, 1.35, priority 9000)
2. Test standard pricing on session
3. Create customer-specific rules with overrides
4. Test fallback behavior (customer-specific → standard)
5. Test ALL_PRODUCTS + category + product priority hierarchy
6. Test transparency feature (applied rule display)
8. **Test Rule Preview feature:**
   - Test CATEGORY rule preview (should show grid with all matching products)
   - Test PRODUCT_CODE rule preview (should show grid with one product)
   - Test ALL_PRODUCTS rule preview (should show count only, no grid)
   - Test invalid category (should show 0 products)
   - Test save from preview dialog

**Documentation:**
1. Update HANDOFF.md with pricing engine overview
2. Update PROJECT_OVERVIEW.md with new components
3. Create user guide section for pricing rules management

### Success Criteria for MVP (Phase 1)
- [x] Can create and manage pricing rules via UI
- [x] Can flag customers for custom pricing
- [x] Rules calculate prices correctly using all four methods (COST_PLUS_PERCENT, COST_PLUS_FIXED, FIXED_PRICE, MAINTAIN_GP_PERCENT)
- [x] Priority system works (lower number wins)
- [x] Fallback from custom to standard rules works
- [x] ALL_PRODUCTS condition works as default/catchall
- [x] Applied rule name is displayed for every line item
- [x] Cannot delete last default rule
- [x] Manual override capability works
- [ ] **Rule Preview/Test feature works:**
  - [ ] "Test This Rule" button appears in rule create/edit dialog
  - [ ] Preview shows grid with all matching products for CATEGORY/PRODUCT_CODE rules
  - [ ] Preview shows simple message for ALL_PRODUCTS rules
  - [ ] Can save rule directly from preview dialog
  - [ ] Preview correctly calculates prices using all three pricing methods
- [ ] **Pricing Sessions View redesign complete:**
  - [ ] Nested headers display correctly with grouped columns
  - [ ] Historical data loads and displays accurately from v_grouped_line_items
  - [ ] "Apply Rules" button calculates all prices using pricing engine
  - [ ] GP color coding provides clear visual feedback (green/red)
  - [ ] Info icon shows correct rule details in popup dialog
  - [ ] Manual override works with inline editing
  - [ ] Orange background and "Manual Override" indicator show for overridden prices
  - [ ] Session save persists all comparison data and metadata
  - [ ] Session load restores all data including rule references and overrides
  - [ ] Grid performance is acceptable with 100+ line items

## UI Mockups and Transparency Feature

### Transparency Requirement (CRITICAL)

Every pricing decision must be traceable and visible to the user. This builds trust and helps users understand and debug pricing.

**Pricing Sessions Grid - Applied Rule Column:**

```
+-------------+-------------+-----------+------+------------+----------------+----------------------------------+
| Customer    | Product     | Quantity  | Cost | Unit Price | Total Amount   | Applied Rule                     |
+-------------+-------------+-----------+------+------------+----------------+----------------------------------+
| ABC_MEATS   | RIBEYE      | 50        | 8.50 | 9.52       | 476.00         | ABC Meats - Ribeye (Cost×1.12)   |
| ABC_MEATS   | T-BONE      | 30        | 7.25 | 8.34       | 250.20         | ABC Meats - Beef (Cost×1.15)     |
| ABC_MEATS   | PORK_CHOP   | 40        | 4.00 | 4.80       | 192.00         | ABC Meats - Default (Cost×1.20)  |
| XYZ_CORP    | RIBEYE      | 25        | 8.50 | 28.50      | 712.50         | XYZ Corp - Ribeye (Fixed)        |
| XYZ_CORP    | T-BONE      | 20        | 7.25 | 9.43       | 188.60         | Standard Beef (Cost×1.30)        |
| PREMIUM_CO  | RIBEYE      | 15        | 10.50| 12.60      | 189.00         | Maintain Margins (Maintained 16.7% GP)|
| PREMIUM_CO  | PORK        | 25        | 6.00 | 8.00       | 200.00         | Maintain Margins (Maintain GP% default 25%)|
| GENERIC_CO  | CHICKEN     | 100       | 3.50 | 6.00       | 600.00         | Chicken Fee (Cost+$2.50)         |
| GENERIC_CO  | GROUND_BEEF | 75        | 4.20 | 5.67       | 425.25         | Default Markup (Cost×1.35)       |
| GENERIC_CO  | RIBEYE      | 10        | 8.50 | 11.00      | 110.00         | Manual Override ⚠                |
+-------------+-------------+-----------+------+------------+----------------+----------------------------------+
```

**Applied Rule Format:**
- **Rule Name (Method)** - Clear, concise
- Examples:
  - `ABC Meats - Ribeye (Cost×1.12)` - Shows rule name and calculation
  - `Standard Beef (Cost×1.30)` - Standard rule
  - `XYZ Corp - Ribeye (Fixed)` - Fixed price rule
  - `Chicken Fee (Cost+$2.50)` - Fixed amount addition
  - `Maintain Margins (Maintained 16.7% GP)` - GP% method with historical data
  - `Maintain Margins (Maintain GP% default 25%)` - GP% method using fallback default
  - `Manual Override ⚠` - User manually changed the price
- Tooltip on hover could show:
  - Full rule name
  - Condition matched (ALL_PRODUCTS / CATEGORY: BEEF / PRODUCT_CODE: RIBEYE)
  - Pricing method and value
  - Original cost
  - Calculation breakdown

**Pricing Rules Management Grid:**

```
+-----------------------------+--------------+----------------+-----------------+-----------------+---------------+----------+--------+
| Rule Name                   | Customer     | Condition Type | Condition Value | Pricing Method  | Pricing Value | Priority | Active |
+-----------------------------+--------------+----------------+-----------------+-----------------+---------------+----------+--------+
| ABC Meats - Ribeye Special  | ABC_MEATS    | PRODUCT_CODE   | RIBEYE          | COST_PLUS_PCT   | 1.12          | 100      | ✓      |
| ABC Meats - Beef Category   | ABC_MEATS    | CATEGORY       | BEEF            | COST_PLUS_PCT   | 1.15          | 1000     | ✓      |
| ABC Meats - Default Markup  | ABC_MEATS    | ALL_PRODUCTS   | (null)          | COST_PLUS_PCT   | 1.20          | 5000     | ✓      |
| XYZ Corp - Ribeye Contract  | XYZ_CORP     | PRODUCT_CODE   | RIBEYE          | FIXED_PRICE     | 28.50         | 100      | ✓      |
| Premium Corp - Maintain GP  | PREMIUM_CORP | ALL_PRODUCTS   | (null)          | MAINTAIN_GP_PCT | 0.25          | 5000     | ✓      |
| Standard Beef - Auto Adjust | (Standard)   | CATEGORY       | BEEF            | MAINTAIN_GP_PCT | 0.30          | 1000     | ✓      |
| Standard Beef Pricing       | (Standard)   | CATEGORY       | BEEF            | COST_PLUS_PCT   | 1.30          | 2000     | ✓      |
| Standard Pork Pricing       | (Standard)   | CATEGORY       | PORK            | COST_PLUS_PCT   | 1.25          | 1000     | ✓      |
| Chicken Processing Fee      | (Standard)   | CATEGORY       | CHICKEN         | COST_PLUS_FIX   | 2.50          | 1000     | ✓      |
| Default Markup              | (Standard)   | ALL_PRODUCTS   | (null)          | COST_PLUS_PCT   | 1.35          | 9000     | ✓      |
+-----------------------------+--------------+----------------+-----------------+-----------------+---------------+----------+--------+
```

**Customer Edit Dialog:**

```
┌──────────────────────────────────────────────────┐
│ Edit Customer: ABC Meats                         │
├──────────────────────────────────────────────────┤
│                                                  │
│ Customer Code: ABC_MEATS                         │
│ Customer Name: ABC Meats Corporation             │
│ ...                                              │
│                                                  │
│ NOTE: To create customer-specific pricing       │
│ rules for this customer, go to:                 │
│ Pricing → Pricing Rules                         │
│                                                  │
│ Customer-specific rules are identified by       │
│ setting customer_code = ABC_MEATS in the rule.  │
│                                                  │
│              [Cancel]  [Save]                    │
└──────────────────────────────────────────────────┘
```

## Rule Application Logic - Detailed Pseudocode

This pseudocode can be used as a reference for implementing the `PriceCalculationService`:

```java
/**
 * Calculate price for a product using pricing rules
 *
 * @param customerCode Customer code (e.g., "ABC_MEATS")
 * @param productCode Product code (e.g., "RIBEYE")
 * @param productCategory Product primary group/category (e.g., "BEEF")
 * @param cost Product cost
 * @return PricingResult containing calculated price and rule name
 */
public PricingResult calculatePrice(
    String customerCode,
    String productCode,
    String productCategory,
    BigDecimal cost
) {
    // STEP 1: Get applicable rules (both customer-specific and standard)
    List<PricingRule> rules = pricingRuleRepository.findApplicableRules(customerCode);
    // Query filters:
    //   - WHERE is_active = TRUE
    //   - WHERE customer_code IS NULL (standard rules)
    //      OR customer_code = :customerCode (customer-specific rules)
    //   - ORDER BY priority ASC, id ASC

    // STEP 2: Find first matching rule
    for (PricingRule rule : rules) {
        boolean matches = false;

        // Check if rule condition matches this product
        switch (rule.getConditionType()) {
            case "ALL_PRODUCTS":
                matches = true;  // Always matches
                break;

            case "CATEGORY":
                matches = rule.getConditionValue().equalsIgnoreCase(productCategory);
                break;

            case "PRODUCT_CODE":
                matches = rule.getConditionValue().equalsIgnoreCase(productCode);
                break;
        }

        // If condition matches, apply pricing method and return
        if (matches) {
            BigDecimal calculatedPrice = applyPricingMethod(
                rule.getPricingMethod(),
                rule.getPricingValue(),
                cost
            );

            // Format rule description for transparency
            String ruleDescription = formatRuleDescription(rule);

            return new PricingResult(calculatedPrice, ruleDescription, rule.getRuleName());
        }
    }

    // STEP 3: No rule matched (should never happen if default rule exists)
    throw new PricingException(
        "No pricing rule matched for customer=" + customerCode +
        ", product=" + productCode +
        ". Ensure at least one default rule (ALL_PRODUCTS) exists."
    );
}

/**
 * Apply pricing method calculation
 */
private BigDecimal applyPricingMethod(
    String method,
    BigDecimal value,
    BigDecimal cost,
    BigDecimal lastCost,           // For MAINTAIN_GP_PERCENT
    BigDecimal lastUnitSellPrice   // For MAINTAIN_GP_PERCENT
) {
    switch (method) {
        case "COST_PLUS_PERCENT":
            // value = 1.20 for 20% markup
            // price = cost × 1.20
            return cost.multiply(value).setScale(2, RoundingMode.HALF_UP);

        case "COST_PLUS_FIXED":
            // value = 2.50 for $2.50 addition
            // price = cost + 2.50
            return cost.add(value).setScale(2, RoundingMode.HALF_UP);

        case "FIXED_PRICE":
            // value = 28.50 for fixed $28.50
            // price = 28.50 (ignore cost)
            return value.setScale(2, RoundingMode.HALF_UP);

        case "MAINTAIN_GP_PERCENT":
            // Calculate historical GP% or use default
            BigDecimal gpPercent = calculateGPPercent(lastCost, lastUnitSellPrice, value);

            // price = cost / (1 - GP%)
            BigDecimal divisor = BigDecimal.ONE.subtract(gpPercent);
            if (divisor.compareTo(BigDecimal.ZERO) <= 0) {
                // Safety: If GP% >= 100%, fall back to default
                divisor = BigDecimal.ONE.subtract(value);
            }
            return cost.divide(divisor, 2, RoundingMode.HALF_UP);

        default:
            throw new PricingException("Unknown pricing method: " + method);
    }
}

/**
 * Calculate GP% from historical data with safety checks
 * Constants: MIN_GP = 0.10 (10%), MAX_GP = 0.60 (60%)
 */
private BigDecimal calculateGPPercent(
    BigDecimal lastCost,
    BigDecimal lastUnitSellPrice,
    BigDecimal defaultGP
) {
    // If no historical data, use default
    if (lastCost == null || lastUnitSellPrice == null ||
        lastUnitSellPrice.compareTo(BigDecimal.ZERO) == 0) {
        return defaultGP;
    }

    // Calculate historical GP%: (price - cost) / price
    BigDecimal grossProfit = lastUnitSellPrice.subtract(lastCost);
    BigDecimal gpPercent = grossProfit.divide(lastUnitSellPrice, 4, RoundingMode.HALF_UP);

    // Apply safety caps
    BigDecimal MIN_GP = new BigDecimal("0.10");  // 10%
    BigDecimal MAX_GP = new BigDecimal("0.60");  // 60%

    if (gpPercent.compareTo(MIN_GP) < 0) {
        return MIN_GP;  // Cap at minimum 10%
    }
    if (gpPercent.compareTo(MAX_GP) > 0) {
        return MAX_GP;  // Cap at maximum 60%
    }
    if (gpPercent.compareTo(BigDecimal.ZERO) < 0) {
        return defaultGP;  // Negative GP%, use default
    }

    return gpPercent;
}

/**
 * Format rule description for display in UI
 * Examples:
 *   "ABC Meats - Ribeye (Cost×1.12)"
 *   "Standard Beef (Cost×1.30)"
 *   "XYZ Corp - Ribeye (Fixed)"
 *   "Chicken Fee (Cost+$2.50)"
 *   "Premium Corp - Maintain Margins (Maintained 18.5% GP)"
 *   "Standard Beef - Auto Adjust (Maintain GP% default 25%)"
 */
private String formatRuleDescription(
    PricingRule rule,
    BigDecimal appliedGP  // Actual GP% used (may differ from pricing_value if historical data used)
) {
    StringBuilder sb = new StringBuilder(rule.getRuleName());
    sb.append(" (");

    switch (rule.getPricingMethod()) {
        case "COST_PLUS_PERCENT":
            sb.append("Cost×").append(rule.getPricingValue());
            break;
        case "COST_PLUS_FIXED":
            sb.append("Cost+$").append(rule.getPricingValue());
            break;
        case "FIXED_PRICE":
            sb.append("Fixed");
            break;
        case "MAINTAIN_GP_PERCENT":
            if (appliedGP != null) {
                // Show actual GP% that was used
                BigDecimal gpPercent = appliedGP.multiply(new BigDecimal("100"));
                sb.append("Maintained ").append(gpPercent.setScale(1, RoundingMode.HALF_UP)).append("% GP");
            } else {
                // Fallback to default (used when no historical data)
                BigDecimal defaultGP = rule.getPricingValue().multiply(new BigDecimal("100"));
                sb.append("Maintain GP% default ").append(defaultGP.setScale(0, RoundingMode.HALF_UP)).append("%");
            }
            break;
    }

    sb.append(")");
    return sb.toString();
}

/**
 * Result object containing price and applied rule info
 */
public class PricingResult {
    private BigDecimal price;
    private String ruleDescription;  // For UI display
    private String ruleName;         // For storage in line items

    // Constructor, getters...
}
```

**Repository Query Example (jOOQ):**

```java
public List<PricingRule> findApplicableRules(String customerCode) {
    // Include both customer-specific and standard rules
    return dsl.selectFrom(PRICING_RULE)
        .where(PRICING_RULE.IS_ACTIVE.eq(true))
        .and(
            PRICING_RULE.CUSTOMER_CODE.isNull()  // Standard rules
            .or(PRICING_RULE.CUSTOMER_CODE.eq(customerCode))  // Customer-specific rules
        )
        .orderBy(PRICING_RULE.PRIORITY.asc(), PRICING_RULE.ID.asc())
        .fetchInto(PricingRule.class);
}
```

## Rule Preview/Test Feature - Detailed Specification

### Overview

The Rule Preview/Test Feature is a critical user experience component that allows users to test pricing rules BEFORE saving them. This builds confidence, prevents mistakes, and helps users understand how the pricing engine works.

### User Interface Design

#### Button in Create/Edit Rule Dialog

```
┌─────────────────────────────────────┐
│ New Pricing Rule                    │
├─────────────────────────────────────┤
│ Rule Name: [ABC Beef Premium     ] │
│ Applies To: [Category: BEEF      ▼] │
│ Pricing Method: [Cost + 15%      ▼] │
│                                     │
│      [Test This Rule] [Cancel] [Save]│
└─────────────────────────────────────┘
```

**Button Characteristics:**
- Label: "Test This Rule"
- Theme: Tertiary (less prominent than Save)
- Position: Left side of button group
- Enabled: Only when rule form has valid input

#### Preview Dialog for Specific Rules (Category/Product)

When testing a CATEGORY or PRODUCT_CODE rule, show full grid of matching products:

```
┌─────────────────────────────────────────────────────┐
│ Rule Preview: ABC Beef Premium                      │
├─────────────────────────────────────────────────────┤
│ This rule will match 47 products                    │
│                                                     │
│ ┌─────────────────────────────────────────────────┐│
│ │ Product Code │ Product Name │ Cost   │New Price││
│ ├──────────────┼──────────────┼────────┼─────────┤│
│ │ RIBEYE-001   │ Ribeye Steak │ $10.00 │ $11.50  ││
│ │ SIRLOIN-002  │ Sirloin      │ $8.50  │ $9.78   ││
│ │ TBONE-003    │ T-Bone       │ $12.00 │ $13.80  ││
│ │ BRISKET-004  │ Brisket      │ $7.00  │ $8.05   ││
│ │ ...          │ ...          │ ...    │ ...     ││
│ │ (scrollable - shows ALL 47 products)            ││
│ └─────────────────────────────────────────────────┘│
│                                                     │
│                          [Close] [Save Rule]        │
└─────────────────────────────────────────────────────┘
```

**Dialog Characteristics:**
- Width: 800px
- Height: 600px
- Grid Height: 400px (scrollable)
- Shows ALL matching products (no pagination limit)
- Columns: Product Code, Product Name, Cost, New Price
- Two buttons: Close (tertiary), Save Rule (primary)

#### Preview Dialog for "All Products" Rule

When testing an ALL_PRODUCTS rule, show simple message (no grid for performance):

```
┌─────────────────────────────────────┐
│ Rule Preview: ABC Default Markup    │
├─────────────────────────────────────┤
│ This will match ALL products        │
│ (347 total)                         │
│                                     │
│ All products in the system will     │
│ be priced using this rule.          │
│                                     │
│                [Close] [Save Rule]  │
└─────────────────────────────────────┘
```

**Design Rationale:**
- ALL_PRODUCTS rules could match hundreds or thousands of products
- Loading and rendering all products would be slow and unnecessary
- Users only need to know the total count, not individual products
- Keeps the preview fast and responsive

### Backend Implementation

#### Data Structures

**RulePreviewResult Class:**
```java
package com.meatrics.pricing;

import java.util.List;

/**
 * Result of previewing a pricing rule before saving
 */
public class RulePreviewResult {
    private int totalMatchCount;
    private boolean isAllProducts;
    private List<PricePreview> previews;  // Empty for ALL_PRODUCTS

    public RulePreviewResult(int totalMatchCount, boolean isAllProducts, List<PricePreview> previews) {
        this.totalMatchCount = totalMatchCount;
        this.isAllProducts = isAllProducts;
        this.previews = previews;
    }

    /**
     * Factory method for ALL_PRODUCTS rules - no detailed preview needed
     */
    public static RulePreviewResult allProducts(int count) {
        return new RulePreviewResult(count, true, List.of());
    }

    // Getters...
}
```

**PricePreview Class:**
```java
package com.meatrics.pricing;

import java.math.BigDecimal;

/**
 * Preview of how a pricing rule would price a single product
 */
public class PricePreview {
    private String productCode;
    private String productName;
    private BigDecimal cost;
    private BigDecimal calculatedPrice;

    public PricePreview(String productCode, String productName, BigDecimal cost, BigDecimal calculatedPrice) {
        this.productCode = productCode;
        this.productName = productName;
        this.cost = cost;
        this.calculatedPrice = calculatedPrice;
    }

    // Getters...
}
```

#### Service Method - PricingRuleService.previewRule()

```java
/**
 * Preview what products a rule would match and calculate sample prices
 * Tests against ALL products in product_costs table
 *
 * @param ruleToTest Pricing rule DTO with user's current form values
 * @return RulePreviewResult with match count and price calculations
 */
public RulePreviewResult previewRule(PricingRuleDTO ruleToTest) {
    // Special case: ALL_PRODUCTS - just return count, no grid
    if ("ALL_PRODUCTS".equals(ruleToTest.getConditionType())) {
        int totalCount = productCostRepository.countAll();
        return RulePreviewResult.allProducts(totalCount);
    }

    // Query based on condition type
    List<ProductCost> matchingProducts = switch(ruleToTest.getConditionType()) {
        case "CATEGORY" ->
            productCostRepository.findByCategory(ruleToTest.getConditionValue());
        case "PRODUCT_CODE" ->
            productCostRepository.findByProductCode(ruleToTest.getConditionValue());
        default -> List.of();
    };

    // Calculate prices for ALL matching products
    List<PricePreview> previews = matchingProducts.stream()
        .map(product -> {
            BigDecimal calculatedPrice = applyPricingMethod(
                product.getStdcost(),
                ruleToTest.getPricingMethod(),
                ruleToTest.getPricingValue()
            );
            return new PricePreview(
                product.getProductCode(),
                product.getProductName(),
                product.getStdcost(),
                calculatedPrice
            );
        })
        .collect(Collectors.toList());

    return new RulePreviewResult(matchingProducts.size(), false, previews);
}

/**
 * Apply pricing method calculation - same logic as PriceCalculationService
 */
private BigDecimal applyPricingMethod(BigDecimal cost, String method, BigDecimal value) {
    return switch(method) {
        case "COST_PLUS_PERCENT" -> cost.multiply(value).setScale(2, RoundingMode.HALF_UP);
        case "COST_PLUS_FIXED" -> cost.add(value).setScale(2, RoundingMode.HALF_UP);
        case "FIXED_PRICE" -> value.setScale(2, RoundingMode.HALF_UP);
        default -> cost;
    };
}
```

**Key Design Points:**
- Tests against `product_costs` table, NOT pricing session data
- Shows ALL matching products (no artificial limit)
- No conflict detection or rule priority checking (keep it simple)
- For ALL_PRODUCTS: Just count, no expensive query for all products
- Shares pricing method calculation logic with PriceCalculationService

### Frontend Implementation - Vaadin

#### Test Button in Rule Dialog

```java
/**
 * In PricingRuleDialog or similar component
 */
private void createTestButton() {
    Button testButton = new Button("Test This Rule", e -> showPreview());
    testButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    // Enable only when form is valid
    binder.addStatusChangeListener(event -> {
        testButton.setEnabled(!event.hasValidationErrors());
    });

    return testButton;
}
```

#### Preview Dialog Implementation

```java
private void showPreview() {
    // Build DTO from current form values (not yet saved)
    PricingRuleDTO testRule = buildRuleFromForm();

    // Call service to preview
    RulePreviewResult result = pricingRuleService.previewRule(testRule);

    // Create and configure dialog
    Dialog previewDialog = new Dialog();
    previewDialog.setWidth("800px");
    previewDialog.setHeight("600px");
    previewDialog.setHeaderTitle("Rule Preview: " + testRule.getRuleName());

    // Different content based on rule type
    if (result.isAllProducts()) {
        // Simple message for ALL_PRODUCTS
        VerticalLayout content = new VerticalLayout();
        content.add(new H4("This will match ALL products (" + result.getTotalMatchCount() + " total)"));
        content.add(new Paragraph("All products in the system will be priced using this rule."));
        previewDialog.add(content);
    } else {
        // Grid for specific matches
        VerticalLayout content = new VerticalLayout();
        content.add(new Paragraph("This rule will match " + result.getTotalMatchCount() + " products"));

        Grid<PricePreview> grid = new Grid<>();
        grid.addColumn(PricePreview::getProductCode).setHeader("Product Code").setAutoWidth(true);
        grid.addColumn(PricePreview::getProductName).setHeader("Product Name").setAutoWidth(true);
        grid.addColumn(p -> formatCurrency(p.getCost())).setHeader("Cost").setAutoWidth(true);
        grid.addColumn(p -> formatCurrency(p.getCalculatedPrice())).setHeader("New Price").setAutoWidth(true);
        grid.setItems(result.getPreviews());
        grid.setHeight("400px");

        content.add(grid);
        content.setSizeFull();
        previewDialog.add(content);
    }

    // Footer buttons
    Button closeButton = new Button("Close", e -> previewDialog.close());
    closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    Button saveButton = new Button("Save Rule", e -> {
        saveRule();
        previewDialog.close();
    });
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    previewDialog.getFooter().add(closeButton, saveButton);

    previewDialog.open();
}

private String formatCurrency(BigDecimal amount) {
    return NumberFormat.getCurrencyInstance().format(amount);
}
```

### Key Implementation Details

**Performance Considerations:**
- ALL_PRODUCTS rules: Only count products, don't load all data
- Specific rules: Load and display all matching products (acceptable for categories/products)
- No pagination needed - modern grids handle hundreds of rows efficiently
- Preview is read-only operation, no database writes

**Simplicity vs. Complexity Trade-offs:**
- NO conflict detection in preview (would require checking all other rules)
- NO priority-based rule evaluation (just show what THIS rule matches)
- NO session data integration (always use product_costs table)
- Focus on one question: "What products does THIS rule match?"

**User Experience Benefits:**
- Immediate feedback - see results before saving
- Educational - helps users understand rule logic
- Mistake prevention - catch wrong category names, wrong multipliers
- Convenience - can save directly from preview dialog

### Testing Scenarios

1. **Test CATEGORY rule:**
   - Create rule: Category = "BEEF", Cost + 15%
   - Click "Test This Rule"
   - Verify grid shows all beef products with correct calculations

2. **Test PRODUCT_CODE rule:**
   - Create rule: Product = "RIBEYE", Fixed Price $28.50
   - Click "Test This Rule"
   - Verify grid shows one product with fixed price

3. **Test ALL_PRODUCTS rule:**
   - Create rule: All Products, Cost + 20%
   - Click "Test This Rule"
   - Verify simple message with total count, no grid

4. **Test invalid category:**
   - Create rule: Category = "INVALID_CAT"
   - Click "Test This Rule"
   - Verify shows "This rule will match 0 products"

5. **Test from preview dialog:**
   - Preview a rule
   - Click "Save Rule" from preview dialog
   - Verify rule is saved correctly

## Pricing Sessions View - Complete Redesign

### Overview

This is a MAJOR redesign of the Pricing Sessions view, transforming it from a simple data display into the core interface for applying pricing rules, analyzing profit impact, and comparing historical vs. new pricing. This is a critical feature that brings the entire pricing engine to life.

### Purpose

The redesigned Pricing Sessions view allows users to:
1. Load historical sales data from a specific time period
2. Apply new pricing rules to see what prices would be calculated
3. Compare old (historical) vs. new (rule-calculated) pricing side-by-side
4. Analyze gross profit impact with visual indicators
5. Manually override rule-calculated prices when needed
6. Understand which rule was applied to each price (transparency)
7. Save pricing scenarios for analysis and decision-making

### Column Structure - Nested Headers

The grid uses Vaadin's nested header feature to group columns into logical sections. This provides a clear visual separation between historical data and new pricing calculations.

**Visual Layout:**

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│ Pricing Session: Q4 2024 Analysis                              [Apply Rules] [Save] [Load]  │
├──────────┬────────┬──────────┬──────────┬────┬──────────────────────┬────────────────────────┤
│          │        │ Product  │          │    │  Historical (Last)   │    New Pricing         │
├──────────┼────────┼──────────┼──────────┼────┼──────┬──────┬───────┼──────┬──────┬──────────┤
│ Customer │ Rating │   Code   │ Product  │ Qty│ Cost │Price │Amount │ Cost │Price │Amount│GP │Notes│
│          │        │          │          │    │      │      │  GP   │      │      │      │   │     │
├──────────┼────────┼──────────┼──────────┼────┼──────┼──────┼───────┼──────┼──────┼──────────┤
│ ABC      │   85   │ RIB-001  │ Ribeye   │ 20 │$10.00│$12.00│$240.00│$10.50│$12.08│$241.50│$31.50│     │
│ Meats    │        │          │          │    │      │      │ $40.00│      │  ⓘ   │ 🟢    │      │     │
├──────────┼────────┼──────────┼──────────┼────┼──────┼──────┼───────┼──────┼──────┼──────────┤
│ Joe's    │   72   │ RIB-001  │ Ribeye   │ 10 │$10.00│$15.00│$150.00│$10.50│$13.00│$130.00│$25.00│Manual│
│ Shop     │        │          │          │    │      │      │ $50.00│      │  ✏️  │ 🔴    │      │Override│
└──────────┴────────┴──────────┴──────────┴────┴──────┴──────┴───────┴──────┴──────┴──────────┘

Legend:
ⓘ = Clickable - shows which rule was applied
✏️ = Manually overridden (orange background on price)
🟢 = GP increased (green text)
🔴 = GP decreased (red text)
```

### Column Definitions

**Basic Information Columns:**
1. **Customer** - Customer name (from v_grouped_line_items)
2. **Rating** - Customer rating (from customers table) - NEW column for context
3. **Product Code** - Product code
4. **Product** - Product name
5. **Qty** - Total quantity (summed from line items)

**Historical (Last) Group - Read-Only Reference Data:**
6. **Cost** - Historical cost when sold (from v_grouped_line_items.avg_unit_cost)
7. **Price** - Historical sell price (calculated from v_grouped_line_items)
8. **Amount** - Historical total amount (from v_grouped_line_items.total_amount)
9. **GP** - Historical gross profit (calculated: amount - (cost × qty))

**New Pricing Group - Interactive Calculation Results:**
10. **Cost** - Incoming cost (from product_costs.stdcost) - represents current/future cost
11. **Price** - New calculated or manually set sell price - INTERACTIVE with visual indicators
12. **Amount** - New total amount (calculated: new price × qty)
13. **GP** - New gross profit (calculated: new amount - (new cost × qty)) - COLOR CODED for quick analysis
14. **Notes** - Shows "Manual Override" if user edited price - transparency indicator

### Data Source and Integration

**Primary Data Source:**
- Base data: `v_grouped_line_items` filtered by date range
- Join with `customers` table to get customer rating
- Join with `product_costs` table to get stdcost (incoming cost for new pricing)

**Query Pattern:**
```sql
SELECT
    gli.customer_code,
    gli.customer_name,
    c.customer_rating,
    gli.product_code,
    gli.product_name,
    gli.total_quantity,
    gli.avg_unit_cost AS last_cost,
    (gli.total_amount / gli.total_quantity) AS last_unit_sell_price,
    gli.total_amount AS last_amount,
    (gli.total_amount - (gli.avg_unit_cost * gli.total_quantity)) AS last_gross_profit,
    pc.stdcost AS incoming_cost
FROM v_grouped_line_items gli
LEFT JOIN customers c ON gli.customer_code = c.customer_code
LEFT JOIN product_costs pc ON gli.product_code = pc.product_code
WHERE gli.invoice_date BETWEEN :start_date AND :end_date
ORDER BY gli.customer_name, gli.product_code;
```

### Key Behaviors and User Interactions

#### 1. Apply Rules Button

**Purpose:** Triggers the pricing engine to calculate prices for all items in the session.

**Workflow:**
1. User clicks "Apply Rules" button in toolbar
2. System shows confirmation dialog if there are many items
3. System iterates through ALL items in the grid
4. For each item:
   - Calls `PriceCalculationService.calculatePrice()` with customer code, product code, product category, and incoming cost
   - Receives back calculated price and reference to applied rule
   - Sets `newUnitSellPrice` from rule result
   - Sets `incomingCost` from product_costs.stdcost
   - Stores reference to `appliedRule` for transparency
   - Recalculates `newAmount` = newUnitSellPrice × quantity
   - Recalculates `newGrossProfit` = newAmount - (incomingCost × quantity)
   - Clears any `manualOverride` flags (rule application overrides manual edits)
5. Refreshes grid display to show all new calculations
6. Shows success notification with count of items processed

**Implementation Code:**
```java
private void applyPricingRules() {
    List<GroupedLineItem> items = getCurrentGridItems();

    if (items.isEmpty()) {
        Notification.show("No items to price", 3000, Notification.Position.MIDDLE);
        return;
    }

    int successCount = 0;
    int failCount = 0;

    for (GroupedLineItem item : items) {
        try {
            // Get customer for this item
            Customer customer = customerRepository.findByCode(item.getCustomerCode());

            // Calculate price using pricing engine
            PricingResult result = priceCalculationService.calculatePrice(item, customer);

            // Update item with calculated values
            item.setIncomingCost(result.getCost());
            item.setNewUnitSellPrice(result.getCalculatedPrice());
            item.setAppliedRule(result.getAppliedRule());
            item.setManualOverride(false);  // Clear override flag

            // Recalculate derived values
            BigDecimal newAmount = item.getNewUnitSellPrice()
                .multiply(item.getTotalQuantity())
                .setScale(2, RoundingMode.HALF_UP);
            item.setNewAmount(newAmount);

            BigDecimal costTotal = item.getIncomingCost()
                .multiply(item.getTotalQuantity())
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal newGP = newAmount.subtract(costTotal);
            item.setNewGrossProfit(newGP);

            successCount++;

        } catch (Exception ex) {
            logger.error("Failed to calculate price for item: " + item.getProductCode(), ex);
            failCount++;
        }
    }

    // Refresh grid
    dataGrid.getDataProvider().refreshAll();

    // Mark session as modified
    markSessionAsModified();

    // Show result notification
    String message = String.format("Pricing rules applied: %d succeeded, %d failed",
        successCount, failCount);
    Notification.show(message, 3000, Notification.Position.BOTTOM_START);
}
```

#### 2. Manual Price Override

**Purpose:** Allow users to manually adjust prices when business judgment overrides rule calculations.

**Workflow:**
1. User clicks on "New Price" cell in the grid
2. Cell becomes editable (inline editing)
3. User enters new price value
4. On blur/enter:
   - System validates price is positive
   - Sets `manualOverride = true` flag
   - Clears `appliedRule` reference
   - Cell gets orange background to indicate manual override
   - Notes column shows "Manual Override"
   - Recalculates `newAmount` and `newGrossProfit` with new price
5. Grid refreshes to show visual indicators

**Visual Indicators:**
- Orange background on price cell
- Pencil icon (✏️) next to price
- "Manual Override" text in Notes column
- No info icon (since no rule applied)

**Persistence:**
- Override persists in session until "Apply Rules" clicked again
- Override is saved with session to pricing_session_line_items table
- Override survives session reload

**Important Note:** Clicking "Apply Rules" will overwrite all manual overrides. This is intentional - rules take precedence, and user must re-apply manual overrides after rule application if needed.

#### 3. Rule Details Popup

**Purpose:** Provide transparency into which rule calculated a price and how the calculation worked.

**Trigger:** User clicks info icon (ⓘ) on "New Price" cell

**Dialog Content:**
```
┌─────────────────────────────────────────┐
│ Pricing Rule Applied                    │
├─────────────────────────────────────────┤
│                                         │
│ Product: Ribeye Steak (RIB-001)         │
│ Customer: ABC Meats                     │
│                                         │
│ Rule: ABC Meats - Ribeye Special        │
│ Method: Cost Plus Percentage            │
│                                         │
│ Calculation:                            │
│ $10.50 × 1.12 = $11.76                 │
│                                         │
│                         [Close]         │
└─────────────────────────────────────────┘
```

**Implementation Code:**
```java
private void showRuleDetailsDialog(GroupedLineItem item) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Pricing Rule Applied");
    dialog.setWidth("500px");

    VerticalLayout content = new VerticalLayout();
    content.setPadding(true);
    content.setSpacing(true);

    // Product and customer info
    content.add(new H4("Product: " + item.getProductName() + " (" + item.getProductCode() + ")"));
    content.add(new Paragraph("Customer: " + item.getCustomerName()));

    // Rule details
    if (item.getAppliedRule() != null) {
        PricingRule rule = item.getAppliedRule();

        Div ruleInfo = new Div();
        ruleInfo.add(new Paragraph("Rule: " + rule.getRuleName()));
        ruleInfo.add(new Paragraph("Method: " + formatPricingMethod(rule.getPricingMethod())));

        // Show calculation
        String calculation = switch(rule.getPricingMethod()) {
            case "COST_PLUS_PERCENT" ->
                formatCurrency(item.getIncomingCost()) + " × " + rule.getPricingValue() +
                " = " + formatCurrency(item.getNewUnitSellPrice());
            case "COST_PLUS_FIXED" ->
                formatCurrency(item.getIncomingCost()) + " + " + formatCurrency(rule.getPricingValue()) +
                " = " + formatCurrency(item.getNewUnitSellPrice());
            case "FIXED_PRICE" ->
                "Fixed Price = " + formatCurrency(item.getNewUnitSellPrice());
            default -> "Unknown calculation";
        };

        ruleInfo.add(new Paragraph("Calculation: " + calculation));
        content.add(ruleInfo);
    } else {
        content.add(new Paragraph("No rule applied - price set manually"));
    }

    Button closeBtn = new Button("Close", e -> dialog.close());
    closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    dialog.add(content);
    dialog.getFooter().add(closeBtn);
    dialog.open();
}
```

**Visibility:**
- Info icon only shown for rule-calculated prices
- NOT shown for manual overrides (shows pencil icon instead)
- Provides full transparency into pricing logic

#### 4. GP Color Coding

**Purpose:** Provide instant visual feedback on profitability impact of new pricing.

**Logic:**
- Compare `newGrossProfit` vs `lastGrossProfit`
- If new > last: GREEN text, bold - pricing improvement
- If new < last: RED text, bold - pricing degradation, needs attention
- If equal: Normal text - no change

**Implementation:**
```java
private Component createGPCell(GroupedLineItem item) {
    Span gpText = new Span(formatCurrency(item.getNewGrossProfit()));

    // Color code based on comparison to historical GP
    BigDecimal lastGP = item.getLastGrossProfit();
    BigDecimal newGP = item.getNewGrossProfit();

    if (newGP.compareTo(lastGP) > 0) {
        gpText.getStyle()
            .set("color", "green")
            .set("font-weight", "bold");
    } else if (newGP.compareTo(lastGP) < 0) {
        gpText.getStyle()
            .set("color", "red")
            .set("font-weight", "bold");
    }

    return gpText;
}
```

**Business Value:**
- Immediate identification of pricing problems
- Quick validation that rules are working as intended
- Helps prioritize which items need manual review
- Visual summary of overall session profitability

#### 5. Session Save

**Purpose:** Persist pricing session with all comparison data for future analysis.

**Data Saved:**
- All basic fields (customer, product, quantity)
- Historical values (last cost, price, amount, GP)
- New pricing values (incoming cost, new price, amount, GP)
- Metadata: applied_rule_id (nullable), manual_override flag
- Session header: name, description, created date, modified date

**Behavior:**
- UPSERT logic: Update if line item exists, insert if new
- Tracks which rule was applied for future reference
- Preserves manual override flags
- Allows reloading and re-analyzing later

**Implementation:**
```java
private void saveSession() {
    if (currentSession == null) {
        // Create new session
        currentSession = new PricingSession();
        currentSession.setSessionName(sessionNameField.getValue());
        currentSession.setSessionDescription(sessionDescField.getValue());
        currentSession.setCreatedAt(LocalDateTime.now());
        currentSession = pricingSessionRepository.save(currentSession);
    }

    currentSession.setModifiedAt(LocalDateTime.now());
    pricingSessionRepository.update(currentSession);

    // Save line items
    List<GroupedLineItem> items = getCurrentGridItems();
    for (GroupedLineItem item : items) {
        PricingSessionLineItem lineItem = new PricingSessionLineItem();
        lineItem.setSessionId(currentSession.getId());

        // Basic fields
        lineItem.setCustomerCode(item.getCustomerCode());
        lineItem.setCustomerName(item.getCustomerName());
        lineItem.setProductCode(item.getProductCode());
        lineItem.setProductName(item.getProductName());
        lineItem.setQuantity(item.getTotalQuantity());

        // Historical fields
        lineItem.setLastCost(item.getLastCost());
        lineItem.setLastUnitSellPrice(item.getLastUnitSellPrice());
        lineItem.setLastAmount(item.getLastAmount());
        lineItem.setLastGrossProfit(item.getLastGrossProfit());

        // New pricing fields
        lineItem.setIncomingCost(item.getIncomingCost());
        lineItem.setNewUnitSellPrice(item.getNewUnitSellPrice());
        lineItem.setNewAmount(item.getNewAmount());
        lineItem.setNewGrossProfit(item.getNewGrossProfit());

        // Metadata
        lineItem.setManualOverride(item.isManualOverride());
        if (item.getAppliedRule() != null) {
            lineItem.setAppliedRuleId(item.getAppliedRule().getId());
        }

        pricingSessionLineItemRepository.upsert(lineItem);
    }

    markSessionAsClean();
    Notification.show("Session saved successfully", 3000, Notification.Position.BOTTOM_START);
}
```

### Data Model Changes

#### Enhanced GroupedLineItem Class

```java
package com.meatrics.pricing;

import java.math.BigDecimal;

public class GroupedLineItem {
    // Existing fields - basic information
    private String customerCode;
    private String customerName;
    private String productCode;
    private String productName;
    private BigDecimal totalQuantity;

    // Historical data (from v_grouped_line_items) - NEW fields
    private BigDecimal lastCost;  // Historical cost when sold
    private BigDecimal lastUnitSellPrice;  // Historical sell price
    private BigDecimal lastAmount;  // Historical total amount
    private BigDecimal lastGrossProfit;  // Historical GP (calculated)

    // New pricing data - NEW fields
    private BigDecimal incomingCost;  // From product_costs.stdcost
    private BigDecimal newUnitSellPrice;  // Calculated by rules or manual
    private BigDecimal newAmount;  // Calculated: newUnitSellPrice × qty
    private BigDecimal newGrossProfit;  // Calculated: newAmount - (incomingCost × qty)

    // Customer data - NEW field
    private String customerRating;  // From customers table for context

    // Metadata for UI - NEW fields
    private transient PricingRule appliedRule;  // Which rule was used
    private transient boolean manualOverride;  // User edited price flag

    // Existing transient fields for modification tracking
    private transient BigDecimal originalAmount;
    private transient boolean amountModified;

    // Getters and setters...
}
```

**Key Design Points:**
- Separates historical data (what happened) from new pricing data (what we're calculating)
- Transient fields for UI state (appliedRule, manualOverride) not persisted directly
- Rule reference and override flag stored in pricing_session_line_items table on save

### Database Schema Changes

#### Migration: 009-pricing-sessions-comparison-columns.sql

```sql
-- Add columns to pricing_session_line_items to support comparison view
ALTER TABLE pricing_session_line_items
ADD COLUMN last_cost DECIMAL(10,2),
ADD COLUMN last_unit_sell_price DECIMAL(10,2),
ADD COLUMN last_amount DECIMAL(10,2),
ADD COLUMN last_gross_profit DECIMAL(10,2),
ADD COLUMN incoming_cost DECIMAL(10,2),
ADD COLUMN new_unit_sell_price DECIMAL(10,2),
ADD COLUMN new_amount DECIMAL(10,2),
ADD COLUMN new_gross_profit DECIMAL(10,2),
ADD COLUMN applied_rule_id BIGINT,  -- FK to pricing_rule, nullable
ADD COLUMN manual_override BOOLEAN DEFAULT FALSE;

-- Add foreign key constraint for rule reference
ALTER TABLE pricing_session_line_items
ADD CONSTRAINT fk_pricing_session_line_items_rule
FOREIGN KEY (applied_rule_id) REFERENCES pricing_rule(id)
ON DELETE SET NULL;  -- If rule deleted, set to NULL (preserve data)

-- Add index for faster lookups
CREATE INDEX idx_pricing_session_line_items_rule_id
ON pricing_session_line_items(applied_rule_id);

-- Add comment explaining nullable rule reference
COMMENT ON COLUMN pricing_session_line_items.applied_rule_id IS
'Reference to pricing rule that calculated this price. NULL if manually overridden or rule was deleted.';
```

**Migration Notes:**
- Existing pricing_session_line_items rows will have NULL for new columns
- This is fine - old sessions won't have comparison data
- New sessions will populate all fields
- Applied_rule_id is nullable because: (1) manual overrides have no rule, (2) rules can be deleted

### Vaadin Implementation

#### Complete Grid Creation with Nested Headers

```java
private Grid<GroupedLineItem> createDataGrid() {
    Grid<GroupedLineItem> grid = new Grid<>();

    // Add all columns (no headers yet)
    Grid.Column<GroupedLineItem> customerCol = grid.addColumn(GroupedLineItem::getCustomerName);
    Grid.Column<GroupedLineItem> ratingCol = grid.addColumn(GroupedLineItem::getCustomerRating);
    Grid.Column<GroupedLineItem> productCodeCol = grid.addColumn(GroupedLineItem::getProductCode);
    Grid.Column<GroupedLineItem> productCol = grid.addColumn(GroupedLineItem::getProductName);
    Grid.Column<GroupedLineItem> qtyCol = grid.addColumn(item -> formatQuantity(item.getTotalQuantity()));

    // Historical columns
    Grid.Column<GroupedLineItem> lastCostCol = grid.addColumn(item -> formatCurrency(item.getLastCost()));
    Grid.Column<GroupedLineItem> lastPriceCol = grid.addColumn(item -> formatCurrency(item.getLastUnitSellPrice()));
    Grid.Column<GroupedLineItem> lastAmountCol = grid.addColumn(item -> formatCurrency(item.getLastAmount()));
    Grid.Column<GroupedLineItem> lastGPCol = grid.addColumn(item -> formatCurrency(item.getLastGrossProfit()));

    // New pricing columns
    Grid.Column<GroupedLineItem> newCostCol = grid.addColumn(item -> formatCurrency(item.getIncomingCost()));
    Grid.Column<GroupedLineItem> newPriceCol = grid.addComponentColumn(this::createPriceCell);
    Grid.Column<GroupedLineItem> newAmountCol = grid.addColumn(item -> formatCurrency(item.getNewAmount()));
    Grid.Column<GroupedLineItem> newGPCol = grid.addComponentColumn(this::createGPCell);
    Grid.Column<GroupedLineItem> notesCol = grid.addComponentColumn(this::createNotesCell);

    // Create nested header structure
    HeaderRow topHeader = grid.prependHeaderRow();
    HeaderRow bottomHeader = grid.getHeaderRows().get(0);

    // Top level grouping - join columns under group headers
    topHeader.join(customerCol, ratingCol, productCodeCol, productCol, qtyCol).setText("");
    topHeader.join(lastCostCol, lastPriceCol, lastAmountCol, lastGPCol).setText("Historical (Last)");
    topHeader.join(newCostCol, newPriceCol, newAmountCol, newGPCol, notesCol).setText("New Pricing");

    // Bottom level individual headers
    bottomHeader.getCell(customerCol).setText("Customer");
    bottomHeader.getCell(ratingCol).setText("Rating");
    bottomHeader.getCell(productCodeCol).setText("Product Code");
    bottomHeader.getCell(productCol).setText("Product");
    bottomHeader.getCell(qtyCol).setText("Qty");
    bottomHeader.getCell(lastCostCol).setText("Cost");
    bottomHeader.getCell(lastPriceCol).setText("Price");
    bottomHeader.getCell(lastAmountCol).setText("Amount");
    bottomHeader.getCell(lastGPCol).setText("GP");
    bottomHeader.getCell(newCostCol).setText("Cost");
    bottomHeader.getCell(newPriceCol).setText("Price");
    bottomHeader.getCell(newAmountCol).setText("Amount");
    bottomHeader.getCell(newGPCol).setText("GP");
    bottomHeader.getCell(notesCol).setText("Notes");

    // Set column properties
    grid.setAllRowsVisible(false);
    grid.setHeight("600px");

    return grid;
}
```

#### Price Cell with Visual Indicators

```java
private Component createPriceCell(GroupedLineItem item) {
    HorizontalLayout layout = new HorizontalLayout();
    layout.setSpacing(false);
    layout.setPadding(false);
    layout.setAlignItems(FlexComponent.Alignment.CENTER);

    Span priceText = new Span(formatCurrency(item.getNewUnitSellPrice()));

    // Style for manual override
    if (item.isManualOverride()) {
        priceText.getStyle()
            .set("background-color", "#FFA500")
            .set("color", "white")
            .set("padding", "2px 6px")
            .set("border-radius", "3px")
            .set("font-weight", "bold");
    }

    layout.add(priceText);

    // Add clickable info icon for rule-calculated prices
    if (!item.isManualOverride() && item.getAppliedRule() != null) {
        Icon infoIcon = new Icon(VaadinIcon.INFO_CIRCLE_O);
        infoIcon.setSize("16px");
        infoIcon.getStyle()
            .set("cursor", "pointer")
            .set("margin-left", "4px")
            .set("color", "#0066CC");
        infoIcon.addClickListener(e -> showRuleDetailsDialog(item));
        layout.add(infoIcon);
    }

    return layout;
}
```

#### Notes Cell Implementation

```java
private Component createNotesCell(GroupedLineItem item) {
    if (item.isManualOverride()) {
        Span note = new Span("Manual Override");
        note.getStyle()
            .set("font-size", "0.85em")
            .set("font-style", "italic")
            .set("color", "#666");
        return note;
    }
    return new Span("");
}
```

### Session Load Implementation

```java
private void loadSession(PricingSession session) {
    // Load session line items
    List<PricingSessionLineItem> sessionItems =
        pricingSessionLineItemRepository.findBySessionId(session.getId());

    // Convert to GroupedLineItem with all fields
    List<GroupedLineItem> items = sessionItems.stream()
        .map(sessionItem -> {
            GroupedLineItem item = new GroupedLineItem();

            // Basic fields
            item.setCustomerCode(sessionItem.getCustomerCode());
            item.setCustomerName(sessionItem.getCustomerName());
            item.setProductCode(sessionItem.getProductCode());
            item.setProductName(sessionItem.getProductName());
            item.setTotalQuantity(sessionItem.getQuantity());

            // Historical fields
            item.setLastCost(sessionItem.getLastCost());
            item.setLastUnitSellPrice(sessionItem.getLastUnitSellPrice());
            item.setLastAmount(sessionItem.getLastAmount());
            item.setLastGrossProfit(sessionItem.getLastGrossProfit());

            // New pricing fields
            item.setIncomingCost(sessionItem.getIncomingCost());
            item.setNewUnitSellPrice(sessionItem.getNewUnitSellPrice());
            item.setNewAmount(sessionItem.getNewAmount());
            item.setNewGrossProfit(sessionItem.getNewGrossProfit());

            // Metadata
            item.setManualOverride(sessionItem.isManualOverride());
            if (sessionItem.getAppliedRuleId() != null) {
                PricingRule rule = pricingRuleRepository.findById(sessionItem.getAppliedRuleId());
                item.setAppliedRule(rule);
            }

            // Get customer rating
            Customer customer = customerRepository.findByCode(sessionItem.getCustomerCode());
            if (customer != null) {
                item.setCustomerRating(customer.getCustomerRating());
            }

            return item;
        })
        .collect(Collectors.toList());

    backingList = items;
    dataGrid.setItems(items);
    currentSession = session;
    markSessionAsClean();
}
```

### Testing Scenarios

**Scenario 1: Apply Rules to New Session**
1. Create new pricing session from historical data
2. Click "Apply Rules"
3. Verify all prices calculated using pricing engine
4. Verify "Applied Rule" column shows rule names
5. Verify GP color coding shows green/red appropriately
6. Click info icon to verify rule details dialog

**Scenario 2: Manual Override**
1. Load session with rule-calculated prices
2. Edit a price manually
3. Verify orange background and "Manual Override" in notes
4. Verify info icon disappears
5. Save and reload session - verify override persists

**Scenario 3: Re-apply Rules After Override**
1. Load session with manual overrides
2. Click "Apply Rules"
3. Verify manual overrides are replaced with rule calculations
4. Verify orange backgrounds removed
5. Verify info icons reappear

**Scenario 4: Compare Historical vs New**
1. Load session with large price differences
2. Verify historical data matches import data
3. Verify new pricing reflects current costs and rules
4. Verify GP comparison is accurate
5. Identify items with red GP (needs attention)

**Scenario 5: Session Persistence**
1. Create session with mix of rule-calculated and manual prices
2. Save session
3. Close and reopen application
4. Load session
5. Verify all data matches (prices, overrides, rule references)

### Implementation Priority

This redesign is a **Phase 1 critical feature** because:
1. It's the primary interface for using the pricing engine
2. It provides the business value of comparing old vs new pricing
3. It demonstrates transparency in pricing decisions
4. It validates that the pricing engine is working correctly

**Implementation Order:**
1. Database migration for new columns
2. Update GroupedLineItem class with new fields
3. Update repositories to support new fields
4. Create nested header grid structure
5. Implement "Apply Rules" button integration
6. Implement visual indicators (colors, icons, backgrounds)
7. Implement rule details dialog
8. Implement manual override functionality
9. Update session save/load logic
10. Add comprehensive testing

### Success Criteria

- [ ] Nested headers display correctly with grouped columns
- [ ] Historical data loads and displays accurately
- [ ] "Apply Rules" calculates all prices correctly
- [ ] GP color coding provides clear visual feedback
- [ ] Info icon shows correct rule details
- [ ] Manual override works and persists
- [ ] "Manual Override" indicator shows in Notes column
- [ ] Session save includes all comparison data
- [ ] Session load restores all data including overrides
- [ ] Grid performance is acceptable with 100+ line items

## Related Documentation

- **proposed_pricing_solution.md** - Original proposal for rule-based pricing engine
- **PROJECT_OVERVIEW.md** - Complete project documentation and architecture
- **HANDOFF.md** - Quick reference for future development sessions
- **JOOQ_GENERATION.md** - Database schema change workflow

---

## Document Summary

**Design Status:** Phase 1 COMPLETE, Phase 2 Backend COMPLETE

**Key Decisions Made:**
1. Simple single-table schema (pricing_rule) with customer_code for scoping
2. Three condition types: ALL_PRODUCTS, CATEGORY, PRODUCT_CODE
3. Four pricing methods: COST_PLUS_PERCENT, COST_PLUS_FIXED, FIXED_PRICE, MAINTAIN_GP_PERCENT
4. Priority-based first-match-wins evaluation
5. Customer-specific pricing with automatic fallback to standard rules
6. Transparency requirement: Show applied rule for every price calculation
7. Rating-based pricing deferred to Phase 3
8. **Pricing Sessions View complete redesign with nested headers for historical vs new pricing comparison**
9. **Visual indicators for profitability (GP color coding) and pricing transparency (info icons, orange overrides)**
10. **MAINTAIN_GP_PERCENT method** - Data-driven pricing using historical GP% with intelligent fallback and safety caps

**What Makes This Design Elegant:**
- **Simple but Powerful:** Single table, three condition types, four methods - covers 95% of use cases
- **Data-Driven Intelligence:** MAINTAIN_GP_PERCENT learns from historical transactions and adjusts automatically
- **Safe Defaults:** ALL_PRODUCTS + priority ranges ensure nothing falls through cracks
- **Graceful Degradation:** GP% method falls back to defaults when historical data unavailable
- **Safety Features:** Automatic capping prevents unrealistic margins (10%-60% range)
- **Flexible:** Can be as specific (one product) or broad (all products) as needed
- **Discoverable:** Transparency feature helps users understand and trust the system
- **Extensible:** Schema can grow to Phase 3 without breaking changes
- **User-Focused:** Pricing Sessions redesign provides immediate visual feedback on profitability and pricing decisions

**Major Features:**
1. **Pricing Rules Management** - Create and manage rules with preview/test functionality
2. **Price Calculation Engine** - Rule matching and price calculation service with four methods
3. **Pricing Sessions View Redesign** - Side-by-side comparison of historical vs new pricing with full transparency
4. **Customer-Specific Pricing** - Flag customers for custom rules with automatic fallback
5. **MAINTAIN_GP_PERCENT Method** - Intelligent, data-driven pricing that maintains historical profit margins

**Implementation Status:**
- Phase 1 (Core Rule Engine): COMPLETE and TESTED
- Phase 2 Backend (MAINTAIN_GP_PERCENT): COMPLETE and TESTED
- Phase 2 Frontend: Partial (method works but UI could be enhanced)
- Phase 3 (Advanced Features): Not started

---

**Last Updated:** 2025-11-09 - MAINTAIN_GP_PERCENT pricing method added and documented (Phase 2 backend complete)
