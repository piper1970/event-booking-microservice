package piper1970.bookingservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.time.Duration;
import java.util.List;
import org.apache.hc.core5.util.Timeout;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.topics.Topics;

@Tags({
    @Tag("integration-test"),
    @Tag("kafka-test")
})
@DisplayName("Booking-Service: KafkaMessageConsumingService IT")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles({"test", "integration_kafka"})
class KafkaMessageConsumingServiceTestIT{

  //region Properties Used

  private final Duration timeoutDuration = Duration.ofSeconds(4);

  @Autowired
  BookingRepository bookingRepository;

  @Autowired
  ProducerFactory<Integer, Object> producerFactory;

  @Autowired
  private ConsumerFactory<Integer, Object> consumerFactory;

  // mock producer to test kafka-listener logic
  Producer<Integer, Object> testProducer;

  //endregion Properties Used

  //region Before/After

  @BeforeEach
  void setup() {
    testProducer = producerFactory.createProducer();

    // clear database.
    bookingRepository.deleteAll().block();
  }

  @AfterEach
  void tearDown() {
    testProducer.close();
  }

  //endregion Before/After

  //region Tests

  @Test
  @DisplayName("should trigger a database update from IN_PROGRESS to CONFIRMED when a BookingConfirmed message is posted to kafka topic")
  void consumeBookingConfirmedMessage() {

    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    bookingRepository.save(booking)
        .doOnNext(bk -> {
          var message = new BookingConfirmed();
          message.setBooking(new BookingId(bk.getId(), bk.getEmail(), bk.getUsername()));
          message.setEventId(bk.getEventId());
          testProducer.send(new ProducerRecord<>(Topics.BOOKING_CONFIRMED, bk.getId(), message));
        }).block();

    Awaitility.await().atMost(timeoutDuration).untilAsserted(() -> bookingRepository.findByUsername(booking.getUsername())
        .single()
        .blockOptional()
        .ifPresentOrElse(bk -> {
          var status = bk.getBookingStatus();
          assertThat(status).isNotNull();
          assertThat(status).isEqualTo(BookingStatus.CONFIRMED);
        }, () -> fail("Unable to find booking")));
  }

  @Test
  @DisplayName("should trigger a database update from IN_PROGRESS to CANCELLED when a BookingEventUnavailable message is posted to kafka topic")
  void consumeBookingEventUnavailableMessage_InProgress() {

    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    bookingRepository.save(booking)
        .doOnNext(bk -> {
          var message = new BookingEventUnavailable();
          message.setBooking(new BookingId(bk.getId(), bk.getEmail(), bk.getUsername()));
          message.setEventId(bk.getEventId());
          testProducer.send(
              new ProducerRecord<>(Topics.BOOKING_EVENT_UNAVAILABLE, bk.getId(), message));
        }).block();

    Awaitility.await().atMost(timeoutDuration).untilAsserted(() -> bookingRepository.findByUsername(booking.getUsername())
        .single()
        .blockOptional()
        .ifPresentOrElse(bk -> {
              var status = bk.getBookingStatus();
              assertThat(status).isNotNull();
              assertThat(status).isEqualTo(BookingStatus.CANCELLED);
            },
            () -> fail("Unable to find booking")));
  }

  @Test
  @DisplayName("should trigger a database update from CONFIRMED to CANCELLED when a BookingEventUnavailable message is posted to kafka topic")
  void consumeBookingEventUnavailableMessage_Confirmed() {

    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.CONFIRMED)
        .build();

    bookingRepository.save(booking)
        .doOnNext(bk -> {
          var message = new BookingEventUnavailable();
          message.setBooking(new BookingId(bk.getId(), bk.getEmail(), bk.getUsername()));
          message.setEventId(bk.getEventId());
          testProducer.send(
              new ProducerRecord<>(Topics.BOOKING_EVENT_UNAVAILABLE, bk.getId(), message));
        }).block();

    Awaitility.await().atMost(timeoutDuration).untilAsserted(() -> bookingRepository.findByUsername(booking.getUsername())
        .single()
        .blockOptional()
        .ifPresentOrElse(bk -> {
              var status = bk.getBookingStatus();
              assertThat(status).isNotNull();
              assertThat(status).isEqualTo(BookingStatus.CANCELLED);
            },
            () -> fail("Unable to find booking")));
  }

  @Test
  @DisplayName("should not trigger a database update from COMPLETED to CANCELLED when a BookingEventUnavailable message is posted to kafka topic")
  void consumeBookingEventUnavailableMessage_Completed() throws InterruptedException {

    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.COMPLETED)
        .build();

    bookingRepository.save(booking)
        .doOnNext(bk -> {
          var message = new BookingEventUnavailable();
          message.setBooking(new BookingId(bk.getId(), bk.getEmail(), bk.getUsername()));
          message.setEventId(bk.getEventId());
          testProducer.send(
              new ProducerRecord<>(Topics.BOOKING_EVENT_UNAVAILABLE, bk.getId(), message));
        }).block();

    Timeout.of(timeoutDuration).sleep(); // give it time to go through kafka chain

    bookingRepository.findByUsername(booking.getUsername())
        .single()
        .blockOptional()
        .ifPresentOrElse(bk -> {
              var status = bk.getBookingStatus();
              assertThat(status).isNotNull();
              assertThat(status).isEqualTo(BookingStatus.COMPLETED); // stays the same
            },
            () -> fail("Unable to find booking"));
  }

  @Test
  void consumeEventChangedMessage() {

    var booking1 = Booking.builder() // will need updating
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();
    var booking2 = Booking.builder() // will need updating
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.CONFIRMED)
        .build();
    var booking3 = Booking.builder() // message not for this event
        .email("test_user@test.com")
        .username("test_user")
        .eventId(2)
        .bookingStatus(BookingStatus.CONFIRMED)
        .build();
    var booking4 = Booking.builder() // will not update, since status is CANCELLED
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.CANCELLED)
        .build();

    bookingRepository.saveAll(List.of(booking1, booking2, booking3, booking4))
        .collectList()
        .doOnNext(ignored -> {
          var message = new EventChanged(1, "Test Event Changed Message");
          testProducer.send(new ProducerRecord<>(Topics.EVENT_CHANGED, 1, message));
        }).block();

    try (var testConsumer = consumerFactory.createConsumer()) {
      testConsumer.subscribe(List.of(Topics.BOOKINGS_UPDATED));

      var consumed = KafkaTestUtils.getSingleRecord(testConsumer, Topics.BOOKINGS_UPDATED,
          timeoutDuration);

      assertThat(consumed).isNotNull();
      assertThat(consumed.key()).isEqualTo(1);

      var message = (BookingsUpdated) consumed.value();
      assertThat(message).isNotNull();

      var bookings = message.getBookings();
      assertThat(bookings).isNotNull();
      assertThat(bookings.size()).isEqualTo(2);
    }
  }

  @Test
  void consumeEventCancelledMessage() {

    var booking1 = Booking.builder() // will need cancelling
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();
    var booking2 = Booking.builder() // will need cancelling
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.CONFIRMED)
        .build();
    var booking3 = Booking.builder() // message not for this event
        .email("test_user@test.com")
        .username("test_user")
        .eventId(2)
        .bookingStatus(BookingStatus.CONFIRMED)
        .build();
    var booking4 = Booking.builder() // will not cancel, since status is completed
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.COMPLETED)
        .build();

    bookingRepository.saveAll(List.of(booking1, booking2, booking3, booking4))
        .collectList()
        .doOnNext(ignored -> {
          var message = new EventCancelled(1, "Test Event Changed Message");
          testProducer.send(new ProducerRecord<>(Topics.EVENT_CANCELLED, 1, message));
        }).block();

    try (var testConsumer = consumerFactory.createConsumer()) {
      testConsumer.subscribe(List.of(Topics.BOOKINGS_CANCELLED));

      var consumed = KafkaTestUtils.getSingleRecord(testConsumer, Topics.BOOKINGS_CANCELLED,
          timeoutDuration);

      assertThat(consumed).isNotNull();
      assertThat(consumed.key()).isEqualTo(1);

      var message = (BookingsCancelled) consumed.value();
      assertThat(message).isNotNull();

      var bookings = message.getBookings();
      assertThat(bookings).isNotNull();
      assertThat(bookings.size()).isEqualTo(2);
    }
  }

  @Test
  @Disabled("logic not yet implemented")
  void consumeEventCompletedMessage() {
    // TODO: should trigger a update of all booking items in db to completed, as long as they
    //   are in either IN_PROGRESS or CONFIRMED.  Possibly, IN_PROGRESS should be CANCELLED
    //   at this point, instead of COMPLETED
    fail("Not Yet Implemented...");
  }

  //endregion Tests

  //region Configuration

  @TestConfiguration
  @ActiveProfiles({"test", "integration_kafka"})
  static class TestConfig{
    @Bean
    public SchemaRegistryClient schemaRegistryClient(KafkaProperties kafkaProperties) {
      String registryUrl = kafkaProperties.getProperties().get("schema.registry.url");
      var scope = MockSchemaRegistry.validateAndMaybeGetMockScope(List.of(registryUrl));
      return MockSchemaRegistry.getClientForScope(scope);
    }
  }

  //endregion Configuration

}