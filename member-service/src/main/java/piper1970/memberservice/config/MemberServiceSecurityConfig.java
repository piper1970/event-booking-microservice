package piper1970.memberservice.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CorsSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.FormLoginSpec;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class MemberServiceSecurityConfig {

  @Value("${root.userdetails.username}")
  private String username;

  @Value("${root.userdetails.password}")
  private String password;

  @Value("${root.userdetails.roles}")
  private String roles;

  @Bean
  public MapReactiveUserDetailsService userDetailsService() {
    var roleList = roles.split(",");
    var encodedPassword = passwordEncoder().encode(password);
    UserDetails user = User.builder().username(username)
        .password(encodedPassword)
        .roles(roleList).build();
    return new MapReactiveUserDetailsService(user);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    // TODO: deal with CSRF/CORS more properly
    http.csrf(CsrfSpec::disable)
        .cors(CorsSpec::disable)
        .authorizeExchange(exchanges ->
            exchanges
                .pathMatchers(HttpMethod.GET, "/actuator/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/members").hasRole("ADMIN")
                .pathMatchers(HttpMethod.PUT, "/api/v1/members/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.DELETE, "/api/v1/members/**").hasRole("ADMIN")
                .anyExchange()
                .authenticated())
        .httpBasic(withDefaults())
        .formLogin(FormLoginSpec::disable);

    return http.build();
  }

}
