package piper1970.notificationservice.kafka.listener;

import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import piper1970.eventservice.common.bookings.messages.BookingsCancelled;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.notificationservice.kafka.listener.options.BaseListenerOptions;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

/**
 * Kafka listener for bookings-cancelled topic. Sends out booking-cancelled email to every user booked for an event.
 * This usually happens if the owner of the event cancels the event.
 */
@Component
@Slf4j
public class BookingsCancelledListener extends AbstractListener {

  private static final String BOOKING_CANCELLED_MESSAGE_SUBJECT = "RE: Booking has been cancelled";
  private final Retry defaultMailerRetry;
  private Disposable subscription;

  public BookingsCancelledListener(BaseListenerOptions options,
      @Qualifier("mailer") Retry defaultMailerRetry) {
    super(options);
    this.defaultMailerRetry = defaultMailerRetry;
  }

  @Override
  protected String getTopic() {
    return Topics.BOOKINGS_CANCELLED;
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

  record BookingCancelledMessage(String username, String bookingLink,
                                 String eventLink) {

    public static String template() {
      return "booking-cancelled.mustache";
    }
  }

  /**
   * Helper method to handle bookings cancelled unavailable messages.
   *
   * @param record ReceiverRecord containing BookingsCancelled message
   * @return a Mono[ReceiverRecord], optionally posting to DLT if problems occurred
   */
  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {

    log.debug("BookingsCancelledListener::handleIndividualRequest started");

    if (record.value() instanceof BookingsCancelled message) {
      var eventLink = buildEventLink(message.getEventId());

      var bookingIds = Objects.requireNonNull(message.getBookings())
          .stream()
          .map(BookingId::getId)
          .map(Object::toString)
          .collect(Collectors.joining(","));

      log.info("Consuming from BOOKINGS_CANCELLED topic for event [{}] and bookingIds [{}]",
          message.getEventId(),
          bookingIds);

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
          .doOnNext(tpl -> logMailDelivery(tpl.subject().toString(),
              tpl.body()))
          .doOnError(t -> log.error("Error while reading from flux", t))
          .flatMap(tpl ->
              handleMailFlux(tpl, BOOKING_CANCELLED_MESSAGE_SUBJECT)
                  .retryWhen(defaultMailerRetry)
          ).then(Mono.just(record))
          .onErrorResume(error -> {
            log.error("BOOKINGS_CANCELLED message handling failed. Sending to DLT", error);
            return handleDLTLogic(record);
          });

    } else {
      log.error("Unable to deserialize BookingsCancelled message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}
