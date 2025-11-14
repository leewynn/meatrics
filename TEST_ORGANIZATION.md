# Test Organization Guide

## 📋 Test Structure Overview

We organize tests into **3 clear categories** using tags and naming conventions:

```
src/test/java/com/meatrics/pricing/
├── calculation/                         ← UNIT tests (technical)
│   ├── PricingFormulaTest.java         @Tag("unit") @Tag("fast")
│   ├── MaintainGPPercentTest.java      @Tag("unit") @Tag("fast")
│   └── PricingResultTest.java          @Tag("unit") @Tag("fast")
│
├── business/scenarios/                  ← BUSINESS tests (real scenarios)
│   ├── RibeyePricingScenarios.java     @Tag("business") @Tag("ribeye")
│   ├── CustomerDiscountScenarios.java  @Tag("business") @Tag("customer")
│   └── CostIncreaseScenarios.java      @Tag("business") @Tag("pricing")
│
└── integration/                         ← INTEGRATION tests (full stack)
    ├── ExcelImportIntegrationTest.java @Tag("integration") @Tag("slow")
    └── SessionPersistenceTest.java     @Tag("integration") @Tag("slow")
```

---

## 🏷️ Test Categories

### 1️⃣ UNIT Tests (Technical/Formula)

**Purpose:** Verify formulas and methods work correctly
**Tags:** `@Tag("unit")` `@Tag("fast")`
**Naming:** `*Test.java`
**Speed:** ⚡ Fast (milliseconds)
**Dependencies:** None - pure logic

**Example:**
```java
@Tag("unit")
@Tag("fast")
class PricingFormulaTest {

    @Test
    @DisplayName("COST_PLUS_PERCENT: $10 × 1.20 = $12")
    void costPlusPercent_shouldMultiply() {
        BigDecimal result = new BigDecimal("10").multiply(new BigDecimal("1.20"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("12.00"));
    }
}
```

**What they test:**
- ✅ Math formulas are correct
- ✅ Edge cases (zero, negative, null)
- ✅ Precision and rounding
- ✅ Data structures work

**Who cares:**
- Developers
- QA engineers

---

### 2️⃣ BUSINESS Tests (Scenarios)

**Purpose:** Verify business rules and real-world scenarios
**Tags:** `@Tag("business")` + domain tag (e.g., `@Tag("ribeye")`)
**Naming:** `*Scenarios.java`
**Speed:** 🏃 Medium (seconds)
**Dependencies:** May use real data/repositories

**Example:**
```java
@Tag("business")
@Tag("ribeye")
class RibeyePricingScenarios {

    @Test
    @DisplayName("BR-001: Premium ribeye must maintain minimum 30% GP")
    void premiumRibeye_shouldMaintainMinimum30PercentGP() {
        // GIVEN: Premium ribeye cost $18.50/lb
        BigDecimal cost = new BigDecimal("18.50");

        // WHEN: Price is calculated with 30% GP requirement
        BigDecimal price = calculateWithMinimumGP(cost, new BigDecimal("0.30"));

        // THEN: Price is at least $26.43
        assertThat(price).isGreaterThanOrEqualTo(new BigDecimal("26.43"));
    }
}
```

**What they test:**
- ✅ Business rules are enforced
- ✅ Real customer scenarios
- ✅ Product-specific pricing logic
- ✅ Discount stacking rules
- ✅ Profit margin requirements

**Who cares:**
- Business owners
- Product managers
- Customers (indirectly)

---

### 3️⃣ INTEGRATION Tests (Full Stack)

**Purpose:** Test complete workflows end-to-end
**Tags:** `@Tag("integration")` `@Tag("slow")`
**Naming:** `*IntegrationTest.java`
**Speed:** 🐌 Slow (minutes)
**Dependencies:** Database, files, services

**Example:**
```java
@Tag("integration")
@Tag("slow")
class ExcelImportIntegrationTest {

    @Test
    @DisplayName("Import Nov 3-9 sales file - all ribeye prices correct")
    void importNovemberSales_shouldPriceAllRibeyeCorrectly() {
        // GIVEN: Real sample file
        String file = "src/test/resources/test-data/251103 to 251109.xlsx";

        // WHEN: File is imported
        ImportSummary result = importService.importSalesData(file);

        // THEN: All ribeye items imported correctly
        List<ImportedLineItem> ribeye = lineItemRepository
            .findByProductCodeStartingWith("RIBEYE");
        assertThat(ribeye).hasSize(47);

        // AND: All maintain minimum GP
        ribeye.forEach(item -> {
            assertThat(item.getGrossProfit())
                .isGreaterThanOrEqualTo(item.getAmount().multiply(new BigDecimal("0.30")));
        });
    }
}
```

**What they test:**
- ✅ Complete user workflows
- ✅ Database operations
- ✅ File import/export
- ✅ Session persistence
- ✅ Multi-component interactions

**Who cares:**
- Everyone (these catch the most bugs)

---

## 🎯 Running Different Test Types

### Run only UNIT tests (fast - seconds)
```bash
./mvnw test -Dgroups="unit"

# Output:
# Tests run: 27, Time: 0.5s ⚡
```

### Run only BUSINESS tests (medium - ~1 minute)
```bash
./mvnw test -Dgroups="business"

# Output:
# Tests run: 7, Time: 15s 🏃
```

### Run only INTEGRATION tests (slow - several minutes)
```bash
./mvnw test -Dgroups="integration"

# Output:
# Tests run: 5, Time: 3m 🐌
```

### Run UNIT + BUSINESS (skip slow tests)
```bash
./mvnw test -Dgroups="unit | business"

# Output:
# Tests run: 34, Time: 16s
# Perfect for development!
```

### Run by domain
```bash
# Only ribeye-related tests
./mvnw test -Dgroups="ribeye"

# Only customer-related tests
./mvnw test -Dgroups="customer"
```

### Run everything (CI/CD)
```bash
./mvnw test

# Output:
# Tests run: 39, Time: 4m
```

---

## 📊 Current Test Inventory

| Category | Count | Files | Speed | Purpose |
|----------|-------|-------|-------|---------|
| **Unit** | 27 | 3 | ⚡ 0.5s | Verify formulas work |
| **Business** | 7 | 1 | 🏃 15s | Verify business rules |
| **Integration** | 0 | 0 | 🐌 - | Not yet written |
| **Total** | **34** | **4** | **16s** | - |

---

## 🎨 Naming Conventions

### Test Class Names

| Type | Pattern | Example |
|------|---------|---------|
| Unit | `*Test.java` | `PricingFormulaTest` |
| Business | `*Scenarios.java` | `RibeyePricingScenarios` |
| Integration | `*IntegrationTest.java` | `ExcelImportIntegrationTest` |

### Test Method Names

| Type | Pattern | Example |
|------|---------|---------|
| Unit | Technical description | `costPlusPercent_shouldMultiply()` |
| Business | Business rule ID + description | `BR001_premiumRibeye_shouldMaintainMinimum30PercentGP()` |
| Integration | User story description | `importNovemberSales_shouldPriceAllRibeyeCorrectly()` |

### Display Names

```java
// Unit Test
@DisplayName("COST_PLUS_PERCENT: $10 × 1.20 = $12")

// Business Test
@DisplayName("BR-001: Premium ribeye must maintain minimum 30% GP")

// Integration Test
@DisplayName("Import Nov 3-9 sales file and verify all ribeye prices are correct")
```

---

## 📝 Business Rule Numbering

Use a consistent numbering scheme for business tests:

```
BR-001: Premium ribeye must maintain minimum 30% GP
BR-002: Cost increases should maintain customer GP%
BR-003: Loyalty customers get 5% discount
BR-004: Volume + loyalty discounts stack
BR-005: Price never goes below cost (except clearance)
BR-006: Premium grade gets $2 fee
BR-007: Cost drift >10% requires manager review
```

Benefits:
- ✅ Easy to reference in discussions
- ✅ Maps to requirements docs
- ✅ Traceable to bugs/tickets
- ✅ Business owners understand them

---

## 🔍 Test Discovery in IDEs

### IntelliJ IDEA
Right-click package → "Run Tests" shows:
```
└── pricing
    ├── 📁 calculation (27 unit tests) ⚡
    ├── 📁 business/scenarios (7 business tests) 🏃
    └── 📁 integration (0 tests)
```

### Run with filters:
- Run → Edit Configurations → Test kind: Tags
- Enter: `unit` or `business` or `integration`

---

## ✅ Best Practices

### 1. Start with Business Tests
```java
// ❌ Don't start here:
@Test
void calculate_shouldReturnNumber() { ... }

// ✅ Start here:
@Test
@DisplayName("BR-001: Premium ribeye must maintain minimum 30% GP")
void premiumRibeye_shouldMaintainMinimumGP() { ... }
```

### 2. Then add Unit Tests to support it
```java
// ✅ Supporting unit test:
@Test
@DisplayName("MAINTAIN_GP_PERCENT: 30% GP on $18.50 = $26.43")
void maintainGP_with30Percent() { ... }
```

### 3. Tag Everything
```java
// ❌ Missing tags:
class MyTest { }

// ✅ Tagged:
@Tag("unit")
@Tag("fast")
class MyTest { }
```

### 4. Use Clear Display Names
```java
// ❌ Unclear:
@Test
void test1() { }

// ✅ Clear:
@Test
@DisplayName("BR-001: Premium ribeye must maintain minimum 30% GP")
void premiumRibeye_shouldMaintainMinimumGP() { }
```

---

## 🚀 Development Workflow

### During Development (TDD)
```bash
# Run only fast unit tests constantly
./mvnw test -Dgroups="unit"

# Write business test when feature is working
./mvnw test -Dgroups="business"
```

### Before Commit
```bash
# Run unit + business tests
./mvnw test -Dgroups="unit | business"
```

### CI/CD Pipeline
```bash
# Run everything
./mvnw test

# Generate coverage report
./mvnw jacoco:report
```

---

## 📈 Example: Adding a New Feature

**Feature:** "Add 15% promotional discount for Black Friday"

### Step 1: Write Business Test First
```java
@Tag("business")
@Tag("promotion")
class BlackFridayPromotionScenarios {

    @Test
    @DisplayName("BR-015: Black Friday 15% off all ribeye products")
    void blackFriday_allRibeye_shouldGet15PercentOff() {
        // GIVEN: Black Friday promotion active
        // AND: Customer ordering ribeye
        // WHEN: Price is calculated
        // THEN: 15% discount applied
        // AND: Still maintains minimum 20% GP
    }
}
```

### Step 2: Run Test (it fails)
```bash
./mvnw test -Dtest=BlackFridayPromotionScenarios
# ❌ FAILS - feature doesn't exist yet
```

### Step 3: Implement Feature
```java
// Add promotional discount logic to PriceCalculationService
```

### Step 4: Run Test Again
```bash
./mvnw test -Dtest=BlackFridayPromotionScenarios
# ✅ PASSES - feature works!
```

### Step 5: Add Supporting Unit Tests
```java
@Tag("unit")
class PromotionalDiscountTest {

    @Test
    @DisplayName("15% discount: $100 → $85")
    void discount15Percent_shouldMultiplyBy085() {
        // Technical test of the formula
    }
}
```

### Step 6: Run All Tests Before Commit
```bash
./mvnw test -Dgroups="unit | business"
# ✅ All 41 tests pass
```

---

## 🎓 Summary

**Clear Delineation:**
1. ✅ **File location** separates types (`calculation/` vs `business/scenarios/`)
2. ✅ **Naming** makes intent clear (`*Test` vs `*Scenarios`)
3. ✅ **Tags** enable filtering (`@Tag("unit")` vs `@Tag("business")`)
4. ✅ **Display names** document purpose
5. ✅ **Speed** differs (fast vs slow)

**Benefits:**
- ✅ Run only what you need
- ✅ Business tests readable by non-developers
- ✅ Unit tests verify technical correctness
- ✅ Clear separation of concerns
- ✅ Easy to maintain and grow

**You now have both:**
- **Unit tests** (27) - Verify formulas work
- **Business tests** (7) - Verify business rules work

Run them separately or together as needed! 🎉
