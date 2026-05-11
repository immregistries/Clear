package org.immregistries.clear;

import java.io.InputStream;
import java.util.Properties;

public class ClearConfig {
    private static final String DEFAULT_CLEAR_EXTERNAL_URL = "https://informatics.immregistries.org/clear";
    private static final String HUB_EXTERNAL_URL_DEFAULT = "https://informatics.immregistries.org/hub";
    private static final boolean DEFAULT_AUTH_ENABLED = true;

    public static final String CLEAR_EXTERNAL_URL;
    public static final String HUB_EXTERNAL_URL_DEFAULT_PROD = HUB_EXTERNAL_URL_DEFAULT;
    public static final boolean AUTH_ENABLED;

    static {
        Properties properties = loadProperties("/clear-config.properties");
        CLEAR_EXTERNAL_URL = resolveString(properties, "clear.external.url", DEFAULT_CLEAR_EXTERNAL_URL);
        AUTH_ENABLED = resolveBoolean(properties, "auth.enabled", DEFAULT_AUTH_ENABLED);
    }

    private ClearConfig() {
        // Utility class
    }

    private static Properties loadProperties(String resourcePath) {
        Properties properties = new Properties();
        try (InputStream inputStream = ClearConfig.class.getResourceAsStream(resourcePath)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (Exception e) {
            // Use defaults when config cannot be loaded.
        }
        return properties;
    }

    private static String resolveString(Properties properties, String key, String defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static boolean resolveBoolean(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
}
