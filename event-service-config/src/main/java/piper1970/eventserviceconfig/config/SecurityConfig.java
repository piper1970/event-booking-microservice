package piper1970.eventserviceconfig.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;


@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // disable csrf on encryption & decryption paths
    // these paths used to dynamically create and decrypt passwords via api.
    http.csrf(csrf -> csrf.ignoringRequestMatchers(
            "/encrypt/**", "/decrypt/**"
        ))
        .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
        .formLogin(Customizer.withDefaults())
        .httpBasic(Customizer.withDefaults());

    return http.build();
  }
}
