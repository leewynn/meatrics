package com.meatrics.util;

import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;

@Component
public class JooqCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(JooqCodeGenerator.class);

    private final DatabaseConnectionUtil dbUtil;

    public JooqCodeGenerator(DatabaseConnectionUtil dbUtil) {
        this.dbUtil = dbUtil;
    }

    /**
     * Generate jOOQ classes from the current database schema
     */
    public void generateJooqClasses() throws Exception {
        log.info("Starting jOOQ code generation...");

        Configuration configuration = new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(dbUtil.getJdbcUrl())
                        .withUser(dbUtil.getUsername())
                        .withPassword(dbUtil.getPassword()))
                .withGenerator(new Generator()
                        .withDatabase(new Database()
                                .withName("org.jooq.meta.postgres.PostgresDatabase")
                                .withIncludes(".*")
                                .withExcludes("databasechangelog.*")
                                .withInputSchema("public"))
                        .withTarget(new Target()
                                .withPackageName("com.meatrics.generated")
                                .withDirectory(getTargetDirectory())));

        GenerationTool.generate(configuration);

        log.info("jOOQ code generation completed successfully!");
    }

    /**
     * Generate jOOQ classes with custom configuration
     */
    public void generateJooqClasses(String packageName, String outputDirectory) throws Exception {
        log.info("Starting jOOQ code generation with custom settings...");
        log.info("Package: {}", packageName);
        log.info("Output directory: {}", outputDirectory);

        Configuration configuration = new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(dbUtil.getJdbcUrl())
                        .withUser(dbUtil.getUsername())
                        .withPassword(dbUtil.getPassword()))
                .withGenerator(new Generator()
                        .withDatabase(new Database()
                                .withName("org.jooq.meta.postgres.PostgresDatabase")
                                .withIncludes(".*")
                                .withExcludes("databasechangelog.*")
                                .withInputSchema("public"))
                        .withTarget(new Target()
                                .withPackageName(packageName)
                                .withDirectory(outputDirectory)));

        GenerationTool.generate(configuration);

        log.info("jOOQ code generation completed successfully!");
    }

    /**
     * Get the target directory for generated sources
     */
    private String getTargetDirectory() {
        // Get project root directory
        String projectRoot = Paths.get("").toAbsolutePath().toString();
        return Paths.get(projectRoot, "src", "main", "java").toString();
    }

    /**
     * Generate jOOQ classes for specific tables only
     */
    public void generateJooqClassesForTables(String... tableNames) throws Exception {
        log.info("Starting jOOQ code generation for specific tables: {}", String.join(", ", tableNames));

        String includePattern = String.join("|", tableNames);

        Configuration configuration = new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(dbUtil.getJdbcUrl())
                        .withUser(dbUtil.getUsername())
                        .withPassword(dbUtil.getPassword()))
                .withGenerator(new Generator()
                        .withDatabase(new Database()
                                .withName("org.jooq.meta.postgres.PostgresDatabase")
                                .withIncludes(includePattern)
                                .withExcludes("databasechangelog.*")
                                .withInputSchema("public"))
                        .withTarget(new Target()
                                .withPackageName("com.meatrics.generated")
                                .withDirectory(getTargetDirectory())));

        GenerationTool.generate(configuration);

        log.info("jOOQ code generation completed successfully!");
    }
}
