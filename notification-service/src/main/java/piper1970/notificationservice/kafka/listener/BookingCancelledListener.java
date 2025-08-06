package piper1970.notificationservice.kafka.listener;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import piper1970.eventservice.common.bookings.messages.BookingCancelled;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.notificationservice.kafka.listener.options.BaseListenerOptions;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

@Component
@Slf4j
public class BookingCancelledListener extends AbstractListener {

  public static final String BOOKING_CANCELLED_MESSAGE_SUBJECT = "RE: Booking has been cancelled";
  private final Retry defaultMailerRetry;
  private Disposable subscription;

  public BookingCancelledListener(BaseListenerOptions options,
      @Qualifier("mailer") Retry defaultMailerRetry
  ) {
    super(options);
    this.defaultMailerRetry = defaultMailerRetry;
  }

  @Override
  protected String getTopic() {
    return Topics.BOOKING_CANCELLED;
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
   * Helper method to handle booking cancelled messages.
   *
   * @param record ReceiverRecord containing BookingCancelled message
   * @return a Mono[ReceiverRecord], optionally posting to DLT if problems occurred
   */
  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {

    log.debug("BookingCancelledListener::handleIndividualRequest started");

    if (record.value() instanceof BookingCancelled message) {

      var bookingId = Objects.requireNonNull(message.getBooking());
      log.info("Consuming from BOOKING_CANCELLED topic for id [{}]", bookingId);
      final BookingCancelledMessage props = new BookingCancelledMessage(
          bookingId.getUsername().toString(),
          buildBookingLink(bookingId.getId()),
          buildEventLink(message.getEventId())
      );
      var template = BookingCancelledMessage.template();
      return readerMono(template, props)
          .doOnNext(
              email -> logMailDelivery(bookingId.getEmail(), email))
          .flatMap(msg ->
              handleMailMono(bookingId.getEmail().toString(), BOOKING_CANCELLED_MESSAGE_SUBJECT,
                  msg)
                  .retryWhen(defaultMailerRetry)
                  .then(Mono.just(record))
          ).onErrorResume(error -> {
            log.error("BOOKING_CANCELLED handling failed [{}]. Sending to DLT", bookingId,
                error);
            return handleDLTLogic(record);
          });
    } else {
      log.error(
          "Unable to deserialize BookingCancelled message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}
