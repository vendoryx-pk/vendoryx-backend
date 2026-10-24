package com.vendoryx.dao;

import com.vendoryx.config.DBConnection;
import com.vendoryx.model.Product;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductDAO {

    private static final String SELECT_BASE = """
            SELECT p.product_id, p.category_id, c.category_name, p.sku, p.product_name, p.description,
                   p.unit_price, p.cost_price, p.unit_of_measure, p.image_url, p.is_active,
                   COALESCE(i.stock_quantity, 0) AS stock_quantity,
                   COALESCE(i.low_stock_threshold, 0) AS low_stock_threshold
            FROM products p
            JOIN categories c ON p.category_id = c.category_id
            LEFT JOIN inventory i ON i.product_id = p.product_id
            """;

    public Map<String, Object> listCatalog(Integer categoryId, String q, int page, int size) throws SQLException {
        return listInternal(categoryId, q, false, page, size, false);
    }

    public Map<String, Object> listAdmin(Integer categoryId, String q, boolean includeInactive,
                                         int page, int size) throws SQLException {
        return listInternal(categoryId, q, includeInactive, page, size, true);
    }

    public Product getById(int productId, boolean includeStockDetails) throws SQLException {
        String sql = SELECT_BASE + " WHERE p.product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs, includeStockDetails);
                }
            }
        }
        return null;
    }

    public boolean skuExists(String sku, Integer excludeProductId) throws SQLException {
        String sql = excludeProductId == null
                ? "SELECT 1 FROM products WHERE LOWER(sku) = LOWER(?) LIMIT 1"
                : "SELECT 1 FROM products WHERE LOWER(sku) = LOWER(?) AND product_id <> ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sku);
            if (excludeProductId != null) {
                ps.setInt(2, excludeProductId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Product create(Product product) throws SQLException {
        String sql = """
                INSERT INTO products (
                    category_id, sku, product_name, description, unit_price, cost_price,
                    unit_of_measure, image_url, is_active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int productId;
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    bindProduct(ps, product);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            conn.rollback();
                            return null;
                        }
                        productId = keys.getInt(1);
                    }
                }
                try (PreparedStatement inv = conn.prepareStatement("""
                        INSERT INTO inventory (product_id, stock_quantity, low_stock_threshold)
                        VALUES (?, 0, 0)
                        """)) {
                    inv.setInt(1, productId);
                    inv.executeUpdate();
                }
                conn.commit();
                return getById(productId, true);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public Product update(Product product) throws SQLException {
        String sql = """
                UPDATE products SET
                    category_id = ?, sku = ?, product_name = ?, description = ?,
                    unit_price = ?, cost_price = ?, unit_of_measure = ?, image_url = ?, is_active = ?
                WHERE product_id = ?
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindProduct(ps, product);
            ps.setInt(10, product.getProductId());
            if (ps.executeUpdate() == 0) {
                return null;
            }
        }
        return getById(product.getProductId(), true);
    }

    public boolean updateStock(int productId, int stockQuantity, int lowStockThreshold) throws SQLException {
        String upsert = """
                INSERT INTO inventory (product_id, stock_quantity, low_stock_threshold)
                VALUES (?, ?, ?)
                ON CONFLICT (product_id) DO UPDATE
                SET stock_quantity = EXCLUDED.stock_quantity,
                    low_stock_threshold = EXCLUDED.low_stock_threshold
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setInt(1, productId);
            ps.setInt(2, stockQuantity);
            ps.setInt(3, lowStockThreshold);
            return ps.executeUpdate() > 0;
        }
    }

    private Map<String, Object> listInternal(Integer categoryId, String q, boolean includeInactive,
                                             int page, int size, boolean adminView) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (!includeInactive) {
            where.append(" AND p.is_active = TRUE");
        }
        if (categoryId != null) {
            where.append(" AND p.category_id = ?");
            params.add(categoryId);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (LOWER(p.product_name) LIKE ? OR LOWER(p.sku) LIKE ?)");
            String like = "%" + q.toLowerCase() + "%";
            params.add(like);
            params.add(like);
        }

        String countSql = "SELECT COUNT(*) FROM products p" + where;
        int total;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                total = rs.getInt(1);
            }
        }

        String dataSql = SELECT_BASE + where + " ORDER BY p.product_name LIMIT ? OFFSET ?";
        List<Product> items = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(dataSql)) {
            bindParams(ps, params);
            ps.setInt(params.size() + 1, size);
            ps.setInt(params.size() + 2, (page - 1) * size);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs, adminView));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        return result;
    }

    private void bindProduct(PreparedStatement ps, Product product) throws SQLException {
        ps.setInt(1, product.getCategoryId());
        ps.setString(2, product.getSku());
        ps.setString(3, product.getProductName());
        ps.setString(4, product.getDescription());
        ps.setBigDecimal(5, product.getUnitPrice());
        if (product.getCostPrice() == null) {
            ps.setNull(6, Types.NUMERIC);
        } else {
            ps.setBigDecimal(6, product.getCostPrice());
        }
        ps.setString(7, product.getUnitOfMeasure());
        ps.setString(8, product.getImageUrl());
        ps.setBoolean(9, product.isActive());
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof Integer) {
                ps.setInt(i + 1, (Integer) value);
            } else {
                ps.setString(i + 1, String.valueOf(value));
            }
        }
    }

    private Product map(ResultSet rs, boolean adminView) throws SQLException {
        Product p = new Product();
        p.setProductId(rs.getInt("product_id"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setCategoryName(rs.getString("category_name"));
        p.setSku(rs.getString("sku"));
        p.setProductName(rs.getString("product_name"));
        p.setDescription(rs.getString("description"));
        p.setUnitPrice(rs.getBigDecimal("unit_price"));
        p.setCostPrice(rs.getBigDecimal("cost_price"));
        p.setUnitOfMeasure(rs.getString("unit_of_measure"));
        p.setImageUrl(rs.getString("image_url"));
        p.setActive(rs.getBoolean("is_active"));

        int stock = rs.getInt("stock_quantity");
        int threshold = rs.getInt("low_stock_threshold");
        if (adminView) {
            p.setStockQuantity(stock);
            p.setLowStockThreshold(threshold);
        } else {
            p.setInStock(stock > 0);
            p.setStockQuantity(null);
            p.setLowStockThreshold(null);
        }
        return p;
    }

    public BigDecimal getActiveUnitPrice(Connection conn, int productId) throws SQLException {
        String sql = "SELECT unit_price FROM products WHERE product_id = ? AND is_active = TRUE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("unit_price");
                }
            }
        }
        return null;
    }

    public String getProductName(Connection conn, int productId) throws SQLException {
        String sql = "SELECT product_name FROM products WHERE product_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("product_name");
                }
            }
        }
        return "product#" + productId;
    }

    public int getStock(Connection conn, int productId) throws SQLException {
        String sql = "SELECT COALESCE(stock_quantity, 0) FROM inventory WHERE product_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}
