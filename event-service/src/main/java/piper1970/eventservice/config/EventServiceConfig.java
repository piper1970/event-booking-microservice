package piper1970.eventservice.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CorsSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class EventServiceConfig {


  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http.csrf(CsrfSpec::disable);
    http.cors(CorsSpec::disable);
    http.authorizeExchange(exchange -> {
      exchange
          .pathMatchers(HttpMethod.GET, "/actuator/**").permitAll()
          .pathMatchers(HttpMethod.OPTIONS, "*").permitAll()
          .anyExchange()
          .permitAll();
    });

    return http.build();
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
  GrantedAuthoritiesMapper authoritiesConverter(AuthoritiesConverter realmRolesConverter) {

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
