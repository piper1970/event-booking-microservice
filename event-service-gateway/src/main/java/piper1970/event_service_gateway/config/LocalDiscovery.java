package piper1970.event_service_gateway.config;

import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("local_discovery")
@Configuration
@EnableDiscoveryClient
public class LocalDiscovery {

}
