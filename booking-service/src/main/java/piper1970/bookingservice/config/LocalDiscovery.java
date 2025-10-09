package piper1970.bookingservice.config;

import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile({"local_discovery", "compose",  "ssl_local"})
@Configuration
@EnableDiscoveryClient
public class LocalDiscovery {
}
