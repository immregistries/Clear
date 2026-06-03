package org.immregistries.clear.servlet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.immregistries.clear.auth.SessionUser;
import org.junit.Test;

public class PageShellSupportTest {

    @Test
    public void shouldHideAdminNavigationForNonAdminUsers() {
        SessionUser sessionUser = sessionUser(false);

        String html = renderAuthenticatedPageStart(sessionUser);

        assertFalse(html.contains("href=\"/clear/admin\" class=\"app-nav-item\">Admin</a>"));
    }

    @Test
    public void shouldShowAdminNavigationForAdminUsers() {
        SessionUser sessionUser = sessionUser(true);

        String html = renderAuthenticatedPageStart(sessionUser);

        assertTrue(html.contains("href=\"/clear/admin\" class=\"app-nav-item\">Admin</a>"));
    }

    private static String renderAuthenticatedPageStart(SessionUser sessionUser) {
        StringWriter buffer = new StringWriter();
        PrintWriter out = new PrintWriter(buffer);
        PageShellSupport.printAuthenticatedPageStart(out, "CLEAR", sessionUser, "TX");
        out.flush();
        return buffer.toString();
    }

    private static SessionUser sessionUser(boolean admin) {
        SessionUser sessionUser = new SessionUser();
        sessionUser.setAdmin(admin);
        sessionUser.setDisplayName(admin ? "Admin User" : "Jurisdiction User");
        sessionUser.setEmail(admin ? "admin@example.org" : "user@example.org");
        return sessionUser;
    }
}