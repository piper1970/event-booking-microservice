package piper1970.api_gateway.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import piper1970.eventservice.common.oauth2.extractors.GrantedAuthoritiesExtractor;
import reactor.core.publisher.Mono;

/**
 * Primary Configuration for ApiGateway.
 * <p/>
 * Configures Security, PasswordEncoding, and Custom CORS behavior.
 * <p/>
 * Security is set up for OAuth2 client and resource server traits.
 */
@Configuration
@EnableWebFluxSecurity
public class ApiGatewayConfig {

  @Value("${csrf.enabled:false}")
  private boolean csrfEnabled;

  @Value("${cors.allowedOrigin:*}")
  private String allowedOrigin;

  private final ReactiveClientRegistrationRepository clientRegistrationRepository;
  private final GrantedAuthoritiesExtractor grantedAuthoritiesExtractor;

  public ApiGatewayConfig(
      ReactiveClientRegistrationRepository clientRegistrationRepository,
      @Value("${oauth2.client.id}") String clientId) {
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.grantedAuthoritiesExtractor = new GrantedAuthoritiesExtractor(clientId);
  }

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

    // allow for opting out of CSRF protection
    http.csrf(csrf -> {
      if(!csrfEnabled) {
        csrf.disable();
      }
    });

    http.cors(withDefaults());

    // allow eureka, actuator and swagger/openapi to pass through. Others require authentication
    http.authorizeExchange(exchange -> {
      exchange
          .pathMatchers("/eureka/**").permitAll()
          // passthrough on actuator, openapi/swagger and open/api swagger for events and bookings
          .pathMatchers(HttpMethod.GET, "/actuator/**", "/api/notifications/confirm/**",
              "/v3/api-docs", "/v3/api-docs/**", "/v3/swagger-ui", "/v3/swagger-ui/**").permitAll()
          .pathMatchers("/oauth2/**").permitAll()
          .anyExchange()
          .authenticated();
    });

    http.oauth2Login(withDefaults());

    // setup OAuth2 resource  to use custom grantedAuthenticationConverter() for jwt conversion
    http.oauth2ResourceServer(oauth2 ->
        oauth2.jwt(jwt ->
            jwt.jwtAuthenticationConverter(grantedAuthenticationConverter())));

    // handle logout-success behavior with oidcLogoutSuccessHandler() call
    http.logout(spec -> spec.logoutSuccessHandler(oidcLogoutSuccessHandler()));

    return http.build();
  }

  @Bean
  public CorsWebFilter corsWebFilter() {
    // Provides custom CORS behaviour, allowing Authorization, Content-Type headers
    // and GET, POST, PUT, DELETE, OPTIONS, and PATCH methods
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.addAllowedOrigin(allowedOrigin);
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return new CorsWebFilter(source);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    // defaults to BCrypt, but allows for custom encryption with opcode prefix, such as {bcrypt} or {noop}..
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  private ServerLogoutSuccessHandler oidcLogoutSuccessHandler() {
    // sets oidcLogout handler to go to baseUrl after successful logout
    OidcClientInitiatedServerLogoutSuccessHandler logoutSuccessHandler =
        new OidcClientInitiatedServerLogoutSuccessHandler(this.clientRegistrationRepository);
    logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
    return logoutSuccessHandler;
  }

  /**
   * Converter factory for adding custom JwtGrantedAuthoritiesConverter to converter.
   *
   * @return ReactiveJwtAuthenticationConverterAdapter seeded with GrantedAuthoritiesExtractor component
   * @see GrantedAuthoritiesExtractor
   * @see JwtAuthenticationConverter
   * @see ReactiveJwtAuthenticationConverterAdapter
   */
  private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthenticationConverter() {
    JwtAuthenticationConverter authConverter = new JwtAuthenticationConverter();
    authConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesExtractor);
    return new ReactiveJwtAuthenticationConverterAdapter(authConverter);
  }
}

