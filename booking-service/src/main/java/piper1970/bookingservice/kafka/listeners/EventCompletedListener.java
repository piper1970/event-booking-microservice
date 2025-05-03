package piper1970.bookingservice.kafka.listeners;

import static piper1970.eventservice.common.kafka.KafkaHelper.DEFAULT_RETRY;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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

@Component
@Slf4j
public class EventCompletedListener extends DiscoverableListener {

  private final BookingRepository bookingRepository;
  private final Duration timeoutDuration;
  private Disposable subscription;

  public EventCompletedListener(
      ReactiveKafkaReceiverFactory reactiveKafkaReceiverFactory,
      DeadLetterTopicProducer deadLetterTopicProducer,
      BookingRepository bookingRepository,
      @Value("${booking-repository.timout.milliseconds}") Long timeoutMillis) {
    super(reactiveKafkaReceiverFactory, deadLetterTopicProducer);
    this.bookingRepository = bookingRepository;
    timeoutDuration = Duration.ofMillis(timeoutMillis);
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

  @Override
  protected Mono<ReceiverRecord<Integer, Object>> handleIndividualRequest(
      ReceiverRecord<Integer, Object> record) {
    log.debug("EventCompletedListener::handleIndividualRequest started");
    if (record.value() instanceof EventCompleted message) {
      var eventId = message.getEventId();
      log.debug(
          "[{}] message has been received from EVENT_COMPLETED topic. Updating related bookings",
          eventId);

      return bookingRepository.findBookingsByEventIdAndBookingStatusIn(eventId, List.of(
              BookingStatus.CANCELLED, BookingStatus.COMPLETED, BookingStatus.IN_PROGRESS
          ))
          .subscribeOn(Schedulers.boundedElastic())
          .log()
          .timeout(timeoutDuration)
          .retryWhen(DEFAULT_RETRY)
          .map(booking -> booking.withBookingStatus(BookingStatus.COMPLETED))
          .collectList()
          .flatMapMany(bookingList ->
              bookingRepository.saveAll(bookingList)
                  .subscribeOn(Schedulers.boundedElastic())
                  .log()
                  .timeout(timeoutDuration)
                  .retryWhen(DEFAULT_RETRY)
          )
          .count()
          .doOnNext(count -> log.info("{} Bookings completed for event {}", count, eventId))
          .map(cnt -> record)
          .onErrorResume(err -> {
            log.error("Unable to send EventCompleted message after max attempts. Sending to DLT", err);
            return handleDLTLogic(record);
          });
    } else {
      log.error(
          "Unable to deserialize EventCompleted message. Sending to DLT for further processing");
      return handleDLTLogic(record);
    }
  }
}
