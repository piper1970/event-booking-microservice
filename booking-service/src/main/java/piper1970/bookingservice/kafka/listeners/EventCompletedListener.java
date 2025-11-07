package piper1970.bookingservice.kafka.listeners;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import piper1970.bookingservice.domain.BookingStatus;
import piper1970.bookingservice.repository.BookingRepository;
import piper1970.eventservice.common.events.messages.EventCompleted;
import piper1970.eventservice.common.kafka.reactive.DeadLetterTopicProducer;
import piper1970.eventservice.common.kafka.reactive.DiscoverableListener;
import piper1970.eventservice.common.kafka.reactive.ReactiveKafkaReceiverFactory;
import piper1970.eventservice.common.kafka.topics.Topics;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

/**
 * Listener for EventCompleted messages from the 'event-completed' topic.
 * <p>
 * These messages are sent periodically by the event-service when it determines an event has finished.
 */
@Component
@Slf4j
public class EventCompletedListener extends DiscoverableListener {

  private final BookingRepository bookingRepository;
  private final Duration timeoutDuration;
  private final Retry defaultRepositoryRetry;
  private Disposable subscription;

  public EventCompletedListener(
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
    return Topics.EVENT_COMPLETED;
  }

  @Override
  protected Disposable getSubscription() {
    return subscription;
  }

  /**
   * Helper method to handle event-completed messages.
   *
   * @param record ReceiverRecord containing EventCompleted message
   * @return a Mono[ReceiverRecord], optionally posting to DLT if problems occurred
   */
  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {

    log.debug("EventCompletedListener::handleIndividualRequest started");

    if (record.value() instanceof EventCompleted message) {
      var eventId = message.getEventId();

      log.info("[{}] message has been received from EVENT_COMPLETED topic. Updating related bookings",
          eventId);

      // For handling bookings that have already been confirmed -> status changes to complete
      var completedFlux = bookingRepository.findBookingsByEventIdAndBookingStatusIn(eventId,
              List.of(BookingStatus.CONFIRMED))
          .subscribeOn(Schedulers.boundedElastic())
          .timeout(timeoutDuration)
          .retryWhen(defaultRepositoryRetry)
          .map(booking -> booking.withBookingStatus(BookingStatus.COMPLETED));

      // For handling bookings that have not yet been confirmed -> status changes to cancelled
      var cancelledFlux = bookingRepository.findBookingsByEventIdAndBookingStatusIn(eventId,
              List.of(BookingStatus.IN_PROGRESS))
          .subscribeOn(Schedulers.boundedElastic())
          .timeout(timeoutDuration)
          .retryWhen(defaultRepositoryRetry)
          .map(booking -> booking.withBookingStatus(BookingStatus.CANCELLED));

      return completedFlux
          .concatWith(cancelledFlux)
          // save both cancelled and completed booking
          .flatMap(booking ->
              bookingRepository.save(booking)
                  .subscribeOn(Schedulers.boundedElastic())
                  .timeout(timeoutDuration)
                  .retryWhen(defaultRepositoryRetry)
          ).count()
          .doOnNext(
              count -> log.info("{} Bookings updated for completed event {}", count, eventId))
          .map(cnt -> record)
          .onErrorResume(err -> {
            log.error("Unable to process EventCompleted message after max attempts. Sending to DLT",
                err);
            return handleDLTLogic(record);
          });

    } else {
      log.error("Unable to deserialize EventCompleted message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}
