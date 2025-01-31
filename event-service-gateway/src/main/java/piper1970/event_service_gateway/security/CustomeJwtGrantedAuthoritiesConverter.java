package piper1970.event_service_gateway.security;

import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class CustomeJwtGrantedAuthoritiesConverter implements
    Converter<Jwt, Flux<GrantedAuthority>> {

  @Override
  @SuppressWarnings("unchecked")
  public Flux<GrantedAuthority> convert(Jwt source) {
    var realm_access = (Map<String, List<String>>) source.getClaim("realm_access");
    var fluxStream = realm_access.get("roles").stream()
        .filter(role -> role.startsWith("ROLE_"))
        .map(SimpleGrantedAuthority::new)
        .map(GrantedAuthority.class::cast);
    return Flux.fromStream(fluxStream);

  }
}
