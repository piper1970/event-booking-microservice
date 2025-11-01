package piper1970.bookingservice.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * SSL Configuration for setting trust-store system properties
 */
@Configuration
@Profile({"ssl_local", "ssl_compose"})
public class SystemSSLConfig {

  private final String trustStorePath;
  private final String trustStorePassword;

  public SystemSSLConfig(
      @Value("${BOOKINGS_TRUSTSTORE_PATH}")String trustStorePath,
      @Value("${BOOKINGS_TRUSTSTORE_PASSWORD}")String trustStorePassword) {
    this.trustStorePath = trustStorePath;
    this.trustStorePassword = trustStorePassword;
  }

  @PostConstruct
  public void configureTrustStore() {

    System.setProperty("javax.net.ssl.trustStore", trustStorePath);
    System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
    System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
  }
}
