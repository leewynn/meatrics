# Meatrics Application - Current State Documentation

**Last Updated**: 2025-11-12
**Version**: Post-0.1 Release (Active Development)

This document provides a comprehensive overview of the Meatrics application's current state, focusing on recent changes and the fully implemented pricing engine with session persistence.

---

## Table of Contents

1. [Overview](#overview)
2. [Recent Changes (Last Session)](#recent-changes-last-session)
3. [Core Features](#core-features)
4. [Technical Architecture](#technical-architecture)
5. [Key Implementation Details](#key-implementation-details)
6. [Database Schema](#database-schema)
7. [Development Status](#development-status)

---

## Overview

Meatrics is a **pricing management and analysis application** for meat distribution businesses. It enables:

- Import of sales transaction data and product cost data from Excel
- Analysis of pricing patterns, margins, and customer profitability
- Rule-based pricing engine with layered calculation approach
- What-if scenario planning through pricing sessions with full persistence
- Historical pricing data tracking for GP% maintenance
- Customer rating system using multiple algorithms
- Excel report generation for analysis

**Technology Stack:**
- Java 21, Spring Boot 3.x
- Vaadin 24 (server-side UI framework)
- PostgreSQL with Liquibase migrations
- jOOQ for type-safe database access
- Apache POI for Excel processing

---

## Recent Changes (Last Session)

### 1. Session Persistence Using VaadinSession

**Context**: Previously, when navigating away from the Pricing Sessions view, all work-in-progress pricing calculations would be lost. Users needed to explicitly save sessions before leaving.

**Implementation** (`PricingSessionsViewNew.java`):

```java
// VaadinSession keys for persisting working data
private static final String SESSION_DATA_KEY = "meatrics.pricingSessions.workingData";
private static final String SESSION_STATE_KEY = "meatrics.pricingSessions.sessionState";

// Automatically save working data to VaadinSession when navigating away
@Override
public void beforeLeave(BeforeLeaveEvent event) {
    if (hasUnsavedChanges && backingList != null && !backingList.isEmpty()) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(SESSION_DATA_KEY, new ArrayList<>(backingList));
            session.setAttribute(SESSION_STATE_KEY, currentSession);
        }
    }
    // Allow navigation
    event.getContinueNavigationAction().proceed();
}

// Restore working data when view is re-entered
private void restoreWorkingDataFromSession() {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) return;

    List<GroupedLineItem> savedData =
        (List<GroupedLineItem>) session.getAttribute(SESSION_DATA_KEY);
    if (savedData != null && !savedData.isEmpty()) {
        backingList = new ArrayList<>(savedData);
        dataGrid.setItems(backingList);
        updateFooterTotals();
        hasUnsavedChanges = true;
        updateTitleWithSessionName();

        // Show notification that work was restored
        Notification notification = Notification.show(
            "Restored unsaved work from this browser session. " +
            "Click 'Save Session' to persist your changes.",
            5000, Notification.Position.TOP_CENTER
        );
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
    }

    // Restore session metadata
    PricingSession sessionState =
        (PricingSession) session.getAttribute(SESSION_STATE_KEY);
    if (sessionState != null) {
        currentSession = sessionState;
    }
}
```

**Benefits:**
- Users can navigate between views without losing their pricing work
- Work-in-progress is preserved even if user accidentally leaves the page
- Encourages exploration of other views (Import, Reports) during pricing sessions
- Data is browser-session-scoped (cleaned up when browser closes)
- Explicit notification when restored work is loaded

### 2. Full Pricing Calculation Breakdown Display

**Context**: When loading saved sessions, users needed to see not just the final price but the complete calculation chain showing how multiple rules were applied.

**Implementation** (`PricingSessionsViewNew.java`):

The "Pricing Method" column now displays comprehensive rule information:

```java
// Format pricing method with full breakdown
private String formatPricingMethod(GroupedLineItem item) {
    List<PricingRule> rules = item.getAppliedRules();
    List<BigDecimal> intermediates = item.getIntermediateResults();

    if (rules == null || rules.isEmpty()) {
        if (item.isManualOverride()) {
            return "Manual Override";
        }
        return "No rules applied";
    }

    StringBuilder sb = new StringBuilder();

    // Show layered rule application with intermediate results
    for (int i = 0; i < rules.size(); i++) {
        PricingRule rule = rules.get(i);

        if (i > 0) {
            sb.append("\n→ ");
        }

        // Category badge
        if (rule.getRuleCategory() != null) {
            sb.append("[").append(rule.getRuleCategory().getDisplayName()).append("] ");
        }

        // Rule name
        sb.append(rule.getRuleName()).append(": ");

        // Method description with actual values
        switch (rule.getPricingMethod()) {
            case "COST_PLUS_PERCENT":
                BigDecimal pct = rule.getPricingValue()
                    .subtract(BigDecimal.ONE)
                    .multiply(new BigDecimal("100"));
                String sign = pct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                sb.append(sign).append(pct.setScale(1, RoundingMode.HALF_UP)).append("%");
                break;
            case "COST_PLUS_FIXED":
                sb.append("Cost+$").append(rule.getPricingValue());
                break;
            case "FIXED_PRICE":
                sb.append("Fixed $").append(rule.getPricingValue());
                break;
            case "MAINTAIN_GP_PERCENT":
                // Calculate and show actual historical GP%
                BigDecimal historicalGP = calculateHistoricalGP(item);
                if (historicalGP != null) {
                    BigDecimal gpPercent = historicalGP.multiply(new BigDecimal("100"));
                    sb.append("Maintained ").append(gpPercent.setScale(1, RoundingMode.HALF_UP))
                      .append("% GP");
                } else {
                    BigDecimal defaultGP = rule.getPricingValue().multiply(new BigDecimal("100"));
                    sb.append("Maintain GP (default ").append(defaultGP.setScale(0, RoundingMode.HALF_UP))
                      .append("%)");
                }
                break;
            default:
                sb.append(rule.getPricingMethod());
        }

        // Show intermediate result after this rule
        if (intermediates != null && i < intermediates.size()) {
            BigDecimal price = intermediates.get(i);
            sb.append(" → $").append(price.setScale(2, RoundingMode.HALF_UP));
        }
    }

    return sb.toString();
}
```

**Display Example:**
```
[Base Price] Standard Beef: +30% → $13.00
→ [Customer Adj] ABC Meats Discount: -5% → $12.35
→ [Product Adj] Chicken Processing Fee: Cost+$2.50 → $14.85
```

For MAINTAIN_GP_PERCENT rules, the actual historical GP% is calculated and displayed:
```
[Base Price] Maintain GP: Maintained 18.5% GP → $12.30
```

### 3. Applied Rules Persistence in Database

**Context**: Previously, rules were calculated on-the-fly but not stored with saved sessions. Loading a session would recalculate prices, potentially getting different results if rules had changed.

**Database Migration** (`018-add-applied-rule-text-column.sql`):

```sql
-- Add applied_rule TEXT column to store comma-separated rule names
ALTER TABLE pricing_session_line_items
ADD COLUMN IF NOT EXISTS applied_rule TEXT;

COMMENT ON COLUMN pricing_session_line_items.applied_rule IS
  'Comma-separated list of rule names that were applied to calculate this price';
```

**Schema**: The `pricing_session_line_items` table now includes:
- `applied_rule` (TEXT): Comma-separated rule names (e.g., "COST_PLUS_15,VOLUME_DISCOUNT")
- `manual_override` (BOOLEAN): Flag indicating user manually edited price
- `new_unit_sell_price`, `new_amount`, `new_gross_profit`: Calculated pricing fields

**Service Layer Implementation** (`PricingSessionService.java`):

```java
// Convert GroupedLineItem to PricingSessionLineItem for persistence
private PricingSessionLineItem convertToSessionLineItem(GroupedLineItem item) {
    PricingSessionLineItem lineItem = new PricingSessionLineItem();

    // ... copy fields ...

    // Serialize applied rules to comma-separated string
    String appliedRulesStr = null;
    if (item.getAppliedRules() != null && !item.getAppliedRules().isEmpty()) {
        appliedRulesStr = item.getAppliedRules().stream()
                .map(PricingRule::getRuleName)
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
        log.info("Saving product {}: serialized {} rules to '{}'",
                 item.getProductCode(), item.getAppliedRules().size(), appliedRulesStr);
    }
    lineItem.setAppliedRule(appliedRulesStr);
    lineItem.setManualOverride(item.isManualOverride());

    return lineItem;
}
```

**Repository Implementation** (`PricingSessionLineItemRepository.java`):

```java
public void saveAll(Long sessionId, List<PricingSessionLineItem> items) {
    String sql = "INSERT INTO pricing_session_line_items " +
            "(session_id, customer_code, customer_name, customer_rating, product_code, " +
            "product_description, total_quantity, total_amount, original_amount, total_cost, " +
            "amount_modified, last_cost, last_unit_sell_price, last_amount, last_gross_profit, " +
            "incoming_cost, primary_group, new_unit_sell_price, new_amount, new_gross_profit, " +
            "applied_rule, manual_override) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    dsl.batch(
        items.stream()
            .map(item -> dsl.query(sql,
                sessionId, /* ... all fields including applied_rule and manual_override ... */))
            .toArray(org.jooq.Query[]::new)
    ).execute();
}
```

**Benefits:**
- Sessions capture the exact pricing logic used at save time
- Loading a session shows the same rules even if pricing rules have changed
- Full audit trail of which rules were applied to each product
- Supports debugging and verification of pricing calculations

### 4. Rule Restoration from Database

**Context**: When loading a saved session, rule names are stored as text. The system needs to fetch the full rule metadata from the database for proper display.

**Implementation** (`PricingSessionService.java`):

```java
// Convert PricingSessionLineItem to GroupedLineItem on load
private GroupedLineItem convertToGroupedLineItem(PricingSessionLineItem item) {
    GroupedLineItem groupedItem = new GroupedLineItem();

    // ... copy fields ...

    // Restore applied rules from comma-separated string
    String appliedRuleStr = item.getAppliedRule();

    if (appliedRuleStr != null && !appliedRuleStr.trim().isEmpty()) {
        String[] ruleNames = appliedRuleStr.split(",");
        List<PricingRule> restoredRules = new ArrayList<>();

        for (String ruleName : ruleNames) {
            if (ruleName != null && !ruleName.trim().isEmpty()) {
                String trimmedName = ruleName.trim();

                // Try to fetch full rule from database
                Optional<PricingRule> fullRule =
                    pricingRuleRepository.findByRuleName(trimmedName);

                if (fullRule.isPresent()) {
                    restoredRules.add(fullRule.get());
                    log.info("Restored full rule: {} (with all metadata)", trimmedName);
                } else {
                    // Fallback: create lightweight rule with just the name
                    PricingRule lightweightRule = new PricingRule();
                    lightweightRule.setRuleName(trimmedName);
                    restoredRules.add(lightweightRule);
                    log.warn("Rule '{}' not found in database, using lightweight version",
                             trimmedName);
                }
            }
        }

        if (!restoredRules.isEmpty()) {
            groupedItem.setAppliedRules(restoredRules);
        }
    }

    groupedItem.setManualOverride(item.getManualOverride() != null ?
                                  item.getManualOverride() : false);

    return groupedItem;
}
```

**Fallback Strategy:**
- **Primary**: Fetch full rule from `pricing_rule` table by name (includes method, value, category, etc.)
- **Fallback**: If rule was deleted or renamed, create lightweight rule object with just the name
- **Logging**: Clear logs show whether full or lightweight restoration occurred

**Benefits:**
- Full rule metadata available for detailed display
- Graceful degradation if rules are deleted after session save
- Historical sessions remain viewable even if pricing rules change

### 5. formatPricingMethod Update for Actual GP%

**Context**: Previously, the MAINTAIN_GP_PERCENT rule showed the default GP% from the rule definition, not the actual historical GP% that was calculated and used.

**Implementation**:

The `formatPricingMethod()` function now recalculates the historical GP% for each item to show the actual value used:

```java
case "MAINTAIN_GP_PERCENT":
    BigDecimal historicalGP = calculateHistoricalGP(item);
    if (historicalGP != null) {
        // Show ACTUAL historical GP% used in calculation
        BigDecimal gpPercent = historicalGP.multiply(new BigDecimal("100"));
        sb.append("Maintained ").append(gpPercent.setScale(1, RoundingMode.HALF_UP))
          .append("% GP");

        // Add warning if outside normal range
        if (historicalGP.compareTo(WARNING_LOW_GP) < 0) {
            sb.append(" ⚠️ Low");
        } else if (historicalGP.compareTo(WARNING_HIGH_GP) > 0) {
            sb.append(" ⚠️ High");
        }
    } else {
        // Show default GP% (no historical data available)
        BigDecimal defaultGP = rule.getPricingValue().multiply(new BigDecimal("100"));
        sb.append("Maintain GP (default ").append(defaultGP.setScale(0, RoundingMode.HALF_UP))
          .append("%)");
    }
    break;

// Calculate historical GP% using same formula as pricing engine
private BigDecimal calculateHistoricalGP(GroupedLineItem item) {
    if (item == null || item.getLastGrossProfit() == null || item.getLastAmount() == null) {
        return null;
    }

    BigDecimal lastGrossProfit = item.getLastGrossProfit();
    BigDecimal lastAmount = item.getLastAmount();

    if (lastAmount.compareTo(BigDecimal.ZERO) == 0) {
        return null;
    }

    // GP% = Gross Profit / Amount (matches UI calculation)
    return lastGrossProfit.divide(lastAmount, 6, RoundingMode.HALF_UP);
}
```

**Display Examples:**
- With historical data: `"Maintained 18.5% GP"` (shows actual calculated GP%)
- With warning: `"Maintained 72.3% GP ⚠️ High"` (unusual GP% flagged)
- Without history: `"Maintain GP (default 25%)"` (shows rule's default)

**Benefits:**
- Users see the exact GP% being maintained for transparency
- Warnings alert to unusual margins (< 5% or > 70%)
- Distinguishes between historical calculation and default fallback
- Helps identify products with exceptional margins

---

## Core Features

### 1. Pricing Sessions Management

**Location**: `PricingSessionsViewNew.java` (route: "pricing-sessions")

**Key Capabilities:**

#### Session Creation and Saving
```java
@Transactional
public PricingSession saveSession(String sessionName, List<GroupedLineItem> lineItems) {
    // Check if session exists
    Optional<PricingSession> existingSession =
        pricingSessionRepository.findBySessionName(sessionName);

    if (existingSession.isPresent()) {
        // Update: delete old line items, insert new ones
        session = existingSession.get();
        session.setLastModifiedDate(LocalDateTime.now());
        lineItemRepository.deleteBySessionId(session.getId());
    } else {
        // Create new session
        session = new PricingSession();
        session.setSessionName(sessionName);
        session.setStatus("IN_PROGRESS");
    }

    // Save session and all line items
    session = pricingSessionRepository.save(session);
    lineItemRepository.saveAll(session.getId(), sessionLineItems);

    return session;
}
```

#### Session Loading with Unsaved Changes Dialog

When loading a session with unsaved changes, users are presented with three clear options:

```java
private void loadSessionWithUnsavedChanges(PricingSession session) {
    ConfirmDialog dialog = new ConfirmDialog();
    dialog.setHeader("Unsaved Changes");

    String message = "You have unsaved changes";
    if (currentSession != null && currentSession.getSessionName() != null) {
        message += " in session '" + currentSession.getSessionName() + "'";
    }
    message += ". What would you like to do?";
    dialog.setText(message);

    // Primary action: Save & Load (green)
    dialog.setConfirmText("Save & Load");
    dialog.addConfirmListener(e -> {
        saveCurrentSession();
        loadSessionData(session);
    });
    dialog.setConfirmButtonTheme("success primary");

    // Destructive action: Discard & Load (red)
    dialog.setRejectText("Discard & Load");
    dialog.addRejectListener(e -> {
        hasUnsavedChanges = false;
        loadSessionData(session);
    });
    dialog.setRejectButtonTheme("error");

    // Cancel: Stay in current session
    dialog.setCancelText("Cancel");
    dialog.setCancelable(true);

    dialog.open();
}
```

#### Work-in-Progress Persistence

Sessions persist across navigation using VaadinSession:

```java
@Override
public void beforeLeave(BeforeLeaveEvent event) {
    if (hasUnsavedChanges && backingList != null && !backingList.isEmpty()) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            // Save working data to browser session
            session.setAttribute(SESSION_DATA_KEY, new ArrayList<>(backingList));
            session.setAttribute(SESSION_STATE_KEY, currentSession);
        }
    }
    event.getContinueNavigationAction().proceed();
}
```

**Visual Feedback:**
- Title shows current session name (e.g., "Pricing Sessions - November Analysis")
- Orange title indicates unsaved changes
- Notification on restore: "Restored unsaved work from this browser session"

### 2. Rule-Based Pricing Engine

**Location**: `PriceCalculationService.java`

**Architecture**: Layered calculation approach where rules are applied in sequence by category:

#### Rule Categories (RuleCategory enum)
```java
public enum RuleCategory {
    BASE_PRICE("Base Price", true),           // Sets initial price, single rule only
    CUSTOMER_ADJUSTMENT("Customer Adj", false),  // Customer discounts/fees, multiple
    PRODUCT_ADJUSTMENT("Product Adj", false),    // Product-specific fees, multiple
    PROMOTIONAL("Promotional", false);           // Promotions, multiple

    private final String displayName;
    private final boolean singleRuleOnly;
}
```

#### Calculation Flow

```java
public PricingResult calculatePrice(GroupedLineItem item, LocalDate pricingDate,
                                   Customer customer) {
    BigDecimal currentPrice = item.getIncomingCost();
    List<PricingRule> appliedRules = new ArrayList<>();
    List<BigDecimal> intermediateResults = new ArrayList<>();
    intermediateResults.add(currentPrice); // Starting point

    // Apply rules layer by layer
    for (RuleCategory category : RuleCategory.values()) {
        List<PricingRule> layerRules = findMatchingRulesInLayer(item, category, pricingDate);

        if (category.isSingleRuleOnly()) {
            // BASE_PRICE: only first matching rule applies
            if (!layerRules.isEmpty()) {
                PricingRule rule = layerRules.get(0);
                currentPrice = applyRuleToPrice(currentPrice, rule, item);
                appliedRules.add(rule);
                intermediateResults.add(currentPrice);
            }
        } else {
            // Multiple rules can apply in this layer
            for (PricingRule rule : layerRules) {
                currentPrice = applyRuleToPrice(currentPrice, rule, item);
                appliedRules.add(rule);
                intermediateResults.add(currentPrice);
            }
        }
    }

    BigDecimal finalPrice = currentPrice.setScale(6, RoundingMode.HALF_UP);
    String description = formatMultiRuleDescription(appliedRules);

    return new PricingResult(item.getIncomingCost(), finalPrice,
                            appliedRules, intermediateResults, description);
}
```

#### Pricing Methods

**COST_PLUS_PERCENT**: Apply percentage markup/rebate
```java
// value stored as multiplier: 1.20 for +20%, 0.80 for -20%
return currentPrice.multiply(value);
```

**COST_PLUS_FIXED**: Add fixed amount
```java
// value = 2.50 for $2.50 addition
return currentPrice.add(value);
```

**FIXED_PRICE**: Set absolute price
```java
// value = 28.50 for fixed $28.50
return value;
```

**MAINTAIN_GP_PERCENT**: Maintain historical gross profit percentage
```java
BigDecimal historicalGP = calculateHistoricalGP(item);
if (historicalGP == null) {
    historicalGP = value; // Use default from rule
}

// price = cost / (1 - GP%)
BigDecimal divisor = BigDecimal.ONE.subtract(historicalGP);
return item.getIncomingCost().divide(divisor, 6, RoundingMode.HALF_UP);
```

#### Rule Matching

```java
private boolean ruleMatchesItem(PricingRule rule, GroupedLineItem item) {
    // Customer-specific rule
    if (rule.getCustomerCode() != null) {
        if (!rule.getCustomerCode().equals(item.getCustomerCode())) {
            return false;
        }
    }

    // Condition-based matching
    String conditionType = rule.getConditionType();
    switch (conditionType) {
        case "ALL_PRODUCTS":
            return true;
        case "CATEGORY":
            return rule.getConditionValue() != null
                    && item.getPrimaryGroup() != null
                    && rule.getConditionValue().equalsIgnoreCase(item.getPrimaryGroup());
        case "PRODUCT_CODE":
            return rule.getConditionValue() != null
                    && item.getProductCode() != null
                    && rule.getConditionValue().equalsIgnoreCase(item.getProductCode());
        default:
            return false;
    }
}
```

### 3. Historical Pricing Data Tracking

**Purpose**: Enable MAINTAIN_GP_PERCENT rule by tracking what actually happened in past transactions.

**Database Fields** (in `pricing_session_line_items` and `GroupedLineItem`):

```java
// Historical data (from v_grouped_line_items)
private BigDecimal lastCost;          // Historical cost when sold
private BigDecimal lastUnitSellPrice; // Historical sell price
private BigDecimal lastAmount;        // Historical total amount
private BigDecimal lastGrossProfit;   // Historical gross profit

// New pricing data
private BigDecimal incomingCost;      // From product_costs.stdcost
private BigDecimal newUnitSellPrice;  // Calculated by rules or manual
private BigDecimal newAmount;         // newUnitSellPrice × qty
private BigDecimal newGrossProfit;    // newAmount - (incomingCost × qty)
```

**Data Flow:**

1. **Import Phase**: Sales transactions stored in `imported_line_items`
2. **Aggregation**: View `v_grouped_line_items` aggregates by customer+product
3. **JOIN with Costs**: Service joins grouped items with `product_costs` for incoming cost
4. **Historical Calculation**: `lastGrossProfit / lastAmount` = historical GP%
5. **New Price Calculation**: `incomingCost / (1 - GP%)` = new price
6. **Persistence**: All fields saved to `pricing_session_line_items`

**GP% Calculation Formula** (matches UI display):
```java
private BigDecimal calculateHistoricalGP(GroupedLineItem item) {
    if (item.getLastGrossProfit() == null || item.getLastAmount() == null) {
        return null;
    }

    if (item.getLastAmount().compareTo(BigDecimal.ZERO) == 0) {
        return null;
    }

    // GP% = Gross Profit / Amount (using totals to avoid rounding errors)
    return item.getLastGrossProfit().divide(item.getLastAmount(), 6, RoundingMode.HALF_UP);
}
```

### 4. Cost Import and Product Cost Management

**Location**: `ImportPricingView.java` (route: "import-pricing")

**Product Cost Import**:
- UPSERT strategy: safe replacement of existing data
- Primary cost field: `standard_cost` (Column Z/25 from Excel)
- Fields: product_code, description, standard_cost, latest_cost, average_cost, supplier_cost, sell_price_1 through sell_price_10

**Cost Import Service** (`ProductCostImportService.java`):
```java
public void importProductCosts(InputStream inputStream, String fileName) {
    // Parse Excel starting at row 1 (row 0 is header)
    // Column 25 (Z) is STDCOST - the primary cost field

    dsl.insertInto(PRODUCT_COSTS)
        .set(PRODUCT_COSTS.PRODUCT_CODE, productCode)
        .set(PRODUCT_COSTS.STANDARD_COST, stdCost)
        // ... other fields ...
        .onDuplicateKeyUpdate()
        .set(PRODUCT_COSTS.STANDARD_COST, stdCost)
        // ... other fields ...
        .execute();
}
```

### 5. Customer Ratings and Customer-Specific Pricing

**Customer Rating System**:

Three algorithms calculate customer value:

1. **Original**: `sqrt((amount / 1000 × GP%) × 100)` - User's proposed formula
2. **Modified**: `(amount / 1000) + (GP% × 10)` - Additive version
3. **Claude**: `(Gross_Profit_Dollars × 0.7) + (Revenue_Percentile × 0.3)` - Weighted approach

**Storage Format**: `"original: 23 | modified: 234 | claude: 223"`

**Customer-Specific Pricing Rules**:

```java
// Rule with customer_code set applies only to that customer
PricingRule customerRule = new PricingRule();
customerRule.setCustomerCode("ABC001");
customerRule.setConditionType("ALL_PRODUCTS");  // All products for this customer
customerRule.setPricingMethod("COST_PLUS_PERCENT");
customerRule.setPricingValue(new BigDecimal("1.10"));  // 10% markup
```

### 6. Manual Price Overrides

**Implementation**: Users can click "New Unit Sell Price" to manually set price.

```java
private void openPriceEditDialog(GroupedLineItem item) {
    Dialog dialog = new Dialog();

    NumberField manualPriceField = new NumberField("New Unit Sell Price");
    manualPriceField.setValue(item.getNewUnitSellPrice().doubleValue());

    Button saveButton = new Button("Save", e -> {
        BigDecimal newPrice = BigDecimal.valueOf(manualPriceField.getValue());
        BigDecimal newAmount = newPrice.multiply(item.getTotalQuantity());

        item.setNewUnitSellPrice(newPrice);
        item.setNewAmount(newAmount);
        item.setManualOverride(true);  // Flag as manually edited
        item.setAppliedRules(null);    // Clear auto-applied rules

        dataGrid.getDataProvider().refreshItem(item);
        updateFooterTotals();
        hasUnsavedChanges = true;
        updateTitleWithSessionName();

        dialog.close();
    });

    dialog.add(manualPriceField, saveButton);
    dialog.open();
}
```

**Tracking**: `manualOverride` flag stored in database, displayed as "Manual Override" in Pricing Method column.

---

## Technical Architecture

### Application Layer Structure

```
┌─────────────────────────────────────────────────────────────┐
│                    Vaadin UI Layer (Java)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐     │
│  │ PricingData  │  │  Pricing     │  │    Reports    │     │
│  │     View     │  │SessionsView  │  │     View      │     │
│  │  (read-only) │  │  (editable)  │  │ (export XLS)  │     │
│  └──────────────┘  └──────────────┘  └───────────────┘     │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                      Service Layer                            │
│  ┌─────────────────┐  ┌──────────────────┐                  │
│  │ PricingImport   │  │ PriceCalculation │                  │
│  │    Service      │  │     Service      │                  │
│  └─────────────────┘  └──────────────────┘                  │
│  ┌─────────────────┐  ┌──────────────────┐                  │
│  │ PricingSession  │  │  CustomerRating  │                  │
│  │    Service      │  │     Service      │                  │
│  └─────────────────┘  └──────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   Repository Layer (jOOQ)                     │
│  ┌─────────────────┐  ┌──────────────────┐                  │
│  │ImportedLineItem │  │  PricingSession  │                  │
│  │   Repository    │  │  LineItemRepo    │                  │
│  └─────────────────┘  └──────────────────┘                  │
│  ┌─────────────────┐  ┌──────────────────┐                  │
│  │  PricingRule    │  │    Customer      │                  │
│  │   Repository    │  │   Repository     │                  │
│  └─────────────────┘  └──────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                PostgreSQL Database                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Tables: imported_line_items, pricing_sessions,       │   │
│  │         pricing_session_line_items, pricing_rule,    │   │
│  │         product_costs, customers                     │   │
│  │ Views: v_grouped_line_items, v_invoices              │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Key Architectural Patterns

#### 1. AbstractGridView Pattern

Both `PricingDataView` and `PricingSessionsView` extend `AbstractGridView`:

```java
public abstract class AbstractGridView extends VerticalLayout {
    protected abstract String getStoragePrefix();

    protected void saveColumnVisibility(String columnKey, boolean visible) {
        String storageKey = getStoragePrefix() + columnKey;
        getElement().executeJs(
            "localStorage.setItem($0, $1)",
            storageKey,
            String.valueOf(visible)
        );
    }

    protected void restoreColumn(String columnKey, Checkbox checkbox) {
        String storageKey = getStoragePrefix() + columnKey;
        getElement().executeJs(
            "return localStorage.getItem($0)",
            storageKey
        ).then(String.class, value -> {
            if (value != null) {
                boolean visible = Boolean.parseBoolean(value);
                checkbox.setValue(visible);
            }
        });
    }
}
```

**Benefits:**
- Automatic column visibility persistence to browser localStorage
- Consistent UX across views
- Each view has unique storage prefix to avoid conflicts

#### 2. Backing List Pattern

Two-tier filtering for performance:

```java
// Primary filter: Date range (database query)
private void applyFilter() {
    backingList = pricingImportService.getGroupedLineItemsByDateRange(startDate, endDate);
    applySecondaryFilters();
}

// Secondary filters: Customer/product (in-memory filtering)
private void applySecondaryFilters() {
    List<GroupedLineItem> filtered = backingList.stream()
        .filter(item -> customerNameFilter.isEmpty() ||
                item.getCustomerName().toLowerCase()
                    .contains(customerNameFilter.getValue().toLowerCase()))
        .filter(item -> productFilter.isEmpty() ||
                item.getProductDescription().toLowerCase()
                    .contains(productFilter.getValue().toLowerCase()))
        .collect(Collectors.toList());
    dataGrid.setItems(filtered);
}
```

**Benefits:**
- Single database query for date filter
- Fast in-memory filtering for customer/product
- Reduces database load

#### 3. Session State Management

Three levels of state:

1. **Component State** (in-memory): `backingList`, `currentSession`, `hasUnsavedChanges`
2. **VaadinSession State** (browser session): Work-in-progress data persisted across navigation
3. **Database State**: Explicitly saved sessions with all line items

```java
// Level 1: Component state
private List<GroupedLineItem> backingList = new ArrayList<>();
private PricingSession currentSession = null;
private boolean hasUnsavedChanges = false;

// Level 2: VaadinSession persistence
@Override
public void beforeLeave(BeforeLeaveEvent event) {
    if (hasUnsavedChanges) {
        VaadinSession.getCurrent().setAttribute(SESSION_DATA_KEY, backingList);
        VaadinSession.getCurrent().setAttribute(SESSION_STATE_KEY, currentSession);
    }
}

// Level 3: Database persistence
private void saveCurrentSession() {
    String sessionName = // get from dialog
    currentSession = pricingSessionService.saveSession(sessionName, backingList);
    hasUnsavedChanges = false;
}
```

#### 4. Transient Fields for UI State

```java
public class GroupedLineItem {
    // Persisted fields
    private BigDecimal totalAmount;
    private BigDecimal newUnitSellPrice;

    // Transient UI state (not persisted to DB directly)
    private transient boolean amountModified = false;
    private transient BigDecimal originalAmount = null;
    private transient PricingRule appliedRule;
    private transient boolean manualOverride;
    private transient List<PricingRule> appliedRules;
    private transient List<BigDecimal> intermediateResults;
}
```

**Note**: While marked `transient` in Java, these fields ARE persisted when saving sessions via service layer conversion.

#### 5. Modern Download Handler Pattern

Vaadin Flow recommended approach for file downloads:

```java
public void exportReport() {
    byte[] excelBytes = reportExportService.generateReport(...);

    Anchor downloadLink = new Anchor(event -> {
        try {
            event.setFileName("Report.xlsx");
            event.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            event.setContentLength(excelBytes.length);

            try (OutputStream outputStream = event.getOutputStream()) {
                outputStream.write(excelBytes);
            }

            event.getUI().access(() ->
                Notification.show("Downloaded successfully!"));
        } catch (IOException e) {
            event.getResponse().setStatus(500);
            event.getUI().access(() ->
                Notification.show("Error: " + e.getMessage(), 5000,
                                 Notification.Position.MIDDLE));
        }
    }, "");

    downloadLink.getElement().setAttribute("style", "display: none;");
    add(downloadLink);
    downloadLink.getElement().callJsFunction("click");
}
```

---

## Key Implementation Details

### 1. PricingSessionService.java

**Responsibilities:**
- Save sessions with line items transactionally
- Load sessions and restore GroupedLineItem objects
- Serialize/deserialize applied rules
- Delete sessions (cascade to line items)

**Critical Methods:**

```java
@Transactional
public PricingSession saveSession(String sessionName, List<GroupedLineItem> lineItems)

@Transactional(readOnly = true)
public List<GroupedLineItem> loadSession(Long sessionId)

private PricingSessionLineItem convertToSessionLineItem(GroupedLineItem item)
private GroupedLineItem convertToGroupedLineItem(PricingSessionLineItem item)
```

### 2. PriceCalculationService.java

**Responsibilities:**
- Execute layered pricing calculation
- Match rules to items
- Apply pricing methods
- Track intermediate results
- Format rule descriptions

**Critical Methods:**

```java
public PricingResult calculatePrice(GroupedLineItem item, LocalDate pricingDate, Customer customer)
private List<PricingRule> findMatchingRulesInLayer(GroupedLineItem item, RuleCategory category, LocalDate pricingDate)
private boolean ruleMatchesItem(PricingRule rule, GroupedLineItem item)
private BigDecimal applyRuleToPrice(BigDecimal currentPrice, PricingRule rule, GroupedLineItem item)
private BigDecimal calculateHistoricalGP(GroupedLineItem item)
private String formatMultiRuleDescription(List<PricingRule> rules)
```

### 3. PricingSessionsViewNew.java

**Responsibilities:**
- Display grouped line items with historical and new pricing
- Apply pricing rules automatically
- Allow manual price overrides
- Save/load sessions
- Persist work-in-progress to VaadinSession
- Two-tier filtering (date + customer/product)
- Footer totals

**Grid Structure:**

```
Customer | Rating | Product Code | Product Desc | Qty |
-----------------------------------------------------
         HISTORICAL PRICING (Last Period)          |
-----------------------------------------------------
Last Cost | Last Price | Last Amount | Last GP | Last GP% | Cost Drift |
-----------------------------------------------------
         NEW PRICING (Calculated)                  |
-----------------------------------------------------
New Cost | New Price | New Amount | New GP | New GP% | Pricing Method
```

**Key Methods:**

```java
private void applyPricingRulesToAllItems()
private void openPriceEditDialog(GroupedLineItem item)
private void saveCurrentSession()
private void loadSessionData(PricingSession session)
private void restoreWorkingDataFromSession()
private String formatPricingMethod(GroupedLineItem item)
private void updateFooterTotals()
```

### 4. PricingSessionLineItemRepository.java

**Responsibilities:**
- Batch insert line items
- Load line items by session ID
- Delete line items by session ID
- Map jOOQ records to POJOs

**Batch Insert Strategy:**

```java
public void saveAll(Long sessionId, List<PricingSessionLineItem> items) {
    String sql = "INSERT INTO pricing_session_line_items (...) VALUES (?, ?, ...)";

    dsl.batch(
        items.stream()
            .map(item -> dsl.query(sql, sessionId, /* all 22 fields */))
            .toArray(org.jooq.Query[]::new)
    ).execute();
}
```

**Benefits:**
- Single batch statement for all items (performance)
- Explicit SQL for clarity and control
- Handles nullable fields properly

### 5. Database Migration Strategy

**File**: `src/main/resources/db/changelog/changes/001-initial-schema.sql`

All tables defined in single consolidated migration:
- Section 1: Staging tables (imported_line_items, import_summary)
- Section 2: Views (v_grouped_line_items, v_invoices)
- Section 3: Master data (product_costs, customers)
- Section 4: Pricing sessions (pricing_sessions, pricing_session_line_items)
- Section 5: Pricing rules (pricing_rule)

**Recent Addition**: `018-add-applied-rule-text-column.sql`
- Adds `applied_rule` TEXT column to store comma-separated rule names
- Required for rule persistence and restoration

---

## Database Schema

### Pricing Session Tables

#### pricing_sessions

Stores metadata for pricing sessions.

```sql
CREATE TABLE pricing_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_name VARCHAR(255) NOT NULL UNIQUE,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    notes TEXT
);
```

**Key Points:**
- `session_name` is unique (enforced by database)
- `status` typically 'IN_PROGRESS', 'COMPLETED', 'ARCHIVED'
- `notes` field exists but not fully integrated in UI

#### pricing_session_line_items

Stores individual line items within a pricing session with full pricing breakdown.

```sql
CREATE TABLE pricing_session_line_items (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    customer_code VARCHAR(255),
    customer_name VARCHAR(255),
    customer_rating VARCHAR(50),
    product_code VARCHAR(255),
    product_description TEXT,
    primary_group VARCHAR(255),
    total_quantity NUMERIC(19,6),
    total_amount NUMERIC(19,6),
    original_amount NUMERIC(19,6),
    total_cost NUMERIC(19,6),
    amount_modified BOOLEAN NOT NULL DEFAULT FALSE,

    -- Historical pricing data
    last_cost NUMERIC(19,6),
    last_unit_sell_price NUMERIC(19,6),
    last_amount NUMERIC(19,6),
    last_gross_profit NUMERIC(19,6),

    -- New pricing data
    incoming_cost NUMERIC(19,6),
    new_unit_sell_price NUMERIC(19,6),
    new_amount NUMERIC(19,6),
    new_gross_profit NUMERIC(19,6),

    -- Rule tracking
    applied_rule TEXT,  -- Comma-separated rule names
    manual_override BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_session FOREIGN KEY (session_id)
        REFERENCES pricing_sessions(id) ON DELETE CASCADE
);
```

**Key Points:**
- Foreign key with CASCADE delete (delete session = delete all line items)
- `applied_rule` stores rule chain (e.g., "COST_PLUS_15,VOLUME_DISCOUNT")
- `manual_override` flag distinguishes user edits from rule-calculated prices
- Historical fields enable MAINTAIN_GP_PERCENT calculation
- All monetary values use NUMERIC(19,6) for precision

### Pricing Rule Table

#### pricing_rule

Stores dynamic pricing rules.

```sql
CREATE TABLE pricing_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(255) NOT NULL UNIQUE,
    customer_code VARCHAR(255),  -- NULL = standard rule
    condition_type VARCHAR(50) NOT NULL,  -- ALL_PRODUCTS, CATEGORY, PRODUCT_CODE
    condition_value VARCHAR(255),
    pricing_method VARCHAR(50) NOT NULL,  -- COST_PLUS_PERCENT, COST_PLUS_FIXED, etc.
    pricing_value NUMERIC(19,6),
    priority INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Phase 2 additions
    rule_category VARCHAR(50),  -- BASE_PRICE, CUSTOMER_ADJUSTMENT, etc.
    layer_order INTEGER,        -- Order within category
    valid_from DATE,            -- Optional activation date
    valid_to DATE               -- Optional expiration date
);
```

**Key Points:**
- `rule_name` must be unique (required for deserialization from saved sessions)
- `customer_code` NULL = standard rule, value = customer-specific
- `priority` determines order within layer (lower = higher priority)
- `rule_category` defines which layer the rule belongs to
- `valid_from` / `valid_to` enable date-based rule activation

### Views

#### v_grouped_line_items

Aggregates imported line items by customer and product.

```sql
CREATE OR REPLACE VIEW v_grouped_line_items AS
SELECT
    customer_code,
    customer_name,
    product_code,
    product_description,
    SUM(quantity) AS total_quantity,
    SUM(amount) AS total_amount,
    SUM(cost) AS total_cost
FROM imported_line_items
GROUP BY customer_code, customer_name, product_code, product_description;
```

**Purpose**: Efficient server-side aggregation for Pricing Sessions view

**Note**: For date-filtered queries, the service queries `imported_line_items` directly with WHERE + GROUP BY instead of using this view.

---

## Development Status

### Fully Implemented Features

- **Pricing Sessions**: Save, load, delete with full persistence
- **Work-in-Progress Persistence**: VaadinSession-based across navigation
- **Rule-Based Pricing Engine**: Layered multi-rule calculation
- **Historical Pricing Tracking**: Complete data capture for GP% maintenance
- **Rule Persistence**: Applied rules saved and restored with sessions
- **Manual Overrides**: User can manually set prices with tracking
- **Customer Ratings**: Three algorithms with auto-calculation
- **Cost Import**: UPSERT strategy for product cost data
- **Reports**: Customer rating and cost variance reports with Excel export

### Known Limitations

1. **Session Notes**: Field exists but not fully integrated in save workflow
2. **Single Undo Level**: Can only undo most recent price change
3. **No Batch Price Editing**: Price changes applied one dialog at a time
4. **No Export to Source System**: Cannot push modified pricing back to business system
5. **Limited Audit Trail**: Session changes tracked, but not full historical audit
6. **No User Authentication**: System is currently single-user

### In Development

1. **Pricing Rules Management UI**: View for creating/editing pricing rules
2. **Rule Preview**: Show which rules would apply to current data before saving
3. **Advanced Reporting**: More analytical reports and visualizations
4. **Bulk Operations**: Apply rules or overrides to multiple items at once

### Future Enhancements

1. **Product Cost Integration**: Join with product_costs for margin analysis
2. **Behavioral Adjustments**: Enhance Claude rating algorithm with:
   - Payment behavior (days outstanding)
   - Consistency (order frequency)
   - Growth (YoY trends)
3. **Rule Effectiveness Analysis**: Report showing impact of each rule
4. **Session Comparison**: Side-by-side comparison of multiple sessions
5. **What-If Scenarios**: Test rule changes before applying
6. **User Authentication**: Multi-user support with role-based access

---

## Technical Debt and Improvements

### Code Quality

1. **Deprecated Methods**: `PriceCalculationService` has deprecated single-rule methods for backward compatibility
2. **Error Handling**: Some services could use more robust exception handling
3. **Logging**: Comprehensive logging exists but could be more structured
4. **Testing**: Limited automated tests (primarily manual browser testing)

### Performance Considerations

1. **Batch Operations**: Pricing rule application loops through items one-by-one
2. **Grid Rendering**: Large datasets (10,000+ items) may slow rendering
3. **Session Restoration**: Loading large sessions could benefit from lazy loading

### Documentation

1. **Inline Comments**: Code is well-commented, especially complex calculations
2. **API Documentation**: Service methods have clear Javadoc
3. **Database Comments**: Schema includes COMMENT ON statements for tables/columns

---

## File Locations Reference

**Key Implementation Files:**

```
src/main/java/com/meatrics/
├── pricing/
│   ├── ui/
│   │   ├── PricingSessionsViewNew.java      # Main pricing sessions UI
│   │   ├── PricingDataView.java             # Read-only transaction view
│   │   ├── ReportsView.java                 # Report generation UI
│   │   └── ImportPricingView.java           # Excel import UI
│   ├── PricingSessionService.java           # Session save/load logic
│   ├── PriceCalculationService.java         # Rule-based pricing engine
│   ├── PricingSessionLineItemRepository.java # Line item persistence
│   ├── PricingRuleRepository.java           # Rule data access
│   ├── GroupedLineItem.java                 # Main DTO with all pricing fields
│   ├── PricingRule.java                     # Rule entity
│   └── RuleCategory.java                    # Rule layer enum
├── base/
│   └── ui/
│       ├── AbstractGridView.java            # Column visibility base class
│       └── MainLayout.java                  # Application layout
└── Application.java                         # Spring Boot entry point

src/main/resources/
├── db/changelog/
│   ├── db.changelog-master.xml              # Liquibase master file
│   └── changes/
│       ├── 001-initial-schema.sql           # Consolidated schema
│       └── 018-add-applied-rule-text-column.sql # Rule persistence
└── application.properties                    # Configuration
```

---

## Conclusion

The Meatrics application is in active development with a solid foundation:

- **Core pricing engine** fully functional with layered multi-rule support
- **Session management** complete with work-in-progress persistence
- **Historical pricing tracking** enables sophisticated GP% maintenance
- **Rule persistence** provides full audit trail and reproducibility
- **Modern Vaadin Flow patterns** for maintainable UI code

The application successfully handles the meat distribution pricing use case with transparency, flexibility, and user-friendly workflows. Recent enhancements to session persistence and rule tracking have significantly improved the user experience and data integrity.

---

**For more information:**
- PROJECT_OVERVIEW.md - Comprehensive project documentation
- HANDOFF.md - Quick reference for developers
- JOOQ_GENERATION.md - Database schema workflow
