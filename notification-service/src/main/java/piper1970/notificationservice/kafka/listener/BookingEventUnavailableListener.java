package piper1970.notificationservice.kafka.listener;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.notificationservice.kafka.listener.options.BaseListenerOptions;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

/**
 * Kafka listener for booking-event-unavailable topic. Sends out booking-event-unavailable email to user.
 * This message usually shows up when the available-bookings (integer) amount for the event drops to zero
 * before the user confirms the booking.
 */
@Component
@Slf4j
public class BookingEventUnavailableListener extends AbstractListener {

  public static final String BOOKING_EVENT_UNAVAILABLE_SUBJECT = "RE: Booking event is no longer available";
  private final Retry defaultMailerRetry;
  private Disposable subscription;

  public BookingEventUnavailableListener(BaseListenerOptions options,
      @Qualifier("mailer") Retry defaultMailerRetry) {
    super(options);
    this.defaultMailerRetry = defaultMailerRetry;
  }

  @Override
  protected String getTopic() {
    return Topics.BOOKING_EVENT_UNAVAILABLE;
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

  record BookingEventUnavailableMessage(String username, String bookingLink, String eventLink) {

    static String template() {
      return "booking-event-unavailable.mustache";
    }
  }

  /**
   * Helper method to handle booking event unavailable messages.
   *
   * @param record ReceiverRecord containing BookingEventUnavailable message
   * @return a Mono[ReceiverRecord], optionally posting to DLT if problems occurred
   */
  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {

    log.debug("BookingEventUnavailableListener::handleIndividualRequest started");

    if (record.value() instanceof BookingEventUnavailable message) {
      var bookingId = Objects.requireNonNull(message.getBooking());

      log.info("Consuming from BOOKING_EVENT_UNAVAILABLE topic for id [{}]", bookingId);

      final BookingEventUnavailableMessage props = new BookingEventUnavailableMessage(
          bookingId.getUsername().toString(),
          buildBookingLink(bookingId.getId()), buildEventLink(message.getEventId()));

      var template = BookingEventUnavailableMessage.template();
      return readerMono(template, props)
          .doOnNext(email -> logMailDelivery(bookingId.getEmail(),
              email))
          .flatMap(msg ->
              handleMailMono(bookingId.getEmail().toString(), BOOKING_EVENT_UNAVAILABLE_SUBJECT,
                  msg)
                  .retryWhen(defaultMailerRetry)
                  .then(Mono.just(record))
          ).onErrorResume(error -> {
            log.error("BOOKING_EVENT_UNAVAILABLE handling failed [{}]. Sending to DLT",
                bookingId,
                error);
            return handleDLTLogic(record);
          });
    } else {
      log.error("Unable to deserialize BookingEventUnavailable message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}
