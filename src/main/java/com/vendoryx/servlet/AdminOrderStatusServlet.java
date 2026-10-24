package com.vendoryx.servlet;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.vendoryx.model.Order;
import com.vendoryx.service.BusinessException;
import com.vendoryx.service.OrderService;
import com.vendoryx.util.CorsUtil;
import com.vendoryx.util.JsonUtil;
import com.vendoryx.util.RoleGuard;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/** PUT /api/admin/orders/status?id= */
public class AdminOrderStatusServlet extends HttpServlet {

    private final OrderService orderService = new OrderService();

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
            Integer id = JsonUtil.queryInt(request, "id");
            Integer userId = RoleGuard.getUserId(request);
            JsonObject body = JsonUtil.parseObject(JsonUtil.readBody(request));
            String newStatus = JsonUtil.textAny(body, "newStatus", "status");
            String comments = JsonUtil.text(body, "comments");

            if (id == null || id <= 0 || userId == null) {
                JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST, "id query param is required");
                return;
            }

            Order updated = orderService.changeStatus(id, newStatus, comments, userId);
            JsonUtil.writeSuccess(response, "Order status updated", updated);
        } catch (BusinessException e) {
            JsonUtil.writeError(response, e.getHttpStatus(), e.getMessage());
        } catch (JsonSyntaxException e) {
            JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON body");
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error: " + e.getMessage());
        }
    }
}
