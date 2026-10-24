package com.vendoryx.config;

/**
 * Validates required environment variables at process start (before Tomcat).
 * Values come from OS env or optional local {@code .env} (see DotEnv).
 */
public final class EnvConfig {

    private EnvConfig() {}

    public static void validateRequired() {
        DotEnv.load();
        require("VENDORYX_DB_URL");
        require("VENDORYX_DB_USER");
        require("VENDORYX_DB_PASSWORD");

        String jwt = DotEnv.get("VENDORYX_JWT_SECRET");
        if (jwt == null || jwt.isBlank()) {
            fail("Missing required environment variable: VENDORYX_JWT_SECRET (min 32 characters).");
        }
        if (jwt.trim().length() < 32) {
            fail("VENDORYX_JWT_SECRET must be at least 32 characters (got " + jwt.trim().length() + ").");
        }
    }

    public static int port() {
        String raw = DotEnv.get("PORT");
        if (raw == null || raw.isBlank()) {
            return 8080;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                fail("PORT must be between 1 and 65535 (got " + port + ").");
            }
            return port;
        } catch (NumberFormatException e) {
            fail("PORT must be a number (got '" + raw + "').");
            return 8080;
        }
    }

    private static void require(String key) {
        String value = DotEnv.get(key);
        if (value == null || value.isBlank()) {
            fail("Missing required environment variable: " + key
                    + " (set OS env or create a .env file — see .env.example)");
        }
    }

    private static void fail(String message) {
        System.err.println("FATAL: " + message);
        System.err.println("Required: VENDORYX_DB_URL, VENDORYX_DB_USER, VENDORYX_DB_PASSWORD,");
        System.err.println("          VENDORYX_JWT_SECRET (>=32 chars)");
        System.err.println("Optional: VENDORYX_CORS_ORIGIN, PORT (default 8080)");
        System.err.println("Local tip: copy .env.example to .env and fill values.");
        throw new IllegalStateException(message);
    }
}
