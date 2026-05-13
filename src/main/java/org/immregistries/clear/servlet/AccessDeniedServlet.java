package org.immregistries.clear.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AccessDeniedServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String reason = req.getParameter("reason");
        if (reason == null || reason.trim().isEmpty()) {
            reason = "Your account is not authorized to use CLEAR.";
        }

        resp.setContentType("text/html");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        PageShellSupport.printPageStart(
                out,
                "Access Denied - CLEAR",
                "/clear/login",
                null,
                java.util.Collections.<PageShellSupport.NavItem>emptyList());
        out.println("  <h2>Unable to sign in</h2>");
        out.println("  <p>" + reason + "</p>");
        out.println("  <p>If you believe this is an error, contact your CLEAR administrator.</p>");
        out.println("  <p><a class=\"btn btn-primary\" href=\"login\">Try again</a></p>");
        PageShellSupport.printPageEnd(out, true, false);
        out.close();
    }
}
