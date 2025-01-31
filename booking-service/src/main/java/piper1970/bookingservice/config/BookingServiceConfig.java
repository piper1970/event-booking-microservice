package piper1970.bookingservice.config;

import static org.springframework.security.config.Customizer.withDefaults;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CorsSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class BookingServiceConfig {

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

    http.oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));

    return http.build();

  }

}
