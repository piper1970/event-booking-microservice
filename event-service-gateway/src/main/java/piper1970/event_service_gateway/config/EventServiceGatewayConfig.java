package piper1970.event_service_gateway.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CorsSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import piper1970.event_service_gateway.security.CustomeJwtGrantedAuthoritiesConverter;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class EventServiceGatewayConfig {

//  @Value("${keycloak.client.id}")
//  String clientId;

  private final CustomeJwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter;

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
      ReactiveClientRegistrationRepository clientRegistrationRepository) {
    http.csrf(CsrfSpec::disable);
    http.cors(CorsSpec::disable);
    http.authorizeExchange(exchange -> {
      exchange
          .pathMatchers(HttpMethod.GET, "/actuator/**").permitAll()
          .pathMatchers(HttpMethod.GET, "/login", "/signup").permitAll()
          .anyExchange()
          .authenticated();
    });

    http.oauth2Login(withDefaults());
//    http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
//        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    http.oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));

//    http.logout(logout -> {
//      var logoutSuccessHandler = new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
//      logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/");
//      logout.logoutSuccessHandler(logoutSuccessHandler);
//    });

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public Converter<Jwt, ? extends Mono<? extends AbstractAuthenticationToken>> jwtAuthenticationConverter() {
    var converter = new ReactiveJwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
    return converter;
  }

  interface AuthoritiesConverter extends
      Converter<Map<String, Object>, Collection<GrantedAuthority>> {}

  @Bean
  @SuppressWarnings("unchecked")
  AuthoritiesConverter realmRolesConverter() {
    return claims -> {
      var roles = Optional.ofNullable((Map<String, Object>)claims.get("realm_access"))
          .flatMap(map -> Optional.ofNullable((List<String>)map.get("roles")));

      return roles.stream()
          .flatMap(Collection::stream)
          .map(SimpleGrantedAuthority::new)
          .map(GrantedAuthority.class::cast)
          .toList();
    };
  }

  @Bean
  GrantedAuthoritiesMapper authenticationConverter(AuthoritiesConverter realmRolesConverter) {

    return authorities ->
      authorities.stream()
          .filter(OidcUserAuthority.class::isInstance)
          .map(OidcUserAuthority.class::cast)
          .map(OidcUserAuthority::getIdToken)
          .map(OidcIdToken::getClaims)
          .map(realmRolesConverter::convert)
          .filter(Objects::nonNull)
          .flatMap(Collection::stream)
          .collect(Collectors.toSet());
  }
}

