package org.immregistries.clear.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.hibernate.query.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.clear.SoftwareVersion;
import org.immregistries.clear.auth.ClearAuthSessionSupport;
import org.immregistries.clear.auth.SessionUser;
import org.immregistries.clear.model.Contact;
import org.immregistries.clear.model.EntryForInterop;
import org.immregistries.clear.model.Jurisdiction;
import org.immregistries.clear.model.SystemSetting;
import org.immregistries.clear.service.EmailService;
import org.immregistries.clear.utils.HibernateUtil;
import org.immregistries.clear.utils.SystemSettingSupport;

@MultipartConfig
public class AdminServlet extends HttpServlet {

    private static final String PARAM_SEND_TEST_EMAIL = "sendTestEmail";
    private static final String PARAM_ACTION = "action";
    private static final String ACTION_DOWNLOAD_ENTRY_FOR_INTEROP = "downloadEntryForInterop";
    private static final String ACTION_UPLOAD_ENTRY_FOR_INTEROP = "uploadEntryForInterop";
    private static final String PARAM_ENTRY_FOR_INTEROP_FILE = "entryForInteropFile";

    private static final String CSV_HEADER_REPORTING_PERIOD = "Date time of reporting period";
    private static final String CSV_HEADER_UPDATE_COUNT = "Update count";
    private static final String CSV_HEADER_QUERY_COUNT = "Query count";
    private static final String CSV_HEADER_CONTACT_EMAIL = "Contact Email";
    private static final String CSV_HEADER_CONTACT_FIRST_NAME = "Contact First Name";
    private static final String CSV_HEADER_CONTACT_LAST_NAME = "Contact Last Name";
    private static final String CSV_HEADER_JURISDICTION_MAP_LINK = "Jurisdiction (mapLink)";

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

        String action = normalize(req.getParameter(PARAM_ACTION));
        if (ACTION_DOWNLOAD_ENTRY_FOR_INTEROP.equals(action)) {
            downloadEntryForInteropCsv(resp);
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

        String action = normalize(req.getParameter(PARAM_ACTION));
        if (ACTION_UPLOAD_ENTRY_FOR_INTEROP.equals(action)) {
            handleEntryForInteropUpload(req, resp, sessionUser);
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

    private void downloadEntryForInteropCsv(HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("text/csv");
        resp.setHeader("Content-Disposition", "attachment; filename=\"entryForInterop.csv\"");

        Session session = HibernateUtil.getSessionFactory().openSession();
        PrintWriter out = new PrintWriter(resp.getOutputStream(), false, StandardCharsets.UTF_8);
        try {
            out.println(toCsvRow(CSV_HEADER_REPORTING_PERIOD,
                    CSV_HEADER_UPDATE_COUNT,
                    CSV_HEADER_QUERY_COUNT,
                    CSV_HEADER_CONTACT_EMAIL,
                    CSV_HEADER_CONTACT_FIRST_NAME,
                    CSV_HEADER_CONTACT_LAST_NAME,
                    CSV_HEADER_JURISDICTION_MAP_LINK));

            Query<EntryForInterop> query = session.createQuery(
                    "FROM EntryForInterop ORDER BY reportingPeriod, entryForInteropId",
                    EntryForInterop.class);
            List<EntryForInterop> entries = query.list();
            for (EntryForInterop entry : entries) {
                String reportingPeriod = formatReportingPeriod(entry.getReportingPeriod());
                String countUpdate = Integer.toString(entry.getCountUpdate());
                String countQuery = Integer.toString(entry.getCountQuery());
                String contactEmail = "";
                String contactFirstName = "";
                String contactLastName = "";
                if (entry.getContactId() > 0) {
                    Contact contact = session.get(Contact.class, entry.getContactId());
                    if (contact != null) {
                        contactEmail = safe(contact.getEmailAddress());
                        contactFirstName = safe(contact.getNameFirst());
                        contactLastName = safe(contact.getNameLast());
                    }
                }
                String jurisdictionMapLink = entry.getJurisdiction() == null
                        ? ""
                        : safe(entry.getJurisdiction().getMapLink());

                out.println(toCsvRow(reportingPeriod, countUpdate, countQuery, contactEmail,
                        contactFirstName, contactLastName, jurisdictionMapLink));
            }
        } finally {
            out.flush();
            session.close();
        }
    }

    private void handleEntryForInteropUpload(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException, ServletException {
        Map<String, String> values = loadSettingValues();
        Part csvPart = req.getPart(PARAM_ENTRY_FOR_INTEROP_FILE);
        if (csvPart == null || csvPart.getSize() == 0) {
            renderPage(resp, sessionUser, values, "Unable to upload CSV: Please choose a CSV file.", true);
            return;
        }

        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            Map<String, Jurisdiction> jurisdictionByMapLink = loadJurisdictionByMapLink(session);
            ParseResult parseResult = parseUploadRows(csvPart, jurisdictionByMapLink);
            if (!parseResult.errors.isEmpty()) {
                throw new IllegalArgumentException("CSV validation failed: " + String.join(" ", parseResult.errors));
            }

            Map<String, Contact> contactByEmail = loadContactByEmail(session);
            tx = session.beginTransaction();

            int insertedCount = 0;
            int updatedCount = 0;
            int createdContactCount = 0;
            for (UploadRow row : parseResult.rows) {
                Contact contact = contactByEmail.get(row.contactEmailKey);
                if (contact == null) {
                    contact = createContact(session, row.contactEmail,
                            row.contactFirstName, row.contactLastName, row.jurisdiction);
                    contactByEmail.put(row.contactEmailKey, contact);
                    createdContactCount++;
                }

                EntryForInterop existingEntry = findEntryForInterop(session, row.jurisdiction, row.reportingPeriod);
                if (existingEntry == null) {
                    EntryForInterop newEntry = new EntryForInterop();
                    newEntry.setReportingPeriod(row.reportingPeriod);
                    newEntry.setCountUpdate(row.updateCount);
                    newEntry.setCountQuery(row.queryCount);
                    newEntry.setJurisdiction(row.jurisdiction);
                    newEntry.setContactId(contact.getContactId());
                    session.save(newEntry);
                    insertedCount++;
                } else {
                    existingEntry.setReportingPeriod(row.reportingPeriod);
                    existingEntry.setCountUpdate(row.updateCount);
                    existingEntry.setCountQuery(row.queryCount);
                    existingEntry.setJurisdiction(row.jurisdiction);
                    existingEntry.setContactId(contact.getContactId());
                    session.update(existingEntry);
                    updatedCount++;
                }
            }

            tx.commit();
            String message = "CSV upload completed. Inserted " + insertedCount
                    + " entries, updated " + updatedCount
                    + " entries, created " + createdContactCount + " contacts.";
            renderPage(resp, sessionUser, values, message, false);
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderPage(resp, sessionUser, values, "Unable to upload CSV: " + e.getMessage(), true);
        } finally {
            session.close();
        }
    }

    private ParseResult parseUploadRows(Part csvPart,
            Map<String, Jurisdiction> jurisdictionByMapLink) throws IOException {
        ParseResult parseResult = new ParseResult();
        boolean headerProcessed = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvPart.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.length() > 0 && line.charAt(0) == '\ufeff') {
                    line = line.substring(1);
                }
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> columns = parseCsvLine(line);
                if (!headerProcessed) {
                    headerProcessed = true;
                    validateHeader(columns, parseResult.errors);
                    continue;
                }

                if (columns.size() != 7) {
                    parseResult.errors.add("Line " + lineNumber + ": Expected 7 columns but found "
                            + columns.size() + ".");
                    continue;
                }

                String reportingPeriodText = normalize(columns.get(0));
                String updateCountText = normalize(columns.get(1));
                String queryCountText = normalize(columns.get(2));
                String contactEmail = normalize(columns.get(3));
                String contactFirstName = normalize(columns.get(4));
                String contactLastName = normalize(columns.get(5));
                String mapLink = normalize(columns.get(6));

                // Excel can emit blank rows like ",,,,,,". Ignore rows with no reporting date
                // or no numeric values to report.
                if (reportingPeriodText == null || (updateCountText == null && queryCountText == null)) {
                    continue;
                }

                if (reportingPeriodText == null || updateCountText == null || queryCountText == null
                        || contactEmail == null || mapLink == null) {
                    parseResult.errors.add("Line " + lineNumber + ": Reporting period, update count, query count,"
                            + " contact email, and jurisdiction mapLink must be populated.");
                    continue;
                }

                Jurisdiction jurisdiction = jurisdictionByMapLink.get(mapLink.toLowerCase(Locale.ROOT));
                if (jurisdiction == null) {
                    parseResult.errors.add("Line " + lineNumber + ": Invalid jurisdiction mapLink '"
                            + mapLink + "'. Upload aborted.");
                    continue;
                }

                Date reportingPeriod;
                try {
                    reportingPeriod = normalizeReportingPeriod(parseReportingPeriod(reportingPeriodText));
                } catch (IllegalArgumentException e) {
                    parseResult.errors.add("Line " + lineNumber + ": " + e.getMessage());
                    continue;
                }

                int updateCount;
                int queryCount;
                try {
                    updateCount = parseCount(updateCountText, "Update count", lineNumber);
                    queryCount = parseCount(queryCountText, "Query count", lineNumber);
                } catch (IllegalArgumentException e) {
                    parseResult.errors.add(e.getMessage());
                    continue;
                }

                UploadRow uploadRow = new UploadRow();
                uploadRow.reportingPeriod = reportingPeriod;
                uploadRow.updateCount = updateCount;
                uploadRow.queryCount = queryCount;
                uploadRow.contactEmail = contactEmail;
                uploadRow.contactEmailKey = contactEmail.toLowerCase(Locale.ROOT);
                uploadRow.contactFirstName = contactFirstName;
                uploadRow.contactLastName = contactLastName;
                uploadRow.jurisdiction = jurisdiction;
                parseResult.rows.add(uploadRow);
            }
        }

        if (!headerProcessed) {
            parseResult.errors.add("CSV is empty. Include a header row and at least one data row.");
        } else if (parseResult.rows.isEmpty() && parseResult.errors.isEmpty()) {
            parseResult.errors.add("CSV did not contain any data rows.");
        }
        return parseResult;
    }

    private void validateHeader(List<String> columns, List<String> errors) {
        if (columns.size() != 7) {
            errors.add("Header must have exactly 7 columns.");
            return;
        }
        if (!CSV_HEADER_REPORTING_PERIOD.equalsIgnoreCase(normalize(columns.get(0)))) {
            errors.add("Header column 1 must be '" + CSV_HEADER_REPORTING_PERIOD + "'.");
        }
        if (!CSV_HEADER_UPDATE_COUNT.equalsIgnoreCase(normalize(columns.get(1)))) {
            errors.add("Header column 2 must be '" + CSV_HEADER_UPDATE_COUNT + "'.");
        }
        if (!CSV_HEADER_QUERY_COUNT.equalsIgnoreCase(normalize(columns.get(2)))) {
            errors.add("Header column 3 must be '" + CSV_HEADER_QUERY_COUNT + "'.");
        }
        if (!CSV_HEADER_CONTACT_EMAIL.equalsIgnoreCase(normalize(columns.get(3)))) {
            errors.add("Header column 4 must be '" + CSV_HEADER_CONTACT_EMAIL + "'.");
        }
        if (!CSV_HEADER_CONTACT_FIRST_NAME.equalsIgnoreCase(normalize(columns.get(4)))) {
            errors.add("Header column 5 must be '" + CSV_HEADER_CONTACT_FIRST_NAME + "'.");
        }
        if (!CSV_HEADER_CONTACT_LAST_NAME.equalsIgnoreCase(normalize(columns.get(5)))) {
            errors.add("Header column 6 must be '" + CSV_HEADER_CONTACT_LAST_NAME + "'.");
        }
        if (!CSV_HEADER_JURISDICTION_MAP_LINK.equalsIgnoreCase(normalize(columns.get(6)))) {
            errors.add("Header column 7 must be '" + CSV_HEADER_JURISDICTION_MAP_LINK + "'.");
        }
    }

    private Map<String, Jurisdiction> loadJurisdictionByMapLink(Session session) {
        Map<String, Jurisdiction> jurisdictionByMapLink = new HashMap<String, Jurisdiction>();
        Query<Jurisdiction> query = session.createQuery("FROM Jurisdiction", Jurisdiction.class);
        for (Jurisdiction jurisdiction : query.list()) {
            String mapLink = normalize(jurisdiction.getMapLink());
            if (mapLink != null) {
                jurisdictionByMapLink.put(mapLink.toLowerCase(Locale.ROOT), jurisdiction);
            }
        }
        return jurisdictionByMapLink;
    }

    private Map<String, Contact> loadContactByEmail(Session session) {
        Map<String, Contact> contactByEmail = new HashMap<String, Contact>();
        Query<Contact> query = session.createQuery("FROM Contact", Contact.class);
        for (Contact contact : query.list()) {
            String email = normalize(contact.getEmailAddress());
            if (email != null) {
                contactByEmail.put(email.toLowerCase(Locale.ROOT), contact);
            }
        }
        return contactByEmail;
    }

    private Contact createContact(Session session, String emailAddress,
            String firstName, String lastName, Jurisdiction jurisdiction) {
        Contact contact = new Contact();
        contact.setEmailAddress(emailAddress);
        contact.setNameFirst(firstName == null ? "" : firstName);
        contact.setNameLast(lastName == null ? "" : lastName);
        contact.setDateCreated(new Date());
        contact.setDateAccess(new Date());
        contact.setJurisdictionId(jurisdiction.getJurisdictionId());
        session.save(contact);
        return contact;
    }

    private EntryForInterop findEntryForInterop(Session session, Jurisdiction jurisdiction, Date reportingPeriod) {
        Calendar start = Calendar.getInstance();
        start.setTime(normalizeReportingPeriod(reportingPeriod));
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);

        Query<EntryForInterop> query = session.createQuery(
                "FROM EntryForInterop WHERE jurisdiction = :jurisdiction "
                        + "AND reportingPeriod >= :startDate AND reportingPeriod < :endDate",
                EntryForInterop.class);
        query.setParameter("jurisdiction", jurisdiction);
        query.setParameter("startDate", start.getTime());
        query.setParameter("endDate", end.getTime());
        query.setMaxResults(1);
        List<EntryForInterop> entries = query.list();
        return entries.isEmpty() ? null : entries.get(0);
    }

    private Date normalizeReportingPeriod(Date value) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(value);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private Date parseReportingPeriod(String value) {
        Date parsed = parseDate(value, "yyyy-MM-dd");
        if (parsed != null) {
            return parsed;
        }
        parsed = parseDate(value, "M/d/yyyy");
        if (parsed != null) {
            return parsed;
        }
        throw new IllegalArgumentException("Reporting period must use yyyy-MM-dd or M/d/yyyy format.");
    }

    private Date parseDate(String value, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setLenient(false);
        try {
            return sdf.parse(value);
        } catch (ParseException e) {
            return null;
        }
    }

    private int parseCount(String value, String fieldName, int lineNumber) {
        String normalizedValue = value.replace(",", "");
        try {
            return Integer.parseInt(normalizedValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Line " + lineNumber + ": " + fieldName
                    + " must be a whole number.");
        }
    }

    private String formatReportingPeriod(Date reportingPeriod) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(reportingPeriod);
    }

    private String toCsvRow(String... values) {
        List<String> escapedValues = new ArrayList<String>();
        for (String value : values) {
            escapedValues.add(escapeCsv(value));
        }
        return String.join(",", escapedValues);
    }

    private String escapeCsv(String value) {
        String safeValue = value == null ? "" : value;
        if (safeValue.contains("\"") || safeValue.contains(",") || safeValue.contains("\n")
                || safeValue.contains("\r")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields;
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

            out.println("<hr>");
            out.println("<h3>EntryForInterop CSV</h3>");
            out.println("<p>Download all EntryForInterop data or upload a CSV to upsert records.</p>");
            out.println("<p><a class=\"w3-button w3-blue\" href=\"?" + PARAM_ACTION + "="
                    + ACTION_DOWNLOAD_ENTRY_FOR_INTEROP + "\">Download CSV</a></p>");
            out.println("<form method=\"POST\" enctype=\"multipart/form-data\" class=\"w3-container\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\""
                    + ACTION_UPLOAD_ENTRY_FOR_INTEROP + "\">");
            out.println("  <p><input class=\"w3-input\" type=\"file\" name=\"" + PARAM_ENTRY_FOR_INTEROP_FILE
                    + "\" accept=\".csv,text/csv\"></p>");
            out.println("  <p><button class=\"w3-button w3-orange\" type=\"submit\">Upload CSV</button></p>");
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

    private static class ParseResult {
        private final List<UploadRow> rows = new ArrayList<UploadRow>();
        private final List<String> errors = new ArrayList<String>();
    }

    private static class UploadRow {
        private Date reportingPeriod;
        private int updateCount;
        private int queryCount;
        private String contactEmail;
        private String contactEmailKey;
        private String contactFirstName;
        private String contactLastName;
        private Jurisdiction jurisdiction;
    }
}
