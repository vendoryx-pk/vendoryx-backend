package com.vendoryx.service;

import com.vendoryx.config.DBConnection;
import com.vendoryx.dao.OrderDAO;
import com.vendoryx.dao.ProductDAO;
import com.vendoryx.model.Order;
import com.vendoryx.model.OrderItem;
import jakarta.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrderService {

    private static final Set<String> STOCK_HELD_STATUSES = Set.of(
            "APPROVED", "PROCESSING", "PACKED", "OUT_FOR_DELIVERY", "DELIVERED"
    );

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PENDING", Set.of("APPROVED", "CANCELLED"),
            "APPROVED", Set.of("PROCESSING", "PACKED", "CANCELLED"),
            "PROCESSING", Set.of("PACKED", "CANCELLED"),
            "PACKED", Set.of("OUT_FOR_DELIVERY", "CANCELLED"),
            "OUT_FOR_DELIVERY", Set.of("DELIVERED", "CANCELLED"),
            "DELIVERED", Set.of(),
            "CANCELLED", Set.of()
    );

    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();

    public Map<String, Object> placeOrder(int userId, List<Integer> tokenShopIds, int shopId,
                                          List<Map.Entry<Integer, Integer>> rawItems, String notes)
            throws BusinessException, SQLException {

        if (tokenShopIds == null || !tokenShopIds.contains(shopId)) {
            throw new BusinessException(HttpServletResponse.SC_FORBIDDEN, "Shop not allowed for this user");
        }
        if (rawItems == null || rawItems.isEmpty() || rawItems.size() > 100) {
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, "Items must be between 1 and 100");
        }

        Map<Integer, Integer> merged = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : rawItems) {
            Integer productId = entry.getKey();
            Integer qty = entry.getValue();
            if (productId == null || productId <= 0 || qty == null || qty < 1 || qty > 10000) {
                throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST,
                        "Each item needs valid productId and quantity 1..10000");
            }
            merged.merge(productId, qty, Integer::sum);
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                OrderDAO.ShopOrderContext shop = orderDAO.getShopContext(conn, shopId);
                if (shop == null) {
                    throw new BusinessException(HttpServletResponse.SC_NOT_FOUND, "Shop not found");
                }
                if (shop.status() == null || !"ACTIVE".equalsIgnoreCase(shop.status())) {
                    throw new BusinessException(HttpServletResponse.SC_FORBIDDEN, "Shop is not ACTIVE");
                }

                BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                Map<Integer, BigDecimal> prices = new HashMap<>();
                Map<Integer, Integer> quantities = new LinkedHashMap<>();

                for (Map.Entry<Integer, Integer> e : merged.entrySet()) {
                    int productId = e.getKey();
                    int qty = e.getValue();
                    BigDecimal price = productDAO.getActiveUnitPrice(conn, productId);
                    if (price == null) {
                        throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST,
                                "Product not found or inactive: " + productId);
                    }
                    int stock = productDAO.getStock(conn, productId);
                    if (stock < qty) {
                        String name = productDAO.getProductName(conn, productId);
                        throw new BusinessException(HttpServletResponse.SC_CONFLICT,
                                "Insufficient stock for " + name);
                    }
                    prices.put(productId, price);
                    quantities.put(productId, qty);
                    total = total.add(price.multiply(BigDecimal.valueOf(qty)));
                }

                BigDecimal tax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal discount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal finalAmount = total.setScale(2, RoundingMode.HALF_UP);

                int orderId = orderDAO.insertOrder(conn, shopId, userId, total, tax, discount, finalAmount, notes);
                for (Map.Entry<Integer, Integer> e : quantities.entrySet()) {
                    int productId = e.getKey();
                    int qty = e.getValue();
                    BigDecimal price = prices.get(productId);
                    BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                    orderDAO.insertOrderItem(conn, orderId, productId, qty, price, subtotal);
                }
                orderDAO.insertStatusHistory(conn, orderId, null, "PENDING", userId, "Order placed");

                Order created = orderDAO.getOrderHeader(conn, orderId);
                conn.commit();

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("orderId", created.getOrderId());
                data.put("orderNumber", created.getOrderNumber());
                data.put("finalAmount", created.getFinalAmount());
                data.put("status", created.getOrderStatus());
                return data;
            } catch (BusinessException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public Order changeStatus(int orderId, String newStatusRaw, String comments, int changedByUserId)
            throws BusinessException, SQLException {

        if (newStatusRaw == null || newStatusRaw.isBlank()) {
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, "newStatus is required");
        }
        String newStatus = newStatusRaw.trim().toUpperCase();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Order order = orderDAO.lockOrder(conn, orderId);
                if (order == null) {
                    throw new BusinessException(HttpServletResponse.SC_NOT_FOUND, "Order not found");
                }

                String oldStatus = order.getOrderStatus() != null ? order.getOrderStatus().toUpperCase() : "";
                Set<String> allowed = TRANSITIONS.getOrDefault(oldStatus, Set.of());
                if (!allowed.contains(newStatus)) {
                    throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST,
                            "Invalid status transition: " + oldStatus + " -> " + newStatus);
                }

                List<OrderItem> items = orderDAO.getItems(conn, orderId);

                if ("PENDING".equals(oldStatus) && "APPROVED".equals(newStatus)) {
                    for (OrderItem item : items) {
                        boolean ok = orderDAO.deductStock(conn, item.getProductId(), item.getQuantity());
                        if (!ok) {
                            String name = item.getProductName() != null
                                    ? item.getProductName()
                                    : productDAO.getProductName(conn, item.getProductId());
                            throw new BusinessException(HttpServletResponse.SC_CONFLICT,
                                    "Insufficient stock for " + name);
                        }
                    }

                    OrderDAO.ShopOrderContext shop = orderDAO.getShopContext(conn, order.getShopId());
                    if (shop != null && shop.creditLimit() != null
                            && shop.creditLimit().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal outstanding = orderDAO.sumOutstandingCredit(conn, order.getShopId());
                        BigDecimal projected = outstanding.add(order.getFinalAmount());
                        if (projected.compareTo(shop.creditLimit()) > 0) {
                            throw new BusinessException(HttpServletResponse.SC_CONFLICT,
                                    "Credit limit exceeded for this shop");
                        }
                    }
                }

                if (STOCK_HELD_STATUSES.contains(oldStatus) && "CANCELLED".equals(newStatus)) {
                    for (OrderItem item : items) {
                        orderDAO.restoreStock(conn, item.getProductId(), item.getQuantity());
                    }
                }

                orderDAO.updateStatus(conn, orderId, newStatus);
                orderDAO.insertStatusHistory(conn, orderId, oldStatus, newStatus, changedByUserId, comments);

                Order updated = orderDAO.getOrderHeader(conn, orderId);
                updated.setItems(orderDAO.getItems(conn, orderId));
                updated.setStatusHistory(orderDAO.getHistory(conn, orderId));
                conn.commit();
                return updated;
            } catch (BusinessException e) {
                conn.rollback();
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
