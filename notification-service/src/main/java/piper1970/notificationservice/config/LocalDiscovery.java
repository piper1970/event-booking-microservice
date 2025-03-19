package piper1970.notificationservice.config;

import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Profile("local_discovery")
@Configuration
@EnableDiscoveryClient
@Order(2)
public class LocalDiscovery {
}
