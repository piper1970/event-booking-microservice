package piper1970.notificationservice.service;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
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

  private final BookingConfirmationRepository bookingConfirmationRepository;
  private final MustacheFactory mustacheFactory;
  private final Clock clock;

  @Value("${mustache.location:classpath:/templates}")
  private String mustacheLocation;

  @Value("${confirmation.url:http://localhost:8084/api/notifications/confirm}")
  private String confirmationUrl;

  @Value("${confirmation.duration.minutes:30}")
  private Integer confirmationInMinutes;

  @Override
  @KafkaListener(topics = Topics.BOOKING_CREATED)
  public Mono<Void> consumeBookingCreatedMessage(BookingCreated message) {

    var confirmToken = UUID.randomUUID();
    var confirmLink = confirmationUrl + "/" + confirmToken;

    // Generate UUID
    // TODO: Need to email a 'booking-created' email
    BookingCreatedMessage props = BookingCreatedMessage.of(
        message.getMemberUsername().toString(), message.getBookingId(),
        message.getEventId(),
        confirmLink
    );

    BiFunction<Mustache, StringWriter, String> mustacheHandler = (mustache, writer) -> {
      mustache.execute(writer, props);
      return writer.toString();
    };

    var formattedEmail = executeMustache(BookingCreatedMessage.template(), mustacheHandler);

    // TODO: setup mailer logic

    logMailDelivery(message.getMemberEmail(), formattedEmail);

    var dbConfirmation = BookingConfirmation.builder()
        .bookingId(message.getBookingId())
        .eventId(message.getEventId())
        .confirmationString(confirmToken)
        .confirmationDateTime(LocalDateTime.now(clock))
        .confirmationDuration(Duration.ofMinutes(confirmationInMinutes))
        .confirmationStatus(ConfirmationStatus.AWAITING_CONFIRMATION)
        .build();

    // needs to set up timeout logic
    return bookingConfirmationRepository.save(dbConfirmation)
        .doOnNext(confirmation -> log.info("Booking confirmation: {}", confirmation)).then();

  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_EVENT_UNAVAILABLE)
  public Mono<Void> consumeBookingEventUnavailableMessage(BookingEventUnavailable message) {

    final BookingEventUnavailableMessage props = BookingEventUnavailableMessage.of(message.getMemberUsername(),
        message.getBookingId(), message.getEventId());

    BiFunction<Mustache, StringWriter, String> mustacheHandler = (mustache, writer) -> {
      mustache.execute(writer, props);
      return writer.toString();
    };

    var formattedEmail = executeMustache(BookingEventUnavailableMessage.template(), mustacheHandler);

    // TODO: setup mailer logic

    logMailDelivery(message.getMemberEmail(), formattedEmail);

    return Mono.empty();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_CANCELLED)
  public Mono<Void> consumeBookingCancelledMessage(BookingCancelled message) {

    final BookingCancelledMessage props = BookingCancelledMessage.of(
        message.getMemberUsername(),
        message.getBookingId(),
        message.getEventId()
    );

    BiFunction<Mustache, StringWriter, String> mustacheHandler = (mustache, writer) -> {
      mustache.execute(writer, props);
      return writer.toString();
    };

    var formattedEmail = executeMustache(BookingCancelledMessage.template(), mustacheHandler);

    // TODO: setup mailer logic

    logMailDelivery(message.getMemberEmail(), formattedEmail);

    return Mono.empty();

  }

  @Override
  @KafkaListener(topics = Topics.BOOKINGS_CANCELLED)
  public Mono<Void> consumeBookingsCancelledMessage(BookingsCancelled message) {

    message.getBookingIds()
        .forEach(bookingId -> {

          final BookingCancelledMessage props = BookingCancelledMessage.of(
              message.getMemberUsername(),
              bookingId,
              message.getEventId()
          );

          BiFunction<Mustache, StringWriter, String> mustacheHandler = (mustache, writer) -> {
            mustache.execute(writer, props);
            return writer.toString();
          };

          var formattedEmail = executeMustache(BookingCancelledMessage.template(), mustacheHandler);

          // TODO: setup mailer logic
          // TODO: may need some bulk logic here

          logMailDelivery(message.getMemberEmail(), formattedEmail);

        });
    return Mono.empty();
  }

  @Override
  @KafkaListener(topics = Topics.BOOKINGS_UPDATED)
  public Mono<Void> consumeBookingsUpdatedMessage(BookingsUpdated message) {

    message.getBookingIds()
        .forEach(bookingId -> {
          final BookingUpdatedMessage props = BookingUpdatedMessage.of(
              bookingId,
              message.getEventId(),
              message.getMemberUsername(),
              message.getMessage()
          );

          BiFunction<Mustache, StringWriter, String> mustacheHandler = (mustache, writer) -> {
            mustache.execute(writer, props);
            return writer.toString();
          };

          var formattedEmail = executeMustache(BookingUpdatedMessage.template(), mustacheHandler);

          // TODO: setup mailer logic
          // may need some bulk logic here
          // May need to add some parallelism

          logMailDelivery(message.getMemberEmail(), formattedEmail);
        });

    return Mono.empty();
  }

  private void logMailDelivery(CharSequence memberEmail, String formattedEmail) {
    log.info("Mail sent to {}:  {}", memberEmail, formattedEmail);
  }

  //region Mustache Compilation

  /// Functional helper method to compile and extract formatted message in resource-safe manner
  private String executeMustache(String resource, BiFunction<Mustache, StringWriter, String> mustacheHandler){
    var fullPath = mustacheLocation + "/" + resource;
    try(var inputStream = Resources.getResource(fullPath).openStream();
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

  record BookingUpdatedMessage(Integer bookingId, Integer eventId,
                               CharSequence username,
                               CharSequence message) {
    public static BookingUpdatedMessage of(Integer bookingId, Integer eventId,
        CharSequence username,
        CharSequence message) {
      return new BookingUpdatedMessage(bookingId, eventId, username, message);
    }

    public static String template(){
      return "booking-updated.mustache";
    }
  }

  record BookingCancelledMessage(CharSequence username, Integer bookingId,
                                 Integer eventId) {
    public static BookingCancelledMessage of(CharSequence username, Integer bookingId,
        Integer eventId){
      return new BookingCancelledMessage(username, bookingId, eventId);
    }

    public static String template(){
      return "booking-cancelled.mustache";
    }
  }

  record BookingCreatedMessage(CharSequence username, Integer bookingId,
                               Integer eventId, String confirmationLink){
    public static BookingCreatedMessage of(CharSequence username, Integer bookingId, Integer eventId, String confirmationLink){
      return new BookingCreatedMessage(username, bookingId, eventId, confirmationLink);
    }

    public static String template(){
      return "booking-created.mustache";
    }
  }

  record BookingEventUnavailableMessage(CharSequence username, Integer bookingId,
                                        Integer eventId){
    public static BookingEventUnavailableMessage of(CharSequence username, Integer bookingId, Integer eventId){
      return new BookingEventUnavailableMessage(username, bookingId, eventId);
    }

    static String template(){
      return "booking-event-unavailable.mustache";
    }
  }

  //endregion Helper Records

}
