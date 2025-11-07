package piper1970.eventservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

import brave.Tracer;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer.OrderAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.events.status.EventStatus;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.DiscoverableListener;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.domain.Event;
import piper1970.eventservice.kafka.listeners.BookingCancelledListener;
import piper1970.eventservice.kafka.listeners.BookingConfirmedListener;
import piper1970.eventservice.repository.EventRepository;
import piper1970.eventservice.service.ReactiveKafkaMessagePostingService;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

@Tag("kafka-test")
@DisplayName("Event-Service: Kafka Tests")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1)
@ActiveProfiles({"test", "test_kafka"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestClassOrder(OrderAnnotation.class)
@TestPropertySource(properties = {
    "EVENTS_TRUSTSTORE_PATH=/mock/path",
    "EVENTS_TRUSTSTORE_PASSWORD=mock_password"
})
@Order(1)
public class EventServiceApplicationTests {

  //region Properties Used

  @Autowired
  @Qualifier("repository")
  public Retry defaultRepositoryRetry;

  @Autowired
  @Qualifier("kafka")
  public Retry defaultKafkaRetry;

  @Autowired
  public Tracer tracer;

  private final Duration timeoutDuration = Duration.ofSeconds(4);

  @Autowired
  EventRepository eventRepository;

  @Autowired
  private ReactiveKafkaMessagePostingService reactiveKafkaMessagePostingService;

  @Autowired
  private ReactiveKafkaReceiverFactory receiverFactory;

  @Autowired
  private KafkaSender<Integer, Object> kafkaSender;


  @Autowired
  private DeadLetterTopicProducer dltProducer;

  @Autowired
  private Clock clock;

  @Value("${event-repository.timout.milliseconds}")
  private Integer timeoutInMilliseconds;

  private final List<DiscoverableListener> discoverableListeners = new ArrayList<>();

  //endregion Properties Used

  //region Before/After

  @BeforeAll
  void setupListeners() {
    // setup all kafka listeners
    discoverableListeners.add(new BookingCancelledListener(receiverFactory,
        eventRepository, dltProducer, timeoutInMilliseconds, defaultRepositoryRetry));
    discoverableListeners.add(
        new BookingConfirmedListener(receiverFactory, eventRepository,
            kafkaSender, tracer, dltProducer, timeoutInMilliseconds, defaultRepositoryRetry,
            defaultKafkaRetry, clock));

    // initialize call listeners
    discoverableListeners.forEach(DiscoverableListener::initializeReceiverFlux);
  }

  @AfterAll
  void teardownListeners() {
    // close all kafka listeners
    discoverableListeners.forEach(DiscoverableListener::close);
  }

  @BeforeEach
  void setUp() {
    // clear database before each run
    eventRepository.deleteAll().block();
  }

  //endregion Before/After

  //region Tests

  @Test
  void contextLoads() {
  }

  @Test
  @DisplayName("should be able to consume a BookingCancelled message from the booking-cancelled kafka topic")
  void consumeBookingCancelledMessage() {

    var availableBookings = 10;
    var facilitator = "test-facilitator-1";
    var event = Event.builder()
        .eventDateTime(LocalDateTime.now().plusHours(2))
        .durationInMinutes(60)
        .title("test-title")
        .facilitator(facilitator)
        .location("test-location")
        .description("test-description")
        .eventStatus(EventStatus.IN_PROGRESS)
        .availableBookings(availableBookings)
        .build();

    var savedEvent = eventRepository.save(event)
        .block();
    assertThat(savedEvent).isNotNull();

    var message = new BookingCancelled();
    message.setBooking(new BookingId(27, "test_user@test.com", "test_user"));
    message.setEventId(savedEvent.getId());
    message.setMessage("test-message");
    kafkaSender.send(Mono.just(SenderRecord.create(
            new ProducerRecord<>(Topics.BOOKING_CANCELLED, savedEvent.getId(), message), null)))
        .then()
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() ->
            eventRepository.findByIdAndFacilitator(savedEvent.getId(), facilitator)
                .blockOptional()
                .ifPresentOrElse(ev1 ->
                        assertThat(ev1.getAvailableBookings()).isEqualTo(availableBookings + 1)
                    , () -> fail("Unable to access record in db")));
  }

  @Test
  @DisplayName("""
      should be able to consume a BookingConfirmed message from the booking-confirmed kafka topic
      and properly deduct 1 booking from event availability count given the event still has available
      seats
      """)
  void consumeBookingConfirmedMessage_AvailableBookings() {
    var availableBookings = 1;
    var facilitator = "test-facilitator-2";
    var event = Event.builder()
        .eventDateTime(LocalDateTime.now().plusHours(2))
        .durationInMinutes(60)
        .title("test-title")
        .facilitator(facilitator)
        .location("test-location")
        .description("test-description")
        .eventStatus(EventStatus.IN_PROGRESS)
        .availableBookings(availableBookings)
        .build();

    var savedEvent = eventRepository.save(event)
        .block(timeoutDuration);
    assertThat(savedEvent).isNotNull();

    var message = new BookingConfirmed();
    message.setBooking(new BookingId(27, "test_user@test.com", "test_user"));
    message.setEventId(savedEvent.getId());

    kafkaSender.send(Mono.just(SenderRecord.create(
            new ProducerRecord<>(Topics.BOOKING_CONFIRMED, savedEvent.getId(), message), null)))
        .then()
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() ->
            eventRepository.findByIdAndFacilitator(savedEvent.getId(), facilitator)
                .blockOptional()
                .ifPresentOrElse(ev1 ->
                        assertThat(ev1.getAvailableBookings()).isEqualTo(0)
                    , () -> fail("Unable to access record in db")));
  }

  @DisplayName("""
      should be able to consume a BookingConfirmed message from the booking-confirmed kafka topic
      AND post a BookingEventUnavailable message to the booking-event-unavailable kafka topic
      if the event has no more available seats
      """)
  @Test
  void consumeBookingConfirmedMessage_NoAvailableBookings() {

    var bookingId = 2;
    var facilitator = "test-facilitator-3";
    var event = Event.builder()
        .eventDateTime(LocalDateTime.now().plusHours(2))
        .durationInMinutes(60)
        .title("test-title")
        .facilitator(facilitator)
        .location("test-location")
        .description("test-description")
        .eventStatus(EventStatus.IN_PROGRESS)
        .availableBookings(0)
        .build();

    var savedEvent = eventRepository.save(event)
        .block(timeoutDuration);
    assertThat(savedEvent).isNotNull();

    var message = new BookingConfirmed();
    message.setBooking(new BookingId(bookingId, "test_user@test.com", "test_user"));
    message.setEventId(savedEvent.getId());

    kafkaSender.send(Mono.just(SenderRecord.create(
            new ProducerRecord<>(Topics.BOOKING_CONFIRMED, savedEvent.getId(), message), null)))
        .then()
        .block(timeoutDuration);

    var receiver = receiverFactory.getReceiver(Topics.BOOKING_EVENT_UNAVAILABLE);

    StepVerifier.withVirtualTime(() -> getReceiverAsMono(receiver))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10)) // give it some time
        .assertNext(record ->
            assertAll(
                () -> assertThat(record.value()).isInstanceOf(BookingEventUnavailable.class),
                () -> {
                  if (record.value() instanceof BookingEventUnavailable value) {
                    assertThat(value).isNotNull();
                    var bkg = value.getBooking();
                    assertThat(bkg).isNotNull();
                    var bid = bkg.getId();
                    assertThat(bid).isEqualTo(bookingId);
                  }
                }
            ));
  }

  @DisplayName("should be able to post an EventCancelled message to the event-cancelled kafka topic")
  @Test
  void postEventCancelledMessage() {

    var message = new EventCancelled();
    message.setEventId(1);
    message.setMessage("Test event cancelled message");

    reactiveKafkaMessagePostingService.postEventCancelledMessage(message)
        .block(timeoutDuration);

    var receiver = receiverFactory.getReceiver(Topics.EVENT_CANCELLED);

    StepVerifier.withVirtualTime(() -> getReceiverAsMono(receiver))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10))
        .assertNext(record ->
            assertAll(
                () -> assertThat(record.key()).isEqualTo(1),
                () -> assertThat(record.value()).isEqualTo(message)
            )
        );
  }

  @DisplayName("should be able to post an EventChanged message to the event-changed kafka topic")
  @Test
  void postEventChangedMessage() {

    var message = new EventChanged();
    message.setEventId(1);
    message.setMessage("Test event changed message");

    reactiveKafkaMessagePostingService.postEventChangedMessage(message)
        .block(timeoutDuration);

    var receiver = receiverFactory.getReceiver(Topics.EVENT_CHANGED);

    StepVerifier.withVirtualTime(() -> getReceiverAsMono(receiver))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10))
        .assertNext(record ->
            assertAll(
                () -> assertThat(record.key()).isEqualTo(1),
                () -> assertThat(record.value()).isEqualTo(message)
            )
        );
  }

  @DisplayName("should be able to post an EventCompleted message to the event-completed kafka topic")
  @Test
  void postEventCompletedMessage() {

    var message = new EventCompleted();
    message.setEventId(1);
    message.setMessage("Test event completed message");

    reactiveKafkaMessagePostingService.postEventCompletedMessage(message)
        .block(timeoutDuration);

    var receiver = receiverFactory.getReceiver(Topics.EVENT_COMPLETED);

    StepVerifier.withVirtualTime(() -> getReceiverAsMono(receiver))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10))
        .assertNext(record ->
            assertAll(
                () -> assertThat(record.key()).isEqualTo(1),
                () -> assertThat(record.value()).isEqualTo(message)
            )
        );
  }

//endregion Tests

  // region Helpers

  /**
   * Helper metho to convert a KafkaReceiver object to a ConsumerRecord mono
   */
  private Mono<ConsumerRecord<Integer, Object>> getReceiverAsMono(
      KafkaReceiver<Integer, Object> receiver) {
    return receiver.receiveAtmostOnce()
        .single();
  }

  //endregion Helpers

  //region Configuration

  @TestConfiguration
  @ActiveProfiles({"test", "test_kafka"})
  static class TestConfig {

    /**
     * Mock schema registry client that accesses avro schemas locally from the filesystem
     */
    @Bean
    public SchemaRegistryClient schemaRegistryClient(KafkaProperties kafkaProperties) {
      String registryUrl = kafkaProperties.getProperties().get("schema.registry.url");
      var scope = MockSchemaRegistry.validateAndMaybeGetMockScope(List.of(registryUrl));
      return MockSchemaRegistry.getClientForScope(scope);
    }

    /**
     * Enhanced ReceiverFactory with extended range of Topics needed for tests.
     */
    @Primary
    @Bean
    public ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory(ReceiverOptions<Integer, Object> receiverOptions) {
      var topics = List.of(Topics.BOOKING_CREATED, Topics.BOOKING_EVENT_UNAVAILABLE, Topics.BOOKING_CANCELLED,
          Topics.BOOKINGS_UPDATED, Topics.BOOKINGS_CANCELLED,
          Topics.BOOKING_CONFIRMED, Topics.BOOKING_EXPIRED, Topics.EVENT_CHANGED, Topics.EVENT_CANCELLED,
          Topics.EVENT_COMPLETED);
      return new ReactiveKafkaReceiverFactory(receiverOptions, topics);
    }
  }

//endregion Configuration

}
