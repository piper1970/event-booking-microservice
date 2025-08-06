package piper1970.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static piper1970.eventservice.common.kafka.KafkaHelper.createSenderMono;
import static piper1970.eventservice.common.kafka.reactive.TracingHelper.extractMDCIntoHeaders;
import static piper1970.notificationservice.kafka.listener.BookingCancelledListener.BOOKING_CANCELLED_MESSAGE_SUBJECT;
import static piper1970.notificationservice.kafka.listener.BookingCreatedListener.BOOKING_HAS_BEEN_CREATED_SUBJECT;
import static piper1970.notificationservice.kafka.listener.BookingEventUnavailableListener.BOOKING_EVENT_UNAVAILABLE_SUBJECT;

import brave.Tracer;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer.OrderAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.reactive.TransactionalOperator;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.kafka.reactive.DiscoverableListener;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.eventservice.common.notifications.messages.BookingConfirmed;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.kafka.listener.BookingCancelledListener;
import piper1970.notificationservice.kafka.listener.BookingCreatedListener;
import piper1970.notificationservice.kafka.listener.BookingEventUnavailableListener;
import piper1970.notificationservice.kafka.listener.BookingsCancelledListener;
import piper1970.notificationservice.kafka.listener.BookingsUpdatedListener;
import piper1970.notificationservice.kafka.listener.options.BaseListenerOptions;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import piper1970.notificationservice.service.MessagePostingService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

@Tag("kafka-test")
@DisplayName("Notification-Service: Kafka Tests")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1)
@ActiveProfiles({"test", "test_kafka"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
@TestClassOrder(OrderAnnotation.class)
@Order(1)
public class NotificationServiceApplicationTests {

  //region Properties Used

  private static final JavaMailSender javaMailSender = mock(JavaMailSender.class);

  private final Duration timeoutDuration = Duration.ofSeconds(10);

  @Autowired
  private MessagePostingService kafkaMessagePostingService;

  @Autowired
  private Tracer tracer;

  @Autowired
  private ReactiveKafkaReceiverFactory receiverFactory;

  private final List<DiscoverableListener> discoverableListeners = new ArrayList<>();

  @Autowired
  private BaseListenerOptions baseListenerOptions;

  @Autowired
  BookingConfirmationRepository repository;

  @Autowired
  KafkaSender<Integer, Object> kafkaSender;

  @Autowired
  private Clock clock;

  @Autowired
  @Qualifier("mailer")
  private Retry defaultMailerRetry;

  @Autowired
  @Qualifier("repository")
  private Retry defaultRepositoryRetry;

  @Autowired
  private TransactionalOperator transactionalOperator;

  @Captor
  private ArgumentCaptor<MimeMessage> sendMailCaptor;

  @Value("${notification-repository.timeout.milliseconds}")
  private Long timeoutInMilliseconds;

  @Value("${confirmation.url}")
  private String confirmationUrl;

  @Value("${confirmation.duration.minutes}")
  private Integer confirmationInMinutes;

  //endregion Properties Used

  //region Before/After

  @BeforeAll
  void setupListeners() {
    discoverableListeners.add(new BookingCreatedListener(baseListenerOptions, repository,
        transactionalOperator, clock, timeoutInMilliseconds,
        confirmationUrl, confirmationInMinutes, defaultRepositoryRetry));
    discoverableListeners.add(new BookingCancelledListener(baseListenerOptions, defaultMailerRetry));
    discoverableListeners.add(new BookingEventUnavailableListener(baseListenerOptions, defaultMailerRetry));
    discoverableListeners.add(new BookingsCancelledListener(baseListenerOptions, defaultMailerRetry));
    discoverableListeners.add(new BookingsUpdatedListener(baseListenerOptions,defaultMailerRetry));

    discoverableListeners.forEach(DiscoverableListener::initializeReceiverFlux);
  }

  @AfterAll
  void closeListeners() {
    discoverableListeners.forEach(DiscoverableListener::close);
  }

  @BeforeEach
  void setUp() {
    when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    doNothing().when(javaMailSender).send(any(MimeMessage.class));

    // clear database
    repository.deleteAll()
        .block();
  }

  @AfterEach
  void tearDown() {
    Mockito.reset(javaMailSender);
  }

  //endregion Before/After

  //region Tests

  @Test
  void contextLoads() {
  }

  @Test
  void postBookingConfirmedMessage() {

    var bookingId = new BookingId(1, "test_user@test.com", "test_user");
    var message = new BookingConfirmed(bookingId, 1);

    kafkaMessagePostingService.postBookingConfirmedMessage(message)
        .subscribe();

    StepVerifier.withVirtualTime(
            () -> getReceiverAsMono(receiverFactory.getReceiver(Topics.BOOKING_CONFIRMED)))
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
  void consumeBookingCreatedMessage() {

    var message = new BookingCreated();
    var eventId = 1;
    var bookingId = 2;
    var email = "test_user@test.com";
    var username = "test_user";
    message.setEventId(eventId);
    message.setBooking(new BookingId(bookingId, email, username));

    kafkaSender.send(
            createSenderMono(Topics.BOOKING_CREATED, bookingId, message, clock, extractMDCIntoHeaders(tracer)))
        .single()
        .subscribeOn(Schedulers.boundedElastic())
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() -> {
          var count = repository.count().block();
          assertThat(count).isGreaterThan(0);
          var cf = repository.findAll()
              .single()
              .block();
          assertThat(cf).isNotNull();
          assertAll(
              () -> assertThat(cf.getBookingEmail()).isEqualTo(email),
              () -> assertThat(cf.getBookingUser()).isEqualTo(username),
              () -> assertThat(cf.getBookingId()).isEqualTo(bookingId),
              () -> assertThat(cf.getEventId()).isEqualTo(eventId),
              () -> assertThat(cf.getConfirmationString()).isNotNull(),
              () -> assertThat(cf.getConfirmationStatus()).isEqualTo(
                  ConfirmationStatus.AWAITING_CONFIRMATION),
              () -> {
                verify(javaMailSender, times(1)).send(sendMailCaptor.capture());
                MimeMessage sendMail = sendMailCaptor.getValue();
                assertThat(sendMail).isNotNull();
                assertThat(sendMail.getSubject()).isEqualTo(BOOKING_HAS_BEEN_CREATED_SUBJECT);
              }
          );
        });
  }

  @Test
  void consumeBookingEventUnavailableMessage() {

    int bookingId = 2;
    var message = new BookingEventUnavailable();
    message.setEventId(1);
    message.setBooking(new BookingId(2, "test_user@test.com", "test_user"));

    kafkaSender.send(createSenderMono(Topics.BOOKING_EVENT_UNAVAILABLE, bookingId, message, clock, extractMDCIntoHeaders(tracer)))
        .single()
        .subscribeOn(Schedulers.boundedElastic())
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() -> {
          verify(javaMailSender, times(1)).send(sendMailCaptor.capture());
          MimeMessage sendMail = sendMailCaptor.getValue();
          assertThat(sendMail).isNotNull();
          assertThat(sendMail.getSubject()).isEqualTo(BOOKING_EVENT_UNAVAILABLE_SUBJECT);
        });
  }

  @Test
  void consumeBookingCancelledMessage() {

    int bookingId = 2;
    var message = new BookingCancelled();
    message.setEventId(1);
    message.setBooking(new BookingId(2, "test_user@test.com", "test_user"));

    kafkaSender.send(
            createSenderMono(Topics.BOOKING_CANCELLED, bookingId, message, clock, extractMDCIntoHeaders(tracer)))
        .single()
        .subscribeOn(Schedulers.boundedElastic())
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() -> {
          verify(javaMailSender, times(1)).send(sendMailCaptor.capture());
          MimeMessage sendMail = sendMailCaptor.getValue();
          assertThat(sendMail).isNotNull();
          assertThat(sendMail.getSubject()).isEqualTo(BOOKING_CANCELLED_MESSAGE_SUBJECT);
        });
  }

  @Test
  void consumeBookingsCancelledMessage() {
    var eventId = 1;
    var bookingIds = List.of(
        new BookingId(1, "test_user1@test.com", "test_user1"),
        new BookingId(2, "test_user2@test.com", "test_user2"),
        new BookingId(3, "test_user3@test.com", "test_user3")
    );
    var message = new BookingsCancelled(bookingIds, eventId, "Cancellation Message");

    kafkaSender.send(
            createSenderMono(Topics.BOOKINGS_CANCELLED, eventId, message, clock, extractMDCIntoHeaders(tracer)))
        .single()
        .subscribeOn(Schedulers.boundedElastic())
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() -> verify(javaMailSender, times(3)).send(any(MimeMessage.class)));
  }

  @Test
  void consumeBookingsUpdatedMessage() {
    var eventId = 1;
    var bookingIds = List.of(
        new BookingId(1, "test_user1@test.com", "test_user1"),
        new BookingId(2, "test_user2@test.com", "test_user2"),
        new BookingId(3, "test_user3@test.com", "test_user3")
    );
    var message = new BookingsUpdated(bookingIds, eventId, "Update Message");

    kafkaSender.send(
            createSenderMono(Topics.BOOKINGS_UPDATED, eventId, message, clock, extractMDCIntoHeaders(tracer)))
        .single()
        .subscribeOn(Schedulers.boundedElastic())
        .block(timeoutDuration);

    Awaitility.await().atMost(timeoutDuration.multipliedBy(10))
        .untilAsserted(() -> verify(javaMailSender, times(3)).send(any(MimeMessage.class)));

  }

  //endregion Tests

  //region Helpers

  private Mono<ConsumerRecord<Integer, Object>> getReceiverAsMono(
      KafkaReceiver<Integer, Object> receiver) {
    return receiver.receiveAtmostOnce()
        .single();
  }

  //endregion Helpers

  @TestConfiguration
  @ActiveProfiles({"test", "test_kafka"})
  static class TestConfig {

    @Bean
    public SchemaRegistryClient schemaRegistryClient(KafkaProperties kafkaProperties) {
      String registryUrl = kafkaProperties.getProperties().get("schema.registry.url");
      var scope = MockSchemaRegistry.validateAndMaybeGetMockScope(List.of(registryUrl));
      return MockSchemaRegistry.getClientForScope(scope);
    }

    @Bean
    @Primary
    public JavaMailSender mailSender() {
      return javaMailSender;
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

}
