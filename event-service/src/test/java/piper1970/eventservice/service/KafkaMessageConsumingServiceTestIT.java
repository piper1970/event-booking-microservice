package piper1970.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.topics.Topics;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.repository.EventRepository;

@Tags({
    @Tag("integration-test"),
    @Tag("kafka-test")
})
@DisplayName("Event-Service: KafkaMessageConsumingService IT")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles({"test", "integration_kafka"})
class KafkaMessageConsumingServiceTestIT{

  //region Properties Used

  private final Duration timeoutDuration = Duration.ofSeconds(4);

  @Autowired
  EventRepository eventRepository;

  @Autowired
  ProducerFactory<Integer, Object> producerFactory;

  @Autowired
  private ConsumerFactory<Integer, Object> consumerFactory;

  // mock producer to test kafka-listener logic
  Producer<Integer, Object> testProducer;

  //endregion Properties Used

  //region Before/After

  @BeforeEach
  void setUp() {
    testProducer = producerFactory.createProducer();

    eventRepository.deleteAll().block();
  }

  @AfterEach
  void tearDown() {
    testProducer.close();
  }

  //endregion Before/After

  //region Tests

  @Test
  void consumeBookingCancelledMessage() {

    var availableBookings = 10;
    var event = Event.builder()
        .eventDateTime(LocalDateTime.now().plusHours(2))
        .durationInMinutes(60)
        .title("test-title")
        .facilitator("test-facilitator")
        .location("test-location")
        .description("test-description")
        .cancelled(false)
        .availableBookings(availableBookings)
        .build();

    var savedEvent = eventRepository.save(event)
        .doOnNext(ev -> {
          var message = new BookingCancelled();
          message.setBooking(new BookingId(27, "test_user@test.com", "test_user"));
          message.setEventId(ev.getId());
          message.setMessage("test-message");
          testProducer.send(new ProducerRecord<>(Topics.BOOKING_CANCELLED, ev.getId(), message));
        })
        .blockOptional();

    savedEvent.ifPresentOrElse(
        value -> Awaitility.await().atMost(timeoutDuration).untilAsserted(() ->
            eventRepository.findById(value.getId())
                .blockOptional()
                .ifPresentOrElse(ev1 ->
                        assertThat(ev1.getAvailableBookings()).isEqualTo(availableBookings + 1)
                    , () -> fail("Unable to access record in db"))
        ), () -> fail("Unable to access record in db"));
  }

  @Test
  void consumeBookingConfirmedMessage_AvailableBookings () {
    var availableBookings = 1;
    var event = Event.builder()
        .eventDateTime(LocalDateTime.now().plusHours(2))
        .durationInMinutes(60)
        .title("test-title")
        .facilitator("test-facilitator")
        .location("test-location")
        .description("test-description")
        .cancelled(false)
        .availableBookings(availableBookings)
        .build();

    var savedEvent = eventRepository.save(event)
        .doOnNext(ev -> {
          var message = new BookingConfirmed();
          message.setBooking(new BookingId(27, "test_user@test.com", "test_user"));
          message.setEventId(ev.getId());
          testProducer.send(new ProducerRecord<>(Topics.BOOKING_CONFIRMED, ev.getId(), message));
        }).blockOptional();

    savedEvent.ifPresentOrElse(
        value -> Awaitility.await().atMost(timeoutDuration).untilAsserted(() ->
            eventRepository.findById(value.getId())
                .blockOptional()
                .ifPresentOrElse(ev1 ->
                        assertThat(ev1.getAvailableBookings()).isEqualTo(0)
                    , () -> fail("Unable to access record in db"))
        ), () -> fail("Unable to access record in db"));
  }

  @Test
  void consumeBookingConfirmedMessage_NoAvailableBookings () {

    var bookingId = 2;
    var event = Event.builder()
        .eventDateTime(LocalDateTime.now().plusHours(2))
        .durationInMinutes(60)
        .title("test-title")
        .facilitator("test-facilitator")
        .location("test-location")
        .description("test-description")
        .cancelled(false)
        .availableBookings(0)
        .build();

    eventRepository.save(event)
        .doOnNext(ev -> {
          var message = new BookingConfirmed();
          message.setBooking(new BookingId(bookingId, "test_user@test.com", "test_user"));
          message.setEventId(ev.getId());
          testProducer.send(new ProducerRecord<>(Topics.BOOKING_CONFIRMED, ev.getId(), message));
        }).block();

    try (var testConsumer = consumerFactory.createConsumer()) {
      testConsumer.subscribe(List.of(Topics.BOOKING_EVENT_UNAVAILABLE));

      var consumed = KafkaTestUtils.getSingleRecord(testConsumer,
          Topics.BOOKING_EVENT_UNAVAILABLE,
          timeoutDuration);

      assertThat(consumed).isNotNull();
      assertThat(consumed.key()).isNotNull();

      var value = (BookingEventUnavailable) consumed.value();
      assertThat(value).isNotNull();

      var bId = value.getBooking();
      assertThat(bId).isNotNull();

      var bbId = bId.getId();
      assertThat(bbId).isEqualTo(bookingId);
    }
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