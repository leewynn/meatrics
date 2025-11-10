### Current System Analysis

*   **Input Data:** The key inputs for pricing are the `product_costs` table (which provides the cost for each product) and the `customers` table.
*   **Missing Logic:** There is currently no database-driven pricing logic based on `sell_price_n` columns, as those are irrelevant. The application needs a component to calculate the sell price for a line item, likely based on its cost and potentially the customer associated with it.
*   **Core Need:** The central requirement is to build a system that can define and apply pricing rules (e.g., "all items in category 'X' are priced at cost + 20%", "customer 'Y' gets a 5% discount").
*   **Existing Workflow:** The "Pricing Session" remains the ideal place to use this new engine. A user can load items, apply a set of pricing rules, and then analyze the results.

### Proposal: A Rule-Based Pricing Engine

I propose creating a new, dynamic pricing system that will serve as the application's primary pricing engine. This will give you the flexibility to define rules like "cost-plus-margin" or "category-specific discounts."

Here is the plan:

#### 1. Backend Changes

*   **Database (New Table):**
    *   Create a new `pricing_rule` table to store pricing rules. I will add a new Liquibase script (`009-create-pricing-rules-table.sql`) for this.
    *   **Columns:** `id`, `name`, `priority`, `condition_field` (e.g., "product_category"), `condition_value`, `action_type` (e.g., "COST_PLUS_MARGIN"), `action_value` (e.g., 1.2 for a 20% margin).
    *   Run the jOOQ generator to create the corresponding Java classes for this new table.

*   **Service Layer (New & Modified Services):**
    *   **`PricingRuleService`:** A new service with a corresponding repository for creating, reading, updating, and deleting pricing rules.
    *   **`PriceCalculationService`:** A new, central service responsible for all price calculations.
        *   It will contain the logic for applying the new rule-based model.
    *   **Modify `PricingSessionService`:**
        *   Update it to call the new `PriceCalculationService` when a session is created or updated.

#### 2. Frontend (Vaadin UI) Changes

*   **New `PricingRulesView`:**
    *   Create a new UI view, similar to your existing views, for managing the new pricing rules. Users will be able to define and edit rules from a grid.

*   **Enhance `PricingSessionsView`:**
    *   The view will no longer contain pricing logic itself; it will simply display the results calculated by the new backend services. It will need to trigger the `PriceCalculationService` when pricing needs to be applied to line items within a session.

### Summary of Steps

1.  **Database:** Add the `pricing_rule` table.
2.  **Backend:** Create new services for rule management (`PricingRuleService`) and calculation (`PriceCalculationService`).
3.  **Frontend:** Create the new `PricingRulesView` and update the `PricingSessionsView` to integrate the new pricing engine.
