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
        out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        out.println("  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">");
        out.println("  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>");
        out.println(
                "  <link href=\"https://fonts.googleapis.com/css?family=Open+Sans:400,700|Roboto:400,700\" rel=\"stylesheet\">");
        out.println("  <link rel=\"stylesheet\" href=\"/clear/css/clear-brand.css\"/>");
        out.println("</head>");
        out.println("<body class=\"app-body\">");
        out.println("<header class=\"app-header\">");
        out.println("  <div class=\"app-header-inner\">");
        out.println("    <a class=\"app-brand\" href=\"/clear/login\">");
        out.println("      <img class=\"app-brand-logo\" src=\"/clear/images/aira_logo.webp\" alt=\"AIRA\">");
        out.println("      <span>CLEAR</span>");
        out.println("    </a>");
        out.println("  </div>");
        out.println("</header>");
        out.println("<main class=\"app-main\">");
        out.println("  <h2>Unable to sign in</h2>");
        out.println("  <p>" + reason + "</p>");
        out.println("  <p>If you believe this is an error, contact your CLEAR administrator.</p>");
        out.println("  <p><a class=\"btn btn-primary\" href=\"login\">Try again</a></p>");
        out.println("</main>");
        out.println("</body>");
        out.println("</html>");
        out.close();
    }
}
