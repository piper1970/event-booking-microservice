package piper1970.api_gateway.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SystemPropsConfig {

  private final String trustStorePath;
  private final String trustStorePassword;

  public SystemPropsConfig(
      @Value("${API_TRUSTSTORE_PATH}") String trustStorePath,
      @Value("${API_TRUSTSTORE_PASSWORD}") String trustStorePassword) {
    this.trustStorePath = trustStorePath;
    this.trustStorePassword = trustStorePassword;
  }

  @PostConstruct
  public void configureSsl() {

    // Hard-setting these values due to extended issues setting up SSL with OAuth2 services
    System.setProperty("javax.net.ssl.trustStore", trustStorePath);
    System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
    System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
  }

}
