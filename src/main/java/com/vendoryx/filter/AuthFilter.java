package com.vendoryx.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.vendoryx.util.CorsUtil;
import com.vendoryx.util.JwtUtil;
import com.vendoryx.util.RoleGuard;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protects /api/* except OPTIONS and /api/auth/login.
 */
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        CorsUtil.apply(resp);

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        String path = req.getRequestURI();
        String context = req.getContextPath() != null ? req.getContextPath() : "";
        if (path.startsWith(context)) {
            path = path.substring(context.length());
        }

        if ("/api/auth/login".equals(path) || "/health".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            writeUnauthorized(resp);
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            writeUnauthorized(resp);
            return;
        }

        try {
            DecodedJWT jwt = JwtUtil.verify(token);
            req.setAttribute(RoleGuard.ATTR_USER_ID, JwtUtil.getUserId(jwt));
            req.setAttribute(RoleGuard.ATTR_ROLE, JwtUtil.getRole(jwt));
            req.setAttribute(RoleGuard.ATTR_SHOP_IDS, JwtUtil.getShopIds(jwt));
            chain.doFilter(request, response);
        } catch (JWTVerificationException e) {
            writeUnauthorized(resp);
        }
    }

    private void writeUnauthorized(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "Unauthorized");
        PrintWriter out = resp.getWriter();
        out.print(com.vendoryx.util.JsonUtil.gson().toJson(body));
        out.flush();
    }
}
