package org.immregistries.clear.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.Session;
import org.immregistries.clear.model.SystemSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SystemSettingSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SystemSettingSupport.class);

    private SystemSettingSupport() {
        // Utility class
    }

    public static String getValue(String key) {
        Map<String, String> values = getValues(key);
        return values.get(key);
    }

    public static String getValueOrDefault(String key, String defaultValue) {
        String value = trimToNull(getValue(key));
        return value == null ? defaultValue : value;
    }

    public static Map<String, String> getValues(String... keys) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (keys == null) {
            return values;
        }

        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            for (String key : keys) {
                if (key == null) {
                    continue;
                }
                SystemSetting setting = session.get(SystemSetting.class, key);
                values.put(key, setting == null ? null : setting.getSettingValue());
            }
        } catch (Exception e) {
            LOG.info("SystemSetting table unavailable while reading keys, using fallback values.", e);
        } finally {
            if (session != null) {
                session.close();
            }
        }
        return values;
    }

    public static boolean parseBoolean(String value, boolean defaultValue) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "on".equalsIgnoreCase(normalized);
    }

    public static Integer parseInteger(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
