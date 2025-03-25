package piper1970.notificationservice.service;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.io.Resources;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.topics.Topics;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaMessageConsumingService implements MessageConsumingService {

  private static final String BOOKING_CANCELLED_MESSAGE_SUBJECT = "RE: Booking has been cancelled";
  public static final String BOOKING_EVENT_UNAVAILABLE_SUBJECT = "RE: Booking event is no longer available";
  public static final String BOOKING_HAS_BEEN_CREATED_SUBJECT = "Booking has been created";
  public static final String BOOKING_HAS_BEEN_UPDATED_SUBJECT = "Booking has been updated";

  private final BookingConfirmationRepository bookingConfirmationRepository;
  private final MustacheFactory mustacheFactory;
  private final Clock clock;
  private final JavaMailSender mailSender;

  @Value("${mustache.location:classpath:/templates}")
  private String mustacheLocation;

  @Value("${confirmation.url:http://localhost:8084/api/notifications/confirm}")
  private String confirmationUrl;

  @Value("${mail.message.from}")
  private String fromAddress;

  @Value("${confirmation.duration.minutes:30}")
  private Integer confirmationInMinutes;

  @Value("${events.api.address: http://localhost:8080/api/events}")
  private String eventsApiAddress;

  @Value("${bookings.api.address: http://localhost:8080/api/bookings}")
  private String bookingsApiAddress;

  //region Kafka Consumers

  @Override
  @KafkaListener(topics = Topics.BOOKING_CREATED)
  public Mono<Void> consumeBookingCreatedMessage(BookingCreated message) {

    var confirmToken = UUID.randomUUID();
    var confirmLink = confirmationUrl + "/" + confirmToken;

    var bookingId = Objects.requireNonNull(message.getBooking());
    BookingCreatedMessage props = BookingCreatedMessage.of(
        bookingId.getUsername().toString(), buildBookingLink(bookingId.getId()),
        buildEventLink(message.getEventId()),
        confirmLink
    );

    BiFunction<Mustache, StringWriter, String> mustacheHandler = (mustache, writer) -> {
      mustache.execute(writer, props);
      return writer.toString();
    };

    var formattedEmail = executeMustache(BookingCreatedMessage.template(), mustacheHandler);

//    sendMail(bookingId.getEmail().toString(), BOOKING_HAS_BEEN_CREATED_SUBJECT, formattedEmail);

    logMailDelivery(bookingId.getEmail(), formattedEmail);

    var dbConfirmation = BookingConfirmation.builder()
        .bookingId(bookingId.getId())
        .eventId(message.getEventId())
        .confirmationString(confirmToken)
        .bookingEmail(bookingId.getEmail().toString())
        .bookingUser(bookingId.getUsername().toString())
        .confirmationDateTime(LocalDateTime.now(clock))
        .durationInMinutes(confirmationInMinutes)
        .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
        .build();

    // needs to set up timeout logic
    return bookingConfirmationRepository.save(dbConfirmation)
        .doOnNext(confirmation -> log.info("Booking confirmation: {}", confirmation)).then();

  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_EVENT_UNAVAILABLE)
  public Mono<Void> consumeBookingEventUnavailableMessage(BookingEventUnavailable message) {

    var bookingId = Objects.requireNonNull(message.getBooking());
    final BookingEventUnavailableMessage props = BookingEventUnavailableMessage.of(
        bookingId.getUsername().toString(),
        buildBookingLink(bookingId.getId()), buildEventLink(message.getEventId()));

    BiFunction<Mustache, StringWriter, String> mustacheHandler = (mustache, writer) -> {
      mustache.execute(writer, props);
      return writer.toString();
    };

    var formattedEmail = executeMustache(BookingEventUnavailableMessage.template(),
        mustacheHandler);

//    sendMail(bookingId.getEmail().toString(), BOOKING_EVENT_UNAVAILABLE_SUBJECT,
//        formattedEmail);

    logMailDelivery(bookingId.getEmail(), formattedEmail);

    return Mono.empty();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_CANCELLED)
  public Mono<Void> consumeBookingCancelledMessage(BookingCancelled message) {

    var bookingId = Objects.requireNonNull(message.getBooking());
    final BookingCancelledMessage props = BookingCancelledMessage.of(
        bookingId.getUsername().toString(),
        buildBookingLink(bookingId.getId()),
        buildEventLink(message.getEventId())
    );

    var mustacheHandler = buildMustacheCancellationHandler(props);

    var formattedEmail = executeMustache(BookingCancelledMessage.template(), mustacheHandler);

//    sendMail(bookingId.getEmail().toString(), BOOKING_CANCELLED_MESSAGE_SUBJECT,
//        formattedEmail);

    logMailDelivery(bookingId.getEmail(), formattedEmail);

    return Mono.empty();

  }

  @Override
  @KafkaListener(topics = Topics.BOOKINGS_CANCELLED)
  public Mono<Void> consumeBookingsCancelledMessage(BookingsCancelled message) {

    //TODO: find more efficient way to send bulk deletes
    // to all the bookings
    var eventLink = buildEventLink(message.getEventId());
    message.getBookings()
        .forEach(bookingId -> {

          final BookingCancelledMessage props = BookingCancelledMessage.of(
              bookingId.getUsername().toString(),
              buildBookingLink(bookingId.getId()),
              eventLink
          );

          var mustacheHandler = buildMustacheCancellationHandler(props);

          var formattedEmail = executeMustache(BookingCancelledMessage.template(), mustacheHandler);

//          sendMail(bookingId.getEmail().toString(), BOOKING_HAS_BEEN_UPDATED_SUBJECT, formattedEmail);

          logMailDelivery(bookingId.getEmail(), formattedEmail);

        });
    return Mono.empty();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKINGS_UPDATED)
  public Mono<Void> consumeBookingsUpdatedMessage(BookingsUpdated message) {

    var eventLink = buildEventLink(message.getEventId());
    message.getBookings()
        .forEach(bookingId -> {

          final BookingUpdatedMessage props = BookingUpdatedMessage.of(
              buildBookingLink(bookingId.getId()),
              eventLink,
              bookingId.getUsername().toString(),
              message.getMessage().toString()
          );

          BiFunction<Mustache, StringWriter, String> mustacheHandler = (mustache, writer) -> {
            mustache.execute(writer, props);
            return writer.toString();
          };

          var formattedEmail = executeMustache(BookingUpdatedMessage.template(), mustacheHandler);

//          sendMail(bookingId.getEmail().toString(), BOOKING_CANCELLED_MESSAGE_SUBJECT, formattedEmail);

          // TODO: setup mailer logic
          // may need some bulk logic here
          // May need to add some parallelism

          logMailDelivery(bookingId.getEmail(), formattedEmail);
        });

    return Mono.empty();
  }

  //endregion Kafka Consumers

  //region Helper Methods

  private void logMailDelivery(CharSequence memberEmail, String formattedEmail) {
    log.info("Mail sent to {}:  {}", memberEmail, formattedEmail);
  }

  //region Mustache Compilation

  /// Functional helper method to compile and extract formatted message in resource-safe manner
  private String executeMustache(String resource,
      BiFunction<Mustache, StringWriter, String> mustacheHandler) {
    var fullPath = mustacheLocation + "/" + resource;
    try (var inputStream = Resources.getResource(fullPath).openStream();
        Reader reader = new InputStreamReader(inputStream);
        StringWriter writer = new StringWriter()
    ) {
      var mustache = mustacheFactory.compile(reader, resource);
      return mustacheHandler.apply(mustache, writer);
    } catch (IOException e) {
      var message = "Unable to load template: " + resource;
      throw new IllegalStateException(message, e);
    }
  }

  //endregion Mustache Compilation

  //region Helper Records

  record BookingUpdatedMessage(String bookingLink, String eventLink,
                               String username,
                               String message) {

    public static BookingUpdatedMessage of(String bookingLink, String eventLink,
        String username,
        String message) {
      return new BookingUpdatedMessage(bookingLink, eventLink, username, message);
    }

    public static String template() {
      return "booking-updated.mustache";
    }
  }

  record BookingCancelledMessage(String username, String bookingLink,
                                 String eventLink) {

    public static BookingCancelledMessage of(String username, String bookingLink,
        String eventLink) {
      return new BookingCancelledMessage(username, bookingLink, eventLink);
    }

    public static String template() {
      return "booking-cancelled.mustache";
    }
  }

  record BookingCreatedMessage(String username, String bookingLink,
                               String eventLink, String confirmationLink) {

    public static BookingCreatedMessage of(String username, String bookingLink, String eventLink,
        String confirmationLink) {
      return new BookingCreatedMessage(username, bookingLink, eventLink, confirmationLink);
    }

    public static String template() {
      return "booking-created.mustache";
    }
  }

  record BookingEventUnavailableMessage(String username, String bookingLink, String eventLink) {


    public static BookingEventUnavailableMessage of(String username, String bookingLink, String eventLink) {
      return new BookingEventUnavailableMessage(username, bookingLink, eventLink);
    }

    static String template() {
      return "booking-event-unavailable.mustache";
    }
  }

  //endregion Helper Records

  private void sendMail(String to, String subject, String body) {
    try {
      MimeMessage message = mailSender.createMimeMessage();

      message.setSubject(subject);
      MimeMessageHelper helper = new MimeMessageHelper(message, true);
      helper.setTo(to);
      helper.setText(body, true);
      helper.setFrom(fromAddress);

      mailSender.send(message);
    } catch (MessagingException e) {
      log.error("Unable to send email", e);
    }
  }

  private String buildBookingLink(Integer bookingId) {
    return bookingsApiAddress + "/" + bookingId;
  }

  private String buildEventLink(Integer eventId) {
    return eventsApiAddress + "/" + eventId;
  }

  private BiFunction<Mustache, StringWriter, String> buildMustacheCancellationHandler(BookingCancelledMessage props){
    return (mustache, writer) -> {
      mustache.execute(writer, props);
      return writer.toString();
    };
  }

  //endregion Helper Methods

}
