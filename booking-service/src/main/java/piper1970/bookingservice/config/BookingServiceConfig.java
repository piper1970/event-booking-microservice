package piper1970.bookingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CorsSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import piper1970.bookingservice.config.extractors.GrantedAuthoritiesExtractor;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class BookingServiceConfig {

  private final GrantedAuthoritiesExtractor grantedAuthoritiesExtractor;

  public BookingServiceConfig(
      GrantedAuthoritiesExtractor grantedAuthoritiesExtractor) {
    this.grantedAuthoritiesExtractor = grantedAuthoritiesExtractor;
  }

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http.csrf(CsrfSpec::disable);
    http.cors(CorsSpec::disable);
    http.authorizeExchange(exchange -> {
      exchange
          .pathMatchers(HttpMethod.GET, "/actuator/**").permitAll()
          .pathMatchers(HttpMethod.OPTIONS, "*").permitAll()
          .anyExchange()
          .authenticated();
    });

    http.oauth2ResourceServer(oauth2 -> {
      oauth2.jwt(jwt -> {
        jwt.jwtAuthenticationConverter(grantedAuthenticationConverter());
      });
    });

    return http.build();
  }

  Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthenticationConverter() {
    JwtAuthenticationConverter authConverter = new JwtAuthenticationConverter();
    authConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesExtractor);
    return new ReactiveJwtAuthenticationConverterAdapter(authConverter);
  }
}
