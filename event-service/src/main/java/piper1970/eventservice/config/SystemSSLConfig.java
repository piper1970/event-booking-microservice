package piper1970.eventservice.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SystemSSLConfig {

  private final String trustStorePath;
  private final String trustStorePassword;

  public SystemSSLConfig(
      @Value("${EVENTS_TRUSTSTORE_PATH}")String trustStorePath,
      @Value("${EVENTS_TRUSTSTORE_PASSWORD}")String trustStorePassword) {
    this.trustStorePath = trustStorePath;
    this.trustStorePassword = trustStorePassword;
  }

  @PostConstruct
  public void configureSsl() {

    System.setProperty("javax.net.ssl.trustStore", trustStorePath);
    System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
    System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
  }
}
