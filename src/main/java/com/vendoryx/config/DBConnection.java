package com.vendoryx.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariCP-backed connection factory.
 * Requires env: VENDORYX_DB_URL, VENDORYX_DB_USER, VENDORYX_DB_PASSWORD.
 */
public final class DBConnection {

    private static final String URL = requireEnv("VENDORYX_DB_URL");
    private static final String USER = requireEnv("VENDORYX_DB_USER");
    private static final String PASSWORD = requireEnv("VENDORYX_DB_PASSWORD");

    private static volatile HikariDataSource dataSource;

    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    private static HikariDataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DBConnection.class) {
                if (dataSource == null) {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(URL);
                    config.setUsername(USER);
                    config.setPassword(PASSWORD);
                    config.setMaximumPoolSize(5);
                    config.setPoolName("vendoryx-pool");
                    config.setDriverClassName("org.postgresql.Driver");
                    dataSource = new HikariDataSource(config);
                }
            }
        }
        return dataSource;
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new ExceptionInInitializerError(
                    "Missing required environment variable: " + key
                            + ". Set VENDORYX_DB_URL, VENDORYX_DB_USER, and VENDORYX_DB_PASSWORD before starting.");
        }
        return value.trim();
    }
}
