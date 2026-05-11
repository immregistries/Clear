package org.immregistries.clear.servlet;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.immregistries.clear.auth.ClearAuthSessionSupport;

public class LogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ClearAuthSessionSupport.clearSessionUser(req);
        resp.sendRedirect(req.getContextPath() + "/login");
    }
}
