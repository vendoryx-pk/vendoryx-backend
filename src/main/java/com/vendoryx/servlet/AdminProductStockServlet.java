package com.vendoryx.servlet;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.vendoryx.dao.ProductDAO;
import com.vendoryx.model.Product;
import com.vendoryx.util.CorsUtil;
import com.vendoryx.util.JsonUtil;
import com.vendoryx.util.RoleGuard;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/** PUT /api/admin/products/stock?productId= */
public class AdminProductStockServlet extends HttpServlet {

    private final ProductDAO productDAO = new ProductDAO();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.apply(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        CorsUtil.apply(response);
        if (!RoleGuard.requireRole(request, response, "ADMIN", "DEVELOPER")) {
            return;
        }
        try {
            Integer productId = JsonUtil.queryInt(request, "productId");
            JsonObject body = JsonUtil.parseObject(JsonUtil.readBody(request));
            Integer stockQuantity = JsonUtil.integer(body, "stockQuantity");
            Integer lowStockThreshold = JsonUtil.integer(body, "lowStockThreshold");

            if (productId == null || productId <= 0
                    || stockQuantity == null || stockQuantity < 0
                    || lowStockThreshold == null || lowStockThreshold < 0) {
                JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "productId, stockQuantity (>=0) and lowStockThreshold (>=0) are required");
                return;
            }

            if (productDAO.getById(productId, true) == null) {
                JsonUtil.writeError(response, HttpServletResponse.SC_NOT_FOUND, "Product not found");
                return;
            }

            boolean ok = productDAO.updateStock(productId, stockQuantity, lowStockThreshold);
            if (!ok) {
                JsonUtil.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Stock update failed");
                return;
            }
            Product updated = productDAO.getById(productId, true);
            JsonUtil.writeSuccess(response, "Stock updated", updated);
        } catch (JsonSyntaxException e) {
            JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON body");
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error: " + e.getMessage());
        }
    }
}
