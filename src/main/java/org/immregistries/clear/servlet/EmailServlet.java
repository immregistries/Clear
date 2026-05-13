package org.immregistries.clear.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Random;

import org.hibernate.Transaction;
import org.immregistries.clear.utils.HibernateUtil;
import org.immregistries.clear.auth.ClearAuthSessionSupport;
import org.immregistries.clear.auth.SessionUser;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.immregistries.clear.ClearConfig;
import org.immregistries.clear.model.Jurisdiction;
import org.immregistries.clear.model.ValidationCode;
import org.immregistries.clear.service.EmailService;
import org.immregistries.clear.utils.SystemSettingSupport;

public class EmailServlet extends HttpServlet {

    public static final String PARAM_VIEW = "view";
    public static final String VIEW_MAP = "map";
    public static final String VIEW_DATA = "data";
    private static final String CLEAR_EXTERNAL_URL_SETTING_KEY = "clear.external.url";

    private final EmailService emailService = new EmailService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/html");
        System.out.println("--> calling doGet");

        PrintWriter out = new PrintWriter(resp.getOutputStream());
        try {
            SessionUser sessionUser = ClearAuthSessionSupport.getSessionUser(req);
            System.out.println("--> printing header");
            printHeader(out, sessionUser);
            out.println("<h1>Send Email</h1>");

            String message = req.getParameter("message");
            if (message != null && !message.trim().isEmpty()) {
                String panelClass = "1".equals(req.getParameter("error")) ? "alert-error" : "alert-success";
                out.println("<div class=\"alert-panel " + panelClass + "\">" + escapeHtml(message) + "</div>");
            }

            out.println("<form method=\"POST\">");
            out.println("   <input id=\"emailInput\" type=\"email\" name=\"emailInput\">");
            out.println("      <label for=\"emailInput\">Recipient</label></br></br>");
            out.println("   <select id=\"jurisdictionInput\" name=\"jurisdictionInput\">");
            for (String user : ClearServlet.populationMap.keySet()) {
                out.println("       <option value=\"" + user + "\">" + user + "</option>");
            }
            out.println("   </select>");
            out.println("      <label for=\"jurisdictionInput\">jurisdiction</label></br></br>");
            out.println("   <input class=\"btn\" type=\"submit\" value=\"Submit\">");
            out.println("</form>");

            System.out.println("--> printing footer");
            printFooter(out);
        } catch (Exception e) {
            System.out.println("--> exception!");
            e.printStackTrace();
        } finally {
            out.close();
            System.out.println("--> finished doGet");
        }
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String to = req.getParameter("emailInput");
        String jurisdiction = req.getParameter("jurisdictionInput");

        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = session.beginTransaction();
        boolean txCommitted = false;
        try {
            Random rand = new Random();
            Query<Jurisdiction> jurQuery = session.createQuery("FROM Jurisdiction WHERE mapLink = :jurisdiction",
                    Jurisdiction.class);
            jurQuery.setParameter("jurisdiction", jurisdiction);
            ValidationCode vc = new ValidationCode();

            vc.setJurisdictionId(jurQuery.getResultList().get(0).getJurisdictionId());
            vc.setIssueDate(new Date());
            vc.setAccessCode((int) (rand.nextFloat() * 999999));
            session.save(vc);
            tx.commit();
            txCommitted = true;

            String formattedJurisdiction = jurisdiction.replace(' ', '-');
            String clearExternalUrl = SystemSettingSupport.getValueOrDefault(
                    CLEAR_EXTERNAL_URL_SETTING_KEY,
                    ClearConfig.CLEAR_EXTERNAL_URL);
            if (clearExternalUrl == null || clearExternalUrl.trim().isEmpty()) {
                clearExternalUrl = "http://localhost:8080/clear";
            }
            String normalizedBaseUrl = clearExternalUrl.endsWith("/")
                    ? clearExternalUrl.substring(0, clearExternalUrl.length() - 1)
                    : clearExternalUrl;
            String clearUrl = normalizedBaseUrl + "/dashboard?view=data&jurisdiction="
                    + formattedJurisdiction + "&access_code=" + vc.getAccessCode();

            String body = "enter clear\n" + clearUrl;
            emailService.sendPlainTextEmail(to, "CLEAR", body);
            resp.sendRedirect(req.getContextPath() + "/email?message="
                    + URLEncoder.encode("Email sent.", StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            if (!txCommitted && tx != null) {
                tx.rollback();
            }
            resp.sendRedirect(req.getContextPath() + "/email?error=1&message="
                    + URLEncoder.encode("Unable to send email. Verify SMTP settings in Admin.",
                            StandardCharsets.UTF_8));
        } finally {
            session.close();
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    protected void printHeader(PrintWriter out, SessionUser sessionUser) {
        PageShellSupport.printAuthenticatedPageStart(
                out,
                "CLEAR - Community Led Exchange and Aggregate Reporting",
                sessionUser,
                null);
    }

    protected void printFooter(PrintWriter out) {
        PageShellSupport.printAuthenticatedPageEnd(out);
    }

}
