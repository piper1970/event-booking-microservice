package piper1970.eventservice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.domain.EventStatus;
import piper1970.eventservice.repository.EventRepository;

@SpringBootApplication
@EnableR2dbcRepositories
@RequiredArgsConstructor
@Slf4j
public class EventServiceApplication {

  private final EventRepository eventRepository;

  public static void main(String[] args) {
    SpringApplication.run(EventServiceApplication.class, args);
  }

  @Bean
  public CommandLineRunner commandLineRunner() {
    return args -> eventRepository.saveAll(
        List.of(Event.builder()
                .title("Event1")
                .description("Description1")
                .location("Location1")
                .eventDateTime(LocalDateTime.now().plusDays(2))
                .cost(new BigDecimal("100.00"))
                .availableBookings(100)
                .eventStatus(EventStatus.AWAITING)
                .build(), Event.builder()
                .title("Event2")
                .description("Description2")
                .location("Location2")
                .eventDateTime(LocalDateTime.now().plusDays(4))
                .cost(new BigDecimal("150.00"))
                .availableBookings(100)
                .eventStatus(EventStatus.IN_PROGRESS)
                .build(),
            Event.builder()
                .title("Event3")
                .description("Description3")
                .location("Location3")
                .eventDateTime(LocalDateTime.now().plusDays(6))
                .cost(new BigDecimal("200.00"))
                .availableBookings(100)
                .eventStatus(EventStatus.COMPLETED)
                .build())
    ).subscribe(event -> log.info(event.toString()));
  }

}
