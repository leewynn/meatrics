package com.meatrics.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Component
public class DatabaseConnectionUtil {

    @Value("${db.host}")
    private String host;

    @Value("${db.port}")
    private String port;

    @Value("${db.name}")
    private String databaseName;

    @Value("${db.username}")
    private String username;

    @Value("${db.password}")
    private String password;

    /**
     * Get a JDBC connection to the application database
     */
    public Connection getConnection() throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, databaseName);
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * Get JDBC URL for the application database
     */
    public String getJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%s/%s", host, port, databaseName);
    }

    /**
     * Get database username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Get database password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Get database name
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Get database host
     */
    public String getHost() {
        return host;
    }

    /**
     * Get database port
     */
    public String getPort() {
        return port;
    }

    /**
     * Test database connection
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
