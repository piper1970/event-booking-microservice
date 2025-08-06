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
import piper1970.eventservice.common.events.messages.BookingEventUnavailable;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.DiscoverableListener;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

@Component
@Slf4j
public class BookingEventUnavailableListener extends DiscoverableListener {

  private final BookingRepository bookingRepository;
  private final Duration timeoutDuration;
  private final Retry defaultRepositoryRetry;
  private Disposable subscription;

  public BookingEventUnavailableListener(
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
    return Topics.BOOKING_EVENT_UNAVAILABLE;
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  /**
   * Helper method to handle event-unavailable messages.
   *
   * @param record ReceiverRecord containing BookingEventUnavailable message
   * @return a Mono[ReceiverRecord], optionally posting to DLT if problems occurred
   */
  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {

    log.debug("BookingEventUnavailableListener::handleIndividualRequest started");

    if (record.value() instanceof BookingEventUnavailable message) {
      return bookingRepository.findById(message.getBooking().getId())
          .subscribeOn(Schedulers.boundedElastic())
          .timeout(timeoutDuration)
          .retryWhen(defaultRepositoryRetry)
          .filter(booking -> BookingStatus.IN_PROGRESS == booking.getBookingStatus()
              || BookingStatus.CONFIRMED == booking.getBookingStatus())
          .flatMap(booking -> bookingRepository.save(
                  booking.withBookingStatus(BookingStatus.CANCELLED))
              .subscribeOn(Schedulers.boundedElastic())
              .timeout(timeoutDuration)
              .retryWhen(defaultRepositoryRetry)
              .doOnNext(updatedBooking ->
                  log.warn("Booking [{}] for event [{}] has been cancelled due to unavailability",
                      updatedBooking.getId(),
                      updatedBooking.getEventId()))
              .map(updatedBooking -> record)
          )
          .onErrorResume(err -> {
            log.error("BookingEventUnavailable message not handled after max attempts. Sending to DLT",
                err);
            return handleDLTLogic(record);
          });
    } else {
      log.error("Unable to deserialize BookingEventUnavailable message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}
