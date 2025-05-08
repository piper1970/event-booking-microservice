package piper1970.bookingservice.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer.OrderAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import reactor.test.StepVerifier;

@DataR2dbcTest
@DisplayName("Booking Repository")
@TestClassOrder(OrderAnnotation.class)
@Order(4)
class BookingRepositoryTests {

  @Autowired
  BookingRepository bookingRepository;

  @Autowired
  DatabaseClient databaseClient;

  @BeforeEach
  void setUp() {

    var statements = List.of("DROP TABLE IF EXISTS event_service.bookings;",
        "DROP SCHEMA IF EXISTS event_service;",
        "CREATE SCHEMA event_service;",
        """
            CREATE TABLE event_service.bookings
                (
                    id                int GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    version           int,
                    event_id          int NOT NULL,
                    username          varchar(60) NOT NULL,
                    email             varchar(255) NOT NULL,
                    booking_status    varchar(30) NOT NULL
                );
            """);

    statements.forEach(stmt -> databaseClient.sql(stmt)
        .fetch()
        .rowsUpdated()
        .as(StepVerifier::create)
        .expectNextCount(1)
        .verifyComplete());
  }

  @Test
  @DisplayName("should be able to find bookings given a username")
  void findByUsername() {
    var testUsername = "test_username";
    var booking1 = Booking.builder()
        .username(testUsername)
        .email(testUsername  + "@test.com")
        .eventId(1)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking2 = Booking.builder()
        .username(testUsername + "somethingElse")
        .email(testUsername + "somethingElse@test.com")
        .eventId(2)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking3 = Booking.builder()
        .username(testUsername)
        .email(testUsername  + "@test.com")
        .eventId(3)
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
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking2 = Booking.builder()
        .username(testUsername + "somethingElse")
        .email(testUsername + "somethingElse@test.com")
        .eventId(2)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking3 = Booking.builder()
        .username(testUsername)
        .email(testUsername + "@test.com")
        .eventId(3)
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

  @Test
  @DisplayName("should be able to find booking summaries for bookings with given event id that are not completed or cancelled")
  void findBookingSummariesForEventIdThatAreNotCompletedOrCancelled() {
    var testUsername = "test_username";
    var booking1 = Booking.builder()
        .username(testUsername + 1)
        .email(testUsername  + "1@test.com")
        .eventId(1)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking2 = Booking.builder()
        .username(testUsername + 2)
        .email(testUsername + "2@test.com")
        .eventId(2)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking3 = Booking.builder()
        .username(testUsername + 3)
        .email(testUsername + "3@test.com")
        .eventId(1)
        .bookingStatus(BookingStatus.CONFIRMED)
        .build();

    var booking4 = Booking.builder()
        .username(testUsername + 4)
        .email(testUsername + "4@test.com")
        .eventId(1)
        .bookingStatus(BookingStatus.CANCELLED)
        .build();

    var booking5 = Booking.builder()
        .username(testUsername + 5)
        .email(testUsername + "5@test.com")
        .eventId(2)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking6 = Booking.builder()
        .username(testUsername + 6)
        .email(testUsername + "6@test.com")
        .eventId(1)
        .bookingStatus(BookingStatus.COMPLETED)
        .build();

    insertBookings(booking1, booking2, booking3, booking4, booking5, booking6);

    bookingRepository.findByEventIdAndBookingStatusNotIn(1, List.of(BookingStatus.CANCELLED, BookingStatus.COMPLETED))
        .as(StepVerifier::create)
        .expectNextCount(2)
        .verifyComplete();
  }


  private void insertBookings(Booking... bookings) {
    bookingRepository.saveAll(List.of(bookings))
        .as(StepVerifier::create)
        .expectNextCount(bookings.length)
        .verifyComplete();
  }
}