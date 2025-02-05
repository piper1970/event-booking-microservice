package piper1970.eventservice.common.oauth2.extractors;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extractor for converting OAuth2/OIDC realm and client roles into GrantedAuthority components
 */
public class GrantedAuthoritiesExtractor implements Converter<Jwt, Collection<GrantedAuthority>> {

  private final String clientId;

  public GrantedAuthoritiesExtractor(@NonNull String clientId) {
    this.clientId = clientId;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {

    // capture the client roles
    var clientCredsStream =  Optional.ofNullable((Map<String, Object>) jwt.getClaim("resource_access"))
        .map(map -> (Map<String,List<String>>) map.get(clientId))
        .map(map -> map.get("roles"))
        .stream()
        .flatMap(List::stream)
        .map(SimpleGrantedAuthority::new)
        .map(GrantedAuthority.class::cast);

    // capture the realm roles
    var realmCredsStream =  Optional.ofNullable((Map<String, Object>) jwt.getClaim("realm_access"))
        .map(map -> (List<String>) map.get("roles"))
        .stream()
        .flatMap(List::stream)
        .map(SimpleGrantedAuthority::new)
        .map(GrantedAuthority.class::cast);

    // return set of both role groups
    return Stream.concat(clientCredsStream, realmCredsStream)
        .collect(Collectors.toSet());
  }
}
