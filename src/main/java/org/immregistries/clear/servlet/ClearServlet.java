package org.immregistries.clear.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.immregistries.clear.auth.ClearAuthSessionSupport;
import org.immregistries.clear.auth.SessionUser;
import org.immregistries.clear.model.EntryForInterop;
import org.immregistries.clear.model.Jurisdiction;
import org.immregistries.clear.model.JurisdictionAccessRole;
import org.immregistries.clear.service.JurisdictionAccessService;
import org.immregistries.clear.servlet.maps.Color;
import org.immregistries.clear.servlet.maps.MapEntityMaker;
import org.immregistries.clear.servlet.maps.MapPlace;
import org.immregistries.clear.utils.HibernateUtil;
import org.apache.commons.lang.time.DateUtils;
import org.hibernate.Session;
import org.hibernate.query.Query;

public class ClearServlet extends HttpServlet {

    public static final String PARAM_JURISDICTION = "jurisdiction";

    public static final String PARAM_VIEW = "view";
    public static final String VIEW_MAP = "map";
    public static final String VIEW_DATA = "data";
    public static final String VIEW_HOME = "home";

    public static final String PARAM_ACTION = "action";
    public static final String ACTION_SAVE = "Save";

    public static final String PARAM_MONTH = "month";

    public static final String PARAM_DISPLAY_TYPE = "display_type";
    public static final String DISPLAY_TYPE_UPDATES = "updates";
    public static final String DISPLAY_TYPE_QUERIES = "queries";

    public static Map<String, Integer> populationMap = new HashMap<String, Integer>();

    private final JurisdictionAccessService jurisdictionAccessService = new JurisdictionAccessService();

    static {
        populationMap.put("AL", 5025369);
        populationMap.put("AK", 733395);
        populationMap.put("AZ", 7158110);
        populationMap.put("AR", 3011553);
        populationMap.put("CA", 39555674);
        populationMap.put("CO", 5775324);
        populationMap.put("CT", 3607701);
        populationMap.put("DE", 989955);
        populationMap.put("DC", 689545);
        populationMap.put("FL", 21538192);
        populationMap.put("GA", 10713755);
        populationMap.put("HI", 1455252);
        populationMap.put("ID", 1839140);
        populationMap.put("IL", 12821814);
        populationMap.put("IN", 6786587);
        populationMap.put("IA", 3190546);
        populationMap.put("KS", 2937745);
        populationMap.put("KY", 4506302);
        populationMap.put("LA", 4657874);
        populationMap.put("ME", 1363196);
        populationMap.put("MD", 6181629);
        populationMap.put("MA", 7033132);
        populationMap.put("MI", 10079338);
        populationMap.put("MN", 5706692);
        populationMap.put("MS", 2961278);
        populationMap.put("MO", 6154854);
        populationMap.put("MT", 1084216);
        populationMap.put("NE", 1961996);
        populationMap.put("NV", 3105595);
        populationMap.put("NH", 1377546);
        populationMap.put("NJ", 9289014);
        populationMap.put("NM", 2117555);
        populationMap.put("NY", 20203772);
        populationMap.put("NC", 10441499);
        populationMap.put("ND", 779046);
        populationMap.put("OH", 11799453);
        populationMap.put("OK", 3959405);
        populationMap.put("OR", 4237224);
        populationMap.put("PA", 13002909);
        populationMap.put("RI", 1097354);
        populationMap.put("SC", 5118252);
        populationMap.put("SD", 886729);
        populationMap.put("TN", 6912347);
        populationMap.put("TX", 29149458);
        populationMap.put("UT", 3271608);
        populationMap.put("VT", 643082);
        populationMap.put("VA", 8631388);
        populationMap.put("WA", 7707586);
        populationMap.put("WV", 1793736);
        populationMap.put("WI", 5894170);
        populationMap.put("WY", 576844);
        populationMap.put("PR", 3285874);
        populationMap.put("NYC", 8804190);
        populationMap.put("Phil", 1526006);
        populationMap.put("American Samoa", 49710);
        populationMap.put("Guam", 168801);
        populationMap.put("Marshall Islands", 42418);
        populationMap.put("Micronesia", 113373);
        populationMap.put("N. Mariana Islands (CNMI)", 43854);
        populationMap.put("Palau", 21779);
        populationMap.put("Puerto Rico", 3238164);
        populationMap.put("Virgin Islands", 87146);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        SimpleDateFormat sdfMonthYear = new SimpleDateFormat("MMMM yyyy");
        resp.setContentType("text/html");

        String view = VIEW_HOME;
        if (req.getParameter(PARAM_VIEW) != null) {
            view = req.getParameter(PARAM_VIEW);
        }
        String selectedJurisdictionMapLink = "AZ";
        Jurisdiction selectedJurisdiction = null;
        List<Jurisdiction> accessibleJurisdictions = null;
        JurisdictionAccessRole selectedAccessRole = JurisdictionAccessRole.NOT_AUTHORIZED;
        if (req.getParameter(PARAM_JURISDICTION) != null) {
            selectedJurisdictionMapLink = req.getParameter(PARAM_JURISDICTION).replace('-', ' ');
        }

        Calendar latestCompleteMonth = Calendar.getInstance();
        latestCompleteMonth.set(Calendar.DAY_OF_MONTH, 1);
        latestCompleteMonth.add(Calendar.MONTH, -1);

        Calendar month = (Calendar) latestCompleteMonth.clone();
        String monthParam = req.getParameter(PARAM_MONTH);
        if (monthParam != null && !monthParam.isEmpty()) {
            try {
                month.setTime(sdfMonthYear.parse(monthParam));
                month.set(Calendar.DAY_OF_MONTH, 1);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        if (month.after(latestCompleteMonth)) {
            month = (Calendar) latestCompleteMonth.clone();
        }

        String displayType = req.getParameter(PARAM_DISPLAY_TYPE);
        if (displayType == null) {
            displayType = DISPLAY_TYPE_UPDATES;
        }

        SessionUser sessionUser = ClearAuthSessionSupport.getSessionUser(req);
        if (sessionUser == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        boolean adminUser = sessionUser.isAdmin();

        Session session = HibernateUtil.getSessionFactory().openSession();

        PrintWriter out = new PrintWriter(resp.getOutputStream());
        try {

            String message = null;
            Query<Jurisdiction> jurisdictionList;
            {
                jurisdictionList = getJurisdictionList(session);
                List<Jurisdiction> allJurisdictions = jurisdictionList.list();
                if (allJurisdictions.size() == 0) {
                    if (adminUser) {
                        message = resetDatabase(session);
                    } else {
                        message = "No jurisdictions are configured yet.";
                    }
                    jurisdictionList = getJurisdictionList(session);
                    allJurisdictions = jurisdictionList.list();
                }
                accessibleJurisdictions = jurisdictionAccessService.getAccessibleJurisdictions(session, sessionUser,
                        allJurisdictions);
                for (Jurisdiction j : accessibleJurisdictions) {
                    if (j.getMapLink().equals(selectedJurisdictionMapLink)) {
                        selectedJurisdiction = j;
                    }
                }
                if (selectedJurisdiction == null && sessionUser.getJurisdictionId() != null) {
                    for (Jurisdiction j : accessibleJurisdictions) {
                        if (j.getJurisdictionId() == sessionUser.getJurisdictionId().intValue()) {
                            selectedJurisdiction = j;
                            break;
                        }
                    }
                }
                if (selectedJurisdiction == null && !accessibleJurisdictions.isEmpty()) {
                    selectedJurisdiction = accessibleJurisdictions.get(0);
                }
                if (selectedJurisdiction != null) {
                    selectedJurisdictionMapLink = selectedJurisdiction.getMapLink().replace(' ', '-');
                    selectedAccessRole = jurisdictionAccessService.getEffectiveAccessRole(session, sessionUser,
                            selectedJurisdiction.getJurisdictionId());
                }
            }

            if (view.equals(VIEW_HOME)) {
                printHomeHeader(out, sessionUser);
                renderHomeView(out);
                printFooter(out);
                return;
            }

            if (selectedJurisdiction == null) {
                printHeader(out, selectedJurisdictionMapLink, sessionUser);
                out.println("<h4>No jurisdiction access is available for your account.</h4>");
                printFooter(out);
                return;
            }

            boolean clickedResetButton = req.getParameter("resetButton") != null ? true : false;
            if (clickedResetButton) {
                if (adminUser) {
                    message = resetDatabase(session);
                } else {
                    message = "Only admins can reset database data.";
                }
            }

            printHeader(out, selectedJurisdictionMapLink, sessionUser);
            if (message != null) {
                out.println("<p>" + message + "</p>");
            }

            if (view.equals(VIEW_DATA)) {
                out.println("<h3> " + selectedJurisdiction.getDisplayLabel() + "</h3>");
                out.println("<p>Access role: " + formatAccessRoleLabel(selectedAccessRole, adminUser) + "</p>");
                printJurisdictionSelector(out, accessibleJurisdictions, selectedJurisdictionMapLink, view);
                HashMap<String, EntryForInterop> entryForInteropMap = getEntryForInteropMap(selectedJurisdiction,
                        session);
                out.println("<p>Found " + entryForInteropMap.size() + " entries already saved.</p>");
                printViewMode(out, entryForInteropMap, sdfMonthYear);
                out.println("<form action=\"/clear/enter\" method=\"get\">");
                out.println("   <input type=\"hidden\" name=\"" + PARAM_JURISDICTION + "\" value=\""
                        + selectedJurisdictionMapLink + "\">");
                out.println("   <input class=\"btn\" type=\"submit\" value=\"Enter Data\">");
                out.println("</form>");
            }

            if (view.equals(VIEW_MAP)) {

                // get highest and lowest test participant numbers
                int highestDisplayCount = -1;
                int lowestDisplayCount = -1;

                List<EntryForInterop> allEntries = getEntriesForScope(session, adminUser, accessibleJurisdictions);

                for (EntryForInterop efi : allEntries) {
                    if (!sdfMonthYear.format(efi.getReportingPeriod())
                            .equals(sdfMonthYear.format(month.getTime()))) {
                        continue;
                    }
                    int displayCount = efi.getCountUpdate();
                    if (displayCount > highestDisplayCount || highestDisplayCount == -1) {
                        highestDisplayCount = displayCount;
                    }
                    if (displayCount < lowestDisplayCount || lowestDisplayCount == -1) {
                        lowestDisplayCount = displayCount;
                    }

                }

                int lowerBorder = (highestDisplayCount - lowestDisplayCount) / 3;
                int upperBorder = lowerBorder * 2;

                if (highestDisplayCount == -1 || lowestDisplayCount == -1) {
                    upperBorder = 0;
                    lowerBorder = 0;
                }

                try {
                    MapEntityMaker mapEntityMaker = new MapEntityMaker();

                    for (EntryForInterop efi : allEntries) {
                        if (!sdfMonthYear.format(efi.getReportingPeriod())
                                .equals(sdfMonthYear.format(month.getTime()))) {
                            continue;
                        }
                        Jurisdiction jurisdiction = efi.getJurisdiction();

                        int displayCount = efi.getCountUpdate();
                        if (displayType.equals(DISPLAY_TYPE_QUERIES)) {
                            displayCount = efi.getCountQuery();
                        }

                        MapPlace mapPlace = new MapPlace(jurisdiction.getMapLink());

                        mapPlace.setFillerColor(Color.DEFAULT);
                        if (displayCount < lowerBorder) {
                            mapPlace.setFillerColor(Color.MAP_LOWER);
                        } else if (displayCount > upperBorder) {
                            mapPlace.setFillerColor(Color.MAP_UPPER);
                        } else {
                            mapPlace.setFillerColor(Color.MAP_CENTER);
                        }

                        mapEntityMaker.addMapPlace(mapPlace);
                    }
                    mapEntityMaker.setMapTitle("Map");
                    mapEntityMaker.setStatusTitle("Population");
                    mapEntityMaker.printMapWithKey(out);
                } catch (Exception e) {
                    e.printStackTrace(out);
                }

                String updatesCheckedString = "checked";
                String queriesCheckedString = "";
                if (displayType.equals(DISPLAY_TYPE_UPDATES)) {
                    updatesCheckedString = "checked";
                } else {
                    updatesCheckedString = "";
                    queriesCheckedString = "checked";
                }

                out.println("<div class=\"app-section\" style=\"width:40%\">");
                out.println("<div class=\"app-section\">");
                out.println("   <form method=\"GET\" action=\"/clear/dashboard\">");
                out.println("      <input type=\"hidden\" name=\"" + PARAM_VIEW + "\" value=\"" + view + "\">");
                out.println("      <input type=\"hidden\" name=\"" + PARAM_MONTH + "\" value=\""
                        + sdfMonthYear.format(month.getTime()) + "\">");
                out.println("      <input " + updatesCheckedString
                        + " id=\"updatesRadio\" class=\"btn\" type=\"radio\" name=\"" + PARAM_DISPLAY_TYPE
                        + "\" value=\"" + DISPLAY_TYPE_UPDATES + "\" onclick=\"this.form.submit()\">");
                out.println("      <label for=\"updatesRadio\">Updates</label>");
                out.println("      <input " + queriesCheckedString
                        + " id=\"queriesRadio\" class=\"btn\" type=\"radio\" name=\"" + PARAM_DISPLAY_TYPE
                        + "\" value=\"" + DISPLAY_TYPE_QUERIES + "\" onclick=\"this.form.submit()\">");
                out.println("      <label for=\"queriesRadio\">Queries</label>");
                out.println("   </form>");
                out.println("</div>");

                Date pastMonth = DateUtils.addMonths(month.getTime(), -1);
                Date futureMonth = DateUtils.addMonths(month.getTime(), 1);

                String pastMonthString = sdfMonthYear.format(pastMonth.getTime());
                String futureMonthString = sdfMonthYear.format(futureMonth.getTime());

                out.println("<div class=\"app-section\">");
                out.println("<form method=\"GET\" action=\"/clear/dashboard\">");
                out.println("   <input type=\"hidden\" name=\"" + PARAM_VIEW + "\" value=\"" + view + "\">");
                out.println(
                        "   <input type=\"hidden\" name=\"" + PARAM_DISPLAY_TYPE + "\" value=\"" + displayType + "\">");
                out.println("   <div class=\"app-row\">");
                out.println("      <div class=\"app-col\">");
                out.println("          <input class=\"btn\" type=\"submit\" name=\"" + PARAM_MONTH + "\" value=\""
                        + pastMonthString + "\" onclick=\"this.form.submit()\">");
                out.println("      </div>");
                out.println("      <div class=\"app-col\">");
                out.println("          <p>" + sdfMonthYear.format(month.getTime()) + "</p>");
                out.println("      </div>");
                out.println("      <div class=\"app-col\">");
                if (futureMonth.after(latestCompleteMonth.getTime())) {
                    out.println("          <button class=\"btn\" type=\"button\" disabled>" + futureMonthString
                            + "</button>");
                } else {
                    out.println(
                            "          <input class=\"btn\" type=\"submit\" name=\"" + PARAM_MONTH + "\" value=\""
                                    + futureMonthString + "\" onclick=\"this.form.submit()\">");
                }
                out.println("      </div>");
                out.println("   </div>");
                out.println("</form>");
                out.println("</div>");

                int numEntries = 0;

                float uToPopTotal = 0;
                float qToPopTotal = 0;
                float uToQTotal = 0;

                int popTotal = 0;
                int updatesTotal = 0;
                int queriesTotal = 0;

                out.println("<div class=\"app-section\">");
                out.println("   <table class=\"data-table table-striped\">");
                out.println("      <tr>");
                out.println("          <th>User</th>");
                out.println("          <th>Population</th>");
                out.println("          <th>Updates</th>");
                out.println("          <th>Queries</th>");
                out.println("          <th>Updates/Population Ratio</th>");
                out.println("          <th>Queries/Population Ratio</th>");
                out.println("          <th>Updates/Queries Ratio</th>");
                out.println("      </tr>");
                for (EntryForInterop efi : allEntries) {
                    Jurisdiction jurisdiction = efi.getJurisdiction();
                    Integer populationValue = populationMap.get(jurisdiction.getDisplayLabel());
                    if (populationValue == null) {
                        populationValue = populationMap.get(jurisdiction.getMapLink());
                    }
                    if (populationValue == null) {
                        continue;
                    }
                    int population = populationValue;
                    if (sdfMonthYear.format(efi.getReportingPeriod()).equals(sdfMonthYear.format(month.getTime()))) {
                        numEntries += 1;
                        popTotal += population;
                        updatesTotal += efi.getCountUpdate();
                        queriesTotal += efi.getCountQuery();
                        out.println("      <tr>");
                        out.println("           <td style=\"width:20%\">" + jurisdiction.getDisplayLabel() + "</td>");
                        out.println("           <td class=\"formatted-number\" style=\"width:20%\">" + population
                                + "</td>");
                        out.println("           <td class=\"formatted-number\" style=\"width:20%\">"
                                + efi.getCountUpdate() + "</td>");
                        out.println("           <td class=\"formatted-number\" style=\"width:20%\">"
                                + efi.getCountQuery() + "</td>");
                        float uToP = (float) efi.getCountUpdate() / population;
                        uToPopTotal += uToP;
                        float qToP = (float) efi.getCountQuery() / population;
                        qToPopTotal += qToP;
                        float uToQ = (float) efi.getCountUpdate() / efi.getCountQuery();
                        uToQTotal += uToQ;
                        out.println("           <td style=\"width:20%\">" + String.format("%.2f", uToP) + "</td>");
                        out.println("           <td style=\"width:20%\">" + String.format("%.2f", qToP) + "</td>");
                        out.println("           <td style=\"width:20%\">" + String.format("%.2f", uToQ) + "</td>");
                        out.println("      </tr>");
                    }
                }
                out.println("   </table>");
                out.println("</div></br>");

                out.println("<div class=\"app-section\">");
                out.println("   <table class=\"data-table table-striped\">");
                out.println("      <tr>");
                out.println("          <th>Updates/Population Average</th>");
                out.println("          <th>Queries/Population Average</th>");
                out.println("          <th>Updates/Queries Average</th>");
                out.println("          <th>Total Updates/Population </th>");
                out.println("          <th>Total Queries/Population </th>");
                out.println("          <th>Total Updates/Queries </th>");
                out.println("      </tr>");
                out.println("      <tr>");
                float uToPopAverage = numEntries > 0 ? uToPopTotal / numEntries : 0;
                float qToPopAverage = numEntries > 0 ? qToPopTotal / numEntries : 0;
                float uToQAverage = numEntries > 0 ? uToQTotal / numEntries : 0;
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", uToPopAverage) + "</td>");
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", qToPopAverage) + "</td>");
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", uToQAverage) + "</td>");
                float totalUToPop = popTotal > 0 ? (float) updatesTotal / popTotal : 0;
                float totalQToPop = popTotal > 0 ? (float) queriesTotal / popTotal : 0;
                float totalUToQ = queriesTotal > 0 ? (float) updatesTotal / queriesTotal : 0;
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", totalUToPop) + "</td>");
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", totalQToPop) + "</td>");
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", totalUToQ) + "</td>");
                out.println("      </tr>");
                out.println("   </table>");
                out.println("</div>");

                if (adminUser) {
                    out.println("<form>");
                    out.println(
                            "   <input class=\"btn\" type=\"submit\" name=\"resetButton\" value=\"reset database\">");
                    out.println("</form>");
                }
                out.println("</div>");

                // Format all <p> elements with class 'formatted-number'
                out.println("<script>");
                out.println("document.addEventListener(\"DOMContentLoaded\", function () {");
                out.println("    document.querySelectorAll(\".formatted-number\").forEach(paragraph => {");
                out.println("        let rawValue = paragraph.textContent.replace(/,/g, \"\").trim();");
                out.println("        if (!isNaN(rawValue) && rawValue.length > 0) {");
                out.println("            paragraph.textContent = Number(rawValue).toLocaleString(\"en-US\");");
                out.println("        }");
                out.println("    });");
                out.println("});");
                out.println("</script>");

            }

            printFooter(out);
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

    private HashMap<String, EntryForInterop> getEntryForInteropMap(Jurisdiction selectedJurisdiction, Session session) {
        SimpleDateFormat sdfRowName = new SimpleDateFormat("MMMMYYYY");
        HashMap<String, EntryForInterop> entryForInteropMap = new HashMap<String, EntryForInterop>();
        Query<EntryForInterop> query = session.createQuery("FROM EntryForInterop WHERE jurisdiction = :jurisdiction",
                EntryForInterop.class);
        query.setParameter("jurisdiction", selectedJurisdiction);
        List<EntryForInterop> entryForInteropList = query.list();
        for (EntryForInterop efi : entryForInteropList) {
            entryForInteropMap.put(sdfRowName.format(efi.getReportingPeriod()), efi);
        }
        return entryForInteropMap;
    }

    private Query<Jurisdiction> getJurisdictionList(Session session) {
        Query<Jurisdiction> jq = session.createQuery("from Jurisdiction order by displayLabel", Jurisdiction.class);
        return jq;
    }

    private List<EntryForInterop> getEntriesForScope(Session session, boolean adminUser,
            List<Jurisdiction> accessibleJurisdictions) {
        if (adminUser) {
            Query<EntryForInterop> query = session.createQuery("FROM EntryForInterop", EntryForInterop.class);
            return query.list();
        }
        if (accessibleJurisdictions == null || accessibleJurisdictions.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Query<EntryForInterop> query = session.createQuery(
                "FROM EntryForInterop WHERE jurisdiction in (:jurisdictions)", EntryForInterop.class);
        query.setParameterList("jurisdictions", accessibleJurisdictions);
        return query.list();
    }

    private void printJurisdictionSelector(PrintWriter out, List<Jurisdiction> accessibleJurisdictions,
            String selectedJurisdictionMapLink, String view) {
        if (accessibleJurisdictions == null || accessibleJurisdictions.size() < 2) {
            return;
        }
        out.println(
                "<form action=\"/clear/dashboard\" method=\"get\" class=\"app-section\" style=\"max-width: 420px; padding-left: 0;\">");
        out.println("   <input type=\"hidden\" name=\"" + PARAM_VIEW + "\" value=\"" + view + "\">");
        out.println("   <label for=\"jurisdictionSelector\"><strong>Jurisdiction</strong></label>");
        out.println("   <select id=\"jurisdictionSelector\" class=\"form-select\" name=\"" + PARAM_JURISDICTION
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

    private void printViewMode(PrintWriter out, HashMap<String, EntryForInterop> entryForInteropMap,
            SimpleDateFormat sdfMonthYear) {
        out.println("<div class=\"app-section\" style=\"width:200px\">");
        out.println("   <table class=\"data-table table-striped\">");
        out.println("      <tr>");
        out.println("          <th>Month</th>");
        out.println("          <th>Updates</th>");
        out.println("          <th>Queries</th>");
        out.println("      </tr>");
        Calendar tmpCalendar = Calendar.getInstance();
        tmpCalendar.add(Calendar.YEAR, -2);
        tmpCalendar.set(Calendar.DAY_OF_MONTH, 1);
        for (int i = 0; i < 25; i++) {
            SimpleDateFormat sdfRowName = new SimpleDateFormat("MMMMYYYY");
            Date reportingPeriod = tmpCalendar.getTime();
            String countUpdate = "";
            String countQuery = "";
            EntryForInterop efi = entryForInteropMap.get(sdfRowName.format(reportingPeriod));
            if (efi != null) {
                countUpdate = "" + efi.getCountUpdate();
                countQuery = "" + efi.getCountQuery();
            }
            out.println("      <tr>");
            out.println("           <td>" + sdfMonthYear.format(reportingPeriod) + "</td>");
            out.println("           <td class=\"formatted-number\">" + countUpdate + "</td>");
            out.println("           <td class=\"formatted-number\">" + countQuery + "</td>");
            out.println("      </tr>");
            tmpCalendar.add(Calendar.MONTH, 1);
        }
        out.println("   </table>");
        out.println("</div>");
    }

    private String formatAccessRoleLabel(JurisdictionAccessRole accessRole, boolean adminUser) {
        if (adminUser) {
            return "Admin";
        }
        if (accessRole == null) {
            return "Not Authorized";
        }
        return accessRole.name().replace('_', ' ').toLowerCase();
    }

    private String resetDatabase(Session session) {
        String message;
        session.beginTransaction();
        session.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        session.createNativeQuery("TRUNCATE TABLE EntryForInterop").executeUpdate();
        session.createNativeQuery("TRUNCATE TABLE Contact").executeUpdate();
        session.createNativeQuery("TRUNCATE TABLE Jurisdiction").executeUpdate();
        session.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
        session.getTransaction().commit();

        // create jurisdictions
        session.beginTransaction();

        for (String user : populationMap.keySet()) {
            Jurisdiction newJur = new Jurisdiction();
            newJur.setMapLink(user);
            newJur.setDisplayLabel(user);
            session.save(newJur);
        }
        session.getTransaction().commit();

        message = "Database reset with jurisdictions initialized.";
        return message;
    }

    protected void printHeader(PrintWriter out, String selectedJurisdiction, SessionUser sessionUser) {
        PageShellSupport.printAuthenticatedPageStart(
                out,
                "CLEAR - Community Led Exchange and Aggregate Reporting",
                sessionUser,
                selectedJurisdiction);
    }

    protected void printHomeHeader(PrintWriter out, SessionUser sessionUser) {
        PageShellSupport.printAuthenticatedPageStart(
                out,
                "CLEAR - Community Led Exchange and Aggregate Reporting",
                sessionUser,
                null);
    }

    protected void printFooter(PrintWriter out) {
        PageShellSupport.printAuthenticatedPageEnd(out);
    }

    private void renderHomeView(PrintWriter out) {
        out.println("<div class=\"app-section\" style=\"max-width: 800px; line-height: 1.6; color: #333;\">");
        out.println(
                "  <h1 style=\"font-size: 2em; margin-bottom: 0.5em; color: #2c5aa0;\">Community Led Exchange and Aggregate Reporting (CLEAR)</h1>");
        out.println("");
        out.println("  <p style=\"margin-bottom: 1em;\">");
        out.println(
                "    CLEAR helps us understand how immunization information systems (IIS) support real-time HL7 interoperability across the nation.");
        out.println(
                "    We're asking IIS to share the volume of HL7 v2 updates and queries they're processing each month.");
        out.println("  </p>");
        out.println("");
        out.println("  <p style=\"margin-bottom: 1em;\">");
        out.println(
                "    Your data is valuable and will be shared with other IIS participants. You'll be able to see what others are reporting.");
        out.println(
                "    Externally, we'll publish only an aggregate national estimate to help communicate the vital services IIS are providing");
        out.println("    and demonstrate that interoperability is working nationwide.");
        out.println("  </p>");
        out.println("");
        out.println("  <p style=\"margin-bottom: 2em;\">");
        out.println(
                "    Together, we're building a picture of the real-world impact of standardized data exchange in immunization systems.");
        out.println("  </p>");
        out.println("");
        out.println("  <p style=\"margin-top: 3em; padding-top: 2em; border-top: 1px solid #ddd;\">");
        out.println("    <a href=\"/clear/logout\" style=\"color: #2c5aa0; text-decoration: none;\">Sign out</a>");
        out.println("  </p>");
        out.println("</div>");
    }

}
