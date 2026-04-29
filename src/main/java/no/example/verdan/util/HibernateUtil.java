package no.example.verdan.util;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

public class HibernateUtil {
  private static final EntityManagerFactory emf;

  static {
    Map<String, String> properties = new HashMap<>();

    // Database credentials are loaded exclusively from environment variables.
    // Set DB_HOST, DB_USER, and DB_PASS before starting the application.
    // See .env.example for the required format.
    String host = requireEnv("DB_HOST");
    String user = requireEnv("DB_USER");
    String pass = requireEnv("DB_PASS");

    // SSL can be disabled for CI/test environments where MySQL has no SSL configured.
    // Set MYSQL_SSL=false to disable. Defaults to true for production safety.
    boolean useSSL = !"false".equalsIgnoreCase(System.getenv("MYSQL_SSL"));

    properties.put("jakarta.persistence.jdbc.url",
        "jdbc:mysql://" + host + ":3306/verdan_db?createDatabaseIfNotExist=true&useSSL=" + useSSL + "&requireSSL=false");
    properties.put("jakarta.persistence.jdbc.user", user);
    properties.put("jakarta.persistence.jdbc.password", pass);

    emf = Persistence.createEntityManagerFactory("verdanPU", properties);
  }

  /**
   * Reads a required environment variable. Fails fast with a clear error
   * message if the variable is not set, preventing startup with missing
   * database configuration.
   */
  private static String requireEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isEmpty()) {
      throw new IllegalStateException(
          "Required environment variable '" + name + "' is not set. "
        + "Set DB_HOST, DB_USER, and DB_PASS before starting the application. "
        + "See .env.example for details.");
    }
    return value;
  }

  public static EntityManagerFactory emf() {
    return emf;
  }
}
