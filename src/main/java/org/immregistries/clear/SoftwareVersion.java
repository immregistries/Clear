package org.immregistries.clear;

import java.io.InputStream;
import java.util.Properties;

public class SoftwareVersion {
  public static final String VERSION = loadVersion();

  private static String loadVersion() {
    String fallbackVersion = "unknown";
    try (InputStream inputStream = SoftwareVersion.class.getResourceAsStream("/version.properties")) {
      if (inputStream == null) {
        return fallbackVersion;
      }
      Properties properties = new Properties();
      properties.load(inputStream);
      return properties.getProperty("app.version", fallbackVersion);
    } catch (Exception e) {
      return fallbackVersion;
    }
  }
}
