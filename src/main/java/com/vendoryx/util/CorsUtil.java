package com.vendoryx.util;

import com.vendoryx.config.DotEnv;
import jakarta.servlet.http.HttpServletResponse;

public final class CorsUtil {

    private CorsUtil() {}

    public static void apply(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", resolveOrigin());
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Vary", "Origin");
    }

    private static String resolveOrigin() {
        DotEnv.load();
        String origin = DotEnv.get("VENDORYX_CORS_ORIGIN");
        if (origin == null || origin.isBlank() || "*".equals(origin.trim())) {
            return "http://localhost:4200";
        }
        return origin.trim();
    }
}
