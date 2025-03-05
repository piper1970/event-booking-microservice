package piper1970.bookingservice.repository;

import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import reactor.test.StepVerifier;

@SpringBootTest
class BookingRepositoryTest {

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
                    event_id          int     NOT NULL,
                    username          varchar(60) NOT NULL,
                    event_date_time   timestamp   NOT NULL,
                    created_date_time timestamp,
                    updated_date_time timestamp,
                    booking_status    varchar(30) NOT NULL
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
    var testUsername = "test_username";
    var booking1 = Booking.builder()
        .username(testUsername)
        .eventId(1)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking2 = Booking.builder()
        .username(testUsername + "somethingElse")
        .eventId(2)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking3 = Booking.builder()
        .username(testUsername)
        .eventId(3)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    insertBookings(booking1, booking2, booking3);

    bookingRepository.findAll()
        .as(StepVerifier::create)
//        .expectNext(booking1, booking2, booking3)
        .expectNextCount(3)
        .verifyComplete();
  }

  @Test
  void findById() {
    var testUsername = "test_username";
    var booking1 = Booking.builder()
        .username(testUsername)
        .eventId(1)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking2 = Booking.builder()
        .username(testUsername)
        .eventId(2)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking3 = Booking.builder()
        .username(testUsername)
        .eventId(3)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    insertBookings(booking1, booking2, booking3);

    var expected = bookingRepository.findAll()
        .blockFirst();

    assertNotNull(expected);

    bookingRepository.findById(expected.getId())
        .as(StepVerifier::create)
        .expectNext(expected)
        .verifyComplete();
  }

  @Test
  void findByUsername() {
    var testUsername = "test_username";
    var booking1 = Booking.builder()
        .username(testUsername)
        .eventId(1)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking2 = Booking.builder()
        .username(testUsername + "somethingElse")
        .eventId(2)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking3 = Booking.builder()
        .username(testUsername)
        .eventId(3)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    insertBookings(booking1, booking2, booking3);

    bookingRepository.findByUsername(testUsername)
        .as(StepVerifier::create)
        .expectNext(booking1, booking3)
        .verifyComplete();
  }

  @Test
  void findBookingByIdAndUsername() {
    var testUsername = "test_username";
    var booking1 = Booking.builder()
        .username(testUsername)
        .eventId(1)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking2 = Booking.builder()
        .username(testUsername + "somethingElse")
        .eventId(2)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking3 = Booking.builder()
        .username(testUsername)
        .eventId(3)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    insertBookings(booking1, booking2, booking3);

    var expected = bookingRepository.findByUsername(testUsername)
        .blockFirst();

    assertNotNull(expected);

    bookingRepository.findBookingByIdAndUsername(expected.getId(), testUsername)
        .as(StepVerifier::create)
        .expectNext(booking1)
        .verifyComplete();
  }

  @Test
  void count(){
    var testUsername = "test_username";
    var booking1 = Booking.builder()
        .username(testUsername)
        .eventId(1)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking2 = Booking.builder()
        .username(testUsername + "somethingElse")
        .eventId(2)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking3 = Booking.builder()
        .username(testUsername)
        .eventId(3)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    insertBookings(booking1, booking2, booking3);

    bookingRepository.count()
        .as(StepVerifier::create)
        .expectNext(3L)
        .verifyComplete();
  }

  @Test
  void save(){

    var testUsername = "test_username";
    var booking1 = Booking.builder()
        .username(testUsername)
        .eventId(1)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking2 = Booking.builder()
        .username(testUsername + "somethingElse")
        .eventId(2)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var booking3 = Booking.builder()
        .username(testUsername)
        .eventId(3)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    insertBookings(booking1, booking2, booking3);

    bookingRepository.count()
        .as(StepVerifier::create)
        .expectNext(3L)
        .verifyComplete();

    var newBooking = Booking.builder()
        .username("new dude")
        .eventId(3)
        .eventDateTime(LocalDateTime.now())
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    bookingRepository.save(newBooking)
        .as(StepVerifier::create)
        .expectNext(newBooking)
        .verifyComplete();

    bookingRepository.count()
        .as(StepVerifier::create)
        .expectNext(4L)
        .verifyComplete();

  }

  private void insertBookings(Booking... bookings) {
    bookingRepository.saveAll(List.of(bookings))
        .as(StepVerifier::create)
        .expectNextCount(bookings.length)
        .verifyComplete();
  }
}