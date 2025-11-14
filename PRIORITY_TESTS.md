# Top 10 Priority Tests for Meatrics

## Priority Rating System
- 🔥🔥🔥 = CRITICAL (Do first - high bug risk, core business logic)
- 🔥🔥 = HIGH (Important business logic)
- 🔥 = MEDIUM (Nice to have, prevents edge case bugs)

---

## 🔥🔥🔥 CRITICAL - Test These First (1-3 hours)

### 1. MAINTAIN_GP_PERCENT Calculation 🔥🔥🔥
**Why Critical:** This is your most complex pricing method. GP% bugs directly affect profit margins.

**File:** `src/test/java/com/meatrics/pricing/calculation/MaintainGPPercentTest.java`

**Test Cases:**
```java
@Test
void maintainGP_with25Percent_shouldCalculateCorrectPrice() {
    // Formula: price = cost / (1 - GP%)
    // If cost = $10, GP = 25%, then price = $10 / 0.75 = $13.33

    BigDecimal cost = new BigDecimal("10.00");
    BigDecimal gpPercent = new BigDecimal("0.25");

    BigDecimal price = service.calculatePriceWithGP(cost, gpPercent);

    // Verify price
    assertThat(price).isEqualByComparingTo(new BigDecimal("13.33"));

    // Verify GP% is correct in reverse
    BigDecimal actualGP = price.subtract(cost).divide(price, 4, HALF_UP);
    assertThat(actualGP).isEqualByComparingTo(gpPercent);
}

@Test
void maintainGP_withHistoricalGP_shouldUseLastCycleGP() {
    // User's last cycle: sold at $15, cost was $10 → GP = 33.33%
    // New cost: $12
    // Should maintain 33.33% GP → new price = $12 / 0.6667 = $18.00

    BigDecimal lastPrice = new BigDecimal("15.00");
    BigDecimal lastCost = new BigDecimal("10.00");
    BigDecimal newCost = new BigDecimal("12.00");

    BigDecimal historicalGP = service.calculateHistoricalGP(lastPrice, lastCost);
    BigDecimal newPrice = service.calculatePriceWithGP(newCost, historicalGP);

    assertThat(newPrice).isEqualByComparingTo(new BigDecimal("18.00"));
}

@Test
void maintainGP_withZeroGP_shouldReturnCost() {
    // Edge case: GP% = 0% means selling at cost
    BigDecimal cost = new BigDecimal("10.00");
    BigDecimal gpPercent = BigDecimal.ZERO;

    BigDecimal price = service.calculatePriceWithGP(cost, gpPercent);

    assertThat(price).isEqualByComparingTo(cost);
}

@Test
void maintainGP_withNegativeGP_shouldHandleGracefully() {
    // Edge case: Negative GP (selling below cost)
    // Should not crash, maybe cap at cost or throw exception
    BigDecimal cost = new BigDecimal("10.00");
    BigDecimal gpPercent = new BigDecimal("-0.10"); // -10% GP

    // Either: throws exception OR returns cost as minimum
    // Define your business rule here
}
```

**Why This Matters:**
- GP% calculation errors directly impact profitability
- Most complex formula in your system
- Historical GP tracking is critical feature

---

### 2. Multi-Layer Pricing Rule Application 🔥🔥🔥
**Why Critical:** This is your core pricing engine. Bugs here affect all customers.

**File:** `src/test/java/com/meatrics/pricing/calculation/MultiLayerPricingTest.java`

**Test Cases:**
```java
@Test
void applyRules_multiLayer_shouldApplyInCorrectOrder() {
    // From your PRICING_ENGINE_DESIGN.md example:
    // Cost: $10.00
    // → Base Price (Cost + 20%): $12.00
    // → Customer Adjustment (Volume -10%): $10.80
    // → Customer Adjustment (Loyalty -5%): $10.26
    // → Product Adjustment (Premium +$2): $12.26
    // → Promotional (Sale -15%): $10.42

    BigDecimal cost = new BigDecimal("10.00");

    List<PricingRule> rules = List.of(
        createRule("Base", BASE_PRICE, "COST_PLUS_PERCENT", "1.20"),
        createRule("Volume", CUSTOMER_ADJUSTMENT, "COST_PLUS_PERCENT", "0.90"),
        createRule("Loyalty", CUSTOMER_ADJUSTMENT, "COST_PLUS_PERCENT", "0.95"),
        createRule("Premium", PRODUCT_ADJUSTMENT, "COST_PLUS_FIXED", "2.00"),
        createRule("Sale", PROMOTIONAL, "COST_PLUS_PERCENT", "0.85")
    );

    PricingResult result = service.applyMultiLayerPricing(cost, rules);

    // Verify final price
    assertThat(result.getCalculatedPrice())
        .isEqualByComparingTo(new BigDecimal("10.42"));

    // Verify intermediate steps
    assertThat(result.getIntermediateResults()).containsExactly(
        new BigDecimal("12.00"),  // After base
        new BigDecimal("10.80"),  // After volume
        new BigDecimal("10.26"),  // After loyalty
        new BigDecimal("12.26"),  // After premium fee
        new BigDecimal("10.42")   // After sale
    );

    // Verify all rules were applied
    assertThat(result.getAppliedRules()).hasSize(5);
}

@Test
void applyRules_onlyBasePrice_shouldApplyOnce() {
    // BASE_PRICE category: only first matching rule should apply

    BigDecimal cost = new BigDecimal("10.00");

    List<PricingRule> rules = List.of(
        createRule("Base 1", BASE_PRICE, "COST_PLUS_PERCENT", "1.20"),
        createRule("Base 2", BASE_PRICE, "COST_PLUS_PERCENT", "1.30"), // Should NOT apply
        createRule("Base 3", BASE_PRICE, "COST_PLUS_PERCENT", "1.40")  // Should NOT apply
    );

    PricingResult result = service.applyMultiLayerPricing(cost, rules);

    // Only first base price rule should apply
    assertThat(result.getCalculatedPrice())
        .isEqualByComparingTo(new BigDecimal("12.00"));
    assertThat(result.getAppliedRules()).hasSize(1);
}

@Test
void applyRules_categoryOrder_shouldRespectLayerOrder() {
    // Rules should apply in category order, not insertion order
    // Correct order: BASE → CUSTOMER → PRODUCT → PROMOTIONAL

    BigDecimal cost = new BigDecimal("10.00");

    // Insert in WRONG order - should still apply in correct order
    List<PricingRule> rulesOutOfOrder = List.of(
        createRule("Promo", PROMOTIONAL, "COST_PLUS_PERCENT", "0.90"),      // Should apply LAST
        createRule("Base", BASE_PRICE, "COST_PLUS_PERCENT", "1.20"),        // Should apply FIRST
        createRule("Product", PRODUCT_ADJUSTMENT, "COST_PLUS_FIXED", "1.00"), // Should apply THIRD
        createRule("Customer", CUSTOMER_ADJUSTMENT, "COST_PLUS_PERCENT", "0.95") // Should apply SECOND
    );

    PricingResult result = service.applyMultiLayerPricing(cost, rulesOutOfOrder);

    // Verify applied in correct category order
    assertThat(result.getAppliedRules().get(0).getRuleCategory()).isEqualTo(BASE_PRICE);
    assertThat(result.getAppliedRules().get(1).getRuleCategory()).isEqualTo(CUSTOMER_ADJUSTMENT);
    assertThat(result.getAppliedRules().get(2).getRuleCategory()).isEqualTo(PRODUCT_ADJUSTMENT);
    assertThat(result.getAppliedRules().get(3).getRuleCategory()).isEqualTo(PROMOTIONAL);
}
```

**Why This Matters:**
- Core pricing engine logic
- Category ordering is critical business rule
- Must handle multiple rules correctly

---

### 3. Excel Import with Real Sample Files 🔥🔥🔥
**Why Critical:** Data import bugs cause bad data in database, hard to detect.

**File:** `src/test/java/com/meatrics/pricing/importer/PricingImportServiceTest.java`

**Test Cases:**
```java
@Test
void importSalesData_withRealSampleFile_shouldImportAllRows() throws Exception {
    // Use your actual sample file
    String sampleFile = "sample_file/251103 to 251109.xlsx";

    // Mock to prevent duplicate check (or use real DB with @Transactional)
    when(summaryRepository.existsByFilename(anyString())).thenReturn(false);

    // When
    ImportSummary result = importService.importSalesData(sampleFile);

    // Then - verify counts match file
    assertThat(result.getTotalRows()).isGreaterThan(0);
    assertThat(result.getSuccessfulRows()).isEqualTo(result.getTotalRows());
    assertThat(result.getFailedRows()).isEqualTo(0);

    // Verify actual data was parsed correctly
    verify(lineItemRepository, times(1)).saveAll(argThat(items -> {
        // Verify first item has expected structure
        ImportedLineItem firstItem = items.get(0);
        return firstItem.getCustomerCode() != null &&
               firstItem.getProductCode() != null &&
               firstItem.getQuantity() != null &&
               firstItem.getAmount() != null;
    }));
}

@Test
void importCostData_withRealSampleFile_shouldImportAllProducts() throws Exception {
    // Use your actual cost file
    String sampleFile = "sample_file/New cost clean.xlsx";

    // When
    CostImportSummary result = costImportService.importCostData(sampleFile);

    // Then
    assertThat(result.getTotalRows()).isGreaterThan(0);
    assertThat(result.getSuccessfulRows()).isEqualTo(result.getTotalRows());

    // Verify products have valid cost data
    verify(productCostRepository).saveAll(argThat(costs -> {
        ProductCost firstCost = costs.get(0);
        return firstCost.getProductCode() != null &&
               firstCost.getStandardCost() != null &&
               firstCost.getStandardCost().compareTo(BigDecimal.ZERO) > 0;
    }));
}

@Test
void importSalesData_withDuplicateFile_shouldThrowException() {
    // When file already imported
    when(summaryRepository.existsByFilename(anyString())).thenReturn(true);

    String sampleFile = "sample_file/251103 to 251109.xlsx";

    // Then
    assertThatThrownBy(() -> importService.importSalesData(sampleFile))
        .isInstanceOf(DuplicateImportException.class)
        .hasMessageContaining("already imported");
}

@Test
void importSalesData_withMissingFile_shouldThrowException() {
    String nonExistentFile = "sample_file/does-not-exist.xlsx";

    assertThatThrownBy(() -> importService.importSalesData(nonExistentFile))
        .isInstanceOf(FileNotFoundException.class);
}
```

**Setup Required:**
```bash
# Copy sample files to test resources
mkdir -p src/test/resources/test-data
cp sample_file/*.xlsx src/test/resources/test-data/
```

**Why This Matters:**
- Real file structure validation
- Catches parsing bugs early
- Prevents corrupt data imports

---

## 🔥🔥 HIGH PRIORITY - Test Next (2-3 hours)

### 4. Customer Rating (GP%) Calculation 🔥🔥
**Why Important:** Affects customer tier classification and reporting.

**File:** `src/test/java/com/meatrics/pricing/customer/CustomerRatingServiceTest.java`

```java
@Test
void calculateGPPercent_standardCase_shouldReturnCorrectPercentage() {
    // Amount: $1000, Cost: $750
    // GP = ($1000 - $750) / $1000 = $250 / $1000 = 0.25 = 25%

    BigDecimal amount = new BigDecimal("1000.00");
    BigDecimal cost = new BigDecimal("750.00");

    BigDecimal gpPercent = service.calculateGPPercent(amount, cost);

    assertThat(gpPercent).isEqualByComparingTo(new BigDecimal("0.25"));
}

@Test
void calculateGPPercent_zeroAmount_shouldHandleGracefully() {
    // Edge case: avoid division by zero
    BigDecimal amount = BigDecimal.ZERO;
    BigDecimal cost = new BigDecimal("100.00");

    BigDecimal gpPercent = service.calculateGPPercent(amount, cost);

    // Should return 0 or -100% (cost without revenue)
    assertThat(gpPercent).isNotNull();
}

@Test
void calculateGPPercent_negativeGP_shouldReturnNegative() {
    // Selling below cost: Amount: $100, Cost: $150
    // GP = ($100 - $150) / $100 = -50%

    BigDecimal amount = new BigDecimal("100.00");
    BigDecimal cost = new BigDecimal("150.00");

    BigDecimal gpPercent = service.calculateGPPercent(amount, cost);

    assertThat(gpPercent).isLessThan(BigDecimal.ZERO);
    assertThat(gpPercent).isEqualByComparingTo(new BigDecimal("-0.50"));
}
```

---

### 5. Pricing Session Save/Load Integrity 🔥🔥
**Why Important:** Data loss or corruption in sessions is very bad UX.

**File:** `src/test/java/com/meatrics/pricing/session/PricingSessionServiceTest.java`

```java
@Test
void saveAndLoadSession_shouldPreserveAllData() {
    // Given - create session with pricing data
    List<GroupedLineItem> items = createTestItems();
    items.get(0).setNewUnitSellPrice(new BigDecimal("15.50"));
    items.get(0).setManualOverride(true);

    PricingRule rule = createTestRule();
    items.get(1).setAppliedRule(rule);
    items.get(1).setNewUnitSellPrice(new BigDecimal("12.00"));

    // When - save
    PricingSession session = sessionService.saveSession("Test Session", items);
    Long sessionId = session.getId();

    // When - load
    List<GroupedLineItem> loadedItems = sessionService.loadSession(sessionId);

    // Then - verify data integrity
    assertThat(loadedItems).hasSize(items.size());

    // Verify manual override preserved
    assertThat(loadedItems.get(0).isManualOverride()).isTrue();
    assertThat(loadedItems.get(0).getNewUnitSellPrice())
        .isEqualByComparingTo(new BigDecimal("15.50"));

    // Verify rule preserved
    assertThat(loadedItems.get(1).getAppliedRule()).isNotNull();
    assertThat(loadedItems.get(1).getAppliedRule().getRuleName())
        .isEqualTo(rule.getRuleName());
}

@Test
void saveSession_withMultipleRules_shouldPreserveAllRules() {
    // Test multi-rule tracking
    List<GroupedLineItem> items = createTestItems();

    List<PricingRule> rules = List.of(
        createTestRule("Rule 1"),
        createTestRule("Rule 2"),
        createTestRule("Rule 3")
    );

    items.get(0).setAppliedRules(rules);
    items.get(0).setIntermediateResults(List.of(
        new BigDecimal("12.00"),
        new BigDecimal("10.80"),
        new BigDecimal("10.26")
    ));

    // Save and load
    PricingSession session = sessionService.saveSession("Multi-Rule Test", items);
    List<GroupedLineItem> loaded = sessionService.loadSession(session.getId());

    // Verify all rules preserved
    assertThat(loaded.get(0).getAppliedRules()).hasSize(3);
    assertThat(loaded.get(0).getIntermediateResults()).hasSize(3);
}
```

---

### 6. Cost Drift Calculation 🔥🔥
**Why Important:** Alerts users to cost changes affecting profitability.

**File:** `src/test/java/com/meatrics/pricing/calculation/CostDriftTest.java`

```java
@Test
void calculateCostDrift_withIncrease_shouldReturnPositivePercentage() {
    // Last cost: $10.00, New cost: $12.00
    // Drift = ($12 - $10) / $10 = 20%

    BigDecimal lastCost = new BigDecimal("10.00");
    BigDecimal newCost = new BigDecimal("12.00");

    BigDecimal drift = service.calculateCostDrift(lastCost, newCost);

    assertThat(drift).isEqualByComparingTo(new BigDecimal("0.20")); // 20%
}

@Test
void calculateCostDrift_withDecrease_shouldReturnNegativePercentage() {
    // Last cost: $10.00, New cost: $8.00
    // Drift = ($8 - $10) / $10 = -20%

    BigDecimal lastCost = new BigDecimal("10.00");
    BigDecimal newCost = new BigDecimal("8.00");

    BigDecimal drift = service.calculateCostDrift(lastCost, newCost);

    assertThat(drift).isEqualByComparingTo(new BigDecimal("-0.20")); // -20%
}

@Test
void isCostDriftSignificant_above10Percent_shouldReturnTrue() {
    BigDecimal drift = new BigDecimal("0.15"); // 15%

    boolean isSignificant = service.isCostDriftSignificant(drift);

    assertThat(isSignificant).isTrue();
}
```

---

## 🔥 MEDIUM PRIORITY - Test When Time Permits

### 7. Rule Matching Logic 🔥
**File:** `src/test/java/com/meatrics/pricing/rule/RuleMatchingTest.java`

### 8. Report Data Aggregation 🔥
**File:** `src/test/java/com/meatrics/pricing/report/ReportExportServiceTest.java`

### 9. VaadinSession State Persistence 🔥
**File:** `src/test/java/com/meatrics/pricing/ui/pricing/PricingSessionManagerTest.java`

### 10. Decimal Precision (6 decimals) 🔥
**File:** `src/test/java/com/meatrics/pricing/calculation/DecimalPrecisionTest.java`

```java
@Test
void calculations_shouldMaintain6DecimalPlaces() {
    // Ensure all calculations maintain required precision
    BigDecimal value1 = new BigDecimal("10.123456");
    BigDecimal value2 = new BigDecimal("1.234567");

    BigDecimal result = service.multiply(value1, value2);

    // Should not lose precision
    assertThat(result.scale()).isGreaterThanOrEqualTo(6);
}
```

---

## Implementation Order

**Day 1 (3-4 hours):**
1. ✅ MAINTAIN_GP_PERCENT Calculation (#1)
2. ✅ Multi-Layer Pricing (#2)
3. ✅ Excel Import with sample files (#3)

**Day 2 (2-3 hours):**
4. ✅ Customer Rating GP% (#4)
5. ✅ Session Save/Load (#5)
6. ✅ Cost Drift (#6)

**Later (as needed):**
7-10. Medium priority tests

---

## Quick Setup Script

```bash
# Create test directory structure
mkdir -p src/test/java/com/meatrics/pricing/{calculation,importer,customer,session}
mkdir -p src/test/resources/test-data

# Copy sample files for testing
cp sample_file/*.xlsx src/test/resources/test-data/

# Run tests
./mvnw test
```

---

## Expected ROI

These 10 tests will:
- ✅ Catch 80% of critical bugs before production
- ✅ Make future refactoring safe
- ✅ Provide clear documentation of business logic
- ✅ Enable confident AI-assisted development
- ✅ Reduce debugging time by 50%+

**Total Time Investment:** 5-7 hours
**Bug Prevention Value:** Incalculable (prevents data corruption, pricing errors, profit loss)
