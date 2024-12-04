package piper1970.bookingservice;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.repository.BookingRepository;

@SpringBootApplication
@EnableR2dbcRepositories
@RequiredArgsConstructor
@Slf4j
public class BookingServiceApplication {

  private final BookingRepository bookingRepository;

  public static void main(String[] args) {
    SpringApplication.run(BookingServiceApplication.class, args);
  }

  @Bean
  public CommandLineRunner commandLineRunner() {
    return args -> bookingRepository.saveAll(
        List.of(Booking.builder()
                .event("Event1")
                .username("User1")
                .eventDateTime(LocalDateTime.now().plusDays(2))
            .build(),
            Booking.builder()
                .event("Event2")
                .username("User2")
                .eventDateTime(LocalDateTime.now().plusDays(3))
                .build())
        ).subscribe(booking -> log.info(booking.toString()));
  }

}
