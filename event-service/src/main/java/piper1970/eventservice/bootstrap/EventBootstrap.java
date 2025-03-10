package piper1970.eventservice.bootstrap;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.repository.EventRepository;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class EventBootstrap implements CommandLineRunner {

  private final EventRepository eventRepository;

  private final Clock clock;

  @Override
  public void run(String... args){
    eventRepository.count()
        .filter(count -> count == 0) // only hydrate empty db
        .flatMapMany(count -> eventRepository.saveAll(
            List.of(
                Event.builder()
                    .facilitator("test-performer")
                    .title("Event1")
                    .description("Description1")
                    .location("Location1")
                    .eventDateTime(LocalDateTime.now(clock).plusDays(2))
                    .durationInMinutes(120)
                    .cost(new BigDecimal("100.00"))
                    .availableBookings(100)
                    .build(),
                Event.builder()
                    .facilitator("test-performer")
                    .title("Event2")
                    .description("Description2")
                    .location("Location2")
                    .eventDateTime(LocalDateTime.now(clock).plusDays(4))
                    .durationInMinutes(60)
                    .cost(new BigDecimal("150.00"))
                    .availableBookings(100)
                    .build(),
                Event.builder()
                    .facilitator("test-performer")
                    .title("Event3")
                    .description("Description3")
                    .location("Location3")
                    .eventDateTime(LocalDateTime.now(clock).plusDays(6))
                    .durationInMinutes(30)
                    .cost(new BigDecimal("200.00"))
                    .availableBookings(100)
                    .build())
        )).subscribe(event -> log.info(event.toString()));
  }
}
