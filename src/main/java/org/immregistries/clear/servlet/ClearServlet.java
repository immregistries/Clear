package org.immregistries.clear.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.immregistries.clear.SoftwareVersion;
import org.immregistries.clear.model.EntryForInterop;
import org.immregistries.clear.model.Jurisdiction;
import org.immregistries.clear.servlet.maps.Color;
import org.immregistries.clear.servlet.maps.MapEntityMaker;
import org.immregistries.clear.servlet.maps.MapPlace;
import org.immregistries.clear.utils.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

public class ClearServlet extends HttpServlet {

    String userIisName = "AZ";
    Calendar viewMonth = Calendar.getInstance();

    static {

    }

    private static Map<String, Integer> populationMap = new HashMap<String, Integer>();

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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        SimpleDateFormat sdfMonthYear = new SimpleDateFormat("MMMM YYYY");
        resp.setContentType("text/html");
        System.out.println("--> calling doGet");

        PrintWriter out = new PrintWriter(resp.getOutputStream());
        try {
            System.out.println("--> printing header");
            printHeader(out);

            
            Session session = HibernateUtil.getSessionFactory().openSession();
            
            boolean clickedResetButton = req.getParameter("resetButton") != null ? true : false;
            if(clickedResetButton) {
                session.beginTransaction();
                session.createNativeQuery("TRUNCATE TABLE EntryForInterop").executeUpdate();
                session.createNativeQuery("TRUNCATE TABLE Contact").executeUpdate();
                session.createNativeQuery("TRUNCATE TABLE Jurisdiction").executeUpdate();
                session.getTransaction().commit();

                //create fake jurisdictions
                session.beginTransaction();

                for (String user : populationMap.keySet()) {
                    Jurisdiction newJur = new Jurisdiction();
                    newJur.setMapLink(user);
                    newJur.setDisplayLabel(user);
                    session.save(newJur);
                }
                session.getTransaction().commit();

                //get and randomize all jurisdiction numbers
                session.beginTransaction();
                out.println("<p> randomizing all numbers</p>");
                Query<Jurisdiction> jurisdictionQuery = session.createQuery("FROM Jurisdiction", Jurisdiction.class);

                for (Jurisdiction jur : jurisdictionQuery.list()) {
                    EntryForInterop newEntry = new EntryForInterop();
                    Random rand = new Random();
                    int userPopulation = populationMap.get(jur.getMapLink());
                    newEntry.setCountUpdate(
                            (int) Math.round(rand.nextFloat() * (userPopulation / 2.0) + (userPopulation / 2.0)));
                    newEntry.setCountQuery(
                            (int) Math.round(rand.nextFloat() * (userPopulation / 2.0) + (userPopulation / 2.0)));
                    newEntry.setReportingPeriod(viewMonth.getTime());
                    newEntry.setJurisdictionId(jur.getJurisdictionId());
                    newEntry.setContactId(0);
                    session.save(newEntry);
                }
                session.getTransaction().commit();
            }

            {
                session.beginTransaction();
                Calendar tmpCalendar = Calendar.getInstance();
                tmpCalendar.add(Calendar.YEAR, -2);
                for (int i = 0; i < 25; i++) {
                    SimpleDateFormat sdfRowName = new SimpleDateFormat("MMMMYYYY");
                    tmpCalendar.add(Calendar.MONTH, 1);

                    String rowName = sdfRowName.format(tmpCalendar.getTime());
                    String updateCountString = req.getParameter(rowName + "-Updates");
                    String queryCountString = req.getParameter(rowName + "-Queries");

                    if (updateCountString == null || updateCountString == "") {
                        continue;
                    }
                    if (queryCountString == null || queryCountString == "") {
                        continue;
                    }
                    int updateCount = Integer.parseInt(updateCountString);
                    int queryCount = Integer.parseInt(queryCountString);

                    EntryForInterop newEntry = new EntryForInterop();
                    newEntry.setCountUpdate(updateCount);
                    newEntry.setCountQuery(queryCount);
                    newEntry.setReportingPeriod(tmpCalendar.getTime());

                    Query<Jurisdiction> jq = session.createQuery("FROM Jurisdiction WHERE mapLink = :jurName", Jurisdiction.class);
                    jq.setParameter("jurName", userIisName);
                    Jurisdiction jur = jq.list().get(0);
                    if(jur != null) {
                        newEntry.setJurisdictionId(jur.getJurisdictionId());

                        EntryForInterop entryExists = session.createNativeQuery("FROM EntryForInterop WHERE jurisdictionId = :jurId AND reportingPeriod = :date", EntryForInterop.class).setParameter("jurId", jur.getJurisdictionId()).setParameter("date", tmpCalendar.getTime()).uniqueResult();
                        if(entryExists == null) {
                            session.save(newEntry);
                        } else {
                            entryExists = newEntry;
                            session.update(entryExists);
                        }
                    }

                    
                }
                session.getTransaction().commit();
            }

            out.println("<h1> " + userIisName + " IIS</h3>");
            out.println("<form>");
            out.println("   <table class=\"w3-table w3-striped\">");
            out.println("      <tr>");
            out.println("          <th>Month</th>");
            out.println("          <th>Updates</th>");
            out.println("          <th>Queries</th>");
            out.println("      </tr>");
            {
                Calendar tmpCalendar = Calendar.getInstance();
                tmpCalendar.add(Calendar.YEAR, -2);
                for (int i = 0; i < 25; i++) {
                    SimpleDateFormat sdfRowName = new SimpleDateFormat("MMMMYYYY");
                    String rowName = sdfRowName.format(tmpCalendar.getTime());
                    out.println("      <tr>");
                    out.println("           <td>" + sdfMonthYear.format(tmpCalendar.getTime()) + "</td>");
                    out.println("           <td><input type=\"number\" name=\"" + rowName + "-Updates" + "\"></td>");
                    out.println("           <td><input type=\"number\" name=\"" + rowName + "-Queries" + "\"></td>");
                    out.println("      </tr>");
                    tmpCalendar.add(Calendar.MONTH, 1);
                }
            }
            out.println("   </table>");
            out.println("   <input class=\"w3-button\" type=\"submit\" value=\"Submit\">");
            out.println("</form>");

            // get highest and lowest test participant numbers
            int highestDisplayCount = -1;
            int lowestDisplayCount = -1;

            Query<EntryForInterop> allEntriesQuery = session.createQuery("FROM EntryForInterop", EntryForInterop.class);
            List<EntryForInterop> allEntries = allEntriesQuery.list();

            for (EntryForInterop efi : allEntries) {
                if(!sdfMonthYear.format(efi.getReportingPeriod()).equals(sdfMonthYear.format(viewMonth.getTime()))) {
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
            out.println("<p> highest update count: " + highestDisplayCount + "</p>");
            out.println("<p> lowest update count: " + lowestDisplayCount + "</p>");

            try {
                MapEntityMaker mapEntityMaker = new MapEntityMaker();

                for (EntryForInterop efi : allEntries) {
                    Query<Jurisdiction> jq = session.createQuery("FROM Jurisdiction WHERE JurisdictionId IS :jurId", Jurisdiction.class);
                    jq.setParameter("jurId", efi.getJurisdictionId());
                    Jurisdiction jurisdiction = jq.list().get(0);
                    int displayCount = efi.getCountUpdate();

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

            out.println("<input id=\"updatesRadio\" class=\"w3-button\" type=\"radio\" name=\"display_type\">");
            out.println("<label for=\"updatesRadio\">Updates</label>");
            out.println("<input id=\"queriesRadio\" class=\"w3-button\" type=\"radio\" name=\"display_type\">");
            out.println("<label for=\"queriesRadio\">Queries</label>");

            out.println("<div class=\"w3-cell-row\">");
            out.println("   <div class=\"w3-cell\">");
            out.println("       <input class=\"w3-button\" type=\"button\" value=\"<-\">");
            out.println("   </div>");
            out.println("   <div class=\"w3-cell\">");
            out.println("       <p>" + sdfMonthYear.format(viewMonth.getTime()) + "</p>");
            out.println("   </div>");
            out.println("   <div class=\"w3-cell\">");
            out.println("       <input class=\"w3-button\" type=\"button\" value=\"->\">");
            out.println("   </div>");
            out.println("</div>");

            out.println("   <table class=\"w3-table w3-striped\">");
            out.println("      <tr>");
            out.println("          <th>User</th>");
            out.println("          <th>Updates</th>");
            out.println("          <th>Queries</th>");
            out.println("      </tr>");
            for (EntryForInterop efi : allEntries) {
                Query<Jurisdiction> jq = session.createQuery("FROM Jurisdiction WHERE JurisdictionId IS :jurId", Jurisdiction.class);
                jq.setParameter("jurId", efi.getJurisdictionId());
                Jurisdiction jurisdiction = jq.list().get(0);
                out.println("      <tr>");
                out.println("           <td>" + jurisdiction.getDisplayLabel() + "</td>");
                out.println("           <td>" + efi.getCountUpdate() + "</td>");
                out.println("           <td>" + efi.getCountQuery() + "</td>");
                out.println("      </tr>");
            }
            out.println("   </table>");

            out.println("<form>");
            out.println("   <input class=\"w3-button\" type=\"submit\" name=\"resetButton\" value=\"reset database\">");
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

    protected void printHeader(PrintWriter out) {
        out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\">");
        out.println("<html>");
        out.println("  <head>");
        out.println("    <title>CLEAR - Community Led Exchange and Aggregate Reporting</title>");
        out.println("    <link rel=\"stylesheet\" href=\"https://www.w3schools.com/w3css/4/w3.css\"/>");
        out.println("  </head>");
        out.println("  <body>");

        out.println("    <header class=\"w3-container w3-light-grey\">");
        out.println("      <div class=\"w3-bar w3-light-grey\">");
        out.println("        <h1>CLEAR - Community Led Exchange and Aggregate Reporting</h1> ");
        out.println("        <a href=\"\" class=\"w3-bar-item w3-button\">Main</a> ");
        out.println("        <a href=\"clear/email\" class=\"w3-bar-item w3-button\">Mail</a> ");
        out.println("      </div>");
        out.println("    </header>");
        out.println("    <div class=\"w3-container\">");
    }

    protected void printFooter(PrintWriter out) {
        out.println("   </div>");
        out.println("  <div class=\"w3-container w3-green\">");
        out.println("      <p>Step Into CDSi " + SoftwareVersion.VERSION + " - ");
        out.println(
                "      <a href=\"https://aira.memberclicks.net/assets/docs/Organizational_Docs/AIRA%20Privacy%20Policy%20-%20Final%202024_.pdf\" class=\"underline\">AIRA Privacy Policy</a> - ");
        out.println(
                "      <a href=\"https://aira.memberclicks.net/assets/docs/Organizational_Docs/AIRA%20Terms%20of%20Use%20-%20Final%202024_.pdf\" class=\"underline\">AIRA Terms and Conditions of Use</a></p>");
        out.println("    </div>");
        out.println("  </body>");
        out.println("</html>");
    }

}
