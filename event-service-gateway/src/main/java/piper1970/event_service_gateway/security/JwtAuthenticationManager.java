package piper1970.event_service_gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import piper1970.event_service_gateway.util.JwtUtil;
import reactor.core.publisher.Mono;

//@Component
@RequiredArgsConstructor
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

  private final JwtUtil jwtUtil;

  @Override
  public Mono<Authentication> authenticate(Authentication authentication) {
    return Mono.empty();
//    return Mono.just(authentication)
//        .map(Authentication::getCredentials)
//        .map(String.class::cast)
////        .map(jwtUtil::validateToken)
  }
}
