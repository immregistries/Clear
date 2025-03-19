package org.immregistries.clear.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Random;

import io.github.cdimascio.dotenv.Dotenv;
import org.immregistries.clear.servlet.ClearServlet;
import org.immregistries.clear.utils.HibernateUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.hibernate.query.Query;
import org.immregistries.clear.SoftwareVersion;
import org.immregistries.clear.model.EntryForInterop;
import org.immregistries.clear.model.Jurisdiction;
import org.immregistries.clear.model.ValidationCode;

public class EmailServlet extends HttpServlet {

    public static final String PARAM_VIEW = "view";
    public static final String VIEW_MAP = "map";
    public static final String VIEW_DATA = "data";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/html");
        System.out.println("--> calling doGet");

        PrintWriter out = new PrintWriter(resp.getOutputStream());
        try {
            System.out.println("--> printing header");
            printHeader(out);
            out.println("<h1>Send Email</h3>");
            out.println("<form method=\"POST\">");
            out.println("   <input id=\"emailInput\" type=\"email\" name=\"emailInput\">");
            out.println("      <label for=\"emailInput\">Recipient</label></br></br>");
            out.println("   <select id=\"jurisdictionInput\" name=\"jurisdictionInput\">");
            for (String user : ClearServlet.populationMap.keySet()) {
                out.println("       <option value=\"" + user + "\">" + user + "</option>");
            }
            out.println("   <select>");
            out.println("      <label for=\"jurisdictionInput\">jurisdiction</label></br></br>");
            out.println("   <input class=\"w3-button\" type=\"submit\" value=\"Submit\">");
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
        Dotenv dotenv = Dotenv.load();
        String from = dotenv.get("GMAIL_USERNAME");
        final String username = dotenv.get("GMAIL_USERNAME");
        final String password = dotenv.get("GMAIL_PASSWORD");
        String jurisdiction = req.getParameter("jurisdictionInput");
        String host = "smtp.gmail.com";

        org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession();
        session.beginTransaction();
        Random rand = new Random();
        Query<Jurisdiction> jurQuery = session.createQuery("FROM Jurisdiction WHERE mapLink = :jurisdiction", Jurisdiction.class);
        jurQuery.setParameter("jurisdiction", jurisdiction);
        ValidationCode vc = new ValidationCode();
        
        vc.setJurisdictionId(jurQuery.getResultList().get(0).getJurisdictionId());
        vc.setIssueDate(new Date());
        vc.setAccessCode((int)(rand.nextFloat() * 999999));
        session.save(vc);
        session.getTransaction().commit();

        sendEmail(jurisdiction, to, from, username, password, host, vc.getAccessCode());
        

        doGet(req, resp);
    }

    protected void sendEmail(String jurisdiction, String to, String from, String username, String password, String host, int accessCode) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", "587");
        Session session = Session.getInstance(props, new jakarta.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject("CLEAR");
            String formattedJurisdiction = jurisdiction.replace(' ', '-');
            message.setText("enter clear\nhttp://localhost:8080/clear/clear?view=data&jurisdiction=" + formattedJurisdiction + "&access_code=" + accessCode);

            Transport.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
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

        out.println("    <header class=\"w3-container w3-green\">");
        out.println("      <div class=\"w3-bar\">");
        out.println("        <h1>CLEAR - Community Led Exchange and Aggregate Reporting</h1> ");
        out.println("        <a href=\"/clear/clear?" + PARAM_VIEW + "=" + VIEW_DATA
                + "\" class=\"w3-bar-item w3-button\">Data</a> ");
        out.println("        <a href=\"/clear/clear?" + PARAM_VIEW + "=" + VIEW_MAP
                + "\" class=\"w3-bar-item w3-button\">Map</a> ");
        out.println("        <a href=\"\" class=\"w3-bar-item w3-button\">Mail</a> ");
        out.println("      </div>");
        out.println("    </header>");
        out.println("    <div class=\"w3-container\">");
    }

    protected void printFooter(PrintWriter out) {
        out.println("  </div>");
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
