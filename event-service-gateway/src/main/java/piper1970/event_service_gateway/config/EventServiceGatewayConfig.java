package piper1970.event_service_gateway.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import piper1970.eventservice.common.oauth2.extractors.GrantedAuthoritiesExtractor;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class EventServiceGatewayConfig {

  private final ReactiveClientRegistrationRepository clientRegistrationRepository;

  private final GrantedAuthoritiesExtractor grantedAuthoritiesExtractor;

  public EventServiceGatewayConfig(
      ReactiveClientRegistrationRepository clientRegistrationRepository,
      @Value("${oauth2.client.id}") String clientId) {
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.grantedAuthoritiesExtractor = new GrantedAuthoritiesExtractor(clientId);
  }

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http.csrf(CsrfSpec::disable);
    http.cors(CorsSpec::disable);
    http.authorizeExchange(exchange -> {
      exchange
          .pathMatchers("/eureka/**").permitAll()
          .pathMatchers(HttpMethod.GET, "/actuator/**").permitAll()
          .pathMatchers("/oauth2/**").permitAll()
          .anyExchange()
          .authenticated();
    });

    http.oauth2Login(withDefaults());

    http.oauth2ResourceServer(oauth2 ->
        oauth2.jwt(jwt ->
            jwt.jwtAuthenticationConverter(grantedAuthenticationConverter())));

    http.logout(spec -> spec.logoutSuccessHandler(oidcLogoutSuccessHandler()));



    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private ServerLogoutSuccessHandler oidcLogoutSuccessHandler() {
    OidcClientInitiatedServerLogoutSuccessHandler logoutSuccessHandler =
        new OidcClientInitiatedServerLogoutSuccessHandler(this.clientRegistrationRepository);
    logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
    return logoutSuccessHandler;
  }

  Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthenticationConverter() {
    JwtAuthenticationConverter authConverter = new JwtAuthenticationConverter();
    authConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesExtractor);
    return new ReactiveJwtAuthenticationConverterAdapter(authConverter);
  }

}

