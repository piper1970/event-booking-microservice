package piper1970.eventservice.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import piper1970.eventservice.common.observations.TracingObservationCustomizer;

/**
 * Zipkin traceability configuration to skip actuator and security traces.
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
