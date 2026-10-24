package com.vendoryx.servlet;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.vendoryx.dao.UserDAO;
import com.vendoryx.model.User;
import com.vendoryx.util.CorsUtil;
import com.vendoryx.util.JsonUtil;
import com.vendoryx.util.PasswordUtil;
import com.vendoryx.util.RoleGuard;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * PUT|POST /api/users/update
 * userId comes from JWT (not body).
 * Password change requires currentPassword + new password; new password stored as BCrypt.
 */
public class UserUpdateServlet extends HttpServlet {

    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.apply(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handleUpdate(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handleUpdate(request, response);
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.apply(request, response);

        try {
            Integer userId = RoleGuard.getUserId(request);
            if (userId == null || userId <= 0) {
                JsonUtil.writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                return;
            }

            JsonObject body = JsonUtil.parseObject(JsonUtil.readBody(request));
            String fullName = JsonUtil.textAny(body, "fullName", "full_name");
            String email = JsonUtil.text(body, "email");
            String newPassword = JsonUtil.textAny(body, "password", "newPassword");
            String currentPassword = JsonUtil.textAny(body, "currentPassword", "oldPassword");

            boolean hasFullName = fullName != null && !fullName.isBlank();
            boolean hasEmail = email != null && !email.isBlank();
            boolean hasPassword = newPassword != null && !newPassword.isBlank();

            if (!hasFullName && !hasEmail && !hasPassword) {
                JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Provide fullName, email, and/or password to update");
                return;
            }

            String newPasswordHash = null;
            if (hasPassword) {
                if (currentPassword == null || currentPassword.isBlank()) {
                    JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                            "currentPassword is required to change password");
                    return;
                }
                String storedHash = userDAO.getPasswordHash(userId);
                if (!PasswordUtil.verify(currentPassword, storedHash)) {
                    JsonUtil.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "Invalid username or password");
                    return;
                }
                newPasswordHash = PasswordUtil.hash(newPassword);
            }

            User updated = userDAO.updateProfile(
                    userId,
                    hasFullName ? fullName : null,
                    hasEmail ? email : null,
                    newPasswordHash
            );

            if (updated == null) {
                JsonUtil.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                        "User not found — update not success");
                return;
            }

            String message = hasPassword
                    ? "Password updated successfully"
                    : "Profile updated successfully";
            JsonUtil.writeSuccess(response, message, updated);

        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            JsonUtil.writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON body");
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error — update not success: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Server error — update not success: " + e.getMessage());
        }
    }
}
