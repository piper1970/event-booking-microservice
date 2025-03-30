package piper1970.bookingservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.topics.Topics;

@Tag("integration-test")
class KafkaMessagePostingServiceTest extends AbstractKafkaTest {

  @Autowired
  private KafkaMessagePostingService kafkaMessagePostingService;

  @Autowired
  private ConsumerFactory<Integer, Object> consumerFactory;

  // mock consumer to test message-posting-service logic
  private Consumer<Integer, Object> testConsumer;

  @BeforeEach
  void setUp() {
    testConsumer = consumerFactory.createConsumer();
    testConsumer.subscribe(List.of(Topics.BOOKING_CREATED,
        Topics.BOOKING_CANCELLED, Topics.BOOKINGS_UPDATED, Topics.BOOKINGS_CANCELLED));
  }

  @AfterEach
  void tearDown() {
    testConsumer.close();
  }

  @Test
  void postBookingCreatedMessage() {

    var message = new BookingCreated();
    var bookingID = new BookingId();
    bookingID.setId(1);
    bookingID.setUsername("test_user");
    bookingID.setEmail("test_user@test.com");
    message.setBooking(bookingID);
    message.setEventId(1);
    kafkaMessagePostingService.postBookingCreatedMessage(message);

    var consumed = KafkaTestUtils.getSingleRecord(testConsumer, Topics.BOOKING_CREATED,
        Duration.ofMillis(3500));

    assertThat(consumed).isNotNull();
    assertThat(consumed.key()).isEqualTo(1);
    assertThat(consumed.value()).isEqualTo(message);

  }

  @Test
  void postBookingCancelledMessage() {
    var message = new BookingCancelled();
    var bookingID = new BookingId();
    bookingID.setId(1);
    bookingID.setUsername("test_user");
    bookingID.setEmail("test_user@test.com");
    message.setBooking(bookingID);
    message.setEventId(1);
    kafkaMessagePostingService.postBookingCancelledMessage(message);

    var consumed = KafkaTestUtils.getSingleRecord(testConsumer, Topics.BOOKING_CANCELLED,
        Duration.ofMillis(3500));

    assertThat(consumed).isNotNull();
    assertThat(consumed.key()).isEqualTo(1);
    assertThat(consumed.value()).isEqualTo(message);
  }

  @Test
  void postBookingsUpdatedMessage() {
    var message = new BookingsUpdated();
    var bookingID = new BookingId();
    bookingID.setId(1);
    bookingID.setUsername("test_user");
    bookingID.setEmail("test_user@test.com");
    message.setBookings(List.of(bookingID));
    message.setEventId(1);
    kafkaMessagePostingService.postBookingsUpdatedMessage(message);

    var consumed = KafkaTestUtils.getSingleRecord(testConsumer, Topics.BOOKINGS_UPDATED,
        Duration.ofMillis(3500));

    assertThat(consumed).isNotNull();
    assertThat(consumed.key()).isEqualTo(1);
    assertThat(consumed.value()).isEqualTo(message);
  }

  @Test
  void postBookingsCancelledMessage() {
    var message = new BookingsCancelled();
    var bookingID = new BookingId();
    bookingID.setId(1);
    bookingID.setUsername("test_user");
    bookingID.setEmail("test_user@test.com");
    message.setBookings(List.of(bookingID));
    message.setEventId(1);
    kafkaMessagePostingService.postBookingsCancelledMessage(message);

    var consumed = KafkaTestUtils.getSingleRecord(testConsumer, Topics.BOOKINGS_CANCELLED,
        Duration.ofMillis(3500));

    assertThat(consumed).isNotNull();
    assertThat(consumed.key()).isEqualTo(1);
    assertThat(consumed.value()).isEqualTo(message);
  }


}