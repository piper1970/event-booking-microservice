package piper1970.eventservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.NoOpTaskScheduler;
import org.springframework.transaction.reactive.TransactionalOperator;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.repository.EventRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("Event-Service Scheduled Services")
@DataR2dbcTest
@ExtendWith(MockitoExtension.class)
class SchedulingServiceTests {

  private SchedulingService schedulingService;

  @Autowired
  DatabaseClient databaseClient;

  @Autowired
  private EventRepository eventRepository;

  @Mock
  private MessagePostingService messagePostingService;

  @Mock
  private TransactionalOperator transactionalOperator;

  private final Clock clock = Clock.systemUTC();

  @BeforeEach
  void setUp() {

    initializeDatabase();
    initializeTransactionalMock();
    schedulingService = new SchedulingService(eventRepository, messagePostingService,
        transactionalOperator, clock, 1);
  }

  @Test
  @DisplayName("checkForCompletedEvents should properly process completed events, saving updates and posting to Kafka")
  void checkForCompletedEvents_SomeEventsCompleted() {
    initializeMockKafkaMessage();
    // 3 non-complete events, 2 of which should be marked as complete
    var events = List.of(
        Event.builder()
            .facilitator("facilitator-1")
            .title("title-1")
            .description("description-1")
            .location("location-1")
            .availableBookings(10)
            .eventStatus(EventStatus.AWAITING)
            .durationInMinutes(10)
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(30))
            .build(),
        Event.builder()
            .facilitator("facilitator-2")
            .title("title-2")
            .description("description-2")
            .location("location-2")
            .availableBookings(10)
            .eventStatus(EventStatus.IN_PROGRESS)
            .durationInMinutes(60)
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(30))
            .build(),
        Event.builder()
            .facilitator("facilitator-3")
            .title("title-3")
            .description("description-3")
            .location("location-3")
            .availableBookings(10)
            .eventStatus(EventStatus.IN_PROGRESS)
            .durationInMinutes(60)
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(90))
            .build()
    );
    eventRepository.saveAll(events)
        .then()
        .block();

    schedulingService.checkForCompletedEvents(); // blocking event

    eventRepository.findByEventStatusIn(List.of(EventStatus.COMPLETED))
        .as(StepVerifier::create)
        .expectNextCount(2L)
        .verifyComplete();

    verify(messagePostingService, times(2)).postEventCompletedMessage(any(EventCompleted.class));
  }

  @Test
  @DisplayName("checkForCompletedEvents should properly process events when none are completed, skipping saving updates and not posting to Kafka")
  void checkForCompletedEvents_NoEventsCompleted() {
    // 3 non-complete events, all of which should be left alone
    var events = List.of(
        Event.builder()
            .facilitator("facilitator-1")
            .title("title-1")
            .description("description-1")
            .location("location-1")
            .availableBookings(10)
            .eventStatus(EventStatus.AWAITING)
            .durationInMinutes(100)
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(30))
            .build(),
        Event.builder()
            .facilitator("facilitator-2")
            .title("title-2")
            .description("description-2")
            .location("location-2")
            .availableBookings(10)
            .eventStatus(EventStatus.IN_PROGRESS)
            .durationInMinutes(90)
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(60))
            .build(),
        Event.builder()
            .facilitator("facilitator-3")
            .title("title-3")
            .description("description-3")
            .location("location-3")
            .availableBookings(10)
            .eventStatus(EventStatus.IN_PROGRESS)
            .durationInMinutes(120)
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(90))
            .build()
    );
    eventRepository.saveAll(events)
        .then()
        .block();

    schedulingService.checkForCompletedEvents(); // blocking event

    eventRepository.findByEventStatusIn(List.of(EventStatus.COMPLETED))
        .as(StepVerifier::create)
        .expectNextCount(0L)
        .verifyComplete();

    verify(messagePostingService, never()).postEventCompletedMessage(any(EventCompleted.class));
  }

  @Test
  @DisplayName("checkForAwaitingEventsThatHaveStarted should properly process events, updating events to IN_PROGRESS when some have started")
  void checkForAwaitingEventsThatHaveStarted_SomeFound() {
    // 3 awaiting events, 2 of which should be marked as started
    var events = List.of(
        Event.builder()
            .facilitator("facilitator-1")  // started
            .title("title-1")
            .description("description-1")
            .location("location-1")
            .availableBookings(10)
            .eventStatus(EventStatus.AWAITING)
            .durationInMinutes(60)
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(30))
            .build(),
        Event.builder()
            .facilitator("facilitator-2") // not started, but actually completed, does not apply
            .title("title-2")
            .description("description-2")
            .location("location-2")
            .availableBookings(10)
            .eventStatus(EventStatus.AWAITING)
            .durationInMinutes(30)
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(60))
            .build(),
        Event.builder()
            .facilitator("facilitator-3") // started
            .title("title-3")
            .description("description-3")
            .location("location-3")
            .availableBookings(10)
            .eventStatus(EventStatus.AWAITING)
            .durationInMinutes(35)
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(30))
            .build()
    );
    eventRepository.saveAll(events)
        .then()
        .block();

    schedulingService.checkForAwaitingEventsThatHaveStarted(); // blocking event

    eventRepository.findByEventStatusIn(List.of(EventStatus.IN_PROGRESS))
        .as(StepVerifier::create)
        .expectNextCount(2L)
        .verifyComplete();
  }

  @Test
  @DisplayName("checkForAwaitingEventsThatHaveStarted should properly process events, avoiding any updates to events when none have started without completion")
  void checkForAwaitingEventsThatHaveStarted_NoneFound() {
    // 2 still awaiting, one completed, none should be marked as started
    var events = List.of(
        Event.builder()
            .facilitator("facilitator-1")  // started
            .title("title-1")
            .description("description-1")
            .location("location-1")
            .availableBookings(10)
            .eventStatus(EventStatus.AWAITING)
            .durationInMinutes(60)
            .eventDateTime(LocalDateTime.now(clock).plusMinutes(10))
            .build(),
        Event.builder()
            .facilitator("facilitator-2") // not started, but actually completed, does not apply
            .title("title-2")
            .description("description-2")
            .location("location-2")
            .availableBookings(10)
            .eventStatus(EventStatus.AWAITING)
            .durationInMinutes(30)
            .eventDateTime(LocalDateTime.now(clock).minusMinutes(60))
            .build(),
        Event.builder()
            .facilitator("facilitator-3") // started
            .title("title-3")
            .description("description-3")
            .location("location-3")
            .availableBookings(10)
            .eventStatus(EventStatus.AWAITING)
            .durationInMinutes(35)
            .eventDateTime(LocalDateTime.now(clock).plusMinutes(30))
            .build()
    );
    eventRepository.saveAll(events)
        .then()
        .block();

    schedulingService.checkForAwaitingEventsThatHaveStarted(); // blocking event

    eventRepository.findByEventStatusIn(List.of(EventStatus.IN_PROGRESS))
        .as(StepVerifier::create)
        .expectNextCount(0L)
        .verifyComplete();
  }

  private void initializeMockKafkaMessage() {
    when(messagePostingService.postEventCompletedMessage(any(EventCompleted.class)))
        .thenReturn(Mono.empty());
  }

  private void initializeTransactionalMock() {
    when(transactionalOperator.transactional(ArgumentMatchers.<Flux<Event>>any()))
        .thenAnswer(i -> i.getArgument(0));
  }

  private void initializeDatabase() {
    var statements = List.of(
        "DROP TABLE IF EXISTS event_service.events;",
        "DROP SCHEMA IF EXISTS event_service;",
        "CREATE SCHEMA event_service;",
        """
              CREATE TABLE IF NOT EXISTS event_service.events
              (
                id                  int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                version             integer,
                facilitator         varchar(60)  NOT NULL,
                title               varchar(255) NOT NULL,
                description         varchar(255),
                location            varchar(255) NOT NULL,
                event_date_time     timestamp    NOT NULL,
                duration_in_minutes int          NOT NULL,
                available_bookings  smallint     NOT NULL,
                event_status        varchar(30)  NOT NULL
              );
            """);

    statements.forEach(stmt -> databaseClient.sql(stmt)
        .fetch()
        .rowsUpdated()
        .as(StepVerifier::create)
        .expectNextCount(1)
        .verifyComplete());
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    TaskScheduler taskScheduler() {
      // ignore scheduled runs
      return new NoOpTaskScheduler();
    }
  }
}