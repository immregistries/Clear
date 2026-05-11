package org.immregistries.clear.servlet;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.immregistries.clear.auth.ClearAuthSessionSupport;
import org.immregistries.clear.auth.SessionUser;

public class LoginServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String code = req.getParameter("code");
        if (code == null || code.trim().isEmpty()) {
            ClearAuthSessionSupport.redirectToHubLogin(req, resp);
            return;
        }

        SessionUser sessionUser = ClearAuthSessionSupport.exchangeCodeAndAuthorize(req, code);
        if (!sessionUser.isAllowed()) {
            String reason = sessionUser.getDenialReason();
            if (reason == null || reason.trim().isEmpty()) {
                reason = "Login was not authorized for this application.";
            }
            resp.sendRedirect(ClearAuthSessionSupport.getAccessDeniedRedirect(req, reason));
            return;
        }

        ClearAuthSessionSupport.setSessionUser(req, sessionUser);
        String originalRequest = ClearAuthSessionSupport.consumeOriginalRequest(req);
        if (originalRequest != null) {
            resp.sendRedirect(originalRequest);
            return;
        }

        resp.sendRedirect(req.getContextPath() + "/clear");
    }
}
