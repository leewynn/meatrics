# GP% Coloring Debug Guide

## Overview
This document explains the GP% coloring logic in PricingSessionsViewNew and how to debug edge cases.

## Problem Description
Two edge cases were identified where GP% coloring was not working as expected:

### Issue 1 - Motel 707 (SCRM200)
- **Expected**: RED (declining GP%)
- **Observed**: GREEN
- **Data**:
  - Historical: GP$ = $0.74, Amount = $3.12, GP% = 23.7%
  - New: GP$ = $0.49, Amount = $2.22, GP% = 21.8%
  - Difference: 1.9 percentage points (should show RED)

### Issue 2 - The Crib Hut (LFOCH)
- **Expected**: NO COLOR (GP% maintained at same negative value)
- **Observed**: RED
- **Data**:
  - Historical: GP$ = -$88.41, Amount = $1237.77, GP% = -7.1%
  - New: GP$ = -$102.67, Amount = $1437.41, GP% = -7.1%
  - Difference: ~0 percentage points (should show no color)

## Changes Made

### 1. Enhanced GP% Calculation Precision
**File**: `/mnt/d/dev/meatrics/src/main/java/com/meatrics/pricing/ui/PricingSessionsViewNew.java`

**Method**: `calculateGPPercent(BigDecimal grossProfit, BigDecimal amount)`

**Changes**:
- Increased precision from 4 to 6 decimal places before multiplying by 100
- Added explicit setScale(4, HALF_UP) after multiplication
- This prevents rounding errors that could cause false positives or negatives

**Formula**:
```java
return grossProfit.divide(amount, 6, RoundingMode.HALF_UP)
        .multiply(new BigDecimal("100"))
        .setScale(4, RoundingMode.HALF_UP);
```

### 2. New Helper Method: determineGPPercentColor
**Method**: `determineGPPercentColor(BigDecimal newGPPercent, BigDecimal lastGPPercent, BigDecimal tolerance)`

**Logic**:
1. Return `null` if either value is null (no color)
2. Calculate difference: `newGPPercent - lastGPPercent`
3. Calculate absolute difference
4. If `abs(difference) <= tolerance`: return `null` (no color - within tolerance)
5. If `difference > 0`: return `"green"` (GP% increased)
6. If `difference < 0`: return `"red"` (GP% decreased)

**Tolerance**: 0.1 percentage points (e.g., 23.7% vs 23.8% has difference of 0.1)

### 3. Added Debug Logging
Both GP$ and GP% column renderers now log detailed information for products SCRM200 and LFOCH.

**Log Fields**:
- `Product`: Product code
- `Customer`: Customer name
- `NewGP$`: New gross profit dollar amount
- `NewAmount`: New total amount
- `NewGP%_calc`: Calculated GP% with full precision (e.g., 23.7179)
- `NewGP%_display`: Displayed GP% rounded to 1 decimal (e.g., 23.7%)
- `LastGP$`: Historical gross profit dollar amount
- `LastAmount`: Historical total amount
- `LastGP%_calc`: Calculated historical GP% with full precision
- `LastGP%_display`: Displayed historical GP% rounded to 1 decimal
- `Difference`: Signed difference (new - last) in percentage points
- `AbsDiff`: Absolute difference
- `Tolerance`: Tolerance value (0.1)
- `Color`: Resulting color decision (green/red/null)
- `Logic`: Boolean comparisons used in decision

## How to Debug

### Step 1: Reproduce the Issue
1. Start the application
2. Navigate to "Pricing Sessions" view
3. Load data that includes the problematic products (SCRM200 and/or LFOCH)
4. Observe the GP$ and GP% coloring

### Step 2: Check the Logs
Look for log entries with prefix "GP$ Coloring Debug" or "GP% Coloring Debug"

**Example log entry**:
```
GP% Coloring Debug - Product: SCRM200, Customer: Motel 707,
NewGP$: 0.49, NewAmount: 2.22, NewGP%_calc: 22.0721, NewGP%_display: 22.1%,
LastGP$: 0.74, LastAmount: 3.12, LastGP%_calc: 23.7179, LastGP%_display: 23.7%,
Difference: -1.6458, AbsDiff: 1.6458, Tolerance: 0.1,
Color: red, Logic: new>last=false, new<last=true, diff>tol=true
```

### Step 3: Analyze the Values
Check the following:

#### A. Are the raw GP$ and Amount values correct?
- Compare `NewGP$` and `LastGP$` with what you see in the grid
- Compare `NewAmount` and `LastAmount` with what you see in the grid

#### B. Are the calculated GP% values correct?
- Manually verify: `GP%_calc = (GP$ / Amount) × 100`
- Example: `0.49 / 2.22 × 100 = 22.072072...` → rounds to `22.0721` with scale 4

#### C. Are the displayed GP% values correct?
- Verify: `GP%_display` should be `GP%_calc` rounded to 1 decimal place
- Example: `22.0721` → `22.1%`

#### D. Is the difference calculation correct?
- Verify: `Difference = NewGP%_calc - LastGP%_calc`
- Example: `22.0721 - 23.7179 = -1.6458`

#### E. Is the tolerance logic correct?
- If `AbsDiff > 0.1`: color should be applied
- If `AbsDiff <= 0.1`: no color (null)

#### F. Is the color direction correct?
- If `Difference > 0`: should be green (GP% improved)
- If `Difference < 0`: should be red (GP% declined)

### Step 4: Common Issues to Check

#### Issue: Displayed values don't match calculated values
**Symptom**: The grid shows one GP% but the log shows a different value

**Possible Causes**:
1. Data is changing between render cycles
2. Wrong field is being used (e.g., using totalAmount instead of lastAmount)
3. NULL values are being handled incorrectly

**Solution**: Check the GroupedLineItem getters to ensure they return the expected values

#### Issue: Color is applied but shouldn't be (or vice versa)
**Symptom**: Tolerance check is not working

**Possible Causes**:
1. Tolerance value is wrong scale (should be 0.1 for 0.1 percentage points)
2. Precision loss in BigDecimal operations
3. Wrong comparison operator

**Solution**: Verify the tolerance comparison logic in `determineGPPercentColor`

#### Issue: Color direction is reversed (green when should be red)
**Symptom**: `Color: green` but `Difference` is negative

**Possible Causes**:
1. Values are swapped (new and last are reversed)
2. Sign is inverted in the comparison

**Solution**: Check that `newGPPercent` and `lastGPPercent` are calculated from the correct fields

#### Issue: Negative GP% values behaving incorrectly
**Symptom**: Two negative GP% values with same magnitude show different colors

**Possible Causes**:
1. Absolute value comparison when should be signed
2. Special handling needed for negative values

**Example**:
- Last: -7.1% (calculated as -7.1424)
- New: -7.1% (calculated as -7.1424)
- Difference: -7.1424 - (-7.1424) = 0.0000
- AbsDiff: 0.0000
- Should show NO COLOR (correct)

## Expected Behavior

### Tolerance Implementation
The tolerance of 0.1 percentage points means:

- **23.7%** vs **23.8%**: Difference = 0.1, at tolerance boundary → NO COLOR
- **23.7%** vs **23.9%**: Difference = 0.2, exceeds tolerance → GREEN
- **23.7%** vs **23.5%**: Difference = -0.2, exceeds tolerance → RED
- **23.7%** vs **23.7%**: Difference = 0.0, within tolerance → NO COLOR

### Negative GP% Handling
Negative values should work the same as positive:

- **-7.0%** vs **-7.2%**: Difference = -0.2, GP% got worse (more negative) → RED
- **-7.2%** vs **-7.0%**: Difference = +0.2, GP% improved (less negative) → GREEN
- **-7.1%** vs **-7.1%**: Difference = 0.0, within tolerance → NO COLOR

## Testing Recommendations

1. **Test with tolerance edge cases**:
   - Find items with exactly 0.1 percentage point difference
   - Verify no color is applied

2. **Test with negative GP%**:
   - Find items with negative historical and new GP%
   - Verify color logic works correctly (improving = less negative = green)

3. **Test with zero/null values**:
   - Items with zero amount should return null from calculateGPPercent
   - Should not throw exceptions

4. **Test with large differences**:
   - Items with 5%, 10%, 20% differences
   - Should always show appropriate color

## Files Modified
- `/mnt/d/dev/meatrics/src/main/java/com/meatrics/pricing/ui/PricingSessionsViewNew.java`
  - Lines 493-501: Enhanced `calculateGPPercent` precision
  - Lines 515-539: New `determineGPPercentColor` helper method
  - Lines 320-354: Refactored GP$ column renderer with logging
  - Lines 362-401: Refactored GP% column renderer with logging

## Next Steps
1. Run the application
2. Load data with SCRM200 and LFOCH products
3. Review the debug logs
4. Based on log output, identify which of the following is the root cause:
   - Data issue (wrong values in database/objects)
   - Calculation issue (precision/rounding problem)
   - Logic issue (comparison or tolerance bug)
5. Once debug logs are reviewed, can remove the logging code for production

## Contact
If issues persist after reviewing debug logs, provide:
1. The complete log output for the affected products
2. Screenshots showing the grid display
3. Database query results showing the raw data for the affected items
