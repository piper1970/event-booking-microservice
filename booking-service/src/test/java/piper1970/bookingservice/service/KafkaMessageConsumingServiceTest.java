package piper1970.bookingservice.service;

import static org.junit.jupiter.api.Assertions.fail;

import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ProducerFactory;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;

@Disabled("next in line")
@Tag("integration-test")
class KafkaMessageConsumingServiceTest extends AbstractKafkaTest {

  @Autowired
  KafkaMessageConsumingService kafkaMessageConsumingService;

  @Autowired
  BookingRepository bookingRepository;

  @Autowired
  ProducerFactory<Integer,Object> producerFactory;

  // mock producer to test kafka-listener logic
  Producer<Integer, Object> testProducer;

  @BeforeEach
  void setup() {
    testProducer = producerFactory.createProducer();
  }

  @AfterEach
  void tearDown() {
    testProducer.close();
  }

  @Test
  void consumeBookingConfirmedMessage() {

    fail("Not Yet Implemented...");

    // initialize db to delete then add record below
    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var bookingConfirmedMessage = new BookingConfirmed();

    // we save the booking in the database, capturing the id
    // we post the bookingConfirmed message for the booking with that id
    // then we wait until the booking in the database is in status of confirmed
  }

  @Test
  void consumeBookingEventUnavailableMessage() {
    fail("Not Yet Implemented...");

    // initialize db to delete then add record below
    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.CONFIRMED)
        .build();

    var unavailableMessage = new BookingEventUnavailable();

    // we save the booking in the database, capturing the id
    // we post the unavailableMessage message for the booking with that id
    // then we wait until the booking in the database is in status of cancelled

  }

  @Test
  void consumeEventChangedMessage() {
    fail("Not Yet Implemented...");

    // initialize db to delete then add record(s) below
    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.CONFIRMED)
        .build();

    var eventChangedMessage = new EventChanged();

    // we save the booking(s) in the database, capturing the id
    // we post the eventChangedMessage message for the booking with these id's
    // then we wait until the bookings in the database have updated info
    // and

  }

  @Test
  void consumeEventCancelledMessage() {
    fail("Not Yet Implemented...");
    // initialize db to delete then add record(s) below
    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.CONFIRMED)
        .build();

    var eventCancelledMessage = new EventCancelled();

    // we save the booking(s) in the database, capturing the id
    // we post the eventCancelled message for the bookings with these id's
    // then we wait for the Mono<BookingsUpdated> to be returned
  }

  @Test
  @Disabled("logic not yet implemented")
  void consumeEventCompletedMessage() {
    fail("Not Yet Implemented...");
  }
}