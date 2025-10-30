package piper1970.notificationservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("Notification-Service Scheduled Services")
@DataR2dbcTest
@ExtendWith(MockitoExtension.class)
class SchedulingServiceTests {

  private SchedulingService schedulingService;

  @Autowired
  private BookingConfirmationRepository bookingConfirmationRepository;

  @Autowired
  DatabaseClient databaseClient;

  @Mock
  private MessagePostingService messagePostingService;

  @Mock
  private Counter expirationCounter;

  @Mock
  private TransactionalOperator transactionalOperator;

  private final Clock clock = Clock.systemUTC();
  private int staleDataDurationInHours = 6;

  @BeforeEach
  void setUp() {
    initializeDatabase();
    staleDataDurationInHours = 6;
    schedulingService = new SchedulingService(bookingConfirmationRepository,
        messagePostingService, transactionalOperator, expirationCounter, clock, staleDataDurationInHours, 10);
  }

  @Test
  @DisplayName("checkForExpiredConfirmations should properly process expired confirmations, saving updates and posting to Kafka")
  void checkForExpiredConfirmations_SomeFound() {
    initializeTransactionalMock();
    initializeMockKafkaMessage();

    var confirmations = List.of(
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // skipped, since already confirmed
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-1")
            .bookingEmail("user-1@test.com")
            .confirmationDateTime(LocalDateTime.now(clock))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.CONFIRMED)
            .build(),
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // expired
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-2")
            .bookingEmail("user-2@test.com")
            .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(61))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
            .build(),
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // expired
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-3")
            .bookingEmail("user-3@test.com")
            .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(65))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
            .build(),
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // not-yet expired
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-1")
            .bookingEmail("user-1@test.com")
            .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(20))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
            .build()
    );

    bookingConfirmationRepository.saveAll(confirmations)
        .then()
        .block();

    schedulingService.checkForExpiredConfirmations(); // blocking event

    bookingConfirmationRepository.findByConfirmationStatus(ConfirmationStatus.EXPIRED)
        .as(StepVerifier::create)
        .expectNextCount(2)
        .verifyComplete();

    verify(messagePostingService, times(2)).postBookingExpiredMessage(any(BookingExpired.class));
  }


  @Test
  @DisplayName("checkForExpiredConfirmations should behave properly when no confirmations have expired, skipping saving updates and not posting to Kafka")
  void checkForExpiredConfirmations_NoneFound() {
    initializeTransactionalMock();

    var confirmations = List.of(
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // skipped, since already confirmed
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-1")
            .bookingEmail("user-1@test.com")
            .confirmationDateTime(LocalDateTime.now(clock))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.CONFIRMED)
            .build(),
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // not-yet expired
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-2")
            .bookingEmail("user-2@test.com")
            .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(59))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
            .build(),
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // not-yet expired
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-3")
            .bookingEmail("user-3@test.com")
            .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(55))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
            .build(),
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // not-yet expired
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-1")
            .bookingEmail("user-1@test.com")
            .confirmationDateTime(LocalDateTime.now(clock).minusMinutes(20))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
            .build()
    );

    bookingConfirmationRepository.saveAll(confirmations)
        .then()
        .block();

    schedulingService.checkForExpiredConfirmations(); // blocking event

    bookingConfirmationRepository.findByConfirmationStatus(ConfirmationStatus.EXPIRED)
        .as(StepVerifier::create)
        .expectNextCount(0)
        .verifyComplete();

    verify(messagePostingService, never()).postBookingExpiredMessage(any(BookingExpired.class));
  }

  @Test
  @DisplayName("clearStaleData should properly delete events past the stale data mark when found")
  void clearStaleData_SomeFound() {
    var confirmations = List.of(
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // stale data - should be removed
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-1")
            .bookingEmail("user-1@test.com")
            .confirmationDateTime(LocalDateTime.now(clock)
                .minusHours(staleDataDurationInHours).minusSeconds(2))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.CONFIRMED)
            .build(),
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // not-yet stale (2 minutes left)
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-2")
            .bookingEmail("user-2@test.com")
            .confirmationDateTime(LocalDateTime.now(clock).minusHours(staleDataDurationInHours).plusMinutes(2))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
            .build(),
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) //  stale....
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-3")
            .bookingEmail("user-3@test.com")
            .confirmationDateTime(LocalDateTime.now(clock).minusHours(staleDataDurationInHours))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.EXPIRED)
            .build()
    );

    bookingConfirmationRepository.saveAll(confirmations)
        .then()
        .block();

    schedulingService.clearStaleData(); // blocking event

    bookingConfirmationRepository.findAll()
        .as(StepVerifier::create)
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  @DisplayName("clearStaleData should leave repository alone if no stale data was found past the stale data mark")
  void clearStaleData_NoneFound() {
    var confirmations = List.of(
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // not-stale yet (2 seconds left)
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-1")
            .bookingEmail("user-1@test.com")
            .confirmationDateTime(LocalDateTime.now(clock)
                .minusHours(staleDataDurationInHours).plusHours(1)) // 1 hour after staleSpot
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.CONFIRMED)
            .build(),
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // not-yet stale (2 minutes left)
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-2")
            .bookingEmail("user-2@test.com")
            .confirmationDateTime(LocalDateTime.now(clock).minusHours(staleDataDurationInHours).plusMinutes(2))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
            .build(),
        BookingConfirmation.builder()
            .confirmationString(UUID.randomUUID()) // not-quite stale....
            .eventId(1)
            .bookingId(1)
            .bookingUser("user-3")
            .bookingEmail("user-3@test.com")
            .confirmationDateTime(LocalDateTime.now(clock).minusHours(staleDataDurationInHours)
                .plusSeconds(2))
            .durationInMinutes(60)
            .confirmationStatus(ConfirmationStatus.EXPIRED)
            .build()
    );

    bookingConfirmationRepository.saveAll(confirmations)
        .then()
        .block();

    schedulingService.clearStaleData(); // blocking event

    bookingConfirmationRepository.findAll()
        .as(StepVerifier::create)
        .expectNextCount(confirmations.size())
        .verifyComplete();
  }

  /**
   * Helper method to set up mock behavior for messagePostingService.  Not all tests require this mock behavior
   */
  private void initializeMockKafkaMessage() {
    when(messagePostingService.postBookingExpiredMessage(any(BookingExpired.class)))
        .thenReturn(Mono.empty());
  }

  /**
   * Helper method to set up transactionalOperator moc behavior when needed. Not all tests need this mock behavior
   */
  private void initializeTransactionalMock() {
    when(transactionalOperator.transactional(ArgumentMatchers.<Flux<BookingConfirmation>>any()))
        .thenAnswer(i -> i.getArgument(0));
  }

  /**
   * Helper method to initialize database before each test
   */
  private void initializeDatabase() {
    var statements = List.of(
        "DROP TABLE IF EXISTS event_service.booking_confirmations;",
        "DROP SCHEMA IF EXISTS event_service;",
        "CREATE SCHEMA event_service;",
        """
            CREATE TABLE event_service.booking_confirmations
            (
              id                     int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
              version                int,
              booking_id             int          NOT NULL,
              event_id               int          NOT NULL,
              confirmation_string    UUID         NOT NULL,
              booking_user           varchar(255) NOT NULL,
              booking_email          varchar(255) NOT NULL,
              confirmation_date_time timestamp    NOT NULL,
              duration_in_minutes    int          NOT NULL,
              confirmation_status    varchar(30)  NOT NULL
            );
            """
    );

    statements.forEach(stmt -> databaseClient.sql(stmt)
        .fetch()
        .rowsUpdated()
        .as(StepVerifier::create)
        .expectNextCount(1)
        .verifyComplete());
  }

  /**
   * Helper configuration set up to disable default scheduler behavior during tests.
   */
  @TestConfiguration
  static class TestConfig {

    /**
     * Override TaskScheduler bean with no-op version for tests
     */
    @Bean
    TaskScheduler taskScheduler() {
      // ignore scheduled runs
      return new NoOpTaskScheduler();
    }
  }
}