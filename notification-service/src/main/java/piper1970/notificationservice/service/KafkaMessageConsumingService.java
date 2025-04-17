package piper1970.notificationservice.service;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.io.Resources;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.topics.Topics;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
public class KafkaMessageConsumingService implements MessageConsumingService {

  // TODO: consider executor-service w/virtual threads for calls to mailSender/logger

  static final String BOOKING_CANCELLED_MESSAGE_SUBJECT = "RE: Booking has been cancelled";
  static final String BOOKING_EVENT_UNAVAILABLE_SUBJECT = "RE: Booking event is no longer available";
  static final String BOOKING_HAS_BEEN_CREATED_SUBJECT = "RE: Booking has been created";
  static final String BOOKING_HAS_BEEN_UPDATED_SUBJECT = "RE: Booking has been updated";

  private final BookingConfirmationRepository bookingConfirmationRepository;
  private final MustacheFactory mustacheFactory;
  private final Clock clock;
  private final JavaMailSender mailSender;
  private final Duration notificationTimeoutDuration;


  @Value("${mustache.location:templates}")
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

  public KafkaMessageConsumingService(BookingConfirmationRepository bookingConfirmationRepository,
      MustacheFactory mustacheFactory,
      Clock clock,
      JavaMailSender mailSender,
      @Value("${notification-repository.timout.milliseconds}") Long notificationRepositoryTimeoutInMilliseconds) {
    this.bookingConfirmationRepository = bookingConfirmationRepository;
    this.mustacheFactory = mustacheFactory;
    this.clock = clock;
    this.mailSender = mailSender;
    notificationTimeoutDuration = Duration.ofMillis(notificationRepositoryTimeoutInMilliseconds);
  }

  //region Kafka Consumers

  @Override
  @KafkaListener(topics = Topics.BOOKING_CREATED,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeBookingCreatedMessage(BookingCreated message) {

    var confirmToken = UUID.randomUUID();
    var confirmLink = confirmationUrl + "/" + confirmToken;
    log.debug("Consuming from BOOKING_CREATED topic. Confirm link created [{}]", confirmLink);

    var bookingId = Objects.requireNonNull(message.getBooking());
    BookingCreatedMessage props = new BookingCreatedMessage(
        bookingId.getUsername().toString(), buildBookingLink(bookingId.getId()),
        buildEventLink(message.getEventId()),
        confirmLink
    );

    var template = BookingCreatedMessage.template();
    var emailAddress = bookingId.getEmail();
    var sendEmailMono = readerMono(template, props)
        .doOnNext(email -> logMailDelivery(emailAddress, email))
        .flatMap(msg ->
            handleMailMono(emailAddress.toString(), BOOKING_HAS_BEEN_CREATED_SUBJECT, msg)
        );

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

    return bookingConfirmationRepository.save(dbConfirmation)
        .timeout(notificationTimeoutDuration)
        .onErrorResume(e -> {
          log.error(
              "Save of booking confirmation for confirmation string [{}] failed due to timeout. Manual adjustment of record may be necessary",
              confirmToken, e);
          return Mono.empty();
        })
        .doOnNext(confirmation -> log.info("Booking confirmation saved [{}]", confirmation))
        .then(sendEmailMono)
        .doOnError(
            err -> log.error("Booking confirmation/email sending failed [{}]", confirmToken, err));
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_EVENT_UNAVAILABLE,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeBookingEventUnavailableMessage(BookingEventUnavailable message) {

    var bookingId = Objects.requireNonNull(message.getBooking());
    log.debug("Consuming from BOOKING_EVENT_UNAVAILABLE topic for id [{}]", bookingId);

    final BookingEventUnavailableMessage props = new BookingEventUnavailableMessage(
        bookingId.getUsername().toString(),
        buildBookingLink(bookingId.getId()), buildEventLink(message.getEventId()));

    var template = BookingEventUnavailableMessage.template();
    return readerMono(template, props)
        .doOnNext(email -> logMailDelivery(bookingId.getEmail(), email))
        .flatMap(msg ->
            handleMailMono(bookingId.getEmail().toString(), BOOKING_EVENT_UNAVAILABLE_SUBJECT, msg)
        ).doOnError(error -> log.error("Booking event unavailable handling failed [{}]", bookingId, error));
  }

  @Override
  @KafkaListener(topics = Topics.BOOKING_CANCELLED,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeBookingCancelledMessage(BookingCancelled message) {

    var bookingId = Objects.requireNonNull(message.getBooking());
    log.debug("Consuming from BOOKING_CANCELLED topic for id [{}]", bookingId);

    final BookingCancelledMessage props = new BookingCancelledMessage(
        bookingId.getUsername().toString(),
        buildBookingLink(bookingId.getId()),
        buildEventLink(message.getEventId())
    );
    var template = BookingCancelledMessage.template();
    return readerMono(template, props)
        .doOnNext(email -> logMailDelivery(bookingId.getEmail(), email))
        .flatMap(msg ->
            handleMailMono(bookingId.getEmail().toString(), BOOKING_CANCELLED_MESSAGE_SUBJECT, msg)
        ).doOnError(error -> log.error("Booking cancelled handling failed [{}]", bookingId, error));
  }

  @Override
  @KafkaListener(topics = Topics.BOOKINGS_CANCELLED,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeBookingsCancelledMessage(BookingsCancelled message) {

    var eventLink = buildEventLink(message.getEventId());

    if (log.isDebugEnabled()) {
      var bookingIds = Objects.requireNonNull(message.getBookings())
          .stream()
          .map(BookingId::getId)
          .map(Object::toString)
          .collect(Collectors.joining(","));
      log.debug("Consuming from BOOKINGS_CANCELLED topic for event [{}] and bookingIds [{}]",
          message.getEventId(),
          bookingIds);
    }

    var props = message.getBookings().stream()
        .map(bookingId -> {
          var messages = new BookingCancelledMessage(
              bookingId.getUsername().toString(),
              buildBookingLink(bookingId.getId()),
              eventLink
          );
          return new PropsHolder(bookingId.getEmail().toString(), messages);
        });
    var template = BookingCancelledMessage.template();

    return readerFlux(template, props)
        .doOnNext(tpl -> logMailDelivery(tpl.subject().toString(), tpl.body()))
        .doOnError(t -> log.error("Error while reading from flux", t))
        .flatMap(tpl ->
            handleMailFlux(tpl, BOOKING_CANCELLED_MESSAGE_SUBJECT)
        ).then()
        .doOnError(error -> log.error("Bookings Cancelled Handling Failed", error));
  }

  @Override
  @KafkaListener(topics = Topics.BOOKINGS_UPDATED,
      errorHandler = "kafkaListenerErrorHandler")
  public Mono<Void> consumeBookingsUpdatedMessage(BookingsUpdated message) {

    var eventLink = buildEventLink(message.getEventId());

    if (log.isDebugEnabled()) {
      var bookingIds = Objects.requireNonNull(message.getBookings())
          .stream()
          .map(BookingId::getId)
          .map(Object::toString)
          .collect(Collectors.joining(","));
      log.debug("Consuming from BOOKINGS_UPDATED topic for event [{}] and bookingIds [{}]",
          message.getEventId(),
          bookingIds);
    }

    var props = message.getBookings().stream()
        .map(bookingId -> {
          var messages = new BookingUpdatedMessage(
              buildBookingLink(bookingId.getId()),
              eventLink,
              bookingId.getUsername().toString(),
              message.getMessage().toString()
          );
          return new PropsHolder(bookingId.getEmail().toString(), messages);
        });

    var template = BookingUpdatedMessage.template();
    return readerFlux(template, props)
        .doOnNext(tpl -> logMailDelivery(tpl.subject().toString(), tpl.body()))
        .flatMap(tpl ->
            handleMailFlux(tpl, BOOKING_HAS_BEEN_UPDATED_SUBJECT)
        ).then()
        .doOnError(error -> log.error("Bookings Updated Handling Failed", error));
  }

  //endregion Kafka Consumers

  //region Helper Methods

  /// Helper method to box-then-unbox from flux-mono-flux, to allow for using the fromRunnable logic
  /// in deferring blocking sendMail logic
  private Flux<Object> handleMailFlux(EmailTemplate tpl, String topic) {
    return Flux.defer(() -> Mono.fromRunnable(() -> sendMail(tpl.subject().toString(), topic,
                tpl.body()))
            .flux())
        .publishOn(Schedulers.boundedElastic());
  }

  private Mono<Void> handleMailMono(String email, String subject, String body) {
    return Mono.fromRunnable(() -> sendMail(email, subject, body))
        .publishOn(Schedulers.boundedElastic())
        .then();
  }

  private void logMailDelivery(CharSequence memberEmail, String formattedEmail) {
    log.info("Mail sent to {}:  {}", memberEmail, formattedEmail);
  }

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


  /// Provides flux setup for proper auto-close behavior of Reader resource Used by Flux.using() as
  /// the initial supplier parameter
  private Flux<EmailTemplate> readerFlux(String template, Stream<PropsHolder> propHolders) {
    return Flux.using(readerSupplier(template),
        reader -> buildEmailsFromMustacheAsFlux(reader, template, propHolders)
    );
  }

  /// Provides mono setup for proper auto-close behavior of Reader resource
  private Mono<String> readerMono(String template, Object props) {
    return Mono.using(readerSupplier(template),
        reader -> buildEmailFromMustacheAsMono(reader, template, props)
    );
  }

  /// Used as first supplier parameter by Mono.using() and Flux.using()
  private Callable<Reader> readerSupplier(String resource) {
    var fullPath = mustacheLocation + "/" + resource;
    return () -> new InputStreamReader(Resources.getResource(fullPath).openStream());
  }

  private String buildEmailFromMustache(Mustache mustache, Object prop) {
    StringWriter writer = new StringWriter();
    mustache.execute(writer, prop);
    return writer.toString();
  }

  /// Used as second parameter to Mono.using() to build Mustache templating logic from Reader
  /// resource
  private Mono<String> buildEmailFromMustacheAsMono(Reader reader, String template, Object props) {
    return Mono.fromCallable(() -> {
      Mustache mustache = mustacheFactory.compile(reader, template);
      return buildEmailFromMustache(mustache, props);
    }).publishOn(Schedulers.boundedElastic());
  }

  /// Used as second parameter to Flux.using() to build Mustache templating logic from Reader
  /// resource
  private Flux<EmailTemplate> buildEmailsFromMustacheAsFlux(Reader reader, String template,
      Stream<PropsHolder> propsHolderStream) {
    Mustache mustache = mustacheFactory.compile(reader, template);
    return Flux.fromStream(propsHolderStream)
        .map(propsHolder -> {
              var msg = buildEmailFromMustache(mustache, propsHolder.props());
              return new EmailTemplate(propsHolder.email(), msg);
            }
        ).publishOn(Schedulers.boundedElastic());
  }

  //endregion Helper Methods

  //region Helper Records

  private record EmailTemplate(Object subject, String body) {

  }

  private record PropsHolder(String email, Object props) {

  }

  record BookingUpdatedMessage(String bookingLink, String eventLink,
                               String username,
                               String message) {

    public static String template() {
      return "booking-updated.mustache";
    }
  }

  record BookingCancelledMessage(String username, String bookingLink,
                                 String eventLink) {

    public static String template() {
      return "booking-cancelled.mustache";
    }
  }

  record BookingCreatedMessage(String username, String bookingLink,
                               String eventLink, String confirmationLink) {

    public static String template() {
      return "booking-created.mustache";
    }
  }

  record BookingEventUnavailableMessage(String username, String bookingLink, String eventLink) {

    static String template() {
      return "booking-event-unavailable.mustache";
    }
  }

  //endregion Helper Records

}
