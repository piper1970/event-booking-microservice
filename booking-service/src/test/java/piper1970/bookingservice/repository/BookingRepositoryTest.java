package piper1970.bookingservice.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import reactor.test.StepVerifier;

@DataR2dbcTest
@DisplayName("Booking repository")
class BookingRepositoryTest {

  @Autowired
  BookingRepository bookingRepository;

  @InjectMocks
  Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

  @Autowired
  DatabaseClient databaseClient;

  final Instant clockInstant = Instant.now();
  final ZoneId clockZone = ZoneId.systemDefault();

  @BeforeEach
  void setUp() {



    var statements = List.of("DROP TABLE IF EXISTS event_service.bookings;",
        "DROP SCHEMA IF EXISTS event_service;",
        "CREATE SCHEMA event_service;",
        """
            CREATE TABLE event_service.bookings
                (
                    id                int GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    event_id          int     NOT NULL,
                    username          varchar(60) NOT NULL,
                    email             varchar(255) NOT NULL,
                    event_date_time   timestamp   NOT NULL,
                    booking_status    varchar(30) NOT NULL,
                    created_date_time timestamp,
                    updated_date_time timestamp
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
  @DisplayName("should be able to find bookings given a username")
  void findByUsername() {
    var testUsername = "test_username";
    var booking1 = Booking.builder()
        .username(testUsername)
        .email(testUsername  + "@test.com")
        .eventId(1)
        .eventDateTime(LocalDateTime.now(clock))
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking2 = Booking.builder()
        .username(testUsername + "somethingElse")
        .email(testUsername + "somethingElse@test.com")
        .eventId(2)
        .eventDateTime(LocalDateTime.now(clock))
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking3 = Booking.builder()
        .username(testUsername)
        .email(testUsername  + "@test.com")
        .eventId(3)
        .eventDateTime(LocalDateTime.now(clock))
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    insertBookings(booking1, booking2, booking3);

    bookingRepository.findByUsername(testUsername)
        .as(StepVerifier::create)
        .expectNext(booking1, booking3)
        .verifyComplete();
  }

  @Test
  @DisplayName("should be able to find a booking by id and username")
  void findByIdAndUsername() {
    var testUsername = "test_username";
    var booking1 = Booking.builder()
        .username(testUsername)
        .email(testUsername  + "@test.com")
        .eventId(1)
        .eventDateTime(LocalDateTime.now(clock))
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking2 = Booking.builder()
        .username(testUsername + "somethingElse")
        .email(testUsername + "somethingElse@test.com")
        .eventId(2)
        .eventDateTime(LocalDateTime.now(clock))
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking3 = Booking.builder()
        .username(testUsername)
        .email(testUsername + "@test.com")
        .eventId(3)
        .eventDateTime(LocalDateTime.now(clock))
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    insertBookings(booking1, booking2, booking3);

    var expected = bookingRepository.findByUsername(testUsername)
        .blockFirst();

    assertNotNull(expected);

    bookingRepository.findByIdAndUsername(expected.getId(), testUsername)
        .as(StepVerifier::create)
        .expectNext(booking1)
        .verifyComplete();
  }


  private void insertBookings(Booking... bookings) {
    bookingRepository.saveAll(List.of(bookings))
        .as(StepVerifier::create)
        .expectNextCount(bookings.length)
        .verifyComplete();
  }
}