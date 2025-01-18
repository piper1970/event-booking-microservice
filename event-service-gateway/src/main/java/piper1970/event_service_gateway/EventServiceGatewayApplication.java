package piper1970.event_service_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@EnableWebFlux
public class EventServiceGatewayApplication {
  public static void main(String[] args) {
    SpringApplication.run(EventServiceGatewayApplication.class, args);
  }
}
