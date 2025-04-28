package piper1970.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static piper1970.notificationservice.kafka.listener.BookingCancelledListener.BOOKING_CANCELLED_MESSAGE_SUBJECT;
import static piper1970.notificationservice.kafka.listener.BookingCreatedListener.BOOKING_HAS_BEEN_CREATED_SUBJECT;
import static piper1970.notificationservice.kafka.listener.BookingEventUnavailableListener.BOOKING_EVENT_UNAVAILABLE_SUBJECT;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.List;
import org.apache.hc.core5.util.Timeout;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.repository.BookingConfirmationRepository;

@Tags({
    @Tag("integration-test"),
    @Tag("kafka-test")
})
@DisplayName("Notification-Service: KafkaMessageConsumingService IT")
@ExtendWith(MockitoExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1)
@ActiveProfiles({"test", "integration_kafka"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaMessageConsumingServiceTestIT{

  //region Properties Used

  protected static JavaMailSender javaMailSender = mock(JavaMailSender.class);

  private final Duration timeoutDuration = Duration.ofSeconds(6);

  @Autowired
  BookingConfirmationRepository repository;

  @Autowired
  ProducerFactory<Integer, Object> producerFactory;

  // mock producer to test kafka-listener logic
  Producer<Integer, Object> testProducer;

  @Captor
  ArgumentCaptor<MimeMessage> sendMailCaptor;

  //endregion Properties Used

  //region Before/After

  @BeforeEach
  void setUp() {
    testProducer = producerFactory.createProducer();

    when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session)null));
    doNothing().when(javaMailSender).send(any(MimeMessage.class));

    // clear database
    repository.deleteAll().block();
  }

  @AfterEach
  void tearDown() {
    testProducer.close();
    Mockito.reset(javaMailSender);
  }

  //endregion Before/After

  //region Tests

  @Test
  void consumeBookingCreatedMessage() throws InterruptedException, MessagingException {

    var message = new BookingCreated();
    var eventId = 1;
    var bookingId = 2;
    var email = "test_user@test.com";
    var username = "test_user";
    message.setEventId(eventId);
    message.setBooking(new BookingId(bookingId, email, username));

    testProducer.send(new ProducerRecord<>(Topics.BOOKING_CREATED, bookingId, message));

    Timeout.of(timeoutDuration).sleep();

    repository.findAll().single()
        .blockOptional()
        .ifPresentOrElse(cf -> {
          assertThat(cf.getBookingEmail()).isEqualTo(email);
          assertThat(cf.getBookingUser()).isEqualTo(username);
          assertThat(cf.getBookingId()).isEqualTo(bookingId);
          assertThat(cf.getEventId()).isEqualTo(eventId);
          assertThat(cf.getConfirmationStatus()).isEqualTo(ConfirmationStatus.AWAITING_CONFIRMATION);
          assertThat(cf.getConfirmationString()).isNotNull();
        }, () -> fail("No record found"));

    verify(javaMailSender, times(1)).send(sendMailCaptor.capture());
    MimeMessage sendMail = sendMailCaptor.getValue();
    assertThat(sendMail).isNotNull();
    assertThat(sendMail.getSubject()).isEqualTo(BOOKING_HAS_BEEN_CREATED_SUBJECT);
  }

  @Test
  void consumeBookingEventUnavailableMessage(){

    int bookingId = 2;
    var message = new BookingEventUnavailable();
    message.setEventId(1);
    message.setBooking(new BookingId(2, "test_user@test.com", "test_user"));

    testProducer.send(new ProducerRecord<>(Topics.BOOKING_EVENT_UNAVAILABLE, bookingId, message));

    Awaitility.await().atMost(timeoutDuration).untilAsserted(() -> {
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

    testProducer.send(new ProducerRecord<>(Topics.BOOKING_CANCELLED, bookingId, message));

    Awaitility.await().atMost(timeoutDuration).untilAsserted(() -> {
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

    testProducer.send(new ProducerRecord<>(Topics.BOOKINGS_CANCELLED, eventId, message));

    Awaitility.await().atMost(timeoutDuration).untilAsserted(() -> verify(javaMailSender, times(3)).send(any(MimeMessage.class)));
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

    testProducer.send(new ProducerRecord<>(Topics.BOOKINGS_UPDATED, eventId, message));

    Awaitility.await().atMost(timeoutDuration).untilAsserted(() -> verify(javaMailSender, times(3)).send(any(MimeMessage.class)));
  }

  //endregion Tests

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

    @Bean
    public JavaMailSender mailSender() {
      return javaMailSender;
    }
  }

  //endregion Configuration

}