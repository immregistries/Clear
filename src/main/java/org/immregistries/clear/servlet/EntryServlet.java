package org.immregistries.clear.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.immregistries.clear.auth.ClearAuthSessionSupport;
import org.immregistries.clear.auth.SessionUser;
import org.immregistries.clear.model.EntryForInterop;
import org.immregistries.clear.model.Jurisdiction;
import org.immregistries.clear.service.JurisdictionAccessService;
import org.immregistries.clear.utils.HibernateUtil;

public class EntryServlet extends ClearServlet {

    private static final String PARAM_YEAR = "year";
    private static final String PARAM_UPDATES_PREFIX = "month";
    private static final String PARAM_QUERIES_SUFFIX = "-queries";
    private static final String PARAM_UPDATES_SUFFIX = "-updates";

    private final JurisdictionAccessService jurisdictionAccessService = new JurisdictionAccessService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleRequest(req, resp, false);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleRequest(req, resp, true);
    }

    private void handleRequest(HttpServletRequest req, HttpServletResponse resp, boolean isPost)
            throws IOException {
        resp.setContentType("text/html");

        SessionUser sessionUser = ClearAuthSessionSupport.getSessionUser(req);
        if (sessionUser == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        Session session = HibernateUtil.getSessionFactory().openSession();
        PrintWriter out = new PrintWriter(resp.getOutputStream());
        try {
            List<Jurisdiction> allJurisdictions = session
                    .createQuery("from Jurisdiction order by displayLabel", Jurisdiction.class)
                    .list();
            List<Jurisdiction> accessibleJurisdictions = jurisdictionAccessService.getAccessibleJurisdictions(
                    session,
                    sessionUser,
                    allJurisdictions);

            Jurisdiction selectedJurisdiction = selectJurisdiction(req, sessionUser, accessibleJurisdictions);
            if (selectedJurisdiction == null) {
                printHeader(out, "", sessionUser);
                out.println("<h4>No jurisdiction access is available for your account.</h4>");
                printFooter(out);
                return;
            }

            String selectedJurisdictionMapLink = selectedJurisdiction.getMapLink().replace(' ', '-');
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            int selectedYear = parseSelectedYear(req.getParameter(PARAM_YEAR), currentYear);
            if (selectedYear > currentYear) {
                selectedYear = currentYear;
            }

            String message = null;
            boolean error = false;
            Map<Integer, String> submittedUpdates = new HashMap<Integer, String>();
            Map<Integer, String> submittedQueries = new HashMap<Integer, String>();

            if (isPost && ACTION_SAVE.equals(req.getParameter(PARAM_ACTION))) {
                if (!jurisdictionAccessService.canEdit(session, sessionUser,
                        selectedJurisdiction.getJurisdictionId())) {
                    message = "You are not authorized to edit data for this jurisdiction.";
                    error = true;
                } else {
                    SaveResult saveResult = saveYearData(req, session, sessionUser, selectedJurisdiction, selectedYear,
                            submittedUpdates, submittedQueries);
                    if (!saveResult.errors.isEmpty()) {
                        error = true;
                        message = String.join("<br>", saveResult.errors);
                    } else {
                        resp.sendRedirect(req.getContextPath() + "/results?" + PARAM_JURISDICTION + "="
                                + URLEncoder.encode(selectedJurisdictionMapLink, StandardCharsets.UTF_8));
                        return;
                    }
                }
            }

            renderPage(out, sessionUser, accessibleJurisdictions, selectedJurisdictionMapLink, selectedYear,
                    currentYear, selectedJurisdiction, message, error, submittedUpdates, submittedQueries, session);
        } catch (Exception e) {
            out.println("<h3>Exception</h3>");
            out.println("<p>" + e.getMessage() + "</p>");
            out.println("<pre>");
            e.printStackTrace(out);
            out.println("</pre>");
        } finally {
            if (out != null) {
                out.close();
            }
            if (session != null) {
                session.close();
            }
        }
    }

    private SaveResult saveYearData(HttpServletRequest req, Session session, SessionUser sessionUser,
            Jurisdiction selectedJurisdiction, int selectedYear, Map<Integer, String> submittedUpdates,
            Map<Integer, String> submittedQueries) {
        SaveResult saveResult = new SaveResult();
        Calendar now = Calendar.getInstance();
        int currentYear = now.get(Calendar.YEAR);
        int currentMonth = now.get(Calendar.MONTH);

        Date yearStart = getMonthStart(selectedYear, Calendar.JANUARY);
        Date yearEnd = getMonthStart(selectedYear + 1, Calendar.JANUARY);
        Query<EntryForInterop> query = session.createQuery(
                "from EntryForInterop where jurisdiction = :jurisdiction and reportingPeriod >= :start and reportingPeriod < :end",
                EntryForInterop.class);
        query.setParameter("jurisdiction", selectedJurisdiction);
        query.setParameter("start", yearStart);
        query.setParameter("end", yearEnd);

        Map<Integer, EntryForInterop> entriesByMonth = new HashMap<Integer, EntryForInterop>();
        for (EntryForInterop entry : query.list()) {
            Calendar rowCalendar = Calendar.getInstance();
            rowCalendar.setTime(entry.getReportingPeriod());
            entriesByMonth.put(rowCalendar.get(Calendar.MONTH), entry);
        }

        int[] updatesByMonth = new int[12];
        int[] queriesByMonth = new int[12];
        SimpleDateFormat monthNameFormat = new SimpleDateFormat("MMMM");
        for (int monthIndex = 0; monthIndex < 12; monthIndex++) {
            String updatesParam = getUpdatesParamName(monthIndex);
            String queriesParam = getQueriesParamName(monthIndex);
            String updatesValue = trimOrEmpty(req.getParameter(updatesParam));
            String queriesValue = trimOrEmpty(req.getParameter(queriesParam));
            submittedUpdates.put(Integer.valueOf(monthIndex), updatesValue);
            submittedQueries.put(Integer.valueOf(monthIndex), queriesValue);

            if (isCurrentOrFutureMonth(selectedYear, monthIndex, currentYear, currentMonth)) {
                continue;
            }

            Date monthDate = getMonthStart(selectedYear, monthIndex);
            String monthName = monthNameFormat.format(monthDate);
            try {
                updatesByMonth[monthIndex] = parseCount(updatesValue);
            } catch (NumberFormatException nfe) {
                saveResult.errors.add(monthName + " updates must be a whole number.");
            }
            try {
                queriesByMonth[monthIndex] = parseCount(queriesValue);
            } catch (NumberFormatException nfe) {
                saveResult.errors.add(monthName + " queries must be a whole number.");
            }
        }

        if (!saveResult.errors.isEmpty()) {
            return saveResult;
        }

        Transaction transaction = session.beginTransaction();
        try {
            for (int monthIndex = 0; monthIndex < 12; monthIndex++) {
                if (isCurrentOrFutureMonth(selectedYear, monthIndex, currentYear, currentMonth)) {
                    continue;
                }

                EntryForInterop existingEntry = entriesByMonth.get(Integer.valueOf(monthIndex));
                int updates = updatesByMonth[monthIndex];
                int queriesCount = queriesByMonth[monthIndex];
                if (existingEntry != null) {
                    existingEntry.setCountUpdate(updates);
                    existingEntry.setCountQuery(queriesCount);
                    session.update(existingEntry);
                } else if (updates > 0 || queriesCount > 0) {
                    EntryForInterop newEntry = new EntryForInterop();
                    newEntry.setCountUpdate(updates);
                    newEntry.setCountQuery(queriesCount);
                    newEntry.setReportingPeriod(getMonthStart(selectedYear, monthIndex));
                    newEntry.setJurisdiction(selectedJurisdiction);
                    if (sessionUser.getContactId() != null) {
                        newEntry.setContactId(sessionUser.getContactId().intValue());
                    }
                    session.save(newEntry);
                }
            }
            transaction.commit();
        } catch (RuntimeException e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            saveResult.errors.add("Unable to save data: " + e.getMessage());
        }
        return saveResult;
    }

    private void renderPage(PrintWriter out, SessionUser sessionUser, List<Jurisdiction> accessibleJurisdictions,
            String selectedJurisdictionMapLink, int selectedYear, int currentYear, Jurisdiction selectedJurisdiction,
            String message, boolean error, Map<Integer, String> submittedUpdates, Map<Integer, String> submittedQueries,
            Session session) {
        printHeader(out, selectedJurisdictionMapLink, sessionUser);
        if (message != null) {
            out.println("<p class=\"" + (error ? "w3-text-red" : "w3-text-green") + "\">" + message + "</p>");
        }

        out.println("<h3>" + selectedJurisdiction.getDisplayLabel() + "</h3>");
        printJurisdictionSelector(out, accessibleJurisdictions, selectedJurisdictionMapLink, selectedYear);

        out.println("<div class=\"w3-container\" style=\"margin-bottom:16px;padding-left:0;\">");
        out.println("<form method=\"get\" action=\"/clear/enter\" style=\"display:inline;\">");
        out.println("  <input type=\"hidden\" name=\"" + PARAM_JURISDICTION + "\" value=\""
                + selectedJurisdictionMapLink + "\">");
        out.println("  <button class=\"w3-button\" type=\"submit\" name=\"" + PARAM_YEAR + "\" value=\""
                + (selectedYear - 1) + "\">&larr; " + (selectedYear - 1) + "</button>");
        out.println("</form>");
        out.println("<span style=\"margin:0 16px;font-weight:bold;\">" + selectedYear + "</span>");
        if (selectedYear < currentYear) {
            out.println("<form method=\"get\" action=\"/clear/enter\" style=\"display:inline;\">");
            out.println("  <input type=\"hidden\" name=\"" + PARAM_JURISDICTION + "\" value=\""
                    + selectedJurisdictionMapLink + "\">");
            out.println("  <button class=\"w3-button\" type=\"submit\" name=\"" + PARAM_YEAR + "\" value=\""
                    + (selectedYear + 1) + "\">" + (selectedYear + 1) + " &rarr;</button>");
            out.println("</form>");
        }
        out.println("</div>");

        Map<Integer, EntryForInterop> existingEntriesByMonth = loadExistingEntriesByMonth(session, selectedJurisdiction,
                selectedYear);
        SimpleDateFormat monthNameFormat = new SimpleDateFormat("MMMM");
        Calendar now = Calendar.getInstance();
        int currentMonth = now.get(Calendar.MONTH);

        out.println("<div class=\"w3-container\" style=\"max-width:560px;padding-left:0;\">");
        out.println("<form action=\"/clear/enter\" method=\"post\">");
        out.println("  <input type=\"hidden\" name=\"" + PARAM_JURISDICTION + "\" value=\""
                + selectedJurisdictionMapLink + "\">");
        out.println("  <input type=\"hidden\" name=\"" + PARAM_YEAR + "\" value=\"" + selectedYear + "\">");
        out.println("  <table class=\"w3-table w3-striped\">");
        out.println("    <tr><th>Month</th><th>Updates</th><th>Queries</th></tr>");

        for (int monthIndex = 0; monthIndex < 12; monthIndex++) {
            Date monthDate = getMonthStart(selectedYear, monthIndex);
            String monthLabel = monthNameFormat.format(monthDate);
            EntryForInterop existingEntry = existingEntriesByMonth.get(Integer.valueOf(monthIndex));

            String updatesValue = "";
            if (submittedUpdates.containsKey(Integer.valueOf(monthIndex))) {
                updatesValue = submittedUpdates.get(Integer.valueOf(monthIndex));
            } else if (existingEntry != null) {
                updatesValue = String.valueOf(existingEntry.getCountUpdate());
            }

            String queriesValue = "";
            if (submittedQueries.containsKey(Integer.valueOf(monthIndex))) {
                queriesValue = submittedQueries.get(Integer.valueOf(monthIndex));
            } else if (existingEntry != null) {
                queriesValue = String.valueOf(existingEntry.getCountQuery());
            }

            boolean disableInputs = isCurrentOrFutureMonth(selectedYear, monthIndex, currentYear, currentMonth);
            String rowClass = disableInputs ? " class=\"w3-light-grey\"" : "";
            String disabledAttribute = disableInputs ? " disabled" : "";
            out.println("    <tr" + rowClass + ">");
            out.println("      <td>" + monthLabel + "</td>");
            out.println("      <td><input class=\"formatted-number\" type=\"text\" name=\""
                    + getUpdatesParamName(monthIndex) + "\" value=\"" + escapeHtml(updatesValue) + "\""
                    + disabledAttribute + "></td>");
            out.println("      <td><input class=\"formatted-number\" type=\"text\" name=\""
                    + getQueriesParamName(monthIndex) + "\" value=\"" + escapeHtml(queriesValue) + "\""
                    + disabledAttribute + "></td>");
            out.println("    </tr>");
        }

        out.println("  </table>");
        out.println("  <input class=\"w3-button w3-green\" type=\"submit\" name=\"" + PARAM_ACTION
                + "\" value=\"" + ACTION_SAVE + "\">");
        out.println("</form>");
        out.println("</div>");

        out.println("<script>");
        out.println("document.addEventListener(\"DOMContentLoaded\", function () {");
        out.println("    document.querySelectorAll(\"input.formatted-number\").forEach(function (input) {");
        out.println("        var rawValue = input.value.replace(/,/g, \"\");");
        out.println("        if (!isNaN(rawValue) && rawValue.length > 0) {");
        out.println("            input.value = Number(rawValue).toLocaleString(\"en-US\");");
        out.println("        }");
        out.println("        input.addEventListener(\"input\", function () {");
        out.println("            var value = this.value.replace(/,/g, \"\");");
        out.println("            if (!isNaN(value) && value.length > 0) {");
        out.println("                this.value = Number(value).toLocaleString(\"en-US\");");
        out.println("            }");
        out.println("        });");
        out.println("    });");
        out.println("    var form = document.querySelector(\"form[action='/clear/enter'][method='post']\");");
        out.println("    if (form) {");
        out.println("        form.addEventListener(\"submit\", function () {");
        out.println("            form.querySelectorAll(\"input.formatted-number\").forEach(function (input) {");
        out.println("                input.value = input.value.replace(/,/g, \"\");");
        out.println("            });");
        out.println("        });");
        out.println("    }");
        out.println("});");
        out.println("</script>");

        printFooter(out);
    }

    private void printJurisdictionSelector(PrintWriter out, List<Jurisdiction> accessibleJurisdictions,
            String selectedJurisdictionMapLink, int selectedYear) {
        if (accessibleJurisdictions == null || accessibleJurisdictions.size() < 2) {
            return;
        }
        out.println(
                "<form action=\"/clear/enter\" method=\"get\" class=\"w3-container\" style=\"max-width: 420px; padding-left: 0;\">");
        out.println("   <input type=\"hidden\" name=\"" + PARAM_YEAR + "\" value=\"" + selectedYear + "\">");
        out.println("   <label for=\"jurisdictionSelector\"><strong>Jurisdiction</strong></label>");
        out.println("   <select id=\"jurisdictionSelector\" class=\"w3-select\" name=\"" + PARAM_JURISDICTION
                + "\" onchange=\"this.form.submit()\">");
        for (Jurisdiction jurisdiction : accessibleJurisdictions) {
            String optionValue = jurisdiction.getMapLink().replace(' ', '-');
            String selected = optionValue.equals(selectedJurisdictionMapLink) ? " selected" : "";
            out.println("      <option value=\"" + optionValue + "\"" + selected + ">"
                    + jurisdiction.getDisplayLabel() + "</option>");
        }
        out.println("   </select>");
        out.println("</form>");
    }

    private Map<Integer, EntryForInterop> loadExistingEntriesByMonth(Session session, Jurisdiction jurisdiction,
            int selectedYear) {
        Date yearStart = getMonthStart(selectedYear, Calendar.JANUARY);
        Date yearEnd = getMonthStart(selectedYear + 1, Calendar.JANUARY);
        Query<EntryForInterop> query = session.createQuery(
                "from EntryForInterop where jurisdiction = :jurisdiction and reportingPeriod >= :start and reportingPeriod < :end",
                EntryForInterop.class);
        query.setParameter("jurisdiction", jurisdiction);
        query.setParameter("start", yearStart);
        query.setParameter("end", yearEnd);

        Map<Integer, EntryForInterop> existingEntriesByMonth = new HashMap<Integer, EntryForInterop>();
        for (EntryForInterop entryForInterop : query.list()) {
            Calendar reportingCal = Calendar.getInstance();
            reportingCal.setTime(entryForInterop.getReportingPeriod());
            existingEntriesByMonth.put(Integer.valueOf(reportingCal.get(Calendar.MONTH)), entryForInterop);
        }
        return existingEntriesByMonth;
    }

    private Jurisdiction selectJurisdiction(HttpServletRequest req, SessionUser sessionUser,
            List<Jurisdiction> accessibleJurisdictions) {
        if (accessibleJurisdictions == null || accessibleJurisdictions.isEmpty()) {
            return null;
        }

        String requestedMapLink = req.getParameter(PARAM_JURISDICTION);
        if (requestedMapLink != null && !requestedMapLink.isEmpty()) {
            String normalizedRequested = requestedMapLink.replace('-', ' ');
            for (Jurisdiction jurisdiction : accessibleJurisdictions) {
                if (jurisdiction.getMapLink().equals(normalizedRequested)) {
                    return jurisdiction;
                }
            }
        }

        if (sessionUser.getJurisdictionId() != null) {
            for (Jurisdiction jurisdiction : accessibleJurisdictions) {
                if (jurisdiction.getJurisdictionId() == sessionUser.getJurisdictionId().intValue()) {
                    return jurisdiction;
                }
            }
        }

        return accessibleJurisdictions.get(0);
    }

    private int parseSelectedYear(String yearParam, int defaultYear) {
        if (yearParam == null || yearParam.trim().isEmpty()) {
            return defaultYear;
        }
        try {
            return Integer.parseInt(yearParam.trim());
        } catch (NumberFormatException nfe) {
            return defaultYear;
        }
    }

    private Date getMonthStart(int year, int monthIndex) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, monthIndex);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        return cal.getTime();
    }

    private boolean isCurrentOrFutureMonth(int selectedYear, int monthIndex, int currentYear, int currentMonth) {
        if (selectedYear > currentYear) {
            return true;
        }
        return selectedYear == currentYear && monthIndex >= currentMonth;
    }

    private int parseCount(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        return Integer.parseInt(value.replace(",", "").trim());
    }

    private String trimOrEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String getUpdatesParamName(int monthIndex) {
        return PARAM_UPDATES_PREFIX + monthIndex + PARAM_UPDATES_SUFFIX;
    }

    private String getQueriesParamName(int monthIndex) {
        return PARAM_UPDATES_PREFIX + monthIndex + PARAM_QUERIES_SUFFIX;
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

    private static class SaveResult {
        private final List<String> errors = new ArrayList<String>();
    }
}