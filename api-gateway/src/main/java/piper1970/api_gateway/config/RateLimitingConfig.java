package piper1970.api_gateway.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Configuration for remote-address-based key extraction, used for rate-limiting tracking.
 */
@Configuration
public class RateLimitingConfig {

  @Bean
  public KeyResolver hostAddressResolver() {
    // rate-limit based off host address
    return exchange -> Optional.ofNullable(exchange.getRequest().getRemoteAddress())
        .map(InetSocketAddress::getAddress)
        .map(InetAddress::getHostAddress)
        .map(Mono::just)
        .orElseGet(Mono::empty);
  }
}
