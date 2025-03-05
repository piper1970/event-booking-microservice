package piper1970.eventservice.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.domain.Event;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
class EventRepositoryTest {

  @Autowired
  EventRepository eventRepository;

  @Autowired
  DatabaseClient databaseClient;

  @BeforeEach
  void init() {

    var statements = List.of("DROP TABLE IF EXISTS event_service.events;",
        "DROP SCHEMA IF EXISTS event_service;",
        "CREATE SCHEMA event_service",
        """
                CREATE TABLE IF NOT EXISTS event_service.events
                (
                    id                 int GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    facilitator        varchar(60)   NOT NULL,
                    title              varchar(255)  NOT NULL,
                    description        varchar(255),
                    location           varchar(255)  NOT NULL,
                    event_date_time    timestamp     NOT NULL,
                    created_date_time  timestamp,
                    updated_date_time  timestamp,
                    cost               numeric(6, 2) NOT NULL,
                    available_bookings smallint      NOT NULL,
                    event_status       varchar(30)   NOT NULL
                );
            """);

    statements.forEach(stmt -> {
      databaseClient.sql(stmt)
          .fetch()
          .rowsUpdated()
          .as(StepVerifier::create)
          .expectNextCount(1)
          .verifyComplete();
    });
  }

  @Test
  void findAll() {
    var event1 = Event.builder()
        .facilitator("test_facilitator_1")
        .title("test_title_1")
        .description("test_description_1")
        .location("test_location_1")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().plusDays(3))
        .eventStatus(EventStatus.AWAITING)
        .build();
    var event2 = Event.builder()
        .facilitator("test_facilitator_2")
        .title("test_title_2")
        .description("test_description_2")
        .location("test_location_2")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().plusDays(2))
        .eventStatus(EventStatus.IN_PROGRESS)
        .build();
    var event3 = Event.builder()
        .facilitator("test_facilitator_3")
        .title("test_title_3")
        .description("test_description_3")
        .location("test_location_3")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().minusDays(1))
        .eventStatus(EventStatus.COMPLETED)
        .build();

    insertEvents(event1, event2, event3);

    eventRepository.findAll()
        .as(StepVerifier::create)
        .expectNextCount(3)
        .verifyComplete();
  }

  @Test
  void findById() {
    var event1 = Event.builder()
        .facilitator("test_facilitator_1")
        .title("test_title_1")
        .description("test_description_1")
        .location("test_location_1")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().plusDays(3))
        .eventStatus(EventStatus.AWAITING)
        .build();
    var event2 = Event.builder()
        .facilitator("test_facilitator_2")
        .title("test_title_2")
        .description("test_description_2")
        .location("test_location_2")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().plusDays(2))
        .eventStatus(EventStatus.IN_PROGRESS)
        .build();
    var event3 = Event.builder()
        .facilitator("test_facilitator_3")
        .title("test_title_3")
        .description("test_description_3")
        .location("test_location_3")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().minusDays(1))
        .eventStatus(EventStatus.COMPLETED)
        .build();

    insertEvents(event1, event2, event3);

    var expected = eventRepository.findAll()
        .filter(it -> it.getFacilitator().equals("test_facilitator_1"))
        .switchIfEmpty(Mono.error(new RuntimeException("failure to access event for test")))
        .blockFirst();

    assertNotNull(expected);

    eventRepository.findById(expected.getId())
        .as(StepVerifier::create)
        .expectNext(expected)
        .verifyComplete();
  }

  @Test
  void count() {
    var event1 = Event.builder()
        .facilitator("test_facilitator_1")
        .title("test_title_1")
        .description("test_description_1")
        .location("test_location_1")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().plusDays(3))
        .eventStatus(EventStatus.AWAITING)
        .build();
    var event2 = Event.builder()
        .facilitator("test_facilitator_2")
        .title("test_title_2")
        .description("test_description_2")
        .location("test_location_2")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().plusDays(2))
        .eventStatus(EventStatus.IN_PROGRESS)
        .build();
    var event3 = Event.builder()
        .facilitator("test_facilitator_3")
        .title("test_title_3")
        .description("test_description_3")
        .location("test_location_3")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().minusDays(1))
        .eventStatus(EventStatus.COMPLETED)
        .build();

    insertEvents(event1, event2, event3);

    eventRepository.count()
        .as(StepVerifier::create)
        .expectNext(3L)
        .verifyComplete();
  }

  @Test
  void save() {
    var event1 = Event.builder()
        .facilitator("test_facilitator_1")
        .title("test_title_1")
        .description("test_description_1")
        .location("test_location_1")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().plusDays(3))
        .eventStatus(EventStatus.AWAITING)
        .build();
    var event2 = Event.builder()
        .facilitator("test_facilitator_2")
        .title("test_title_2")
        .description("test_description_2")
        .location("test_location_2")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().plusDays(2))
        .eventStatus(EventStatus.IN_PROGRESS)
        .build();
    var event3 = Event.builder()
        .facilitator("test_facilitator_3")
        .title("test_title_3")
        .description("test_description_3")
        .location("test_location_3")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().minusDays(1))
        .eventStatus(EventStatus.COMPLETED)
        .build();

    insertEvents(event1, event2, event3);

    eventRepository.count()
        .as(StepVerifier::create)
        .expectNext(3L)
        .verifyComplete();

    var newEvent = Event.builder()
        .facilitator("test_facilitator_4")
        .title("test_title_4")
        .description("test_description_4")
        .location("test_location_4")
        .cost(BigDecimal.TEN)
        .availableBookings(10)
        .eventDateTime(LocalDateTime.now().plusDays(1))
        .eventStatus(EventStatus.AWAITING)
        .build();

    eventRepository.save(newEvent)
        .as(StepVerifier::create)
        .expectNext(newEvent)
        .verifyComplete();

    eventRepository.count()
        .as(StepVerifier::create)
        .expectNext(4L)
        .verifyComplete();
  }

  private void insertEvents(Event... events) {
    eventRepository.saveAll(List.of(events))
        .as(StepVerifier::create)
        .expectNextCount(events.length)
        .verifyComplete();
  }

}