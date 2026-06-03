package org.immregistries.clear.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.mail.internet.InternetAddress;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.immregistries.clear.model.Contact;
import org.immregistries.clear.model.EntryForInterop;
import org.immregistries.clear.model.Jurisdiction;

public class ReportingReminderService {

    public enum ReportingStatus {
        CURRENT,
        ONE_MONTH_BEHIND,
        TWO_MONTHS_BEHIND,
        THREE_PLUS_MONTHS_BEHIND,
        NEVER_REPORTED
    }

    public static class ReportingStatusRow {
        private final Jurisdiction jurisdiction;
        private final EntryForInterop latestEntry;
        private final Contact lastReporter;
        private final YearMonth lastReportedMonth;
        private final ReportingStatus reportingStatus;
        private final boolean reminderDefaultSelected;

        ReportingStatusRow(Jurisdiction jurisdiction, EntryForInterop latestEntry, Contact lastReporter,
                YearMonth lastReportedMonth, ReportingStatus reportingStatus, boolean reminderDefaultSelected) {
            this.jurisdiction = jurisdiction;
            this.latestEntry = latestEntry;
            this.lastReporter = lastReporter;
            this.lastReportedMonth = lastReportedMonth;
            this.reportingStatus = reportingStatus;
            this.reminderDefaultSelected = reminderDefaultSelected;
        }

        public Jurisdiction getJurisdiction() {
            return jurisdiction;
        }

        public EntryForInterop getLatestEntry() {
            return latestEntry;
        }

        public Contact getLastReporter() {
            return lastReporter;
        }

        public YearMonth getLastReportedMonth() {
            return lastReportedMonth;
        }

        public ReportingStatus getReportingStatus() {
            return reportingStatus;
        }

        public boolean isReminderDefaultSelected() {
            return reminderDefaultSelected;
        }

        public String getLastReporterEmail() {
            return lastReporter == null ? null : lastReporter.getEmailAddress();
        }
    }

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    public YearMonth determineTargetMonth(LocalDate today) {
        return YearMonth.from(today.minusMonths(1));
    }

    public List<ReportingStatusRow> loadReportingStatusRows(Session session, YearMonth targetMonth) {
        List<Jurisdiction> jurisdictions = session.createQuery("FROM Jurisdiction ORDER BY displayLabel",
                Jurisdiction.class).list();

        Map<Integer, EntryForInterop> latestEntryByJurisdiction = loadLatestEntryByJurisdiction(session);
        Map<Integer, Contact> contactById = loadContactById(session);

        List<ReportingStatusRow> rows = new ArrayList<ReportingStatusRow>();
        for (Jurisdiction jurisdiction : jurisdictions) {
            EntryForInterop latestEntry = latestEntryByJurisdiction
                    .get(Integer.valueOf(jurisdiction.getJurisdictionId()));
            Contact lastReporter = latestEntry == null ? null
                    : contactById.get(Integer.valueOf(latestEntry.getContactId()));
            YearMonth lastReportedMonth = latestEntry == null ? null : toYearMonth(latestEntry.getReportingPeriod());
            ReportingStatus reportingStatus = determineStatus(lastReportedMonth, targetMonth);
            boolean defaultSelected = shouldDefaultReminderSelected(reportingStatus, lastReporter, lastReportedMonth,
                    targetMonth);
            rows.add(new ReportingStatusRow(jurisdiction, latestEntry, lastReporter, lastReportedMonth,
                    reportingStatus, defaultSelected));
        }
        return rows;
    }

    public ReportingStatus determineStatus(YearMonth lastReportedMonth, YearMonth targetMonth) {
        if (lastReportedMonth == null) {
            return ReportingStatus.NEVER_REPORTED;
        }
        long monthsBehind = lastReportedMonth.until(targetMonth, ChronoUnit.MONTHS);
        if (monthsBehind <= 0L) {
            return ReportingStatus.CURRENT;
        }
        if (monthsBehind == 1L) {
            return ReportingStatus.ONE_MONTH_BEHIND;
        }
        if (monthsBehind == 2L) {
            return ReportingStatus.TWO_MONTHS_BEHIND;
        }
        return ReportingStatus.THREE_PLUS_MONTHS_BEHIND;
    }

    public boolean shouldDefaultReminderSelected(ReportingStatus reportingStatus, Contact lastReporter,
            YearMonth lastReportedMonth, YearMonth targetMonth) {
        if (reportingStatus == ReportingStatus.CURRENT || reportingStatus == ReportingStatus.NEVER_REPORTED) {
            return false;
        }
        if (lastReporter == null || !isValidEmail(lastReporter.getEmailAddress())) {
            return false;
        }
        if (lastReportedMonth == null) {
            return false;
        }
        long monthsBehind = lastReportedMonth.until(targetMonth, ChronoUnit.MONTHS);
        return monthsBehind >= 1L && monthsBehind <= 3L;
    }

    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        try {
            new InternetAddress(email, true).validate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String buildReminderSubject(YearMonth targetMonth) {
        return formatMonth(targetMonth) + " HL7 counts requested";
    }

    public String buildReminderBody(String firstName, YearMonth targetMonth, String enterUrl) {
        String greeting = trimToFallback(firstName, "there");
        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(greeting).append(",\n\n");
        body.append("It's time to enter your ").append(formatMonth(targetMonth))
                .append(" HL7 counts for CLEAR.\n\n");
        body.append("We're looking for the total number of HL7 v2 updates and queries received by your IIS during ")
                .append(formatMonth(targetMonth))
                .append(". Most IIS teams already track these numbers, so this usually takes less than a minute.\n\n");
        body.append("Enter your counts here:\n\n");
        body.append(enterUrl).append("\n\n");
        body.append(
                "Once you save your numbers, you'll be able to view the latest peer reporting results and national estimates.\n\n");
        body.append(
                "Thank you for helping us build a better picture of how HL7 interoperability is supporting IIS nationwide.\n\n");
        body.append("AIRA");
        return body.toString();
    }

    public String buildInvitationSubject() {
        return "Invitation to submit IIS query and update counts for CLEAR";
    }

    public String buildInvitationBody(String firstName, String enterUrl) {
        String greeting = trimToFallback(firstName, "there");
        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(greeting).append(",\n\n");
        body.append("AIRA is inviting IIS programs to participate in CLEAR reporting.\n\n");
        body.append(
                "CLEAR collects monthly IIS query and update counts and provides participating jurisdictions with access to peer reporting results.\n\n");
        body.append("Please enter your IIS counts here:\n\n");
        body.append(enterUrl).append("\n\n");
        body.append(
                "The form is simple: enter the monthly query and update counts, save, and view the peer results.\n\n");
        body.append("Thank you,\nAIRA");
        return body.toString();
    }

    public String buildEnterUrl(String clearBaseUrl, YearMonth targetMonth, String jurisdictionMapLink) {
        String baseUrl = trimToNull(clearBaseUrl);
        String relativeUrl = "/enter?year=" + targetMonth.getYear() + "&jurisdiction="
                + urlEncode(jurisdictionMapLink == null ? "" : jurisdictionMapLink.replace(' ', '-'));
        if (baseUrl == null) {
            return relativeUrl;
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + relativeUrl;
        }
        return baseUrl + relativeUrl;
    }

    public String formatMonth(YearMonth yearMonth) {
        return yearMonth == null ? "" : yearMonth.format(MONTH_FORMATTER);
    }

    public String formatLastReportedMonth(YearMonth yearMonth) {
        return yearMonth == null ? "" : yearMonth.format(MONTH_FORMATTER);
    }

    public String formatStatusLabel(ReportingStatus reportingStatus) {
        if (reportingStatus == null) {
            return "";
        }
        switch (reportingStatus) {
            case CURRENT:
                return "Current";
            case ONE_MONTH_BEHIND:
                return "1 month behind";
            case TWO_MONTHS_BEHIND:
                return "2 months behind";
            case THREE_PLUS_MONTHS_BEHIND:
                return "3+ months behind";
            case NEVER_REPORTED:
                return "Never reported";
            default:
                return reportingStatus.name();
        }
    }

    public String formatReporterLabel(Contact contact) {
        if (contact == null) {
            return "";
        }
        StringBuilder label = new StringBuilder();
        if (trimToNull(contact.getNameFirst()) != null) {
            label.append(contact.getNameFirst().trim());
        }
        if (trimToNull(contact.getNameLast()) != null) {
            if (label.length() > 0) {
                label.append(" ");
            }
            label.append(contact.getNameLast().trim());
        }
        if (label.length() == 0) {
            return "Contact #" + contact.getContactId();
        }
        return label.toString();
    }

    private Map<Integer, EntryForInterop> loadLatestEntryByJurisdiction(Session session) {
        Query<EntryForInterop> query = session.createQuery(
                "FROM EntryForInterop ORDER BY jurisdiction.jurisdictionId, reportingPeriod DESC, entryForInteropId DESC",
                EntryForInterop.class);
        Map<Integer, EntryForInterop> latestEntryByJurisdiction = new LinkedHashMap<Integer, EntryForInterop>();
        for (EntryForInterop entry : query.list()) {
            if (entry.getJurisdiction() == null) {
                continue;
            }
            Integer jurisdictionId = Integer.valueOf(entry.getJurisdiction().getJurisdictionId());
            if (!latestEntryByJurisdiction.containsKey(jurisdictionId)) {
                latestEntryByJurisdiction.put(jurisdictionId, entry);
            }
        }
        return latestEntryByJurisdiction;
    }

    private Map<Integer, Contact> loadContactById(Session session) {
        Query<Contact> query = session.createQuery("FROM Contact ORDER BY contactId", Contact.class);
        Map<Integer, Contact> contactById = new LinkedHashMap<Integer, Contact>();
        for (Contact contact : query.list()) {
            contactById.put(Integer.valueOf(contact.getContactId()), contact);
        }
        return contactById;
    }

    private YearMonth toYearMonth(Date date) {
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return YearMonth.of(localDate.getYear(), localDate.getMonthValue());
    }

    private String trimToFallback(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }
}