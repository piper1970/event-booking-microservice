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

@Configuration
@Slf4j
public class WebClientConfig {

  @Bean
  WebClient webClient(ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
      SslBundles sslBundles,
      @Value("${server.ssl.enabled:false}") boolean sslEnabled) {

    var oauth2Client = new ServerOAuth2AuthorizedClientExchangeFilterFunction(
        authorizedClientManager);

    var builder = WebClient.builder();

    // Use Netty version of HttpClient, rather than JDBC, since it is
    reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create();

    // add SSL capabilities conditionally
    if (sslEnabled) {

      try {

        SslBundle sslBundle = sslBundles.getBundle("event-booking-service");
        var managers = sslBundle.getManagers();

        // Netty's HttpClient requires its own SslContext version.
        var sslContext = io.netty.handler.ssl.SslContextBuilder
            .forClient()
            .sslProvider(JDK)
            .trustManager(managers.getTrustManagerFactory())
            .keyManager(managers.getKeyManagerFactory())
            .build();

        httpClient.secure(spec -> spec.sslContext(sslContext));

      } catch (SSLException e) {
        log.error("SSL exception. Aborting...", e);
        throw new RuntimeException(e);
      }
    }

    // Use ReactorClientHttpConnector rather than JDBC version
    builder.clientConnector(new ReactorClientHttpConnector(httpClient));

    return builder
        .filter(oauth2Client)
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
