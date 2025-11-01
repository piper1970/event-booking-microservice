package piper1970.bookingservice.config;

import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Local eureka discovery client configuration for select profiles
 */
@Profile({"local_discovery", "compose",  "ssl_local", "ssl_compose"})
@Configuration
@EnableDiscoveryClient
public class LocalDiscovery {
}
