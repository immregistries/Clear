package org.immregistries.clear.servlet;

import org.immregistries.clear.model.EntryForInterop;
import org.immregistries.clear.model.Jurisdiction;
import org.immregistries.clear.utils.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

@WebServlet(name = "ResultsServlet", urlPatterns = { "/results" })
public class ResultsServlet extends HttpServlet {
    // Helper for ranking
    private static void rankAndRenderTable(List<RankedRow> allRows, Comparator<RankedRow> cmp,
            String selectedJurisdiction, PrintWriter out, String tableType) {
        // Filter rows with data for this table
        List<RankedRow> rows = new ArrayList<>();
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
            if (include)
                rows.add(row);
        }
        rows.sort(cmp.reversed()); // Descending
        int rank = 1;
        int prevValue = Integer.MIN_VALUE;
        float prevFloat = Float.NaN;
        int skip = 0;
        for (int i = 0; i < rows.size(); i++) {
            RankedRow row = rows.get(i);
            boolean tie = false;
            switch (tableType) {
                case "updates":
                    tie = (row.updates == prevValue);
                    prevValue = row.updates;
                    break;
                case "queries":
                    tie = (row.queries == prevValue);
                    prevValue = row.queries;
                    break;
                case "updatesPerCapita":
                    tie = (Float.compare(row.updatesPerCapita, prevFloat) == 0);
                    prevFloat = row.updatesPerCapita;
                    break;
                case "queriesPerCapita":
                    tie = (Float.compare(row.queriesPerCapita, prevFloat) == 0);
                    prevFloat = row.queriesPerCapita;
                    break;
                case "queryUpdateRatio":
                    tie = (Float.compare(row.queryUpdateRatio, prevFloat) == 0);
                    prevFloat = row.queryUpdateRatio;
                    break;
            }
            if (i > 0 && tie) {
                skip++;
            } else {
                rank += skip;
                skip = 0;
            }
            // Highlight selected jurisdiction
            String highlight = row.jurisdiction.getMapLink().equals(selectedJurisdiction) ? "w3-green" : "";
            String boldStart = highlight.isEmpty() ? "" : "<b>";
            String boldEnd = highlight.isEmpty() ? "" : "</b>";
            out.print("<tr class='" + highlight + "'>");
            out.print("<td>" + rank + "</td>");
            out.print("<td>" + boldStart + row.jurisdiction.getDisplayLabel() + boldEnd + "</td>");
            switch (tableType) {
                case "updates":
                    out.print("<td>" + row.updates + "</td>");
                    break;
                case "queries":
                    out.print("<td>" + row.queries + "</td>");
                    break;
                case "updatesPerCapita":
                    out.print("<td>" + row.population + "</td><td>" + row.updates + "</td><td>"
                            + String.format("%.2f", row.updatesPerCapita) + "</td>");
                    break;
                case "queriesPerCapita":
                    out.print("<td>" + row.population + "</td><td>" + row.queries + "</td><td>"
                            + String.format("%.2f", row.queriesPerCapita) + "</td>");
                    break;
                case "queryUpdateRatio":
                    out.print("<td>" + row.updates + "</td><td>" + row.queries + "</td><td>"
                            + String.format("%.2f", row.queryUpdateRatio) + "</td>");
                    break;
            }
            out.println("</tr>");
        }
    }

    // Helper class for table rows
    private static class RankedRow {
        Jurisdiction jurisdiction;
        int population;
        int updates;
        int queries;
        float updatesPerCapita;
        float queriesPerCapita;
        float queryUpdateRatio;
    }

    private static final SimpleDateFormat sdfMonthYear = new SimpleDateFormat("MMMM yyyy");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            out.println("<html><head><title>Jurisdiction Results</title>");
            out.println("<link rel=\"stylesheet\" href=\"https://www.w3schools.com/w3css/4/w3.css\">");
            out.println("</head><body>");
            out.println("<header class=\"w3-container w3-green\"><h2>Jurisdiction Results</h2></header>");

            // --- Parameter parsing ---
            String selectedJurisdiction = req.getParameter("jurisdiction");
            String monthParam = req.getParameter("month");

            // --- Month navigation logic ---
            Calendar currentMonthStart = Calendar.getInstance();
            currentMonthStart.set(Calendar.DAY_OF_MONTH, 1);
            currentMonthStart.set(Calendar.HOUR_OF_DAY, 0);
            currentMonthStart.set(Calendar.MINUTE, 0);
            currentMonthStart.set(Calendar.SECOND, 0);
            currentMonthStart.set(Calendar.MILLISECOND, 0);

            Calendar defaultMonth = (Calendar) currentMonthStart.clone();
            defaultMonth.add(Calendar.MONTH, -1); // previous month is the latest reportable month
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
                    // fallback to defaultMonth
                }
            }

            // Never allow current month or any future month.
            if (!selectedMonth.before(currentMonthStart)) {
                selectedMonth = (Calendar) defaultMonth.clone();
            }

            // --- Jurisdiction list for dropdown ---
            Query<Jurisdiction> jq = session.createQuery("from Jurisdiction order by displayLabel", Jurisdiction.class);
            List<Jurisdiction> jurisdictions = jq.list();
            if (selectedJurisdiction == null && !jurisdictions.isEmpty()) {
                selectedJurisdiction = jurisdictions.get(0).getMapLink();
            }

            // --- Jurisdiction dropdown ---
            out.println("<form method='get' class='w3-container' style='margin-top:16px;margin-bottom:16px;'>");
            out.println("<label for='jurisdiction'>Select Jurisdiction:</label> ");
            out.println(
                    "<select name='jurisdiction' id='jurisdiction' class='w3-select' style='width:auto;display:inline;' onchange='this.form.submit()'>");
            for (Jurisdiction j : jurisdictions) {
                String abbrev = j.getMapLink();
                String label = j.getDisplayLabel();
                String selected = (abbrev != null && abbrev.equals(selectedJurisdiction)) ? "selected" : "";
                out.println("<option value='" + abbrev + "' " + selected + ">" + label + "</option>");
            }
            out.println("</select>");
            out.println(
                    "<input type='hidden' name='month' value='" + sdfMonthYear.format(selectedMonth.getTime()) + "'>");
            out.println("</form>");

            // --- Month navigation ---
            Calendar prevMonth = (Calendar) selectedMonth.clone();
            prevMonth.add(Calendar.MONTH, -1);
            Calendar nextMonth = (Calendar) selectedMonth.clone();
            nextMonth.add(Calendar.MONTH, 1);
            boolean showNext = nextMonth.before(currentMonthStart);

            out.println("<div class='w3-container' style='margin-bottom:16px;'>");
            out.println("<form method='get' style='display:inline;'>");
            out.println("<input type='hidden' name='jurisdiction' value='" + selectedJurisdiction + "'>");
            out.println("<button class='w3-button' type='submit' name='month' value='"
                    + sdfMonthYear.format(prevMonth.getTime()) + "'>&larr; " + sdfMonthYear.format(prevMonth.getTime())
                    + "</button>");
            out.println("</form>");
            out.println("<span style='margin:0 16px;font-weight:bold;'>" + sdfMonthYear.format(selectedMonth.getTime())
                    + "</span>");
            if (showNext) {
                out.println("<form method='get' style='display:inline;'>");
                out.println("<input type='hidden' name='jurisdiction' value='" + selectedJurisdiction + "'>");
                out.println("<button class='w3-button' type='submit' name='month' value='"
                        + sdfMonthYear.format(nextMonth.getTime()) + "'>" + sdfMonthYear.format(nextMonth.getTime())
                        + " &rarr;</button>");
                out.println("</form>");
            }
            out.println("</div>");

            // --- Load EntryForInterop data for selected month ---
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

            // --- Build population map ---
            Map<String, Integer> populationMap = ClearServlet.populationMap;

            // --- Build data rows for all jurisdictions ---
            Map<String, RankedRow> rowMap = new HashMap<>();
            for (Jurisdiction j : jurisdictions) {
                RankedRow row = new RankedRow();
                row.jurisdiction = j;
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
                String key = j.getMapLink();
                RankedRow row = rowMap.get(key);
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

            List<RankedRow> allRows = new ArrayList<>(rowMap.values());

            // --- Table rendering skeleton ---
            out.println("<div class='w3-container' style='margin-top:24px;'>");
            out.println("<h3>Updates (absolute)</h3>");
            out.println(
                    "<p class='w3-text-grey'>Ranks jurisdictions by total update messages received for the selected month. Larger jurisdictions often appear near the top because this is a raw count.</p>");
            out.println(
                    "<table class='w3-table w3-striped'><tr><th>Rank</th><th>Jurisdiction</th><th>Updates</th></tr>");
            rankAndRenderTable(allRows, Comparator.comparingInt(r -> r.updates), selectedJurisdiction, out, "updates");
            out.println("</table>");

            out.println("<h3>Queries (absolute)</h3>");
            out.println(
                    "<p class='w3-text-grey'>Ranks jurisdictions by total query messages received for the selected month. This is also a raw count and reflects overall activity volume.</p>");
            out.println(
                    "<table class='w3-table w3-striped'><tr><th>Rank</th><th>Jurisdiction</th><th>Queries</th></tr>");
            rankAndRenderTable(allRows, Comparator.comparingInt(r -> r.queries), selectedJurisdiction, out, "queries");
            out.println("</table>");

            out.println("<h3>Updates Per Capita</h3>");
            out.println(
                    "<p class='w3-text-grey'>Ranks jurisdictions by updates per 1,000 residents. This normalizes for population so jurisdictions can be compared more fairly across sizes.</p>");
            out.println(
                    "<table class='w3-table w3-striped'><tr><th>Rank</th><th>Jurisdiction</th><th>Population</th><th>Updates</th><th>Updates/1,000 Residents</th></tr>");
            rankAndRenderTable(allRows, Comparator.comparingDouble(r -> r.updatesPerCapita), selectedJurisdiction, out,
                    "updatesPerCapita");
            out.println("</table>");

            out.println("<h3>Queries Per Capita</h3>");
            out.println(
                    "<p class='w3-text-grey'>Ranks jurisdictions by queries per 1,000 residents. This shows how much query activity exists relative to population size.</p>");
            out.println(
                    "<table class='w3-table w3-striped'><tr><th>Rank</th><th>Jurisdiction</th><th>Population</th><th>Queries</th><th>Queries/1,000 Residents</th></tr>");
            rankAndRenderTable(allRows, Comparator.comparingDouble(r -> r.queriesPerCapita), selectedJurisdiction, out,
                    "queriesPerCapita");
            out.println("</table>");

            out.println("<h3>Query/Update Balance</h3>");
            out.println(
                    "<p class='w3-text-grey'>Ranks jurisdictions by the ratio of queries to updates. Higher values indicate more query activity compared with the number of updates submitted.</p>");
            out.println(
                    "<table class='w3-table w3-striped'><tr><th>Rank</th><th>Jurisdiction</th><th>Updates</th><th>Queries</th><th>Queries per Update</th></tr>");
            rankAndRenderTable(allRows, Comparator.comparingDouble(r -> r.queryUpdateRatio), selectedJurisdiction, out,
                    "queryUpdateRatio");
            out.println("</table>");
            out.println("</div>");

            out.println("</body></html>");
        } catch (Exception e) {
            out.println("<h3>Exception</h3>");
            out.println("<p>" + e.getMessage() + "</p>");
            out.println("<pre>");
            e.printStackTrace(out);
            out.println("</pre>");
        } finally {
            if (out != null)
                out.close();
            if (session != null)
                session.close();
        }
    }
}
