package com.vendoryx.util;

import jakarta.servlet.http.HttpServletResponse;

public final class CorsUtil {

    private static final String ORIGIN = resolveOrigin();

    private CorsUtil() {}

    public static void apply(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", ORIGIN);
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Vary", "Origin");
    }

    private static String resolveOrigin() {
        String origin = System.getenv("VENDORYX_CORS_ORIGIN");
        if (origin == null || origin.isBlank() || "*".equals(origin.trim())) {
            return "http://localhost:4200";
        }
        return origin.trim();
    }
}
