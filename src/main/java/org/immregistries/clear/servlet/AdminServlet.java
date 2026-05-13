package org.immregistries.clear.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLEncoder;
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
import java.util.HashSet;
import java.util.Set;

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
import org.immregistries.clear.model.ContactJurisdictionAccess;
import org.immregistries.clear.model.EntryForInterop;
import org.immregistries.clear.model.Jurisdiction;
import org.immregistries.clear.model.JurisdictionAccessRole;
import org.immregistries.clear.model.SystemSetting;
import org.immregistries.clear.service.JurisdictionAdminSupport;
import org.immregistries.clear.service.JurisdictionAdminSupport.BulkJurisdictionUpdate;
import org.immregistries.clear.service.JurisdictionAdminSupport.BulkJurisdictionUpdateResult;
import org.immregistries.clear.service.JurisdictionAdminSupport.JurisdictionEditRow;
import org.immregistries.clear.service.JurisdictionAdminSupport.JurisdictionEditValidationResult;
import org.immregistries.clear.service.EmailService;
import org.immregistries.clear.utils.HibernateUtil;
import org.immregistries.clear.utils.SystemSettingSupport;

@MultipartConfig
public class AdminServlet extends HttpServlet {

    private static final String PARAM_SEND_TEST_EMAIL = "sendTestEmail";
    private static final String PARAM_ACTION = "action";
    private static final String ACTION_DOWNLOAD_ENTRY_FOR_INTEROP = "downloadEntryForInterop";
    private static final String ACTION_UPLOAD_ENTRY_FOR_INTEROP = "uploadEntryForInterop";
    private static final String ACTION_SAVE_ACCESS_OVERRIDE = "saveAccessOverride";
    private static final String ACTION_DELETE_ACCESS_OVERRIDE = "deleteAccessOverride";
    private static final String ACTION_VIEW_JURISDICTIONS = "jurisdictions";
    private static final String ACTION_VIEW_CONTACT_ADMIN = "contactAdmin";
    private static final String ACTION_SAVE_JURISDICTIONS = "saveJurisdictions";
    private static final String ACTION_BULK_UPDATE_JURISDICTIONS = "bulkUpdateJurisdictions";
    private static final String ACTION_SAVE_JURISDICTION_DETAILS = "saveJurisdictionDetails";
    private static final String ACTION_SET_JURISDICTION_ROLE = "setJurisdictionRole";
    private static final String ACTION_SET_JURISDICTION_PRIMARY_REPORTER = "setJurisdictionPrimaryReporter";
    private static final String ACTION_REMOVE_JURISDICTION_ROLE = "removeJurisdictionRole";
    private static final String ACTION_ADD_CONTACT = "addContact";
    private static final String ACTION_SAVE_CONTACT = "saveContact";
    private static final String ACTION_UPLOAD_CONTACTS = "uploadContacts";
    private static final String ACTION_SAVE_CONTACT_ACCESS = "saveContactAccess";
    private static final String ACTION_DELETE_CONTACT_ACCESS = "deleteContactAccess";
    private static final String PARAM_ENTRY_FOR_INTEROP_FILE = "entryForInteropFile";
    private static final String PARAM_CONTACT_FILE = "contactFile";
    private static final String PARAM_ACCESS_OVERRIDE_ID = "accessOverrideId";
    private static final String PARAM_ACCESS_CONTACT_ID = "accessContactId";
    private static final String PARAM_ACCESS_JURISDICTION_ID = "accessJurisdictionId";
    private static final String PARAM_ACCESS_ROLE = "accessRole";
    private static final String PARAM_CONTACT_SEARCH = "contactSearch";
    private static final String PARAM_CONTACT_ID = "contactId";
    private static final String PARAM_CONTACT_FIRST_NAME = "contactFirstName";
    private static final String PARAM_CONTACT_LAST_NAME = "contactLastName";
    private static final String PARAM_CONTACT_EMAIL = "contactEmail";
    private static final String PARAM_CONTACT_JURISDICTION_ID = "contactJurisdictionId";
    private static final String PARAM_CONTACT_ACCESS_ID = "contactAccessId";
    private static final String PARAM_CONTACT_ACCESS_JURISDICTION_ID = "contactAccessJurisdictionId";
    private static final String PARAM_CONTACT_ACCESS_ROLE = "contactAccessRole";
    private static final String PARAM_JURISDICTION_ID = "jurisdictionId";
    private static final String PARAM_TARGET_JURISDICTION_ID = "targetJurisdictionId";
    private static final String PARAM_TARGET_CONTACT_ID = "targetContactId";
    private static final String PARAM_TARGET_ROLE = "targetRole";
    private static final String PARAM_LOCAL_CONTACT_SEARCH = "localContactSearch";
    private static final String PARAM_GLOBAL_CONTACT_SEARCH = "globalContactSearch";
    private static final String PARAM_JURISDICTION_DISPLAY_LABEL = "jurisdictionDisplayLabel";
    private static final String PARAM_JURISDICTION_MAP_LINK = "jurisdictionMapLink";
    private static final String PARAM_BULK_JURISDICTION_TEXT = "bulkJurisdictionText";

    private static final String CSV_HEADER_REPORTING_PERIOD = "Date time of reporting period";
    private static final String CSV_HEADER_UPDATE_COUNT = "Update count";
    private static final String CSV_HEADER_QUERY_COUNT = "Query count";
    private static final String CSV_HEADER_CONTACT_EMAIL = "Contact Email";
    private static final String CSV_HEADER_CONTACT_FIRST_NAME = "Contact First Name";
    private static final String CSV_HEADER_CONTACT_LAST_NAME = "Contact Last Name";
    private static final String CSV_HEADER_JURISDICTION_MAP_LINK = "Jurisdiction (mapLink)";

    private static final String CONTACT_CSV_HEADER_ORG_NAME = "Org Name";
    private static final String CONTACT_CSV_HEADER_AART_ACRONYM = "AART IIS Acronym";
    private static final String CONTACT_CSV_HEADER_FIRST_NAME = "First Name";
    private static final String CONTACT_CSV_HEADER_LAST_NAME = "Last Name";
    private static final String CONTACT_CSV_HEADER_EMAIL = "Email";

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
    private final JurisdictionAdminSupport jurisdictionAdminSupport = new JurisdictionAdminSupport();

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

        String pathInfo = req.getPathInfo();
        if ("/jurisdictions".equals(pathInfo)) {
            renderJurisdictionPage(resp, sessionUser, null, false, null, "");
            return;
        }
        if ("/jurisdictions/edit".equals(pathInfo)) {
            Integer jurisdictionId = parseInteger(req.getParameter(PARAM_JURISDICTION_ID));
            renderJurisdictionEditPage(resp, sessionUser, jurisdictionId, null, false,
                    normalize(req.getParameter(PARAM_LOCAL_CONTACT_SEARCH)),
                    normalize(req.getParameter(PARAM_GLOBAL_CONTACT_SEARCH)));
            return;
        }
        if ("/contacts".equals(pathInfo)) {
            renderContactAdminPage(req, resp, sessionUser, null, false);
            return;
        }

        String action = normalize(req.getParameter(PARAM_ACTION));
        if (ACTION_DOWNLOAD_ENTRY_FOR_INTEROP.equals(action)) {
            downloadEntryForInteropCsv(resp);
            return;
        }
        if (ACTION_VIEW_JURISDICTIONS.equals(action)) {
            renderJurisdictionPage(resp, sessionUser, null, false, null, "");
            return;
        }
        if (ACTION_VIEW_CONTACT_ADMIN.equals(action)) {
            renderContactAdminPage(req, resp, sessionUser, null, false);
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
        if (ACTION_SAVE_ACCESS_OVERRIDE.equals(action)) {
            handleSaveAccessOverride(req, resp, sessionUser);
            return;
        }
        if (ACTION_DELETE_ACCESS_OVERRIDE.equals(action)) {
            handleDeleteAccessOverride(req, resp, sessionUser);
            return;
        }
        if (ACTION_SAVE_JURISDICTIONS.equals(action)) {
            handleSaveJurisdictions(req, resp, sessionUser);
            return;
        }
        if (ACTION_BULK_UPDATE_JURISDICTIONS.equals(action)) {
            handleBulkJurisdictionUpdates(req, resp, sessionUser);
            return;
        }
        if (ACTION_SAVE_JURISDICTION_DETAILS.equals(action)) {
            handleSaveJurisdictionDetails(req, resp, sessionUser);
            return;
        }
        if (ACTION_SET_JURISDICTION_ROLE.equals(action)) {
            handleSetJurisdictionRole(req, resp, sessionUser);
            return;
        }
        if (ACTION_SET_JURISDICTION_PRIMARY_REPORTER.equals(action)) {
            handleSetJurisdictionPrimaryReporter(req, resp, sessionUser);
            return;
        }
        if (ACTION_REMOVE_JURISDICTION_ROLE.equals(action)) {
            handleRemoveJurisdictionRole(req, resp, sessionUser);
            return;
        }
        if (ACTION_ADD_CONTACT.equals(action)) {
            handleAddContact(req, resp, sessionUser);
            return;
        }
        if (ACTION_SAVE_CONTACT.equals(action)) {
            handleSaveContact(req, resp, sessionUser);
            return;
        }
        if (ACTION_UPLOAD_CONTACTS.equals(action)) {
            handleContactUpload(req, resp, sessionUser);
            return;
        }
        if (ACTION_SAVE_CONTACT_ACCESS.equals(action)) {
            handleSaveContactAccess(req, resp, sessionUser);
            return;
        }
        if (ACTION_DELETE_CONTACT_ACCESS.equals(action)) {
            handleDeleteContactAccess(req, resp, sessionUser);
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

            resp.sendRedirect(req.getContextPath() + "/admin?saved=1");
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

    private void handleSaveAccessOverride(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException {
        Map<String, String> values = loadSettingValues();
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            Integer overrideId = parseInteger(req.getParameter(PARAM_ACCESS_OVERRIDE_ID));
            Integer contactId = parseRequiredInteger(req.getParameter(PARAM_ACCESS_CONTACT_ID), "Contact");
            Integer jurisdictionId = parseRequiredInteger(req.getParameter(PARAM_ACCESS_JURISDICTION_ID),
                    "Jurisdiction");
            JurisdictionAccessRole accessRole = parseAccessRole(req.getParameter(PARAM_ACCESS_ROLE));

            Contact contact = session.get(Contact.class, contactId);
            if (contact == null) {
                throw new IllegalArgumentException("Selected contact was not found.");
            }
            Jurisdiction jurisdiction = session.get(Jurisdiction.class, jurisdictionId);
            if (jurisdiction == null) {
                throw new IllegalArgumentException("Selected jurisdiction was not found.");
            }

            tx = session.beginTransaction();
            ContactJurisdictionAccess accessOverride = findExistingAccessOverride(session, overrideId, contactId,
                    jurisdictionId);
            if (accessRole == JurisdictionAccessRole.PRIMARY_REPORTER) {
                ensurePrimaryReporterAvailable(session, jurisdictionId, accessOverride == null
                        ? null
                        : Integer.valueOf(accessOverride.getContactJurisdictionAccessId()));
            }

            if (accessOverride == null) {
                accessOverride = new ContactJurisdictionAccess();
                accessOverride.setDateCreated(new Date());
            }
            accessOverride.setContactId(contactId.intValue());
            accessOverride.setJurisdictionId(jurisdictionId.intValue());
            accessOverride.setAccessRole(accessRole);
            accessOverride.setDateUpdated(new Date());
            accessOverride.setUpdatedByContactId(sessionUser.getContactId());
            session.saveOrUpdate(accessOverride);
            tx.commit();

            renderPage(resp, sessionUser, values, "Access override saved.", false);
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderPage(resp, sessionUser, values, "Unable to save access override: " + e.getMessage(), true);
        } finally {
            session.close();
        }
    }

    private void handleDeleteAccessOverride(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException {
        Map<String, String> values = loadSettingValues();
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            Integer overrideId = parseRequiredInteger(req.getParameter(PARAM_ACCESS_OVERRIDE_ID), "Access override");
            tx = session.beginTransaction();
            ContactJurisdictionAccess accessOverride = session.get(ContactJurisdictionAccess.class, overrideId);
            if (accessOverride == null) {
                throw new IllegalArgumentException("Selected access override was not found.");
            }
            session.remove(accessOverride);
            tx.commit();
            renderPage(resp, sessionUser, values, "Access override removed.", false);
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderPage(resp, sessionUser, values, "Unable to remove access override: " + e.getMessage(), true);
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
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            List<Contact> contacts = loadContacts(session);
            List<Jurisdiction> jurisdictions = loadJurisdictions(session);
            List<AccessOverrideDisplay> accessOverrides = loadAccessOverrides(session);
            printHeader(out, sessionUser);
            out.println("<h2>Admin</h2>");
            out.println(
                    "<p><a class=\"w3-button w3-blue\" href=\"/clear/admin/jurisdictions\">Manage Jurisdictions</a></p>");
            out.println("<p><a class=\"w3-button w3-teal\" href=\"/clear/admin/contacts\">Manage Contacts</a></p>");
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

            out.println("<hr>");
            out.println("<h3>Jurisdiction Access Overrides</h3>");
            out.println(
                    "<p>Only explicit overrides are stored here. Home-jurisdiction access remains implicit unless overridden.</p>");
            out.println("<form method=\"POST\" class=\"w3-container\" style=\"max-width: 720px; padding-left: 0;\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\""
                    + ACTION_SAVE_ACCESS_OVERRIDE + "\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_ACCESS_OVERRIDE_ID + "\" value=\"\">");
            out.println("  <p><label>Contact</label>");
            out.println("  <select class=\"w3-select\" name=\"" + PARAM_ACCESS_CONTACT_ID + "\">");
            out.println("    <option value=\"\">Select contact</option>");
            for (Contact contact : contacts) {
                out.println("    <option value=\"" + contact.getContactId() + "\">"
                        + escapeHtml(formatContactLabel(contact)) + "</option>");
            }
            out.println("  </select></p>");
            out.println("  <p><label>Jurisdiction</label>");
            out.println("  <select class=\"w3-select\" name=\"" + PARAM_ACCESS_JURISDICTION_ID + "\">");
            out.println("    <option value=\"\">Select jurisdiction</option>");
            for (Jurisdiction jurisdiction : jurisdictions) {
                out.println("    <option value=\"" + jurisdiction.getJurisdictionId() + "\">"
                        + escapeHtml(jurisdiction.getDisplayLabel()) + "</option>");
            }
            out.println("  </select></p>");
            out.println("  <p><label>Access Role</label>");
            out.println("  <select class=\"w3-select\" name=\"" + PARAM_ACCESS_ROLE + "\">");
            for (JurisdictionAccessRole accessRole : JurisdictionAccessRole.values()) {
                out.println("    <option value=\"" + accessRole.name() + "\">"
                        + escapeHtml(formatRoleLabel(accessRole)) + "</option>");
            }
            out.println("  </select></p>");
            out.println("  <p><button class=\"w3-button w3-teal\" type=\"submit\">Save Access Override</button></p>");
            out.println("</form>");

            out.println("<table class=\"w3-table w3-bordered w3-striped\">");
            out.println(
                    "  <tr><th>Contact</th><th>Home Jurisdiction</th><th>Override Jurisdiction</th><th>Role</th><th>Updated</th><th>Action</th></tr>");
            for (AccessOverrideDisplay accessOverride : accessOverrides) {
                out.println("  <tr>");
                out.println("    <td>" + escapeHtml(accessOverride.contactLabel) + "</td>");
                out.println("    <td>" + escapeHtml(accessOverride.homeJurisdictionLabel) + "</td>");
                out.println("    <td>" + escapeHtml(accessOverride.overrideJurisdictionLabel) + "</td>");
                out.println("    <td>" + escapeHtml(formatRoleLabel(accessOverride.accessRole)) + "</td>");
                out.println("    <td>" + escapeHtml(accessOverride.updatedLabel) + "</td>");
                out.println("    <td>");
                out.println("      <form method=\"POST\" style=\"margin:0\">");
                out.println("        <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\""
                        + ACTION_DELETE_ACCESS_OVERRIDE + "\">");
                out.println("        <input type=\"hidden\" name=\"" + PARAM_ACCESS_OVERRIDE_ID + "\" value=\""
                        + accessOverride.accessOverrideId + "\">");
                out.println("        <button class=\"w3-button w3-small w3-red\" type=\"submit\">Remove</button>");
                out.println("      </form>");
                out.println("    </td>");
                out.println("  </tr>");
            }
            out.println("</table>");

            printFooter(out);
        } finally {
            session.close();
            out.close();
        }
    }

    private void handleSaveJurisdictions(HttpServletRequest req, HttpServletResponse resp, SessionUser sessionUser)
            throws IOException {
        try {
            List<JurisdictionEditRow> submittedRows = readJurisdictionRows(req);
            String bulkText = safe(req.getParameter(PARAM_BULK_JURISDICTION_TEXT));
            JurisdictionEditValidationResult validationResult = jurisdictionAdminSupport
                    .validateJurisdictionRows(submittedRows);
            if (validationResult.hasErrors()) {
                renderJurisdictionPage(resp, sessionUser,
                        joinErrors("Unable to save jurisdictions", validationResult.getErrors()),
                        true,
                        submittedRows,
                        bulkText);
                return;
            }

            Session session = HibernateUtil.getSessionFactory().openSession();
            Transaction tx = session.beginTransaction();
            try {
                Map<Integer, Jurisdiction> jurisdictionById = loadJurisdictionById(session);
                Set<String> mapLinks = new HashSet<String>();
                for (JurisdictionEditRow row : submittedRows) {
                    Jurisdiction jurisdiction = jurisdictionById.get(row.getJurisdictionId());
                    if (jurisdiction == null) {
                        throw new IllegalArgumentException(
                                "Jurisdiction #" + row.getJurisdictionId() + " was not found.");
                    }
                    String mapLink = normalize(row.getMapLink());
                    String mapLinkKey = mapLink.toLowerCase(Locale.ROOT);
                    if (!mapLinks.add(mapLinkKey)) {
                        throw new IllegalArgumentException("Map link '" + mapLink + "' is duplicated in the form.");
                    }
                    jurisdiction.setDisplayLabel(normalize(row.getDisplayLabel()));
                    jurisdiction.setMapLink(mapLink);
                    session.saveOrUpdate(jurisdiction);
                }
                tx.commit();
                renderJurisdictionPage(resp, sessionUser, "Jurisdictions were updated.", false,
                        loadJurisdictionEditRows(session), "");
            } catch (RuntimeException e) {
                if (tx != null) {
                    tx.rollback();
                }
                renderJurisdictionPage(resp, sessionUser,
                        joinErrors("Unable to save jurisdictions", e.getMessage()),
                        true,
                        submittedRows,
                        bulkText);
            } finally {
                session.close();
            }
        } catch (RuntimeException e) {
            Session session = HibernateUtil.getSessionFactory().openSession();
            try {
                renderJurisdictionPage(resp, sessionUser,
                        joinErrors("Unable to save jurisdictions", e.getMessage()),
                        true,
                        loadJurisdictionEditRows(session),
                        safe(req.getParameter(PARAM_BULK_JURISDICTION_TEXT)));
            } finally {
                session.close();
            }
        }
    }

    private void handleBulkJurisdictionUpdates(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException {
        String bulkText = safe(req.getParameter(PARAM_BULK_JURISDICTION_TEXT));
        BulkJurisdictionUpdateResult parseResult = jurisdictionAdminSupport.parseBulkJurisdictionUpdates(bulkText);
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            if (parseResult.hasErrors()) {
                renderJurisdictionPage(resp, sessionUser,
                        joinErrors("Unable to apply bulk jurisdiction update", parseResult.getErrors()),
                        true,
                        loadJurisdictionEditRows(session),
                        bulkText);
                return;
            }

            Transaction tx = session.beginTransaction();
            int updatedCount = 0;
            int createdCount = 0;
            try {
                Map<String, Jurisdiction> jurisdictionByMapLink = loadJurisdictionByMapLink(session);
                for (BulkJurisdictionUpdate update : parseResult.getRows()) {
                    String mapLinkKey = normalize(update.getMapLink()).toLowerCase(Locale.ROOT);
                    Jurisdiction jurisdiction = jurisdictionByMapLink.get(mapLinkKey);
                    if (jurisdiction == null) {
                        jurisdiction = new Jurisdiction();
                        jurisdiction.setMapLink(normalize(update.getMapLink()));
                        jurisdiction.setDisplayLabel(normalize(update.getDisplayLabel()));
                        session.save(jurisdiction);
                        jurisdictionByMapLink.put(mapLinkKey, jurisdiction);
                        createdCount++;
                    } else {
                        jurisdiction.setDisplayLabel(normalize(update.getDisplayLabel()));
                        jurisdiction.setMapLink(normalize(update.getMapLink()));
                        session.saveOrUpdate(jurisdiction);
                        updatedCount++;
                    }
                }
                tx.commit();
                renderJurisdictionPage(resp, sessionUser,
                        "Bulk update complete. Updated " + updatedCount + " jurisdiction(s) and created "
                                + createdCount + " jurisdiction(s).",
                        false,
                        loadJurisdictionEditRows(session),
                        "");
            } catch (RuntimeException e) {
                if (tx != null) {
                    tx.rollback();
                }
                renderJurisdictionPage(resp, sessionUser,
                        joinErrors("Unable to apply bulk jurisdiction update", e.getMessage()),
                        true,
                        loadJurisdictionEditRows(session),
                        bulkText);
            }
        } finally {
            session.close();
        }
    }

    private void handleSaveJurisdictionDetails(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException {
        Integer jurisdictionId = parseInteger(req.getParameter(PARAM_TARGET_JURISDICTION_ID));
        String localSearch = normalize(req.getParameter(PARAM_LOCAL_CONTACT_SEARCH));
        String globalSearch = normalize(req.getParameter(PARAM_GLOBAL_CONTACT_SEARCH));
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            Integer requiredJurisdictionId = parseRequiredInteger(req.getParameter(PARAM_TARGET_JURISDICTION_ID),
                    "Jurisdiction");
            String displayLabel = normalize(req.getParameter(PARAM_JURISDICTION_DISPLAY_LABEL));
            String mapLink = normalize(req.getParameter(PARAM_JURISDICTION_MAP_LINK));
            if (displayLabel == null) {
                throw new IllegalArgumentException("Display label is required.");
            }
            if (mapLink == null) {
                throw new IllegalArgumentException("Map link is required.");
            }

            tx = session.beginTransaction();
            Jurisdiction jurisdiction = session.get(Jurisdiction.class, requiredJurisdictionId);
            if (jurisdiction == null) {
                throw new IllegalArgumentException("Selected jurisdiction was not found.");
            }
            jurisdiction.setDisplayLabel(displayLabel);
            jurisdiction.setMapLink(mapLink);
            session.saveOrUpdate(jurisdiction);
            tx.commit();

            renderJurisdictionEditPage(resp, sessionUser, Integer.valueOf(requiredJurisdictionId),
                    "Jurisdiction details saved.", false, localSearch, globalSearch);
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderJurisdictionEditPage(resp, sessionUser, jurisdictionId,
                    "Unable to save jurisdiction details: " + e.getMessage(), true, localSearch, globalSearch);
        } finally {
            session.close();
        }
    }

    private void handleSetJurisdictionRole(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException {
        Integer jurisdictionId = parseInteger(req.getParameter(PARAM_TARGET_JURISDICTION_ID));
        String localSearch = normalize(req.getParameter(PARAM_LOCAL_CONTACT_SEARCH));
        String globalSearch = normalize(req.getParameter(PARAM_GLOBAL_CONTACT_SEARCH));
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            Integer requiredJurisdictionId = parseRequiredInteger(req.getParameter(PARAM_TARGET_JURISDICTION_ID),
                    "Jurisdiction");
            Integer contactId = parseRequiredInteger(req.getParameter(PARAM_TARGET_CONTACT_ID), "Contact");
            JurisdictionAccessRole accessRole = parseAccessRole(req.getParameter(PARAM_TARGET_ROLE));

            Contact contact = session.get(Contact.class, contactId);
            if (contact == null) {
                throw new IllegalArgumentException("Selected contact was not found.");
            }
            Jurisdiction jurisdiction = session.get(Jurisdiction.class, requiredJurisdictionId);
            if (jurisdiction == null) {
                throw new IllegalArgumentException("Selected jurisdiction was not found.");
            }

            tx = session.beginTransaction();
            upsertJurisdictionAccessRole(session, contact, jurisdiction.getJurisdictionId(), accessRole, sessionUser);
            tx.commit();

            renderJurisdictionEditPage(resp, sessionUser, Integer.valueOf(requiredJurisdictionId),
                    "Role updated for " + formatContactLabel(contact) + ".", false, localSearch, globalSearch);
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderJurisdictionEditPage(resp, sessionUser, jurisdictionId,
                    "Unable to update role: " + e.getMessage(), true, localSearch, globalSearch);
        } finally {
            session.close();
        }
    }

    private void handleSetJurisdictionPrimaryReporter(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException {
        Integer jurisdictionId = parseInteger(req.getParameter(PARAM_TARGET_JURISDICTION_ID));
        String localSearch = normalize(req.getParameter(PARAM_LOCAL_CONTACT_SEARCH));
        String globalSearch = normalize(req.getParameter(PARAM_GLOBAL_CONTACT_SEARCH));
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            Integer requiredJurisdictionId = parseRequiredInteger(req.getParameter(PARAM_TARGET_JURISDICTION_ID),
                    "Jurisdiction");
            Integer contactId = parseRequiredInteger(req.getParameter(PARAM_TARGET_CONTACT_ID), "Contact");

            Contact selectedContact = session.get(Contact.class, contactId);
            if (selectedContact == null) {
                throw new IllegalArgumentException("Selected contact was not found.");
            }
            Jurisdiction jurisdiction = session.get(Jurisdiction.class, requiredJurisdictionId);
            if (jurisdiction == null) {
                throw new IllegalArgumentException("Selected jurisdiction was not found.");
            }

            tx = session.beginTransaction();
            Query<ContactJurisdictionAccess> primaryQuery = session.createQuery(
                    "FROM ContactJurisdictionAccess WHERE jurisdictionId = :jurisdictionId AND accessRole = :accessRole",
                    ContactJurisdictionAccess.class);
            primaryQuery.setParameter("jurisdictionId", Integer.valueOf(requiredJurisdictionId.intValue()));
            primaryQuery.setParameter("accessRole", JurisdictionAccessRole.PRIMARY_REPORTER);
            for (ContactJurisdictionAccess existingPrimary : primaryQuery.list()) {
                if (existingPrimary.getContactId() == contactId.intValue()) {
                    continue;
                }
                Contact existingContact = session.get(Contact.class, Integer.valueOf(existingPrimary.getContactId()));
                if (existingContact != null
                        && existingContact.getJurisdictionId() == requiredJurisdictionId.intValue()) {
                    session.remove(existingPrimary);
                } else {
                    existingPrimary.setAccessRole(JurisdictionAccessRole.VIEWER);
                    existingPrimary.setDateUpdated(new Date());
                    existingPrimary.setUpdatedByContactId(sessionUser.getContactId());
                    session.saveOrUpdate(existingPrimary);
                }
            }

            upsertJurisdictionAccessRole(session, selectedContact, requiredJurisdictionId.intValue(),
                    JurisdictionAccessRole.PRIMARY_REPORTER, sessionUser);
            tx.commit();

            renderJurisdictionEditPage(resp, sessionUser, Integer.valueOf(requiredJurisdictionId.intValue()),
                    "Primary reporter updated to " + formatContactLabel(selectedContact) + ".", false,
                    localSearch, globalSearch);
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderJurisdictionEditPage(resp, sessionUser, jurisdictionId,
                    "Unable to set primary reporter: " + e.getMessage(), true, localSearch, globalSearch);
        } finally {
            session.close();
        }
    }

    private void handleRemoveJurisdictionRole(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException {
        Integer jurisdictionId = parseInteger(req.getParameter(PARAM_TARGET_JURISDICTION_ID));
        String localSearch = normalize(req.getParameter(PARAM_LOCAL_CONTACT_SEARCH));
        String globalSearch = normalize(req.getParameter(PARAM_GLOBAL_CONTACT_SEARCH));
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            Integer requiredJurisdictionId = parseRequiredInteger(req.getParameter(PARAM_TARGET_JURISDICTION_ID),
                    "Jurisdiction");
            Integer contactId = parseRequiredInteger(req.getParameter(PARAM_TARGET_CONTACT_ID), "Contact");

            Contact contact = session.get(Contact.class, contactId);
            if (contact == null) {
                throw new IllegalArgumentException("Selected contact was not found.");
            }

            tx = session.beginTransaction();
            removeJurisdictionAccessRole(session, contactId.intValue(), requiredJurisdictionId.intValue());
            tx.commit();

            renderJurisdictionEditPage(resp, sessionUser, Integer.valueOf(requiredJurisdictionId.intValue()),
                    "Removed explicit role for " + formatContactLabel(contact) + ".", false,
                    localSearch, globalSearch);
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderJurisdictionEditPage(resp, sessionUser, jurisdictionId,
                    "Unable to remove role: " + e.getMessage(), true, localSearch, globalSearch);
        } finally {
            session.close();
        }
    }

    private void handleAddContact(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException {
        String searchText = normalize(req.getParameter(PARAM_CONTACT_SEARCH));
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            String firstName = normalize(req.getParameter(PARAM_CONTACT_FIRST_NAME));
            String lastName = normalize(req.getParameter(PARAM_CONTACT_LAST_NAME));
            String email = normalize(req.getParameter(PARAM_CONTACT_EMAIL));
            Integer jurisdictionId = parseRequiredInteger(req.getParameter(PARAM_CONTACT_JURISDICTION_ID),
                    "Jurisdiction");

            validateContactFields(firstName, lastName, email);
            ensureUniqueContactEmail(session, email, null);

            Jurisdiction jurisdiction = session.get(Jurisdiction.class, jurisdictionId);
            if (jurisdiction == null) {
                throw new IllegalArgumentException("Selected jurisdiction was not found.");
            }

            tx = session.beginTransaction();
            Contact contact = createContact(session, email, firstName, lastName, jurisdiction);
            tx.commit();
            renderContactAdminPage(resp, sessionUser, "Contact created.", false, searchText,
                    Integer.valueOf(contact.getContactId()));
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderContactAdminPage(resp, sessionUser, "Unable to create contact: " + e.getMessage(), true,
                    searchText, null);
        } finally {
            session.close();
        }
    }

    private void handleSaveContact(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException {
        String searchText = normalize(req.getParameter(PARAM_CONTACT_SEARCH));
        Integer contactId = parseInteger(req.getParameter(PARAM_CONTACT_ID));
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            Integer requiredContactId = parseRequiredInteger(req.getParameter(PARAM_CONTACT_ID), "Contact");
            String firstName = normalize(req.getParameter(PARAM_CONTACT_FIRST_NAME));
            String lastName = normalize(req.getParameter(PARAM_CONTACT_LAST_NAME));
            String email = normalize(req.getParameter(PARAM_CONTACT_EMAIL));
            Integer jurisdictionId = parseRequiredInteger(req.getParameter(PARAM_CONTACT_JURISDICTION_ID),
                    "Jurisdiction");

            validateContactFields(firstName, lastName, email);
            ensureUniqueContactEmail(session, email, requiredContactId);

            Contact contact = session.get(Contact.class, requiredContactId);
            if (contact == null) {
                throw new IllegalArgumentException("Selected contact was not found.");
            }
            Jurisdiction jurisdiction = session.get(Jurisdiction.class, jurisdictionId);
            if (jurisdiction == null) {
                throw new IllegalArgumentException("Selected jurisdiction was not found.");
            }

            tx = session.beginTransaction();
            contact.setNameFirst(firstName);
            contact.setNameLast(lastName);
            contact.setEmailAddress(email);
            contact.setJurisdictionId(jurisdiction.getJurisdictionId());
            session.saveOrUpdate(contact);
            tx.commit();

            renderContactAdminPage(resp, sessionUser, "Contact updated.", false, searchText,
                    Integer.valueOf(contact.getContactId()));
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderContactAdminPage(resp, sessionUser, "Unable to update contact: " + e.getMessage(), true,
                    searchText, contactId);
        } finally {
            session.close();
        }
    }

    private void handleContactUpload(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException, ServletException {
        String searchText = normalize(req.getParameter(PARAM_CONTACT_SEARCH));
        Part csvPart = req.getPart(PARAM_CONTACT_FILE);
        if (csvPart == null || csvPart.getSize() == 0) {
            renderContactAdminPage(resp, sessionUser,
                    "Unable to upload contacts: Please choose a CSV file.",
                    true, searchText, null);
            return;
        }

        ContactCsvParseResult parseResult = parseContactUploadRows(csvPart);
        if (parseResult.rowsByEmail.isEmpty()) {
            renderContactAdminPage(resp, sessionUser,
                    "Unable to upload contacts: " + joinErrors("No valid rows were found", parseResult.errors),
                    true, searchText, null);
            return;
        }

        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            Map<String, Contact> contactByEmail = loadContactByEmail(session);
            Map<String, Jurisdiction> jurisdictionByDisplayLabel = loadJurisdictionByDisplayLabel(session);

            tx = session.beginTransaction();
            int createdContacts = 0;
            int updatedContacts = 0;
            int createdJurisdictions = 0;
            for (ContactCsvRow row : parseResult.rowsByEmail.values()) {
                Jurisdiction jurisdiction = jurisdictionByDisplayLabel.get(row.jurisdictionKey);
                if (jurisdiction == null) {
                    jurisdiction = new Jurisdiction();
                    jurisdiction.setDisplayLabel(row.jurisdictionLabel);
                    jurisdiction.setMapLink(row.jurisdictionLabel);
                    session.save(jurisdiction);
                    jurisdictionByDisplayLabel.put(row.jurisdictionKey, jurisdiction);
                    createdJurisdictions++;
                }

                Contact contact = contactByEmail.get(row.emailKey);
                if (contact == null) {
                    contact = createContact(session, row.email, row.firstName, row.lastName, jurisdiction);
                    contactByEmail.put(row.emailKey, contact);
                    createdContacts++;
                } else {
                    contact.setNameFirst(row.firstName);
                    contact.setNameLast(row.lastName);
                    contact.setEmailAddress(row.email);
                    contact.setJurisdictionId(jurisdiction.getJurisdictionId());
                    session.saveOrUpdate(contact);
                    updatedContacts++;
                }
            }
            tx.commit();

            String message = "Contact upload completed. Created " + createdContacts + " contact(s), updated "
                    + updatedContacts + " contact(s), created " + createdJurisdictions + " jurisdiction(s).";
            if (!parseResult.errors.isEmpty()) {
                message += " Skipped " + parseResult.errors.size() + " row(s): "
                        + String.join(" ", parseResult.errors);
            }
            renderContactAdminPage(resp, sessionUser, message, false, searchText, null);
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderContactAdminPage(resp, sessionUser, "Unable to upload contacts: " + e.getMessage(),
                    true, searchText, null);
        } finally {
            session.close();
        }
    }

    private void handleSaveContactAccess(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException {
        String searchText = normalize(req.getParameter(PARAM_CONTACT_SEARCH));
        Integer selectedContactId = parseInteger(req.getParameter(PARAM_CONTACT_ID));
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            Integer contactId = parseRequiredInteger(req.getParameter(PARAM_CONTACT_ID), "Contact");
            Integer jurisdictionId = parseRequiredInteger(req.getParameter(PARAM_CONTACT_ACCESS_JURISDICTION_ID),
                    "Jurisdiction");
            JurisdictionAccessRole accessRole = parseAccessRole(req.getParameter(PARAM_CONTACT_ACCESS_ROLE));

            Contact contact = session.get(Contact.class, contactId);
            if (contact == null) {
                throw new IllegalArgumentException("Selected contact was not found.");
            }
            Jurisdiction jurisdiction = session.get(Jurisdiction.class, jurisdictionId);
            if (jurisdiction == null) {
                throw new IllegalArgumentException("Selected jurisdiction was not found.");
            }

            tx = session.beginTransaction();
            ContactJurisdictionAccess accessOverride = findExistingAccessOverride(session, null, contactId,
                    jurisdictionId);
            if (accessRole == JurisdictionAccessRole.PRIMARY_REPORTER) {
                ensurePrimaryReporterAvailable(session, jurisdictionId, accessOverride == null
                        ? null
                        : Integer.valueOf(accessOverride.getContactJurisdictionAccessId()));
            }
            if (accessOverride == null) {
                accessOverride = new ContactJurisdictionAccess();
                accessOverride.setDateCreated(new Date());
            }
            accessOverride.setContactId(contactId.intValue());
            accessOverride.setJurisdictionId(jurisdictionId.intValue());
            accessOverride.setAccessRole(accessRole);
            accessOverride.setDateUpdated(new Date());
            accessOverride.setUpdatedByContactId(sessionUser.getContactId());
            session.saveOrUpdate(accessOverride);
            tx.commit();

            renderContactAdminPage(resp, sessionUser, "Contact access saved.", false, searchText,
                    Integer.valueOf(contactId.intValue()));
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderContactAdminPage(resp, sessionUser, "Unable to save contact access: " + e.getMessage(), true,
                    searchText, selectedContactId);
        } finally {
            session.close();
        }
    }

    private void handleDeleteContactAccess(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser) throws IOException {
        String searchText = normalize(req.getParameter(PARAM_CONTACT_SEARCH));
        Integer selectedContactId = parseInteger(req.getParameter(PARAM_CONTACT_ID));
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            Integer contactId = parseRequiredInteger(req.getParameter(PARAM_CONTACT_ID), "Contact");
            Integer accessId = parseRequiredInteger(req.getParameter(PARAM_CONTACT_ACCESS_ID), "Contact access");

            tx = session.beginTransaction();
            ContactJurisdictionAccess accessOverride = session.get(ContactJurisdictionAccess.class, accessId);
            if (accessOverride == null) {
                throw new IllegalArgumentException("Selected contact access was not found.");
            }
            if (accessOverride.getContactId() != contactId.intValue()) {
                throw new IllegalArgumentException("Selected contact access does not belong to this contact.");
            }
            session.remove(accessOverride);
            tx.commit();

            renderContactAdminPage(resp, sessionUser, "Contact access removed.", false, searchText,
                    Integer.valueOf(contactId.intValue()));
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            renderContactAdminPage(resp, sessionUser, "Unable to remove contact access: " + e.getMessage(), true,
                    searchText, selectedContactId);
        } finally {
            session.close();
        }
    }

    private void renderContactAdminPage(HttpServletRequest req, HttpServletResponse resp,
            SessionUser sessionUser, String message, boolean error)
            throws IOException {
        renderContactAdminPage(resp, sessionUser, message, error,
                normalize(req.getParameter(PARAM_CONTACT_SEARCH)), parseInteger(req.getParameter(PARAM_CONTACT_ID)));
    }

    private void renderContactAdminPage(HttpServletResponse resp, SessionUser sessionUser,
            String message, boolean error, String searchText, Integer selectedContactId)
            throws IOException {
        resp.setContentType("text/html");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            List<Jurisdiction> jurisdictions = loadJurisdictions(session);
            Map<Integer, Jurisdiction> jurisdictionById = new HashMap<Integer, Jurisdiction>();
            for (Jurisdiction jurisdiction : jurisdictions) {
                jurisdictionById.put(Integer.valueOf(jurisdiction.getJurisdictionId()), jurisdiction);
            }

            List<Contact> contacts = loadContactsForSearch(session, searchText);
            Contact selectedContact = selectedContactId == null ? null : session.get(Contact.class, selectedContactId);
            List<ContactJurisdictionAccess> selectedContactAccess = selectedContact == null
                    ? new ArrayList<ContactJurisdictionAccess>()
                    : loadContactAccess(session, selectedContact.getContactId());

            printHeader(out, sessionUser);
            out.println("<h2>Contact Admin</h2>");
            out.println("<p><a class=\"w3-button w3-light-grey\" href=\"/clear/admin\">Back to Admin</a></p>");
            if (message != null && !message.isEmpty()) {
                String panelClass = error ? "w3-pale-red" : "w3-pale-green";
                out.println("<div class=\"w3-panel " + panelClass + "\">" + escapeHtml(message) + "</div>");
            }

            out.println("<h3>Search Contacts</h3>");
            out.println("<form method=\"GET\" class=\"w3-container\" action=\"/clear/admin/contacts\">");
            out.println("  <p>");
            out.println("    <input class=\"w3-input\" type=\"text\" name=\"" + PARAM_CONTACT_SEARCH
                    + "\" value=\"" + escapeHtml(safe(searchText))
                    + "\" placeholder=\"Search first name, last name, or email\">");
            out.println("  </p>");
            out.println("  <p><button class=\"w3-button w3-blue\" type=\"submit\">Search</button></p>");
            out.println("</form>");

            out.println("<table class=\"w3-table w3-bordered w3-striped\">");
            out.println(
                    "  <tr><th>First Name</th><th>Last Name</th><th>Email</th><th>Jurisdiction</th><th>Action</th></tr>");
            for (Contact contact : contacts) {
                Jurisdiction jurisdiction = jurisdictionById.get(Integer.valueOf(contact.getJurisdictionId()));
                String href = "/clear/admin/contacts?" + PARAM_CONTACT_ID + "=" + contact.getContactId();
                if (searchText != null) {
                    href += "&" + PARAM_CONTACT_SEARCH + "=" + urlEncode(searchText);
                }
                out.println("  <tr>");
                out.println("    <td>" + escapeHtml(safe(contact.getNameFirst())) + "</td>");
                out.println("    <td>" + escapeHtml(safe(contact.getNameLast())) + "</td>");
                out.println("    <td>" + escapeHtml(safe(contact.getEmailAddress())) + "</td>");
                out.println("    <td>" + escapeHtml(jurisdiction == null ? "" : safe(jurisdiction.getDisplayLabel()))
                        + "</td>");
                out.println("    <td><a class=\"w3-button w3-small w3-teal\" href=\"" + href
                        + "\">View/Edit</a></td>");
                out.println("  </tr>");
            }
            out.println("</table>");

            out.println("<hr>");
            out.println("<h3>Add Contact</h3>");
            out.println("<form method=\"POST\" class=\"w3-container\" style=\"max-width: 720px; padding-left: 0;\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_ADD_CONTACT
                    + "\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_CONTACT_SEARCH + "\" value=\""
                    + escapeHtml(safe(searchText)) + "\">");
            out.println("  <p><label>First Name</label><input class=\"w3-input\" type=\"text\" name=\""
                    + PARAM_CONTACT_FIRST_NAME + "\"></p>");
            out.println("  <p><label>Last Name</label><input class=\"w3-input\" type=\"text\" name=\""
                    + PARAM_CONTACT_LAST_NAME + "\"></p>");
            out.println("  <p><label>Email</label><input class=\"w3-input\" type=\"text\" name=\""
                    + PARAM_CONTACT_EMAIL + "\"></p>");
            out.println("  <p><label>Jurisdiction</label>");
            out.println("  <select class=\"w3-select\" name=\"" + PARAM_CONTACT_JURISDICTION_ID + "\">");
            out.println("    <option value=\"\">Select jurisdiction</option>");
            for (Jurisdiction jurisdiction : jurisdictions) {
                out.println("    <option value=\"" + jurisdiction.getJurisdictionId() + "\">"
                        + escapeHtml(jurisdiction.getDisplayLabel()) + "</option>");
            }
            out.println("  </select></p>");
            out.println("  <p><button class=\"w3-button w3-green\" type=\"submit\">Add Contact</button></p>");
            out.println("</form>");

            out.println("<hr>");
            out.println("<h3>Upload Contacts CSV</h3>");
            out.println("<p>Upload CSV with columns Org Name, AART IIS Acronym, First Name, Last Name, Email.</p>");
            out.println(
                    "<form method=\"POST\" enctype=\"multipart/form-data\" class=\"w3-container\" style=\"max-width: 720px; padding-left: 0;\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_UPLOAD_CONTACTS
                    + "\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_CONTACT_SEARCH + "\" value=\""
                    + escapeHtml(safe(searchText)) + "\">");
            out.println("  <p><input class=\"w3-input\" type=\"file\" name=\"" + PARAM_CONTACT_FILE
                    + "\" accept=\".csv,text/csv\"></p>");
            out.println("  <p><button class=\"w3-button w3-orange\" type=\"submit\">Upload Contacts</button></p>");
            out.println("</form>");

            if (selectedContact != null) {
                out.println("<hr>");
                out.println("<h3>Edit Contact</h3>");
                out.println(
                        "<form method=\"POST\" class=\"w3-container\" style=\"max-width: 720px; padding-left: 0;\">");
                out.println("  <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_SAVE_CONTACT
                        + "\">");
                out.println("  <input type=\"hidden\" name=\"" + PARAM_CONTACT_SEARCH + "\" value=\""
                        + escapeHtml(safe(searchText)) + "\">");
                out.println("  <input type=\"hidden\" name=\"" + PARAM_CONTACT_ID + "\" value=\""
                        + selectedContact.getContactId() + "\">");
                out.println("  <p><label>First Name</label><input class=\"w3-input\" type=\"text\" name=\""
                        + PARAM_CONTACT_FIRST_NAME + "\" value=\""
                        + escapeHtml(safe(selectedContact.getNameFirst())) + "\"></p>");
                out.println("  <p><label>Last Name</label><input class=\"w3-input\" type=\"text\" name=\""
                        + PARAM_CONTACT_LAST_NAME + "\" value=\""
                        + escapeHtml(safe(selectedContact.getNameLast())) + "\"></p>");
                out.println("  <p><label>Email</label><input class=\"w3-input\" type=\"text\" name=\""
                        + PARAM_CONTACT_EMAIL + "\" value=\""
                        + escapeHtml(safe(selectedContact.getEmailAddress())) + "\"></p>");
                out.println("  <p><label>Jurisdiction</label>");
                out.println("  <select class=\"w3-select\" name=\"" + PARAM_CONTACT_JURISDICTION_ID + "\">");
                out.println("    <option value=\"\">Select jurisdiction</option>");
                for (Jurisdiction jurisdiction : jurisdictions) {
                    boolean selected = jurisdiction.getJurisdictionId() == selectedContact.getJurisdictionId();
                    out.println("    <option value=\"" + jurisdiction.getJurisdictionId() + "\""
                            + (selected ? " selected" : "") + ">"
                            + escapeHtml(jurisdiction.getDisplayLabel()) + "</option>");
                }
                out.println("  </select></p>");
                out.println("  <p><button class=\"w3-button w3-teal\" type=\"submit\">Save Contact</button></p>");
                out.println("</form>");

                out.println("<h3>Contact Jurisdiction Access</h3>");
                out.println(
                        "<form method=\"POST\" class=\"w3-container\" style=\"max-width: 720px; padding-left: 0;\">");
                out.println("  <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\""
                        + ACTION_SAVE_CONTACT_ACCESS + "\">");
                out.println("  <input type=\"hidden\" name=\"" + PARAM_CONTACT_SEARCH + "\" value=\""
                        + escapeHtml(safe(searchText)) + "\">");
                out.println("  <input type=\"hidden\" name=\"" + PARAM_CONTACT_ID + "\" value=\""
                        + selectedContact.getContactId() + "\">");
                out.println("  <p><label>Jurisdiction</label>");
                out.println("  <select class=\"w3-select\" name=\"" + PARAM_CONTACT_ACCESS_JURISDICTION_ID + "\">");
                out.println("    <option value=\"\">Select jurisdiction</option>");
                for (Jurisdiction jurisdiction : jurisdictions) {
                    out.println("    <option value=\"" + jurisdiction.getJurisdictionId() + "\">"
                            + escapeHtml(jurisdiction.getDisplayLabel()) + "</option>");
                }
                out.println("  </select></p>");
                out.println("  <p><label>Access Role</label>");
                out.println("  <select class=\"w3-select\" name=\"" + PARAM_CONTACT_ACCESS_ROLE + "\">");
                for (JurisdictionAccessRole accessRole : JurisdictionAccessRole.values()) {
                    out.println("    <option value=\"" + accessRole.name() + "\">"
                            + escapeHtml(formatRoleLabel(accessRole)) + "</option>");
                }
                out.println("  </select></p>");
                out.println(
                        "  <p><button class=\"w3-button w3-blue\" type=\"submit\">Save Contact Access</button></p>");
                out.println("</form>");

                out.println("<table class=\"w3-table w3-bordered w3-striped\">");
                out.println("  <tr><th>Jurisdiction</th><th>Role</th><th>Updated</th><th>Action</th></tr>");
                SimpleDateFormat accessDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                for (ContactJurisdictionAccess access : selectedContactAccess) {
                    Jurisdiction jurisdiction = jurisdictionById.get(Integer.valueOf(access.getJurisdictionId()));
                    out.println("  <tr>");
                    out.println("    <td>" + escapeHtml(jurisdiction == null
                            ? "Unknown jurisdiction #" + access.getJurisdictionId()
                            : safe(jurisdiction.getDisplayLabel())) + "</td>");
                    out.println("    <td>" + escapeHtml(formatRoleLabel(access.getAccessRole())) + "</td>");
                    out.println("    <td>" + escapeHtml(access.getDateUpdated() == null
                            ? ""
                            : accessDateFormat.format(access.getDateUpdated())) + "</td>");
                    out.println("    <td>");
                    out.println("      <form method=\"POST\" style=\"margin:0\">");
                    out.println("        <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\""
                            + ACTION_DELETE_CONTACT_ACCESS + "\">");
                    out.println("        <input type=\"hidden\" name=\"" + PARAM_CONTACT_SEARCH + "\" value=\""
                            + escapeHtml(safe(searchText)) + "\">");
                    out.println("        <input type=\"hidden\" name=\"" + PARAM_CONTACT_ID + "\" value=\""
                            + selectedContact.getContactId() + "\">");
                    out.println("        <input type=\"hidden\" name=\"" + PARAM_CONTACT_ACCESS_ID + "\" value=\""
                            + access.getContactJurisdictionAccessId() + "\">");
                    out.println("        <button class=\"w3-button w3-small w3-red\" type=\"submit\">Remove</button>");
                    out.println("      </form>");
                    out.println("    </td>");
                    out.println("  </tr>");
                }
                out.println("</table>");
            }

            printFooter(out);
        } finally {
            session.close();
            out.close();
        }
    }

    private List<Contact> loadContactsForSearch(Session session, String searchText) {
        if (searchText == null) {
            return loadContacts(session);
        }
        String searchKey = "%" + searchText.toLowerCase(Locale.ROOT) + "%";
        Query<Contact> query = session.createQuery(
                "FROM Contact WHERE lower(nameFirst) LIKE :search OR lower(nameLast) LIKE :search "
                        + "OR lower(emailAddress) LIKE :search ORDER BY nameLast, nameFirst, emailAddress",
                Contact.class);
        query.setParameter("search", searchKey);
        return query.list();
    }

    private List<ContactJurisdictionAccess> loadContactAccess(Session session, int contactId) {
        Query<ContactJurisdictionAccess> query = session.createQuery(
                "FROM ContactJurisdictionAccess WHERE contactId = :contactId ORDER BY jurisdictionId",
                ContactJurisdictionAccess.class);
        query.setParameter("contactId", Integer.valueOf(contactId));
        return query.list();
    }

    private void validateContactFields(String firstName, String lastName, String email) {
        if (firstName == null) {
            throw new IllegalArgumentException("First name is required.");
        }
        if (lastName == null) {
            throw new IllegalArgumentException("Last name is required.");
        }
        if (email == null) {
            throw new IllegalArgumentException("Email is required.");
        }
    }

    private void ensureUniqueContactEmail(Session session, String email, Integer excludedContactId) {
        Contact existing = findContactByEmailKey(session, email.toLowerCase(Locale.ROOT));
        if (existing != null && (excludedContactId == null
                || existing.getContactId() != excludedContactId.intValue())) {
            throw new IllegalArgumentException("Email must be unique.");
        }
    }

    private Contact findContactByEmailKey(Session session, String emailKey) {
        Query<Contact> query = session.createQuery("FROM Contact WHERE lower(emailAddress) = :email", Contact.class);
        query.setParameter("email", emailKey);
        query.setMaxResults(1);
        List<Contact> contacts = query.list();
        return contacts.isEmpty() ? null : contacts.get(0);
    }

    private Map<String, Jurisdiction> loadJurisdictionByDisplayLabel(Session session) {
        Map<String, Jurisdiction> jurisdictionByDisplayLabel = new HashMap<String, Jurisdiction>();
        Query<Jurisdiction> query = session.createQuery("FROM Jurisdiction", Jurisdiction.class);
        for (Jurisdiction jurisdiction : query.list()) {
            String displayLabel = normalize(jurisdiction.getDisplayLabel());
            if (displayLabel != null) {
                jurisdictionByDisplayLabel.put(displayLabel.toLowerCase(Locale.ROOT), jurisdiction);
            }
        }
        return jurisdictionByDisplayLabel;
    }

    private ContactCsvParseResult parseContactUploadRows(Part csvPart) throws IOException {
        ContactCsvParseResult parseResult = new ContactCsvParseResult();
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
                    if (looksLikeContactUploadHeader(columns)) {
                        headerProcessed = true;
                    }
                    continue;
                }

                if (columns.size() < 5) {
                    parseResult.errors.add("Line " + lineNumber + ": Expected at least 5 columns but found "
                            + columns.size() + ".");
                    continue;
                }

                String jurisdictionLabel = normalize(columns.get(1));
                String firstName = normalize(columns.get(2));
                String lastName = normalize(columns.get(3));
                String email = normalize(columns.get(4));
                if (jurisdictionLabel == null || firstName == null || lastName == null || email == null) {
                    parseResult.errors.add("Line " + lineNumber
                            + ": AART IIS Acronym, First Name, Last Name, and Email are required.");
                    continue;
                }

                ContactCsvRow row = new ContactCsvRow();
                row.jurisdictionLabel = jurisdictionLabel;
                row.jurisdictionKey = jurisdictionLabel.toLowerCase(Locale.ROOT);
                row.firstName = firstName;
                row.lastName = lastName;
                row.email = email;
                row.emailKey = email.toLowerCase(Locale.ROOT);
                parseResult.rowsByEmail.put(row.emailKey, row);
            }
        }

        if (!headerProcessed) {
            parseResult.errors.add("Could not find contact CSV header row.");
        } else if (parseResult.rowsByEmail.isEmpty() && parseResult.errors.isEmpty()) {
            parseResult.errors.add("CSV did not contain any contact rows.");
        }
        return parseResult;
    }

    private boolean looksLikeContactUploadHeader(List<String> columns) {
        if (columns == null || columns.size() < 5) {
            return false;
        }
        return CONTACT_CSV_HEADER_ORG_NAME.equalsIgnoreCase(normalize(columns.get(0)))
                && CONTACT_CSV_HEADER_AART_ACRONYM.equalsIgnoreCase(normalize(columns.get(1)))
                && CONTACT_CSV_HEADER_FIRST_NAME.equalsIgnoreCase(normalize(columns.get(2)))
                && CONTACT_CSV_HEADER_LAST_NAME.equalsIgnoreCase(normalize(columns.get(3)))
                && CONTACT_CSV_HEADER_EMAIL.equalsIgnoreCase(normalize(columns.get(4)));
    }

    private void renderJurisdictionPage(HttpServletResponse resp, SessionUser sessionUser,
            String message, boolean error, List<JurisdictionEditRow> jurisdictionRows, String bulkText)
            throws IOException {
        resp.setContentType("text/html");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            List<JurisdictionEditRow> rows = jurisdictionRows == null
                    ? loadJurisdictionEditRows(session)
                    : jurisdictionRows;
            Map<Integer, Contact> primaryReporterByJurisdiction = loadPrimaryReporterByJurisdiction(session);
            printHeader(out, sessionUser);
            out.println("<h2>Jurisdictions</h2>");
            out.println("<p><a class=\"w3-button w3-light-grey\" href=\"/clear/admin\">Back to Admin</a></p>");
            if (message != null && !message.isEmpty()) {
                String panelClass = error ? "w3-pale-red" : "w3-pale-green";
                out.println("<div class=\"w3-panel " + panelClass + "\">" + escapeHtml(message) + "</div>");
            }

            out.println("<h3>Jurisdiction List</h3>");
            out.println("<p>Select Edit to update display settings and contact access roles.</p>");
            out.println("<table class=\"w3-table w3-bordered w3-striped\">");
            out.println(
                    "  <tr><th>Display Label</th><th>Map Link</th><th>Primary Reporter</th><th>ID</th><th>Action</th></tr>");
            for (JurisdictionEditRow row : rows) {
                Contact primaryReporter = primaryReporterByJurisdiction.get(Integer.valueOf(row.getJurisdictionId()));
                String editHref = "/clear/admin/jurisdictions/edit?" + PARAM_JURISDICTION_ID + "="
                        + row.getJurisdictionId();
                out.println("    <tr>");
                out.println("      <td>" + escapeHtml(safe(row.getDisplayLabel())) + "</td>");
                out.println("      <td>" + escapeHtml(safe(row.getMapLink())) + "</td>");
                out.println("      <td><a href=\"" + editHref + "\">"
                        + escapeHtml(primaryReporter == null ? "Unassigned" : formatContactLabel(primaryReporter))
                        + "</a></td>");
                out.println("      <td>" + row.getJurisdictionId() + "</td>");
                out.println("      <td><a class=\"w3-button w3-small w3-teal\" href=\"" + editHref
                        + "\">Edit</a></td>");
                out.println("    </tr>");
            }
            out.println("</table>");

            out.println("<hr>");
            out.println("<h3>Bulk Display Label Update</h3>");
            out.println("<p>Use AART with the old interface, then go to System &gt;&gt; SQLReports and run:</p>");
            out.println(
                    "<pre>SELECT test_participant_label, map_link FROM test_participant WHERE map_link IS NOT NULL AND map_link &lt;&gt; '';</pre>");
            out.println(
                    "<p>Paste the results below as one line per jurisdiction in the form <strong>displayLabel, mapLink</strong>.</p>");
            out.println("<form method=\"POST\" class=\"w3-container\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\""
                    + ACTION_BULK_UPDATE_JURISDICTIONS + "\">");
            out.println("  <p><textarea class=\"w3-input\" name=\"" + PARAM_BULK_JURISDICTION_TEXT
                    + "\" rows=\"12\" style=\"font-family: monospace;\">"
                    + escapeHtml(safe(bulkText)) + "</textarea></p>");
            out.println("  <p><button class=\"w3-button w3-blue\" type=\"submit\">Apply Bulk Update</button></p>");
            out.println("</form>");

            printFooter(out);
        } finally {
            session.close();
            out.close();
        }
    }

    private void renderJurisdictionEditPage(HttpServletResponse resp, SessionUser sessionUser,
            Integer jurisdictionId, String message, boolean error, String localSearch, String globalSearch)
            throws IOException {
        resp.setContentType("text/html");
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            Jurisdiction jurisdiction = jurisdictionId == null ? null : session.get(Jurisdiction.class, jurisdictionId);

            printHeader(out, sessionUser);
            out.println("<h2>Edit Jurisdiction</h2>");
            out.println(
                    "<p><a class=\"w3-button w3-light-grey\" href=\"/clear/admin/jurisdictions\">Back to Jurisdictions</a></p>");
            if (message != null && !message.isEmpty()) {
                String panelClass = error ? "w3-pale-red" : "w3-pale-green";
                out.println("<div class=\"w3-panel " + panelClass + "\">" + escapeHtml(message) + "</div>");
            }
            if (jurisdiction == null) {
                out.println("<div class=\"w3-panel w3-pale-red\">Jurisdiction not found.</div>");
                printFooter(out);
                return;
            }

            List<ContactJurisdictionAccess> overrides = loadAccessByJurisdiction(session,
                    jurisdiction.getJurisdictionId());
            Map<Integer, ContactJurisdictionAccess> overrideByContactId = new HashMap<Integer, ContactJurisdictionAccess>();
            for (ContactJurisdictionAccess override : overrides) {
                overrideByContactId.put(Integer.valueOf(override.getContactId()), override);
            }

            Map<Integer, Contact> contactById = loadContactById(session);
            List<Contact> localContacts = loadContactsByJurisdiction(session, jurisdiction.getJurisdictionId(),
                    localSearch);
            List<Contact> globalContacts = loadContactsOutsideJurisdiction(session, jurisdiction.getJurisdictionId(),
                    globalSearch);
            Contact primaryReporter = null;
            for (ContactJurisdictionAccess override : overrides) {
                if (override.getAccessRole() == JurisdictionAccessRole.PRIMARY_REPORTER) {
                    primaryReporter = contactById.get(Integer.valueOf(override.getContactId()));
                    break;
                }
            }

            out.println("<h3>Jurisdiction Details</h3>");
            out.println("<form method=\"POST\" class=\"w3-container\" style=\"max-width: 720px; padding-left: 0;\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\""
                    + ACTION_SAVE_JURISDICTION_DETAILS + "\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_TARGET_JURISDICTION_ID + "\" value=\""
                    + jurisdiction.getJurisdictionId() + "\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_LOCAL_CONTACT_SEARCH + "\" value=\""
                    + escapeHtml(safe(localSearch)) + "\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_GLOBAL_CONTACT_SEARCH + "\" value=\""
                    + escapeHtml(safe(globalSearch)) + "\">");
            out.println("  <p><label>Display Label</label><input class=\"w3-input\" type=\"text\" name=\""
                    + PARAM_JURISDICTION_DISPLAY_LABEL + "\" value=\""
                    + escapeHtml(safe(jurisdiction.getDisplayLabel())) + "\"></p>");
            out.println("  <p><label>Map Link</label><input class=\"w3-input\" type=\"text\" name=\""
                    + PARAM_JURISDICTION_MAP_LINK + "\" value=\""
                    + escapeHtml(safe(jurisdiction.getMapLink())) + "\"></p>");
            out.println("  <p><button class=\"w3-button w3-green\" type=\"submit\">Save Jurisdiction</button></p>");
            out.println("</form>");

            out.println("<hr>");
            out.println("<h3>Primary Reporter</h3>");
            out.println("<p>Current: <strong>"
                    + escapeHtml(primaryReporter == null ? "Unassigned" : formatContactLabel(primaryReporter))
                    + "</strong></p>");
            if (primaryReporter != null) {
                out.println("<form method=\"POST\" style=\"margin-bottom: 16px;\">");
                out.println("  <input type=\"hidden\" name=\"" + PARAM_ACTION + "\" value=\""
                        + ACTION_REMOVE_JURISDICTION_ROLE + "\">");
                out.println("  <input type=\"hidden\" name=\"" + PARAM_TARGET_JURISDICTION_ID + "\" value=\""
                        + jurisdiction.getJurisdictionId() + "\">");
                out.println("  <input type=\"hidden\" name=\"" + PARAM_TARGET_CONTACT_ID + "\" value=\""
                        + primaryReporter.getContactId() + "\">");
                out.println("  <input type=\"hidden\" name=\"" + PARAM_LOCAL_CONTACT_SEARCH + "\" value=\""
                        + escapeHtml(safe(localSearch)) + "\">");
                out.println("  <input type=\"hidden\" name=\"" + PARAM_GLOBAL_CONTACT_SEARCH + "\" value=\""
                        + escapeHtml(safe(globalSearch)) + "\">");
                out.println(
                        "  <button class=\"w3-button w3-small w3-red\" type=\"submit\">Remove Primary Reporter</button>");
                out.println("</form>");
            }

            out.println("<h4>Find Contact In This Jurisdiction</h4>");
            out.println(
                    "<form method=\"GET\" class=\"w3-container\" style=\"max-width: 720px; padding-left: 0;\" action=\"/clear/admin/jurisdictions/edit\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_JURISDICTION_ID + "\" value=\""
                    + jurisdiction.getJurisdictionId() + "\">");
            out.println("  <p><input class=\"w3-input\" type=\"text\" name=\"" + PARAM_LOCAL_CONTACT_SEARCH
                    + "\" value=\"" + escapeHtml(safe(localSearch))
                    + "\" placeholder=\"Type part of first name, last name, or email\"></p>");
            out.println("  <p><button class=\"w3-button w3-blue\" type=\"submit\">Search Local Contacts</button></p>");
            out.println("</form>");

            out.println("<table class=\"w3-table w3-bordered w3-striped\">");
            out.println(
                    "  <tr><th>Contact</th><th>Role</th><th>Primary</th><th>Secondary</th><th>Not Authorized</th><th>Viewer</th><th>Remove Override</th></tr>");
            for (Contact contact : localContacts) {
                ContactJurisdictionAccess override = overrideByContactId.get(Integer.valueOf(contact.getContactId()));
                JurisdictionAccessRole effectiveRole = override == null
                        ? JurisdictionAccessRole.VIEWER
                        : override.getAccessRole();
                out.println("  <tr>");
                out.println("    <td>" + escapeHtml(formatContactLabel(contact)) + "</td>");
                out.println("    <td>" + escapeHtml(formatRoleLabel(effectiveRole))
                        + (override == null ? " (implicit)" : " (explicit)") + "</td>");
                out.println("    <td>" + buildRoleActionForm(jurisdiction.getJurisdictionId(), contact.getContactId(),
                        ACTION_SET_JURISDICTION_PRIMARY_REPORTER, null, "Set Primary", localSearch, globalSearch)
                        + "</td>");
                out.println("    <td>" + buildRoleActionForm(jurisdiction.getJurisdictionId(), contact.getContactId(),
                        ACTION_SET_JURISDICTION_ROLE, JurisdictionAccessRole.SECONDARY_REPORTER, "Set Secondary",
                        localSearch, globalSearch) + "</td>");
                out.println("    <td>" + buildRoleActionForm(jurisdiction.getJurisdictionId(), contact.getContactId(),
                        ACTION_SET_JURISDICTION_ROLE, JurisdictionAccessRole.NOT_AUTHORIZED, "Set Not Authorized",
                        localSearch, globalSearch) + "</td>");
                out.println("    <td>" + buildRoleActionForm(jurisdiction.getJurisdictionId(), contact.getContactId(),
                        ACTION_SET_JURISDICTION_ROLE, JurisdictionAccessRole.VIEWER, "Set Viewer", localSearch,
                        globalSearch) + "</td>");
                out.println("    <td>" + buildRoleActionForm(jurisdiction.getJurisdictionId(), contact.getContactId(),
                        ACTION_REMOVE_JURISDICTION_ROLE, null, "Remove", localSearch, globalSearch) + "</td>");
                out.println("  </tr>");
            }
            out.println("</table>");

            out.println("<details style=\"margin-top: 24px;\">");
            out.println("  <summary><strong>Search all contacts (outside jurisdiction)</strong></summary>");
            out.println("  <p>Use this only when a reporter should come from another jurisdiction.</p>");
            out.println(
                    "  <form method=\"GET\" class=\"w3-container\" style=\"max-width: 720px; padding-left: 0;\" action=\"/clear/admin/jurisdictions/edit\">");
            out.println("    <input type=\"hidden\" name=\"" + PARAM_JURISDICTION_ID + "\" value=\""
                    + jurisdiction.getJurisdictionId() + "\">");
            out.println("    <input type=\"hidden\" name=\"" + PARAM_LOCAL_CONTACT_SEARCH + "\" value=\""
                    + escapeHtml(safe(localSearch)) + "\">");
            out.println("    <p><input class=\"w3-input\" type=\"text\" name=\"" + PARAM_GLOBAL_CONTACT_SEARCH
                    + "\" value=\"" + escapeHtml(safe(globalSearch))
                    + "\" placeholder=\"Type part of name or email\"></p>");
            out.println("    <p><button class=\"w3-button w3-blue\" type=\"submit\">Search All Contacts</button></p>");
            out.println("  </form>");

            if (globalSearch != null) {
                out.println("  <table class=\"w3-table w3-bordered w3-striped\">");
                out.println(
                        "    <tr><th>Contact</th><th>Home Jurisdiction</th><th>Primary</th><th>Secondary</th><th>Not Authorized</th><th>Viewer</th></tr>");
                Map<Integer, Jurisdiction> jurisdictionById = loadJurisdictionById(session);
                for (Contact contact : globalContacts) {
                    Jurisdiction homeJurisdiction = jurisdictionById.get(Integer.valueOf(contact.getJurisdictionId()));
                    out.println("    <tr>");
                    out.println("      <td>" + escapeHtml(formatContactLabel(contact)) + "</td>");
                    out.println("      <td>"
                            + escapeHtml(homeJurisdiction == null ? "Unknown" : homeJurisdiction.getDisplayLabel())
                            + "</td>");
                    out.println(
                            "      <td>" + buildRoleActionForm(jurisdiction.getJurisdictionId(), contact.getContactId(),
                                    ACTION_SET_JURISDICTION_PRIMARY_REPORTER, null, "Set Primary", localSearch,
                                    globalSearch) + "</td>");
                    out.println(
                            "      <td>" + buildRoleActionForm(jurisdiction.getJurisdictionId(), contact.getContactId(),
                                    ACTION_SET_JURISDICTION_ROLE, JurisdictionAccessRole.SECONDARY_REPORTER,
                                    "Set Secondary", localSearch, globalSearch) + "</td>");
                    out.println(
                            "      <td>" + buildRoleActionForm(jurisdiction.getJurisdictionId(), contact.getContactId(),
                                    ACTION_SET_JURISDICTION_ROLE, JurisdictionAccessRole.NOT_AUTHORIZED,
                                    "Set Not Authorized", localSearch, globalSearch) + "</td>");
                    out.println("      <td>" + buildRoleActionForm(jurisdiction.getJurisdictionId(),
                            contact.getContactId(),
                            ACTION_SET_JURISDICTION_ROLE, JurisdictionAccessRole.VIEWER, "Set Viewer", localSearch,
                            globalSearch) + "</td>");
                    out.println("    </tr>");
                }
                out.println("  </table>");
            }
            out.println("</details>");

            out.println("<hr>");
            out.println("<h3>Explicit Overrides For This Jurisdiction</h3>");
            out.println("<table class=\"w3-table w3-bordered w3-striped\">");
            out.println(
                    "  <tr><th>Contact</th><th>Home Jurisdiction</th><th>Role</th><th>Updated</th><th>Action</th></tr>");
            SimpleDateFormat accessDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            Map<Integer, Jurisdiction> jurisdictionById = loadJurisdictionById(session);
            for (ContactJurisdictionAccess override : overrides) {
                Contact contact = contactById.get(Integer.valueOf(override.getContactId()));
                Jurisdiction homeJurisdiction = contact == null
                        ? null
                        : jurisdictionById.get(Integer.valueOf(contact.getJurisdictionId()));
                out.println("  <tr>");
                out.println("    <td>" + escapeHtml(contact == null
                        ? "Unknown contact #" + override.getContactId()
                        : formatContactLabel(contact)) + "</td>");
                out.println("    <td>" + escapeHtml(homeJurisdiction == null ? "" : homeJurisdiction.getDisplayLabel())
                        + "</td>");
                out.println("    <td>" + escapeHtml(formatRoleLabel(override.getAccessRole())) + "</td>");
                out.println("    <td>" + escapeHtml(override.getDateUpdated() == null
                        ? ""
                        : accessDateFormat.format(override.getDateUpdated())) + "</td>");
                out.println("    <td>" + buildRoleActionForm(jurisdiction.getJurisdictionId(), override.getContactId(),
                        ACTION_REMOVE_JURISDICTION_ROLE, null, "Remove", localSearch, globalSearch) + "</td>");
                out.println("  </tr>");
            }
            out.println("</table>");

            printFooter(out);
        } finally {
            session.close();
            out.close();
        }
    }

    private List<JurisdictionEditRow> readJurisdictionRows(HttpServletRequest req) {
        String[] jurisdictionIds = req.getParameterValues(PARAM_JURISDICTION_ID);
        String[] displayLabels = req.getParameterValues(PARAM_JURISDICTION_DISPLAY_LABEL);
        String[] mapLinks = req.getParameterValues(PARAM_JURISDICTION_MAP_LINK);
        if (jurisdictionIds == null || displayLabels == null || mapLinks == null
                || jurisdictionIds.length != displayLabels.length || jurisdictionIds.length != mapLinks.length) {
            throw new IllegalArgumentException("Submitted jurisdiction rows are incomplete.");
        }

        List<JurisdictionEditRow> rows = new ArrayList<JurisdictionEditRow>();
        for (int i = 0; i < jurisdictionIds.length; i++) {
            rows.add(new JurisdictionEditRow(parseRequiredInteger(jurisdictionIds[i], "Jurisdiction id"),
                    displayLabels[i], mapLinks[i]));
        }
        return rows;
    }

    private Map<Integer, Jurisdiction> loadJurisdictionById(Session session) {
        Map<Integer, Jurisdiction> jurisdictionById = new HashMap<Integer, Jurisdiction>();
        for (Jurisdiction jurisdiction : loadJurisdictions(session)) {
            jurisdictionById.put(Integer.valueOf(jurisdiction.getJurisdictionId()), jurisdiction);
        }
        return jurisdictionById;
    }

    private List<JurisdictionEditRow> loadJurisdictionEditRows(Session session) {
        List<JurisdictionEditRow> rows = new ArrayList<JurisdictionEditRow>();
        for (Jurisdiction jurisdiction : loadJurisdictions(session)) {
            rows.add(new JurisdictionEditRow(Integer.valueOf(jurisdiction.getJurisdictionId()),
                    jurisdiction.getDisplayLabel(), jurisdiction.getMapLink()));
        }
        return rows;
    }

    private Map<Integer, Contact> loadPrimaryReporterByJurisdiction(Session session) {
        Map<Integer, Contact> primaryReporterByJurisdiction = new HashMap<Integer, Contact>();
        Map<Integer, Contact> contactById = loadContactById(session);
        Query<ContactJurisdictionAccess> query = session.createQuery(
                "FROM ContactJurisdictionAccess WHERE accessRole = :accessRole ORDER BY jurisdictionId, dateUpdated DESC",
                ContactJurisdictionAccess.class);
        query.setParameter("accessRole", JurisdictionAccessRole.PRIMARY_REPORTER);
        for (ContactJurisdictionAccess access : query.list()) {
            Integer jurisdictionId = Integer.valueOf(access.getJurisdictionId());
            if (!primaryReporterByJurisdiction.containsKey(jurisdictionId)) {
                primaryReporterByJurisdiction.put(jurisdictionId,
                        contactById.get(Integer.valueOf(access.getContactId())));
            }
        }
        return primaryReporterByJurisdiction;
    }

    private List<ContactJurisdictionAccess> loadAccessByJurisdiction(Session session, int jurisdictionId) {
        Query<ContactJurisdictionAccess> query = session.createQuery(
                "FROM ContactJurisdictionAccess WHERE jurisdictionId = :jurisdictionId ORDER BY accessRole, contactId",
                ContactJurisdictionAccess.class);
        query.setParameter("jurisdictionId", Integer.valueOf(jurisdictionId));
        return query.list();
    }

    private List<Contact> loadContactsByJurisdiction(Session session, int jurisdictionId, String searchText) {
        StringBuilder hql = new StringBuilder(
                "FROM Contact WHERE jurisdictionId = :jurisdictionId");
        boolean hasSearch = searchText != null;
        if (hasSearch) {
            hql.append(
                    " AND (lower(nameFirst) LIKE :search OR lower(nameLast) LIKE :search OR lower(emailAddress) LIKE :search)");
        }
        hql.append(" ORDER BY nameLast, nameFirst, emailAddress");
        Query<Contact> query = session.createQuery(hql.toString(), Contact.class);
        query.setParameter("jurisdictionId", Integer.valueOf(jurisdictionId));
        if (hasSearch) {
            query.setParameter("search", "%" + searchText.toLowerCase(Locale.ROOT) + "%");
        }
        query.setMaxResults(200);
        return query.list();
    }

    private List<Contact> loadContactsOutsideJurisdiction(Session session, int jurisdictionId, String searchText) {
        if (searchText == null) {
            return new ArrayList<Contact>();
        }
        Query<Contact> query = session.createQuery(
                "FROM Contact WHERE jurisdictionId <> :jurisdictionId "
                        + "AND (lower(nameFirst) LIKE :search OR lower(nameLast) LIKE :search OR lower(emailAddress) LIKE :search) "
                        + "ORDER BY nameLast, nameFirst, emailAddress",
                Contact.class);
        query.setParameter("jurisdictionId", Integer.valueOf(jurisdictionId));
        query.setParameter("search", "%" + searchText.toLowerCase(Locale.ROOT) + "%");
        query.setMaxResults(30);
        return query.list();
    }

    private Map<Integer, Contact> loadContactById(Session session) {
        Map<Integer, Contact> contactById = new HashMap<Integer, Contact>();
        for (Contact contact : loadContacts(session)) {
            contactById.put(Integer.valueOf(contact.getContactId()), contact);
        }
        return contactById;
    }

    private void upsertJurisdictionAccessRole(Session session, Contact contact, int jurisdictionId,
            JurisdictionAccessRole accessRole, SessionUser sessionUser) {
        ContactJurisdictionAccess existing = findExistingAccessOverride(session, null,
                Integer.valueOf(contact.getContactId()), Integer.valueOf(jurisdictionId));
        if (accessRole == JurisdictionAccessRole.VIEWER && contact.getJurisdictionId() == jurisdictionId) {
            if (existing != null) {
                session.remove(existing);
            }
            return;
        }

        if (existing == null) {
            existing = new ContactJurisdictionAccess();
            existing.setContactId(contact.getContactId());
            existing.setJurisdictionId(jurisdictionId);
            existing.setDateCreated(new Date());
        }
        existing.setAccessRole(accessRole);
        existing.setDateUpdated(new Date());
        existing.setUpdatedByContactId(sessionUser.getContactId());
        session.saveOrUpdate(existing);
    }

    private void removeJurisdictionAccessRole(Session session, int contactId, int jurisdictionId) {
        ContactJurisdictionAccess existing = findExistingAccessOverride(session, null,
                Integer.valueOf(contactId), Integer.valueOf(jurisdictionId));
        if (existing != null) {
            session.remove(existing);
        }
    }

    private String buildRoleActionForm(int jurisdictionId, int contactId, String action,
            JurisdictionAccessRole role, String buttonLabel, String localSearch, String globalSearch) {
        StringBuilder html = new StringBuilder();
        html.append("<form method=\"POST\" style=\"margin:0\">");
        html.append("<input type=\"hidden\" name=\"")
                .append(PARAM_ACTION)
                .append("\" value=\"")
                .append(action)
                .append("\">");
        html.append("<input type=\"hidden\" name=\"")
                .append(PARAM_TARGET_JURISDICTION_ID)
                .append("\" value=\"")
                .append(jurisdictionId)
                .append("\">");
        html.append("<input type=\"hidden\" name=\"")
                .append(PARAM_TARGET_CONTACT_ID)
                .append("\" value=\"")
                .append(contactId)
                .append("\">");
        if (role != null) {
            html.append("<input type=\"hidden\" name=\"")
                    .append(PARAM_TARGET_ROLE)
                    .append("\" value=\"")
                    .append(role.name())
                    .append("\">");
        }
        html.append("<input type=\"hidden\" name=\"")
                .append(PARAM_LOCAL_CONTACT_SEARCH)
                .append("\" value=\"")
                .append(escapeHtml(safe(localSearch)))
                .append("\">");
        html.append("<input type=\"hidden\" name=\"")
                .append(PARAM_GLOBAL_CONTACT_SEARCH)
                .append("\" value=\"")
                .append(escapeHtml(safe(globalSearch)))
                .append("\">");
        html.append("<button class=\"w3-button w3-tiny w3-blue\" type=\"submit\">")
                .append(escapeHtml(buttonLabel))
                .append("</button>");
        html.append("</form>");
        return html.toString();
    }

    private String joinErrors(String prefix, String error) {
        List<String> errors = new ArrayList<String>();
        errors.add(error);
        return joinErrors(prefix, errors);
    }

    private String joinErrors(String prefix, List<String> errors) {
        StringBuilder message = new StringBuilder(prefix);
        if (errors != null && !errors.isEmpty()) {
            message.append(": ");
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) {
                    message.append(" ");
                }
                message.append(errors.get(i));
            }
        }
        return message.toString();
    }

    private List<Contact> loadContacts(Session session) {
        Query<Contact> query = session.createQuery("FROM Contact ORDER BY nameLast, nameFirst, emailAddress",
                Contact.class);
        return query.list();
    }

    private List<Jurisdiction> loadJurisdictions(Session session) {
        Query<Jurisdiction> query = session.createQuery("FROM Jurisdiction ORDER BY displayLabel", Jurisdiction.class);
        return query.list();
    }

    private List<AccessOverrideDisplay> loadAccessOverrides(Session session) {
        Map<Integer, Contact> contactById = new HashMap<Integer, Contact>();
        for (Contact contact : loadContacts(session)) {
            contactById.put(contact.getContactId(), contact);
        }
        Map<Integer, Jurisdiction> jurisdictionById = new HashMap<Integer, Jurisdiction>();
        for (Jurisdiction jurisdiction : loadJurisdictions(session)) {
            jurisdictionById.put(jurisdiction.getJurisdictionId(), jurisdiction);
        }

        Query<ContactJurisdictionAccess> query = session.createQuery(
                "FROM ContactJurisdictionAccess ORDER BY jurisdictionId, contactId", ContactJurisdictionAccess.class);
        List<AccessOverrideDisplay> accessOverrides = new ArrayList<AccessOverrideDisplay>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (ContactJurisdictionAccess access : query.list()) {
            Contact contact = contactById.get(access.getContactId());
            Jurisdiction overrideJurisdiction = jurisdictionById.get(access.getJurisdictionId());
            AccessOverrideDisplay display = new AccessOverrideDisplay();
            display.accessOverrideId = access.getContactJurisdictionAccessId();
            display.contactLabel = contact == null ? "Unknown contact #" + access.getContactId()
                    : formatContactLabel(contact);
            Jurisdiction homeJurisdiction = contact == null ? null : jurisdictionById.get(contact.getJurisdictionId());
            display.homeJurisdictionLabel = homeJurisdiction == null ? "" : homeJurisdiction.getDisplayLabel();
            display.overrideJurisdictionLabel = overrideJurisdiction == null
                    ? "Unknown jurisdiction #" + access.getJurisdictionId()
                    : overrideJurisdiction.getDisplayLabel();
            display.accessRole = access.getAccessRole();
            display.updatedLabel = access.getDateUpdated() == null ? "" : sdf.format(access.getDateUpdated());
            accessOverrides.add(display);
        }
        return accessOverrides;
    }

    private ContactJurisdictionAccess findExistingAccessOverride(Session session, Integer overrideId, Integer contactId,
            Integer jurisdictionId) {
        if (overrideId != null) {
            return session.get(ContactJurisdictionAccess.class, overrideId);
        }
        Query<ContactJurisdictionAccess> query = session.createQuery(
                "FROM ContactJurisdictionAccess WHERE contactId = :contactId AND jurisdictionId = :jurisdictionId",
                ContactJurisdictionAccess.class);
        query.setParameter("contactId", contactId);
        query.setParameter("jurisdictionId", jurisdictionId);
        List<ContactJurisdictionAccess> results = query.list();
        return results.isEmpty() ? null : results.get(0);
    }

    private void ensurePrimaryReporterAvailable(Session session, int jurisdictionId, Integer currentOverrideId) {
        Query<ContactJurisdictionAccess> query = session.createQuery(
                "FROM ContactJurisdictionAccess WHERE jurisdictionId = :jurisdictionId AND accessRole = :accessRole",
                ContactJurisdictionAccess.class);
        query.setParameter("jurisdictionId", jurisdictionId);
        query.setParameter("accessRole", JurisdictionAccessRole.PRIMARY_REPORTER);
        for (ContactJurisdictionAccess existing : query.list()) {
            if (currentOverrideId == null
                    || existing.getContactJurisdictionAccessId() != currentOverrideId.intValue()) {
                throw new IllegalArgumentException("This jurisdiction already has a primary reporter override.");
            }
        }
    }

    private Integer parseInteger(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return Integer.valueOf(normalized);
    }

    private Integer parseRequiredInteger(String value, String label) {
        Integer parsed = parseInteger(value);
        if (parsed == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return parsed;
    }

    private JurisdictionAccessRole parseAccessRole(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Access role is required.");
        }
        try {
            return JurisdictionAccessRole.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Access role is invalid.");
        }
    }

    private String formatContactLabel(Contact contact) {
        StringBuilder label = new StringBuilder();
        if (normalize(contact.getNameLast()) != null) {
            label.append(contact.getNameLast());
        }
        if (normalize(contact.getNameFirst()) != null) {
            if (label.length() > 0) {
                label.append(", ");
            }
            label.append(contact.getNameFirst());
        }
        if (normalize(contact.getEmailAddress()) != null) {
            if (label.length() > 0) {
                label.append(" - ");
            }
            label.append(contact.getEmailAddress());
        }
        if (label.length() == 0) {
            return "Contact #" + contact.getContactId();
        }
        return label.toString();
    }

    private String formatRoleLabel(JurisdictionAccessRole accessRole) {
        return accessRole.name().replace('_', ' ').toLowerCase(Locale.ROOT);
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
        out.println("        <a href=\"/clear/?view=data\" class=\"w3-bar-item w3-button\">Data</a> ");
        out.println("        <a href=\"/clear/?view=map\" class=\"w3-bar-item w3-button\">Map</a> ");
        out.println("        <a href=\"/clear/email\" class=\"w3-bar-item w3-button\">Mail</a> ");
        out.println("        <a href=\"/clear/admin\" class=\"w3-bar-item w3-button\">Admin</a> ");
        if (sessionUser.isAdmin()) {
            out.println(
                    "        <a href=\"/clear/admin/jurisdictions\" class=\"w3-bar-item w3-button\">Jurisdictions</a> ");
            out.println("        <a href=\"/clear/admin/contacts\" class=\"w3-bar-item w3-button\">Contacts</a> ");
        }
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

    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    private static class AccessOverrideDisplay {
        private int accessOverrideId;
        private String contactLabel;
        private String homeJurisdictionLabel;
        private String overrideJurisdictionLabel;
        private JurisdictionAccessRole accessRole;
        private String updatedLabel;
    }

    private static class ContactCsvParseResult {
        private final Map<String, ContactCsvRow> rowsByEmail = new LinkedHashMap<String, ContactCsvRow>();
        private final List<String> errors = new ArrayList<String>();
    }

    private static class ContactCsvRow {
        private String jurisdictionLabel;
        private String jurisdictionKey;
        private String firstName;
        private String lastName;
        private String email;
        private String emailKey;
    }
}
