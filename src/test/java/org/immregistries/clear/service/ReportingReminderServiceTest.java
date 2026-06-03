package org.immregistries.clear.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;

import org.immregistries.clear.model.Contact;
import org.immregistries.clear.service.ReportingReminderService.ReportingStatus;
import org.junit.Test;

public class ReportingReminderServiceTest {

    private final ReportingReminderService reportingReminderService = new ReportingReminderService();

    @Test
    public void shouldDeterminePreviousCalendarMonthAsTargetMonth() {
        YearMonth targetMonth = reportingReminderService.determineTargetMonth(LocalDate.of(2026, 7, 18));

        assertEquals(YearMonth.of(2026, 6), targetMonth);
    }

    @Test
    public void shouldClassifyReportingStatusByMonthDistance() {
        YearMonth targetMonth = YearMonth.of(2026, 6);

        assertEquals(ReportingStatus.CURRENT,
                reportingReminderService.determineStatus(YearMonth.of(2026, 6), targetMonth));
        assertEquals(ReportingStatus.ONE_MONTH_BEHIND,
                reportingReminderService.determineStatus(YearMonth.of(2026, 5), targetMonth));
        assertEquals(ReportingStatus.TWO_MONTHS_BEHIND,
                reportingReminderService.determineStatus(YearMonth.of(2026, 4), targetMonth));
        assertEquals(ReportingStatus.THREE_PLUS_MONTHS_BEHIND,
                reportingReminderService.determineStatus(YearMonth.of(2026, 3), targetMonth));
        assertEquals(ReportingStatus.NEVER_REPORTED, reportingReminderService.determineStatus(null, targetMonth));
    }

    @Test
    public void shouldDefaultReminderOnlyForRecentReporterWithValidEmail() {
        YearMonth targetMonth = YearMonth.of(2026, 6);
        Contact validContact = contact("Alex", "Anderson", "alex@example.org");
        Contact invalidContact = contact("Alex", "Anderson", "not-an-email");

        assertTrue(reportingReminderService.shouldDefaultReminderSelected(
                ReportingStatus.ONE_MONTH_BEHIND, validContact, YearMonth.of(2026, 5), targetMonth));
        assertTrue(reportingReminderService.shouldDefaultReminderSelected(
                ReportingStatus.THREE_PLUS_MONTHS_BEHIND, validContact, YearMonth.of(2026, 3), targetMonth));
        assertFalse(reportingReminderService.shouldDefaultReminderSelected(
                ReportingStatus.CURRENT, validContact, YearMonth.of(2026, 6), targetMonth));
        assertFalse(reportingReminderService.shouldDefaultReminderSelected(
                ReportingStatus.ONE_MONTH_BEHIND, invalidContact, YearMonth.of(2026, 5), targetMonth));
        assertFalse(reportingReminderService.shouldDefaultReminderSelected(
                ReportingStatus.THREE_PLUS_MONTHS_BEHIND, validContact, YearMonth.of(2026, 2), targetMonth));
        assertFalse(reportingReminderService.shouldDefaultReminderSelected(
                ReportingStatus.NEVER_REPORTED, validContact, null, targetMonth));
    }

    @Test
    public void shouldBuildReminderAndInvitationMessages() {
        String reminderEnterUrl = reportingReminderService.buildEnterUrl("https://example.org/clear",
                YearMonth.of(2026, 6), "AZ");

        assertEquals("https://example.org/clear/enter?year=2026&jurisdiction=AZ", reminderEnterUrl);
        assertEquals("June 2026 HL7 counts requested",
                reportingReminderService.buildReminderSubject(YearMonth.of(2026, 6)));

        String reminderBody = reportingReminderService.buildReminderBody("Alex", YearMonth.of(2026, 6),
                reminderEnterUrl);
        assertTrue(reminderBody.contains("Hello Alex,"));
        assertTrue(reminderBody.contains("It's time to enter your June 2026 HL7 counts for CLEAR."));
        assertTrue(reminderBody.contains(reminderEnterUrl));

        String invitationBody = reportingReminderService.buildInvitationBody("Jamie", "/clear/enter");
        assertEquals("Invitation to submit IIS query and update counts for CLEAR",
                reportingReminderService.buildInvitationSubject());
        assertTrue(invitationBody.contains("Hello Jamie,"));
        assertTrue(invitationBody.contains("Please enter your IIS counts here:"));
        assertTrue(invitationBody.contains("/clear/enter"));
    }

    private static Contact contact(String firstName, String lastName, String email) {
        Contact contact = new Contact();
        contact.setNameFirst(firstName);
        contact.setNameLast(lastName);
        contact.setEmailAddress(email);
        return contact;
    }
}