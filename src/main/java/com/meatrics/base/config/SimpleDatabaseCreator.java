package com.meatrics.base.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Creates the PostgreSQL database if it doesn't exist.
 * Runs early in Spring Boot lifecycle via EnvironmentPostProcessor.
 * Configured in META-INF/spring.factories.
 */
public class SimpleDatabaseCreator implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dbName = environment.getProperty("db.name");
        String username = environment.getProperty("db.username");
        String password = environment.getProperty("db.password");
        String host = environment.getProperty("db.host", "localhost");
        String port = environment.getProperty("db.port", "5432");

        if (dbName != null && username != null && password != null) {
            createDatabase(dbName, username, password, host, port);
        }
    }

    private void createDatabase(String dbName, String username, String password, String host, String port) {
        String postgresUrl = String.format("jdbc:postgresql://%s:%s/postgres", host, port);

        try (Connection conn = DriverManager.getConnection(postgresUrl, username, password)) {
            // Check if database exists
            var checkStmt = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?");
            checkStmt.setString(1, dbName);
            var rs = checkStmt.executeQuery();

            if (!rs.next()) {
                // Create database (no quotes needed for lowercase names)
                String createSql = "CREATE DATABASE " + dbName;
                conn.createStatement().executeUpdate(createSql);
                System.out.println("Created database: " + dbName);
            }
        } catch (Exception e) {
            System.err.println("Database creation failed: " + e.getMessage());
            // Don't throw - let app continue if database already exists or if creation fails
            // Liquibase/app startup will provide better error messages if there are real issues
        }
    }
}
