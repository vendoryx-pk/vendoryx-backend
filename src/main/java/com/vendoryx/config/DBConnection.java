package com.vendoryx.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariCP-backed connection factory.
 * Requires: VENDORYX_DB_URL, VENDORYX_DB_USER, VENDORYX_DB_PASSWORD (OS env or .env).
 */
public final class DBConnection {

    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;

    static {
        DotEnv.load();
        URL = requireEnv("VENDORYX_DB_URL");
        USER = requireEnv("VENDORYX_DB_USER");
        PASSWORD = requireEnv("VENDORYX_DB_PASSWORD");
    }

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
        String value = DotEnv.get(key);
        if (value == null || value.isBlank()) {
            throw new ExceptionInInitializerError(
                    "Missing required environment variable: " + key
                            + ". Set env vars or create .env (see .env.example).");
        }
        return value.trim();
    }
}
