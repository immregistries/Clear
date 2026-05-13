package org.immregistries.clear.servlet;

import java.io.PrintWriter;
import java.util.List;
import java.util.ArrayList;
import org.immregistries.clear.SoftwareVersion;
import org.immregistries.clear.auth.SessionUser;

public final class PageShellSupport {

    private PageShellSupport() {
        // utility class
    }

    public static final class NavItem {
        private final String href;
        private final String label;

        private NavItem(String href, String label) {
            this.href = href;
            this.label = label;
        }
    }

    public static NavItem nav(String href, String label) {
        return new NavItem(href, label);
    }

    public static void printPageStart(PrintWriter out, String title, String brandHref,
            SessionUser sessionUser, List<NavItem> navItems) {
        out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\">");
        out.println("<html>");
        out.println("  <head>");
        out.println("    <title>" + escapeHtml(title) + "</title>");
        out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        out.println("    <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">");
        out.println("    <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>");
        out.println(
                "    <link href=\"https://fonts.googleapis.com/css?family=Open+Sans:400,700|Roboto:400,700\" rel=\"stylesheet\">");
        out.println("    <link rel=\"stylesheet\" href=\"/clear/css/clear-brand.css\"/>");
        out.println("  </head>");
        out.println("  <body class=\"app-body\">");

        out.println("    <header class=\"app-header\">");
        out.println("      <div class=\"app-header-inner\">");
        out.println("        <a class=\"app-brand\" href=\"" + escapeHtml(brandHref) + "\">");
        out.println("          <img class=\"app-brand-logo\" src=\"/clear/images/aira_logo.webp\" alt=\"AIRA\">");
        out.println("          <span>CLEAR</span>");
        out.println("        </a>");

        if (navItems != null && !navItems.isEmpty()) {
            out.println("        <nav class=\"app-nav\">");
            for (NavItem item : navItems) {
                out.println("        <a href=\"" + escapeHtml(item.href) + "\" class=\"app-nav-item\">"
                        + escapeHtml(item.label) + "</a> ");
            }
            out.println("        </nav>");
        }

        out.println("      </div>");

        if (sessionUser != null) {
            out.println("      <div class=\"app-user-meta\">Signed in as "
                    + escapeHtml(sessionUser.getDisplayName())
                    + " (" + escapeHtml(sessionUser.getEmail()) + ")"
                    + (sessionUser.isAdmin() ? " - Admin" : "") + "</div>");
        }

        out.println("    </header>");
        out.println("    <main class=\"app-main\">");
    }

    public static void printAuthenticatedPageStart(PrintWriter out, String title,
            SessionUser sessionUser, String selectedJurisdiction) {
        List<NavItem> navItems = new ArrayList<NavItem>();

        String dataHref = "/clear/dashboard?view=data";
        String enterHref = "/clear/enter";
        if (selectedJurisdiction != null && !selectedJurisdiction.isEmpty()) {
            dataHref += "&jurisdiction=" + selectedJurisdiction;
            enterHref += "?jurisdiction=" + selectedJurisdiction;
        }

        navItems.add(nav(dataHref, "Data"));
        navItems.add(nav(enterHref, "Enter"));
        navItems.add(nav("/clear/dashboard?view=map", "Map"));
        navItems.add(nav("/clear/email", "Mail"));
        navItems.add(nav("/clear/admin", "Admin"));

        if (sessionUser != null && sessionUser.isAdmin()) {
            navItems.add(nav("/clear/admin/jurisdictions", "Jurisdictions"));
            navItems.add(nav("/clear/admin/contacts", "Contacts"));
        }

        navItems.add(nav("/clear/logout", "Logout"));

        printPageStart(out, title, dataHref, sessionUser, navItems);
    }

    public static void printAuthenticatedPageEnd(PrintWriter out) {
        printPageEnd(out, true, false);
    }

    public static void printPageEnd(PrintWriter out, boolean includeTermsLink, boolean includeSourceCodeLink) {
        out.println("   </main>");
        out.println("  <footer class=\"app-footer\">");
        out.println("      <p>CLEAR " + SoftwareVersion.VERSION + " - ");
        out.println(
                "      <a href=\"https://aira.memberclicks.net/assets/docs/Organizational_Docs/AIRA%20Privacy%20Policy%20-%20Final%202024_.pdf\" class=\"underline\">AIRA Privacy Policy</a>");
        if (includeTermsLink) {
            out.println(
                    "       - <a href=\"https://aira.memberclicks.net/assets/docs/Organizational_Docs/AIRA%20Terms%20of%20Use%20-%20Final%202024_.pdf\" class=\"underline\">AIRA Terms and Conditions of Use</a>");
        }
        if (includeSourceCodeLink) {
            out.println(
                    "       - <a href=\"https://github.com/ImmRegistries/Clear\" class=\"underline\">Source Code</a>");
        }
        out.println("      </p>");
        out.println("  </footer>");
        out.println("  </body>");
        out.println("</html>");
    }

    public static String escapeHtml(String value) {
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
}
