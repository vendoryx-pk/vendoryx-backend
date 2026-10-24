package com.vendoryx.servlet;

import com.vendoryx.dao.OrderDAO;
import com.vendoryx.model.Order;
import com.vendoryx.util.CorsUtil;
import com.vendoryx.util.JsonUtil;
import com.vendoryx.util.RoleGuard;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** GET /api/admin/orders — ADMIN/DEVELOPER */
public class AdminOrderServlet extends HttpServlet {

    private final OrderDAO orderDAO = new OrderDAO();

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
                Order order = orderDAO.getDetail(id);
                if (order == null) {
                    JsonUtil.writeError(response, HttpServletResponse.SC_NOT_FOUND, "Order not found");
                    return;
                }
                JsonUtil.writeSuccess(response, "Order loaded", order);
                return;
            }

            String status = JsonUtil.queryText(request, "status");
            Integer cityId = JsonUtil.queryInt(request, "cityId");
            Integer dayId = JsonUtil.queryInt(request, "dayId");
            LocalDate from = parseDate(JsonUtil.queryText(request, "from"));
            LocalDate to = parseDate(JsonUtil.queryText(request, "to"));
            int page = JsonUtil.pageOrDefault(request, 1);
            int size = JsonUtil.sizeOrDefault(request, 20, 100);

            JsonUtil.writeSuccess(response, "Orders loaded",
                    orderDAO.listAdmin(status, cityId, dayId, from, to, page, size));
        } catch (DateTimeParseException e) {
            JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "from/to must be YYYY-MM-DD");
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error: " + e.getMessage());
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDate.parse(raw);
    }
}
