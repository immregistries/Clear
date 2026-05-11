# InteropHub Integration Guide for Clear

This document describes how to integrate the InteropHub-Client library into a new application called **Clear**, based on the reference implementation in StepIntoCDSI. Clear connects to the same InteropHub endpoint but differs in one key way: the Hub URL is stored in a database rather than being hardcoded in the application.

---

## How StepIntoCDSI Uses InteropHub

The integration is built from five components working together:

```
Browser Request
    ↓
AuthenticationFilter (/*)
    ↓ (if unauthenticated)
Redirect → Hub login
    ↓ (Hub sends back ?code=...)
LoginServlet (/login)
    ↓
AuthSessionSupport.exchangeCode()
    ↓
SessionUser stored in HttpSession
    ↓
Protected pages accessible
```

**Core objects from the library:**

| Class | Role |
|---|---|
| `HubClientConfig` | Holds your app's external URL, the Hub URL, your app code, and timeouts |
| `InteropHubClientFactory.create(config)` | Builds the client instance |
| `InteropHubClient` | Single interface: build login URL, exchange auth code, determine redirect |
| `HubExchangeResult` / `HubUserInfo` | Response objects from code exchange |

In StepIntoCDSI, all URLs are known at startup (from `software-version.properties` + Maven filtering), so `InteropHubClient` is a `static final` field initialized once at class load.

---

## What's Different for Clear

**The key architectural difference**: Clear stores the Hub URL in a database. This means the `InteropHubClient` cannot be a `static final` — it must be created lazily after the database is accessible. You'll also need to handle the case where no Hub URL is configured yet.

---

## Step-by-Step Implementation for Clear

### 1. Maven Dependency

In Clear's `pom.xml`, add:

```xml
<properties>
    <interophub.client.version>1.0.0</interophub.client.version>
    <!-- Clear's own deployed URL — still build-time config -->
    <clear.external.url>https://yourserver.org/clear</clear.external.url>
    <auth.enabled>true</auth.enabled>
</properties>

<dependency>
    <groupId>org.immregistries</groupId>
    <artifactId>interophub-client</artifactId>
    <version>${interophub.client.version}</version>
</dependency>
```

### 2. Properties File (for Clear's own URL only)

Create `src/main/resources/clear-config.properties` (Maven-filtered):

```properties
clear.external.url=${clear.external.url}
auth.enabled=${auth.enabled}
```

**Note:** `hub.external.url` is intentionally absent — Clear reads that from the database.

### 3. `ClearConfig.java` (equivalent of `SoftwareVersion.java` in StepIntoCDSI)

```java
public class ClearConfig {
    private static final String DEFAULT_CLEAR_EXTERNAL_URL = "https://yourserver.org/clear";
    private static final boolean DEFAULT_AUTH_ENABLED = false;

    public static final String CLEAR_EXTERNAL_URL;
    public static final boolean AUTH_ENABLED;

    static {
        // Load only the app's own URL from properties — Hub URL comes from DB
        Properties props = loadProperties("clear-config.properties");
        CLEAR_EXTERNAL_URL = resolve(props, "clear.external.url", DEFAULT_CLEAR_EXTERNAL_URL);
        AUTH_ENABLED = resolveBoolean(props, "auth.enabled", DEFAULT_AUTH_ENABLED);
    }
    // ... same loading/resolve logic as SoftwareVersion.java
}
```

### 4. `ClearAuthSessionSupport.java` (the key difference — lazy client)

This is where Clear diverges from StepIntoCDSI. Instead of `static final InteropHubClient HUB_CLIENT`, use a lazy-initialized client that reads the Hub URL from the database on first use and rebuilds when it changes:

```java
public final class ClearAuthSessionSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ClearAuthSessionSupport.class);
    private static final String APP_CODE = "clear";  // Register this app code with the Hub
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 8000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 12000;

    // Lazily initialized — hub URL comes from DB
    private static volatile InteropHubClient hubClient;
    private static volatile String lastKnownHubUrl;

    public static final String SESSION_USER_ATTRIBUTE = "clearAuthenticatedUser";

    private ClearAuthSessionSupport() {}

    /**
     * Returns the hub client, building/rebuilding it if the hub URL in the
     * database has changed. Returns null if no hub URL is configured.
     */
    public static InteropHubClient getInteropHubClient() {
        String hubUrl = loadHubUrlFromDatabase();  // your DB read method
        if (hubUrl == null || hubUrl.isBlank()) {
            return null;
        }
        if (hubClient == null || !hubUrl.equals(lastKnownHubUrl)) {
            synchronized (ClearAuthSessionSupport.class) {
                if (hubClient == null || !hubUrl.equals(lastKnownHubUrl)) {
                    hubClient = InteropHubClientFactory.create(new HubClientConfig(
                            ClearConfig.CLEAR_EXTERNAL_URL,
                            hubUrl,
                            APP_CODE,
                            DEFAULT_CONNECT_TIMEOUT_MS,
                            DEFAULT_READ_TIMEOUT_MS));
                    lastKnownHubUrl = hubUrl;
                    LOG.info("InteropHubClient initialized with hubUrl={}", hubUrl);
                }
            }
        }
        return hubClient;
    }

    private static String loadHubUrlFromDatabase() {
        // Pull from your DB/config table — e.g. a "system_settings" table
        // with key = "hub.external.url"
        return YourConfigRepository.getSetting("hub.external.url");
    }

    // --- Same helper methods as StepIntoCDSI's AuthSessionSupport ---

    public static SessionUser getSessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object value = session.getAttribute(SESSION_USER_ATTRIBUTE);
        if (value instanceof SessionUser) {
            SessionUser sessionUser = (SessionUser) value;
            if (isBlank(sessionUser.getDisplayName()) || isBlank(sessionUser.getEmail())) {
                session.removeAttribute(SESSION_USER_ATTRIBUTE);
                return null;
            }
            return sessionUser;
        }
        return null;
    }

    public static void clearSessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_USER_ATTRIBUTE);
        }
    }

    public static void redirectToHubLogin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        InteropHubClient client = getInteropHubClient();
        if (client == null) {
            // Hub not configured yet — redirect to an admin setup page
            response.sendRedirect(request.getContextPath() + "/admin/hub-setup");
            return;
        }
        String redirectUrl = client.buildLoginUrl(getCurrentUrl(request));
        LOG.info("Redirecting to Hub login: redirectUrl={}", redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    public static String getCurrentUrl(HttpServletRequest request) {
        // Same pattern as StepIntoCDSI but using ClearConfig.CLEAR_EXTERNAL_URL
        String basePath = ClearConfig.CLEAR_EXTERNAL_URL;
        if (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        String query = request.getQueryString();
        String result = basePath + requestPath;
        return (query != null && !query.isEmpty()) ? result + "?" + query : result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
```

### 5. `ClearAuthenticationFilter.java` (nearly identical to StepIntoCDSI)

Copy `AuthenticationFilter` from StepIntoCDSI and adjust:
- Replace `SoftwareVersion.AUTH_ENABLED` with `ClearConfig.AUTH_ENABLED`
- Replace `AuthSessionSupport` references with `ClearAuthSessionSupport`
- Update `isPublicPath()` to whitelist Clear's public paths

```java
private boolean isPublicPath(HttpServletRequest request) {
    // ...same URI-stripping logic as StepIntoCDSI...
    return path.equals("/login")
        || path.equals("/logout")
        || path.equals("/temp-auth")
        || path.startsWith("/api/public/");  // adjust for Clear's public endpoints
}
```

### 6. `LoginServlet.java` (same logic, different app code)

Copy StepIntoCDSI's `LoginServlet` and change:
- `APP_CODE = "clear"`
- All `AuthSessionSupport` references → `ClearAuthSessionSupport`
- The home redirect path should point to Clear's home (e.g. `/home`)

The code exchange flow (`exchangeCode()`, `determineRedirectDecision()`) is identical.

### 7. `SessionUser.java`

Copy directly from StepIntoCDSI — it is a simple serializable value object with `displayName`, `organization`, `title`, and `email` fields. No changes are needed.

### 8. `web.xml` registrations

```xml
<filter>
    <filter-name>authenticationFilter</filter-name>
    <filter-class>your.package.auth.ClearAuthenticationFilter</filter-class>
</filter>

<servlet>
    <servlet-name>login</servlet-name>
    <servlet-class>your.package.servlet.LoginServlet</servlet-class>
</servlet>
<servlet>
    <servlet-name>logout</servlet-name>
    <servlet-class>your.package.servlet.LogoutServlet</servlet-class>
</servlet>

<servlet-mapping>
    <servlet-name>login</servlet-name>
    <url-pattern>/login</url-pattern>
</servlet-mapping>
<servlet-mapping>
    <servlet-name>logout</servlet-name>
    <url-pattern>/logout</url-pattern>
</servlet-mapping>

<filter-mapping>
    <filter-name>authenticationFilter</filter-name>
    <url-pattern>/*</url-pattern>
    <dispatcher>REQUEST</dispatcher>
    <dispatcher>FORWARD</dispatcher>
</filter-mapping>
```

---

## The Database Configuration Table

A simple `system_settings` table is recommended to store the Hub URL and other runtime-configurable values:

```sql
CREATE TABLE system_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500)
);

INSERT INTO system_settings (setting_key, setting_value)
VALUES ('hub.external.url', 'https://informatics.immregistries.org/hub');
```

Clear's admin UI should have a settings page that allows an administrator to update this value. When it changes, the `synchronized` block in `getInteropHubClient()` will automatically rebuild the `InteropHubClient` on the next request — no restart required.

---

## What to Register with the Hub

Before authentication will work, the Hub must recognize "clear" as an authorized application. You will need to register:

| Field | Value |
|---|---|
| **App code** | `clear` |
| **Callback/redirect URL** | `https://yourserver.org/clear/login` |
| **App home URL** | `https://yourserver.org/clear/home` |

These correspond to the `appCode` and `stepExternalUrl` parameters in `HubClientConfig`, and to the callback path that the Hub will redirect back to after a successful login.

---

## Reference: StepIntoCDSI Files to Use as Templates

| StepIntoCDSI file | Clear equivalent | Changes needed |
|---|---|---|
| `SoftwareVersion.java` | `ClearConfig.java` | Remove hub URL; rename constants |
| `AuthSessionSupport.java` | `ClearAuthSessionSupport.java` | Lazy client from DB; handle null client |
| `AuthenticationFilter.java` | `ClearAuthenticationFilter.java` | Update public paths; swap config/support refs |
| `LoginServlet.java` | `LoginServlet.java` | Change `APP_CODE`; swap support refs |
| `SessionUser.java` | `SessionUser.java` | Copy as-is |
| `LogoutServlet.java` | `LogoutServlet.java` | Copy as-is; adjust redirect target |
| `software-version.properties` | `clear-config.properties` | Only `clear.external.url` and `auth.enabled` |
