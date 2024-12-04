package piper1970.bookingservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
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
  private final ObjectMapper objectMapper;

  public static void main(String[] args) {
    SpringApplication.run(BookingServiceApplication.class, args);
  }

  @Bean
  public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
    return args -> {
      bookingRepository.saveAll(
          List.of(Booking.builder()
                  .userId(1)
                  .eventId(1)
                  .eventDateTime(LocalDateTime.now().plusDays(2))
              .build(),
              Booking.builder()
                  .userId(2)
                  .eventId(2)
                  .eventDateTime(LocalDateTime.now().plusDays(3))
                  .build())
          )
          .<String>handle((booking, sink) -> {
            try {
              sink.next(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(booking));
            } catch (JsonProcessingException e) {
              sink.error(new RuntimeException(e));
            }
          }).subscribe(log::info);
    };
  }

}
