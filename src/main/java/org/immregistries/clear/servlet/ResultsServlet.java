package org.immregistries.clear.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.immregistries.clear.auth.ClearAuthSessionSupport;
import org.immregistries.clear.auth.SessionUser;
import org.immregistries.clear.model.ClearAgreement;
import org.immregistries.clear.model.EntryForInterop;
import org.immregistries.clear.model.Jurisdiction;
import org.immregistries.clear.service.ClearAgreementService;
import org.immregistries.clear.utils.HibernateUtil;

@WebServlet(name = "ResultsServlet", urlPatterns = { "/results" })
public class ResultsServlet extends HttpServlet {
    private static final String PARAM_ACTION = "action";
    private static final String ACTION_ACKNOWLEDGE_AGREEMENT = "acknowledgeAgreement";
    private static final String PARAM_RETURN_TO = "returnTo";
    private static final String PARAM_AGREE = "agree";
    private static final String CHECKBOX_TRUE_VALUE = "on";

    private final ClearAgreementService clearAgreementService = new ClearAgreementService();

    private static String formatInteger(int value) {
        return formatHumanReadableLargeNumber(value);
    }

    private static String formatHumanReadableLargeNumber(long value) {
        if (value >= 1_000_000_000L) {
            String s = String.format(Locale.US, "%.1f", (double) value / 1_000_000_000.0);
            if (s.endsWith(".0")) {
                s = s.substring(0, s.length() - 2);
            }
            return s + " billion";
        } else if (value >= 1_000_000L) {
            String s = String.format(Locale.US, "%.1f", (double) value / 1_000_000.0);
            if (s.endsWith(".0")) {
                s = s.substring(0, s.length() - 2);
            }
            return s + " million";
        } else if (value >= 1_000L) {
            return String.format(Locale.US, "%d thousand", value / 1_000L);
        } else {
            return String.format(Locale.US, "%,d", value);
        }
    }

    private static void rankAndRenderTable(List<RankedRow> allRows, Comparator<RankedRow> cmp,
            String selectedJurisdiction, PrintWriter out, String tableType) {
        List<RankedRow> rows = new ArrayList<RankedRow>();
        for (RankedRow row : allRows) {
            boolean include = false;
            switch (tableType) {
                case "updates":
                    include = row.updates > 0;
                    break;
                case "queries":
                    include = row.queries > 0;
                    break;
                case "updatesPerCapita":
                    include = row.updates > 0 && row.population > 0;
                    break;
                case "queriesPerCapita":
                    include = row.queries > 0 && row.population > 0;
                    break;
                case "queryUpdateRatio":
                    include = row.updates > 0 && row.queries > 0;
                    break;
            }
            if (include) {
                rows.add(row);
            }
        }
        rows.sort(cmp.reversed());
        int rank = 1;
        int prevValue = Integer.MIN_VALUE;
        float prevFloat = Float.NaN;
        int skip = 0;
        for (int i = 0; i < rows.size(); i++) {
            RankedRow row = rows.get(i);
            boolean tie = false;
            switch (tableType) {
                case "updates":
                    tie = row.updates == prevValue;
                    prevValue = row.updates;
                    break;
                case "queries":
                    tie = row.queries == prevValue;
                    prevValue = row.queries;
                    break;
                case "updatesPerCapita":
                    tie = Float.compare(row.updatesPerCapita, prevFloat) == 0;
                    prevFloat = row.updatesPerCapita;
                    break;
                case "queriesPerCapita":
                    tie = Float.compare(row.queriesPerCapita, prevFloat) == 0;
                    prevFloat = row.queriesPerCapita;
                    break;
                case "queryUpdateRatio":
                    tie = Float.compare(row.queryUpdateRatio, prevFloat) == 0;
                    prevFloat = row.queryUpdateRatio;
                    break;
            }
            if (i > 0 && tie) {
                skip++;
            } else if (i > 0) {
                rank += skip + 1;
                skip = 0;
            }
            String highlight = row.jurisdiction.getMapLink().equals(selectedJurisdiction) ? "table-highlight" : "";
            String boldStart = highlight.isEmpty() ? "" : "<b>";
            String boldEnd = highlight.isEmpty() ? "" : "</b>";
            String displayLabel = row.displayLabel == null ? row.jurisdiction.getDisplayLabel() : row.displayLabel;
            out.print("<tr class='" + highlight + "'>");
            out.print("<td>" + rank + "</td>");
            out.print("<td>" + boldStart + PageShellSupport.escapeHtml(displayLabel) + boldEnd + "</td>");
            switch (tableType) {
                case "updates":
                    out.print("<td>" + formatInteger(row.updates) + "</td>");
                    break;
                case "queries":
                    out.print("<td>" + formatInteger(row.queries) + "</td>");
                    break;
                case "updatesPerCapita":
                    out.print("<td>" + formatInteger(row.population) + "</td><td>" + formatInteger(row.updates)
                            + "</td><td>" + String.format("%,d", Math.round(row.updatesPerCapita)) + "</td>");
                    break;
                case "queriesPerCapita":
                    out.print("<td>" + formatInteger(row.population) + "</td><td>" + formatInteger(row.queries)
                            + "</td><td>" + String.format("%,d", Math.round(row.queriesPerCapita)) + "</td>");
                    break;
                case "queryUpdateRatio":
                    out.print("<td>" + formatInteger(row.updates) + "</td><td>" + formatInteger(row.queries)
                            + "</td><td>" + String.format("%,d", Math.round(row.queryUpdateRatio)) + "</td>");
                    break;
            }
            out.println("</tr>");
        }
    }

    private static class RankedRow {
        Jurisdiction jurisdiction;
        String displayLabel;
        int population;
        int updates;
        int queries;
        float updatesPerCapita;
        float queriesPerCapita;
        float queryUpdateRatio;
    }

    private static final SimpleDateFormat sdfMonthYear = new SimpleDateFormat("MMMM yyyy");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (ACTION_ACKNOWLEDGE_AGREEMENT.equals(req.getParameter(PARAM_ACTION))) {
            handleAcknowledgeAgreement(req, resp);
            return;
        }
        doGet(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            SessionUser sessionUser = ClearAuthSessionSupport.getSessionUser(req);
            if (sessionUser == null) {
                resp.sendRedirect(req.getContextPath() + "/login");
                return;
            }

            if (!sessionUser.isAdmin() && !clearAgreementService.hasUserAcknowledgedCurrentAgreement(session,
                    sessionUser, ClearAgreementService.AGREEMENT_CODE_PEER_DATA_SHARING)) {
                renderAgreementPrompt(req, out, session, sessionUser, null);
                return;
            }

            PageShellSupport.printAuthenticatedPageStart(out, "Jurisdiction Results", sessionUser, null);
            out.println("<h2>Jurisdiction Results</h2>");
            out.println("<p class='text-muted'>" + PageShellSupport.escapeHtml(ClearAgreementService.REMINDER_TEXT)
                    + "</p>");

            String selectedJurisdiction = req.getParameter("jurisdiction");
            String monthParam = req.getParameter("month");

            Calendar currentMonthStart = Calendar.getInstance();
            currentMonthStart.set(Calendar.DAY_OF_MONTH, 1);
            currentMonthStart.set(Calendar.HOUR_OF_DAY, 0);
            currentMonthStart.set(Calendar.MINUTE, 0);
            currentMonthStart.set(Calendar.SECOND, 0);
            currentMonthStart.set(Calendar.MILLISECOND, 0);

            Calendar defaultMonth = (Calendar) currentMonthStart.clone();
            defaultMonth.add(Calendar.MONTH, -1);
            Calendar selectedMonth = (Calendar) defaultMonth.clone();
            if (monthParam != null && !monthParam.isEmpty()) {
                try {
                    selectedMonth.setTime(sdfMonthYear.parse(monthParam));
                    selectedMonth.set(Calendar.DAY_OF_MONTH, 1);
                    selectedMonth.set(Calendar.HOUR_OF_DAY, 0);
                    selectedMonth.set(Calendar.MINUTE, 0);
                    selectedMonth.set(Calendar.SECOND, 0);
                    selectedMonth.set(Calendar.MILLISECOND, 0);
                } catch (Exception e) {
                    selectedMonth = (Calendar) defaultMonth.clone();
                }
            }

            if (!selectedMonth.before(currentMonthStart)) {
                selectedMonth = (Calendar) defaultMonth.clone();
            }

            Query<Jurisdiction> jq = session.createQuery("from Jurisdiction order by displayLabel", Jurisdiction.class);
            List<Jurisdiction> jurisdictions = jq.list();
            if (selectedJurisdiction == null && !jurisdictions.isEmpty()) {
                selectedJurisdiction = jurisdictions.get(0).getMapLink();
            }

            out.println("<form method='get' class='app-section' style='margin-top:16px;margin-bottom:16px;'>");
            out.println("<label for='jurisdiction'>Select Jurisdiction:</label> ");
            out.println(
                    "<select name='jurisdiction' id='jurisdiction' class='form-select' style='width:auto;display:inline;' onchange='this.form.submit()'>");
            for (Jurisdiction j : jurisdictions) {
                String abbrev = j.getMapLink();
                String label = clearAgreementService.getDisplayJurisdictionLabel(session, j, sessionUser);
                String selected = abbrev != null && abbrev.equals(selectedJurisdiction) ? "selected" : "";
                out.println("<option value='" + abbrev + "' " + selected + ">" + PageShellSupport.escapeHtml(label)
                        + "</option>");
            }
            out.println("</select>");
            out.println(
                    "<input type='hidden' name='month' value='" + sdfMonthYear.format(selectedMonth.getTime()) + "'>");
            out.println("</form>");

            Calendar prevMonth = (Calendar) selectedMonth.clone();
            prevMonth.add(Calendar.MONTH, -1);
            Calendar nextMonth = (Calendar) selectedMonth.clone();
            nextMonth.add(Calendar.MONTH, 1);
            boolean showNext = nextMonth.before(currentMonthStart);

            out.println("<div class='app-section' style='margin-bottom:16px;'>");
            out.println("<form method='get' style='display:inline;'>");
            out.println("<input type='hidden' name='jurisdiction' value='" + selectedJurisdiction + "'>");
            out.println("<button class='btn' type='submit' name='month' value='"
                    + sdfMonthYear.format(prevMonth.getTime()) + "'>&larr; " + sdfMonthYear.format(prevMonth.getTime())
                    + "</button>");
            out.println("</form>");
            out.println("<span style='margin:0 16px;font-weight:bold;'>" + sdfMonthYear.format(selectedMonth.getTime())
                    + "</span>");
            if (showNext) {
                out.println("<form method='get' style='display:inline;'>");
                out.println("<input type='hidden' name='jurisdiction' value='" + selectedJurisdiction + "'>");
                out.println("<button class='btn' type='submit' name='month' value='"
                        + sdfMonthYear.format(nextMonth.getTime()) + "'>" + sdfMonthYear.format(nextMonth.getTime())
                        + " &rarr;</button>");
                out.println("</form>");
            }
            out.println("</div>");

            Date monthStart = selectedMonth.getTime();
            Calendar monthEndCal = (Calendar) selectedMonth.clone();
            monthEndCal.add(Calendar.MONTH, 1);
            Date monthEnd = monthEndCal.getTime();
            Query<EntryForInterop> eq = session.createQuery(
                    "from EntryForInterop where reportingPeriod >= :start and reportingPeriod < :end",
                    EntryForInterop.class);
            eq.setParameter("start", monthStart);
            eq.setParameter("end", monthEnd);
            List<EntryForInterop> entries = eq.list();

            Map<String, Integer> populationMap = ClearServlet.populationMap;
            Map<String, RankedRow> rowMap = new HashMap<String, RankedRow>();
            for (Jurisdiction j : jurisdictions) {
                RankedRow row = new RankedRow();
                row.jurisdiction = j;
                row.displayLabel = clearAgreementService.getDisplayJurisdictionLabel(session, j, sessionUser);
                row.population = populationMap.getOrDefault(j.getMapLink(), -1);
                row.updates = 0;
                row.queries = 0;
                row.updatesPerCapita = -1;
                row.queriesPerCapita = -1;
                row.queryUpdateRatio = -1;
                rowMap.put(j.getMapLink(), row);
            }
            for (EntryForInterop e : entries) {
                Jurisdiction j = e.getJurisdiction();
                RankedRow row = rowMap.get(j.getMapLink());
                if (row != null) {
                    row.updates += e.getCountUpdate();
                    row.queries += e.getCountQuery();
                }
            }
            for (RankedRow row : rowMap.values()) {
                if (row.population > 0) {
                    row.updatesPerCapita = (float) row.updates / row.population * 1000f;
                    row.queriesPerCapita = (float) row.queries / row.population * 1000f;
                }
                if (row.queries > 0 && row.updates > 0) {
                    row.queryUpdateRatio = (float) row.queries / row.updates;
                }
            }

            List<RankedRow> allRows = new ArrayList<RankedRow>(rowMap.values());

            out.println("<div class='app-section' style='margin-top:24px;'>");
            out.println("<h3>Updates (absolute)</h3>");
            out.println(
                    "<p class='text-muted'>Ranks jurisdictions by total update messages received for the selected month. Larger jurisdictions often appear near the top because this is a raw count.</p>");
            out.println(
                    "<table class='data-table table-striped'><tr><th>Rank</th><th>Jurisdiction</th><th>Updates</th></tr>");
            rankAndRenderTable(allRows, Comparator.comparingInt(r -> r.updates), selectedJurisdiction, out, "updates");
            out.println("</table>");

            out.println("<h3>Queries (absolute)</h3>");
            out.println(
                    "<p class='text-muted'>Ranks jurisdictions by total query messages received for the selected month. This is also a raw count and reflects overall activity volume.</p>");
            out.println(
                    "<table class='data-table table-striped'><tr><th>Rank</th><th>Jurisdiction</th><th>Queries</th></tr>");
            rankAndRenderTable(allRows, Comparator.comparingInt(r -> r.queries), selectedJurisdiction, out, "queries");
            out.println("</table>");

            out.println("<h3>Updates Per Capita</h3>");
            out.println(
                    "<p class='text-muted'>Ranks jurisdictions by updates per 1,000 residents. This normalizes for population so jurisdictions can be compared more fairly across sizes.</p>");
            out.println(
                    "<table class='data-table table-striped'><tr><th>Rank</th><th>Jurisdiction</th><th>Population</th><th>Updates</th><th>Updates/1,000 Residents</th></tr>");
            rankAndRenderTable(allRows, Comparator.comparingDouble(r -> r.updatesPerCapita), selectedJurisdiction, out,
                    "updatesPerCapita");
            out.println("</table>");

            out.println("<h3>Queries Per Capita</h3>");
            out.println(
                    "<p class='text-muted'>Ranks jurisdictions by queries per 1,000 residents. This shows how much query activity exists relative to population size.</p>");
            out.println(
                    "<table class='data-table table-striped'><tr><th>Rank</th><th>Jurisdiction</th><th>Population</th><th>Queries</th><th>Queries/1,000 Residents</th></tr>");
            rankAndRenderTable(allRows, Comparator.comparingDouble(r -> r.queriesPerCapita), selectedJurisdiction, out,
                    "queriesPerCapita");
            out.println("</table>");

            out.println("<h3>Query/Update Balance</h3>");
            out.println(
                    "<p class='text-muted'>Ranks jurisdictions by the ratio of queries to updates. Higher values indicate more query activity compared with the number of updates submitted.</p>");
            out.println(
                    "<table class='data-table table-striped'><tr><th>Rank</th><th>Jurisdiction</th><th>Updates</th><th>Queries</th><th>Queries per Update</th></tr>");
            rankAndRenderTable(allRows, Comparator.comparingDouble(r -> r.queryUpdateRatio), selectedJurisdiction, out,
                    "queryUpdateRatio");
            out.println("</table>");
            out.println("</div>");

            PageShellSupport.printAuthenticatedPageEnd(out);
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

    private void handleAcknowledgeAgreement(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        SessionUser sessionUser = ClearAuthSessionSupport.getSessionUser(req);
        if (sessionUser == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        String returnTo = resolveReturnTo(req.getParameter(PARAM_RETURN_TO), req.getContextPath() + "/results");
        if (!CHECKBOX_TRUE_VALUE.equalsIgnoreCase(req.getParameter(PARAM_AGREE))) {
            resp.sendRedirect(returnTo);
            return;
        }

        Session session = null;
        Transaction tx = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            tx = session.beginTransaction();
            clearAgreementService.acknowledgeAgreement(session, sessionUser, req,
                    ClearAgreementService.AGREEMENT_CODE_PEER_DATA_SHARING);
            tx.commit();
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
        } finally {
            if (session != null) {
                session.close();
            }
        }
        resp.sendRedirect(returnTo);
    }

    private void renderAgreementPrompt(HttpServletRequest req, PrintWriter out, Session session,
            SessionUser sessionUser,
            String message) {
        ClearAgreement agreement = clearAgreementService.getActiveAgreement(session,
                ClearAgreementService.AGREEMENT_CODE_PEER_DATA_SHARING);
        String title = agreement == null ? ClearAgreementService.DEFAULT_TITLE : agreement.getTitle();
        String text = agreement == null ? ClearAgreementService.DEFAULT_TEXT : agreement.getAgreementText();

        PageShellSupport.printAuthenticatedPageStart(out, "Agreement Required", sessionUser, null);
        out.println("<h2>" + PageShellSupport.escapeHtml(title) + "</h2>");
        out.println("<div class='app-section' style='max-width:760px;'>");
        for (String paragraph : text.split("\\n\\n")) {
            out.println("<p>" + PageShellSupport.escapeHtml(paragraph) + "</p>");
        }
        if (message != null && !message.trim().isEmpty()) {
            out.println("<p class='text-error'>" + PageShellSupport.escapeHtml(message) + "</p>");
        }
        out.println("<form method='post' action='/clear/results'>");
        out.println("<input type='hidden' name='" + PARAM_ACTION + "' value='" + ACTION_ACKNOWLEDGE_AGREEMENT + "'>");
        out.println("<input type='hidden' name='" + PARAM_RETURN_TO + "' value='" + PageShellSupport.escapeHtml(
                req.getRequestURI() + (req.getQueryString() == null || req.getQueryString().isEmpty() ? ""
                        : "?" + req.getQueryString()))
                + "'>");
        out.println("<label><input type='checkbox' name='" + PARAM_AGREE + "'> "
                + PageShellSupport.escapeHtml(ClearAgreementService.DEFAULT_CHECKBOX_TEXT) + "</label>");
        out.println("<div style='margin-top:16px;'>");
        out.println("<button class='btn btn-primary' type='submit'>Agree and View Results</button>");
        out.println("</div>");
        out.println("</form>");
        out.println(
                "<p style='margin-top:16px;'><a href='/clear/dashboard?view=home'>Return to Dashboard Home</a></p>");
        out.println("</div>");
        PageShellSupport.printAuthenticatedPageEnd(out);
    }

    private String resolveReturnTo(String returnTo, String defaultValue) {
        if (returnTo == null || returnTo.trim().isEmpty()) {
            return defaultValue;
        }
        String normalized = returnTo.trim();
        if (!normalized.startsWith("/clear/")) {
            return defaultValue;
        }
        return normalized;
    }
}
