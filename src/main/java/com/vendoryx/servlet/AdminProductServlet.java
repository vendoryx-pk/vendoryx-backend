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
import java.math.BigDecimal;
import java.sql.SQLException;

/** /api/admin/products — ADMIN/DEVELOPER */
public class AdminProductServlet extends HttpServlet {

    private final ProductDAO productDAO = new ProductDAO();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.apply(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        CorsUtil.apply(request, response);
        if (!RoleGuard.requireRole(request, response, "ADMIN", "DEVELOPER")) {
            return;
        }
        try {
            Integer id = JsonUtil.queryInt(request, "id");
            if (id != null) {
                Product product = productDAO.getById(id, true);
                if (product == null) {
                    JsonUtil.writeError(response, HttpServletResponse.SC_NOT_FOUND, "Product not found");
                    return;
                }
                JsonUtil.writeSuccess(response, "Product loaded", product);
                return;
            }
            Integer categoryId = JsonUtil.queryInt(request, "categoryId");
            String q = JsonUtil.queryText(request, "q");
            boolean includeInactive = "true".equalsIgnoreCase(JsonUtil.queryText(request, "includeInactive"));
            int page = JsonUtil.pageOrDefault(request, 1);
            int size = JsonUtil.sizeOrDefault(request, 20, 100);
            JsonUtil.writeSuccess(response, "Products loaded",
                    productDAO.listAdmin(categoryId, q, includeInactive, page, size));
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        CorsUtil.apply(request, response);
        if (!RoleGuard.requireRole(request, response, "ADMIN", "DEVELOPER")) {
            return;
        }
        try {
            Product product = parseProduct(JsonUtil.readBody(request));
            if (product == null) {
                JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "sku, productName, categoryId and unitPrice (>=0) are required");
                return;
            }
            if (productDAO.skuExists(product.getSku(), null)) {
                JsonUtil.writeError(response, HttpServletResponse.SC_CONFLICT, "SKU already exists");
                return;
            }
            Product created = productDAO.create(product);
            JsonUtil.writeSuccess(response, "Product created", created);
        } catch (JsonSyntaxException e) {
            JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON body");
        } catch (SQLException e) {
            e.printStackTrace();
            if (isUniqueViolation(e)) {
                JsonUtil.writeError(response, HttpServletResponse.SC_CONFLICT, "SKU already exists");
            } else {
                JsonUtil.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Database error: " + e.getMessage());
            }
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        CorsUtil.apply(request, response);
        if (!RoleGuard.requireRole(request, response, "ADMIN", "DEVELOPER")) {
            return;
        }
        try {
            Integer id = JsonUtil.queryInt(request, "id");
            Product product = parseProduct(JsonUtil.readBody(request));
            if (id == null || id <= 0 || product == null) {
                JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "id query param and product fields are required");
                return;
            }
            product.setProductId(id);
            if (productDAO.skuExists(product.getSku(), id)) {
                JsonUtil.writeError(response, HttpServletResponse.SC_CONFLICT, "SKU already exists");
                return;
            }
            Product updated = productDAO.update(product);
            if (updated == null) {
                JsonUtil.writeError(response, HttpServletResponse.SC_NOT_FOUND, "Product not found");
                return;
            }
            JsonUtil.writeSuccess(response, "Product updated", updated);
        } catch (JsonSyntaxException e) {
            JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON body");
        } catch (SQLException e) {
            e.printStackTrace();
            if (isUniqueViolation(e)) {
                JsonUtil.writeError(response, HttpServletResponse.SC_CONFLICT, "SKU already exists");
            } else {
                JsonUtil.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Database error: " + e.getMessage());
            }
        }
    }

    private Product parseProduct(String raw) {
        JsonObject body = JsonUtil.parseObject(raw);
        String sku = JsonUtil.text(body, "sku");
        String name = JsonUtil.textAny(body, "productName", "product_name", "name");
        Integer categoryId = JsonUtil.integer(body, "categoryId");
        if (categoryId == null) {
            categoryId = JsonUtil.integer(body, "category_id");
        }
        BigDecimal unitPrice = JsonUtil.money(body, "unitPrice");
        if (unitPrice == null) {
            unitPrice = JsonUtil.money(body, "unit_price");
        }
        if (sku == null || sku.isBlank() || name == null || name.isBlank()
                || categoryId == null || categoryId <= 0
                || unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }

        Product p = new Product();
        p.setSku(sku);
        p.setProductName(name);
        p.setCategoryId(categoryId);
        p.setUnitPrice(unitPrice);
        p.setCostPrice(JsonUtil.money(body, "costPrice") != null
                ? JsonUtil.money(body, "costPrice")
                : JsonUtil.money(body, "cost_price"));
        p.setDescription(JsonUtil.text(body, "description"));
        p.setUnitOfMeasure(JsonUtil.textAny(body, "unitOfMeasure", "unit_of_measure"));
        p.setImageUrl(JsonUtil.textAny(body, "imageUrl", "image_url"));
        Boolean active = JsonUtil.bool(body, "active");
        if (active == null) {
            active = JsonUtil.bool(body, "isActive");
        }
        p.setActive(active != null ? active : true);
        return p;
    }

    private boolean isUniqueViolation(SQLException e) {
        return "23505".equals(e.getSQLState())
                || (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate"));
    }
}
