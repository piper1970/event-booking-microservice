package piper1970.notificationservice.kafka.listener;

import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import piper1970.eventservice.common.bookings.messages.BookingsUpdated;
import piper1970.eventservice.common.bookings.messages.types.BookingId;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.notificationservice.kafka.listener.options.BaseListenerOptions;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

/**
 * Kafka listener for bookings-updated topic. Sends out booking-updated email to every user booked for an event.
 * This usually happens if the owner of the event updates the event before it starts.
 */
@Component
@Slf4j
public class BookingsUpdatedListener extends AbstractListener {

  private static final String BOOKING_HAS_BEEN_UPDATED_SUBJECT = "RE: Booking has been updated";
  private final Retry defaultMailerRetry;
  private Disposable subscription;

  public BookingsUpdatedListener(BaseListenerOptions options,
      @Qualifier("mailer") Retry defaultMailerRetry) {
    super(options);
    this.defaultMailerRetry = defaultMailerRetry;
  }

  @Override
  protected String getTopic() {
    return Topics.BOOKINGS_UPDATED;
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

  record BookingUpdatedMessage(String bookingLink, String eventLink,
                               String username,
                               String message) {

    public static String template() {
      return "booking-updated.mustache";
    }
  }

  /**
   * Helper method to handle bookings updated unavailable messages.
   *
   * @param record ReceiverRecord containing BookingsUpdated message
   * @return a Mono[ReceiverRecord], optionally posting to DLT if problems occurred
   */
  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {

    log.debug("BookingsUpdatedListener::handleIndividualRequest started");

    if (record.value() instanceof BookingsUpdated message) {

      var eventLink = buildEventLink(message.getEventId());

      var bookingIds = Objects.requireNonNull(message.getBookings())
          .stream()
          .map(BookingId::getId)
          .map(Object::toString)
          .collect(Collectors.joining(","));
      log.info("Consuming from BOOKINGS_UPDATED topic for event [{}] and bookingIds [{}]",
          message.getEventId(),
          bookingIds);

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
          .doOnNext(tpl -> logMailDelivery(tpl.subject().toString(),
              tpl.body()))
          .flatMap(tpl ->
              handleMailFlux(tpl, BOOKING_HAS_BEEN_UPDATED_SUBJECT)
                  .retryWhen(defaultMailerRetry)
          ).then(Mono.just(record))
          .onErrorResume(error -> {
            log.error("BOOKINGS_UPDATED message handling failed. Sending to DLT", error);
            return handleDLTLogic(record);
          });
    } else {
      log.error(
          "Unable to deserialize BookingsUpdated message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}
