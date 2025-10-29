package piper1970.eventservice.common.observations;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

/**
 * Custom ObservationRegistryCustomizer to avoid tracing actuator and spring security activity.
 *
 * @see ObservationRegistryCustomizer
 * @see ObservationRegistry
 */
public class TracingObservationCustomizer implements
    ObservationRegistryCustomizer<ObservationRegistry> {

  private static final PathMatcher pathMatcher = new AntPathMatcher("/");
  private static final String securityPrefix = "spring.security";

  @Override
  public void customize(ObservationRegistry registry) {
    registry.observationConfig()
        .observationPredicate((name, observation) -> {
          if (observation instanceof ServerRequestObservationContext observationContext) {
            return !pathMatcher.match("/actuator/**",
                observationContext.getCarrier().getPath().value());
          } else {
            return !name.startsWith(securityPrefix);
          }
        });
  }
}
