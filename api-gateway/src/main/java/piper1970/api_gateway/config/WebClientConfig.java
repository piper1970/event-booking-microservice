package piper1970.api_gateway.config;

import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  @Bean
  WebClient webClient(ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
      SslBundles sslBundles,
      @Value("${server.ssl.enabled:false}") boolean sslEnabled) {
    var oauth2Client = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

    var builder = WebClient.builder();

    // add SSL capabilities conditionally
    if(sslEnabled) {
      SslBundle sslBundle = sslBundles.getBundle("event-booking-service-bundle");
      HttpClient httpClient = HttpClient.newBuilder()
          .sslContext(sslBundle.createSslContext())
          .build();
      builder.clientConnector(new JdkClientHttpConnector(httpClient));
    }

    return builder
        .filter(oauth2Client)
        .build();
  }

  @Bean
  public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
      ReactiveClientRegistrationRepository clientRegistrationRepository,
      ReactiveOAuth2AuthorizedClientService oAuth2AuthorizedClientService
  ){
    return new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(clientRegistrationRepository, oAuth2AuthorizedClientService);
  }
}
