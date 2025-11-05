# jOOQ Code Generation Guide

This guide explains how to generate jOOQ classes from your database schema.

## Prerequisites

1. PostgreSQL installed and running on localhost:5432
2. Database user: `postgres` with password: `password` (or configure via properties)

## Normal Development Workflow

**Just run your application!** Liquibase will automatically:
- Create the database (via `DatabaseInitializer`)
- Run migrations on startup

You **don't** need to regenerate jOOQ code unless the schema changes.

## Generate jOOQ Code (Only When Schema Changes)

When you modify database tables/views, regenerate jOOQ classes:

```bash
mvn jooq-codegen:generate
```

Or in IntelliJ:
1. Open Maven tool window
2. Navigate to: **meatrics** → **Plugins** → **jooq-codegen** → **jooq-codegen:generate**
3. Double-click to run

This generates jOOQ classes in `src/main/java/com/meatrics/generated/`

## Custom Database Settings

Override database credentials:

```bash
mvn clean generate-sources \
  -Ddb.host=localhost \
  -Ddb.port=5432 \
  -Ddb.name=meatrics \
  -Ddb.username=postgres \
  -Ddb.password=your_password
```

## Output Location

Generated jOOQ classes will be in:
```
src/main/java/com/meatrics/generated/
├── Tables.java
├── tables/
│   ├── ImportSummary.java
│   ├── ImportedLineItems.java
│   └── records/
│       ├── ImportSummaryRecord.java
│       └── ImportedLineItemsRecord.java
└── ...
```

## Troubleshooting

### Database Connection Issues

If you get connection errors:

1. Check PostgreSQL is running:
   ```bash
   # Windows
   pg_ctl status -D "C:\Program Files\PostgreSQL\16\data"

   # Linux/Mac
   sudo systemctl status postgresql
   ```

2. Verify credentials in `src/main/resources/application.properties`

3. Test connection manually:
   ```bash
   psql -h localhost -p 5432 -U postgres -d meatrics
   ```

### Plugin Execution Issues

If plugins don't run:

1. Check plugin versions are compatible
2. Clear Maven cache:
   ```bash
   mvn dependency:purge-local-repository
   ```

### Generated Code Conflicts

If you see compilation errors after generation:

1. Refresh your IDE project (IntelliJ: File → Reload All from Disk)
2. Mark `src/main/java` as Sources Root in your IDE
3. Rebuild the project

## Integration with IDE

### IntelliJ IDEA

1. Right-click `pom.xml` → Maven → Reload project
2. Or use Maven tool window: Click refresh icon
3. Run configuration: Create new Maven run configuration with goal `clean generate-sources`

### Eclipse

1. Right-click project → Maven → Update Project
2. Run As → Maven build... → Goals: `clean generate-sources`

## Notes

- Generated code is tracked in git (in `src/main/java`)
- Regenerate after any database schema changes
- Don't manually edit generated jOOQ classes
