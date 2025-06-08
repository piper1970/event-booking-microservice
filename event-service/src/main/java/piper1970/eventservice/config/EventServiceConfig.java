package piper1970.eventservice.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import piper1970.eventservice.common.oauth2.extractors.GrantedAuthoritiesExtractor;
import piper1970.eventservice.common.validation.validators.CustomFutureValidator;
import piper1970.eventservice.common.validation.validators.context.ValidationContextProvider;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class EventServiceConfig {

  private final GrantedAuthoritiesExtractor grantedAuthoritiesExtractor;

  public EventServiceConfig(@Value("${oauth2.client.id}") String clientId) {
    this.grantedAuthoritiesExtractor = new GrantedAuthoritiesExtractor(clientId);
  }

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http.csrf(CsrfSpec::disable)
        .cors(withDefaults())
        .authorizeExchange(exchange -> exchange
            // passthrough for actuator and open-api/swagger
            .pathMatchers(HttpMethod.GET, "/actuator/**", "/events/api-docs",
                "/events/api-docs/**", "/events/swagger-ui/**").permitAll()
            .pathMatchers(HttpMethod.OPTIONS, "*").permitAll()
            .anyExchange()
            .authenticated())
        .oauth2ResourceServer(oauth2 ->
            oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(grantedAuthenticationConverter())));

    return http.build();
  }

  @Bean
  public CorsWebFilter corsWebFilter() {

    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.addAllowedOrigin("*");
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return new CorsWebFilter(source);
  }

  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }

  @Bean
  public CustomFutureValidator customFutureValidator() {
    return new CustomFutureValidator();
  }

  @Bean
  public ValidationContextProvider contextProvider() {
    return new ValidationContextProvider();
  }

  @Bean
  @Qualifier("repository")
  public Retry defaultRepositoryRetry(
      @Value("${repository.retry.max.attempts:3}") long maxAttempts,
      @Value("${repository.retry.duration.millis:500}") long durationInMillis,
      @Value("${repository.retry.jitter.factor:0.7D}")double jitterFactor
  ){
    return Retry.backoff(maxAttempts, Duration.ofMillis(durationInMillis))
        .filter(throwable -> throwable instanceof TimeoutException)
        .jitter(jitterFactor);
  }

  @Bean
  @Qualifier("kafka")
  public Retry defaultKafkaRetry(
      @Value("${kafka.retry.max.attempts:3}") long maxAttempts,
      @Value("${kafka.retry.duration.millis:500}") long durationInMillis,
      @Value("${kafka.retry.jitter.factor:0.7D}")double jitterFactor
  ){
    return Retry.backoff(maxAttempts, Duration.ofMillis(durationInMillis))
        .filter(throwable -> throwable instanceof TimeoutException)
        .jitter(jitterFactor);
  }

  private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthenticationConverter() {
    JwtAuthenticationConverter authConverter = new JwtAuthenticationConverter();
    authConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesExtractor);
    return new ReactiveJwtAuthenticationConverterAdapter(authConverter);
  }

}
