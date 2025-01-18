package piper1970.eventserviceconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class EventServiceConfigApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventServiceConfigApplication.class, args);
	}

}
