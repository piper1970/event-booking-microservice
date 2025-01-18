package piper1970.event_service_gateway.config.filters;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import piper1970.event_service_gateway.util.JwtUtil;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

  private final JwtUtil jwtUtil;
  private final ReactiveUserDetailsService reactiveUserDetailsService;

  @Override
  @NonNull
  public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {

    String username = null;
    String jwt = null;
    String auth = exchange.getResponse().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    if(auth != null && auth.startsWith("Bearer ")) {
      jwt = auth.substring(7);
      username = jwtUtil.extractUsername(jwt);
    }

    if(username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      final String finalJwt = jwt;
      return reactiveUserDetailsService.findByUsername(username)
          .filter(userDetails -> jwtUtil.validateToken(finalJwt, userDetails))
          .flatMap(userDetails -> {
            var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            //    var remoteAddress = exchange.getRequest().getRemoteAddress().toString();
//    var session = exchange.getSession().map(WebSession::getId).block();
//    var object = Map.of("remoteAddress", remoteAddress, "sessionId", session);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            return chain.filter(exchange);
          });
      }else{
      return chain.filter(exchange);
    }
  }
}
