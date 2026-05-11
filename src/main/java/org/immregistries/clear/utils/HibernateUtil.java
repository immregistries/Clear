package org.immregistries.clear.utils;

import java.util.HashMap;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateUtil {
	private static final String DB_DRIVER_ENV = "CLEAR_DB_DRIVER";
	private static final String DB_URL_ENV = "CLEAR_DB_URL";
	private static final String DB_USER_ENV = "CLEAR_DB_USER";
	private static final String DB_PASSWORD_ENV = "CLEAR_DB_PASSWORD";

	private static SessionFactory sessionFactory = buildSessionFactory();

	private static SessionFactory buildSessionFactory() {
		try {
			if (sessionFactory == null) {
				Map<String, String> dbSettings = loadRequiredDbSettings();

				Configuration config = new Configuration();
				config.configure("hibernate.cfg.xml");

				// Override with environment variables
				config.setProperty("hibernate.connection.driver_class", dbSettings.get(DB_DRIVER_ENV));
				config.setProperty("hibernate.connection.url", dbSettings.get(DB_URL_ENV));
				config.setProperty("hibernate.connection.username", dbSettings.get(DB_USER_ENV));
				config.setProperty("hibernate.connection.password", dbSettings.get(DB_PASSWORD_ENV));

				sessionFactory = config.buildSessionFactory();
			}
			return sessionFactory;
		} catch (Throwable ex) {
			throw new ExceptionInInitializerError(ex);
		}
	}

	private static Map<String, String> loadRequiredDbSettings() {
		Map<String, String> settings = new HashMap<>();
		settings.put(DB_DRIVER_ENV, requireEnvVar(DB_DRIVER_ENV));
		settings.put(DB_URL_ENV, requireEnvVar(DB_URL_ENV));
		settings.put(DB_USER_ENV, requireEnvVar(DB_USER_ENV));
		settings.put(DB_PASSWORD_ENV, requireEnvVar(DB_PASSWORD_ENV));
		return settings;
	}

	private static String requireEnvVar(String key) {
		String value = System.getenv(key);
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalStateException("Missing required environment variable: " + key);
		}
		return value.trim();
	}

	public static SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	public static void shutdown() {
		getSessionFactory().close();
	}
}