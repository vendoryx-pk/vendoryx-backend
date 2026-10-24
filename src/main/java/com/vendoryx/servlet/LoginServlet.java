package com.vendoryx.servlet;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.vendoryx.dao.UserDAO;
import com.vendoryx.model.User;
import com.vendoryx.util.CorsUtil;
import com.vendoryx.util.JsonUtil;
import com.vendoryx.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POST /api/auth/login
 * Body: { "username": "...", "password": "..." }
 */
public class LoginServlet extends HttpServlet {

    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.apply(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.apply(response);

        try {
            JsonObject body = JsonUtil.parseObject(JsonUtil.readBody(request));
            String username = JsonUtil.text(body, "username");
            String password = JsonUtil.text(body, "password");

            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Username and password are required");
                return;
            }

            User user = userDAO.authenticate(username, password);
            if (user == null) {
                JsonUtil.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "Invalid username or password");
                return;
            }

            List<Integer> shopIds = userDAO.getActiveShopIds(user.getUserId());
            userDAO.updateLastLogin(user.getUserId());

            String token = JwtUtil.createToken(user.getUserId(), user.getRole(), shopIds);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = JsonUtil.gson().fromJson(JsonUtil.gson().toJson(user), LinkedHashMap.class);
            data.put("token", token);
            data.put("shopIds", shopIds);

            JsonUtil.writeSuccess(response, "Login successful", data);

        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON body");
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Server error: " + e.getMessage());
        }
    }
}
