package piper1970.bookingservice.config;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import static org.springframework.security.config.Customizer.withDefaults;
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
import piper1970.eventservice.common.events.EventDtoToStatusMapper;
import piper1970.eventservice.common.oauth2.extractors.GrantedAuthoritiesExtractor;
import piper1970.eventservice.common.validation.validators.CustomFutureValidator;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class BookingServiceConfig {

  private final GrantedAuthoritiesExtractor grantedAuthoritiesExtractor;


  public BookingServiceConfig(
      @Value("${oauth2.client.id}") String clientId) {
    this.grantedAuthoritiesExtractor = new GrantedAuthoritiesExtractor(clientId);
  }

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http.csrf(CsrfSpec::disable);
    http.cors(withDefaults());
    http.authorizeExchange(exchange -> {
      exchange
          .pathMatchers(HttpMethod.GET, "/actuator/**").permitAll()
          .pathMatchers(HttpMethod.OPTIONS, "*").permitAll()
          .anyExchange()
          .authenticated();
    });

    http.oauth2ResourceServer(oauth2 ->
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
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return new CorsWebFilter(source);
  }

  @Bean
  public EventDtoToStatusMapper eventToStatusHandler() {
    return new EventDtoToStatusMapper(clock());
  }

  @Bean
  public Clock clock(){
    return Clock.systemDefaultZone();
  }

  @Bean
  public CustomFutureValidator customFutureValidator() {
    return new CustomFutureValidator(clock());
  }

  Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthenticationConverter() {
    JwtAuthenticationConverter authConverter = new JwtAuthenticationConverter();
    authConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesExtractor);
    return new ReactiveJwtAuthenticationConverterAdapter(authConverter);
  }
}
