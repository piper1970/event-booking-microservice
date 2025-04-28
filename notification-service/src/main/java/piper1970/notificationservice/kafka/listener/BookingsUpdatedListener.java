package piper1970.notificationservice.kafka.listener;

import java.time.Duration;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
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

@Component
@Slf4j
public class BookingsUpdatedListener extends AbstractListener {

  private static final String BOOKING_HAS_BEEN_UPDATED_SUBJECT = "RE: Booking has been updated";
  private Disposable subscription;

  public BookingsUpdatedListener(BaseListenerOptions options) {
    super(options);
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

  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {
    if (record.value() instanceof BookingsUpdated message) {

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
          ).retryWhen(Retry.backoff(3L, Duration.ofMillis(500L))
              .jitter(0.7D)
          ).then(Mono.just(record))
          .onErrorResume(error -> {
            log.error("BOOKINGS_UPDATED message handling failed. Sending to DLT", error);
            return handleDLTLogic(record);
          });
    } else {
      log.error(
          "Unable to unmarshal BookingsUpdated message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}
