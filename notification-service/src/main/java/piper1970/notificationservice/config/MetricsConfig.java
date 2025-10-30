package piper1970.notificationservice.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for custom metrics
 */
@Configuration
public class MetricsConfig {

  /**
   * Counter metric covering number confirmations that expired before being clicked
   */
  @Bean
  @Qualifier("expirations")
  public Counter expiredConfirmationCounter(MeterRegistry registry) {
    return Counter.builder("confirmations.expired")
        .description("Number of confirmation requests that expired before being confirmed")
        .register(registry);
  }

  /**
   * Counter metric covering number confirmations that were clicked in time
   */
  @Bean
  @Qualifier("confirmations")
  public Counter succeededConfirmation(MeterRegistry registry) {
    return Counter.builder("confirmations.succeeded")
        .description("Number of succeeded confirmations")
        .register(registry);
  }
}
