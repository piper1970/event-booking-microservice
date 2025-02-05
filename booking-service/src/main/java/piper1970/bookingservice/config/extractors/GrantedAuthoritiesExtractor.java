package piper1970.bookingservice.config.extractors;

import com.nimbusds.jose.shaded.gson.internal.LinkedTreeMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
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
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
    Object client = resourceAccess.get(clientId);

    LinkedTreeMap<String, List<String>> clientRoleMap = (LinkedTreeMap<String, List<String>>) client;

    List<String> clientRoles = new ArrayList<>(clientRoleMap.get("roles"));

    return clientRoles.stream()
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());
  }
}
