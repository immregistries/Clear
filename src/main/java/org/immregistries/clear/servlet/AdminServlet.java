package org.immregistries.clear.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.clear.SoftwareVersion;
import org.immregistries.clear.auth.ClearAuthSessionSupport;
import org.immregistries.clear.auth.SessionUser;
import org.immregistries.clear.model.SystemSetting;
import org.immregistries.clear.service.EmailService;
import org.immregistries.clear.utils.HibernateUtil;
import org.immregistries.clear.utils.SystemSettingSupport;

public class AdminServlet extends HttpServlet {

    private static final String PARAM_SEND_TEST_EMAIL = "sendTestEmail";

    private static final String KEY_HUB_EXTERNAL_URL = "hub.external.url";
    private static final String KEY_CLEAR_EXTERNAL_URL = "clear.external.url";
    private static final String KEY_SMTP_HOST = EmailService.SMTP_HOST_KEY;
    private static final String KEY_SMTP_PORT = EmailService.SMTP_PORT_KEY;
    private static final String KEY_SMTP_USERNAME = EmailService.SMTP_USERNAME_KEY;
    private static final String KEY_SMTP_PASSWORD = EmailService.SMTP_PASSWORD_KEY;
    private static final String KEY_SMTP_FROM_EMAIL = EmailService.SMTP_FROM_EMAIL_KEY;
    private static final String KEY_SMTP_FROM_NAME = EmailService.SMTP_FROM_NAME_KEY;
    private static final String KEY_SMTP_AUTH = EmailService.SMTP_AUTH_KEY;
    private static final String KEY_SMTP_STARTTLS = EmailService.SMTP_STARTTLS_KEY;
    private static final String KEY_SMTP_SSL = EmailService.SMTP_SSL_KEY;

    private final EmailService emailService = new EmailService();

    private static final SettingDefinition[] SETTING_DEFINITIONS = new SettingDefinition[] {
            new SettingDefinition(
                    KEY_HUB_EXTERNAL_URL,
                    "Interop Hub URL",
                    "External Hub URL used for login and code exchange.",
                    InputType.TEXT,
                    null),
            new SettingDefinition(
                    KEY_CLEAR_EXTERNAL_URL,
                    "CLEAR External URL",
                    "Public CLEAR URL used for callback and redirect generation.",
                    InputType.TEXT,
                    null),
            new SettingDefinition(
                    KEY_SMTP_HOST,
                    "SMTP Host",
                    "SMTP server hostname.",
                    InputType.TEXT,
                    null),
            new SettingDefinition(
                    KEY_SMTP_PORT,
                    "SMTP Port",
                    "SMTP server port, usually 587 or 465.",
                    InputType.NUMBER,
                    EmailService.DEFAULT_SMTP_PORT),
            new SettingDefinition(
                    KEY_SMTP_USERNAME,
                    "SMTP Username",
                    "SMTP authentication username.",
                    InputType.TEXT,
                    null),
            new SettingDefinition(
                    KEY_SMTP_PASSWORD,
                    "SMTP Password",
                    "SMTP authentication password.",
                    InputType.PASSWORD,
                    null),
            new SettingDefinition(
                    KEY_SMTP_FROM_EMAIL,
                    "From Email",
                    "Email address used in the From header.",
                    InputType.TEXT,
                    null),
            new SettingDefinition(
                    KEY_SMTP_FROM_NAME,
                    "From Name",
                    "Display name used in the From header.",
                    InputType.TEXT,
                    "CLEAR"),
            new SettingDefinition(
                    KEY_SMTP_AUTH,
                    "SMTP Auth",
                    "Use SMTP username and password.",
                    InputType.CHECKBOX,
                    EmailService.DEFAULT_SMTP_AUTH),
            new SettingDefinition(
                    KEY_SMTP_STARTTLS,
                    "SMTP STARTTLS",
                    "Enable STARTTLS encryption.",
                    InputType.CHECKBOX,
                    EmailService.DEFAULT_SMTP_STARTTLS),
            new SettingDefinition(
                    KEY_SMTP_SSL,
                    "SMTP SSL",
                    "Enable SSL/TLS SMTP mode.",
                    InputType.CHECKBOX,
                    EmailService.DEFAULT_SMTP_SSL)
    };

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        SessionUser sessionUser = requireAdmin(req, resp);
        if (sessionUser == null) {
            return;
        }

        Map<String, String> values = loadSettingValues();
        String message = "1".equals(req.getParameter("saved"))
                ? "System settings were saved."
                : null;
        renderPage(resp, sessionUser, values, message, false);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        SessionUser sessionUser = requireAdmin(req, resp);
        if (sessionUser == null) {
            return;
        }

        try {
            saveSettingValues(req);

            boolean sendTestEmail = req.getParameter(PARAM_SEND_TEST_EMAIL) != null;
            if (sendTestEmail) {
                emailService.sendTestEmail(sessionUser.getEmail());
                Map<String, String> values = loadSettingValues();
                renderPage(resp, sessionUser, values,
                        "System settings were saved. Test email sent to " + sessionUser.getEmail() + ".",
                        false);
                return;
            }

            resp.sendRedirect(req.getContextPath() + "/clear/admin?saved=1");
        } catch (Exception e) {
            Map<String, String> values = loadSettingValues();
            renderPage(resp, sessionUser, values,
                    "Unable to save system settings: " + e.getMessage(),
                    true);
        }
    }

    private SessionUser requireAdmin(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        SessionUser sessionUser = ClearAuthSessionSupport.getSessionUser(req);
        if (sessionUser == null || !sessionUser.isAdmin()) {
            resp.sendRedirect(req.getContextPath() + "/access-denied");
            return null;
        }
        return sessionUser;
    }

    private Map<String, String> loadSettingValues() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            for (SettingDefinition definition : SETTING_DEFINITIONS) {
                SystemSetting setting = session.get(SystemSetting.class, definition.key);
                String settingValue = setting == null
                        ? definition.defaultValue
                        : safe(setting.getSettingValue());
                values.put(definition.key, safe(settingValue));
            }
            return values;
        } finally {
            session.close();
        }
    }

    private void saveSettingValues(HttpServletRequest req) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = session.beginTransaction();
        try {
            for (SettingDefinition definition : SETTING_DEFINITIONS) {
                String incoming = getIncomingValue(req, definition);
                SystemSetting setting = session.get(SystemSetting.class, definition.key);
                if (incoming == null) {
                    if (setting != null) {
                        session.remove(setting);
                    }
                } else {
                    if (setting == null) {
                        setting = new SystemSetting();
                        setting.setSettingKey(definition.key);
                    }
                    setting.setSettingValue(incoming);
                    session.saveOrUpdate(setting);
                }
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx != null) {
                tx.rollback();
            }
            throw e;
        } finally {
            session.close();
        }
    }

    private void renderPage(HttpServletResponse resp, SessionUser sessionUser,
            Map<String, String> values, String message, boolean error)
            throws IOException {
        resp.setContentType("text/html");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        try {
            printHeader(out, sessionUser);
            out.println("<h2>Admin</h2>");
            out.println("<h3>System Settings</h3>");
            out.println("<p>Manage system-level configuration values.</p>");

            if (message != null && !message.isEmpty()) {
                String panelClass = error ? "w3-pale-red" : "w3-pale-green";
                out.println("<div class=\"w3-panel " + panelClass + "\">" + escapeHtml(message) + "</div>");
            }

            out.println("<form method=\"POST\" class=\"w3-container\">");
            out.println("  <table class=\"w3-table w3-bordered w3-striped\">");
            out.println("    <tr><th>Setting</th><th>Value</th><th>Description</th></tr>");
            for (SettingDefinition definition : SETTING_DEFINITIONS) {
                out.println("    <tr>");
                out.println("      <td><strong>" + escapeHtml(definition.label)
                        + "</strong><br><code>" + escapeHtml(definition.key) + "</code></td>");
                out.println("      <td>" + renderInput(definition, safe(values.get(definition.key))) + "</td>");
                out.println("      <td>" + escapeHtml(definition.description) + "</td>");
                out.println("    </tr>");
            }
            out.println("  </table>");
            out.println("  <p>");
            out.println("    <label>");
            out.println("      <input class=\"w3-check\" type=\"checkbox\" name=\"" + PARAM_SEND_TEST_EMAIL
                    + "\" value=\"1\"> Send test email to " + escapeHtml(sessionUser.getEmail()));
            out.println("    </label>");
            out.println("  </p>");
            out.println("  <p><button class=\"w3-button w3-green\" type=\"submit\">Save Settings</button></p>");
            out.println("</form>");
            printFooter(out);
        } finally {
            out.close();
        }
    }

    private void printHeader(PrintWriter out, SessionUser sessionUser) {
        out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\">");
        out.println("<html>");
        out.println("  <head>");
        out.println("    <title>CLEAR - Community Led Exchange and Aggregate Reporting</title>");
        out.println("    <link rel=\"stylesheet\" href=\"https://www.w3schools.com/w3css/4/w3.css\"/>");
        out.println("  </head>");
        out.println("  <body>");

        out.println("    <header class=\"w3-container w3-green\">");
        out.println("      <div class=\"w3-bar\">");
        out.println("        <h1>CLEAR - Community Led Exchange and Aggregate Reporting</h1> ");
        out.println("        <a href=\"/clear/clear?view=data\" class=\"w3-bar-item w3-button\">Data</a> ");
        out.println("        <a href=\"/clear/clear?view=map\" class=\"w3-bar-item w3-button\">Map</a> ");
        out.println("        <a href=\"/clear/email\" class=\"w3-bar-item w3-button\">Mail</a> ");
        out.println("        <a href=\"\" class=\"w3-bar-item w3-button\">Admin</a> ");
        out.println("        <a href=\"/clear/logout\" class=\"w3-bar-item w3-button\">Logout</a> ");
        out.println("      </div>");
        out.println("      <div class=\"w3-small\">Signed in as " + escapeHtml(sessionUser.getDisplayName())
                + " (" + escapeHtml(sessionUser.getEmail()) + ")"
                + (sessionUser.isAdmin() ? " - Admin" : "") + "</div>");
        out.println("    </header>");
        out.println("    <div class=\"w3-container\">");
    }

    private void printFooter(PrintWriter out) {
        out.println("   </div>");
        out.println("  <div class=\"w3-container w3-green\">");
        out.println("      <p>CLEAR " + SoftwareVersion.VERSION + " - ");
        out.println(
                "      <a href=\"https://aira.memberclicks.net/assets/docs/Organizational_Docs/AIRA%20Privacy%20Policy%20-%20Final%202024_.pdf\" class=\"underline\">AIRA Privacy Policy</a> - ");
        out.println(
                "      <a href=\"https://github.com/ImmRegistries/Clear\" class=\"underline\">Source Code</a></p>");
        out.println("  </div>");
        out.println("  </body>");
        out.println("</html>");
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String getIncomingValue(HttpServletRequest req, SettingDefinition definition) {
        if (definition.inputType == InputType.CHECKBOX) {
            return req.getParameter(definition.key) == null ? "false" : "true";
        }
        return normalize(req.getParameter(definition.key));
    }

    private String renderInput(SettingDefinition definition, String value) {
        if (definition.inputType == InputType.CHECKBOX) {
            boolean checked = SystemSettingSupport.parseBoolean(value,
                    SystemSettingSupport.parseBoolean(definition.defaultValue, false));
            return "<input class=\"w3-check\" type=\"checkbox\" name=\""
                    + escapeHtml(definition.key) + "\" value=\"true\""
                    + (checked ? " checked" : "") + ">";
        }

        String htmlType = "text";
        if (definition.inputType == InputType.PASSWORD) {
            htmlType = "password";
        } else if (definition.inputType == InputType.NUMBER) {
            htmlType = "number";
        }

        return "<input class=\"w3-input\" type=\"" + htmlType + "\" name=\""
                + escapeHtml(definition.key) + "\" value=\""
                + escapeHtml(safe(value)) + "\">";
    }

    private String safe(String value) {
        return value == null ? "" : value;
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

    private static class SettingDefinition {
        private final String key;
        private final String label;
        private final String description;
        private final InputType inputType;
        private final String defaultValue;

        private SettingDefinition(String key, String label, String description,
                InputType inputType, String defaultValue) {
            this.key = key;
            this.label = label;
            this.description = description;
            this.inputType = inputType;
            this.defaultValue = defaultValue;
        }
    }

    private enum InputType {
        TEXT,
        NUMBER,
        PASSWORD,
        CHECKBOX
    }
}
