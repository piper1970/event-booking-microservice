package piper1970.eventservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@EnableR2dbcRepositories
@EnableWebFlux
@Slf4j
public class EventServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(EventServiceApplication.class, args);
  }
}
