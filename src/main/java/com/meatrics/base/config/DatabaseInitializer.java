package com.meatrics.base.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Value("${db.name}")
    private String databaseName;

    @Value("${db.host}")
    private String host;

    @Value("${db.port}")
    private String port;

    @Value("${db.username}")
    private String username;

    @Value("${db.password}")
    private String password;

    @Value("${db.client-name}")
    private String clientName;

    @Override
    public void run(String... args) {
        createDatabaseIfNotExists();
    }

    private void createDatabaseIfNotExists() {
        String postgresUrl = String.format("jdbc:postgresql://%s:%s/postgres?ApplicationName=%s",
                host, port, clientName);

        try (Connection conn = DriverManager.getConnection(postgresUrl, username, password)) {
            if (!databaseExists(conn, databaseName)) {
                log.info("Database '{}' does not exist. Creating...", databaseName);
                createDatabase(conn, databaseName);
                log.info("Database '{}' created successfully.", databaseName);
            } else {
                log.info("Database '{}' already exists.", databaseName);
            }
        } catch (SQLException e) {
            log.error("Failed to initialize database: {}", e.getMessage(), e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private boolean databaseExists(Connection conn, String dbName) throws SQLException {
        String query = "SELECT 1 FROM pg_database WHERE datname = ?";
        try (var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, dbName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void createDatabase(Connection conn, String dbName) throws SQLException {
        // Can't use prepared statement for CREATE DATABASE
        String sql = String.format("CREATE DATABASE %s", dbName);
        try (var stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
