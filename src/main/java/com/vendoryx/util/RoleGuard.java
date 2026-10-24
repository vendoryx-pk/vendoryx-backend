package com.vendoryx.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class RoleGuard {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_ROLE = "role";
    public static final String ATTR_SHOP_IDS = "shopIds";

    private RoleGuard() {}

    /**
     * @return true if role is allowed; otherwise writes 403 and returns false
     */
    public static boolean requireRole(HttpServletRequest req, HttpServletResponse resp, String... allowedRoles)
            throws IOException {
        Object roleObj = req.getAttribute(ATTR_ROLE);
        String role = roleObj != null ? roleObj.toString() : null;

        if (role != null && allowedRoles != null) {
            for (String allowed : allowedRoles) {
                if (allowed != null && allowed.equalsIgnoreCase(role)) {
                    return true;
                }
            }
        }

        JsonUtil.writeError(resp, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        return false;
    }

    public static Integer getUserId(HttpServletRequest req) {
        Object value = req.getAttribute(ATTR_USER_ID);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<Integer> getShopIds(HttpServletRequest req) {
        Object value = req.getAttribute(ATTR_SHOP_IDS);
        if (value instanceof List<?> list) {
            return (List<Integer>) list;
        }
        return Collections.emptyList();
    }

    public static boolean ownsShop(HttpServletRequest req, int shopId) {
        return getShopIds(req).contains(shopId);
    }
}
