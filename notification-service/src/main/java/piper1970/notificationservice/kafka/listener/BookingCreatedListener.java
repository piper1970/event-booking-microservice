package piper1970.notificationservice.kafka.listener;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import piper1970.eventservice.common.bookings.messages.BookingCreated;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.notificationservice.domain.BookingConfirmation;
import piper1970.notificationservice.domain.ConfirmationStatus;
import piper1970.notificationservice.kafka.listener.options.BaseListenerOptions;
import piper1970.notificationservice.repository.BookingConfirmationRepository;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

@Component
@Slf4j
public class BookingCreatedListener extends AbstractListener {

  public static final String BOOKING_HAS_BEEN_CREATED_SUBJECT = "RE: Booking has been created";
  private final BookingConfirmationRepository bookingConfirmationRepository;
  private final String confirmationUrl;
  private final Integer confirmationInMinutes;
  private final Duration notificationTimeoutDuration;
  private final Clock clock;
  private final TransactionalOperator transactionalOperator;
  private final Retry defaultRepositoryRetry;

  private Disposable subscription;

  public BookingCreatedListener(BaseListenerOptions options,
      BookingConfirmationRepository bookingConfirmationRepository,
      TransactionalOperator transactionalOperator,
      Clock clock,
      @Value("${notification-repository.timeout.milliseconds}") Long notificationRepositoryTimeoutInMilliseconds,
      @Value("${confirmation.url:http://localhost:8084/api/notifications/confirm}") String confirmationUrl,
      @Value("${confirmation.duration.minutes:30}") Integer confirmationInMinutes,
      @Qualifier("repository") Retry defaultRepositoryRetry
      ) {
    super(options);
    this.bookingConfirmationRepository = bookingConfirmationRepository;
    this.confirmationUrl = confirmationUrl;
    this.confirmationInMinutes = confirmationInMinutes;
    this.notificationTimeoutDuration = Duration.ofMinutes(notificationRepositoryTimeoutInMilliseconds);
    this.clock = clock;
    this.transactionalOperator = transactionalOperator;
    this.defaultRepositoryRetry = defaultRepositoryRetry;
  }

  @Override
  protected String getTopic() {
    return Topics.BOOKING_CREATED;
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  @Override
  protected Logger getLogger() {
    return log;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Override
  public void initializeReceiverFlux() {
    subscription = buildFluxRequest()
        .subscribe(rec -> rec.receiverOffset().acknowledge());
  }

  record BookingCreatedMessage(String username, String bookingLink,
                               String eventLink, String confirmationLink) {

    public static String template() {
      return "booking-created.mustache";
    }
  }

  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(ReceiverRecord<Integer, Object> record) {
    log.debug("BookingCreatedListener::handleIndividualRequest started");
    if(record.value() instanceof BookingCreated message) {
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
          .subscribeOn(Schedulers.boundedElastic())
          .timeout(notificationTimeoutDuration)
          .doOnNext(confirmation -> log.info("Booking confirmation saved [{}]", confirmation))
          .then(sendEmailMono)
          .as(transactionalOperator::transactional)
          .retryWhen(defaultRepositoryRetry)
          .then(Mono.just(record))
          .onErrorResume(error -> {
            log.error("BOOKING_CREATED message handling failed. Transaction rolled back and message sent to DLT", error);
            return handleDLTLogic(record);
          });
    }else{
      log.error("Unable to deserialize BookingCreated message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}
