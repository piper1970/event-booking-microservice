package piper1970.bookingservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@EnableR2dbcRepositories
@EnableWebFlux
@Slf4j
public class BookingServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(BookingServiceApplication.class, args);
  }
}
