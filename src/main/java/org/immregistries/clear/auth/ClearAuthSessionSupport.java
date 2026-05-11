package org.immregistries.clear.auth;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.immregistries.interophub.client.HubClientConfig;
import org.immregistries.interophub.client.HubExchangeResult;
import org.immregistries.interophub.client.HubUserInfo;
import org.immregistries.interophub.client.InteropHubClient;
import org.immregistries.interophub.client.InteropHubClientFactory;
import org.immregistries.clear.ClearConfig;
import org.immregistries.clear.model.Contact;
import org.immregistries.clear.model.SystemSetting;
import org.immregistries.clear.utils.HibernateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClearAuthSessionSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ClearAuthSessionSupport.class);

    private static final String APP_CODE = "clear";
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 8000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 12000;
    private static final String SESSION_USER_ATTRIBUTE = "clearAuthenticatedUser";
    private static final String ORIGINAL_REQUEST_ATTRIBUTE = "clearOriginalRequest";
    private static final String HUB_SETTING_KEY = "hub.external.url";
    private static final String CLEAR_EXTERNAL_URL_SETTING_KEY = "clear.external.url";
    private static final String HUB_URL_ENV = "CLEAR_HUB_URL";
    private static final String CLEAR_EXTERNAL_URL_ENV = "CLEAR_EXTERNAL_URL";

    private static volatile InteropHubClient hubClient;
    private static volatile String lastKnownHubUrl;
    private static volatile String lastKnownClearExternalUrl;

    private ClearAuthSessionSupport() {
        // Utility class
    }

    public static SessionUser getSessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_USER_ATTRIBUTE);
        if (!(value instanceof SessionUser)) {
            return null;
        }
        SessionUser sessionUser = (SessionUser) value;
        if (!sessionUser.isAllowed() || isBlank(sessionUser.getEmail())) {
            clearSessionUser(request);
            return null;
        }
        return sessionUser;
    }

    public static void setSessionUser(HttpServletRequest request, SessionUser sessionUser) {
        request.getSession(true).setAttribute(SESSION_USER_ATTRIBUTE, sessionUser);
    }

    public static void clearSessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_USER_ATTRIBUTE);
            session.removeAttribute(ORIGINAL_REQUEST_ATTRIBUTE);
        }
    }

    public static void saveOriginalRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String path = query == null || query.isEmpty() ? uri : uri + "?" + query;
        request.getSession(true).setAttribute(ORIGINAL_REQUEST_ATTRIBUTE, path);
    }

    public static String consumeOriginalRequest(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(ORIGINAL_REQUEST_ATTRIBUTE);
        session.removeAttribute(ORIGINAL_REQUEST_ATTRIBUTE);
        if (value instanceof String) {
            String path = ((String) value).trim();
            if (!path.isEmpty()) {
                return path;
            }
        }
        return null;
    }

    public static void redirectToHubLogin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        InteropHubClient client = getInteropHubClient();
        String redirectUrl = client.buildLoginUrl(getLoginCallbackUrl());
        response.sendRedirect(redirectUrl);
    }

    public static SessionUser exchangeCodeAndAuthorize(HttpServletRequest request, String code) {
        InteropHubClient client = getInteropHubClient();
        String hubUrl = resolveHubUrl();
        String clearExternalUrl = resolveClearExternalUrl();

        String clientIP = getClientIpAddress(request);
        LOG.info("Initiating Hub code exchange. code={} clientIP={} hubUrl={} clearExternalUrl={}",
                code, clientIP, hubUrl, clearExternalUrl);

        try {
            HubExchangeResult exchangeResult = client.exchangeCode(code, clientIP);

            if (exchangeResult == null) {
                LOG.error("Hub code exchange returned null result");
                SessionUser denied = new SessionUser();
                denied.setAllowed(false);
                denied.setDenialReason("Authorization exchange failed: empty response from Hub.");
                return denied;
            }

            if (!exchangeResult.isSuccess()) {
                String error = trimToNull(exchangeResult.getErrorMessage());
                if (error == null) {
                    error = "Authorization exchange failed with HTTP status " + exchangeResult.getHttpStatus() + ".";
                }
                LOG.error("Hub code exchange failed. status={} errorMessage={} hasUserInfo={} responseBody={}",
                        exchangeResult.getHttpStatus(), exchangeResult.getErrorMessage(),
                        exchangeResult.hasRequiredUserInfo(), exchangeResult.getResponseBody());
                SessionUser denied = new SessionUser();
                denied.setAllowed(false);
                denied.setDenialReason(error);
                return denied;
            }

            HubUserInfo userInfo = exchangeResult.getUserInfo();
            String responseBody = exchangeResult.getResponseBody();

            String email = toLowerSafe(firstNonBlank(
                    userInfo == null ? null : userInfo.getEmail(),
                    extractJsonString(responseBody, "email"),
                    extractJsonString(responseBody, "emailAddress")));

            String firstName = firstNonBlank(
                    userInfo == null ? null : userInfo.getFirstName(),
                    extractJsonString(responseBody, "first_name"),
                    extractJsonString(responseBody, "firstName"));

            String lastName = firstNonBlank(
                    userInfo == null ? null : userInfo.getLastName(),
                    extractJsonString(responseBody, "last_name"),
                    extractJsonString(responseBody, "lastName"));

            String fallbackName = firstNonBlank(
                    userInfo == null ? null : userInfo.getName(),
                    extractJsonString(responseBody, "name"));
            String displayName = buildDisplayName(firstName, lastName, email);
            if (!isBlank(fallbackName) && displayName.equals(email)) {
                displayName = fallbackName;
            }

            if (isBlank(email)) {
                LOG.warn("Hub exchange succeeded but email is missing. hasRequiredUserInfo={} responseBody={}",
                        exchangeResult.hasRequiredUserInfo(), exchangeResult.getResponseBody());
                SessionUser denied = new SessionUser();
                denied.setAllowed(false);
                denied.setDenialReason("Hub login succeeded but did not include an email for this app.");
                return denied;
            }

            SessionUser sessionUser = new SessionUser();
            sessionUser.setEmail(email);
            sessionUser.setFirstName(firstName);
            sessionUser.setLastName(lastName);
            sessionUser.setDisplayName(displayName);

            if (email.endsWith("@immregistries.org")) {
                LOG.info("User authenticated as admin. email={} displayName={}", email, displayName);
                sessionUser.setAllowed(true);
                sessionUser.setAdmin(true);
                return sessionUser;
            }

            Contact contact = findContactByEmail(email);
            if (contact == null) {
                LOG.info("User not found in Contact table. email={}", email);
                sessionUser.setAllowed(false);
                sessionUser.setAdmin(false);
                sessionUser.setDenialReason("Your account is not associated with a registered Contact in CLEAR.");
                return sessionUser;
            }

            LOG.info("User authenticated as non-admin. email={} displayName={} contactId={} jurisdictionId={}",
                    email, displayName, contact.getContactId(), contact.getJurisdictionId());
            sessionUser.setAllowed(true);
            sessionUser.setAdmin(false);
            sessionUser.setContactId(contact.getContactId());
            sessionUser.setJurisdictionId(contact.getJurisdictionId());
            return sessionUser;
        } catch (Exception e) {
            LOG.error("Unexpected error during Hub code exchange", e);
            SessionUser denied = new SessionUser();
            denied.setAllowed(false);
            denied.setDenialReason("An unexpected error occurred during authentication.");
            return denied;
        }
    }

    public static String getAccessDeniedRedirect(HttpServletRequest request, String reason) {
        String encoded = URLEncoder.encode(reason, StandardCharsets.UTF_8);
        return request.getContextPath() + "/access-denied?reason=" + encoded;
    }

    private static Contact findContactByEmail(String email) {
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            Query<Contact> query = session.createQuery(
                    "FROM Contact WHERE lower(emailAddress) = :email", Contact.class);
            query.setParameter("email", email);
            List<Contact> contacts = query.list();
            return contacts.isEmpty() ? null : contacts.get(0);
        } catch (Exception e) {
            LOG.warn("Unable to query Contact by email", e);
            return null;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    private static InteropHubClient getInteropHubClient() {
        String hubUrl = resolveHubUrl();
        String clearExternalUrl = resolveClearExternalUrl();
        if (hubClient == null || !hubUrl.equals(lastKnownHubUrl)
                || !clearExternalUrl.equals(lastKnownClearExternalUrl)) {
            synchronized (ClearAuthSessionSupport.class) {
                if (hubClient == null || !hubUrl.equals(lastKnownHubUrl)
                        || !clearExternalUrl.equals(lastKnownClearExternalUrl)) {
                    hubClient = createInteropHubClient(hubUrl, clearExternalUrl);
                    lastKnownHubUrl = hubUrl;
                    lastKnownClearExternalUrl = clearExternalUrl;
                    LOG.info("InteropHub client initialized with clearExternalUrl={} hubUrl={}", clearExternalUrl,
                            hubUrl);
                }
            }
        }
        return hubClient;
    }

    private static String resolveHubUrl() {
        String envValue = trimToNull(System.getenv(HUB_URL_ENV));
        if (envValue != null) {
            return envValue;
        }

        String dbValue = trimToNull(loadSettingFromDatabase(HUB_SETTING_KEY));
        if (dbValue != null) {
            return dbValue;
        }

        return ClearConfig.HUB_EXTERNAL_URL_DEFAULT_PROD;
    }

    private static String resolveClearExternalUrl() {
        String envValue = trimToNull(System.getenv(CLEAR_EXTERNAL_URL_ENV));
        if (envValue != null) {
            return envValue;
        }

        String dbValue = trimToNull(loadSettingFromDatabase(CLEAR_EXTERNAL_URL_SETTING_KEY));
        if (dbValue != null) {
            return dbValue;
        }

        return trimToNull(ClearConfig.CLEAR_EXTERNAL_URL) == null
                ? "https://informatics.immregistries.org/clear"
                : ClearConfig.CLEAR_EXTERNAL_URL;
    }

    private static String loadSettingFromDatabase(String key) {
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            SystemSetting setting = session.get(SystemSetting.class, key);
            if (setting == null) {
                return null;
            }
            return setting.getSettingValue();
        } catch (Exception e) {
            LOG.info("SystemSetting table unavailable while reading key={}, using fallback values.", key);
            return null;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    private static InteropHubClient createInteropHubClient(String hubUrl, String clearExternalUrl) {
        HubClientConfig config = new HubClientConfig(
                clearExternalUrl,
                hubUrl,
                APP_CODE,
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS);
        return InteropHubClientFactory.create(config);
    }

    private static String getLoginCallbackUrl() {
        String basePath = resolveClearExternalUrl();
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        return basePath + "/login";
    }

    private static String buildDisplayName(String firstName, String lastName, String email) {
        StringBuilder builder = new StringBuilder();
        if (!isBlank(firstName)) {
            builder.append(firstName.trim());
        }
        if (!isBlank(lastName)) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(lastName.trim());
        }
        if (builder.length() == 0) {
            return email;
        }
        return builder.toString();
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (!isBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String extractJsonString(String body, String key) {
        if (isBlank(body) || isBlank(key)) {
            return null;
        }
        String quotedKey = "\"" + key + "\"";
        int keyPos = body.indexOf(quotedKey);
        if (keyPos < 0) {
            return null;
        }
        int colonPos = body.indexOf(':', keyPos + quotedKey.length());
        if (colonPos < 0) {
            return null;
        }
        int valueStart = body.indexOf('"', colonPos + 1);
        if (valueStart < 0) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = valueStart + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                value.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        return null;
    }

    private static String toLowerSafe(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String getClientIpAddress(HttpServletRequest request) {
        // Check for X-Forwarded-For header (proxy/load balancer)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        // Check for X-Real-IP header (nginx proxy)
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP.trim();
        }

        // Fall back to remote address
        return request.getRemoteAddr();
    }
}
