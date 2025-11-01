package piper1970.bookingservice.config;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient Configuration for setting load-balanced web client usage with event-service web client.
 */
@Configuration
@Slf4j
public class WebClientConfig {

  private final String apiUri;

  public WebClientConfig(@Value("${api.event-service.uri}") String apiUri) {
    this.apiUri = apiUri;
  }

  @Bean
  @LoadBalanced
  public WebClient.Builder webClientBuilder(ObservationRegistry registry) {

    log.debug("Setting up web client with base uri {}", apiUri);

    return WebClient.builder()
        .observationRegistry(registry)
        .baseUrl(apiUri);
  }
}
