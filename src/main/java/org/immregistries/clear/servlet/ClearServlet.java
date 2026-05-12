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
import org.immregistries.clear.SoftwareVersion;
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
import org.hibernate.Transaction;
import org.hibernate.query.Query;

public class ClearServlet extends HttpServlet {

    public static final String PARAM_JURISDICTION = "jurisdiction";

    public static final String PARAM_VIEW = "view";
    public static final String VIEW_MAP = "map";
    public static final String VIEW_DATA = "data";

    public static final String PARAM_ACTION = "action";
    public static final String ACTION_SAVE = "Save";

    public static final String PARAM_MODE = "mode";
    public static final String MODE_VIEW = "view";
    public static final String MODE_EDIT = "edit";

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

        String view = VIEW_DATA;
        if (req.getParameter(PARAM_VIEW) != null) {
            view = req.getParameter(PARAM_VIEW);
        }
        String mode = req.getParameter(PARAM_MODE);
        String selectedJurisdictionMapLink = "AZ";
        Jurisdiction selectedJurisdiction = null;
        List<Jurisdiction> accessibleJurisdictions = null;
        JurisdictionAccessRole selectedAccessRole = JurisdictionAccessRole.NOT_AUTHORIZED;
        if (req.getParameter(PARAM_JURISDICTION) != null) {
            selectedJurisdictionMapLink = req.getParameter(PARAM_JURISDICTION).replace('-', ' ');
        }

        Calendar month = Calendar.getInstance();
        String monthParam = req.getParameter(PARAM_MONTH);
        if (monthParam != null && monthParam != "") {
            try {
                month.setTime(sdfMonthYear.parse(monthParam));
            } catch (ParseException e) {
                e.printStackTrace();
            }
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
                if (mode == null || mode.equals("")) {
                    mode = selectedJurisdiction != null
                            && jurisdictionAccessService.startsInEditMode(session, sessionUser,
                                    selectedJurisdiction.getJurisdictionId())
                                            ? MODE_EDIT
                                            : MODE_VIEW;
                }
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

            String action = req.getParameter(PARAM_ACTION);
            if (action != null) {
                if (action.equals(ACTION_SAVE)) {
                    if (!jurisdictionAccessService.canEdit(session, sessionUser,
                            selectedJurisdiction.getJurisdictionId())) {
                        message = "You are not authorized to edit data for this jurisdiction.";
                    } else {
                        HashMap<String, EntryForInterop> entryForInteropMap = getEntryForInteropMap(
                                selectedJurisdiction,
                                session);

                        Calendar tmpCalendar = Calendar.getInstance();
                        tmpCalendar.add(Calendar.YEAR, -2);
                        tmpCalendar.set(Calendar.DAY_OF_MONTH, 1);
                        int saveCount = 0;
                        Transaction transaction = session.beginTransaction();
                        for (int i = 0; i < 25; i++) {
                            SimpleDateFormat sdfRowName = new SimpleDateFormat("MMMMYYYY");
                            Date reportingPeriod = tmpCalendar.getTime();
                            String rowName = sdfRowName.format(reportingPeriod);
                            String countUpdateString = req.getParameter(rowName + "-Updates");
                            String countQueryString = req.getParameter(rowName + "-Queries");
                            int countUpdate = 0;
                            if (countUpdateString != null && !countUpdateString.equals("")) {
                                countUpdate = Integer.parseInt(countUpdateString);
                            }
                            int countQuery = 0;
                            if (countQueryString != null && !countQueryString.equals("")) {
                                countQuery = Integer.parseInt(countQueryString);
                            }

                            EntryForInterop efi = entryForInteropMap.get(sdfRowName.format(reportingPeriod));
                            if (efi != null) {
                                efi.setCountUpdate(countUpdate);
                                efi.setCountQuery(countQuery);
                                session.update(efi);
                                saveCount++;
                            } else if (countUpdate > 0 || countQuery > 0) {
                                EntryForInterop newEntry = new EntryForInterop();
                                newEntry.setCountUpdate(countUpdate);
                                newEntry.setCountQuery(countQuery);
                                newEntry.setReportingPeriod(reportingPeriod);
                                newEntry.setJurisdiction(selectedJurisdiction);
                                if (sessionUser.getContactId() != null) {
                                    newEntry.setContactId(sessionUser.getContactId());
                                }
                                session.save(newEntry);
                                saveCount++;
                            }
                            tmpCalendar.add(Calendar.MONTH, 1);
                        }
                        transaction.commit();
                        message = "Saved " + saveCount + " entries";
                        view = VIEW_DATA;
                    }
                }
            }

            printHeader(out, selectedJurisdictionMapLink, sessionUser);
            if (message != null) {
                out.println("<p>" + message + "</p>");
            }

            if (view.equals(VIEW_DATA)) {
                out.println("<h3> " + selectedJurisdiction.getDisplayLabel() + "</h3>");
                out.println("<p>Access role: " + formatAccessRoleLabel(selectedAccessRole, adminUser) + "</p>");
                printJurisdictionSelector(out, accessibleJurisdictions, selectedJurisdictionMapLink, view, mode);
                HashMap<String, EntryForInterop> entryForInteropMap = getEntryForInteropMap(selectedJurisdiction,
                        session);
                out.println("<p>Found " + entryForInteropMap.size() + " entries already saved.</p>");

                if (MODE_EDIT.equals(mode)) {
                    printEditMode(out, selectedJurisdictionMapLink, entryForInteropMap, sdfMonthYear);
                    out.println("<form action=\"clear\" method=\"get\">");
                    out.println("   <input type=\"hidden\" name=\"" + PARAM_VIEW + "\" value=\"" + VIEW_DATA + "\">");
                    out.println("   <input type=\"hidden\" name=\"" + PARAM_JURISDICTION + "\" value=\""
                            + selectedJurisdictionMapLink + "\">");
                    out.println("   <input type=\"hidden\" name=\"" + PARAM_MODE + "\" value=\"" + MODE_VIEW + "\">");
                    out.println("   <input class=\"w3-button w3-light-grey\" type=\"submit\" value=\"View Results\">");
                    out.println("</form>");
                } else {
                    printViewMode(out, entryForInteropMap, sdfMonthYear);
                    out.println("<form action=\"clear\" method=\"get\">");
                    out.println("   <input type=\"hidden\" name=\"" + PARAM_VIEW + "\" value=\"" + VIEW_DATA + "\">");
                    out.println("   <input type=\"hidden\" name=\"" + PARAM_JURISDICTION + "\" value=\""
                            + selectedJurisdictionMapLink + "\">");
                    out.println("   <input type=\"hidden\" name=\"" + PARAM_MODE + "\" value=\"" + MODE_EDIT + "\">");
                    out.println("   <input class=\"w3-button\" type=\"submit\" value=\"Edit Data\">");
                    out.println("</form>");
                }

                out.println("<script>");
                out.println("document.addEventListener(\"DOMContentLoaded\", function () {");
                out.println("    document.querySelectorAll(\".formatted-number\").forEach(input => {");
                out.println("        let rawValue = input.value.replace(/,/g, \"\");");
                out.println("        if (!isNaN(rawValue) && rawValue.length > 0) {");
                out.println("            input.value = Number(rawValue).toLocaleString(\"en-US\");");
                out.println("        }");
                out.println("        input.addEventListener(\"input\", function () {");
                out.println("            let value = this.value.replace(/,/g, \"\");");
                out.println("            if (!isNaN(value) && value.length > 0) {");
                out.println("                this.value = Number(value).toLocaleString(\"en-US\");");
                out.println("            }");
                out.println("        });");
                out.println("        input.form?.addEventListener(\"submit\", function () {");
                out.println("            input.value = input.value.replace(/,/g, \"\");");
                out.println("        });");
                out.println("    });");
                out.println("});");
                out.println("</script>");
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

                out.println("<div class=\"w3-container\" style=\"width:40%\">");
                out.println("<div class=\"w3-container\">");
                out.println("   <form method=\"GET\" action=\"/clear/clear\">");
                out.println("      <input type=\"hidden\" name=\"" + PARAM_VIEW + "\" value=\"" + view + "\">");
                out.println("      <input type=\"hidden\" name=\"" + PARAM_MONTH + "\" value=\""
                        + sdfMonthYear.format(month.getTime()) + "\">");
                out.println("      <input " + updatesCheckedString
                        + " id=\"updatesRadio\" class=\"w3-button\" type=\"radio\" name=\"" + PARAM_DISPLAY_TYPE
                        + "\" value=\"" + DISPLAY_TYPE_UPDATES + "\" onclick=\"this.form.submit()\">");
                out.println("      <label for=\"updatesRadio\">Updates</label>");
                out.println("      <input " + queriesCheckedString
                        + " id=\"queriesRadio\" class=\"w3-button\" type=\"radio\" name=\"" + PARAM_DISPLAY_TYPE
                        + "\" value=\"" + DISPLAY_TYPE_QUERIES + "\" onclick=\"this.form.submit()\">");
                out.println("      <label for=\"queriesRadio\">Queries</label>");
                out.println("   </form>");
                out.println("</div>");

                Date pastMonth = DateUtils.addMonths(month.getTime(), -1);
                Date futureMonth = DateUtils.addMonths(month.getTime(), 1);

                String pastMonthString = sdfMonthYear.format(pastMonth.getTime());
                String futureMonthString = sdfMonthYear.format(futureMonth.getTime());

                out.println("<div class=\"w3-container\">");
                out.println("<form method=\"GET\" action=\"/clear/clear\">");
                out.println("   <input type=\"hidden\" name=\"" + PARAM_VIEW + "\" value=\"" + view + "\">");
                out.println(
                        "   <input type=\"hidden\" name=\"" + PARAM_DISPLAY_TYPE + "\" value=\"" + displayType + "\">");
                out.println("   <div class=\"w3-cell-row\">");
                out.println("      <div class=\"w3-cell\">");
                out.println("          <input class=\"w3-button\" type=\"submit\" name=\"" + PARAM_MONTH + "\" value=\""
                        + pastMonthString + "\" onclick=\"this.form.submit()\">");
                out.println("      </div>");
                out.println("      <div class=\"w3-cell\">");
                out.println("          <p>" + sdfMonthYear.format(month.getTime()) + "</p>");
                out.println("      </div>");
                out.println("      <div class=\"w3-cell\">");
                out.println("          <input class=\"w3-button\" type=\"submit\" name=\"" + PARAM_MONTH + "\" value=\""
                        + futureMonthString + "\" onclick=\"this.form.submit()\">");
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

                out.println("<div class=\"w3-container\">");
                out.println("   <table class=\"w3-table w3-striped\">");
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
                    int population = populationMap.get(jurisdiction.getDisplayLabel());
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

                out.println("<div class=\"w3-container\">");
                out.println("   <table class=\"w3-table w3-striped\">");
                out.println("      <tr>");
                out.println("          <th>Updates/Population Average</th>");
                out.println("          <th>Queries/Population Average</th>");
                out.println("          <th>Updates/Queries Average</th>");
                out.println("          <th>Total Updates/Population </th>");
                out.println("          <th>Total Queries/Population </th>");
                out.println("          <th>Total Updates/Queries </th>");
                out.println("      </tr>");
                out.println("      <tr>");
                float uToPopAverage = uToPopTotal / numEntries;
                float qToPopAverage = qToPopTotal / numEntries;
                float uToQAverage = uToQTotal / numEntries;
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", uToPopAverage) + "</td>");
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", qToPopAverage) + "</td>");
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", uToQAverage) + "</td>");
                float totalUToPop = ((float) updatesTotal / numEntries) / ((float) popTotal / numEntries);
                float totalQToPop = ((float) queriesTotal / numEntries) / ((float) popTotal / numEntries);
                float totalUToQ = ((float) updatesTotal / numEntries) / ((float) queriesTotal / numEntries);
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", totalUToPop) + "</td>");
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", totalQToPop) + "</td>");
                out.println("           <td style=\"width:50%\">" + String.format("%.2f", totalUToQ) + "</td>");
                out.println("      </tr>");
                out.println("   </table>");
                out.println("</div>");

                if (adminUser) {
                    out.println("<form>");
                    out.println(
                            "   <input class=\"w3-button\" type=\"submit\" name=\"resetButton\" value=\"reset database\">");
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
            String selectedJurisdictionMapLink, String view, String mode) {
        if (accessibleJurisdictions == null || accessibleJurisdictions.size() < 2) {
            return;
        }
        out.println(
                "<form action=\"clear\" method=\"get\" class=\"w3-container\" style=\"max-width: 420px; padding-left: 0;\">");
        out.println("   <input type=\"hidden\" name=\"" + PARAM_VIEW + "\" value=\"" + view + "\">");
        out.println("   <input type=\"hidden\" name=\"" + PARAM_MODE + "\" value=\"" + mode + "\">");
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

    private void printEditMode(PrintWriter out, String selectedJurisdictionMapLink,
            HashMap<String, EntryForInterop> entryForInteropMap, SimpleDateFormat sdfMonthYear) {
        out.println("<div class=\"w3-container\" style=\"width:200px\">");
        out.println("<form action=\"clear\" method=\"post\">");
        out.println("   <input type=\"hidden\" name=\"" + PARAM_VIEW + "\" value=\"" + VIEW_DATA + "\">");
        out.println("   <input type=\"hidden\" name=\"" + PARAM_JURISDICTION + "\" value=\""
                + selectedJurisdictionMapLink + "\">");
        out.println("   <input type=\"hidden\" name=\"" + PARAM_MODE + "\" value=\"" + MODE_EDIT + "\">");
        out.println("   <table class=\"w3-table w3-striped\">");
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
            String rowName = sdfRowName.format(reportingPeriod);
            String countUpdate = "";
            String countQuery = "";
            EntryForInterop efi = entryForInteropMap.get(sdfRowName.format(reportingPeriod));
            if (efi != null) {
                countUpdate = "" + efi.getCountUpdate();
                countQuery = "" + efi.getCountQuery();
            }
            out.println("      <tr>");
            out.println("           <td>" + sdfMonthYear.format(tmpCalendar.getTime()) + "</td>");
            out.println("           <td><input class=\"formatted-number\" type=\"text\" name=\"" + rowName
                    + "-Updates\" value=\"" + countUpdate + "\"></td>");
            out.println("           <td><input class=\"formatted-number\" type=\"text\" name=\"" + rowName
                    + "-Queries\" value=\"" + countQuery + "\"></td>");
            out.println("      </tr>");
            tmpCalendar.add(Calendar.MONTH, 1);
        }
        out.println("   </table>");
        out.println("   <input class=\"w3-button\" type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\""
                + ACTION_SAVE + "\">");
        out.println("</form>");
        out.println("</div>");
    }

    private void printViewMode(PrintWriter out, HashMap<String, EntryForInterop> entryForInteropMap,
            SimpleDateFormat sdfMonthYear) {
        out.println("<div class=\"w3-container\" style=\"width:200px\">");
        out.println("   <table class=\"w3-table w3-striped\">");
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
        out.println("        <a href=\"clear?" + PARAM_VIEW + "=" + VIEW_DATA + "&" + PARAM_JURISDICTION + "="
                + selectedJurisdiction + "\" class=\"w3-bar-item w3-button\">Data</a> ");
        out.println("        <a href=\"clear?" + PARAM_VIEW + "=" + VIEW_MAP
                + "\" class=\"w3-bar-item w3-button\">Map</a> ");
        out.println("        <a href=\"clear/email\" class=\"w3-bar-item w3-button\">Mail</a> ");
        out.println("        <a href=\"clear/admin\" class=\"w3-bar-item w3-button\">Admin</a> ");
        out.println("        <a href=\"logout\" class=\"w3-bar-item w3-button\">Logout</a> ");
        out.println("      </div>");
        out.println("      <div class=\"w3-small\">Signed in as " + sessionUser.getDisplayName()
                + " (" + sessionUser.getEmail() + ")"
                + (sessionUser.isAdmin() ? " - Admin" : "") + "</div>");
        out.println("    </header>");
        out.println("    <div class=\"w3-container\">");
    }

    protected void printFooter(PrintWriter out) {
        out.println("   </div>");
        out.println("  <div class=\"w3-container w3-green\">");
        out.println("      <p>CLEAR " + SoftwareVersion.VERSION + " - ");
        out.println(
                "      <a href=\"https://aira.memberclicks.net/assets/docs/Organizational_Docs/AIRA%20Privacy%20Policy%20-%20Final%202024_.pdf\" class=\"underline\">AIRA Privacy Policy</a> - ");
        out.println(
                "      <a href=\"https://aira.memberclicks.net/assets/docs/Organizational_Docs/AIRA%20Terms%20of%20Use%20-%20Final%202024_.pdf\" class=\"underline\">AIRA Terms and Conditions of Use</a></p>");
        out.println("    </div>");
        out.println("  </body>");
        out.println("</html>");
    }

}
