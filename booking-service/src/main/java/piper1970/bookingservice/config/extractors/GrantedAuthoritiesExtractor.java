package piper1970.bookingservice.config.extractors;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class GrantedAuthoritiesExtractor implements Converter<Jwt, Collection<GrantedAuthority>> {

  private final String clientId;

  public GrantedAuthoritiesExtractor(@Value("${oauth2.client.id}") String clientId) {
    this.clientId = clientId;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {

    return Optional.ofNullable((Map<String, Object>) jwt.getClaim("resource_access"))
        .map(map -> (Map<String,List<String>>) map.get(clientId))
        .map(map -> map.get("roles"))
        .stream()
        .flatMap(List::stream)
        .map(SimpleGrantedAuthority::new)
        .map(GrantedAuthority.class::cast)
        .toList();
  }
}
