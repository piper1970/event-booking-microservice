package piper1970.notificationservice.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import piper1970.eventservice.common.observations.TracingObservationCustomizer;

/**
 * Configuration for zipkin tracing. Setup to skip actuator and security calls in tracing
 *
 * @see TracingObservationCustomizer
 */
@Configuration
public class ObservationConfig {

  @Bean
  ObservationRegistryCustomizer<ObservationRegistry> skipActuatorAndSecurityCustomizer() {
    return new TracingObservationCustomizer();
  }
}
