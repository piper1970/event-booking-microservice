package piper1970.bookingservice.kafka.listeners;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.DiscoverableListener;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import piper1970.eventservice.common.notifications.messages.BookingExpired;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

/**
 * Listener for BookingExpired messages from the 'booking-expired' topic.
 * <p>
 * These messages are sent via the notification-service in one of two possibilities:
 * <ul>
 *   <li>User attempts to click a confirmation link after the allotted time has expired</li>
 *   <li>A scheduled maintenance run of notification-service finds a stale confirmation that has expiree</li>
 * </ul>
 */
@Component
@Slf4j
public class BookingExpiredListener extends DiscoverableListener {

  private final BookingRepository bookingRepository;
  private final Duration timeoutDuration;
  private final Retry defaultRepositoryRetry;
  private Disposable subscription;

  public BookingExpiredListener(
      ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer,
      BookingRepository bookingRepository,
      @Value("${booking-repository.timout.milliseconds}") Long timeoutMillis,
      @Qualifier("repository") Retry defaultRepositoryRetry) {
    super(reactiveKafkaReceiverFactory, deadLetterTopicProducer);
    this.bookingRepository = bookingRepository;
    timeoutDuration = Duration.ofMillis(timeoutMillis);
    this.defaultRepositoryRetry = defaultRepositoryRetry;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Override
  public void initializeReceiverFlux() {
    subscription = buildFluxRequest()
        .subscribe(rcv -> rcv.receiverOffset().acknowledge());
  }

  @Override
  protected String getTopic() {
    return Topics.BOOKING_EXPIRED;
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  /**
   * Helper method to handle booking-expired messages.
   *
   * @param record ReceiverRecord containing BookingExpired message
   * @return a Mono[ReceiverRecord], optionally posting to DLT if problems occurred
   */
  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {

    log.debug("BookingExpiredListener::handleIndividualRequest started");

    if (record.value() instanceof BookingExpired message) {
      return bookingRepository.findById(message.getBooking().getId())
          .subscribeOn(Schedulers.boundedElastic())
          .timeout(timeoutDuration)
          .retryWhen(defaultRepositoryRetry)
          .filter(booking -> BookingStatus.IN_PROGRESS == booking.getBookingStatus())
          .flatMap(booking -> bookingRepository.save(
                  booking.withBookingStatus(BookingStatus.CANCELLED))
              .subscribeOn(Schedulers.boundedElastic())
              .timeout(timeoutDuration)
              .retryWhen(defaultRepositoryRetry)
              .doOnNext(updatedBooking ->
                  log.info("Booking cancelled due to expired confirmation: {}",
                      updatedBooking)
              ).map(updatedBooking -> record)

          )
          .onErrorResume(err -> {
            log.error("BookingExpired message not handled after max attempts. Sending to DLT",
                err);
            return handleDLTLogic(record);
          });
    } else {
      log.error("Unable to deserialize BookingExpired message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}
