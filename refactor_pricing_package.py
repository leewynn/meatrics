#!/usr/bin/env python3
"""
Refactor pricing package from flat structure to Domain-Driven Design structure.

This script:
1. Creates domain sub-packages (customer, rule, session, calculation, importer, product, report, config)
2. Moves files to their appropriate domain packages
3. Updates package declarations in all moved files
4. Updates import statements across the entire codebase

USAGE:
    python3 refactor_pricing_package.py [--dry-run]

OPTIONS:
    --dry-run  Show what would be done without making changes
"""

import os
import re
import sys
from pathlib import Path
from collections import defaultdict

# Configuration
BASE_DIR = Path(__file__).parent / "src/main/java/com/meatrics"
PRICING_DIR = BASE_DIR / "pricing"

# Domain organization matching the DDD structure
DOMAINS = {
    "customer": [
        "Customer.java",
        "CustomerRepository.java",
        "CustomerRatingService.java",
        "CustomerRatingReportDTO.java"
    ],
    "rule": [
        "PricingRule.java",
        "PricingRuleRepository.java",
        "PricingRuleService.java",
        "RuleCategory.java",
        "RulePreviewResult.java"
    ],
    "session": [
        "PricingSession.java",
        "PricingSessionRepository.java",
        "PricingSessionService.java",
        "PricingSessionLineItem.java",
        "PricingSessionLineItemRepository.java"
    ],
    "calculation": [
        "PriceCalculationService.java",
        "PricePreview.java",
        "PricingResult.java"
    ],
    "importer": [
        "ImportSummary.java",
        "ImportSummaryRepository.java",
        "ImportedLineItem.java",
        "ImportedLineItemRepository.java",
        "PricingImportService.java",
        "ProductCostImportService.java",
        "CostImportSummary.java",
        "CostImportSummaryRepository.java",
        "DuplicateImportException.java"
    ],
    "product": [
        "ProductCost.java",
        "ProductCostRepository.java",
        "GroupedLineItem.java",
        "GroupedLineItemRepository.java"
    ],
    "report": [
        "ReportExportService.java",
        "CostReportDTO.java"
    ],
    "config": [
        "SystemDefaultPricingRuleInitializer.java"
    ]
}

# Build class-to-domain mapping for import updates
CLASS_TO_DOMAIN = {}
for domain, files in DOMAINS.items():
    for filename in files:
        classname = filename.replace(".java", "")
        CLASS_TO_DOMAIN[classname] = domain


def print_header(text):
    """Print a formatted header"""
    print("\n" + "=" * 80)
    print(text)
    print("=" * 80)


def create_domain_packages(dry_run=False):
    """Create all domain sub-packages"""
    print("\nSTEP 1: Creating domain packages...")
    for domain in DOMAINS.keys():
        domain_dir = PRICING_DIR / domain
        if dry_run:
            print(f"  [DRY-RUN] Would create: {domain}/")
        else:
            domain_dir.mkdir(exist_ok=True)
            print(f"  ✓ Created: {domain}/")


def move_files_and_update_packages(dry_run=False):
    """Move files to domain packages and update package declarations"""
    print("\nSTEP 2: Moving files and updating package declarations...")
    moved_count = 0

    for domain, files in DOMAINS.items():
        print(f"\n{domain.upper()} domain:")
        for filename in files:
            source = PRICING_DIR / filename
            dest = PRICING_DIR / domain / filename

            if not source.exists():
                print(f"  ⚠ WARNING: {filename} not found, skipping")
                continue

            if dry_run:
                print(f"  [DRY-RUN] Would move {filename} -> {domain}/")
                moved_count += 1
            else:
                # Read file
                with open(source, 'r', encoding='utf-8') as f:
                    content = f.read()

                # Update package declaration
                new_package = f"com.meatrics.pricing.{domain}"
                content = re.sub(
                    r'^package\s+com\.meatrics\.pricing\s*;',
                    f'package {new_package};',
                    content,
                    flags=re.MULTILINE
                )

                # Write to new location
                with open(dest, 'w', encoding='utf-8') as f:
                    f.write(content)

                # Remove old file
                source.unlink()

                print(f"  ✓ {filename}")
                moved_count += 1

    print(f"\nTotal files moved: {moved_count}")
    return moved_count


def update_imports(dry_run=False):
    """Update imports across all Java files"""
    print("\nSTEP 3: Updating imports across codebase...")

    files_updated = 0
    imports_updated = 0
    import_details = defaultdict(list)

    for java_file in BASE_DIR.rglob("*.java"):
        # Skip files we just moved (they're in new locations now)
        rel_path = java_file.relative_to(BASE_DIR)

        if dry_run:
            # In dry-run, files haven't been moved yet
            continue

        with open(java_file, 'r', encoding='utf-8') as f:
            original_content = f.read()

        content = original_content
        file_import_count = 0

        # Update specific imports for each class
        for classname, domain in CLASS_TO_DOMAIN.items():
            old_import = f"import com.meatrics.pricing.{classname};"
            new_import = f"import com.meatrics.pricing.{domain}.{classname};"

            if old_import in content:
                content = content.replace(old_import, new_import)
                file_import_count += 1
                import_details[str(rel_path)].append(f"{classname} -> {domain}")

        # Handle wildcard imports
        if "import com.meatrics.pricing.*;" in content:
            # Find which classes are used
            used_classes = set()
            for classname in CLASS_TO_DOMAIN.keys():
                # Simple check if class name appears in the file
                if re.search(r'\b' + classname + r'\b', content):
                    used_classes.add(classname)

            if used_classes:
                # Build new imports
                new_imports = []
                for classname in sorted(used_classes):
                    domain = CLASS_TO_DOMAIN[classname]
                    new_imports.append(f"import com.meatrics.pricing.{domain}.{classname};")

                # Replace wildcard with specific imports
                content = content.replace(
                    "import com.meatrics.pricing.*;",
                    "\n".join(new_imports)
                )
                file_import_count += len(new_imports)
                import_details[str(rel_path)].append(f"Wildcard -> {len(new_imports)} specific imports")

        # Write back if changed
        if content != original_content:
            if not dry_run:
                with open(java_file, 'w', encoding='utf-8') as f:
                    f.write(content)

            files_updated += 1
            imports_updated += file_import_count
            print(f"  ✓ {rel_path} ({file_import_count} imports)")

    print(f"\nFiles with updated imports: {files_updated}")
    print(f"Total imports updated: {imports_updated}")
    return files_updated, imports_updated


def verify_structure():
    """Verify the new directory structure"""
    print("\nVERIFYING STRUCTURE:")
    print(f"  {PRICING_DIR.name}/")

    total_files = 0
    for domain in sorted(DOMAINS.keys()):
        domain_path = PRICING_DIR / domain
        if domain_path.exists():
            file_count = len(list(domain_path.glob("*.java")))
            total_files += file_count
            print(f"    ├── {domain}/ ({file_count} files)")
        else:
            print(f"    ├── {domain}/ (NOT CREATED)")

    # Count UI files separately
    ui_path = PRICING_DIR / "ui"
    if ui_path.exists():
        ui_file_count = len(list(ui_path.rglob("*.java")))
        print(f"    └── ui/ ({ui_file_count} files)")

    print(f"\n  Total domain files: {total_files}")


def print_summary(moved_count, files_updated, imports_updated):
    """Print final summary"""
    print_header("REFACTORING COMPLETE")

    print(f"\nFiles moved and package declarations updated: {moved_count}")
    print(f"Files with import statements updated: {files_updated}")
    print(f"Total import statements updated: {imports_updated}")

    verify_structure()

    print("\nNext steps:")
    print("  1. Verify compilation: mvn compile")
    print("  2. Run tests: mvn test")
    print("  3. Review changes: git status && git diff")
    print("  4. Commit: git add . && git commit -m 'Refactor pricing package to DDD structure'")


def main():
    """Main execution function"""
    dry_run = "--dry-run" in sys.argv

    print_header("PRICING PACKAGE REFACTORING")
    print(f"Mode: {'DRY-RUN (no changes will be made)' if dry_run else 'LIVE (changes will be applied)'}")
    print(f"Target directory: {PRICING_DIR}")

    if not PRICING_DIR.exists():
        print(f"\nERROR: Pricing directory not found: {PRICING_DIR}")
        print("Please run this script from the project root directory.")
        sys.exit(1)

    if dry_run:
        print("\n⚠ DRY-RUN MODE: No changes will be made")
    else:
        response = input("\n⚠ This will reorganize 33+ files. Continue? (yes/no): ")
        if response.lower() != "yes":
            print("Aborted.")
            sys.exit(0)

    # Execute refactoring steps
    create_domain_packages(dry_run)
    moved_count = move_files_and_update_packages(dry_run)

    if not dry_run:
        files_updated, imports_updated = update_imports(dry_run)
        print_summary(moved_count, files_updated, imports_updated)
    else:
        print("\n[DRY-RUN] To execute, run without --dry-run flag")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nAborted by user.")
        sys.exit(1)
    except Exception as e:
        print(f"\n\nERROR: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
