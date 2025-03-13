package piper1970.paymentservice.config;

import static org.springframework.security.config.Customizer.withDefaults;

import jakarta.ws.rs.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class PaymentServiceConfig {

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http.csrf(CsrfSpec::disable)
        .cors(withDefaults())
        .authorizeExchange(exchange -> {
          exchange
              .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "*").permitAll()
              .pathMatchers(HttpMethod.OPTIONS, "actuator/**").permitAll()
              .anyExchange().denyAll();
        });
    return http.build();
  }

}
