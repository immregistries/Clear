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
        out.println("<!DOCTYPE html>");
        out.println("<html>");
        out.println("<head>");
        out.println("  <title>Access Denied - CLEAR</title>");
        out.println("  <link rel=\"stylesheet\" href=\"https://www.w3schools.com/w3css/4/w3.css\"/>");
        out.println("</head>");
        out.println("<body class=\"w3-container\">");
        out.println("  <h2>Unable to sign in</h2>");
        out.println("  <p>" + reason + "</p>");
        out.println("  <p>If you believe this is an error, contact your CLEAR administrator.</p>");
        out.println("  <p><a class=\"w3-button w3-green\" href=\"login\">Try again</a></p>");
        out.println("</body>");
        out.println("</html>");
        out.close();
    }
}
