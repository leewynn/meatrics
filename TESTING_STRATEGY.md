# Unit Testing Strategy for Meatrics Pricing Application

## Current State

### ✅ Already Configured
- **spring-boot-starter-test** dependency (includes JUnit 5, Mockito, AssertJ)
- Spring Boot 3.5.7 (excellent testing support)
- Test directory structure can be created easily
- Sample data files available in `sample_file/`

### 📊 Scope Analysis
- **16 Service/Repository classes** to test
- Key domains: pricing calculation, import, sessions, rules, customers, products

## Difficulty Assessment: **MEDIUM** ⭐⭐⭐

### Easy to Test (Low Complexity)
1. **Calculation Logic** - Pure business logic, no external dependencies
   - `PriceCalculationService` - pricing rule calculations
   - `CustomerRatingService` - GP% calculations
   - Difficulty: ⭐ (EASY)

2. **DTOs and Models** - Simple POJO validation
   - `PricingResult`, `PricePreview`, `CustomerRatingReportDTO`, etc.
   - Difficulty: ⭐ (EASY)

3. **Utilities** - Self-contained helper methods
   - `ExcelParsingUtil` - can use sample Excel files
   - Difficulty: ⭐⭐ (EASY-MEDIUM)

### Medium Complexity
4. **Repositories** - Need database/jOOQ mocking
   - All `*Repository` classes use jOOQ DSL
   - Can use Testcontainers for real DB or mock DSLContext
   - Difficulty: ⭐⭐⭐ (MEDIUM)

5. **Services with Business Logic** - Need repository mocking
   - `PricingRuleService`, `PricingSessionService`, etc.
   - Difficulty: ⭐⭐⭐ (MEDIUM)

### Higher Complexity
6. **Import Services** - File I/O, transaction handling
   - `PricingImportService`, `ProductCostImportService`
   - Can use actual sample Excel files for integration tests
   - Difficulty: ⭐⭐⭐⭐ (MEDIUM-HIGH)

7. **UI Components** - Vaadin testing
   - Would need Vaadin TestBench (separate license/setup)
   - Generally skip for unit tests, focus on service layer
   - Difficulty: ⭐⭐⭐⭐⭐ (HIGH)

## Recommended Testing Strategy

### Phase 1: Core Business Logic (Start Here) ⭐
**Effort: 1-2 days**

Focus on pure calculation and business logic - highest ROI:

```
src/test/java/com/meatrics/pricing/
├── calculation/
│   ├── PriceCalculationServiceTest.java ⭐ HIGH PRIORITY
│   ├── PricingResultTest.java
│   └── PricePreviewTest.java
├── customer/
│   └── CustomerRatingServiceTest.java ⭐ HIGH PRIORITY
└── rule/
    └── PricingRuleServiceTest.java ⭐ HIGH PRIORITY
```

### Phase 2: Repository Layer (Medium Effort) ⭐⭐
**Effort: 2-3 days**

Use Testcontainers with PostgreSQL for integration tests:

```
src/test/java/com/meatrics/pricing/
├── product/
│   ├── ProductCostRepositoryTest.java
│   └── GroupedLineItemRepositoryTest.java
├── customer/
│   └── CustomerRepositoryTest.java
└── session/
    └── PricingSessionRepositoryTest.java
```

### Phase 3: Import/Export (Use Sample Files) ⭐⭐⭐
**Effort: 2-3 days**

Test with actual sample Excel files:

```
src/test/java/com/meatrics/pricing/
├── importer/
│   ├── PricingImportServiceTest.java ⭐ Use sample files
│   └── ProductCostImportServiceTest.java ⭐ Use sample files
└── report/
    └── ReportExportServiceTest.java
```

### Phase 4: Session Management ⭐⭐
**Effort: 1-2 days**

```
src/test/java/com/meatrics/pricing/
└── session/
    └── PricingSessionServiceTest.java
```

## Sample File Usage

Your sample files are perfect for testing:

```
sample_file/
├── 251103 to 251109.xlsx    ← Sales/invoice data
└── New cost clean.xlsx       ← Product cost data
```

### Test Resources Structure
```
src/test/resources/
├── test-data/
│   ├── sales-sample.xlsx        ← Copy from sample_file
│   ├── cost-sample.xlsx         ← Copy from sample_file
│   ├── sales-invalid.xlsx       ← Create for error testing
│   └── cost-malformed.xlsx      ← Create for error testing
└── application-test.properties  ← Test database config
```

## Additional Dependencies Needed

Add to `pom.xml`:

```xml
<!-- Already have spring-boot-starter-test ✓ -->

<!-- For database integration tests -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>

<!-- For better assertions -->
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
    <!-- Version from spring-boot-starter-test -->
</dependency>
```

## Key Testing Patterns

### 1. Service Layer Testing (Easy)
```java
@ExtendWith(MockitoExtension.class)
class PriceCalculationServiceTest {

    @Mock
    private ProductCostRepository productCostRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private PriceCalculationService service;

    @Test
    void calculatePrice_withCostPlusPercent_shouldApplyMarkup() {
        // Given
        PricingRule rule = new PricingRule();
        rule.setPricingMethod("COST_PLUS_PERCENT");
        rule.setPricingValue(new BigDecimal("1.20")); // 20% markup

        BigDecimal cost = new BigDecimal("10.00");

        // When
        BigDecimal result = service.calculatePrice(cost, rule);

        // Then
        assertThat(result).isEqualTo(new BigDecimal("12.00"));
    }
}
```

### 2. Repository Testing with Testcontainers (Medium)
```java
@Testcontainers
@SpringBootTest
class ProductCostRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private ProductCostRepository repository;

    @Test
    void findByProductCode_shouldReturnCost() {
        // Given
        ProductCost cost = new ProductCost();
        cost.setProductCode("TEST001");
        cost.setStandardCost(new BigDecimal("15.50"));
        repository.save(cost);

        // When
        Optional<ProductCost> result = repository.findByProductCode("TEST001");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getStandardCost())
            .isEqualByComparingTo(new BigDecimal("15.50"));
    }
}
```

### 3. Import Testing with Sample Files (Medium)
```java
@ExtendWith(MockitoExtension.class)
class PricingImportServiceTest {

    @Mock
    private ImportedLineItemRepository lineItemRepository;

    @Mock
    private ImportSummaryRepository summaryRepository;

    @InjectMocks
    private PricingImportService importService;

    @Test
    void importSalesData_withValidFile_shouldImportAllRows() throws Exception {
        // Given
        String testFile = "src/test/resources/test-data/sales-sample.xlsx";

        when(summaryRepository.existsByFilename(anyString())).thenReturn(false);
        when(summaryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        ImportSummary result = importService.importSalesData(testFile);

        // Then
        assertThat(result.getTotalRows()).isGreaterThan(0);
        assertThat(result.getSuccessfulRows()).isEqualTo(result.getTotalRows());
        verify(lineItemRepository, atLeastOnce()).saveAll(anyList());
    }

    @Test
    void importSalesData_withDuplicateFile_shouldThrowException() {
        // Given
        String testFile = "src/test/resources/test-data/sales-sample.xlsx";
        when(summaryRepository.existsByFilename(anyString())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> importService.importSalesData(testFile))
            .isInstanceOf(DuplicateImportException.class)
            .hasMessageContaining("already imported");
    }
}
```

## Test Coverage Goals

### Minimum Viable Testing (MVP)
- **Target: 60-70% coverage**
- Focus: Service layer business logic
- Effort: ~5-7 days
- Tests: ~30-40 test classes

### Comprehensive Testing
- **Target: 80%+ coverage**
- Includes: Repositories, edge cases, integration tests
- Effort: ~10-15 days
- Tests: ~60-80 test classes

### Critical Path Testing (Recommended Start)
- **Target: Critical business logic only**
- Focus areas:
  1. Price calculation logic (all rule types)
  2. GP% calculation and validation
  3. Excel import/export with sample files
  4. Session save/load integrity
- Effort: ~3-4 days
- Tests: ~15-20 test classes
- **Best ROI** for catching bugs

## Challenges & Solutions

### Challenge 1: jOOQ DSLContext Mocking
**Problem:** jOOQ fluent API is hard to mock
**Solution:**
- Use Testcontainers for integration tests (real DB)
- For unit tests, mock at repository boundary, not DSLContext

### Challenge 2: Excel File Parsing
**Problem:** POI library complexity
**Solution:**
- Use actual sample files from `sample_file/` directory
- Create minimal test files for edge cases
- Test `ExcelParsingUtil` separately

### Challenge 3: VaadinSession in Session Manager
**Problem:** `VaadinSession.getCurrent()` returns null in tests
**Solution:**
- Extract session access to interface
- Mock the session access layer
- Or test service logic separately from session persistence

### Challenge 4: Transaction Management
**Problem:** Multi-step operations with rollback
**Solution:**
- Use `@Transactional` in tests
- Testcontainers provides real transaction support

## Quick Start: Your First Test

Here's a simple test you can add right now (5 minutes):

```java
package com.meatrics.pricing.calculation;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.Assertj.assertThat;

class PricingResultTest {

    @Test
    void constructor_withSingleRule_shouldStorePriceAndRule() {
        // Given
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal price = new BigDecimal("12.00");
        PricingRule rule = new PricingRule();
        rule.setRuleName("Test Rule");

        // When
        PricingResult result = new PricingResult(
            cost, price, rule, "Test calculation"
        );

        // Then
        assertThat(result.getCost()).isEqualByComparingTo(cost);
        assertThat(result.getCalculatedPrice()).isEqualByComparingTo(price);
        assertThat(result.getAppliedRule()).isEqualTo(rule);
        assertThat(result.getAppliedRules()).hasSize(1);
        assertThat(result.isMultiRule()).isFalse();
    }
}
```

Save as: `src/test/java/com/meatrics/pricing/calculation/PricingResultTest.java`

Run: `./mvnw test`

## Conclusion

**Overall Difficulty: MEDIUM** ⭐⭐⭐ out of 5

### Why It's Achievable:
✅ Dependencies already configured
✅ Sample data files available
✅ Clean service layer separation
✅ Spring Boot test support excellent
✅ Business logic is testable (not too coupled)

### Best Approach:
1. **Start small** - Test 1-2 calculation services first (1 day)
2. **Add file tests** - Use your sample Excel files (1 day)
3. **Expand gradually** - Add repository tests with Testcontainers (2-3 days)
4. **Iterate** - Add tests as bugs are found

### Estimated Time Investment:
- **Minimal viable testing**: 3-5 days
- **Comprehensive testing**: 10-15 days
- **Critical path only**: 2-3 days ⭐ **RECOMMENDED START**

The sample files in `sample_file/` are **perfect** for testing the import functionality - they represent real-world data structure and can catch parsing bugs early.

Would you like me to create a few example test classes to get you started?
