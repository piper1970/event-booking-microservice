package piper1970.bookingservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
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
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import piper1970.bookingservice.domain.Booking;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.kafka.listeners.BookingConfirmedListener;
import piper1970.bookingservice.kafka.listeners.BookingEventUnavailableListener;
import piper1970.bookingservice.kafka.listeners.BookingExpiredListener;
import piper1970.bookingservice.kafka.listeners.EventCancelledListener;
import piper1970.bookingservice.kafka.listeners.EventChangedListener;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.bookingservice.service.MessagePostingService;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.events.messages.EventCancelled;
import piper1970.eventservice.common.events.messages.EventChanged;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.DiscoverableListener;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;


@Tag("kafka-test")
@DisplayName("Booking-Service: Kafka Tests")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1)
@ActiveProfiles({"test", "test_kafka"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
@TestClassOrder(OrderAnnotation.class)
@Order(1)
@Slf4j
class BookingServiceApplicationTests {

  //region Properties Used

  @Autowired
  private MessagePostingService kafkaMessagePostingService;

  private final Duration timeoutDuration = Duration.ofSeconds(4);

  @Autowired
  BookingRepository bookingRepository;

  @Autowired
  ReactiveKafkaProducerTemplate<Integer, Object> reactiveKafkaProducerTemplate;

  @Autowired
  private ReactiveKafkaReceiverFactory receiverFactory;

  @Autowired
  private DeadLetterTopicProducer dltProducer;

  @Autowired
  private TransactionalOperator transactionalOperator;

  @Autowired
  @Qualifier("repository")
  private Retry defaultRepositoryRetry;

  @Autowired
  @Qualifier("kafka")
  private Retry defaultKafkaRetry;

  @Value("${booking-repository.timout.milliseconds}")
  private Long timeoutMillis;

  private final List<DiscoverableListener> discoverableListeners = new ArrayList<>();

  //endregion Properties Used

  //region Before/After

  @BeforeAll
  void setupListeners() {
    discoverableListeners.add(new BookingConfirmedListener(receiverFactory, dltProducer,
        bookingRepository, timeoutMillis, defaultRepositoryRetry));
    discoverableListeners.add(new BookingExpiredListener(receiverFactory, dltProducer,
        bookingRepository, timeoutMillis,defaultRepositoryRetry));
    discoverableListeners.add(new BookingEventUnavailableListener(receiverFactory, dltProducer,
        bookingRepository, timeoutMillis,defaultRepositoryRetry));
    discoverableListeners.add(new EventChangedListener(receiverFactory, dltProducer,
        reactiveKafkaProducerTemplate,
        bookingRepository, timeoutMillis, defaultRepositoryRetry, defaultKafkaRetry));
    discoverableListeners.add(new EventCancelledListener(receiverFactory, dltProducer,
        reactiveKafkaProducerTemplate,
        bookingRepository, transactionalOperator, timeoutMillis, defaultRepositoryRetry));

    discoverableListeners.forEach(DiscoverableListener::initializeReceiverFlux);
  }

  @AfterAll
  void teardownListeners() {
    discoverableListeners.forEach(DiscoverableListener::close);
  }

  @BeforeEach
  void setup() {

    // clear database.
    bookingRepository.deleteAll().block();
  }

  //endregion Before/After

  //region Tests

  @Test
  void contextLoads() {
  }

  @Test
  @DisplayName("should be able to post BookingCreated message to BOOKING_CREATED kafka topic")
  void postBookingCreatedMessage() {

    var message = new BookingCreated();
    var bookingID = new BookingId();
    bookingID.setId(1);
    bookingID.setUsername("test_user");
    bookingID.setEmail("test_user@test.com");
    message.setBooking(bookingID);
    message.setEventId(1);
    kafkaMessagePostingService.postBookingCreatedMessage(message)
        .block(timeoutDuration);

    var receiver = receiverFactory.getReceiver(Topics.BOOKING_CREATED);

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

  @Test
  @DisplayName("should be able to post BookingCancelled message to BOOKING_CANCELLED kafka topic")
  void postBookingCancelledMessage() {
    var message = new BookingCancelled();
    var bookingID = new BookingId();
    bookingID.setId(1);
    bookingID.setUsername("test_user");
    bookingID.setEmail("test_user@test.com");
    message.setBooking(bookingID);
    message.setEventId(1);
    kafkaMessagePostingService.postBookingCancelledMessage(message)
        .block(timeoutDuration);

    var receiver = receiverFactory.getReceiver(Topics.BOOKING_CANCELLED);

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

  @Test
  @DisplayName("should be able to post BookingsUpdated message to BOOKINGS_UPDATED kafka topic")
  void postBookingsUpdatedMessage() {
    var message = new BookingsUpdated();
    var bookingID = new BookingId();
    bookingID.setId(1);
    bookingID.setUsername("test_user");
    bookingID.setEmail("test_user@test.com");
    message.setBookings(List.of(bookingID));
    message.setEventId(1);
    kafkaMessagePostingService.postBookingsUpdatedMessage(message)
        .subscribe();

    var receiver = receiverFactory.getReceiver(Topics.BOOKINGS_UPDATED);

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

  @Test
  @DisplayName("should be able to post BookingsCancelled message to BOOKINGS_CANCELLED kafka topic")
  void postBookingsCancelledMessage() {
    var message = new BookingsCancelled();
    var bookingID = new BookingId();
    bookingID.setId(1);
    bookingID.setUsername("test_user");
    bookingID.setEmail("test_user@test.com");
    message.setBookings(List.of(bookingID));
    message.setEventId(1);
    kafkaMessagePostingService.postBookingsCancelledMessage(message)
        .subscribe();

    var receiver = receiverFactory.getReceiver(Topics.BOOKINGS_CANCELLED);

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

  @Test
  @DisplayName("should trigger a database update from IN_PROGRESS to CONFIRMED when a BookingConfirmed message is posted to BOOKING_CONFIRMED kafka topic")
  void consumeBookingConfirmedMessage() {

    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var savedBooking = bookingRepository.save(booking)
        .block(timeoutDuration);
    assertThat(savedBooking).isNotNull();

    var message = new BookingConfirmed();
    message.setBooking(
        new BookingId(savedBooking.getId(), savedBooking.getEmail(), savedBooking.getUsername()));
    message.setEventId(savedBooking.getEventId());

    reactiveKafkaProducerTemplate.send(
            new ProducerRecord<>(Topics.BOOKING_CONFIRMED, savedBooking.getId(), message))
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(
            () -> bookingRepository.findByIdAndUsername(savedBooking.getId(), booking.getUsername())
                .blockOptional()
                .ifPresentOrElse(bk -> {
                  var status = bk.getBookingStatus();
                  assertThat(status).isNotNull();
                  assertThat(status).isEqualTo(BookingStatus.CONFIRMED);
                }, () -> fail("Unable to find booking")));
  }

  @Test
  @DisplayName("should trigger a database update from IN_PROGRESS to CANCELLED when a BookingExpired message is posted to BOOKING_EXPIRED kafka topic")
  void consumeBookingExpiredMessage() {

    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var savedBooking = bookingRepository.save(booking)
        .block(timeoutDuration);
    assertThat(savedBooking).isNotNull();

    var message = new BookingExpired();
    message.setBooking(
        new BookingId(savedBooking.getId(), savedBooking.getEmail(), savedBooking.getUsername()));
    message.setEventId(savedBooking.getEventId());

    reactiveKafkaProducerTemplate.send(
            new ProducerRecord<>(Topics.BOOKING_EXPIRED, savedBooking.getId(), message))
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() -> bookingRepository.findByIdAndUsername(savedBooking.getId(),
                booking.getUsername())
            .blockOptional()
            .ifPresentOrElse(bk -> {
              var status = bk.getBookingStatus();
              assertThat(status).isNotNull();
              assertThat(status).isEqualTo(BookingStatus.CANCELLED);
            }, () -> fail("Unable to find booking")));
  }

  @Test
  @DisplayName("should trigger a database update from IN_PROGRESS to CANCELLED when a BookingEventUnavailable message is posted to BOOKING_EVENT_UNAVAILABLE kafka topic")
  void consumeBookingEventUnavailableMessage_InProgress() {

    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.IN_PROGRESS)
        .build();

    var savedBooking = bookingRepository.save(booking)
        .block(timeoutDuration);
    assertThat(savedBooking).isNotNull();

    var message = new BookingEventUnavailable();
    message.setBooking(
        new BookingId(savedBooking.getId(), savedBooking.getEmail(), savedBooking.getUsername()));
    message.setEventId(savedBooking.getEventId());

    reactiveKafkaProducerTemplate.send(
            new ProducerRecord<>(Topics.BOOKING_EVENT_UNAVAILABLE, savedBooking.getId(), message))
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() -> bookingRepository.findByIdAndUsername(savedBooking.getId(),
                booking.getUsername())
            .blockOptional()
            .ifPresentOrElse(bk -> {
                  var status = bk.getBookingStatus();
                  assertThat(status).isNotNull();
                  assertThat(status).isEqualTo(BookingStatus.CANCELLED);
                },
                () -> fail("Unable to find booking")));

  }

  @Test
  @DisplayName("should trigger a database update from CONFIRMED to CANCELLED when a BookingEventUnavailable message is posted to BOOKING_EVENT_UNAVAILABLE kafka topic")
  void consumeBookingEventUnavailableMessage_Confirmed() {

    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.CONFIRMED)
        .build();

    var savedBooking = bookingRepository.save(booking)
        .block(timeoutDuration);
    assertThat(savedBooking).isNotNull();

    var message = new BookingEventUnavailable();
    message.setBooking(
        new BookingId(savedBooking.getId(), savedBooking.getEmail(), savedBooking.getUsername()));
    message.setEventId(savedBooking.getEventId());

    reactiveKafkaProducerTemplate.send(
            new ProducerRecord<>(Topics.BOOKING_EVENT_UNAVAILABLE, savedBooking.getId(), message))
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() -> bookingRepository.findByIdAndUsername(savedBooking.getId(),
                booking.getUsername())
            .blockOptional()
            .ifPresentOrElse(bk -> {
                  var status = bk.getBookingStatus();
                  assertThat(status).isNotNull();
                  assertThat(status).isEqualTo(BookingStatus.CANCELLED);
                },
                () -> fail("Unable to find booking")));
  }

  @Test
  @DisplayName("should not trigger a database update from COMPLETED to CANCELLED when a BookingEventUnavailable message is posted to BOOKING_EVENT_UNAVAILABLE kafka topic")
  void consumeBookingEventUnavailableMessage_Completed() {

    var booking = Booking.builder()
        .email("test_user@test.com")
        .username("test_user")
        .eventId(1)
        .bookingStatus(BookingStatus.COMPLETED)
        .build();

    var savedBooking = bookingRepository.save(booking)
        .block(timeoutDuration);
    assertThat(savedBooking).isNotNull();

    var message = new BookingEventUnavailable();
    message.setBooking(
        new BookingId(savedBooking.getId(), savedBooking.getEmail(), savedBooking.getUsername()));
    message.setEventId(savedBooking.getEventId());

    reactiveKafkaProducerTemplate.send(
            new ProducerRecord<>(Topics.BOOKING_EVENT_UNAVAILABLE, savedBooking.getId(), message))
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() -> bookingRepository.findByIdAndUsername(savedBooking.getId(),
                booking.getUsername())
            .blockOptional()
            .ifPresentOrElse(bk -> {
                  var status = bk.getBookingStatus();
                  assertThat(status).isNotNull();
                  assertThat(status).isEqualTo(BookingStatus.COMPLETED); // stays the same
                },
                () -> fail("Unable to find booking")));
  }

  @Test
  @DisplayName(
      "should trigger a BookingsUpdated message on the BOOKINGS_UPDATED kafka topic when an "
          + "EventChanged message is posted to the EVENT_CHANGED kafka topic")
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
        .block(timeoutDuration);

    var message = new EventChanged(1, "Test Event Changed Message");
    reactiveKafkaProducerTemplate.send(
            new ProducerRecord<>(Topics.EVENT_CHANGED, 1, message))
        .block(timeoutDuration);

    var receiver = receiverFactory.getReceiver(Topics.BOOKINGS_UPDATED);
    StepVerifier.withVirtualTime(() -> getReceiverAsMono(receiver))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10)) // give it some time
        .assertNext(record ->
            assertAll(
                () -> assertThat(record.key()).isEqualTo(1),
                () -> assertThat(record.value()).isInstanceOf(BookingsUpdated.class),
                () -> {
                  if (record.value() instanceof BookingsUpdated buMessage) {
                    var bookings = buMessage.getBookings();
                    assertThat(bookings).isNotNull();
                    assertThat(bookings.size()).isEqualTo(2);
                  }
                }
            ));
  }

  @Test
  @DisplayName(
      "should trigger a BookingsCancelled message on BOOKINGS_CANCELLED kafka topic when an "
          + "EventCancelled message is posted to the EVENT_CANCELLED kafka topic")
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
        .block(timeoutDuration);

    var message = new EventCancelled(1, "Test Event Changed Message");
    reactiveKafkaProducerTemplate.send(
            new ProducerRecord<>(Topics.EVENT_CANCELLED, 1, message))
        .block(timeoutDuration);

    var receiver = receiverFactory.getReceiver(Topics.BOOKINGS_CANCELLED);
    StepVerifier.withVirtualTime(() -> getReceiverAsMono(receiver))
        .expectSubscription()
        .thenAwait(timeoutDuration.multipliedBy(10)) // give it some time
        .assertNext(record ->
            assertAll(
                () -> assertThat(record.key()).isEqualTo(1),
                () -> assertThat(record.value()).isInstanceOf(BookingsCancelled.class),
                () -> {
                  if (record.value() instanceof BookingsCancelled bcMessage) {
                    var bookings = bcMessage.getBookings();
                    assertThat(bookings).isNotNull();
                    assertThat(bookings.size()).isEqualTo(2);
                  }
                }
            ));
  }

  @Test
  @DisplayName("should update all bookings associated with event, setting status from IN_PROGRESS->CANCELLED, or CONFIRMED->COMPLETE")
  void consumeEventCompletedMessage() {
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
        .eventId(1)
        .bookingStatus(BookingStatus.CONFIRMED)
        .build();
    var booking4 = Booking.builder() // will not cancel, since status is completed
        .email("test_user@test.com")
        .username("test_user")
        .eventId(2)
        .bookingStatus(BookingStatus.COMPLETED)
        .build();

    bookingRepository.saveAll(List.of(booking1, booking2, booking3, booking4))
        .collectList()
        .block(timeoutDuration);

    var message = new EventCompleted(1, "Test Event Completed Message");
    reactiveKafkaProducerTemplate.send(
            new ProducerRecord<>(Topics.EVENT_COMPLETED, 1, message))
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() ->
            assertAll(
                () -> bookingRepository.findBookingsByEventIdAndBookingStatusIn(1,
                        List.of(BookingStatus.CANCELLED))
                    .count()
                    .blockOptional()
                    .ifPresentOrElse(
                        count -> assertThat(count).isEqualTo(1),
                        () -> fail(
                            "Should not happen: Unable to find booking count for CANCELLED bookings")
                    ),
                () -> bookingRepository.findBookingsByEventIdAndBookingStatusIn(1, List.of(BookingStatus.COMPLETED))
                    .count()
                    .blockOptional()
                    .ifPresentOrElse(
                        count -> assertThat(count).isEqualTo(2),
                        () -> fail("Should not happen: Unable to find booking count for COMPLETED bookings")
                    )));
  }

  //endregion Tests

  //region Helpers

  private Mono<ConsumerRecord<Integer, Object>> getReceiverAsMono(
      KafkaReceiver<Integer, Object> receiver) {
    return receiver.receiveAtmostOnce()
        .single();
  }

  //endregion Helpers

  //region Configuration

  @TestConfiguration
  @ActiveProfiles({"test", "integration_kafka"})
  static class TestConfig {

    @Bean
    public SchemaRegistryClient schemaRegistryClient(KafkaProperties kafkaProperties) {
      String registryUrl = kafkaProperties.getProperties().get("schema.registry.url");
      var scope = MockSchemaRegistry.validateAndMaybeGetMockScope(List.of(registryUrl));
      return MockSchemaRegistry.getClientForScope(scope);
    }

    @Primary
    @Bean
    public ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory(
        ReceiverOptions<Integer, Object> receiverOptions) {
      var topics = List.of(Topics.BOOKING_CREATED, Topics.BOOKING_EVENT_UNAVAILABLE,
          Topics.BOOKING_CANCELLED,
          Topics.BOOKINGS_UPDATED, Topics.BOOKINGS_CANCELLED,
          Topics.BOOKING_CONFIRMED, Topics.BOOKING_EXPIRED, Topics.EVENT_CHANGED,
          Topics.EVENT_CANCELLED,
          Topics.EVENT_COMPLETED);
      return new ReactiveKafkaReceiverFactory(receiverOptions, topics);
    }
  }

  //endregion Configuration

}
