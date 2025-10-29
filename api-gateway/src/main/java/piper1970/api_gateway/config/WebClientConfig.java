package piper1970.api_gateway.config;

import static io.netty.handler.ssl.SslProvider.JDK;

import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for outgoing, reactive web-client, ensuring keystore/truststore properties are set, if ssl is enabled.
 * Also ensures web-client has OAuth2 Authorization Middleware running as a filter.
 */
@Configuration
@Slf4j
public class WebClientConfig {

  @Bean
  WebClient webClient(ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
      SslBundles sslBundles,
      @Value("${server.ssl.enabled:false}") boolean sslEnabled) {

    // Use Netty reactive version of HttpClient, rather than JDBC version
    reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create();

    // conditionally add SSL capabilities to httpClient used by web-client
    if (sslEnabled) {
      try {
        SslBundle sslBundle = sslBundles.getBundle("event-booking-service");
        var managers = sslBundle.getManagers();

        // Netty's HttpClient requires its own SslContext version.
        // expects bundle to have both keystore and truststore managers setup
        var sslContext = io.netty.handler.ssl.SslContextBuilder
            .forClient()
            .sslProvider(JDK)
            .trustManager(managers.getTrustManagerFactory())
            .keyManager(managers.getKeyManagerFactory())
            .build();

        httpClient = httpClient.secure(spec -> spec.sslContext(sslContext));

      } catch (SSLException e) {
        // Abort if we get here.  System cannot run without ssl-context properly set up
        log.error("SSL exception. Aborting...", e);
        throw new RuntimeException(e);
      }
    }

    return WebClient.builder()
        // Use reactive ReactorClientHttpConnector rather than JDBC version
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        // filter/middleware to ensure OAuth2 secured calls from client
        .filter(new ServerOAuth2AuthorizedClientExchangeFilterFunction(
            authorizedClientManager))
        .build();
  }

    @Bean
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager (
        ReactiveClientRegistrationRepository clientRegistrationRepository,
        ReactiveOAuth2AuthorizedClientService oAuth2AuthorizedClientService
  ){
      return new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
          clientRegistrationRepository, oAuth2AuthorizedClientService);
    }
  }
